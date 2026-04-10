package com.minicloud.core.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response DTO for CloudWatch-equivalent metrics.
 *
 * cpuHistory / ramHistory hold time-series data points (spec §11)
 * collected every 5 seconds by MetricsService into a ConcurrentLinkedDeque.
 * The UI MonitorPanel uses these lists to plot live charts.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetricsResponse {

    // ── Point-in-time system metrics ─────────────────────────────
    private double cpuPercent;
    private long heapUsedMb;
    private long heapMaxMb;
    private long diskUsedGb;
    private long diskTotalGb;
    private long diskFreeGb;

    // ── Rolling history (last 60 samples @ 5s interval = 5 min) ─
    private List<Double> cpuHistory;    // CPU % readings over time
    private List<Double> ramHistory;    // Heap used MB readings over time

    // ── Instance metrics (for /cloudwatch/instances) ─────────────
    private List<InstanceMetric> instances;

    // ── Log lines (for /cloudwatch/logs) ─────────────────────────
    private List<String> logLines;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InstanceMetric {
        private String id;
        private String name;
        private String state;
        private Integer pid;
        private long uptimeSeconds;
        private boolean alive;
    }
}
