package com.minicloud.core.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccessKeyResponse {
    private String id;
    private String keyId;
    private String secretKey; // only returned ONCE on creation
    private boolean active;
    private String createdAt;
}
