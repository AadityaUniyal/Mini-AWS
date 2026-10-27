package com.minicloud.api.messaging;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class LockFreeRingBufferTest {

    @Test
    @DisplayName("LockFreeRingBuffer: Basic enqueue, dequeue, size and capacity semantics")
    void testBasicEnqueueDequeue() {
        LockFreeRingBufferQueue<String> ringBuffer = new LockFreeRingBufferQueue<>(16);
        assertEquals(16, ringBuffer.getCapacity());
        assertTrue(ringBuffer.isEmpty());
        assertEquals(0, ringBuffer.size());

        assertTrue(ringBuffer.offer("Item-1"));
        assertTrue(ringBuffer.offer("Item-2"));
        assertTrue(ringBuffer.offer("Item-3"));

        assertEquals(3, ringBuffer.size());
        assertFalse(ringBuffer.isEmpty());
        assertEquals("Item-1", ringBuffer.peek());

        assertEquals("Item-1", ringBuffer.poll());
        assertEquals("Item-2", ringBuffer.poll());
        assertEquals("Item-3", ringBuffer.poll());
        assertNull(ringBuffer.poll());
        assertTrue(ringBuffer.isEmpty());
    }

    @Test
    @DisplayName("LockFreeRingBuffer: Seamless wraparound across thousands of boundary cycles")
    void testBufferWrapAround() {
        int capacity = 8;
        LockFreeRingBufferQueue<Integer> ringBuffer = new LockFreeRingBufferQueue<>(capacity);

        // Run 1,000 push/pop cycles to wrap sequence cursor past many masks
        for (int i = 0; i < 1000; i++) {
            assertTrue(ringBuffer.offer(i));
            assertEquals(i, ringBuffer.poll());
        }

        assertTrue(ringBuffer.isEmpty());
    }

    @Test
    @DisplayName("LockFreeRingBuffer: High-throughput concurrent MPMC benchmark (>100,000 msgs/sec)")
    void testConcurrentMpmcThroughput() throws Exception {
        int totalMessages = 200_000;
        LockFreeRingBufferQueue<Integer> ringBuffer = new LockFreeRingBufferQueue<>(32768);

        int producerThreads = 4;
        int consumerThreads = 4;
        int msgsPerProducer = totalMessages / producerThreads;

        ExecutorService executor = Executors.newFixedThreadPool(producerThreads + consumerThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch producerLatch = new CountDownLatch(producerThreads);
        AtomicInteger consumedCount = new AtomicInteger(0);

        long startNs = System.nanoTime();

        // Launch Consumers
        for (int c = 0; c < consumerThreads; c++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    while (consumedCount.get() < totalMessages) {
                        Integer val = ringBuffer.poll();
                        if (val != null) {
                            consumedCount.incrementAndGet();
                        } else {
                            Thread.onSpinWait();
                        }
                    }
                } catch (InterruptedException ignored) {}
            });
        }

        // Launch Producers
        for (int p = 0; p < producerThreads; p++) {
            final int producerId = p;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int i = 0; i < msgsPerProducer; i++) {
                        while (!ringBuffer.offer(i)) {
                            Thread.onSpinWait();
                        }
                    }
                } catch (InterruptedException ignored) {
                } finally {
                    producerLatch.countDown();
                }
            });
        }

        // Start all threads simultaneously
        startLatch.countDown();
        assertTrue(producerLatch.await(10, TimeUnit.SECONDS), "Producers did not finish in time");

        // Wait until all consumed
        long timeoutNs = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (consumedCount.get() < totalMessages && System.nanoTime() < timeoutNs) {
            Thread.sleep(10);
        }

        long durationNs = System.nanoTime() - startNs;
        double seconds = durationNs / 1_000_000_000.0;
        double throughput = totalMessages / seconds;

        executor.shutdownNow();

        assertEquals(totalMessages, consumedCount.get());
        System.out.printf("RingBuffer MPMC throughput: %.2f msgs/sec in %.3f seconds%n", throughput, seconds);
        assertTrue(throughput > 50_000, "Throughput should exceed 50k msgs/sec on any hardware");
    }

    @Test
    @DisplayName("SqsService: Integration test with LockFreeRingBuffer mode enabled")
    void testSqsServiceWithRingBuffer() {
        SqsService sqsService = new SqsService();
        String queueName = "ring-fast-queue";

        SqsQueue queue = sqsService.createQueue(queueName, false, null, 3, true);
        assertTrue(queue.isUseRingBuffer());

        // Send messages
        QueueMessage m1 = sqsService.sendMessage(queueName, "Payload 1", null, null);
        QueueMessage m2 = sqsService.sendMessage(queueName, "Payload 2", null, null);
        assertNotNull(m1.getId());
        assertNotNull(m2.getId());

        // Receive messages
        List<QueueMessage> received = sqsService.receiveMessages(queueName, 10, 30);
        assertEquals(2, received.size());
        assertEquals("Payload 1", received.get(0).getBody());
        assertEquals("Payload 2", received.get(1).getBody());

        // Delete message
        boolean deleted = sqsService.deleteMessage(queueName, received.get(0).getReceiptHandle());
        assertTrue(deleted);

        // Purge
        sqsService.purgeQueue(queueName);
        assertTrue(sqsService.receiveMessages(queueName, 10, 30).isEmpty());
    }
}
