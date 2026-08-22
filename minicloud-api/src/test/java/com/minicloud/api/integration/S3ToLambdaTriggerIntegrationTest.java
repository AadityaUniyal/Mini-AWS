package com.minicloud.api.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minicloud.api.config.TestSecurityConfig;
import com.minicloud.api.domain.*;
import com.minicloud.api.dto.ApiResponse;
import com.minicloud.api.lambda.*;
import com.minicloud.api.service.TaskService;
import com.minicloud.api.storage.S3TriggerDispatcher;
import com.minicloud.api.storage.S3TriggerService;
import com.minicloud.api.storage.StorageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.test.context.TestPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive integration test suite for Milestone 2 (R2):
 * S3-to-Lambda Asynchronous Event Triggers & WebSocket Streaming.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "java.awt.headless=true",
        "minicloud.system-tray.enabled=false",
        "minicloud.h2.tcp.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:m2testdb;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.profiles.active=test",
        "minicloud.storage.base-path=./target/test-storage",
        "minicloud.lambda.tmp-dir=./target/test-lambda-tmp"
})
@Import(TestSecurityConfig.class)
public class S3ToLambdaTriggerIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BucketRepository bucketRepository;

    @Autowired
    private FunctionRepository functionRepository;

    @Autowired
    private S3LambdaTriggerRepository triggerRepository;

    @Autowired
    private LambdaInvocationLogRepository logRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private S3TriggerService s3TriggerService;

    @Autowired
    private S3TriggerDispatcher s3TriggerDispatcher;

    @Autowired
    private LambdaExecutionService lambdaExecutionService;

    @Autowired
    private StorageService storageService;

    @Autowired
    private TaskService taskService;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${minicloud.storage.base-path:./target/test-storage}")
    private String storageBasePath;

    @Value("${minicloud.lambda.tmp-dir:./target/test-lambda-tmp}")
    private String lambdaTmpDir;

    private String baseUrl;
    private User testUser;
    private UUID testUserId;
    private String testBucketName;

    @BeforeEach
    void setUp() throws IOException {
        baseUrl = "http://localhost:" + port;

        // Ensure directories exist
        Files.createDirectories(Path.of(storageBasePath));
        Files.createDirectories(Path.of(lambdaTmpDir));

        // Clean tables
        triggerRepository.deleteAll();
        logRepository.deleteAll();
        functionRepository.deleteAll();
        bucketRepository.deleteAll();
        taskRepository.deleteAll();
        userRepository.deleteAll();

        // Create test user
        String username = "m2-user-" + System.currentTimeMillis();
        testUser = User.builder()
                .username(username)
                .email("m2user@example.com")
                .passwordHash("hashed")
                .accountId("acct-m2-1001")
                .role(UserRole.ADMIN)
                .enabled(true)
                .rootUser(false)
                .build();
        testUser = userRepository.save(testUser);
        testUserId = testUser.getId();

        // Create test bucket
        testBucketName = "test-media-bucket-" + UUID.randomUUID().toString().substring(0, 8);
        Bucket bucket = Bucket.builder()
                .name(testBucketName)
                .userId(testUserId)
                .accountId(testUser.getAccountId())
                .region("us-east-1")
                .build();
        bucketRepository.save(bucket);
        storageService.createBucketDirectory(testUserId, testBucketName);
    }

    @AfterEach
    void tearDown() {
        triggerRepository.deleteAll();
        logRepository.deleteAll();
        functionRepository.deleteAll();
        bucketRepository.deleteAll();
        taskRepository.deleteAll();
        userRepository.deleteAll();
    }

    /**
     * Test Requirement 2.2: REST CRUD API for triggers
     * POST /api/v1/storage/triggers
     * GET /api/v1/storage/triggers
     * GET /api/v1/storage/triggers/{id}
     * DELETE /api/v1/storage/triggers/{id}
     */
    @Test
    void testTriggerCrudRestApi() {
        // 1. Create Trigger
        CreateTriggerRequest createReq = new CreateTriggerRequest(
                testBucketName,
                "thumbnail-generator",
                List.of("s3:ObjectCreated:*"),
                true,
                testUserId
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<CreateTriggerRequest> requestEntity = new HttpEntity<>(createReq, headers);

        ResponseEntity<String> postResponse = restTemplate.postForEntity(
                baseUrl + "/api/v1/storage/triggers",
                requestEntity,
                String.class
        );

        assertEquals(HttpStatus.CREATED, postResponse.getStatusCode(), "POST /api/v1/storage/triggers should return 201 Created");
        assertNotNull(postResponse.getBody());

        try {
            JsonNode root = objectMapper.readTree(postResponse.getBody());
            assertTrue(root.path("success").asBoolean(), "Response success should be true");
            JsonNode data = root.path("data");
            assertNotNull(data.path("id").asText(), "Trigger ID must be present");
            assertEquals(testBucketName, data.path("bucketName").asText());
            assertEquals("thumbnail-generator", data.path("functionName").asText());
            assertTrue(data.path("enabled").asBoolean());

            String triggerId = data.path("id").asText();

            // 2. Get Trigger by ID
            ResponseEntity<String> getByIdResponse = restTemplate.getForEntity(
                    baseUrl + "/api/v1/storage/triggers/" + triggerId,
                    String.class
            );
            assertEquals(HttpStatus.OK, getByIdResponse.getStatusCode());
            JsonNode getByIdData = objectMapper.readTree(getByIdResponse.getBody()).path("data");
            assertEquals(triggerId, getByIdData.path("id").asText());

            // 3. List Triggers with bucketName query param
            ResponseEntity<String> listResponse = restTemplate.getForEntity(
                    baseUrl + "/api/v1/storage/triggers?bucketName=" + testBucketName,
                    String.class
            );
            assertEquals(HttpStatus.OK, listResponse.getStatusCode());
            JsonNode listData = objectMapper.readTree(listResponse.getBody()).path("data");
            assertTrue(listData.isArray());
            assertEquals(1, listData.size());
            assertEquals(triggerId, listData.get(0).path("id").asText());

            // 4. Delete Trigger
            ResponseEntity<Void> deleteResponse = restTemplate.exchange(
                    baseUrl + "/api/v1/storage/triggers/" + triggerId,
                    HttpMethod.DELETE,
                    null,
                    Void.class
            );
            assertEquals(HttpStatus.NO_CONTENT, deleteResponse.getStatusCode(), "DELETE trigger should return 204 No Content");

            // 5. Verify Trigger is deleted
            ResponseEntity<String> postDeleteListResponse = restTemplate.getForEntity(
                    baseUrl + "/api/v1/storage/triggers?bucketName=" + testBucketName,
                    String.class
            );
            JsonNode postDeleteData = objectMapper.readTree(postDeleteListResponse.getBody()).path("data");
            assertEquals(0, postDeleteData.size(), "Trigger list should be empty after deletion");

        } catch (Exception e) {
            fail("Failed parsing JSON response: " + e.getMessage());
        }
    }

    /**
     * Test Requirement 2.2: CLI / Lambda Alias Endpoints
     * POST /api/v1/lambda/triggers
     * GET /api/v1/lambda/triggers
     * DELETE /api/v1/lambda/triggers/{id}
     */
    @Test
    void testLambdaTriggerAliases() {
        CreateTriggerRequest createReq = new CreateTriggerRequest(
                testBucketName,
                "pdf-parser",
                List.of("s3:ObjectCreated:Put"),
                true,
                testUserId
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<CreateTriggerRequest> requestEntity = new HttpEntity<>(createReq, headers);

        ResponseEntity<String> postResponse = restTemplate.postForEntity(
                baseUrl + "/api/v1/lambda/triggers",
                requestEntity,
                String.class
        );

        assertEquals(HttpStatus.CREATED, postResponse.getStatusCode());
        try {
            JsonNode root = objectMapper.readTree(postResponse.getBody());
            String triggerId = root.path("data").path("id").asText();

            ResponseEntity<String> listResponse = restTemplate.getForEntity(
                    baseUrl + "/api/v1/lambda/triggers?bucketName=" + testBucketName,
                    String.class
            );
            assertEquals(HttpStatus.OK, listResponse.getStatusCode());
            JsonNode listData = objectMapper.readTree(listResponse.getBody()).path("data");
            assertEquals(1, listData.size());

            // Delete via lambda trigger route
            ResponseEntity<String> deleteResponse = restTemplate.exchange(
                    baseUrl + "/api/v1/lambda/triggers/" + triggerId,
                    HttpMethod.DELETE,
                    null,
                    String.class
            );
            assertEquals(HttpStatus.OK, deleteResponse.getStatusCode());
        } catch (Exception e) {
            fail("Exception in lambda trigger aliases: " + e.getMessage());
        }
    }

    /**
     * Test Requirement 2.3: Standard AWS S3 Event Notification JSON Schema
     * Verifies standard Records[0].s3.bucket.name, Records[0].s3.object.key, Records[0].eventName, etc.
     */
    @Test
    void testStandardAwsS3EventPayloadStructure() throws Exception {
        S3LambdaTrigger trigger = S3LambdaTrigger.builder()
                .id(UUID.randomUUID())
                .bucketName(testBucketName)
                .functionName("data-indexer")
                .events(List.of("s3:ObjectCreated:*"))
                .enabled(true)
                .build();

        String objectKey = "uploads/2026/report.pdf";
        long sizeBytes = 2048576L;
        String eTag = "5d41402abc4b2a76b9719d911017c592";

        String jsonPayload = s3TriggerDispatcher.buildS3EventPayload(
                trigger,
                testBucketName,
                objectKey,
                sizeBytes,
                eTag,
                testUserId.toString()
        );

        assertNotNull(jsonPayload, "Payload JSON must not be null");

        JsonNode root = objectMapper.readTree(jsonPayload);
        assertTrue(root.has("Records"), "Payload must contain 'Records' root array");
        JsonNode records = root.get("Records");
        assertTrue(records.isArray() && records.size() == 1, "Records must be an array of size 1");

        JsonNode record = records.get(0);
        assertEquals("2.1", record.path("eventVersion").asText());
        assertEquals("aws:s3", record.path("eventSource").asText());
        assertEquals("ObjectCreated:Put", record.path("eventName").asText());
        assertEquals(testUserId.toString(), record.path("userIdentity").path("principalId").asText());

        JsonNode s3Node = record.path("s3");
        assertEquals("1.0", s3Node.path("s3SchemaVersion").asText());
        assertEquals(trigger.getId().toString(), s3Node.path("configurationId").asText());

        // Bucket validation
        JsonNode bucketNode = s3Node.path("bucket");
        assertEquals(testBucketName, bucketNode.path("name").asText());
        assertEquals("arn:aws:s3:::" + testBucketName, bucketNode.path("arn").asText());
        assertEquals(testUserId.toString(), bucketNode.path("ownerIdentity").path("principalId").asText());

        // Object validation
        JsonNode objectNode = s3Node.path("object");
        assertEquals(objectKey, objectNode.path("key").asText());
        assertEquals(sizeBytes, objectNode.path("size").asLong());
        assertEquals(eTag, objectNode.path("eTag").asText());
        assertNotNull(objectNode.path("sequencer").asText());
        assertFalse(objectNode.path("sequencer").asText().isBlank());
    }

    /**
     * Test Requirement 2.3: End-to-End S3 Upload to Lambda Asynchronous Invocation
     * Sets up a real Python Lambda function, registers trigger, uploads file to bucket,
     * and verifies that the Lambda function runs, receives the JSON payload over stdin,
     * and logs the execution.
     */
    @Test
    void testEndToEndUploadTriggersLambdaExecution() throws Exception {
        // 1. Create a Python script file that parses the standard AWS S3 event JSON
        Path scriptDir = Path.of(lambdaTmpDir, "test-scripts");
        Files.createDirectories(scriptDir);
        Path scriptPath = scriptDir.resolve("s3_processor.py");

        String pythonCode = """
import sys
import json

raw_input = sys.stdin.read()
if raw_input:
    event = json.loads(raw_input)
    record = event['Records'][0]
    bucket = record['s3']['bucket']['name']
    key = record['s3']['object']['key']
    size = record['s3']['object']['size']
    event_name = record['eventName']
    print(f"SUCCESS_TRIGGER: Event={event_name}, Bucket={bucket}, Key={key}, Size={size}")
else:
    print("NO_PAYLOAD_RECEIVED")
""";
        Files.writeString(scriptPath, pythonCode);

        // 2. Register Lambda Function in Database
        String functionName = "s3-event-consumer-" + UUID.randomUUID().toString().substring(0, 6);
        Function fn = Function.builder()
                .name(functionName)
                .description("Processes S3 upload notifications")
                .userId(testUserId)
                .accountId(testUser.getAccountId())
                .runtime(Function.Runtime.PYTHON)
                .handler(scriptPath.toAbsolutePath().toString())
                .codePath(scriptPath.toAbsolutePath().toString())
                .memoryMb(128)
                .timeoutSec(10)
                .status(Function.FunctionStatus.ACTIVE)
                .build();
        functionRepository.save(fn);

        // 3. Register S3 Lambda Trigger
        S3LambdaTrigger trigger = S3LambdaTrigger.builder()
                .bucketName(testBucketName)
                .functionName(functionName)
                .events(List.of("s3:ObjectCreated:*"))
                .enabled(true)
                .userId(testUserId)
                .accountId(testUser.getAccountId())
                .build();
        triggerRepository.save(trigger);

        // 4. Perform S3 Multipart Upload via REST API
        String uploadFileName = "sample_image.png";
        byte[] fileBytes = "MINICLOUD_S3_TEST_PAYLOAD_CONTENT_BYTES_12345".getBytes();

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        ByteArrayResource fileResource = new ByteArrayResource(fileBytes) {
            @Override
            public String getFilename() {
                return uploadFileName;
            }
        };
        body.add("file", fileResource);

        HttpHeaders uploadHeaders = new HttpHeaders();
        uploadHeaders.setContentType(MediaType.MULTIPART_FORM_DATA);
        HttpEntity<MultiValueMap<String, Object>> uploadEntity = new HttpEntity<>(body, uploadHeaders);

        String uploadUrl = baseUrl + "/api/v1/storage/buckets/" + testBucketName + "/upload?userId=" + testUserId;
        ResponseEntity<String> uploadResponse = restTemplate.postForEntity(uploadUrl, uploadEntity, String.class);

        assertEquals(HttpStatus.ACCEPTED, uploadResponse.getStatusCode(), "S3 upload should return 202 Accepted");

        // 5. Wait for asynchronous trigger invocation to complete
        boolean invocationFound = false;
        String capturedOutput = null;

        for (int i = 0; i < 30; i++) {
            Thread.sleep(500);
            var logs = logRepository.findAll();
            var matchingLog = logs.stream()
                    .filter(l -> functionName.equals(l.getFunctionName()))
                    .findFirst();

            if (matchingLog.isPresent()) {
                invocationFound = true;
                capturedOutput = matchingLog.get().getOutput();
                break;
            }
        }

        assertTrue(invocationFound, "Lambda function should have been asynchronously invoked by S3 upload trigger");
        assertNotNull(capturedOutput, "Lambda invocation output should not be null");
        assertTrue(capturedOutput.contains("SUCCESS_TRIGGER"),
                "Lambda output should contain SUCCESS_TRIGGER confirmation. Output was: " + capturedOutput);
        assertTrue(capturedOutput.contains("Bucket=" + testBucketName),
                "Lambda output should contain the uploaded bucket name. Output was: " + capturedOutput);
        assertTrue(capturedOutput.contains("Key=" + uploadFileName),
                "Lambda output should contain the uploaded object key. Output was: " + capturedOutput);
    }

    /**
     * Test Requirement 2.3: Disabled Triggers Must Not Trigger Lambda Invocations
     */
    @Test
    void testDisabledTriggerDoesNotInvokeLambda() throws Exception {
        String functionName = "disabled-trigger-func-" + UUID.randomUUID().toString().substring(0, 6);
        Function fn = Function.builder()
                .name(functionName)
                .description("Should not be invoked")
                .userId(testUserId)
                .runtime(Function.Runtime.PYTHON)
                .handler("dummy.py")
                .status(Function.FunctionStatus.ACTIVE)
                .build();
        functionRepository.save(fn);

        // Register DISABLED trigger
        S3LambdaTrigger trigger = S3LambdaTrigger.builder()
                .bucketName(testBucketName)
                .functionName(functionName)
                .events(List.of("s3:ObjectCreated:*"))
                .enabled(false) // Disabled
                .userId(testUserId)
                .build();
        triggerRepository.save(trigger);

        // Dispatch upload event
        CompletableFuture<List<LambdaExecutionService.InvocationResult>> future = s3TriggerDispatcher.dispatchUploadEvent(
                testBucketName,
                "disabled-test.txt",
                100L,
                "etag123",
                testUserId,
                testUser.getAccountId()
        );

        List<LambdaExecutionService.InvocationResult> results = future.get(5, TimeUnit.SECONDS);
        assertTrue(results.isEmpty(), "Disabled trigger should result in 0 executions");

        var logs = logRepository.findAll().stream()
                .filter(l -> functionName.equals(l.getFunctionName()))
                .toList();
        assertEquals(0, logs.size(), "No invocation logs should exist for disabled trigger");
    }

    /**
     * Test Requirement 2.4: WebSocket Task Streaming on /ws-events/tasks
     * Connects a WebSocket client, performs upload and trigger dispatch, and verifies
     * that real-time task update messages are streamed over WebSocket.
     */
    @Test
    void testWebSocketTaskStreaming() throws Exception {
        StandardWebSocketClient wsClient = new StandardWebSocketClient();
        BlockingQueue<String> receivedMessages = new LinkedBlockingQueue<>();

        String wsUrl = "ws://localhost:" + port + "/ws-events/tasks";

        WebSocketSession session = wsClient.execute(new TestWebSocketClientHandler(receivedMessages), wsUrl)
                .get(5, TimeUnit.SECONDS);

        assertTrue(session.isOpen(), "WebSocket session should be open");

        // Emit task updates via TaskService
        Task task = taskService.createTask(
                "S3_TRIGGER_INVOCATION",
                "Testing WebSocket streaming for S3 trigger",
                testUserId,
                testUser.getAccountId()
        );

        taskService.updateProgress(task.getId(), 50, "RUNNING", null);
        taskService.updateProgress(task.getId(), 100, "COMPLETED", "Task finished successfully");

        // Wait for WebSocket messages
        List<String> messages = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            String msg = receivedMessages.poll(2, TimeUnit.SECONDS);
            if (msg != null) {
                messages.add(msg);
            }
        }

        assertFalse(messages.isEmpty(), "WebSocket should have received task update messages");

        boolean foundMatchingTask = messages.stream()
                .anyMatch(m -> m.contains(task.getId().toString()) && m.contains("S3_TRIGGER_INVOCATION"));
        assertTrue(foundMatchingTask, "WebSocket message should contain S3_TRIGGER_INVOCATION task updates");

        session.close();
    }

    static class TestWebSocketClientHandler extends TextWebSocketHandler {
        private final BlockingQueue<String> queue;

        public TestWebSocketClientHandler(BlockingQueue<String> queue) {
            this.queue = queue;
        }

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) {
            queue.offer(message.getPayload());
        }
    }
}
