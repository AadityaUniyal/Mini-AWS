package com.minicloud.api.compute;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ProcessManager — OS-level process control for MiniCloud.
 * Tracks and manages background processes (Instance simulations, RDS H2, Lambda workers).
 */
@Slf4j
@Component
public class ProcessManager {

    private final Map<String, Process> processes = new ConcurrentHashMap<>();
    private final boolean dockerAvailable;

    public ProcessManager() {
        this.dockerAvailable = checkDockerAvailable();
        log.info("ProcessManager initialized. Docker available: {}", dockerAvailable);
    }

    private boolean checkDockerAvailable() {
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"docker", "info"});
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isDockerAvailable() {
        return dockerAvailable;
    }

    public int launchProcess(String id, String command) throws IOException {
        // 1. Sanitize incoming command
        String sanitizedCommand = CommandSanitizer.sanitize(command);
        
        log.info("Launching process [{}]: {}", id, sanitizedCommand);

        ProcessBuilder pb;
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");

        if (dockerAvailable) {
            log.info("Docker is running. Spawning instance [{}] inside containerized sandbox.", id);
            // Run inside a lightweight alpine container. Escape quotes properly.
            String containerName = "minicloud-ec2-" + id.replaceAll("[^a-zA-Z0-9-]", "");
            if (isWindows) {
                pb = new ProcessBuilder("cmd.exe", "/c", "docker run --rm --name " + containerName + " alpine sh -c \"" + sanitizedCommand.replace("\"", "\\\"") + "\"");
            } else {
                pb = new ProcessBuilder("docker", "run", "--rm", "--name", containerName, "alpine", "sh", "-c", sanitizedCommand);
            }
        } else {
            log.warn("Docker NOT running. Falling back to native host OS process execution.");
            if (isWindows) {
                pb = new ProcessBuilder("cmd.exe", "/c", sanitizedCommand);
            } else {
                pb = new ProcessBuilder("bash", "-c", sanitizedCommand);
            }
        }

        pb.redirectErrorStream(true);
        Process process = pb.start();

        processes.put(id, process);
        return process.hashCode();
    }

    public void terminate(long pidOrId) {
        log.info("Terminating process: {}", pidOrId);
        processes.values().forEach(p -> {
            if (p.hashCode() == pidOrId || (p.isAlive() && String.valueOf(p.hashCode()).equals(String.valueOf(pidOrId)))) {
                p.destroyForcibly();
            }
        });
    }

    public void terminateProcess(long pid) {
        terminate(pid);
    }

    public boolean isAlive(long pid) {
        return processes.values().stream()
                .anyMatch(p -> p.hashCode() == pid && p.isAlive());
    }
}
