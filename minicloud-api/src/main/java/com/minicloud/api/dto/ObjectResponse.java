package com.minicloud.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ObjectResponse {
    private String id;
    private String bucketName;
    private String objectKey;
    private long sizeBytes;
    private String contentType;
    private String lastModified;
    private String createdAt;
    private boolean isFolder;
    private java.util.Map<String, String> metadata;
}
