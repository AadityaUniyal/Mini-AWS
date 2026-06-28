package com.minicloud.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BucketResponse {
    private String id;
    private String name;
    private String ownerId;
    private long objectCount;
    private long totalSizeBytes;
    private Integer retentionDays;
    private String createdAt;
}
