package com.minicloud.api.billing;

import com.minicloud.api.domain.BillingRecord;
import com.minicloud.api.dto.ApiResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BillingControllerTest {

    @Mock
    private BillingService billingService;

    @Mock
    private RightsizingAdvisorService rightsizingAdvisorService;

    @InjectMocks
    private BillingController billingController;

    @Test
    void testPerformanceBenchmarkGeneratesScores() {
        ResponseEntity<ApiResponse<Map<String, Object>>> response = billingController.getPerformanceBenchmark();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        Map<String, Object> data = response.getBody().getData();
        assertNotNull(data);
        assertEquals("OPTIMAL", data.get("status"));
        assertTrue((Long) data.get("throughputSimulatedOpsPerSec") > 0);
        assertEquals(14.8, data.get("p95SimulatedLatencyMs"));
        assertEquals(240, data.get("memoryFootprintIdleMB"));
        assertEquals(98.5, data.get("systemHealthScore"));
    }

    @Test
    void testExportBillingReportCsv() {
        BillingRecord record = BillingRecord.builder()
                .id(UUID.randomUUID())
                .accountId("123456789012")
                .service("EC2")
                .resourceId("i-0123456789")
                .resourceName("web-prod-1")
                .usageQuantity(BigDecimal.valueOf(1.5))
                .unitType("hour")
                .totalCost(BigDecimal.valueOf(0.075))
                .startTime(LocalDateTime.now().minusHours(1))
                .endTime(LocalDateTime.now())
                .build();

        when(billingService.getAccountBills("123456789012")).thenReturn(List.of(record));

        ResponseEntity<?> response = billingController.exportBillingReport("csv", "123456789012");
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody() instanceof String);
        String csv = (String) response.getBody();
        assertTrue(csv.contains("RecordId,AccountId,Service,ResourceId"));
        assertTrue(csv.contains("web-prod-1"));
        assertTrue(csv.contains("123456789012"));
    }

    @Test
    void testExportBillingReportJson() {
        when(billingService.getAccountBills("123456789012")).thenReturn(List.of());
        when(billingService.getMonthToDateEstimate("123456789012")).thenReturn(BigDecimal.ZERO);

        ResponseEntity<?> response = billingController.exportBillingReport("json", "123456789012");
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody() instanceof ApiResponse);
    }
}
