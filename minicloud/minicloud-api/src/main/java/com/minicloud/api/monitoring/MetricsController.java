package com.minicloud.api.monitoring;

import com.minicloud.api.iam.*;
import com.minicloud.core.dto.ApiResponse;
import com.minicloud.core.dto.MetricsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/cloudwatch")
@RequiredArgsConstructor
@Tag(name = "MiniCloudWatch", description = "System metrics and logs (AWS CloudWatch equivalent)")
@SecurityRequirement(name = "BearerAuth")
public class MetricsController {

    private final MetricsService metricsService;
    private final UserRepository userRepository;
    private final PolicyEvaluator policyEvaluator;

    @GetMapping("/system")
    @Operation(summary = "Get system metrics: CPU, heap RAM, disk usage, and rolling history")
    public ResponseEntity<ApiResponse<MetricsResponse>> getSystemMetrics(Authentication auth) {
        User user = getUser(auth);
        checkPermission(user, "cloudwatch:GetMetricData", "*");
        return ResponseEntity.ok(ApiResponse.ok(metricsService.getSystemMetrics()));
    }

    @GetMapping("/metrics/cpu-history")
    @Operation(summary = "Get rolling history of CPU load (last 5 minutes)")
    public ResponseEntity<ApiResponse<List<Double>>> getCpuHistory(Authentication auth) {
        User user = getUser(auth);
        checkPermission(user, "cloudwatch:GetMetricData", "*");
        return ResponseEntity.ok(ApiResponse.ok(metricsService.getCpuHistory()));
    }

    @GetMapping("/metrics/ram-history")
    @Operation(summary = "Get rolling history of Heap usage in MB (last 5 minutes)")
    public ResponseEntity<ApiResponse<List<Double>>> getRamHistory(Authentication auth) {
        User user = getUser(auth);
        checkPermission(user, "cloudwatch:GetMetricData", "*");
        return ResponseEntity.ok(ApiResponse.ok(metricsService.getRamHistory()));
    }

    @GetMapping("/instances")
    @Operation(summary = "Get all instance metrics (PID, state, uptime, alive)")
    public ResponseEntity<ApiResponse<MetricsResponse>> getInstanceMetrics(Authentication auth) {
        User user = getUser(auth);
        checkPermission(user, "cloudwatch:GetMetricData", "*");
        return ResponseEntity.ok(ApiResponse.ok(metricsService.getInstanceMetrics()));
    }

    @GetMapping("/logs")
    @Operation(summary = "Get recent application log lines")
    public ResponseEntity<ApiResponse<List<String>>> getLogs(
            @RequestParam(defaultValue = "50") int lines,
            Authentication auth) {
        User user = getUser(auth);
        checkPermission(user, "cloudwatch:GetLogEvents", "mc:cw:logs");
        List<String> logs = metricsService.getRecentLogs(lines);
        return ResponseEntity.ok(ApiResponse.ok(logs));
    }

    // ─────────────────────────── ALARMS ───────────────────────────

    private final AlarmRepository alarmRepository;

    @GetMapping("/alarms")
    @Operation(summary = "List all alarms for current user")
    public ResponseEntity<ApiResponse<List<Alarm>>> listAlarms(Authentication auth) {
        User user = getUser(auth);
        checkPermission(user, "cloudwatch:DescribeAlarms", "*");
        return ResponseEntity.ok(ApiResponse.ok(alarmRepository.findAllByUserId(user.getId())));
    }

    @PostMapping("/alarms")
    @Operation(summary = "Create a new metric alarm")
    public ResponseEntity<ApiResponse<Alarm>> createAlarm(
            @RequestBody Alarm alarm,
            Authentication auth) {
        User user = getUser(auth);
        checkPermission(user, "cloudwatch:PutMetricAlarm", "mc:cw:alarm:" + alarm.getName());
        alarm.setUserId(user.getId());
        alarm.setState(Alarm.AlarmState.OK);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Alarm created", alarmRepository.save(alarm)));
    }

    @DeleteMapping("/alarms/{id}")
    @Operation(summary = "Delete an alarm")
    public ResponseEntity<ApiResponse<String>> deleteAlarm(
            @PathVariable UUID id,
            Authentication auth) {
        User user = getUser(auth);
        checkPermission(user, "cloudwatch:DeleteAlarms", "*");
        alarmRepository.deleteById(id);
        return ResponseEntity.ok(ApiResponse.ok("Alarm deleted", id.toString()));
    }

    private void checkPermission(User user, String action, String resource) {
        if (!policyEvaluator.isAuthorized(user.getPolicies(), action, resource)) {
            log.warn("Access denied: user {} action {} resource {}", user.getUsername(), action, resource);
            throw new AccessDeniedException("Unauthorized: " + action + " on " + resource);
        }
    }

    private User getUser(Authentication auth) {
        return userRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
    }
}
