package com.minicloud.api.monitoring;

import com.minicloud.api.auth.SecurityUtils;
import com.minicloud.api.auth.UserPrincipal;
import com.minicloud.api.domain.Task;
import com.minicloud.api.dto.ApiResponse;
import com.minicloud.api.service.TaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tasks")
@RequiredArgsConstructor
@Tag(name = "Task Manager", description = "Monitor and cancel background cloud operations")
@SecurityRequirement(name = "BearerAuth")
public class TaskController {

    private final TaskService taskService;

    @GetMapping
    @Operation(summary = "Get background tasks for the authenticated account")
    public ResponseEntity<ApiResponse<List<Task>>> getAllTasks() {
        try {
            UserPrincipal principal = SecurityUtils.getAuthenticatedPrincipal();
            if (principal.getAccountId() != null && !principal.isRoot()) {
                return ResponseEntity.ok(ApiResponse.ok(taskService.getTasksByAccountId(principal.getAccountId())));
            }
        } catch (Exception ignored) {}
        return ResponseEntity.ok(ApiResponse.ok(taskService.getAllTasks()));
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "Get background tasks for a user")
    public ResponseEntity<ApiResponse<List<Task>>> getTasksByUserId(@PathVariable UUID userId) {
        return ResponseEntity.ok(ApiResponse.ok(taskService.getTasksByUserId(userId)));
    }

    @GetMapping("/account/{accountId}")
    @Operation(summary = "Get background tasks for an account")
    public ResponseEntity<ApiResponse<List<Task>>> getTasksByAccountId(@PathVariable String accountId) {
        SecurityUtils.validateAccountOwnership(accountId);
        return ResponseEntity.ok(ApiResponse.ok(taskService.getTasksByAccountId(accountId)));
    }

    @PostMapping("/{taskId}/cancel")
    @Operation(summary = "Cancel a running background task")
    public ResponseEntity<ApiResponse<String>> cancelTask(@PathVariable UUID taskId) {
        taskService.cancelTask(taskId);
        return ResponseEntity.ok(ApiResponse.ok("Task cancellation requested", taskId.toString()));
    }
}
