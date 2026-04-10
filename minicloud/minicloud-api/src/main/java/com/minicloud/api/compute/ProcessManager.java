package com.minicloud.api.compute;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

@Slf4j
@Component
public class ProcessManager {

    // Keep references to Process objects so we can stop them
    private final Map<Integer, Process> processMap = new ConcurrentHashMap<>();
    
    // Store recent log lines for each process (last 500 lines)
    private final Map<Integer, List<String>> consoleLogs = new ConcurrentHashMap<>();

    /**
     * Launch a new OS process from command string.
     * Returns the OS PID.
     */
    public int launchProcess(String command) throws IOException {
        List<String> args;

        // Detect OS and choose appropriate shell
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            args = List.of("cmd.exe", "/c", command);
        } else {
            args = List.of("/bin/sh", "-c", command);
        }

        ProcessBuilder builder = new ProcessBuilder(args);
        builder.redirectErrorStream(true);

        Process process = builder.start();
        int pid = (int) process.pid();
        processMap.put(pid, process);
        
        // Background thread to capture logs
        List<String> logs = new java.util.concurrent.CopyOnWriteArrayList<>();
        consoleLogs.put(pid, logs);
        
        new Thread(() -> {
            try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logs.add(line);
                    // Keep buffer manageable (last 500 lines)
                    if (logs.size() > 500) {
                        logs.remove(0);
                    }
                }
            } catch (IOException e) {
                log.warn("Stopped reading console output for PID {}: {}", pid, e.getMessage());
            }
        }, "ProcessLogReader-" + pid).start();

        log.info("Launched process PID={} command='{}'", pid, command);
        return pid;
    }

    /**
     * Get recent console output for a process.
     */
    public List<String> getConsoleOutput(int pid) {
        return consoleLogs.getOrDefault(pid, List.of("No logs available for PID " + pid));
    }

    /**
     * Gracefully stop a running process (SIGTERM).
     */
    public boolean stopProcess(int pid) {
        Process process = processMap.get(pid);
        if (process != null && process.isAlive()) {
            process.destroy();
            log.info("Stopped process PID={}", pid);
            return true;
        }
        // Try via ProcessHandle as fallback
        ProcessHandle.of(pid).ifPresent(handle -> {
            handle.destroy();
            log.info("Stopped process PID={} via ProcessHandle", pid);
        });
        return true;
    }

    /**
     * Forcibly kill a process (SIGKILL).
     */
    public boolean terminateProcess(int pid) {
        Process process = processMap.remove(pid);
        if (process != null) {
            process.destroyForcibly();
            log.info("Terminated process PID={}", pid);
        }
        ProcessHandle.of(pid).ifPresent(handle -> {
            handle.destroyForcibly();
            log.info("Force-terminated PID={} via ProcessHandle", pid);
        });
        // We keep logs even after termination for a while (until manual cleanup or restart)
        return true;
    }

    /**
     * Check if a process is alive.
     */
    public boolean isAlive(int pid) {
        Process process = processMap.get(pid);
        if (process != null) {
            return process.isAlive();
        }
        return ProcessHandle.of(pid)
                .map(ProcessHandle::isAlive)
                .orElse(false);
    }

    /**
     * Remove stale Process objects whose process has ended.
     */
    public void cleanup() {
        processMap.entrySet().removeIf(entry -> {
            boolean dead = !entry.getValue().isAlive();
            if (dead) {
                // optional: consoleLogs.remove(entry.getKey());
            }
            return dead;
        });
    }
}
