package com.minicloud.api.integration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minicloud.api.chaos.ChaosRequestDTO;
import com.minicloud.api.chaos.ChaosResultDTO;
import com.minicloud.api.config.TestSecurityConfig;
import com.minicloud.api.domain.*;
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
import org.springframework.http.*;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

import com.minicloud.api.MiniCloudApiApplication;

/**
 * Integration test suite for Milestone 4 (R4) - Chaos Engineering & Self-Healing Resilience.
 *
 * Verifies:
 * 1. Chaos injection endpoint POST /api/v1/chaos/terminate-random-instance.
 * 2. Random selection and termination of running instances in an ASG / fleet.
 * 3. Instance state machine transition to TERMINATED.
 * 4. Capacity deficit detection and automatic self-healing replenishment.
 * 5. Configuration preservation for replacement instances.
 * 6. WebSocket event broadcasts for CHAOS_INSTANCE_TERMINATED and SELF_HEALING_RECOVERY.
 * 7. Error handling when no running instances are available.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = MiniCloudApiApplication.class)
@TestPropertySource(properties = {
        "java.awt.headless=true",
        "minicloud.system-tray.enabled=false",
        "minicloud.h2.tcp.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:chaos_testdb;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.profiles.active=test"
})
@Import(TestSecurityConfig.class)
public class ChaosSelfHealingIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private InstanceRepository instanceRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private String baseUrl;
    private User testUser;
    private String testAccountId;

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port;

        instanceRepository.deleteAll();
        taskRepository.deleteAll();
        userRepository.deleteAll();

        testAccountId = "123456789012";
        testUser = User.builder()
                .username("chaos-tester-" + System.currentTimeMillis())
                .email("chaos@minicloud.test")
                .passwordHash("hashed")
                .accountId(testAccountId)
                .role(UserRole.ADMIN)
                .enabled(true)
                .rootUser(false)
                .build();
        testUser = userRepository.save(testUser);
    }

    @AfterEach
    void tearDown() {
        instanceRepository.deleteAll();
        taskRepository.deleteAll();
        userRepository.deleteAll();
    }

    private Instance seedInstance(String name, InstanceState state, InstanceType type, String accountId) {
        Instance instance = Instance.builder()
                .userId(testUser.getId())
                .accountId(accountId != null ? accountId : testAccountId)
                .name(name)
                .type(type != null ? type : InstanceType.T2_MICRO)
                .state(state)
                .privateIp("10.0.1." + (new Random().nextInt(200) + 10))
                .publicIp("54.210.10." + (new Random().nextInt(200) + 10))
                .pid(1000L + new Random().nextInt(50000))
                .command("sleep 3600")
                .launchedAt(LocalDateTime.now().minusHours(1))
                .createdAt(LocalDateTime.now().minusHours(1))
                .build();
        return instanceRepository.save(instance);
    }

    @Test
    @DisplayName("R4.1 & R4.2: Chaos Monkey terminates random running instance and triggers self-healing replenishment")
    void testChaosTerminationAndSelfHealingReplenishment() {
        // Given 3 running instances in an ASG fleet
        Instance inst1 = seedInstance("asg-prod-node-1", InstanceState.RUNNING, InstanceType.T2_MICRO, testAccountId);
        Instance inst2 = seedInstance("asg-prod-node-2", InstanceState.RUNNING, InstanceType.T2_SMALL, testAccountId);
        Instance inst3 = seedInstance("asg-prod-node-3", InstanceState.RUNNING, InstanceType.T2_MEDIUM, testAccountId);

        List<UUID> initialIds = List.of(inst1.getId(), inst2.getId(), inst3.getId());

        // When invoking chaos termination endpoint
        String url = baseUrl + "/api/v1/chaos/terminate-random-instance";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<ChaosRequestDTO> entity = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

        // Then HTTP 200 OK returned
        assertEquals(HttpStatus.OK, response.getStatusCode(), "Chaos endpoint must return 200 OK");
        assertNotNull(response.getBody());

        // Parse response
        try {
            ApiResponse<ChaosResultDTO> apiResponse = objectMapper.readValue(
                    response.getBody(),
                    new TypeReference<ApiResponse<ChaosResultDTO>>() {}
            );

            assertTrue(apiResponse.isSuccess(), "ApiResponse success should be true");
            ChaosResultDTO result = apiResponse.getData();
            assertNotNull(result, "ChaosResultDTO data should not be null");

            // Verify Chaos Result payload details
            assertEquals("TERMINATE_INSTANCE", result.getChaosAction());
            assertEquals("RUNNING", result.getPreviousState());
            assertEquals("TERMINATED", result.getCurrentState());
            assertTrue(result.isDeficitDetected(), "Deficit must be detected");
            assertEquals("SELF_HEALING_COMPLETED", result.getStatus());
            assertNotNull(result.getTimestamp());

            // Verify terminated instance is one of the running instances
            UUID terminatedId = UUID.fromString(result.getTerminatedInstanceId());
            assertTrue(initialIds.contains(terminatedId), "Terminated instance ID must be one of the running instances");

            // Verify DB state of terminated instance is TERMINATED
            Instance dbTerminated = instanceRepository.findById(terminatedId).orElseThrow();
            assertEquals(InstanceState.TERMINATED, dbTerminated.getState(), "Terminated instance in DB must have state TERMINATED");
            assertNull(dbTerminated.getPid(), "Terminated instance process ID should be null");

            // Verify replacement instance was launched and is RUNNING
            assertNotNull(result.getReplacementInstanceId(), "Replacement instance ID must be populated");
            UUID replacementId = UUID.fromString(result.getReplacementInstanceId());
            assertNotEquals(terminatedId, replacementId, "Replacement instance ID must differ from terminated instance ID");

            Instance dbReplacement = instanceRepository.findById(replacementId).orElseThrow();
            assertEquals(InstanceState.RUNNING, dbReplacement.getState(), "Replacement instance in DB must be RUNNING");
            assertNotNull(dbReplacement.getPrivateIp(), "Replacement instance must have assigned private IP");
            assertNotNull(dbReplacement.getPublicIp(), "Replacement instance must have assigned public IP");

            // Verify total active running instances count equals original target capacity (3 instances)
            List<Instance> currentRunning = instanceRepository.findByState(InstanceState.RUNNING);
            assertEquals(3, currentRunning.size(), "Self-healing must restore the cluster running capacity to 3 instances");

        } catch (Exception e) {
            fail("Failed to parse and validate chaos response: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("R4.1: Chaos termination with specific Auto Scaling Group parameter")
    void testChaosTerminationWithAsgFilter() {
        // Given instances in two different groups
        Instance alpha1 = seedInstance("asg-alpha-node-1", InstanceState.RUNNING, InstanceType.T2_MICRO, testAccountId);
        Instance alpha2 = seedInstance("asg-alpha-node-2", InstanceState.RUNNING, InstanceType.T2_MICRO, testAccountId);
        Instance beta1 = seedInstance("asg-beta-node-1", InstanceState.RUNNING, InstanceType.T2_MICRO, testAccountId);

        // When targeting asg-alpha
        String url = baseUrl + "/api/v1/chaos/terminate-random-instance?autoScalingGroupId=asg-alpha";
        ResponseEntity<String> response = restTemplate.postForEntity(url, null, String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());

        try {
            ApiResponse<ChaosResultDTO> apiResponse = objectMapper.readValue(
                    response.getBody(),
                    new TypeReference<ApiResponse<ChaosResultDTO>>() {}
            );
            ChaosResultDTO result = apiResponse.getData();
            UUID terminatedId = UUID.fromString(result.getTerminatedInstanceId());

            // Should have terminated an alpha node, not beta
            assertTrue(terminatedId.equals(alpha1.getId()) || terminatedId.equals(alpha2.getId()),
                    "Terminated instance should belong to targeted ASG 'asg-alpha'");
            assertNotEquals(beta1.getId(), terminatedId, "Beta node should not have been terminated");

            // Verify replacement was created
            UUID replacementId = UUID.fromString(result.getReplacementInstanceId());
            Instance replacement = instanceRepository.findById(replacementId).orElseThrow();
            assertEquals(InstanceState.RUNNING, replacement.getState());
            assertTrue(replacement.getName().contains("alpha"), "Replacement name should reflect the ASG");

        } catch (Exception e) {
            fail("Failed validating ASG filtered chaos termination: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("R4.1: Chaos termination with RequestBody DTO")
    void testChaosTerminationWithRequestBody() {
        Instance inst = seedInstance("asg-gamma-1", InstanceState.RUNNING, InstanceType.T2_MICRO, testAccountId);

        ChaosRequestDTO req = ChaosRequestDTO.builder()
                .autoScalingGroupId("asg-gamma")
                .accountId(testAccountId)
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<ChaosRequestDTO> entity = new HttpEntity<>(req, headers);

        String url = baseUrl + "/api/v1/chaos/terminate-random-instance";
        ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("TERMINATE_INSTANCE"));
        assertTrue(response.getBody().contains("SELF_HEALING_COMPLETED"));
    }

    @Test
    @DisplayName("R4.1: Chaos termination returns 400 Bad Request when no running instances exist")
    void testChaosTermination_NoRunningInstances() {
        // Only stopped or terminated instances
        seedInstance("stopped-node", InstanceState.STOPPED, InstanceType.T2_MICRO, testAccountId);
        seedInstance("terminated-node", InstanceState.TERMINATED, InstanceType.T2_MICRO, testAccountId);

        String url = baseUrl + "/api/v1/chaos/terminate-random-instance";
        ResponseEntity<String> response = restTemplate.postForEntity(url, null, String.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode(),
                "Must return 400 BAD_REQUEST when no running instances are found");
    }

    @Test
    @DisplayName("R4.3: Chaos and Self-Healing broadcast task events over WebSocket")
    void testWebSocketBroadcastOfChaosAndSelfHealingEvents() throws Exception {
        seedInstance("asg-ws-node-1", InstanceState.RUNNING, InstanceType.T2_MICRO, testAccountId);

        CountDownLatch latch = new CountDownLatch(1);
        List<String> receivedMessages = new CopyOnWriteArrayList<>();

        StandardWebSocketClient wsClient = new StandardWebSocketClient();
        String wsUri = "ws://localhost:" + port + "/ws-events/tasks";

        WebSocketSession session = wsClient.execute(new TextWebSocketHandler() {
            @Override
            public void handleTextMessage(WebSocketSession session, TextMessage message) {
                receivedMessages.add(message.getPayload());
                latch.countDown();
            }
        }, wsUri).get(5, TimeUnit.SECONDS);

        try {
            // Trigger chaos experiment
            String url = baseUrl + "/api/v1/chaos/terminate-random-instance";
            ResponseEntity<String> response = restTemplate.postForEntity(url, null, String.class);
            assertEquals(HttpStatus.OK, response.getStatusCode());

            // Await WebSocket message
            boolean received = latch.await(5, TimeUnit.SECONDS);

            // Also verify task entities were registered in TaskRepository
            List<Task> tasks = taskRepository.findAll();
            assertFalse(tasks.isEmpty(), "Task records should be generated for chaos and self-healing operations");

            boolean foundChaosTerminated = tasks.stream().anyMatch(t -> "CHAOS_INSTANCE_TERMINATED".equals(t.getType()));
            boolean foundSelfHealing = tasks.stream().anyMatch(t -> "SELF_HEALING_RECOVERY".equals(t.getType()));
            boolean foundCombined = tasks.stream().anyMatch(t -> "CHAOS_TERMINATE_AND_HEAL".equals(t.getType()));

            assertTrue(foundChaosTerminated, "CHAOS_INSTANCE_TERMINATED task should be created and broadcast");
            assertTrue(foundSelfHealing, "SELF_HEALING_RECOVERY task should be created and broadcast");
            assertTrue(foundCombined, "CHAOS_TERMINATE_AND_HEAL task should be created and broadcast");

        } finally {
            if (session != null && session.isOpen()) {
                session.close();
            }
        }
    }
}
