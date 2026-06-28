package com.minicloud.api.service;

import com.minicloud.api.domain.Function;
import com.minicloud.api.domain.FunctionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Service for managing Lambda-like serverless functions.
 * All methods are transactional for proper database session management.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LambdaService {

    private final FunctionRepository functionRepository;

    @Transactional(readOnly = true)
    public List<Function> getAllFunctions() {
        log.info("[LambdaService] Loading all functions from database");
        List<Function> functions = functionRepository.findAll();
        log.info("[LambdaService] Found {} functions", functions.size());
        return functions;
    }

    @Transactional(readOnly = true)
    public List<Function> getFunctionsByAccountId(String accountId) {
        log.info("[LambdaService] Loading functions for account: {}", accountId);
        return functionRepository.findByAccountId(accountId);
    }

    @Transactional(readOnly = true)
    public Function getFunctionById(UUID id) {
        log.info("[LambdaService] Loading function by ID: {}", id);
        return functionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Function not found: " + id));
    }

    @Transactional(readOnly = true)
    public Function getFunctionByName(String name) {
        log.info("[LambdaService] Loading function by name: {}", name);
        return functionRepository.findByName(name)
                .orElseThrow(() -> new RuntimeException("Function not found: " + name));
    }

    @Transactional
    public Function createFunction(String name, UUID ownerId, String accountId, 
                                   Function.Runtime runtime, String handler, 
                                   int memoryMb, int timeoutSec) {
        log.info("[LambdaService] Creating function: {} for account: {}", name, accountId);
        
        if (functionRepository.existsByName(name)) {
            throw new RuntimeException("Function already exists: " + name);
        }

        Function function = Function.builder()
                .name(name)
                .userId(ownerId)
                .accountId(accountId)
                .runtime(runtime != null ? runtime : Function.Runtime.PYTHON)
                .handler(handler != null ? handler : "index.handler")
                .memoryMb(memoryMb > 0 ? memoryMb : 128)
                .timeoutSec(timeoutSec > 0 ? timeoutSec : 30)
                .status(Function.FunctionStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .invocationCount(0)
                .build();

        Function saved = functionRepository.save(function);
        log.info("[LambdaService] Function created successfully: {}", saved.getId());
        return saved;
    }

    @Transactional
    public Function invokeFunction(UUID functionId) {
        log.info("[LambdaService] Invoking function: {}", functionId);
        
        Function function = functionRepository.findById(functionId)
                .orElseThrow(() -> new RuntimeException("Function not found: " + functionId));

        if (function.getStatus() != Function.FunctionStatus.ACTIVE) {
            throw new RuntimeException("Function is not active: " + function.getStatus());
        }

        // Simulate invocation
        function.setLastInvokedAt(LocalDateTime.now());
        function.setInvocationCount(function.getInvocationCount() + 1);
        function.setLastExitCode(0); // Success

        Function updated = functionRepository.save(function);
        log.info("[LambdaService] Function invoked successfully: {} (total invocations: {})", 
                 functionId, updated.getInvocationCount());
        return updated;
    }

    @Transactional
    public void deleteFunction(UUID functionId) {
        log.info("[LambdaService] Deleting function: {}", functionId);
        
        Function function = functionRepository.findById(functionId)
                .orElseThrow(() -> new RuntimeException("Function not found: " + functionId));

        functionRepository.delete(function);
        log.info("[LambdaService] Function deleted successfully: {}", functionId);
    }

    @Transactional
    public Function updateFunctionStatus(UUID functionId, Function.FunctionStatus status) {
        log.info("[LambdaService] Updating function status: {} to {}", functionId, status);
        
        Function function = functionRepository.findById(functionId)
                .orElseThrow(() -> new RuntimeException("Function not found: " + functionId));

        function.setStatus(status);

        Function updated = functionRepository.save(function);
        log.info("[LambdaService] Function status updated successfully: {}", functionId);
        return updated;
    }
}
