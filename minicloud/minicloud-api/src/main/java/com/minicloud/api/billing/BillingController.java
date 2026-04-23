package com.minicloud.api.billing;

import com.minicloud.api.domain.BillingRecord;
import com.minicloud.api.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/billing")
@Tag(name = "Billing", description = "AWS-style Billing & Cost Management")
@RequiredArgsConstructor
public class BillingController {

    private final BillingService billingService;

    @GetMapping("/summary/{accountId}")
    @Operation(summary = "Get billing summary for an account")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSummary(@PathVariable String accountId) {
        double total = billingService.getMonthToDateEstimate(accountId);
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
        
        double hourlyRate = switch (resourceType.toUpperCase()) {
            case "EC2" -> getEc2Rate(instanceType);
            case "RDS" -> getRdsRate(instanceType);
            case "S3" -> 0.02 / (30 * 24);  // $0.02 per GB-month converted to per hour
            case "LAMBDA" -> 0.0000002;     // $0.0000002 per request
            default -> throw new IllegalArgumentException("Unknown resource type: " + resourceType);
        };
        
        estimate.put("resourceType", resourceType);
        estimate.put("instanceType", instanceType != null ? instanceType : "N/A");
        estimate.put("perMinute", String.format("$%.6f", hourlyRate / 60));
        estimate.put("perHour", String.format("$%.4f", hourlyRate));
        estimate.put("perDay", String.format("$%.2f", hourlyRate * 24));
        estimate.put("perMonth", String.format("$%.2f", hourlyRate * 24 * 30));
        estimate.put("currency", "USD");
        
        return ResponseEntity.ok(ApiResponse.ok("Cost estimate calculated", estimate));
    }

    /**
     * Get EC2 hourly rate based on instance type
     */
    private double getEc2Rate(String instanceType) {
        if (instanceType == null) return 0.05; // default rate
        
        return switch (instanceType.toUpperCase()) {
            case "T2_MICRO" -> 0.0116;   // AWS t2.micro rate
            case "T2_SMALL" -> 0.023;    // AWS t2.small rate
            case "T2_MEDIUM" -> 0.046;   // AWS t2.medium rate
            case "M5_LARGE" -> 0.096;    // AWS m5.large rate
            case "C5_XLARGE" -> 0.17;    // AWS c5.xlarge rate
            case "R5_LARGE" -> 0.126;    // AWS r5.large rate
            default -> 0.05;             // fallback rate
        };
    }

    /**
     * Get RDS hourly rate based on instance type
     */
    private double getRdsRate(String instanceType) {
        if (instanceType == null) return 0.10; // default RDS rate
        
        return switch (instanceType.toUpperCase()) {
            case "DB_T2_MICRO" -> 0.017;   // AWS db.t2.micro rate
            case "DB_T2_SMALL" -> 0.034;   // AWS db.t2.small rate
            case "DB_M5_LARGE" -> 0.192;   // AWS db.m5.large rate
            default -> 0.10;               // fallback rate
        };
    }
}
