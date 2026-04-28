package com.minicloud.api.lambda;

import com.minicloud.api.domain.Function;
import com.minicloud.api.domain.LambdaInvocationLog;
import com.minicloud.api.domain.LambdaInvocationLogRepository;
import com.minicloud.api.lambda.FunctionManagementService;
import com.minicloud.api.lambda.LambdaExecutionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * LambdaController — REST API for MiniLambda serverless functions.
 *
 *  POST   /lambda                        → register a new function
 *  GET    /lambda                        → list all functions
 *  GET    /lambda/{name}                 → get function details
 *  PUT    /lambda/{name}                 → update function configuration
 *  DELETE /lambda/{name}                 → delete a function
 *  PUT    /lambda/{name}/disable         → disable a function
 *  PUT    /lambda/{name}/enable          → enable a function
 *  GET    /lambda/{name}/logs            → get invocation history (paginated)
 *  POST   /lambda/invoke/{name}          → invoke function (public HTTP trigger)
 *  POST   /lambda/invoke/{name}/json     → invoke and return JSON result (authed)
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/lambda")
@RequiredArgsConstructor
@Tag(name = "MiniLambda", description = "Serverless function execution (AWS Lambda equivalent)")
public class LambdaController {

    private final FunctionManagementService managementService;
    private final LambdaExecutionService    executionService;
    private final LambdaInvocationLogRepository logRepository;
    private final com.minicloud.api.audit.AuditService auditService;

    // ─────────────── FUNCTION CRUD ──────────────────────────────────────────

    @PostMapping
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "Register a new serverless function")
    public ResponseEntity<Map<String, Object>> createFunction(
            @RequestBody CreateFunctionRequest req,
            Authentication auth) {

        UUID userId = resolveUserId(auth);

        Function fn = Function.builder()
                .name(req.name())
                .description(req.description())
                .userId(userId)
                .runtime(Function.Runtime.valueOf(req.runtime().toUpperCase()))
                .handler(req.handler())
                .s3Bucket(req.s3Bucket())
                .s3Key(req.s3Key())
                .memoryMb(req.memoryMb() > 0 ? req.memoryMb() : 128)
                .timeoutSec(req.timeoutSec() > 0 ? req.timeoutSec() : 30)
                .environmentConfig(req.environmentConfig())
                .status(Function.FunctionStatus.ACTIVE)
                .createdAt(java.time.LocalDateTime.now())
                .build();

        Function saved = managementService.create(fn);
        
        auditService.recordSuccess(auth.getName(), "Lambda", "CreateFunction", saved.getName());
        
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("message", "Function registered", "data", toResponse(saved)));
    }

    @GetMapping
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "List all serverless functions")
    public ResponseEntity<Map<String, Object>> listFunctions(Authentication auth) {
        List<FunctionResponse> list = managementService.listAll()
                .stream().map(this::toResponse).collect(Collectors.toList());
        return ResponseEntity.ok(Map.of("data", list));
    }

    @GetMapping("/{name}")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "Get function details by name")
    public ResponseEntity<Map<String, Object>> getFunction(@PathVariable String name) {
        return ResponseEntity.ok(Map.of("data", toResponse(managementService.getByName(name))));
    }

    @PutMapping("/{name}")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "Update function configuration")
    public ResponseEntity<Map<String, Object>> updateFunction(
            @PathVariable String name,
            @RequestBody UpdateFunctionRequest req) {

        Function updated = managementService.update(
                name, req.description(), req.s3Bucket(), req.s3Key(),
                req.memoryMb(), req.timeoutSec(), req.environmentConfig());
        
        auditService.recordSuccess(name, "Lambda", "UpdateFunctionConfiguration", name);
        
        return ResponseEntity.ok(Map.of("message", "Function updated", "data", toResponse(updated)));
    }

    @DeleteMapping("/{name}")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "Delete a function by name")
    public ResponseEntity<Map<String, Object>> deleteFunction(@PathVariable String name) {
        managementService.delete(name);
        auditService.recordSuccess(name, "Lambda", "DeleteFunction", name);
        return ResponseEntity.ok(Map.of("message", "Function deleted", "data", name));
    }

    @PutMapping("/{name}/disable")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "Disable a function (prevents future invocations)")
    public ResponseEntity<Map<String, Object>> disableFunction(@PathVariable String name) {
        managementService.setStatus(name, Function.FunctionStatus.DISABLED);
        return ResponseEntity.ok(Map.of("message", "Function disabled", "data", name));
    }

    @PutMapping("/{name}/enable")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "Enable a previously disabled function")
    public ResponseEntity<Map<String, Object>> enableFunction(@PathVariable String name) {
        managementService.setStatus(name, Function.FunctionStatus.ACTIVE);
        return ResponseEntity.ok(Map.of("message", "Function enabled", "data", name));
    }

    // ─────────────── INVOCATION LOGS ────────────────────────────────────────

    @GetMapping("/{name}/logs")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "Get invocation history for a function (paginated)")
    public ResponseEntity<Map<String, Object>> getLogs(
            @PathVariable String name,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Function fn = managementService.getByName(name);
        var logs = logRepository.findAllByFunctionIdOrderByTimestampDesc(
                fn.getId(), PageRequest.of(page, size));
        return ResponseEntity.ok(Map.of(
                "data", logs.getContent(),
                "total", logs.getTotalElements(),
                "page", page,
                "size", size));
    }

    // ─────────────── INVOCATION ─────────────────────────────────────────────

    /**
     * POST /lambda/invoke/{name}
     * Public HTTP trigger — no authentication required.
     * Request body is forwarded as stdin to the function process.
     * Response body is the function's stdout.
     */
    @PostMapping("/invoke/{name}")
    @Operation(summary = "Invoke a function by name (public HTTP trigger)")
    public ResponseEntity<String> invoke(
            @PathVariable String name,
            @RequestBody(required = false) String payload) {

        log.info("HTTP trigger for function '{}'", name);
        LambdaExecutionService.InvocationResult result = executionService.invoke(name, payload, null);

        HttpStatus status = result.exitCode() == 0 ? HttpStatus.OK : HttpStatus.INTERNAL_SERVER_ERROR;
        String body = result.stdout().isBlank() ? result.stderr() : result.stdout();

        return ResponseEntity.status(status)
                .header("X-Lambda-Exit-Code", String.valueOf(result.exitCode()))
                .header("X-Lambda-Duration-Ms", String.valueOf(result.durationMs()))
                .body(body);
    }

    /**
     * POST /lambda/invoke/{name}/json
     * Authenticated invocation returning full result metadata.
     */
    @PostMapping("/invoke/{name}/json")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "Invoke a function and return full JSON result (authenticated)")
    public ResponseEntity<Map<String, Object>> invokeJson(
            @PathVariable String name,
            @RequestBody(required = false) String payload,
            Authentication auth) {

        UUID userId = resolveUserId(auth);
        LambdaExecutionService.InvocationResult result = executionService.invoke(name, payload, userId);

        HttpStatus status = result.success() ? HttpStatus.OK : HttpStatus.INTERNAL_SERVER_ERROR;
        return ResponseEntity.status(status).body(Map.of(
                "function",   name,
                "stdout",     result.stdout(),
                "stderr",     result.stderr(),
                "exitCode",   result.exitCode(),
                "durationMs", result.durationMs(),
                "success",    result.success(),
                "summary",    result.summary()
        ));
    }

    // ─────────────── Helpers ────────────────────────────────────────────────

    private UUID resolveUserId(Authentication auth) {
        if (auth == null) return null;
        try {
            return UUID.fromString(auth.getName());
        } catch (IllegalArgumentException e) {
            // name is a username string — return a deterministic UUID
            return UUID.nameUUIDFromBytes(auth.getName().getBytes());
        }
    }

    private FunctionResponse toResponse(Function fn) {
        return new FunctionResponse(
                fn.getId().toString(),
                fn.getName(),
                fn.getDescription(),
                fn.getRuntime().name(),
                fn.getHandler(),
                fn.getS3Bucket(),
                fn.getS3Key(),
                fn.getMemoryMb(),
                fn.getTimeoutSec(),
                fn.getStatus().name(),
                fn.getInvocationCount(),
                fn.getLastExitCode(),
                fn.getLastInvokedAt() != null ? fn.getLastInvokedAt().toString() : null,
                fn.getCreatedAt() != null ? fn.getCreatedAt().toString() : null
        );
    }

    // ─────────────── Request / Response Records ──────────────────────────────

    public record CreateFunctionRequest(
            String name,
            String description,
            String runtime,
            String handler,
            String s3Bucket,
            String s3Key,
            int memoryMb,
            int timeoutSec,
            String environmentConfig
    ) {}

    public record UpdateFunctionRequest(
            String description,
            String s3Bucket,
            String s3Key,
            int memoryMb,
            int timeoutSec,
            String environmentConfig
    ) {}

    public record FunctionResponse(
            String id,
            String name,
            String description,
            String runtime,
            String handler,
            String s3Bucket,
            String s3Key,
            int memoryMb,
            int timeoutSec,
            String status,
            long invocationCount,
            int lastExitCode,
            String lastInvokedAt,
            String createdAt
    ) {}
}
