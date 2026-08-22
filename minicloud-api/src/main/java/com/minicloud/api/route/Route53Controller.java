package com.minicloud.api.route;

import com.minicloud.api.auth.SecurityUtils;
import com.minicloud.api.auth.UserPrincipal;
import com.minicloud.api.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/route53")
@Tag(name = "Route 53", description = "High-fidelity DNS management")
@RequiredArgsConstructor
@SecurityRequirement(name = "BearerAuth")
public class Route53Controller {

    private final Route53Service route53Service;

    @GetMapping("/zones")
    @Operation(summary = "List hosted zones for authenticated account")
    public ResponseEntity<ApiResponse<List<HostedZone>>> getZonesForCurrentAccount() {
        UserPrincipal principal = SecurityUtils.getAuthenticatedPrincipal();
        return ResponseEntity.ok(ApiResponse.ok("Zones retrieved", route53Service.getZones(principal.getAccountId())));
    }

    @GetMapping("/zones/{accountId}")
    @Operation(summary = "List hosted zones for an account")
    public ResponseEntity<ApiResponse<List<HostedZone>>> getZones(@PathVariable String accountId) {
        SecurityUtils.validateAccountOwnership(accountId);
        return ResponseEntity.ok(ApiResponse.ok("Zones retrieved", route53Service.getZones(accountId)));
    }

    @PostMapping("/zones")
    @Operation(summary = "Create a new hosted zone")
    public ResponseEntity<ApiResponse<HostedZone>> createZone(@RequestBody Map<String, String> request) {
        UserPrincipal principal = SecurityUtils.getAuthenticatedPrincipal();
        String accountId = principal.getAccountId() != null ? principal.getAccountId() : request.get("accountId");
        
        HostedZone zone = route53Service.createZone(
                request.get("name"),
                accountId,
                request.get("comment")
        );
        return ResponseEntity.ok(ApiResponse.ok("Zone created", zone));
    }

    @GetMapping("/records/{zoneId}")
    @Operation(summary = "List records for a hosted zone")
    public ResponseEntity<ApiResponse<List<DnsRecord>>> getRecords(@PathVariable UUID zoneId) {
        return ResponseEntity.ok(ApiResponse.ok("Records retrieved", route53Service.getRecords(zoneId)));
    }

    @PostMapping("/records")
    @Operation(summary = "Create a DNS record")
    public ResponseEntity<ApiResponse<DnsRecord>> createRecord(@RequestBody Map<String, Object> request) {
        UserPrincipal principal = SecurityUtils.getAuthenticatedPrincipal();
        String accountId = principal.getAccountId() != null ? principal.getAccountId() : (request.get("accountId") != null ? request.get("accountId").toString() : null);

        DnsRecord record = route53Service.createRecord(
                UUID.fromString(request.get("zoneId").toString()),
                request.get("name").toString(),
                request.get("type").toString(),
                request.get("value").toString(),
                request.get("ttl") != null ? Long.parseLong(request.get("ttl").toString()) : 300L,
                accountId
        );
        return ResponseEntity.ok(ApiResponse.ok("Record created", record));
    }

    @DeleteMapping("/zones/{zoneId}")
    @Operation(summary = "Delete a hosted zone")
    public ResponseEntity<ApiResponse<Void>> deleteZone(@PathVariable UUID zoneId) {
        route53Service.deleteZone(zoneId);
        return ResponseEntity.ok(ApiResponse.ok("Zone deleted", null));
    }

    @DeleteMapping("/records/{recordId}")
    @Operation(summary = "Delete a DNS record")
    public ResponseEntity<ApiResponse<Void>> deleteRecord(@PathVariable UUID recordId) {
        route53Service.deleteRecord(recordId);
        return ResponseEntity.ok(ApiResponse.ok("Record deleted", null));
    }
}
