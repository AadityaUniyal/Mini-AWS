package com.minicloud.api.service;

import com.minicloud.api.domain.Task;
import com.minicloud.api.domain.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;
    private final ApplicationEventPublisher eventPublisher;
    
    // Tracks active futures for task cancellation support
    private final ConcurrentHashMap<UUID, Future<?>> activeFutures = new ConcurrentHashMap<>();

    @Transactional(readOnly = true)
    public List<Task> getAllTasks() {
        return taskRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Task> getTasksByUserId(UUID userId) {
        return taskRepository.findByUserId(userId);
    }

    @Transactional(readOnly = true)
    public List<Task> getTasksByAccountId(String accountId) {
        return taskRepository.findByAccountId(accountId);
    }

    @Transactional
    public Task createTask(String type, String description, UUID userId, String accountId) {
        log.info("Creating background task of type: {}", type);
        Task task = Task.builder()
                .type(type)
                .status("PENDING")
                .progress(0)
                .description(description)
                .userId(userId)
                .accountId(accountId)
                .startTime(LocalDateTime.now())
                .build();
        
        Task saved = taskRepository.save(task);
        publishTaskUpdate(saved);
        return saved;
    }

    @Transactional
    public void updateProgress(UUID taskId, int progress, String status, String errorDetails) {
        taskRepository.findById(taskId).ifPresent(task -> {
            task.setProgress(progress);
            if (status != null) {
                task.setStatus(status);
            }
            if (errorDetails != null) {
                task.setErrorDetails(errorDetails);
            }
            if ("COMPLETED".equals(status) || "FAILED".equals(status) || "CANCELLED".equals(status)) {
                task.setEndTime(LocalDateTime.now());
                activeFutures.remove(taskId);
            }
            Task updated = taskRepository.save(task);
            publishTaskUpdate(updated);
        });
    }

    @Transactional
    public void cancelTask(UUID taskId) {
        Future<?> future = activeFutures.get(taskId);
        if (future != null) {
            future.cancel(true);
        }
        updateProgress(taskId, 100, "CANCELLED", "Task was cancelled by the user");
    }

    public void registerActiveFuture(UUID taskId, Future<?> future) {
        activeFutures.put(taskId, future);
    }

    private void publishTaskUpdate(Task task) {
        eventPublisher.publishEvent(task);
    }
}
