package com.minicloud.api.config;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;

/**
 * Custom health indicators for Actuator /actuator/health endpoint.
 * Reports on database, storage directory, and Docker daemon status.
 */
@Component("storageHealth")
class StorageHealthIndicator implements HealthIndicator {

    @Override
    public Health health() {
        Path storagePath = Path.of("minicloud-data/storage");
        if (Files.exists(storagePath) && Files.isWritable(storagePath)) {
            try {
                long usableSpace = Files.getFileStore(storagePath).getUsableSpace();
                return Health.up()
                        .withDetail("path", storagePath.toAbsolutePath().toString())
                        .withDetail("usableSpaceMB", usableSpace / (1024 * 1024))
                        .build();
            } catch (IOException e) {
                return Health.down().withException(e).build();
            }
        }
        return Health.down().withDetail("path", storagePath.toString()).withDetail("reason", "not writable").build();
    }
}

@Component("dockerHealth")
class DockerHealthIndicator implements HealthIndicator {

    @Override
    public Health health() {
        try {
            Process p = new ProcessBuilder("docker", "info").redirectErrorStream(true).start();
            boolean finished = p.waitFor(java.util.concurrent.TimeUnit.SECONDS.toMillis(5), java.util.concurrent.TimeUnit.MILLISECONDS);
            if (finished && p.exitValue() == 0) {
                return Health.up().withDetail("status", "Docker daemon is running").build();
            }
            return Health.up().withDetail("status", "INACTIVE").withDetail("warning", "Docker daemon not responding (Subprocess fallback will be used)").build();
        } catch (Exception e) {
            return Health.up().withDetail("status", "INACTIVE").withDetail("warning", "Docker not installed or not accessible (Subprocess fallback will be used)").build();
        }
    }
}
