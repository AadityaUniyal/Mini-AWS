package com.minicloud.core.dto;

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
}
