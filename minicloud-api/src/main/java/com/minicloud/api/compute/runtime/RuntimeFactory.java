package com.minicloud.api.compute.runtime;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RuntimeFactory {

    private final DockerComputeRuntime dockerComputeRuntime;
    private final LocalProcessRuntime localProcessRuntime;
    private final MockComputeRuntime mockComputeRuntime;

    @Value("${minicloud.execution.mode:AUTO}")
    private String executionMode;

    public ComputeRuntime getComputeRuntime() {
        if ("MOCK".equalsIgnoreCase(executionMode)) {
            return mockComputeRuntime;
        }
        if ("LOCAL".equalsIgnoreCase(executionMode) || "LOCAL_DEV".equalsIgnoreCase(executionMode)) {
            return localProcessRuntime;
        }
        if ("DOCKER".equalsIgnoreCase(executionMode)) {
            return dockerComputeRuntime;
        }

        // AUTO mode: check if Docker is accessible
        if (isDockerRunning()) {
            return dockerComputeRuntime;
        }
        return localProcessRuntime;
    }

    private boolean isDockerRunning() {
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"docker", "info"});
            return process.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
