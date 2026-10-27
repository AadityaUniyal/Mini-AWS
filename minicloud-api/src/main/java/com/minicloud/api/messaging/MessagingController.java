package com.minicloud.api.messaging;

import com.minicloud.api.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/messaging")
@RequiredArgsConstructor
@Tag(name = "Messaging (SQS & SNS)", description = "Distributed queue and pub/sub notification services")
public class MessagingController {

    private final SqsService sqsService;
    private final SnsService snsService;

    // --- SQS Endpoints ---

    @PostMapping("/sqs/queues")
    @Operation(summary = "Create an SQS Queue")
    public ResponseEntity<ApiResponse<SqsQueue>> createQueue(
            @RequestParam String queueName,
            @RequestParam(required = false, defaultValue = "false") boolean fifo,
            @RequestParam(required = false) String deadLetterQueueName,
            @RequestParam(required = false, defaultValue = "3") int maxReceiveCount) {

        SqsQueue queue = sqsService.createQueue(queueName, fifo, deadLetterQueueName, maxReceiveCount);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Queue created", queue));
    }

    @GetMapping("/sqs/queues")
    @Operation(summary = "List all SQS Queues")
    public ResponseEntity<ApiResponse<List<SqsQueue>>> listQueues() {
        return ResponseEntity.ok(ApiResponse.ok(sqsService.listQueues()));
    }

    @GetMapping("/sqs/queues/{queueName}/attributes")
    @Operation(summary = "Get SQS Queue attributes and message counts")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getQueueAttributes(@PathVariable String queueName) {
        return ResponseEntity.ok(ApiResponse.ok(sqsService.getQueueAttributes(queueName)));
    }

    @PostMapping("/sqs/queues/{queueName}/messages")
    @Operation(summary = "Send a message to an SQS Queue")
    public ResponseEntity<ApiResponse<QueueMessage>> sendMessage(
            @PathVariable String queueName,
            @RequestParam String body,
            @RequestParam(required = false) String messageGroupId,
            @RequestParam(required = false) String deduplicationId) {

        QueueMessage message = sqsService.sendMessage(queueName, body, messageGroupId, deduplicationId);
        return ResponseEntity.ok(ApiResponse.ok("Message enqueued", message));
    }

    @GetMapping("/sqs/queues/{queueName}/messages")
    @Operation(summary = "Receive messages from an SQS Queue")
    public ResponseEntity<ApiResponse<List<QueueMessage>>> receiveMessages(
            @PathVariable String queueName,
            @RequestParam(required = false, defaultValue = "1") int maxMessages,
            @RequestParam(required = false, defaultValue = "30") int visibilityTimeoutSeconds) {

        List<QueueMessage> messages = sqsService.receiveMessages(queueName, maxMessages, visibilityTimeoutSeconds);
        return ResponseEntity.ok(ApiResponse.ok(messages));
    }

    @DeleteMapping("/sqs/queues/{queueName}/messages")
    @Operation(summary = "Delete an SQS message after processing using receipt handle")
    public ResponseEntity<ApiResponse<String>> deleteMessage(
            @PathVariable String queueName,
            @RequestParam String receiptHandle) {

        boolean deleted = sqsService.deleteMessage(queueName, receiptHandle);
        if (deleted) {
            return ResponseEntity.ok(ApiResponse.ok("Message deleted"));
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("Receipt handle not found or expired"));
    }

    @PostMapping("/sqs/queues/{queueName}/purge")
    @Operation(summary = "Purge all messages in an SQS Queue")
    public ResponseEntity<ApiResponse<String>> purgeQueue(@PathVariable String queueName) {
        sqsService.purgeQueue(queueName);
        return ResponseEntity.ok(ApiResponse.ok("Queue purged"));
    }

    // --- SNS Endpoints ---

    @PostMapping("/sns/topics")
    @Operation(summary = "Create an SNS Topic")
    public ResponseEntity<ApiResponse<SnsTopic>> createTopic(@RequestParam String topicName) {
        SnsTopic topic = snsService.createTopic(topicName);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Topic created", topic));
    }

    @GetMapping("/sns/topics")
    @Operation(summary = "List all SNS Topics")
    public ResponseEntity<ApiResponse<List<SnsTopic>>> listTopics() {
        return ResponseEntity.ok(ApiResponse.ok(snsService.listTopics()));
    }

    @PostMapping("/sns/topics/{topicName}/subscribe")
    @Operation(summary = "Subscribe an SQS queue or endpoint to an SNS Topic")
    public ResponseEntity<ApiResponse<SnsSubscription>> subscribeTopic(
            @PathVariable String topicName,
            @RequestParam String protocol,
            @RequestParam String endpoint) {

        SnsSubscription subscription = snsService.subscribe(topicName, protocol, endpoint);
        return ResponseEntity.ok(ApiResponse.ok("Subscribed", subscription));
    }

    @PostMapping("/sns/topics/{topicName}/publish")
    @Operation(summary = "Publish a message to an SNS Topic (Fan-out)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> publish(
            @PathVariable String topicName,
            @RequestParam String message,
            @RequestParam(required = false, defaultValue = "Notification") String subject) {

        int delivered = snsService.publish(topicName, subject, message);
        return ResponseEntity.ok(ApiResponse.ok("Published", Map.of("deliveredSubscribers", delivered)));
    }
}
