package com.minicloud.api.lambda;

import com.minicloud.api.auth.SecurityUtils;
import com.minicloud.api.auth.UserPrincipal;
import com.minicloud.api.domain.Function;
import com.minicloud.api.domain.LambdaInvocationLogRepository;
import com.minicloud.api.domain.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

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
    private final com.minicloud.api.storage.S3TriggerService triggerService;
    private final S3LambdaTriggerRepository triggerRepository;
    private final UserRepository userRepository;

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
            String userId,
            String accountId,
            String runtime,
            String handler,
            String s3Bucket,
            String s3Key,
            int memoryMb,
            int timeoutSec,
            String status,
            long invocationCount,
            long errorCount,
            String lastInvokedAt,
            String createdAt
    ) {}

    // ─────────────── FUNCTION CRUD ──────────────────────────────────────────

    @PostMapping
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "Register a new serverless function")
    public ResponseEntity<Map<String, Object>> createFunction(
            @RequestBody CreateFunctionRequest req) {

        UserPrincipal principal = SecurityUtils.getAuthenticatedPrincipal();
        UUID userId = principal.getUserId();
        String accountId = principal.getAccountId();

        Function.Runtime runtime;
        try {
            runtime = Function.Runtime.valueOf(req.runtime().toUpperCase());
        } catch (Exception e) {
            runtime = Function.Runtime.PYTHON;
        }

        Function fn = Function.builder()
                .name(req.name())
                .description(req.description())
                .userId(userId)
                .accountId(accountId)
                .runtime(runtime)
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
        auditService.recordSuccess(principal.getUsername(), "Lambda", "CreateFunction", saved.getName());
        
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("message", "Function registered", "data", toResponse(saved)));
    }

    @GetMapping
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "List all serverless functions for current account")
    public ResponseEntity<Map<String, Object>> listFunctions() {
        UserPrincipal principal = SecurityUtils.getAuthenticatedPrincipal();
        List<FunctionResponse> list = managementService.listAll()
                .stream()
                .filter(f -> principal.getAccountId() == null || principal.getAccountId().equals(f.getAccountId()))
                .map(this::toResponse).collect(Collectors.toList());
        return ResponseEntity.ok(Map.of("data", list));
    }

    @GetMapping("/{name}")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "Get function details by name")
    public ResponseEntity<Map<String, Object>> getFunction(@PathVariable String name) {
        Function fn = managementService.getByName(name);
        SecurityUtils.validateAccountOwnership(fn.getAccountId());
        return ResponseEntity.ok(Map.of("data", toResponse(fn)));
    }

    @PutMapping("/{name}")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "Update function configuration")
    public ResponseEntity<Map<String, Object>> updateFunction(
            @PathVariable String name,
            @RequestBody UpdateFunctionRequest req) {

        Function fn = managementService.getByName(name);
        SecurityUtils.validateAccountOwnership(fn.getAccountId());

        Function updated = managementService.update(
                name, req.description(), req.s3Bucket(), req.s3Key(),
                req.memoryMb(), req.timeoutSec(), req.environmentConfig());
        
        auditService.recordSuccess(SecurityUtils.getAuthenticatedUsername(), "Lambda", "UpdateFunctionConfiguration", name);
        return ResponseEntity.ok(Map.of("message", "Function updated", "data", toResponse(updated)));
    }

    @DeleteMapping("/{name}")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "Delete a function by name")
    public ResponseEntity<Map<String, Object>> deleteFunction(@PathVariable String name) {
        Function fn = managementService.getByName(name);
        SecurityUtils.validateAccountOwnership(fn.getAccountId());
        managementService.delete(name);
        auditService.recordSuccess(SecurityUtils.getAuthenticatedUsername(), "Lambda", "DeleteFunction", name);
        return ResponseEntity.ok(Map.of("message", "Function deleted", "data", name));
    }

    @PutMapping("/{name}/disable")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "Disable a function (prevents future invocations)")
    public ResponseEntity<Map<String, Object>> disableFunction(@PathVariable String name) {
        Function fn = managementService.getByName(name);
        SecurityUtils.validateAccountOwnership(fn.getAccountId());
        managementService.setStatus(name, Function.FunctionStatus.DISABLED);
        return ResponseEntity.ok(Map.of("message", "Function disabled", "data", name));
    }

    @PutMapping("/{name}/enable")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "Enable a previously disabled function")
    public ResponseEntity<Map<String, Object>> enableFunction(@PathVariable String name) {
        Function fn = managementService.getByName(name);
        SecurityUtils.validateAccountOwnership(fn.getAccountId());
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
        SecurityUtils.validateAccountOwnership(fn.getAccountId());
        var logs = logRepository.findAllByFunctionIdOrderByTimestampDesc(
                fn.getId(), PageRequest.of(page, size));
        return ResponseEntity.ok(Map.of(
                "data", logs.getContent(),
                "total", logs.getTotalElements(),
                "page", page,
                "size", size));
    }

    // ─────────────── INVOCATION ─────────────────────────────────────────────

    @PostMapping("/invoke/{name}")
    @Operation(summary = "Invoke a function by name (public HTTP trigger)")
    public ResponseEntity<String> invoke(
            @PathVariable String name,
            @RequestBody(required = false) String payload) {

        if (payload != null && payload.length() > 1_048_576) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body("Payload exceeds 1MB limit");
        }

        log.info("HTTP trigger for function '{}'", name);
        LambdaExecutionService.InvocationResult result = executionService.invoke(name, payload, null);

        HttpStatus status = result.exitCode() == 0 ? HttpStatus.OK : HttpStatus.INTERNAL_SERVER_ERROR;
        String body = result.stdout().isBlank() ? result.stderr() : result.stdout();

        return ResponseEntity.status(status)
                .header("X-Lambda-Exit-Code", String.valueOf(result.exitCode()))
                .header("X-Lambda-Duration-Ms", String.valueOf(result.durationMs()))
                .body(body);
    }

    @PostMapping("/invoke/{name}/json")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "Invoke a function and return full JSON result (authenticated)")
    public ResponseEntity<Map<String, Object>> invokeJson(
            @PathVariable String name,
            @RequestBody(required = false) String payload) {

        UserPrincipal principal = SecurityUtils.getAuthenticatedPrincipal();
        Function fn = managementService.getByName(name);
        SecurityUtils.validateAccountOwnership(fn.getAccountId());

        LambdaExecutionService.InvocationResult result = executionService.invoke(name, payload, principal.getUserId());

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

    // ─────────────── S3 LAMBDA TRIGGER ALIASES ─────────────────────────────

    @PostMapping("/triggers")
    @Operation(summary = "Register an S3 to Lambda event trigger")
    public ResponseEntity<com.minicloud.api.dto.ApiResponse<TriggerResponse>> createLambdaTrigger(
            @RequestBody CreateTriggerRequest req) {
        TriggerResponse resp = triggerService.createTrigger(req);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(com.minicloud.api.dto.ApiResponse.ok("Trigger created", resp));
    }

    @GetMapping("/triggers")
    @Operation(summary = "List S3 to Lambda triggers with optional bucket filter")
    public ResponseEntity<com.minicloud.api.dto.ApiResponse<List<TriggerResponse>>> listLambdaTriggers(
            @RequestParam(required = false) String bucketName) {
        List<TriggerResponse> list = triggerService.listTriggers(bucketName);
        return ResponseEntity.ok(com.minicloud.api.dto.ApiResponse.ok(list));
    }

    @DeleteMapping("/triggers/{id}")
    @Operation(summary = "Delete an S3 to Lambda trigger")
    public ResponseEntity<com.minicloud.api.dto.ApiResponse<String>> deleteLambdaTrigger(@PathVariable UUID id) {
        triggerService.deleteTrigger(id);
        return ResponseEntity.ok(com.minicloud.api.dto.ApiResponse.ok("Trigger deleted", id.toString()));
    }

    private FunctionResponse toResponse(Function fn) {
        return new FunctionResponse(
                fn.getId().toString(),
                fn.getName(),
                fn.getDescription(),
                fn.getUserId() != null ? fn.getUserId().toString() : null,
                fn.getAccountId(),
                fn.getRuntime().name(),
                fn.getHandler(),
                fn.getS3Bucket(),
                fn.getS3Key(),
                fn.getMemoryMb(),
                fn.getTimeoutSec(),
                fn.getStatus().name(),
                fn.getInvocationCount(),
                fn.getErrorCount(),
                fn.getLastInvokedAt() != null ? fn.getLastInvokedAt().toString() : null,
                fn.getCreatedAt() != null ? fn.getCreatedAt().toString() : null
        );
    }
}
