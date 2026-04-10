package com.minicloud.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * MiniCloud API — Spring Boot application entry point.
 *
 * @EnableScheduling activates @Scheduled methods (spec § 11):
 *   used in MetricsService for background CPU/RAM metric collection
 *   into a rolling ConcurrentLinkedDeque buffer every 5 seconds.
 */
@SpringBootApplication
@EnableScheduling
public class MiniCloudApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(MiniCloudApiApplication.class, args);
    }
}
