package com.minicloud.api.monitoring;

import com.minicloud.api.monitoring.TenantService;
import com.minicloud.api.monitoring.TenantService.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * TenantController — REST API for multi-tenant management.
 *
 *  POST /tenants                      → Register a new tenant
 *  GET  /tenants                      → List all tenants (admin)
 *  GET  /tenants/{id}                 → Get tenant details
 *  GET  /tenants/{id}/usage           → Get resource usage summary
 *  PUT  /tenants/{id}/suspend         → Suspend a tenant
 *  PUT  /tenants/{id}/activate        → Re-activate a suspended tenant
 *  PUT  /tenants/{id}/tier            → Upgrade/downgrade tenant tier
 *  POST /tenants/{id}/quota/check     → Pre-flight quota check
 *  POST /tenants/{id}/usage/record    → Record resource usage
 *  POST /tenants/{id}/usage/release   → Release used resources
 */
@RestController
@RequestMapping("/tenants")
@RequiredArgsConstructor
@Tag(name = "Multi-Tenancy", description = "Tenant management and resource quota enforcement")
public class TenantController {

    private final TenantService tenantService;

    @PostMapping
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "Register a new tenant with a given tier")
    public ResponseEntity<Map<String, Object>> registerTenant(@RequestBody RegisterTenantRequest req) {
        try {
            TenantTier tier = TenantTier.valueOf(req.tier().toUpperCase());
            Tenant tenant = tenantService.registerTenant(req.tenantId(), req.name(), tier);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of("message", "Tenant registered", "data", toMap(tenant)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "List all registered tenants")
    public ResponseEntity<Map<String, Object>> listTenants() {
        List<Map<String, Object>> list = tenantService.listTenants()
                .stream().map(this::toMap).toList();
        return ResponseEntity.ok(Map.of("data", list, "count", list.size()));
    }

    @GetMapping("/{id}")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "Get tenant details by ID")
    public ResponseEntity<Map<String, Object>> getTenant(@PathVariable String id) {
        try {
            return ResponseEntity.ok(Map.of("data", toMap(tenantService.getTenant(id))));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{id}/usage")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "Get resource usage summary for a tenant")
    public ResponseEntity<Map<String, Object>> getUsage(@PathVariable String id) {
        try {
            TenantUsageSummary summary = tenantService.getUsageSummary(id);
            return ResponseEntity.ok(Map.of("data", summaryToMap(summary)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}/suspend")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "Suspend a tenant account")
    public ResponseEntity<Map<String, Object>> suspend(@PathVariable String id) {
        try {
            tenantService.suspendTenant(id);
            return ResponseEntity.ok(Map.of("message", "Tenant suspended", "tenantId", id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}/activate")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "Re-activate a suspended tenant")
    public ResponseEntity<Map<String, Object>> activate(@PathVariable String id) {
        try {
            tenantService.activateTenant(id);
            return ResponseEntity.ok(Map.of("message", "Tenant activated", "tenantId", id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}/tier")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "Upgrade or downgrade tenant tier")
    public ResponseEntity<Map<String, Object>> changeTier(
            @PathVariable String id, @RequestParam String tier) {
        try {
            tenantService.upgradeTier(id, TenantTier.valueOf(tier.toUpperCase()));
            return ResponseEntity.ok(Map.of("message", "Tier updated", "tenantId", id, "newTier", tier));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/quota/check")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "Pre-flight quota check before allocating a resource")
    public ResponseEntity<Map<String, Object>> checkQuota(
            @PathVariable String id,
            @RequestParam String resource,
            @RequestParam(defaultValue = "1") long amount) {
        try {
            ResourceType type = ResourceType.valueOf(resource.toUpperCase());
            tenantService.checkQuota(id, type, amount);
            return ResponseEntity.ok(Map.of("allowed", true, "tenantId", id, "resource", resource));
        } catch (TenantService.QuotaExceededException e) {
            return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED)
                    .body(Map.of("allowed", false, "error", e.getMessage(),
                            "currentUsage", e.getCurrentUsage(), "limit", e.getLimit()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/usage/record")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "Record resource usage for a tenant")
    public ResponseEntity<Map<String, Object>> recordUsage(
            @PathVariable String id,
            @RequestParam String resource,
            @RequestParam(defaultValue = "1") long amount) {
        try {
            tenantService.recordUsage(id, ResourceType.valueOf(resource.toUpperCase()), amount);
            return ResponseEntity.ok(Map.of("recorded", true, "tenantId", id,
                    "resource", resource, "amount", amount));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/usage/release")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "Release previously recorded resource usage")
    public ResponseEntity<Map<String, Object>> releaseUsage(
            @PathVariable String id,
            @RequestParam String resource,
            @RequestParam(defaultValue = "1") long amount) {
        try {
            tenantService.releaseUsage(id, ResourceType.valueOf(resource.toUpperCase()), amount);
            return ResponseEntity.ok(Map.of("released", true, "tenantId", id,
                    "resource", resource, "amount", amount));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Map<String, Object> toMap(Tenant t) {
        return Map.of(
                "tenantId", t.getTenantId(),
                "name", t.getName(),
                "tier", t.getTier().name(),
                "status", t.getStatus().name(),
                "vlanTag", t.getVlanTag(),
                "createdAt", t.getCreatedAt().toString()
        );
    }

    private Map<String, Object> summaryToMap(TenantUsageSummary s) {
        return Map.of(
                "tenantId", s.getTenantId(),
                "tenantName", s.getTenantName(),
                "tier", s.getTier().name(),
                "computeInstances", Map.of("used", s.getComputeInstances(), "limit", s.getComputeLimit()),
                "storageGb", Map.of("used", s.getStorageGb(), "limit", s.getStorageLimit()),
                "lambdaFunctions", Map.of("used", s.getLambdaFunctions(), "limit", s.getLambdaLimit()),
                "databases", Map.of("used", s.getDatabases(), "limit", s.getDatabaseLimit())
        );
    }

    // ── Request Records ───────────────────────────────────────────────────────

    public record RegisterTenantRequest(String tenantId, String name, String tier) {}
}
