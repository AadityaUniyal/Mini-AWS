package com.minicloud.core.dto;

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
    private String userId;
    private long objectCount;
    private String createdAt;
}
