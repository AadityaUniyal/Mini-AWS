package com.minicloud.api.billing;

import com.minicloud.api.domain.BillingRecord;
import com.minicloud.api.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
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
}
