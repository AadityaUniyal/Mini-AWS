package com.minicloud.api.service;

import com.minicloud.api.domain.Instance;
import com.minicloud.api.domain.InstanceRepository;
import com.minicloud.api.domain.InstanceState;
import com.minicloud.api.domain.InstanceType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Service for managing EC2-like compute instances.
 * All methods are transactional for proper database session management.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InstanceService {

    private final InstanceRepository instanceRepository;

    @Transactional(readOnly = true)
    public List<Instance> getAllInstances() {
        log.info("[InstanceService] Loading all instances from database");
        List<Instance> instances = instanceRepository.findAll();
        log.info("[InstanceService] Found {} instances", instances.size());
        return instances;
    }

    @Transactional(readOnly = true)
    public List<Instance> getInstancesByAccountId(String accountId) {
        log.info("[InstanceService] Loading instances for account: {}", accountId);
        return instanceRepository.findByAccountId(accountId);
    }

    @Transactional(readOnly = true)
    public List<Instance> getActiveInstances() {
        log.info("[InstanceService] Loading active instances (not terminated)");
        return instanceRepository.findByStateNot(InstanceState.TERMINATED);
    }

    @Transactional(readOnly = true)
    public Instance getInstanceById(UUID id) {
        log.info("[InstanceService] Loading instance by ID: {}", id);
        return instanceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Instance not found: " + id));
    }

    @Transactional
    public Instance launchInstance(String name, UUID ownerId, String accountId, InstanceType type) {
        log.info("[InstanceService] Launching instance: {} for account: {}", name, accountId);

        Instance instance = Instance.builder()
                .name(name)
                .userId(ownerId)
                .accountId(accountId)
                .type(type != null ? type : InstanceType.T2_MICRO)
                .state(InstanceState.RUNNING)
                .privateIp("10.0." + (int)(Math.random() * 255) + "." + (int)(Math.random() * 255))
                .publicIp("54." + (int)(Math.random() * 255) + "." + (int)(Math.random() * 255) + "." + (int)(Math.random() * 255))
                .launchedAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .pid((long)(Math.random() * 100000))
                .build();

        Instance saved = instanceRepository.save(instance);
        log.info("[InstanceService] Instance launched successfully: {}", saved.getId());
        return saved;
    }

    @Transactional
    public Instance startInstance(UUID instanceId) {
        log.info("[InstanceService] Starting instance: {}", instanceId);
        
        Instance instance = instanceRepository.findById(instanceId)
                .orElseThrow(() -> new RuntimeException("Instance not found: " + instanceId));

        if (instance.getState() == InstanceState.RUNNING) {
            throw new RuntimeException("Instance is already running");
        }

        if (instance.getState() == InstanceState.TERMINATED) {
            throw new RuntimeException("Cannot start terminated instance");
        }

        instance.setState(InstanceState.RUNNING);
        instance.setLaunchedAt(LocalDateTime.now());
        instance.setPid((long)(Math.random() * 100000));

        Instance updated = instanceRepository.save(instance);
        log.info("[InstanceService] Instance started successfully: {}", instanceId);
        return updated;
    }

    @Transactional
    public Instance stopInstance(UUID instanceId) {
        log.info("[InstanceService] Stopping instance: {}", instanceId);
        
        Instance instance = instanceRepository.findById(instanceId)
                .orElseThrow(() -> new RuntimeException("Instance not found: " + instanceId));

        if (instance.getState() == InstanceState.STOPPED) {
            throw new RuntimeException("Instance is already stopped");
        }

        if (instance.getState() == InstanceState.TERMINATED) {
            throw new RuntimeException("Cannot stop terminated instance");
        }

        instance.setState(InstanceState.STOPPED);
        instance.setPid(null);

        Instance updated = instanceRepository.save(instance);
        log.info("[InstanceService] Instance stopped successfully: {}", instanceId);
        return updated;
    }

    @Transactional
    public void terminateInstance(UUID instanceId) {
        log.info("[InstanceService] Terminating instance: {}", instanceId);
        
        Instance instance = instanceRepository.findById(instanceId)
                .orElseThrow(() -> new RuntimeException("Instance not found: " + instanceId));

        if (instance.getState() == InstanceState.TERMINATED) {
            throw new RuntimeException("Instance is already terminated");
        }

        instance.setState(InstanceState.TERMINATED);
        instance.setPid(null);

        instanceRepository.save(instance);
        log.info("[InstanceService] Instance terminated successfully: {}", instanceId);
    }
}
