package com.minicloud.api.compute;

import com.minicloud.api.auth.SecurityUtils;
import com.minicloud.api.auth.UserPrincipal;
import com.minicloud.api.domain.Task;
import com.minicloud.api.dto.*;
import com.minicloud.api.service.TaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@RestController
@RequestMapping("/api/v1/compute/instances")
@RequiredArgsConstructor
@Tag(name = "EC2 Compute", description = "Virtual servers management")
@SecurityRequirement(name = "BearerAuth")
public class ComputeController {

    private final ComputeService computeService;
    private final TaskService taskService;
    private final ExecutorService asyncComputeExecutor = Executors.newFixedThreadPool(8);

    @GetMapping
    @Operation(summary = "List all active instances for the authenticated account")
    public ResponseEntity<ApiResponse<List<InstanceResponse>>> listAll() {
        return ResponseEntity.ok(ApiResponse.ok(computeService.getActiveInstances()));
    }

    @PostMapping("/launch")
    @Operation(summary = "Launch a new virtual instance")
    public ResponseEntity<ApiResponse<Task>> launch(
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) String accountId,
            @RequestParam(required = false) UUID subnetId,
            @Valid @RequestBody InstanceRequest request) {

        UserPrincipal principal = SecurityUtils.getAuthenticatedPrincipal();
        UUID effectiveUserId = principal.getUserId() != null ? principal.getUserId() : userId;
        String effectiveAccountId = principal.getAccountId() != null ? principal.getAccountId() : accountId;

        String name = request.getName();
        String type = request.getType();
        UUID securityGroupId = request.getSecurityGroupId() != null ? UUID.fromString(request.getSecurityGroupId()) : null;
        String command = request.getCommand();

        // Create background task
        Task task = taskService.createTask("INSTANCE_LAUNCH", "Launching virtual instance: " + name, effectiveUserId, effectiveAccountId);

        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            try {
                taskService.updateProgress(task.getId(), 20, "RUNNING", null);
                computeService.launchInstance(effectiveUserId, effectiveAccountId, name, type, subnetId, securityGroupId, command);
                taskService.updateProgress(task.getId(), 100, "COMPLETED", null);
            } catch (Exception e) {
                log.error("Asynchronous instance launch failed", e);
                taskService.updateProgress(task.getId(), 100, "FAILED", e.getMessage());
            }
        }, asyncComputeExecutor);

        taskService.registerActiveFuture(task.getId(), future);

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.ok("Instance launching sequence started", task));
    }

    @GetMapping("/account/{accountId}")
    @Operation(summary = "List all instances for an account")
    public ResponseEntity<ApiResponse<List<InstanceResponse>>> list(@PathVariable String accountId) {
        SecurityUtils.validateAccountOwnership(accountId);
        return ResponseEntity.ok(ApiResponse.ok(computeService.getInstancesForAccount(accountId)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an instance by ID")
    public ResponseEntity<ApiResponse<InstanceResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(computeService.getInstance(id)));
    }

    @PostMapping("/{id}/stop")
    @Operation(summary = "Stop a running instance")
    public ResponseEntity<ApiResponse<InstanceResponse>> stop(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Stopped", computeService.stopInstance(id)));
    }

    @PostMapping("/{id}/start")
    @Operation(summary = "Start a stopped instance")
    public ResponseEntity<ApiResponse<InstanceResponse>> start(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Started", computeService.startInstance(id)));
    }

    @PostMapping("/{id}/exec")
    @Operation(summary = "Execute command inside a running instance")
    public ResponseEntity<ApiResponse<ExecResponse>> exec(
            @PathVariable UUID id,
            @Valid @RequestBody ExecRequest request) {
        ExecResponse response = computeService.execCommand(id, request);
        return ResponseEntity.ok(ApiResponse.ok("Command executed", response));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Terminate an instance")
    public ResponseEntity<ApiResponse<String>> terminate(@PathVariable UUID id) {
        computeService.terminateInstance(id);
        return ResponseEntity.ok(ApiResponse.ok("Terminated", id.toString()));
    }
}
