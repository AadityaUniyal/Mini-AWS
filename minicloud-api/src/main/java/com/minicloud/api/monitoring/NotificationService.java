package com.minicloud.api.monitoring;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class NotificationService {

    /**
     * Simulation of AWS SNS (Simple Notification Service).
     */
    public void sendNotification(String topic, String message) {
        log.info("SENT NOTIFICATION TO TOPIC '{}': {}", topic, message);
    }
}
