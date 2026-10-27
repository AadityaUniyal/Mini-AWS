package com.minicloud.api.messaging;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class SqsService {

    private final Map<String, SqsQueue> queues = new ConcurrentHashMap<>();

    public SqsQueue createQueue(String name, boolean fifo, String deadLetterQueueName, int maxReceiveCount) {
        return createQueue(name, fifo, deadLetterQueueName, maxReceiveCount, false);
    }

    public SqsQueue createQueue(String name, boolean fifo, String deadLetterQueueName, int maxReceiveCount, boolean useRingBuffer) {
        String finalName = fifo && !name.endsWith(".fifo") ? name + ".fifo" : name;
        String arn = "arn:aws:sqs:us-east-1:123456789012:" + finalName;

        SqsQueue queue = SqsQueue.builder()
                .name(finalName)
                .arn(arn)
                .fifo(fifo)
                .deadLetterQueueName(deadLetterQueueName)
                .maxReceiveCount(maxReceiveCount > 0 ? maxReceiveCount : 3)
                .useRingBuffer(useRingBuffer)
                .build();

        queues.put(finalName, queue);
        log.info("Created SQS Queue: {} (FIFO: {}, RingBuffer: {})", finalName, fifo, useRingBuffer);
        return queue;
    }

    public Optional<SqsQueue> getQueue(String name) {
        return Optional.ofNullable(queues.get(name));
    }

    public List<SqsQueue> listQueues() {
        return new ArrayList<>(queues.values());
    }

    public boolean deleteQueue(String name) {
        return queues.remove(name) != null;
    }

    public QueueMessage sendMessage(String queueName, String body, String messageGroupId, String deduplicationId) {
        SqsQueue queue = queues.get(queueName);
        if (queue == null) {
            throw new IllegalArgumentException("Queue does not exist: " + queueName);
        }

        // FIFO deduplication check (5-minute sliding window)
        if (queue.isFifo() && deduplicationId != null && !deduplicationId.isBlank()) {
            Instant expiresAt = queue.getDeduplicationCache().get(deduplicationId);
            if (expiresAt != null && expiresAt.isAfter(Instant.now())) {
                log.debug("Duplicate message ignored for FIFO queue {}: {}", queueName, deduplicationId);
                return QueueMessage.builder()
                        .body(body)
                        .deduplicationId(deduplicationId)
                        .messageGroupId(messageGroupId)
                        .build();
            }
            queue.getDeduplicationCache().put(deduplicationId, Instant.now().plusSeconds(300));
        }

        QueueMessage message = QueueMessage.builder()
                .body(body)
                .messageGroupId(messageGroupId)
                .deduplicationId(deduplicationId)
                .build();

        if (queue.isUseRingBuffer()) {
            boolean offered = queue.getRingBuffer().offer(message);
            if (!offered) {
                throw new IllegalStateException("SQS RingBuffer queue is at maximum capacity: " + queueName);
            }
        } else {
            queue.getReadyMessages().addLast(message);
        }
        log.debug("Enqueued message {} into SQS queue {}", message.getId(), queueName);
        return message;
    }

    public List<QueueMessage> receiveMessages(String queueName, int maxMessages, int visibilityTimeoutSeconds) {
        SqsQueue queue = queues.get(queueName);
        if (queue == null) {
            throw new IllegalArgumentException("Queue does not exist: " + queueName);
        }

        int limit = Math.min(Math.max(1, maxMessages), 10);
        int timeout = visibilityTimeoutSeconds > 0 ? visibilityTimeoutSeconds : queue.getVisibilityTimeoutSeconds();
        List<QueueMessage> result = new ArrayList<>();

        for (int i = 0; i < limit; i++) {
            QueueMessage msg = queue.isUseRingBuffer()
                    ? queue.getRingBuffer().poll()
                    : queue.getReadyMessages().pollFirst();
            if (msg == null) break;

            msg.setReceiveCount(msg.getReceiveCount() + 1);
            msg.setReceiptHandle(UUID.randomUUID().toString());
            msg.setVisibleAfter(Instant.now().plusSeconds(timeout));

            queue.getInFlightMessages().put(msg.getReceiptHandle(), msg);
            result.add(msg);
        }

        return result;
    }

    public boolean deleteMessage(String queueName, String receiptHandle) {
        SqsQueue queue = queues.get(queueName);
        if (queue == null) return false;
        return queue.getInFlightMessages().remove(receiptHandle) != null;
    }

    public void purgeQueue(String queueName) {
        SqsQueue queue = queues.get(queueName);
        if (queue != null) {
            queue.getReadyMessages().clear();
            queue.getInFlightMessages().clear();
            queue.getDeduplicationCache().clear();
            if (queue.isUseRingBuffer()) {
                queue.getRingBuffer().clear();
            }
            log.info("Purged SQS Queue: {}", queueName);
        }
    }

    public Map<String, Object> getQueueAttributes(String queueName) {
        SqsQueue queue = queues.get(queueName);
        if (queue == null) {
            throw new IllegalArgumentException("Queue does not exist: " + queueName);
        }

        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("QueueArn", queue.getArn());
        attrs.put("ApproximateNumberOfMessages", queue.getReadyMessages().size());
        attrs.put("ApproximateNumberOfMessagesNotVisible", queue.getInFlightMessages().size());
        attrs.put("VisibilityTimeout", queue.getVisibilityTimeoutSeconds());
        attrs.put("FifoQueue", queue.isFifo());
        if (queue.getDeadLetterQueueName() != null) {
            attrs.put("RedrivePolicy", "{\"maxReceiveCount\":" + queue.getMaxReceiveCount()
                    + ",\"deadLetterTargetArn\":\"arn:aws:sqs:us-east-1:123456789012:" + queue.getDeadLetterQueueName() + "\"}");
        }
        return attrs;
    }

    /**
     * Periodically check in-flight messages.
     * If visibility timeout expired without deletion, re-enqueue or dispatch to Dead Letter Queue (DLQ).
     */
    @Scheduled(fixedRate = 1000)
    public void processVisibilityTimeouts() {
        Instant now = Instant.now();

        for (SqsQueue queue : queues.values()) {
            Iterator<Map.Entry<String, QueueMessage>> it = queue.getInFlightMessages().entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, QueueMessage> entry = it.next();
                QueueMessage msg = entry.getValue();

                if (msg.getVisibleAfter().isBefore(now)) {
                    it.remove();

                    // Check if message exceeded maxReceiveCount -> dispatch to Dead Letter Queue
                    if (queue.getDeadLetterQueueName() != null && msg.getReceiveCount() >= queue.getMaxReceiveCount()) {
                        SqsQueue dlq = queues.get(queue.getDeadLetterQueueName());
                        if (dlq != null) {
                            msg.setReceiptHandle(null);
                            dlq.getReadyMessages().addLast(msg);
                            log.warn("Message {} exceeded max receives ({}) on queue {}. Moved to DLQ: {}",
                                    msg.getId(), msg.getReceiveCount(), queue.getName(), dlq.getName());
                            continue;
                        }
                    }

                    // Otherwise return message to queue
                    msg.setReceiptHandle(null);
                    queue.getReadyMessages().addLast(msg);
                    log.debug("Message {} visibility timeout expired on queue {}. Returned to ready pool.",
                            msg.getId(), queue.getName());
                }
            }
        }
    }
}
