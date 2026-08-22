package com.minicloud.api.monitoring;

import com.minicloud.api.audit.AuditService;
import com.minicloud.api.auth.SecurityUtils;
import com.minicloud.api.auth.UserPrincipal;
import com.minicloud.api.domain.AuditLog;
import com.minicloud.api.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/monitoring/audit")
@RequiredArgsConstructor
@Tag(name = "CloudTrail Audit Logs", description = "Global activity tracking")
@SecurityRequirement(name = "BearerAuth")
public class AuditLogController {

    private final AuditService auditService;

    @GetMapping
    @Operation(summary = "Get recent audit logs for authenticated account")
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

    @GetMapping("/account/{accountId}")
    @Operation(summary = "Get audit logs for an account")
    public ResponseEntity<ApiResponse<List<AuditLog>>> getLogsForAccount(@PathVariable String accountId) {
        SecurityUtils.validateAccountOwnership(accountId);
        return ResponseEntity.ok(ApiResponse.ok(auditService.getLogsForAccount(accountId)));
    }

    @GetMapping("/recent")
    @Operation(summary = "Get recent audit logs across all services")
    public ResponseEntity<ApiResponse<List<AuditLog>>> getRecentLogs() {
        return ResponseEntity.ok(ApiResponse.ok(auditService.getRecentLogs()));
    }
}
