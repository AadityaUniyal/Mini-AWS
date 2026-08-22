package com.minicloud.api.storage.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.UUID;

@Getter
public class S3ObjectDeletedEvent extends ApplicationEvent {
    private final String bucketName;
    private final String objectKey;
    private final UUID userId;
    private final String accountId;

    public S3ObjectDeletedEvent(Object source, String bucketName, String objectKey, UUID userId, String accountId) {
        super(source);
        this.bucketName = bucketName;
        this.objectKey = objectKey;
        this.userId = userId;
        this.accountId = accountId;
    }
}
