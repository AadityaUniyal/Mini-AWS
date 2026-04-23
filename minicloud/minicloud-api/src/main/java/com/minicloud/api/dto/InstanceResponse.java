package com.minicloud.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InstanceResponse {
    private String id;
    private String name;
    private String type;
    private String state;
    private Integer pid;
    private String command;
    private long uptimeSeconds;
    private String launchedAt;
    private String updatedAt;
    private String accountId;
    private String subnetId;
    private String privateIp;
    private String publicIp;
    private String securityGroupId;
    private String securityGroupName;
}
