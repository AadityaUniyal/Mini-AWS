package com.minicloud.api.monitoring;

import com.minicloud.api.dto.ApiResponse;
import com.minicloud.api.dto.MetricsResponse;
import java.util.Map;
import com.minicloud.api.monitoring.MetricsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/monitoring")
@RequiredArgsConstructor
@Tag(name = "CloudWatch Metrics", description = "System performance and logging")
public class MetricsController {

    private final MetricsService metricsService;

    @GetMapping("/metrics/current")
    @Operation(summary = "Get current live metrics (CPU, memory, requests)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCurrentMetrics() {
        MetricsResponse m = metricsService.getSystemMetrics();
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("cpuUsage",         m.getCpuLoad());
        data.put("heapUsedPercent",  m.getUsedHeapMb() > 0 ? Math.min(100.0, m.getUsedHeapMb() / 10.24) : 0);
        data.put("totalRequests",    0);
        data.put("gcPauseMs",        0);
        data.put("activeThreads",    m.getActiveThreads());
        data.put("uptimeSeconds",    m.getUptimeSeconds());
        data.put("diskUsedGb",       m.getDiskUsedGb());
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    @GetMapping("/system")
    @Operation(summary = "Get system metrics")
    public ResponseEntity<ApiResponse<MetricsResponse>> getSystemMetrics() {
        return ResponseEntity.ok(ApiResponse.ok(metricsService.getSystemMetrics()));
    }

    @GetMapping("/metrics/cpu-history")
    @Operation(summary = "Get CPU load history")
    public ResponseEntity<ApiResponse<List<Double>>> getCpuHistory() {
        return ResponseEntity.ok(ApiResponse.ok(metricsService.getCpuHistory()));
    }

    @GetMapping("/metrics/ram-history")
    @Operation(summary = "Get RAM history")
    public ResponseEntity<ApiResponse<List<Double>>> getRamHistory() {
        return ResponseEntity.ok(ApiResponse.ok(metricsService.getRamHistory()));
    }

    @GetMapping("/logs")
    @Operation(summary = "Get recent logs")
    public ResponseEntity<ApiResponse<List<String>>> getLogs(@RequestParam(defaultValue = "50") int lines) {
        return ResponseEntity.ok(ApiResponse.ok(metricsService.getRecentLogs(lines)));
    }

    @GetMapping("/logs/streams/{accountId}")
    @Operation(summary = "Get log streams for an account")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getLogStreams(@PathVariable String accountId) {
        // Mock log streams data for now
        List<Map<String, Object>> streams = List.of(
            Map.of("id", "stream-1", "name", "application-logs", "lastEventTime", System.currentTimeMillis()),
            Map.of("id", "stream-2", "name", "error-logs", "lastEventTime", System.currentTimeMillis() - 3600000),
            Map.of("id", "stream-3", "name", "access-logs", "lastEventTime", System.currentTimeMillis() - 7200000)
        );
        return ResponseEntity.ok(ApiResponse.ok(streams));
    }

    @GetMapping("/logs/events/{streamId}")
    @Operation(summary = "Get log events for a specific stream")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getLogEvents(@PathVariable String streamId) {
        // Mock log events data for now
        List<Map<String, Object>> events = List.of(
            Map.of("timestamp", System.currentTimeMillis(), "message", "Application started successfully"),
            Map.of("timestamp", System.currentTimeMillis() - 60000, "message", "Processing user request"),
            Map.of("timestamp", System.currentTimeMillis() - 120000, "message", "Database connection established"),
            Map.of("timestamp", System.currentTimeMillis() - 180000, "message", "Cache initialized")
        );
        return ResponseEntity.ok(ApiResponse.ok(events));
    }

    @PostMapping("/events")
    @Operation(summary = "Record a custom metric event (Internal usage)")
    public ResponseEntity<ApiResponse<String>> recordEvent(
            @RequestParam String service,
            @RequestParam String event) {
        metricsService.recordEvent(service, event);
        return ResponseEntity.ok(ApiResponse.ok("Event recorded"));
    }
}
