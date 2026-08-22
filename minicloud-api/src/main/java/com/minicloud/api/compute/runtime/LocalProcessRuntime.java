package com.minicloud.api.compute.runtime;

import com.minicloud.api.compute.CommandSanitizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class LocalProcessRuntime implements ComputeRuntime {

    private final Map<UUID, Process> runningProcesses = new ConcurrentHashMap<>();

    @Override
    public String getRuntimeType() {
        return "LOCAL";
    }

    @Override
    public RuntimeHandle launch(LaunchSpec spec) {
        String sanitized = CommandSanitizer.sanitize(spec.getCommand() != null && !spec.getCommand().isBlank() ? spec.getCommand() : "sleep 3600");
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");

        ProcessBuilder pb;
        if (isWindows) {
            pb = new ProcessBuilder("cmd.exe", "/c", sanitized);
        } else {
            pb = new ProcessBuilder("sh", "-c", sanitized);
        }

        try {
            Process process = pb.start();
            runningProcesses.put(spec.getInstanceId(), process);
            long pid = process.pid();

            return RuntimeHandle.builder()
                    .instanceId(spec.getInstanceId())
                    .pid(pid)
                    .runtimeType("LOCAL")
                    .active(true)
                    .build();
        } catch (Exception e) {
            log.error("Failed to launch local process for instance {}: {}", spec.getInstanceId(), e.getMessage());
            throw new RuntimeException("Local process launch failed: " + e.getMessage(), e);
        }
    }

    @Override
    public RuntimeStatus status(RuntimeHandle handle) {
        Process p = runningProcesses.get(handle.getInstanceId());
        if (p != null && p.isAlive()) {
            return RuntimeStatus.builder()
                    .running(true)
                    .pid(p.pid())
                    .statusMessage("RUNNING")
                    .build();
        }
        return RuntimeStatus.builder().running(false).statusMessage("STOPPED").build();
    }

    @Override
    public void stop(RuntimeHandle handle) {
        Process p = runningProcesses.remove(handle.getInstanceId());
        if (p != null && p.isAlive()) {
            p.destroy();
        }
    }

    @Override
    public void terminate(RuntimeHandle handle) {
        Process p = runningProcesses.remove(handle.getInstanceId());
        if (p != null && p.isAlive()) {
            p.destroyForcibly();
        }
    }

    @Override
    public CommandResult exec(RuntimeHandle handle, CommandSpec command) {
        long start = System.currentTimeMillis();
        String sanitized = CommandSanitizer.sanitize(command.getCommand());
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");

        ProcessBuilder pb;
        if (isWindows) {
            pb = new ProcessBuilder("cmd.exe", "/c", sanitized);
        } else {
            pb = new ProcessBuilder("sh", "-c", sanitized);
        }

        try {
            Process process = pb.start();
            boolean finished = process.waitFor(command.getTimeoutSeconds(), TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return CommandResult.builder()
                        .timedOut(true)
                        .exitCode(124)
                        .stdout("")
                        .stderr("Command timed out after " + command.getTimeoutSeconds() + " seconds")
                        .durationMs(System.currentTimeMillis() - start)
                        .build();
            }

            String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);

            return CommandResult.builder()
                    .exitCode(process.exitValue())
                    .stdout(stdout != null ? stdout : "")
                    .stderr(stderr != null ? stderr : "")
                    .durationMs(System.currentTimeMillis() - start)
                    .build();
        } catch (Exception e) {
            log.warn("Command execution failed: {}", e.getMessage());
            return CommandResult.builder()
                    .exitCode(1)
                    .stdout("")
                    .stderr(e.getMessage() != null ? e.getMessage() : "Execution failed")
                    .durationMs(System.currentTimeMillis() - start)
                    .build();
        }
    }
}
