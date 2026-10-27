package com.minicloud.api.messaging;

import com.minicloud.api.service.LambdaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class SnsService {

    private final SqsService sqsService;
    private final Map<String, SnsTopic> topics = new ConcurrentHashMap<>();

    public SnsTopic createTopic(String name) {
        String arn = "arn:aws:sns:us-east-1:123456789012:" + name;
        SnsTopic topic = SnsTopic.builder()
                .name(name)
                .arn(arn)
                .build();
        topics.put(arn, topic);
        topics.put(name, topic); // allow lookup by name or ARN
        log.info("Created SNS Topic: {} ({})", name, arn);
        return topic;
    }

    public Optional<SnsTopic> getTopic(String nameOrArn) {
        return Optional.ofNullable(topics.get(nameOrArn));
    }

    public List<SnsTopic> listTopics() {
        return topics.values().stream().distinct().toList();
    }

    public SnsSubscription subscribe(String topicNameOrArn, String protocol, String endpoint) {
        SnsTopic topic = topics.get(topicNameOrArn);
        if (topic == null) {
            throw new IllegalArgumentException("Topic does not exist: " + topicNameOrArn);
        }

        SnsSubscription subscription = SnsSubscription.builder()
                .topicArn(topic.getArn())
                .protocol(protocol.toLowerCase())
                .endpoint(endpoint)
                .build();

        topic.getSubscriptions().add(subscription);
        log.info("Subscribed {} ({}) to topic {}", endpoint, protocol, topic.getName());
        return subscription;
    }

    /**
     * Publish a message to all subscribers (Fan-out Pattern).
     */
    public int publish(String topicNameOrArn, String subject, String message) {
        SnsTopic topic = topics.get(topicNameOrArn);
        if (topic == null) {
            throw new IllegalArgumentException("Topic does not exist: " + topicNameOrArn);
        }

        int delivered = 0;
        for (SnsSubscription sub : topic.getSubscriptions()) {
            try {
                if ("sqs".equalsIgnoreCase(sub.getProtocol())) {
                    // Extract queue name from ARN or endpoint
                    String queueName = sub.getEndpoint();
                    if (queueName.contains(":")) {
                        queueName = queueName.substring(queueName.lastIndexOf(":") + 1);
                    }
                    sqsService.sendMessage(queueName, message, null, null);
                    delivered++;
                } else {
                    log.info("Simulated message delivery to {} endpoint: {}", sub.getProtocol(), sub.getEndpoint());
                    delivered++;
                }
            } catch (Exception e) {
                log.warn("Failed to deliver SNS message to subscription {}: {}", sub.getSubscriptionArn(), e.getMessage());
            }
        }

        log.info("Published message to topic {} (delivered to {} subscribers)", topic.getName(), delivered);
        return delivered;
    }
}
