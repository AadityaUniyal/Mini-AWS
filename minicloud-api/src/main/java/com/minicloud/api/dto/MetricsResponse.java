package com.minicloud.api.dto;

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
    private double cpuLoad;
    private double usedHeapMb;
    private double diskUsedGb;
    private int activeThreads;
    private double uptimeSeconds;

    // ── Rolling history (last 60 samples @ 5s interval = 5 min) ─
    private List<Double> cpuHistory;
    private List<Double> ramHistory;

    // ── Event Correlation (Markers for the graph) ────────────────
    private List<MetricEvent> events;

    // ── Instance metrics (for /cloudwatch/instances / Task Manager) 
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
        private String service; // "EC2", "RDS", etc.
        private String state;
        private Integer pid;
        private long uptimeSeconds;
        private boolean alive;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MetricEvent {
        private String service; // "IAM", "S3", "EC2", "RDS"
        private String action;  // e.g. "CreateUser", "Upload"
        private long timestamp; // epoch millis
    }
}
