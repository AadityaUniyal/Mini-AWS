package com.minicloud.api.compute;

import com.minicloud.api.dto.ApiResponse;
import com.minicloud.api.dto.InstanceResponse;
import com.minicloud.api.domain.Task;
import com.minicloud.api.service.TaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Slf4j
@RestController
@RequestMapping("/api/v1/compute/instances")
@RequiredArgsConstructor
@Tag(name = "EC2 Compute", description = "Virtual servers management")
public class ComputeController {

    private final ComputeService computeService;
    private final TaskService taskService;

    @GetMapping
    @Operation(summary = "List all active instances")
    public ResponseEntity<ApiResponse<List<InstanceResponse>>> listAll() {
        return ResponseEntity.ok(ApiResponse.ok(computeService.getActiveInstances()));
    }

    @PostMapping("/launch")
    @Operation(summary = "Launch a new virtual instance")
    public ResponseEntity<ApiResponse<Task>> launch(
            @RequestParam UUID userId,
            @RequestParam String accountId,
            @RequestParam(required = false) UUID subnetId,
            @jakarta.validation.Valid @RequestBody com.minicloud.api.dto.InstanceRequest request) {

        String name = request.getName();
        String type = request.getType();
        UUID securityGroupId = request.getSecurityGroupId() != null ? UUID.fromString(request.getSecurityGroupId()) : null;
        String command = request.getCommand();

        // Create background task
        Task task = taskService.createTask("INSTANCE_LAUNCH", "Launching virtual instance: " + name, userId, accountId);

        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            try {
                taskService.updateProgress(task.getId(), 20, "RUNNING", null);
                Thread.sleep(1000);

                taskService.updateProgress(task.getId(), 60, "RUNNING", null);
                computeService.launchInstance(userId, accountId, name, type, subnetId, securityGroupId, command);
                Thread.sleep(1000);

                taskService.updateProgress(task.getId(), 100, "COMPLETED", null);
            } catch (Exception e) {
                log.error("Asynchronous instance launch failed", e);
                taskService.updateProgress(task.getId(), 100, "FAILED", e.getMessage());
            }
        });

        taskService.registerActiveFuture(task.getId(), future);

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.ok("Instance launching sequence started", task));
    }

    @GetMapping("/account/{accountId}")
    @Operation(summary = "List all instances for an account")
    public ResponseEntity<ApiResponse<List<InstanceResponse>>> list(@PathVariable String accountId) {
        return ResponseEntity.ok(ApiResponse.ok(computeService.getInstancesForAccount(accountId)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<InstanceResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(computeService.getInstance(id)));
    }

    @PostMapping("/{id}/stop")
    public ResponseEntity<ApiResponse<InstanceResponse>> stop(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Stopped", computeService.stopInstance(id)));
    }

    @PostMapping("/{id}/start")
    public ResponseEntity<ApiResponse<InstanceResponse>> start(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Started", computeService.startInstance(id)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<String>> terminate(@PathVariable UUID id) {
        computeService.terminateInstance(id);
        return ResponseEntity.ok(ApiResponse.ok("Terminated", id.toString()));
    }
}
