package com.minicloud.api.monitoring;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * SystemDiagnostics — Boot diagnostics checker.
 * Detects the presence of third-party command-line utilities and compilers.
 */
@Slf4j
@Component
public class SystemDiagnostics {

    private final Map<String, Boolean> runtimeStatus = Collections.synchronizedMap(new LinkedHashMap<>());

    @EventListener(ApplicationReadyEvent.class)
    public void runDiagnostics() {
        log.info("==================================================");
        log.info("         MINICLOUD ENVIRONMENT DIAGNOSTICS        ");
        log.info("==================================================");

        checkTool("docker", new String[]{"docker", "info"});
        checkTool("python", new String[]{"python", "--version"});
        checkTool("node", new String[]{"node", "--version"});
        checkTool("java", new String[]{"java", "-version"});
        checkTool("ruby", new String[]{"ruby", "--version"});
        checkTool("go", new String[]{"go", "version"});
        checkTool("dotnet", new String[]{"dotnet", "--version"});

        log.info("--------------------------------------------------");
        runtimeStatus.forEach((tool, available) -> {
            String status = available ? "✅ ACTIVE" : "❌ INACTIVE (Fallback Mode)";
            log.info(String.format("  %-10s : %s", tool.toUpperCase(), status));
        });
        log.info("==================================================");
    }

    private void checkTool(String key, String[] command) {
        try {
            Process process = Runtime.getRuntime().exec(command);
            int exitCode = process.waitFor();
            runtimeStatus.put(key, exitCode == 0 || key.equals("java")); // java -version exits with 0 but outputs to stderr sometimes
        } catch (Exception e) {
            runtimeStatus.put(key, false);
        }
    }

    public Map<String, Boolean> getRuntimeStatus() {
        return new LinkedHashMap<>(runtimeStatus);
    }
}
