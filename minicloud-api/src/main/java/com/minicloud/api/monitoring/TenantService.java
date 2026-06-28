package com.minicloud.api.monitoring;

import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * TenantService — Multi-tenancy management with resource quota enforcement.
 *
 * Provides:
 *  - Tenant registration and lifecycle management
 *  - Per-tenant resource quotas (compute instances, storage, lambda functions, DBs)
 *  - Usage tracking and quota enforcement
 *  - Quota exceeded alerts and notifications
 *  - Network isolation via VLAN tags (logical isolation, not physical)
 *
 * Resource quotas per tier:
 *  FREE:       2 instances, 10GB storage, 5 functions, 1 DB
 *  STARTER:    10 instances, 100GB storage, 25 functions, 5 DBs
 *  BUSINESS:   50 instances, 1TB storage, 100 functions, 20 DBs
 *  ENTERPRISE: 500 instances, 10TB storage, unlimited functions, 100 DBs
 */
@Slf4j
@Service
public class TenantService {

    // ── Tenant Store ─────────────────────────────────────────────────────────

    private final ConcurrentHashMap<String, Tenant> tenants = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, TenantUsage> usages = new ConcurrentHashMap<>();

    // ── Tenant Management ─────────────────────────────────────────────────────

    /**
     * Registers a new tenant and initializes their quota + usage tracking.
     */
    public Tenant registerTenant(String tenantId, String name, TenantTier tier) {
        if (tenants.containsKey(tenantId)) {
            throw new IllegalArgumentException("Tenant already exists: " + tenantId);
        }
        Tenant tenant = Tenant.builder()
                .tenantId(tenantId)
                .name(name)
                .tier(tier)
                .status(TenantStatus.ACTIVE)
                .vlanTag(generateVlanTag(tenantId))
                .createdAt(LocalDateTime.now())
                .quota(TenantQuota.forTier(tier))
                .build();

        tenants.put(tenantId, tenant);
        usages.put(tenantId, new TenantUsage());
        log.info("Tenant '{}' ({}) registered with tier {}", name, tenantId, tier);
        return tenant;
    }

    public Tenant getTenant(String tenantId) {
        Tenant t = tenants.get(tenantId);
        if (t == null) throw new IllegalArgumentException("Tenant not found: " + tenantId);
        return t;
    }

    public List<Tenant> listTenants() {
        return new ArrayList<>(tenants.values());
    }

    public void suspendTenant(String tenantId) {
        getTenant(tenantId).setStatus(TenantStatus.SUSPENDED);
        log.warn("Tenant '{}' suspended", tenantId);
    }

    public void activateTenant(String tenantId) {
        getTenant(tenantId).setStatus(TenantStatus.ACTIVE);
    }

    public void upgradeTier(String tenantId, TenantTier newTier) {
        Tenant tenant = getTenant(tenantId);
        tenant.setTier(newTier);
        tenant.setQuota(TenantQuota.forTier(newTier));
        log.info("Tenant '{}' upgraded to {}", tenantId, newTier);
    }

    // ── Quota Enforcement ─────────────────────────────────────────────────────

    /**
     * Checks if a tenant can allocate a given resource.
     * Throws QuotaExceededException if limit is reached.
     */
    public void checkQuota(String tenantId, ResourceType resource, long requestedAmount) {
        Tenant tenant = getTenant(tenantId);
        if (tenant.getStatus() == TenantStatus.SUSPENDED) {
            throw new QuotaExceededException(tenantId, resource, 0, 0,
                    "Tenant account is suspended");
        }

        TenantUsage usage = usages.get(tenantId);
        TenantQuota quota = tenant.getQuota();

        long current = usage.get(resource);
        long limit = quota.getLimit(resource);

        if (limit >= 0 && current + requestedAmount > limit) {
            throw new QuotaExceededException(tenantId, resource, current, limit,
                    String.format("Quota exceeded for %s: used=%d, limit=%d, requested=%d",
                            resource, current, limit, requestedAmount));
        }
    }

    /**
     * Records resource usage for a tenant after allocation.
     */
    public void recordUsage(String tenantId, ResourceType resource, long amount) {
        usages.computeIfAbsent(tenantId, k -> new TenantUsage()).increment(resource, amount);
    }

    /**
     * Releases resources back to the tenant's available quota.
     */
    public void releaseUsage(String tenantId, ResourceType resource, long amount) {
        usages.computeIfAbsent(tenantId, k -> new TenantUsage()).decrement(resource, amount);
    }

    /**
     * Get current usage summary for a tenant.
     */
    public TenantUsageSummary getUsageSummary(String tenantId) {
        Tenant tenant = getTenant(tenantId);
        TenantUsage usage = usages.getOrDefault(tenantId, new TenantUsage());
        TenantQuota quota = tenant.getQuota();

        return TenantUsageSummary.builder()
                .tenantId(tenantId)
                .tenantName(tenant.getName())
                .tier(tenant.getTier())
                .computeInstances(usage.get(ResourceType.COMPUTE_INSTANCES))
                .computeLimit(quota.getLimit(ResourceType.COMPUTE_INSTANCES))
                .storageGb(usage.get(ResourceType.STORAGE_GB))
                .storageLimit(quota.getLimit(ResourceType.STORAGE_GB))
                .lambdaFunctions(usage.get(ResourceType.LAMBDA_FUNCTIONS))
                .lambdaLimit(quota.getLimit(ResourceType.LAMBDA_FUNCTIONS))
                .databases(usage.get(ResourceType.DATABASES))
                .databaseLimit(quota.getLimit(ResourceType.DATABASES))
                .build();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private int generateVlanTag(String tenantId) {
        // Derive a stable VLAN tag from tenant ID hash (1-4094 valid VLAN range)
        return (Math.abs(tenantId.hashCode()) % 4094) + 1;
    }

    // ── Domain Classes ────────────────────────────────────────────────────────

    public enum TenantTier { FREE, STARTER, BUSINESS, ENTERPRISE }

    public enum TenantStatus { ACTIVE, SUSPENDED, TERMINATED }

    public enum ResourceType { COMPUTE_INSTANCES, STORAGE_GB, LAMBDA_FUNCTIONS, DATABASES }

    @Getter
    @Builder
    public static class Tenant {
        private final String tenantId;
        private String name;
        private TenantTier tier;
        private TenantStatus status;
        private final int vlanTag;
        private final LocalDateTime createdAt;
        private TenantQuota quota;

        // Lombok @Builder doesn't generate setters, add manually for mutable fields
        public void setStatus(TenantStatus status) { this.status = status; }
        public void setTier(TenantTier tier) { this.tier = tier; }
        public void setQuota(TenantQuota quota) { this.quota = quota; }
    }

    @Getter
    public static class TenantQuota {
        private final long computeInstances;
        private final long storageGb;
        private final long lambdaFunctions;
        private final long databases;

        public TenantQuota(long compute, long storage, long lambda, long dbs) {
            this.computeInstances = compute;
            this.storageGb = storage;
            this.lambdaFunctions = lambda;
            this.databases = dbs;
        }

        public static TenantQuota forTier(TenantTier tier) {
            return switch (tier) {
                case FREE       -> new TenantQuota(2, 10, 5, 1);
                case STARTER    -> new TenantQuota(10, 100, 25, 5);
                case BUSINESS   -> new TenantQuota(50, 1000, 100, 20);
                case ENTERPRISE -> new TenantQuota(500, 10000, -1, 100); // -1 = unlimited
            };
        }

        public long getLimit(ResourceType type) {
            return switch (type) {
                case COMPUTE_INSTANCES -> computeInstances;
                case STORAGE_GB -> storageGb;
                case LAMBDA_FUNCTIONS -> lambdaFunctions;
                case DATABASES -> databases;
            };
        }
    }

    public static class TenantUsage {
        private final ConcurrentHashMap<ResourceType, AtomicLong> counters = new ConcurrentHashMap<>();

        public long get(ResourceType resource) {
            return counters.getOrDefault(resource, new AtomicLong(0)).get();
        }

        public void increment(ResourceType resource, long amount) {
            counters.computeIfAbsent(resource, k -> new AtomicLong(0)).addAndGet(amount);
        }

        public void decrement(ResourceType resource, long amount) {
            counters.computeIfAbsent(resource, k -> new AtomicLong(0))
                    .updateAndGet(v -> Math.max(0, v - amount));
        }
    }

    @Getter
    @Builder
    public static class TenantUsageSummary {
        private String tenantId;
        private String tenantName;
        private TenantTier tier;
        private long computeInstances;
        private long computeLimit;
        private long storageGb;
        private long storageLimit;
        private long lambdaFunctions;
        private long lambdaLimit;
        private long databases;
        private long databaseLimit;
    }

    public static class QuotaExceededException extends RuntimeException {
        @Getter private final String tenantId;
        @Getter private final ResourceType resource;
        @Getter private final long currentUsage;
        @Getter private final long limit;

        public QuotaExceededException(String tenantId, ResourceType resource,
                                       long current, long limit, String message) {
            super(message);
            this.tenantId = tenantId;
            this.resource = resource;
            this.currentUsage = current;
            this.limit = limit;
        }
    }
}
