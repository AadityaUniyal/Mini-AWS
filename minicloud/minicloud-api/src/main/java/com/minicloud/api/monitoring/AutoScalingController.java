package com.minicloud.api.monitoring;

import com.minicloud.api.monitoring.AutoScalingService;
import com.minicloud.api.monitoring.AutoScalingService.ScalingDecision;
import com.minicloud.api.monitoring.AutoScalingService.ScalingEvent;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * AutoScalingController — REST API for the horizontal scaling engine.
 *
 *  GET  /scaling/replicas         → current replica counts for all services
 *  GET  /scaling/metrics          → current CPU readings per service
 *  GET  /scaling/events           → recent scaling event history
 *  POST /scaling/evaluate         → manually trigger evaluation for a service
 *  PUT  /scaling/{service}/replicas → manually set replica count (admin)
 */
@RestController
@RequestMapping("/api/v1/scaling")
@RequiredArgsConstructor
@Tag(name = "Auto-Scaling", description = "Horizontal scaling engine — replica management and metrics")
public class AutoScalingController {

    private final AutoScalingService autoScalingService;

    @GetMapping("/replicas")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "Get current replica counts for all managed services")
    public ResponseEntity<Map<String, Object>> getReplicaCounts() {
        return ResponseEntity.ok(Map.of(
                "data", autoScalingService.getAllReplicaCounts(),
                "message", "Current replica counts"
        ));
    }

    @GetMapping("/metrics")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "Get current CPU readings for all managed services")
    public ResponseEntity<Map<String, Object>> getCpuMetrics() {
        return ResponseEntity.ok(Map.of(
                "data", autoScalingService.getAllCpuReadings(),
                "message", "Current CPU utilization per service"
        ));
    }

    @GetMapping("/events")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "Get recent scaling events (audit trail)")
    public ResponseEntity<Map<String, Object>> getScalingEvents(
            @RequestParam(defaultValue = "50") int limit) {

        List<ScalingEvent> events = autoScalingService.getRecentEvents(limit);
        return ResponseEntity.ok(Map.of(
                "data", events,
                "count", events.size()
        ));
    }

    @PostMapping("/evaluate")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "Trigger scaling evaluation for a service with a given CPU reading")
    public ResponseEntity<Map<String, Object>> evaluate(
            @RequestParam String service,
            @RequestParam double cpuPercent) {

        autoScalingService.updateCpuReading(service, cpuPercent);
        ScalingDecision decision = autoScalingService.evaluateService(service, cpuPercent);
        return ResponseEntity.ok(Map.of(
                "service", service,
                "action", decision.action().name(),
                "previousReplicas", decision.previousReplicas(),
                "newReplicas", decision.newReplicas(),
                "cpuPercent", cpuPercent
        ));
    }

    @PutMapping("/{service}/replicas")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "Manually set replica count for a service (admin override)")
    public ResponseEntity<Map<String, Object>> setReplicas(
            @PathVariable String service,
            @RequestParam int count) {

        autoScalingService.setReplicaCount(service, count);
        return ResponseEntity.ok(Map.of(
                "service", service,
                "replicas", autoScalingService.getReplicaCount(service),
                "message", "Replica count updated"
        ));
    }
}
