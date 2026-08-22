package com.minicloud.api.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minicloud.api.domain.Task;
import com.minicloud.api.lambda.LambdaExecutionService;
import com.minicloud.api.lambda.S3LambdaTrigger;
import com.minicloud.api.lambda.S3LambdaTriggerRepository;
import com.minicloud.api.service.TaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Dispatches S3 storage events to registered and enabled Lambda triggers asynchronously,
 * producing AWS S3 standard event notification JSON and streaming task updates over WebSocket.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class S3TriggerDispatcher {

    private final S3LambdaTriggerRepository triggerRepository;
    private final S3TriggerService triggerService;
    private final LambdaExecutionService lambdaExecutionService;
    private final TaskService taskService;
    private final ObjectMapper objectMapper;

    @Value("${minicloud.region:us-east-1}")
    private String region;

    private final ExecutorService dispatcherExecutor = Executors.newFixedThreadPool(4);

    @jakarta.annotation.PreDestroy
    public void shutdown() {
        dispatcherExecutor.shutdown();
    }

    /**
     * Dispatches an S3 object creation event asynchronously to all matching triggers.
     */
    public CompletableFuture<List<LambdaExecutionService.InvocationResult>> dispatchUploadEvent(
            String bucketName,
            String objectKey,
            long sizeBytes,
            String eTag,
            UUID userId,
            String accountId) {

        List<S3LambdaTrigger> triggers = triggerRepository.findByBucketNameAndEnabledTrue(bucketName)
                .stream()
                .filter(t -> triggerService.matchesEvent(t, "ObjectCreated:Put"))
                .collect(Collectors.toList());

        if (triggers.isEmpty()) {
            log.debug("No enabled triggers found for bucket '{}'", bucketName);
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        log.info("Found {} matching trigger(s) for bucket '{}' on ObjectCreated:Put", triggers.size(), bucketName);

        List<CompletableFuture<LambdaExecutionService.InvocationResult>> futures = triggers.stream()
                .map(trigger -> CompletableFuture.supplyAsync(() -> {
                    String principalId = userId != null ? userId.toString() : "user-anonymous";
                    Task task = taskService.createTask(
                            "S3_TRIGGER_INVOCATION",
                            "S3 Trigger [" + bucketName + "/" + objectKey + "] -> Lambda [" + trigger.getFunctionName() + "]",
                            userId,
                            accountId
                    );

                    try {
                        taskService.updateProgress(task.getId(), 20, "RUNNING", null);

                        String payloadJson = buildS3EventPayload(trigger, bucketName, objectKey, sizeBytes, eTag, principalId);
                        log.debug("Generated S3 event notification JSON for Lambda '{}': {}", trigger.getFunctionName(), payloadJson);

                        taskService.updateProgress(task.getId(), 50, "RUNNING", null);
                        LambdaExecutionService.InvocationResult result = lambdaExecutionService.invoke(
                                trigger.getFunctionName(),
                                payloadJson,
                                userId
                        );

                        if (result.success()) {
                            log.info("S3 Trigger executed successfully for function '{}' — duration={}ms",
                                    trigger.getFunctionName(), result.durationMs());
                            taskService.updateProgress(
                                    task.getId(),
                                    100,
                                    "COMPLETED",
                                    result.stdout().isBlank() ? "Lambda execution completed" : result.stdout()
                            );
                        } else {
                            log.warn("S3 Trigger failed for function '{}' — exitCode={}, stderr={}",
                                    trigger.getFunctionName(), result.exitCode(), result.stderr());
                            taskService.updateProgress(
                                    task.getId(),
                                    100,
                                    "FAILED",
                                    result.stderr().isBlank() ? "Lambda execution failed" : result.stderr()
                            );
                        }
                        return result;
                    } catch (Exception e) {
                        log.error("Failed to execute S3 trigger for function '{}'", trigger.getFunctionName(), e);
                        taskService.updateProgress(task.getId(), 100, "FAILED", e.getMessage());
                        return LambdaExecutionService.InvocationResult.error("Trigger execution failed: " + e.getMessage(), -1);
                    }
                }, dispatcherExecutor))
                .collect(Collectors.toList());

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream()
                        .map(CompletableFuture::join)
                        .collect(Collectors.toList()));
    }

    /**
     * Builds standard AWS S3 Event Notification JSON compatible with AWS Lambda event handlers.
     */
    public String buildS3EventPayload(
            S3LambdaTrigger trigger,
            String bucketName,
            String objectKey,
            long sizeBytes,
            String eTag,
            String principalId) {

        try {
            String configId = trigger != null && trigger.getId() != null ? trigger.getId().toString() : UUID.randomUUID().toString();
            String safeETag = (eTag != null && !eTag.isBlank()) ? eTag : "d41d8cd98f00b204e9800998ecf8427e";
            String sequencer = Long.toHexString(System.currentTimeMillis()).toUpperCase();

            Map<String, Object> record = new LinkedHashMap<>();
            record.put("eventVersion", "2.1");
            record.put("eventSource", "aws:s3");
            record.put("awsRegion", region != null ? region : "us-east-1");
            record.put("eventTime", Instant.now().toString());
            record.put("eventName", "ObjectCreated:Put");

            Map<String, Object> userIdentity = new LinkedHashMap<>();
            userIdentity.put("principalId", principalId);
            record.put("userIdentity", userIdentity);

            Map<String, Object> s3 = new LinkedHashMap<>();
            s3.put("s3SchemaVersion", "1.0");
            s3.put("configurationId", configId);

            Map<String, Object> bucket = new LinkedHashMap<>();
            bucket.put("name", bucketName);
            bucket.put("ownerIdentity", Map.of("principalId", principalId));
            bucket.put("arn", "arn:aws:s3:::" + bucketName);
            s3.put("bucket", bucket);

            Map<String, Object> object = new LinkedHashMap<>();
            object.put("key", objectKey);
            object.put("size", sizeBytes);
            object.put("eTag", safeETag);
            object.put("sequencer", sequencer);
            s3.put("object", object);

            record.put("s3", s3);

            Map<String, Object> root = new LinkedHashMap<>();
            root.put("Records", List.of(record));

            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            log.error("Failed to construct S3 event notification JSON", e);
            throw new RuntimeException("Error building S3 event notification payload: " + e.getMessage(), e);
        }
    }
}
