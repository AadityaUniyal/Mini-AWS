package com.minicloud.api.monitoring;

import com.minicloud.api.domain.Task;
import com.minicloud.api.dto.ApiResponse;
import com.minicloud.api.service.TaskService;
import io.swagger.v3.oas.annotations.Operation;
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
public class TaskController {

    private final TaskService taskService;

    @GetMapping
    @Operation(summary = "Get all background tasks")
    public ResponseEntity<ApiResponse<List<Task>>> getAllTasks() {
        return ResponseEntity.ok(ApiResponse.ok(taskService.getAllTasks()));
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "Get background tasks for a user")
    public ResponseEntity<ApiResponse<List<Task>>> getTasksByUserId(@PathVariable UUID userId) {
        return ResponseEntity.ok(ApiResponse.ok(taskService.getTasksByUserId(userId)));
    }

    @PostMapping("/{taskId}/cancel")
    @Operation(summary = "Cancel a running background task")
    public ResponseEntity<ApiResponse<String>> cancelTask(@PathVariable UUID taskId) {
        taskService.cancelTask(taskId);
        return ResponseEntity.ok(ApiResponse.ok("Task cancellation requested", taskId.toString()));
    }
}
