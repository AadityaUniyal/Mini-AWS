package com.minicloud.api.messaging;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SnsTopic {
    private String name;
    private String arn;

    @Builder.Default
    private Instant createdAt = Instant.now();

    @Builder.Default
    private List<SnsSubscription> subscriptions = new CopyOnWriteArrayList<>();
}
