package com.minicloud.api.lambda;

import com.minicloud.api.domain.Function;
import com.minicloud.api.domain.FunctionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * FunctionManagementService — Handles CRUD operations for Lambda functions.
 * Separated from the execution engine to keep responsibilities clean.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FunctionManagementService {

    private final FunctionRepository functionRepository;

    @Transactional
    public Function create(Function fn) {
        if (functionRepository.existsByName(fn.getName())) {
            throw new IllegalArgumentException("Function name already exists: " + fn.getName());
        }
        Function saved = functionRepository.save(fn);
        log.info("Function '{}' registered (runtime={})", saved.getName(), saved.getRuntime());
        return saved;
    }

    public Function getByName(String name) {
        return functionRepository.findByName(name)
                .orElseThrow(() -> new IllegalArgumentException("Function not found: " + name));
    }

    public Function getById(UUID id) {
        return functionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Function not found: " + id));
    }

    public List<Function> listByUser(UUID userId) {
        return functionRepository.findByUserId(userId);
    }

    public List<Function> listAll() {
        return functionRepository.findAll();
    }

    @Transactional
    public Function setStatus(String name, Function.FunctionStatus status) {
        Function fn = getByName(name);
        fn.setStatus(status);
        return functionRepository.save(fn);
    }

    @Transactional
    public void delete(String name) {
        Function fn = getByName(name);
        functionRepository.delete(fn);
        log.info("Function '{}' deleted", name);
    }

    @Transactional
    public Function update(String name, String description, String s3Bucket, String s3Key,
                            int memoryMb, int timeoutSec, String environmentConfig) {
        Function fn = getByName(name);
        if (description != null) fn.setDescription(description);
        if (s3Bucket != null) fn.setS3Bucket(s3Bucket);
        if (s3Key != null) fn.setS3Key(s3Key);
        if (memoryMb > 0) fn.setMemoryMb(memoryMb);
        if (timeoutSec > 0) fn.setTimeoutSec(timeoutSec);
        if (environmentConfig != null) fn.setEnvironmentConfig(environmentConfig);
        return functionRepository.save(fn);
    }
}
