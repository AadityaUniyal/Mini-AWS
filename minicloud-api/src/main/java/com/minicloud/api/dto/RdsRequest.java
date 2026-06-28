package com.minicloud.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RdsRequest {
    private String name;
    private String databaseName;
    private String masterUsername;
    private String masterPassword;
    private String securityGroupId;
}
