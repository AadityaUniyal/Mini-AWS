package com.minicloud.api.storage.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.UUID;

@Getter
public class S3ObjectCreatedEvent extends ApplicationEvent {
    private final String bucketName;
    private final String objectKey;
    private final long sizeBytes;
    private final String eTag;
    private final UUID userId;
    private final String accountId;

    public S3ObjectCreatedEvent(Object source, String bucketName, String objectKey, long sizeBytes, String eTag, UUID userId, String accountId) {
        super(source);
        this.bucketName = bucketName;
        this.objectKey = objectKey;
        this.sizeBytes = sizeBytes;
        this.eTag = eTag;
        this.userId = userId;
        this.accountId = accountId;
    }
}
