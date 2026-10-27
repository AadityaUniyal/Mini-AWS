package com.minicloud.api.messaging;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * In-memory representation of an SQS message.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueueMessage {
    @Builder.Default
    private String id = UUID.randomUUID().toString();

    private String body;
    private String receiptHandle;

    @Builder.Default
    private int receiveCount = 0;

    @Builder.Default
    private Instant visibleAfter = Instant.now();

    @Builder.Default
    private Instant createdAt = Instant.now();

    private String messageGroupId;
    private String deduplicationId;
}
