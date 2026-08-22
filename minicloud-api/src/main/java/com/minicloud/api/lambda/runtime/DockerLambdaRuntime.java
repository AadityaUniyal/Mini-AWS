package com.minicloud.api.lambda.runtime;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class DockerLambdaRuntime implements LambdaRuntime {

    @Override
    public boolean supports(String runtimeName) {
        return "DOCKER".equalsIgnoreCase(runtimeName);
    }

    @Override
    public LambdaExecutionResult execute(LambdaExecutionSpec spec) {
        long start = System.currentTimeMillis();
        Path scriptPath = spec.getArtifactPath();
        String containerName = "minicloud-fn-" + spec.getFunctionId().toString().substring(0, 8);
        int memoryMb = Math.max(spec.getMemoryMb(), 128);

        List<String> cmd = new ArrayList<>();
        cmd.add("docker");
        cmd.add("run");
        cmd.add("--rm");
        cmd.add("-i");
        cmd.add("--name");
        cmd.add(containerName);
        cmd.add("--memory=" + memoryMb + "m");
        cmd.add("--cpus=1");
        cmd.add("--pids-limit=50");
        cmd.add("--read-only");
        cmd.add("--tmpfs");
        cmd.add("/tmp:rw,noexec,nosuid,size=64m");

        if (scriptPath != null && Files.exists(scriptPath)) {
            cmd.add("-v");
            cmd.add(scriptPath.toAbsolutePath().toString() + ":/app/script:ro");
        }

        cmd.add("alpine");
        cmd.add("sh");
        cmd.add("-c");
        cmd.add(spec.getHandler() != null ? spec.getHandler() : "cat");

        ProcessBuilder pb = new ProcessBuilder(cmd);
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
                    .stderr("Docker lambda error: " + e.getMessage())
                    .durationMs(System.currentTimeMillis() - start)
                    .build();
        }
    }
}
