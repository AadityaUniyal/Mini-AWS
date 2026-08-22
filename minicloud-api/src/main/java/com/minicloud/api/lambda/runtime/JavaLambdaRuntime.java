package com.minicloud.api.lambda.runtime;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class JavaLambdaRuntime implements LambdaRuntime {

    @Override
    public boolean supports(String runtimeName) {
        return "JAVA".equalsIgnoreCase(runtimeName) || runtimeName != null && runtimeName.toLowerCase().startsWith("java");
    }

    @Override
    public LambdaExecutionResult execute(LambdaExecutionSpec spec) {
        long start = System.currentTimeMillis();
        Path jarOrClassPath = spec.getArtifactPath();
        if (jarOrClassPath == null || !Files.exists(jarOrClassPath)) {
            return LambdaExecutionResult.builder()
                    .exitCode(1)
                    .stderr("Java artifact not found: " + jarOrClassPath)
                    .durationMs(0)
                    .build();
        }

        List<String> cmd = new ArrayList<>();
        cmd.add("java");
        if (jarOrClassPath.toString().endsWith(".jar")) {
            cmd.add("-jar");
            cmd.add(jarOrClassPath.toAbsolutePath().toString());
        } else {
            cmd.add("-cp");
            cmd.add(jarOrClassPath.toAbsolutePath().toString());
            cmd.add(spec.getHandler() != null ? spec.getHandler() : "Main");
        }

        ProcessBuilder pb = new ProcessBuilder(cmd);
        if (jarOrClassPath.getParent() != null) {
            pb.directory(jarOrClassPath.getParent().toFile());
        }
        if (spec.getEnvironmentVars() != null) {
            pb.environment().putAll(spec.getEnvironmentVars());
        }

        try {
            Process process = pb.start();

            if (spec.getPayload() != null && !spec.getPayload().isBlank()) {
                try (OutputStream os = process.getOutputStream()) {
                    os.write(spec.getPayload().getBytes(StandardCharsets.UTF_8));
                    os.flush();
                }
            }

            int timeout = spec.getTimeoutSec() > 0 ? spec.getTimeoutSec() : 30;
            boolean finished = process.waitFor(timeout, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return LambdaExecutionResult.builder()
                        .timedOut(true)
                        .exitCode(124)
                        .stderr("Function execution timed out after " + timeout + " seconds")
                        .durationMs(System.currentTimeMillis() - start)
                        .build();
            }

            String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);

            return LambdaExecutionResult.builder()
                    .exitCode(process.exitValue())
                    .stdout(stdout)
                    .stderr(stderr)
                    .durationMs(System.currentTimeMillis() - start)
                    .build();
        } catch (Exception e) {
            return LambdaExecutionResult.builder()
                    .exitCode(1)
                    .stderr("Java execution error: " + e.getMessage())
                    .durationMs(System.currentTimeMillis() - start)
                    .build();
        }
    }
}
