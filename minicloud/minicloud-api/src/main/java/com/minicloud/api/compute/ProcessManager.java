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

    public int launchProcess(String id, String command) throws IOException {
        log.info("Launching process [{}]: {}", id, command);

        // Use shell to handle paths with spaces and complex commands
        ProcessBuilder pb;
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        if (isWindows) {
            pb = new ProcessBuilder("cmd.exe", "/c", command);
        } else {
            pb = new ProcessBuilder("bash", "-c", command);
        }
        pb.redirectErrorStream(true);
        Process process = pb.start();

        processes.put(id, process);
        return process.hashCode();
    }

    public void terminate(long pidOrId) {
        log.info("Terminating process: {}", pidOrId);
        // In a real implementation, we'd use process.pid() in Java 9+
        // Here we'll just check our map if the input is a string, 
        // or iterate if it's a numeric PID (simplified for simulation).
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
