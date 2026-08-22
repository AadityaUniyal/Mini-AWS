package com.minicloud.api.billing;

import com.minicloud.api.auth.SecurityUtils;
import com.minicloud.api.auth.UserPrincipal;
import com.minicloud.api.domain.BillingRecord;
import com.minicloud.api.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/billing")
@Tag(name = "Billing", description = "AWS-style Billing & Cost Management")
@RequiredArgsConstructor
@SecurityRequirement(name = "BearerAuth")
public class BillingController {

    private final BillingService billingService;
    private final RightsizingAdvisorService rightsizingAdvisorService;

    @GetMapping("/summary")
    @Operation(summary = "Get billing summary for current authenticated account")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCurrentSummary() {
        UserPrincipal principal = SecurityUtils.getAuthenticatedPrincipal();
        String accountId = principal.getAccountId() != null ? principal.getAccountId() : "123456789012";
        return getSummary(accountId);
    }

    @GetMapping("/recommendations")
    @Operation(summary = "Get cost rightsizing recommendations for underutilized instances",
               description = "Analyzes rolling CPU utilization metrics (<10% threshold) and suggests cost-saving instance downgrades")
    public ResponseEntity<ApiResponse<RightsizingResponseDTO>> getRecommendations(
            @Parameter(description = "Optional AWS Account ID to filter recommendations")
            @RequestParam(required = false) String accountId) {
        
        String effectiveAccountId = accountId;
        try {
            UserPrincipal principal = SecurityUtils.getAuthenticatedPrincipal();
            if (principal.getAccountId() != null) {
                effectiveAccountId = principal.getAccountId();
            }
        } catch (Exception ignored) {}

        RightsizingResponseDTO response = rightsizingAdvisorService.getRecommendations(effectiveAccountId);
        return ResponseEntity.ok(ApiResponse.ok("Rightsizing recommendations generated", response));
    }

    @GetMapping("/summary/{accountId}")
    @Operation(summary = "Get billing summary for an account")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSummary(@PathVariable String accountId) {
        SecurityUtils.validateAccountOwnership(accountId);
        BigDecimal total = billingService.getMonthToDateEstimate(accountId);
        List<BillingRecord> records = billingService.getAccountBills(accountId);
        
        Map<String, Object> response = new HashMap<>();
        response.put("monthToDateEstimate", total);
        response.put("currency", "USD");
        response.put("usageRecords", records);
        
        return ResponseEntity.ok(ApiResponse.ok("Billing summary retrieved", response));
    }

    @GetMapping("/estimate")
    @Operation(summary = "Estimate cost before launching a resource", 
               description = "Calculate projected costs per minute/hour/day/month for AWS-equivalent resources")
    public ResponseEntity<ApiResponse<Map<String, Object>>> estimateCost(
            @Parameter(description = "Resource type (EC2, RDS, S3)", example = "EC2")
            @RequestParam String resourceType,
            @Parameter(description = "Instance type for EC2/RDS", example = "T2_MICRO")
            @RequestParam(required = false) String instanceType) {
        
        Map<String, Object> estimate = new LinkedHashMap<>();
        
        double hourlyRate = 0.05;
        if ("RDS".equalsIgnoreCase(resourceType)) {
            hourlyRate = 0.10;
        } else if ("S3".equalsIgnoreCase(resourceType)) {
            hourlyRate = 0.02 / (30.0 * 24.0); // Cost per GB-hour
        } else if (instanceType != null) {
            try {
                com.minicloud.api.domain.InstanceType type = com.minicloud.api.domain.InstanceType.valueOf(instanceType.toUpperCase().replace(".", "_"));
                hourlyRate = type.getCostPerHour();
            } catch (Exception ignored) {}
        }
        
        estimate.put("resourceType", resourceType);
        estimate.put("instanceType", instanceType != null ? instanceType : "STANDARD");
        estimate.put("currency", "USD");
        estimate.put("perMinute", Math.round((hourlyRate / 60.0) * 10000.0) / 10000.0);
        estimate.put("perHour", Math.round(hourlyRate * 100.0) / 100.0);
        estimate.put("perDay", Math.round(hourlyRate * 24.0 * 100.0) / 100.0);
        estimate.put("perMonth", Math.round(hourlyRate * 24.0 * 30.0 * 100.0) / 100.0);
        
        return ResponseEntity.ok(ApiResponse.ok("Cost estimate calculated", estimate));
    }
}
