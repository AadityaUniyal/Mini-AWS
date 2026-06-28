package com.minicloud.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RdsResponse {
    private String id;
    private String name;
    private String status;
    private int port;
    private String endpoint;
    private String databaseName;
    private String masterUsername;
    private String securityGroupId;
    private String securityGroupName;
    private String createdAt;
}
