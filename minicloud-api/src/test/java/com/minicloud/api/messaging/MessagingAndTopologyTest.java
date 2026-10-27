package com.minicloud.api.messaging;

import com.minicloud.api.architecture.ArchitectureTopologyService;
import com.minicloud.api.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class MessagingAndTopologyTest {

    private SqsService sqsService;
    private SnsService snsService;

    @Mock
    private VpcRepository vpcRepository;
    @Mock
    private SubnetRepository subnetRepository;
    @Mock
    private InstanceRepository instanceRepository;
    @Mock
    private BucketRepository bucketRepository;
    @Mock
    private FunctionRepository functionRepository;

    private ArchitectureTopologyService topologyService;

    @BeforeEach
    void setUp() {
        sqsService = new SqsService();
        snsService = new SnsService(sqsService);
        topologyService = new ArchitectureTopologyService(
                vpcRepository, subnetRepository, instanceRepository,
                bucketRepository, functionRepository, sqsService, snsService
        );
    }

    @Test
    @DisplayName("SQS: Full lifecycle with visibility timeout and receipt handle deletion")
    void testSqsMessageLifecycle() {
        SqsQueue queue = sqsService.createQueue("order-processing", false, null, 3);
        assertNotNull(queue);
        assertEquals("order-processing", queue.getName());

        // 1. Send message
        QueueMessage sent = sqsService.sendMessage("order-processing", "{\"orderId\": 1001}", null, null);
        assertNotNull(sent.getId());

        // 2. Receive message
        List<QueueMessage> received = sqsService.receiveMessages("order-processing", 1, 30);
        assertEquals(1, received.size());
        assertEquals("{\"orderId\": 1001}", received.get(0).getBody());
        assertNotNull(received.get(0).getReceiptHandle());
        assertEquals(1, received.get(0).getReceiveCount());

        // 3. Queue should now have 0 ready messages and 1 in-flight message
        Map<String, Object> attrs = sqsService.getQueueAttributes("order-processing");
        assertEquals(0, attrs.get("ApproximateNumberOfMessages"));
        assertEquals(1, attrs.get("ApproximateNumberOfMessagesNotVisible"));

        // 4. Delete message
        boolean deleted = sqsService.deleteMessage("order-processing", received.get(0).getReceiptHandle());
        assertTrue(deleted);

        // 5. In-flight count should be 0
        attrs = sqsService.getQueueAttributes("order-processing");
        assertEquals(0, attrs.get("ApproximateNumberOfMessagesNotVisible"));
    }

    @Test
    @DisplayName("SQS FIFO: Message deduplication window prevents duplicate processing")
    void testSqsFifoDeduplication() {
        SqsQueue queue = sqsService.createQueue("financial-tx.fifo", true, null, 3);
        assertTrue(queue.isFifo());

        // Send two messages with identical deduplication ID
        QueueMessage msg1 = sqsService.sendMessage("financial-tx.fifo", "Transfer $50", "grp-1", "dedup-tx-99");
        QueueMessage msg2 = sqsService.sendMessage("financial-tx.fifo", "Transfer $50", "grp-1", "dedup-tx-99");

        // Only 1 message should be enqueued
        List<QueueMessage> received = sqsService.receiveMessages("financial-tx.fifo", 5, 30);
        assertEquals(1, received.size());
    }

    @Test
    @DisplayName("SNS to SQS Fan-Out: Publishing to SNS topic delivers to subscribed SQS queue")
    void testSnsToSqsFanOut() {
        // Setup SQS queue
        sqsService.createQueue("payment-events-queue", false, null, 3);

        // Setup SNS topic & subscription
        SnsTopic topic = snsService.createTopic("payments-topic");
        snsService.subscribe(topic.getArn(), "sqs", "payment-events-queue");

        // Publish to topic
        int delivered = snsService.publish(topic.getArn(), "Payment Received", "{\"amount\": 99.99}");
        assertEquals(1, delivered);

        // Verify message received in SQS queue
        List<QueueMessage> messages = sqsService.receiveMessages("payment-events-queue", 1, 30);
        assertEquals(1, messages.size());
        assertEquals("{\"amount\": 99.99}", messages.get(0).getBody());
    }

    @Test
    @DisplayName("Architecture Topology: Generates valid Mermaid diagram code from cloud state")
    void testArchitectureTopologyAndDiagram() {
        Vpc vpc = Vpc.builder().id(UUID.randomUUID()).name("production-vpc").cidrBlock("10.0.0.0/16").build();
        when(vpcRepository.findAll()).thenReturn(List.of(vpc));
        when(subnetRepository.findAll()).thenReturn(List.of());
        when(instanceRepository.findAll()).thenReturn(List.of());

        Bucket bucket = Bucket.builder().name("production-assets").totalSizeBytes(2048).objectCount(5).build();
        when(bucketRepository.findAll()).thenReturn(List.of(bucket));

        when(functionRepository.findAll()).thenReturn(List.of());

        String mermaid = topologyService.generateMermaidDiagram();
        assertNotNull(mermaid);
        assertTrue(mermaid.startsWith("flowchart TD"));
        assertTrue(mermaid.contains("production-vpc"));
        assertTrue(mermaid.contains("production-assets"));

        Map<String, Object> topo = topologyService.getTopologyGraph();
        assertNotNull(topo);
        assertTrue((Integer) topo.get("totalResources") >= 2);
    }
}
