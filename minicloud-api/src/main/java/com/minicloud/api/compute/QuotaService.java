package com.minicloud.api.compute;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * QuotaService — Manages resource quotas for the laptop-optimized monolith.
 * Ensures that resource allocation (CPU, RAM, Storage) stays within limits.
 */
@Slf4j
@Service
public class QuotaService {

    // Default limits for a laptop environment
    private static final int MAX_TOTAL_CPU_CORES = 4;
    private static final int MAX_TOTAL_RAM_MB     = 4096;
    private static final long MAX_STORAGE_MB     = 10240; // 10 GB

    private final Map<UUID, ResourceUsage> userUsage = new ConcurrentHashMap<>();

    public boolean canAllocate(UUID userId, int cpu, int ram, long storageMb) {
        ResourceUsage usage = userUsage.getOrDefault(userId, new ResourceUsage());
        
        if (usage.cpu + cpu > MAX_TOTAL_CPU_CORES) {
            log.warn("Quota exceeded for user {}: CPU limit reached", userId);
            return false;
        }
        if (usage.ram + ram > MAX_TOTAL_RAM_MB) {
            log.warn("Quota exceeded for user {}: RAM limit reached", userId);
            return false;
        }
        if (usage.storageMb + storageMb > MAX_STORAGE_MB) {
            log.warn("Quota exceeded for user {}: Storage limit reached", userId);
            return false;
        }
        
        return true;
    }

    public void allocate(UUID userId, int cpu, int ram, long storageMb) {
        userUsage.computeIfAbsent(userId, k -> new ResourceUsage()).add(cpu, ram, storageMb);
        log.info("Resources allocated to user {}: CPU={}, RAM={}MB, Storage={}MB", userId, cpu, ram, storageMb);
    }

    public void release(UUID userId, int cpu, int ram, long storageMb) {
        ResourceUsage usage = userUsage.get(userId);
        if (usage != null) {
            usage.subtract(cpu, ram, storageMb);
            log.info("Resources released for user {}: CPU={}, RAM={}MB, Storage={}MB", userId, cpu, ram, storageMb);
        }
    }

    private static class ResourceUsage {
        int cpu = 0;
        int ram = 0;
        long storageMb = 0;

        synchronized void add(int c, int r, long s) {
            cpu += c;
            ram += r;
            storageMb += s;
        }

        synchronized void subtract(int c, int r, long s) {
            cpu = Math.max(0, cpu - c);
            ram = Math.max(0, ram - r);
            storageMb = Math.max(0, storageMb - s);
        }
    }
}
