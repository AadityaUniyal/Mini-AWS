package com.minicloud.api.compute.runtime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuntimeStatus {
    private boolean running;
    private Long pid;
    private String containerId;
    private double cpuUsagePercent;
    private double memoryUsageMb;
    private String statusMessage;
}
