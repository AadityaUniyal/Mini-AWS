package com.minicloud.api.monitoring;

import com.minicloud.api.dto.ApiResponse;
import com.minicloud.api.domain.AuditLog;
import com.minicloud.api.audit.AuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/monitoring/audit")
@RequiredArgsConstructor
@Tag(name = "CloudTrail Audit Logs", description = "Global activity tracking")
public class AuditLogController {

    private final AuditService auditService;

    @GetMapping
    @Operation(summary = "Get recent audit logs (alias for /recent)")
    public ResponseEntity<ApiResponse<List<AuditLog>>> getAuditLogs(
            @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(ApiResponse.ok(auditService.getRecentLogs()));
    }

    @PostMapping("/record")
    @Operation(summary = "Record a new audit log entry (Internal usage)")
    public ResponseEntity<ApiResponse<String>> recordLog(
            @RequestParam String username,
            @RequestParam String service,
            @RequestParam String action,
            @RequestParam(required = false) String resource,
            @RequestParam String status,
            @RequestParam(required = false) String details) {
        auditService.recordAction(username, service, action, resource, status, details);
        return ResponseEntity.ok(ApiResponse.ok("Audit log recorded"));
    }

    @GetMapping("/user/{username}")
    @Operation(summary = "Get audit logs for a specific user")
    public ResponseEntity<ApiResponse<List<AuditLog>>> getLogsForUser(@PathVariable String username) {
        return ResponseEntity.ok(ApiResponse.ok(auditService.getLogsForUser(username)));
    }

    @GetMapping("/recent")
    @Operation(summary = "Get recent audit logs across all services")
    public ResponseEntity<ApiResponse<List<AuditLog>>> getRecentLogs() {
        return ResponseEntity.ok(ApiResponse.ok(auditService.getRecentLogs()));
    }
}
