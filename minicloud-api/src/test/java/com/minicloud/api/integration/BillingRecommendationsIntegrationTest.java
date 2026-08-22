package com.minicloud.api.integration;

import com.minicloud.api.MiniCloudApiApplication;
import com.minicloud.api.billing.RightsizingAdvisorService;
import com.minicloud.api.billing.RightsizingRecommendationDTO;
import com.minicloud.api.billing.RightsizingResponseDTO;
import com.minicloud.api.config.TestSecurityConfig;
import com.minicloud.api.domain.Instance;
import com.minicloud.api.domain.InstanceRepository;
import com.minicloud.api.domain.InstanceState;
import com.minicloud.api.domain.InstanceType;
import com.minicloud.api.dto.ApiResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test suite for Milestone 3 (R3): Telemetry-Driven Cost & Rightsizing Advisor.
 *
 * Verifies:
 * 1. Rolling average CPU telemetry analysis (<10% threshold).
 * 2. Complete downgrade hierarchy mapping (M5_XLARGE -> T3_LARGE -> T2_MEDIUM -> T2_SMALL -> T2_MICRO).
 * 3. Exact hourly and 730-hour monthly savings calculations.
 * 4. Boundary condition handling (9.9% vs 10.0%, T2_MICRO minimum tier, non-running states).
 * 5. Account ID filtering and multi-instance fleet optimization.
 * 6. REST API endpoint GET /api/v1/billing/recommendations contract compliance.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = MiniCloudApiApplication.class)
@TestPropertySource(properties = {
    "java.awt.headless=true",
    "minicloud.system-tray.enabled=false",
    "minicloud.h2.tcp.enabled=false",
    "spring.datasource.url=jdbc:h2:mem:billing_testdb;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.profiles.active=test"
})
@Import(TestSecurityConfig.class)
public class BillingRecommendationsIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private InstanceRepository instanceRepository;

    @Autowired
    private RightsizingAdvisorService rightsizingAdvisorService;

    private String baseUrl;
    private final String testAccountId = "123456789012";
    private final String otherAccountId = "987654321098";

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port;
        rightsizingAdvisorService.clearAllCpuMetrics();
        instanceRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        rightsizingAdvisorService.clearAllCpuMetrics();
        instanceRepository.deleteAll();
    }

    private Instance createAndSaveInstance(String name, InstanceType type, InstanceState state, String accountId) {
        Instance instance = Instance.builder()
                .name(name)
                .type(type)
                .state(state)
                .accountId(accountId)
                .userId(UUID.randomUUID())
                .build();
        return instanceRepository.save(instance);
    }

    @Test
    @DisplayName("R3.1: Underutilized T2_MEDIUM instance (<10% CPU) receives recommendation to downgrade to T2_SMALL")
    void testUnderutilizedInstanceRecommendation() {
        // Arrange
        Instance instance = createAndSaveInstance("web-server-1", InstanceType.T2_MEDIUM, InstanceState.RUNNING, testAccountId);
        
        // Record low CPU telemetry: 4.5% average
        rightsizingAdvisorService.recordInstanceCpuMetric(instance.getId(), 4.0);
        rightsizingAdvisorService.recordInstanceCpuMetric(instance.getId(), 5.0);

        // Act
        String url = baseUrl + "/api/v1/billing/recommendations?accountId=" + testAccountId;
        ResponseEntity<ApiResponse<RightsizingResponseDTO>> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
        );

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());

        RightsizingResponseDTO data = response.getBody().getData();
        assertNotNull(data);
        assertEquals(1, data.getRecommendationsCount());
        assertNotNull(data.getEvaluatedAt());

        RightsizingRecommendationDTO rec = data.getRecommendations().get(0);
        assertEquals(instance.getId().toString(), rec.getInstanceId());
        assertEquals("web-server-1", rec.getInstanceName());
        assertEquals("T2_MEDIUM", rec.getCurrentInstanceType());
        assertEquals("T2_SMALL", rec.getRecommendedInstanceType());
        assertEquals(4.5, rec.getAverageCpuUtilization(), 0.01);
        assertEquals(0.046, rec.getCurrentHourlyCost(), 0.0001);
        assertEquals(0.023, rec.getRecommendedHourlyCost(), 0.0001);
        assertEquals(0.023, rec.getHourlySavings(), 0.0001);
        
        // 0.023 * 730 = 16.79
        assertEquals(16.79, rec.getEstimatedMonthlySavings(), 0.01);
        assertEquals(16.79, data.getTotalEstimatedMonthlySavings(), 0.01);
        assertTrue(rec.getReason().contains("4.5%"));
        assertTrue(rec.getReason().contains("<10.0% threshold"));
    }

    @Test
    @DisplayName("R3.2: Complete Downgrade Hierarchy & Cost Math Verification")
    void testCompleteDowngradeHierarchyAndMath() {
        // Test all valid downgrade steps:
        // 1. M5_XLARGE ($0.192) -> T3_LARGE ($0.096): hourly savings = 0.096, monthly = 70.08
        // 2. T3_LARGE ($0.096) -> T2_MEDIUM ($0.046): hourly savings = 0.050, monthly = 36.50
        // 3. T2_MEDIUM ($0.046) -> T2_SMALL ($0.023): hourly savings = 0.023, monthly = 16.79
        // 4. T2_SMALL ($0.023) -> T2_MICRO ($0.0116): hourly savings = 0.0114, monthly = 8.322 -> 8.32

        Instance instM5 = createAndSaveInstance("inst-m5", InstanceType.M5_XLARGE, InstanceState.RUNNING, testAccountId);
        Instance instT3 = createAndSaveInstance("inst-t3", InstanceType.T3_LARGE, InstanceState.RUNNING, testAccountId);
        Instance instT2Med = createAndSaveInstance("inst-t2med", InstanceType.T2_MEDIUM, InstanceState.RUNNING, testAccountId);
        Instance instT2Small = createAndSaveInstance("inst-t2small", InstanceType.T2_SMALL, InstanceState.RUNNING, testAccountId);

        rightsizingAdvisorService.recordInstanceCpuMetric(instM5.getId(), 2.0);
        rightsizingAdvisorService.recordInstanceCpuMetric(instT3.getId(), 3.5);
        rightsizingAdvisorService.recordInstanceCpuMetric(instT2Med.getId(), 5.0);
        rightsizingAdvisorService.recordInstanceCpuMetric(instT2Small.getId(), 6.5);

        // Act
        RightsizingResponseDTO response = rightsizingAdvisorService.getRecommendations(testAccountId);

        // Assert
        assertEquals(4, response.getRecommendationsCount());

        RightsizingRecommendationDTO recM5 = response.getRecommendations().stream()
                .filter(r -> r.getInstanceId().equals(instM5.getId().toString())).findFirst().orElseThrow();
        assertEquals("T3_LARGE", recM5.getRecommendedInstanceType());
        assertThat(recM5.getHourlySavings()).isCloseTo(0.096, within(0.0001));
        assertThat(recM5.getEstimatedMonthlySavings()).isCloseTo(70.08, within(0.01));

        RightsizingRecommendationDTO recT3 = response.getRecommendations().stream()
                .filter(r -> r.getInstanceId().equals(instT3.getId().toString())).findFirst().orElseThrow();
        assertEquals("T2_MEDIUM", recT3.getRecommendedInstanceType());
        assertThat(recT3.getHourlySavings()).isCloseTo(0.050, within(0.0001));
        assertThat(recT3.getEstimatedMonthlySavings()).isCloseTo(36.50, within(0.01));

        RightsizingRecommendationDTO recT2Med = response.getRecommendations().stream()
                .filter(r -> r.getInstanceId().equals(instT2Med.getId().toString())).findFirst().orElseThrow();
        assertEquals("T2_SMALL", recT2Med.getRecommendedInstanceType());
        assertThat(recT2Med.getHourlySavings()).isCloseTo(0.023, within(0.0001));
        assertThat(recT2Med.getEstimatedMonthlySavings()).isCloseTo(16.79, within(0.01));

        RightsizingRecommendationDTO recT2Small = response.getRecommendations().stream()
                .filter(r -> r.getInstanceId().equals(instT2Small.getId().toString())).findFirst().orElseThrow();
        assertEquals("T2_MICRO", recT2Small.getRecommendedInstanceType());
        assertThat(recT2Small.getHourlySavings()).isCloseTo(0.0114, within(0.0001));
        assertThat(recT2Small.getEstimatedMonthlySavings()).isCloseTo(8.32, within(0.01));

        // Total savings = 70.08 + 36.50 + 16.79 + 8.32 = 131.69
        assertThat(response.getTotalEstimatedMonthlySavings()).isCloseTo(131.69, within(0.02));
    }

    @Test
    @DisplayName("R3.3: T2_MICRO instances are already at minimum tier and cannot be downgraded")
    void testT2MicroNoDowngradeRecommendation() {
        Instance instMicro = createAndSaveInstance("inst-micro", InstanceType.T2_MICRO, InstanceState.RUNNING, testAccountId);
        rightsizingAdvisorService.recordInstanceCpuMetric(instMicro.getId(), 1.0);

        RightsizingResponseDTO response = rightsizingAdvisorService.getRecommendations(testAccountId);

        assertEquals(0, response.getRecommendationsCount());
        assertEquals(0.0, response.getTotalEstimatedMonthlySavings());
        assertTrue(response.getRecommendations().isEmpty());
    }

    @Test
    @DisplayName("R3.4: Highly utilized instances (>=10% CPU) are NOT flagged for rightsizing")
    void testHighCpuInstanceNotRecommended() {
        Instance instBusy = createAndSaveInstance("busy-app", InstanceType.M5_XLARGE, InstanceState.RUNNING, testAccountId);
        rightsizingAdvisorService.recordInstanceCpuMetric(instBusy.getId(), 45.0);
        rightsizingAdvisorService.recordInstanceCpuMetric(instBusy.getId(), 55.0);

        RightsizingResponseDTO response = rightsizingAdvisorService.getRecommendations(testAccountId);

        assertEquals(0, response.getRecommendationsCount());
        assertEquals(0.0, response.getTotalEstimatedMonthlySavings());
    }

    @Test
    @DisplayName("R3.5: Threshold Boundary Verification (9.9% recommended, 10.0% and 10.1% not recommended)")
    void testCpuThresholdBoundaries() {
        Instance instBelow = createAndSaveInstance("inst-9-9", InstanceType.T2_MEDIUM, InstanceState.RUNNING, testAccountId);
        Instance instExact = createAndSaveInstance("inst-10-0", InstanceType.T2_MEDIUM, InstanceState.RUNNING, testAccountId);
        Instance instAbove = createAndSaveInstance("inst-10-1", InstanceType.T2_MEDIUM, InstanceState.RUNNING, testAccountId);

        rightsizingAdvisorService.recordInstanceCpuMetric(instBelow.getId(), 9.9);
        rightsizingAdvisorService.recordInstanceCpuMetric(instExact.getId(), 10.0);
        rightsizingAdvisorService.recordInstanceCpuMetric(instAbove.getId(), 10.1);

        RightsizingResponseDTO response = rightsizingAdvisorService.getRecommendations(testAccountId);

        assertEquals(1, response.getRecommendationsCount());
        assertEquals(instBelow.getId().toString(), response.getRecommendations().get(0).getInstanceId());
        assertEquals(9.9, response.getRecommendations().get(0).getAverageCpuUtilization(), 0.01);
    }

    @Test
    @DisplayName("R3.6: Non-running instances (STOPPED, TERMINATED) are ignored")
    void testNonRunningInstancesIgnored() {
        Instance instStopped = createAndSaveInstance("inst-stopped", InstanceType.M5_XLARGE, InstanceState.STOPPED, testAccountId);
        Instance instTerminated = createAndSaveInstance("inst-terminated", InstanceType.T3_LARGE, InstanceState.TERMINATED, testAccountId);

        rightsizingAdvisorService.recordInstanceCpuMetric(instStopped.getId(), 2.0);
        rightsizingAdvisorService.recordInstanceCpuMetric(instTerminated.getId(), 2.0);

        RightsizingResponseDTO response = rightsizingAdvisorService.getRecommendations(testAccountId);

        assertEquals(0, response.getRecommendationsCount());
    }

    @Test
    @DisplayName("R3.7: Account ID filtering isolates recommendations to target account")
    void testAccountIdFiltering() {
        Instance instAcc1 = createAndSaveInstance("inst-acc1", InstanceType.T2_MEDIUM, InstanceState.RUNNING, testAccountId);
        Instance instAcc2 = createAndSaveInstance("inst-acc2", InstanceType.T2_MEDIUM, InstanceState.RUNNING, otherAccountId);

        rightsizingAdvisorService.recordInstanceCpuMetric(instAcc1.getId(), 3.0);
        rightsizingAdvisorService.recordInstanceCpuMetric(instAcc2.getId(), 3.0);

        // Query for testAccountId only
        RightsizingResponseDTO res1 = rightsizingAdvisorService.getRecommendations(testAccountId);
        assertEquals(1, res1.getRecommendationsCount());
        assertEquals(instAcc1.getId().toString(), res1.getRecommendations().get(0).getInstanceId());

        // Query for otherAccountId only
        RightsizingResponseDTO res2 = rightsizingAdvisorService.getRecommendations(otherAccountId);
        assertEquals(1, res2.getRecommendationsCount());
        assertEquals(instAcc2.getId().toString(), res2.getRecommendations().get(0).getInstanceId());

        // Query across all accounts
        RightsizingResponseDTO resAll = rightsizingAdvisorService.getRecommendations(null);
        assertEquals(2, resAll.getRecommendationsCount());
        assertThat(resAll.getTotalEstimatedMonthlySavings()).isCloseTo(33.58, within(0.01));
    }

    @Test
    @DisplayName("R3.8: REST API GET /api/v1/billing/recommendations produces compliant JSON payload")
    void testRestEndpointDirectContract() {
        Instance inst = createAndSaveInstance("demo-server", InstanceType.T3_LARGE, InstanceState.RUNNING, testAccountId);
        rightsizingAdvisorService.recordInstanceCpuMetric(inst.getId(), 6.2);

        String url = baseUrl + "/api/v1/billing/recommendations";
        ResponseEntity<ApiResponse<RightsizingResponseDTO>> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertEquals("Rightsizing recommendations generated", response.getBody().getMessage());

        RightsizingResponseDTO body = response.getBody().getData();
        assertNotNull(body);
        assertEquals(1, body.getRecommendationsCount());
        assertThat(body.getTotalEstimatedMonthlySavings()).isCloseTo(36.50, within(0.01));

        RightsizingRecommendationDTO item = body.getRecommendations().get(0);
        assertEquals(inst.getId().toString(), item.getInstanceId());
        assertEquals("demo-server", item.getInstanceName());
        assertEquals("T3_LARGE", item.getCurrentInstanceType());
        assertEquals("T2_MEDIUM", item.getRecommendedInstanceType());
        assertEquals(6.2, item.getAverageCpuUtilization(), 0.01);
        assertEquals(0.096, item.getCurrentHourlyCost(), 0.0001);
        assertEquals(0.046, item.getRecommendedHourlyCost(), 0.0001);
        assertEquals(0.050, item.getHourlySavings(), 0.0001);
        assertEquals(36.50, item.getEstimatedMonthlySavings(), 0.01);
    }
}
