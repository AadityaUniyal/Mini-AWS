package com.minicloud.api.messaging;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SnsSubscription {
    @Builder.Default
    private String subscriptionArn = "arn:aws:sns:us-east-1:123456789012:sub-" + UUID.randomUUID();

    private String topicArn;
    private String protocol; // sqs, lambda, http, email
    private String endpoint;

    @Builder.Default
    private Instant createdAt = Instant.now();
}
