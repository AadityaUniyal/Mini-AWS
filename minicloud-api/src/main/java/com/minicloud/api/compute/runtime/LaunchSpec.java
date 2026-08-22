package com.minicloud.api.compute.runtime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LaunchSpec {
    private UUID instanceId;
    private String instanceName;
    private String accountId;
    private UUID userId;
    private String instanceType;
    private int cpuCores;
    private int ramMb;
    private int diskGb;
    private String command;
    private Map<String, String> environment;
}
