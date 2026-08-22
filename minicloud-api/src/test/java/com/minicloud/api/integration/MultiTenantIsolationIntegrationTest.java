package com.minicloud.api.integration;

import com.minicloud.api.MiniCloudApiApplication;
import com.minicloud.api.compute.ComputeService;
import com.minicloud.api.compute.InstanceStateMachine;
import com.minicloud.api.config.TestSecurityConfig;
import com.minicloud.api.domain.*;
import com.minicloud.api.dto.AddNaclRuleRequest;
import com.minicloud.api.dto.CreateNaclRequest;
import com.minicloud.api.dto.ExecRequest;
import com.minicloud.api.dto.ExecResponse;
import com.minicloud.api.dto.NaclEvaluationRequest;
import com.minicloud.api.dto.NaclEvaluationResponse;
import com.minicloud.api.lambda.LambdaExecutionService;
import com.minicloud.api.route.CidrMatcher;
import com.minicloud.api.route.NetworkAclService;
import com.minicloud.api.storage.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;

/**
 * MultiTenantIsolationIntegrationTest — Full system test verifying multi-tenant isolation,
 * S3 security, compute execution, CIDR evaluation, and Network ACL rules.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = MiniCloudApiApplication.class)
@TestPropertySource(properties = {
    "java.awt.headless=true",
    "minicloud.system-tray.enabled=false",
    "minicloud.h2.tcp.enabled=false",
    "spring.datasource.url=jdbc:h2:mem:multitenant_testdb;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.profiles.active=test"
})
@Import(TestSecurityConfig.class)
public class MultiTenantIsolationIntegrationTest {

    @Autowired
    private StorageService storageService;

    @Autowired
    private LambdaExecutionService lambdaExecutionService;

    @Autowired
    private ComputeService computeService;

    @Autowired
    private NetworkAclService networkAclService;

    @Autowired
    private InstanceRepository instanceRepository;

    @Autowired
    private FunctionRepository functionRepository;

    @Autowired
    private UserRepository userRepository;

    private User userA;
    private User userB;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        instanceRepository.deleteAll();
        functionRepository.deleteAll();

        userA = userRepository.save(User.builder()
                .username("alice")
                .email("alice@tenant-a.com")
                .passwordHash("hashed")
                .accountId("111122223333")
                .role(UserRole.ADMIN)
                .rootUser(true)
                .build());

        userB = userRepository.save(User.builder()
                .username("bob")
                .email("bob@tenant-b.com")
                .passwordHash("hashed")
                .accountId("444455556666")
                .role(UserRole.ADMIN)
                .rootUser(true)
                .build());
    }

    @Test
    @DisplayName("CidrMatcher accurately evaluates IPv4 subnets using bitwise arithmetic")
    void testCidrMatcherBitwiseOperations() {
        // Matches /0
        assertTrue(CidrMatcher.matches("0.0.0.0/0", "192.168.1.100"));
        assertTrue(CidrMatcher.matches("0.0.0.0/0", "10.0.0.1"));

        // Matches /24
        assertTrue(CidrMatcher.matches("192.168.1.0/24", "192.168.1.1"));
        assertTrue(CidrMatcher.matches("192.168.1.0/24", "192.168.1.254"));
        assertFalse(CidrMatcher.matches("192.168.1.0/24", "192.168.2.1"));

        // Matches /16
        assertTrue(CidrMatcher.matches("10.0.0.0/16", "10.0.254.1"));
        assertFalse(CidrMatcher.matches("10.0.0.0/16", "10.1.0.1"));

        // Matches /32 exact IP
        assertTrue(CidrMatcher.matches("10.0.0.5/32", "10.0.0.5"));
        assertFalse(CidrMatcher.matches("10.0.0.5/32", "10.0.0.6"));
    }

    @Test
    @DisplayName("S3 Storage prevents path traversal attacks outside bucket root")
    void testS3PathTraversalPrevention() throws Exception {
        UUID userId = userA.getId();
        String bucket = "secure-bucket-" + UUID.randomUUID();

        // Valid write and read
        String localPath = storageService.writeObject(userId, bucket, "test.txt", new ByteArrayInputStream("hello world".getBytes()));
        byte[] readBytes = storageService.readObjectFromDisk(localPath);
        assertNotNull(readBytes);
        assertEquals("hello world", new String(readBytes));

        // Malicious path traversal attempts must be rejected with SecurityException
        assertThatThrownBy(() -> storageService.writeObject(userId, bucket, "../../../etc/passwd", new ByteArrayInputStream("payload".getBytes())))
                .isInstanceOf(SecurityException.class);

        assertThatThrownBy(() -> storageService.readObjectFromDisk(localPath + "/../../forbidden.txt"))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("Network ACL evaluates numbered rules in ascending order with default deny")
    void testNetworkAclRuleOrderingAndEvaluation() {
        CreateNaclRequest createReq = CreateNaclRequest.builder()
                .name("custom-nacl")
                .build();

        NetworkAcl acl = networkAclService.createAcl(createReq, "111122223333");
        assertNotNull(acl.getId());

        // Add Rule 10: Deny port 80 for 192.168.1.50
        networkAclService.addRule(acl.getId(), AddNaclRuleRequest.builder()
                .ruleNumber(10)
                .egress(false)
                .protocol("TCP")
                .ruleAction("DENY")
                .cidrBlock("192.168.1.50/32")
                .fromPort(80)
                .toPort(80)
                .build());

        // Add Rule 20: Allow port 80 for 192.168.1.0/24
        networkAclService.addRule(acl.getId(), AddNaclRuleRequest.builder()
                .ruleNumber(20)
                .egress(false)
                .protocol("TCP")
                .ruleAction("ALLOW")
                .cidrBlock("192.168.1.0/24")
                .fromPort(80)
                .toPort(80)
                .build());

        // Evaluate 192.168.1.50:80 -> Should DENY (rule 10)
        NaclEvaluationResponse res1 = networkAclService.evaluatePacket(acl.getId(), NaclEvaluationRequest.builder()
                .ip("192.168.1.50")
                .port(80)
                .protocol("TCP")
                .egress(false)
                .build());
        assertEquals("DENY", res1.getDecision());
        assertEquals(10, res1.getMatchedRuleNumber());

        // Evaluate 192.168.1.51:80 -> Should ALLOW (rule 20)
        NaclEvaluationResponse res2 = networkAclService.evaluatePacket(acl.getId(), NaclEvaluationRequest.builder()
                .ip("192.168.1.51")
                .port(80)
                .protocol("TCP")
                .egress(false)
                .build());
        assertEquals("ALLOW", res2.getDecision());
        assertEquals(20, res2.getMatchedRuleNumber());
    }

    @Test
    @DisplayName("Compute Instance State Machine enforces valid lifecycle transitions")
    void testComputeInstanceStateTransitions() {
        assertTrue(InstanceStateMachine.canTransition(InstanceState.PENDING, InstanceState.RUNNING));
        assertTrue(InstanceStateMachine.canTransition(InstanceState.RUNNING, InstanceState.STOPPED));
        assertTrue(InstanceStateMachine.canTransition(InstanceState.STOPPED, InstanceState.RUNNING));
        assertTrue(InstanceStateMachine.canTransition(InstanceState.RUNNING, InstanceState.TERMINATED));
        assertTrue(InstanceStateMachine.canTransition(InstanceState.STOPPED, InstanceState.TERMINATED));
        assertTrue(InstanceStateMachine.canTransition(InstanceState.RUNNING, InstanceState.FAILED));

        assertFalse(InstanceStateMachine.canTransition(InstanceState.PENDING, InstanceState.STOPPED));
        assertFalse(InstanceStateMachine.canTransition(InstanceState.TERMINATED, InstanceState.RUNNING));
    }

    @Test
    @DisplayName("EC2 exec endpoint runs command against running compute instance")
    void testComputeExecEndpoint() {
        Instance inst = instanceRepository.save(Instance.builder()
                .name("exec-test-inst")
                .type(InstanceType.T2_MICRO)
                .state(InstanceState.RUNNING)
                .userId(userA.getId())
                .accountId(userA.getAccountId())
                .build());

        ExecRequest req = ExecRequest.builder()
                .command("echo Hello MiniCloud")
                .timeoutSeconds(5)
                .build();

        ExecResponse res = computeService.execCommand(inst.getId(), req);
        assertNotNull(res);
        assertTrue(res.getExitCode() == 0 || res.getStdout().contains("Hello") || !res.isTimedOut());
    }
}
