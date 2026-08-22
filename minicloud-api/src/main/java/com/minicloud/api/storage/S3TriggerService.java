package com.minicloud.api.storage;

import com.minicloud.api.domain.BucketRepository;
import com.minicloud.api.domain.FunctionRepository;
import com.minicloud.api.lambda.CreateTriggerRequest;
import com.minicloud.api.lambda.S3LambdaTrigger;
import com.minicloud.api.lambda.S3LambdaTriggerRepository;
import com.minicloud.api.lambda.TriggerResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service managing S3-to-Lambda trigger definitions and event matching.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class S3TriggerService {

    private final S3LambdaTriggerRepository triggerRepository;
    private final BucketRepository bucketRepository;
    private final FunctionRepository functionRepository;

    @Transactional
    public S3LambdaTrigger createTrigger(CreateTriggerRequest request, UUID userId) {
        if (request.bucketName() == null || request.bucketName().isBlank()) {
            throw new IllegalArgumentException("Bucket name is required");
        }
        if (request.functionName() == null || request.functionName().isBlank()) {
            throw new IllegalArgumentException("Function name is required");
        }

        List<String> events = request.events();
        if (events == null || events.isEmpty()) {
            events = List.of("s3:ObjectCreated:*");
        }

        boolean enabled = request.enabled() == null || request.enabled();

        S3LambdaTrigger trigger = S3LambdaTrigger.builder()
                .bucketName(request.bucketName())
                .functionName(request.functionName())
                .events(new ArrayList<>(events))
                .enabled(enabled)
                .userId(userId)
                .build();

        S3LambdaTrigger saved = triggerRepository.save(trigger);
        log.info("Registered S3 Lambda Trigger: ID={}, bucket={}, function={}, events={}",
                saved.getId(), saved.getBucketName(), saved.getFunctionName(), saved.getEvents());
        return saved;
    }

    public List<S3LambdaTrigger> listTriggers(String bucketName, UUID userId) {
        if (bucketName != null && !bucketName.isBlank()) {
            return triggerRepository.findByBucketName(bucketName);
        }
        if (userId != null) {
            return triggerRepository.findByUserId(userId);
        }
        return triggerRepository.findAll();
    }

    public S3LambdaTrigger getTrigger(UUID id) {
        return triggerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Trigger not found: " + id));
    }

    @Transactional
    public void deleteTrigger(UUID id) {
        if (!triggerRepository.existsById(id)) {
            throw new IllegalArgumentException("Trigger not found: " + id);
        }
        triggerRepository.deleteById(id);
        log.info("Deleted S3 Lambda Trigger ID={}", id);
    }

    public boolean matchesEvent(S3LambdaTrigger trigger, String incomingEvent) {
        if (trigger == null || !trigger.isEnabled()) {
            return false;
        }
        List<String> configuredEvents = trigger.getEvents();
        if (configuredEvents == null || configuredEvents.isEmpty()) {
            return true;
        }

        String normalizedIncoming = incomingEvent.startsWith("s3:") ? incomingEvent : "s3:" + incomingEvent;

        for (String pattern : configuredEvents) {
            if (pattern == null || pattern.isBlank() || "*".equals(pattern) || "s3:*".equals(pattern)) {
                return true;
            }
            String normalizedPattern = pattern.startsWith("s3:") ? pattern : "s3:" + pattern;

            if (normalizedPattern.equalsIgnoreCase(normalizedIncoming)) {
                return true;
            }

            if (normalizedPattern.endsWith("*")) {
                String prefix = normalizedPattern.substring(0, normalizedPattern.length() - 1);
                if (normalizedIncoming.toLowerCase().startsWith(prefix.toLowerCase())) {
                    return true;
                }
            }
        }
        return false;
    }

    public TriggerResponse toResponse(S3LambdaTrigger trigger) {
        return new TriggerResponse(
                trigger.getId() != null ? trigger.getId().toString() : null,
                trigger.getBucketName(),
                trigger.getFunctionName(),
                trigger.getEvents(),
                trigger.isEnabled(),
                trigger.getCreatedAt() != null ? trigger.getCreatedAt().toString() : null
        );
    }
}
