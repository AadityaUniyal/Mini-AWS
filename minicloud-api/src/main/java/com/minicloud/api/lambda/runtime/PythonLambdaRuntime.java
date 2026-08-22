package com.minicloud.api.lambda.runtime;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class PythonLambdaRuntime implements LambdaRuntime {

    @Override
    public boolean supports(String runtimeName) {
        return "PYTHON".equalsIgnoreCase(runtimeName) || runtimeName != null && runtimeName.toLowerCase().startsWith("python");
    }

    @Override
    public LambdaExecutionResult execute(LambdaExecutionSpec spec) {
        long start = System.currentTimeMillis();
        Path scriptPath = spec.getArtifactPath();
        if (scriptPath == null || !Files.exists(scriptPath)) {
            return LambdaExecutionResult.builder()
                    .exitCode(1)
                    .stderr("Function script not found: " + scriptPath)
                    .durationMs(0)
                    .build();
        }

        List<String> cmd = new ArrayList<>();
        cmd.add("python");
        cmd.add(scriptPath.toAbsolutePath().toString());

        ProcessBuilder pb = new ProcessBuilder(cmd);
        if (scriptPath.getParent() != null) {
            pb.directory(scriptPath.getParent().toFile());
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
                    .stderr("Execution exception: " + e.getMessage())
                    .durationMs(System.currentTimeMillis() - start)
                    .build();
        }
    }
}
