package com.minicloud.api.messaging;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Thread-safe simulated SQS Queue with visibility timeouts, FIFO deduplication, and DLQ support.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SqsQueue {
    private String name;
    private String arn;

    @Builder.Default
    private boolean fifo = false;

    @Builder.Default
    private int visibilityTimeoutSeconds = 30;

    @Builder.Default
    private int maxReceiveCount = 3;

    private String deadLetterQueueName;

    @Builder.Default
    private Instant createdAt = Instant.now();

    // Queued messages ready for consumption
    @Builder.Default
    private ConcurrentLinkedDeque<QueueMessage> readyMessages = new ConcurrentLinkedDeque<>();

    // Ultra-high-throughput lock-free ring buffer engine
    @Builder.Default
    private boolean useRingBuffer = false;

    @Builder.Default
    private LockFreeRingBufferQueue<QueueMessage> ringBuffer = new LockFreeRingBufferQueue<>(65536);

    // In-flight messages awaiting delete confirmation (receiptHandle -> QueueMessage)
    @Builder.Default
    private ConcurrentHashMap<String, QueueMessage> inFlightMessages = new ConcurrentHashMap<>();

    // Message deduplication cache for FIFO queues (deduplicationId -> expiration Instant)
    @Builder.Default
    private ConcurrentHashMap<String, Instant> deduplicationCache = new ConcurrentHashMap<>();
}
