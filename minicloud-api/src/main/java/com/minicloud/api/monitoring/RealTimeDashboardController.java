package com.minicloud.api.monitoring;

import com.minicloud.api.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Real-Time Dashboard Controller
 * Provides live database metrics and monitoring endpoints
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
@Tag(name = "Real-Time Dashboard", description = "Live database monitoring and metrics")
public class RealTimeDashboardController {

    private final RealTimeDbService realTimeDbService;

    @GetMapping("/live-status")
    @Operation(summary = "Get live system status from database")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getLiveStatus() {
        Map<String, Object> status = realTimeDbService.getLiveSystemStatus();
        return ResponseEntity.ok(ApiResponse.ok("Live system status", status));
    }

    @GetMapping("/live-users")
    @Operation(summary = "Get live user activity from database")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getLiveUsers() {
        List<Map<String, Object>> users = realTimeDbService.getLiveUserActivity();
        return ResponseEntity.ok(ApiResponse.ok("Live user activity", users));
    }

    @GetMapping("/live-resources/{accountId}")
    @Operation(summary = "Get live resource summary for account")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getLiveResources(@PathVariable String accountId) {
        Map<String, Object> resources = realTimeDbService.getLiveResourceSummary(accountId);
        return ResponseEntity.ok(ApiResponse.ok("Live resource summary", resources));
    }

    @GetMapping("/live-costs/{accountId}")
    @Operation(summary = "Get live cost summary for account")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getLiveCosts(@PathVariable String accountId) {
        Map<String, Object> costs = realTimeDbService.getLiveCostSummary(accountId);
        return ResponseEntity.ok(ApiResponse.ok("Live cost summary", costs));
    }

    @PostMapping("/record-event")
    @Operation(summary = "Record a real-time event (Internal use)")
    public ResponseEntity<ApiResponse<String>> recordEvent(
            @RequestParam String eventType,
            @RequestParam String sourceService,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String accountId,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String resourceId,
            @RequestParam(required = false) String eventData,
            @RequestParam(defaultValue = "INFO") String severity) {
        
        realTimeDbService.recordEvent(eventType, sourceService, userId, accountId, 
                                     resourceType, resourceId, eventData, severity);
        
        return ResponseEntity.ok(ApiResponse.ok("Event recorded successfully"));
    }

    @PostMapping("/create-notification")
    @Operation(summary = "Create a user notification")
    public ResponseEntity<ApiResponse<String>> createNotification(
            @RequestParam String userId,
            @RequestParam String type,
            @RequestParam String title,
            @RequestParam String message,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String resourceId) {
        
        realTimeDbService.createNotification(userId, type, title, message, resourceType, resourceId);
        
        return ResponseEntity.ok(ApiResponse.ok("Notification created successfully"));
    }
}