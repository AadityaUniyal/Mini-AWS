package com.minicloud.api.monitoring;

import com.minicloud.api.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/diagnostics")
@RequiredArgsConstructor
@Tag(name = "System Diagnostics", description = "Environmental configuration diagnostics")
public class SystemDiagnosticsController {

    private final SystemDiagnostics systemDiagnostics;

    @GetMapping
    @Operation(summary = "Get system runtime diagnostic statuses")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> getDiagnostics() {
        return ResponseEntity.ok(ApiResponse.ok("System diagnostics status", systemDiagnostics.getRuntimeStatus()));
    }
}
