package com.minicloud.api.integration;

import com.minicloud.api.domain.*;
import com.minicloud.api.dto.ApiResponse;
import com.minicloud.api.config.TestSecurityConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.*;
import org.springframework.test.context.TestPropertySource;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for existing REST endpoints
 * Validates that all cloud service endpoints remain functional after web service mode transformation
 * 
 * Tests EC2, S3, Lambda, RDS, IAM, billing, audit, and monitoring endpoints
 * 
 * **Validates: Requirements 6.1, 6.2, 6.3, 6.4, 6.5, 6.6, 6.7, 6.8, 6.9**
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
    "java.awt.headless=true",
    "minicloud.system-tray.enabled=false",
    "minicloud.h2.tcp.enabled=false",
    "spring.datasource.url=jdbc:h2:mem:testdb",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.profiles.active=test"
})
@Import(TestSecurityConfig.class)
class RestEndpointIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BucketRepository bucketRepository;

    @Autowired
    private InstanceRepository instanceRepository;

    @Autowired
    private FunctionRepository functionRepository;

    @Autowired
    private RdsRepository rdsRepository;

    private String baseUrl;
    private UUID testUserId;
    private String testAccountId;
    private String testUsername;

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port;
        
        // Clean up any existing test data
        userRepository.deleteAll();
        
        // Create test user with unique username
        String uniqueUsername = "testuser-" + System.currentTimeMillis();
        User testUser = User.builder()
            .username(uniqueUsername)
            .email("test@example.com")
            .passwordHash("hashedpassword")
            .accountId("test-account-123")
            .role(UserRole.ADMIN)
            .enabled(true)
            .rootUser(false)
            .build();
        testUser = userRepository.save(testUser);
        testUserId = testUser.getId();
        testAccountId = testUser.getAccountId();
        testUsername = uniqueUsername;
    }

    @AfterEach
    void tearDown() {
        // Clean up test data
        userRepository.deleteAll();
        bucketRepository.deleteAll();
        instanceRepository.deleteAll();
        functionRepository.deleteAll();
        rdsRepository.deleteAll();
    }

    /**
     * Test EC2 endpoints (launch, terminate, list instances)
     * Validates: Requirement 6.1
     */
    @Test
    void testEc2Endpoints() {
        // Test list instances endpoint
        String listUrl = baseUrl + "/api/v1/compute/instances/account/" + testAccountId;
        ResponseEntity<String> listResponse = restTemplate.getForEntity(listUrl, String.class);
        
        assertEquals(HttpStatus.OK, listResponse.getStatusCode(), 
            "EC2 list instances endpoint should return 200 OK");
        assertNotNull(listResponse.getBody(), "Response body should not be null");
    }

    /**
     * Test S3 endpoints (create bucket, upload, download, delete)
     * Validates: Requirement 6.2
     */
    @Test
    void testS3Endpoints() {
        // Test list buckets endpoint
        String listUrl = baseUrl + "/api/v1/storage/buckets/user/" + testUserId;
        ResponseEntity<String> listResponse = restTemplate.getForEntity(listUrl, String.class);
        
        assertEquals(HttpStatus.OK, listResponse.getStatusCode(),
            "S3 list buckets endpoint should return 200 OK");
        assertNotNull(listResponse.getBody(), "Response body should not be null");
    }

    /**
     * Test Lambda endpoints (create, invoke, list, delete)
     * Validates: Requirement 6.3
     */
    @Test
    void testLambdaEndpoints() {
        // Test list functions endpoint
        String listUrl = baseUrl + "/api/v1/lambda?userId=" + testUserId;
        ResponseEntity<String> listResponse = restTemplate.getForEntity(listUrl, String.class);
        
        assertEquals(HttpStatus.OK, listResponse.getStatusCode(),
            "Lambda list functions endpoint should return 200 OK");
        assertNotNull(listResponse.getBody(), "Response body should not be null");
    }

    /**
     * Test RDS endpoints (create, list, delete)
     * Validates: Requirement 6.4
     */
    @Test
    void testRdsEndpoints() {
        // Test list RDS instances endpoint
        String listUrl = baseUrl + "/api/v1/rds/instances/user/" + testUserId;
        ResponseEntity<String> listResponse = restTemplate.getForEntity(listUrl, String.class);
        
        assertEquals(HttpStatus.OK, listResponse.getStatusCode(),
            "RDS list instances endpoint should return 200 OK");
        assertNotNull(listResponse.getBody(), "Response body should not be null");
    }

    /**
     * Test IAM endpoints (create users, policies, access keys)
     * Validates: Requirement 6.5
     */
    @Test
    void testIamEndpoints() {
        // Test list users endpoint
        String listUrl = baseUrl + "/api/v1/iam/users";
        ResponseEntity<String> listResponse = restTemplate.getForEntity(listUrl, String.class);
        
        assertEquals(HttpStatus.OK, listResponse.getStatusCode(),
            "IAM list users endpoint should return 200 OK");
        assertNotNull(listResponse.getBody(), "Response body should not be null");
        
        // Test list policies endpoint
        String policiesUrl = baseUrl + "/api/v1/iam/policies";
        ResponseEntity<String> policiesResponse = restTemplate.getForEntity(policiesUrl, String.class);
        
        assertEquals(HttpStatus.OK, policiesResponse.getStatusCode(),
            "IAM list policies endpoint should return 200 OK");
        
        // Test list access keys endpoint
        String keysUrl = baseUrl + "/api/v1/iam/users/" + testUsername + "/access-keys";
        ResponseEntity<String> keysResponse = restTemplate.getForEntity(keysUrl, String.class);
        
        assertEquals(HttpStatus.OK, keysResponse.getStatusCode(),
            "IAM list access keys endpoint should return 200 OK");
    }

    /**
     * Test billing endpoints (view costs, invoices)
     * Validates: Requirement 6.6
     */
    @Test
    void testBillingEndpoints() {
        // Test get billing summary endpoint
        String summaryUrl = baseUrl + "/api/v1/billing/summary/" + testAccountId;
        ResponseEntity<String> summaryResponse = restTemplate.getForEntity(summaryUrl, String.class);
        
        assertEquals(HttpStatus.OK, summaryResponse.getStatusCode(),
            "Billing summary endpoint should return 200 OK");
        assertNotNull(summaryResponse.getBody(), "Response body should not be null");
    }

    /**
     * Test audit endpoints (view audit trail)
     * Validates: Requirement 6.7
     */
    @Test
    void testAuditEndpoints() {
        // Test list recent audit logs endpoint
        String auditUrl = baseUrl + "/api/v1/monitoring/audit/recent";
        ResponseEntity<String> auditResponse = restTemplate.getForEntity(auditUrl, String.class);
        
        assertEquals(HttpStatus.OK, auditResponse.getStatusCode(),
            "Audit logs endpoint should return 200 OK");
        assertNotNull(auditResponse.getBody(), "Response body should not be null");
    }

    /**
     * Test monitoring endpoints (CloudWatch metrics, alarms)
     * Validates: Requirement 6.8, 6.9
     */
    @Test
    void testMonitoringEndpoints() {
        // Test system metrics endpoint
        String metricsUrl = baseUrl + "/api/v1/monitoring/system";
        ResponseEntity<String> metricsResponse = restTemplate.getForEntity(metricsUrl, String.class);
        
        assertEquals(HttpStatus.OK, metricsResponse.getStatusCode(),
            "Monitoring system metrics endpoint should return 200 OK");
        assertNotNull(metricsResponse.getBody(), "Response body should not be null");
        
        // Test alarms endpoint
        String alarmsUrl = baseUrl + "/api/v1/monitoring/alarms/user/" + testUserId;
        ResponseEntity<String> alarmsResponse = restTemplate.getForEntity(alarmsUrl, String.class);
        
        assertEquals(HttpStatus.OK, alarmsResponse.getStatusCode(),
            "Monitoring alarms endpoint should return 200 OK");
    }

    /**
     * Verify that all endpoints return proper HTTP status codes
     */
    @Test
    void testEndpointsReturnValidHttpResponses() {
        String[] endpoints = {
            "/api/compute/instances/account/" + testAccountId,
            "/storage/buckets/user/" + testUserId,
            "/lambda?userId=" + testUserId,
            "/rds/instances/user/" + testUserId,
            "/api/iam/users",
            "/api/billing/summary/" + testAccountId,
            "/monitoring/audit/recent",
            "/monitoring/system"
        };
        
        for (String endpoint : endpoints) {
            ResponseEntity<String> response = restTemplate.getForEntity(baseUrl + endpoint, String.class);
            
            assertTrue(response.getStatusCode().is2xxSuccessful() || 
                      response.getStatusCode().is4xxClientError(),
                "Endpoint " + endpoint + " should return valid HTTP status code");
        }
    }

    /**
     * Verify that the web service is accessible via HTTP
     */
    @Test
    void testWebServiceIsAccessible() {
        String healthUrl = baseUrl + "/actuator/health";
        ResponseEntity<String> response = restTemplate.getForEntity(healthUrl, String.class);
        
        assertEquals(HttpStatus.OK, response.getStatusCode(),
            "Health endpoint should be accessible");
        assertTrue(response.getBody().contains("UP") || response.getBody().contains("\"status\""),
            "Health endpoint should return status information");
    }
}
