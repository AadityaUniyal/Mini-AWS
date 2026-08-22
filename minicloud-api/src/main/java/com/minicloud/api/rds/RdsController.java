package com.minicloud.api.rds;

import com.minicloud.api.auth.SecurityUtils;
import com.minicloud.api.auth.UserPrincipal;
import com.minicloud.api.domain.Task;
import com.minicloud.api.dto.ApiResponse;
import com.minicloud.api.dto.RdsRequest;
import com.minicloud.api.dto.RdsResponse;
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
@RequestMapping("/api/v1/rds")
@RequiredArgsConstructor
@Tag(name = "RDS Database", description = "Relational Database Service operations")
@SecurityRequirement(name = "BearerAuth")
public class RdsController {

    private final RdsService rdsService;
    private final TaskService taskService;
    private final ExecutorService asyncRdsExecutor = Executors.newFixedThreadPool(4);

    @GetMapping({"", "/instances"})
    @Operation(summary = "List all RDS database instances for current account")
    public ResponseEntity<ApiResponse<List<RdsResponse>>> listAll() {
        UserPrincipal principal = SecurityUtils.getAuthenticatedPrincipal();
        if (principal.getAccountId() != null) {
            return ResponseEntity.ok(ApiResponse.ok(rdsService.listInstancesForAccount(principal.getAccountId())));
        }
        return ResponseEntity.ok(ApiResponse.ok(rdsService.listInstances(principal.getUserId())));
    }

    @GetMapping({"/user/{userId}", "/instances/user/{userId}"})
    @Operation(summary = "List RDS instances by user ID")
    public ResponseEntity<ApiResponse<List<RdsResponse>>> list(@PathVariable UUID userId) {
        return ResponseEntity.ok(ApiResponse.ok(rdsService.listInstances(userId)));
    }

    @PostMapping
    @Operation(summary = "Launch a new RDS database instance")
    public ResponseEntity<ApiResponse<Task>> launch(
            @RequestParam(required = false) UUID userId,
            @Valid @RequestBody RdsRequest request) {

        UserPrincipal principal = SecurityUtils.getAuthenticatedPrincipal();
        UUID effectiveUserId = principal.getUserId() != null ? principal.getUserId() : userId;
        String effectiveAccountId = principal.getAccountId();

        Task task = taskService.createTask("RDS_LAUNCH", "Creating RDS database instance: " + request.getName(), effectiveUserId, effectiveAccountId);

        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            try {
                taskService.updateProgress(task.getId(), 20, "RUNNING", null);
                
                UUID sgId = request.getSecurityGroupId() != null ? UUID.fromString(request.getSecurityGroupId()) : null;
                rdsService.launchInstance(
                        effectiveUserId,
                        effectiveAccountId,
                        request.getName(),
                        request.getDatabaseName() != null ? request.getDatabaseName() : "minidb",
                        request.getMasterUsername(),
                        request.getMasterPassword(),
                        sgId
                );

                taskService.updateProgress(task.getId(), 100, "COMPLETED", null);
            } catch (Exception e) {
                log.error("Asynchronous RDS creation failed", e);
                taskService.updateProgress(task.getId(), 100, "FAILED", e.getMessage());
            }
        }, asyncRdsExecutor);

        taskService.registerActiveFuture(task.getId(), future);

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.ok("RDS launch sequence started", task));
    }

    @PostMapping("/{id}/stop")
    @Operation(summary = "Stop a running RDS database instance")
    public ResponseEntity<ApiResponse<RdsResponse>> stop(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Stopped", rdsService.stopInstance(id)));
    }

    @PostMapping("/{id}/start")
    @Operation(summary = "Start a stopped RDS database instance")
    public ResponseEntity<ApiResponse<RdsResponse>> start(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Started", rdsService.startInstance(id)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Terminate an RDS database instance")
    public ResponseEntity<ApiResponse<String>> terminate(@PathVariable UUID id) {
        rdsService.terminateInstance(id);
        return ResponseEntity.ok(ApiResponse.ok("Terminated", id.toString()));
    }
}
