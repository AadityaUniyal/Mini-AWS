package com.minicloud.api.compute.runtime;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class DockerComputeRuntime implements ComputeRuntime {

    @Override
    public String getRuntimeType() {
        return "DOCKER";
    }

    @Override
    public RuntimeHandle launch(LaunchSpec spec) {
        String containerName = "minicloud-ec2-" + spec.getInstanceId().toString().substring(0, 8);
        int memoryMb = Math.max(spec.getRamMb(), 128);
        int cpuCores = Math.max(spec.getCpuCores(), 1);

        List<String> cmd = new ArrayList<>();
        cmd.add("docker");
        cmd.add("run");
        cmd.add("-d");
        cmd.add("--name");
        cmd.add(containerName);
        cmd.add("--memory=" + memoryMb + "m");
        cmd.add("--cpus=" + cpuCores);
        cmd.add("--pids-limit=100");
        cmd.add("alpine");
        cmd.add("sh");
        cmd.add("-c");
        cmd.add(spec.getCommand() != null && !spec.getCommand().isBlank() ? spec.getCommand() : "sleep 3600");

        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            Process process = pb.start();
            boolean finished = process.waitFor(15, TimeUnit.SECONDS);
            if (!finished || process.exitValue() != 0) {
                log.warn("Docker run returned non-zero exit code: {}", process.exitValue());
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
            String containerId = reader.readLine();
            if (containerId == null || containerId.isBlank()) {
                containerId = containerName;
            }

            return RuntimeHandle.builder()
                    .instanceId(spec.getInstanceId())
                    .containerId(containerId.trim())
                    .runtimeType("DOCKER")
                    .active(true)
                    .build();
        } catch (Exception e) {
            log.error("Failed to launch Docker container for instance {}: {}", spec.getInstanceId(), e.getMessage());
            throw new RuntimeException("Docker launch failed: " + e.getMessage(), e);
        }
    }

    @Override
    public RuntimeStatus status(RuntimeHandle handle) {
        if (handle.getContainerId() == null) {
            return RuntimeStatus.builder().running(false).build();
        }

        try {
            Process process = new ProcessBuilder("docker", "inspect", "-f", "{{.State.Running}}", handle.getContainerId()).start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
            String line = reader.readLine();
            boolean running = "true".equalsIgnoreCase(line != null ? line.trim() : "");
            return RuntimeStatus.builder()
                    .running(running)
                    .containerId(handle.getContainerId())
                    .statusMessage(running ? "RUNNING" : "STOPPED")
                    .build();
        } catch (Exception e) {
            return RuntimeStatus.builder().running(false).statusMessage("ERROR: " + e.getMessage()).build();
        }
    }

    @Override
    public void stop(RuntimeHandle handle) {
        if (handle.getContainerId() != null) {
            try {
                new ProcessBuilder("docker", "stop", handle.getContainerId()).start().waitFor(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.error("Failed to stop Docker container {}: {}", handle.getContainerId(), e.getMessage());
            }
        }
    }

    @Override
    public void terminate(RuntimeHandle handle) {
        if (handle.getContainerId() != null) {
            try {
                new ProcessBuilder("docker", "rm", "-f", handle.getContainerId()).start().waitFor(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.error("Failed to remove Docker container {}: {}", handle.getContainerId(), e.getMessage());
            }
        }
    }

    @Override
    public CommandResult exec(RuntimeHandle handle, CommandSpec command) {
        long start = System.currentTimeMillis();
        try {
            List<String> cmd = new ArrayList<>();
            cmd.add("docker");
            cmd.add("exec");
            cmd.add(handle.getContainerId());
            cmd.add("sh");
            cmd.add("-c");
            cmd.add(command.getCommand());

            ProcessBuilder pb = new ProcessBuilder(cmd);
            Process process = pb.start();

            boolean finished = process.waitFor(command.getTimeoutSeconds(), TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return CommandResult.builder()
                        .timedOut(true)
                        .exitCode(124)
                        .stderr("Command timed out after " + command.getTimeoutSeconds() + " seconds")
                        .durationMs(System.currentTimeMillis() - start)
                        .build();
            }

            String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);

            return CommandResult.builder()
                    .exitCode(process.exitValue())
                    .stdout(stdout)
                    .stderr(stderr)
                    .durationMs(System.currentTimeMillis() - start)
                    .build();
        } catch (Exception e) {
            return CommandResult.builder()
                    .exitCode(1)
                    .stderr("Execution error: " + e.getMessage())
                    .durationMs(System.currentTimeMillis() - start)
                    .build();
        }
    }
}
