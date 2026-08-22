package com.minicloud.api.lambda;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minicloud.api.domain.Function;
import com.minicloud.api.domain.LambdaInvocationLog;
import com.minicloud.api.domain.FunctionRepository;
import com.minicloud.api.domain.LambdaInvocationLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.time.Duration;
import java.util.concurrent.*;


/**
 * LambdaExecutionService — Core execution engine for MiniLambda.
 *
 * Execution flow per invocation:
 *  1. Load function definition from DB
 *  2. Locate artifact on local storage path (downloaded by StorageService)
 *  3. Build OS command based on runtime (java, node, python, bash, ruby, go, dotnet)
 *  4. Fork a subprocess with configurable timeout
 *  5. Capture stdout + stderr as invocation result
 *  6. Persist invocation log
 *  7. Update function statistics
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LambdaExecutionService {

    private final CircuitBreaker lambdaCircuitBreaker = CircuitBreaker.of("lambdaExecution",
            CircuitBreakerConfig.custom()
                    .failureRateThreshold(50)
                    .waitDurationInOpenState(Duration.ofSeconds(10))
                    .slidingWindowSize(10)
                    .build());

    private final Retry lambdaRetry = Retry.of("lambdaExecution",
            RetryConfig.custom()
                    .maxAttempts(3)
                    .waitDuration(Duration.ofSeconds(1))
                    .retryExceptions(IOException.class, TimeoutException.class, InterruptedException.class, ExecutionException.class)
                    .build());

    private final FunctionRepository functionRepository;
    private final LambdaInvocationLogRepository logRepository;
    private final ObjectMapper objectMapper;
    private final com.minicloud.api.audit.AuditService auditService;
    private final com.minicloud.api.domain.UserRepository userRepository;
    private final com.minicloud.api.monitoring.logs.LogService logService;
    private final com.minicloud.api.compute.ProcessManager processManager;

    @Value("${minicloud.storage.base-path}")
    private String storagePath;

    @Value("${minicloud.lambda.tmp-dir:./minicloud-data/lambda-tmp}")
    private String lambdaTmpDir;

    private final ExecutorService lambdaExecutor = Executors.newFixedThreadPool(4);

    @jakarta.annotation.PreDestroy
    public void shutdown() {
        lambdaExecutor.shutdownNow();
    }

    // ─────────────────────────── Invocation ───────────────────────────

    /**
     * Invokes a function by name, passing an optional payload string as stdin.
     * Returns an InvocationResult containing stdout, stderr, exit code, and duration.
     */
    public InvocationResult invoke(String functionName, String payload, UUID callerUserId) {
        long start = System.currentTimeMillis();

        Function fn = functionRepository.findByName(functionName)
                .orElseThrow(() -> new IllegalArgumentException("Function not found: " + functionName));

        if (fn.getStatus() == Function.FunctionStatus.DISABLED) {
            return InvocationResult.error("Function '" + functionName + "' is DISABLED.", 0);
        }

        try {
            // 1. Resolve artifact path from storage
            Path artifactPath = resolveArtifact(fn);

            // Execute using decorated retry + circuit breaker
            InvocationResult res = CircuitBreaker.decorateCallable(
                lambdaCircuitBreaker,
                Retry.decorateCallable(lambdaRetry, () -> runProcessAndCapture(fn, artifactPath, payload))
            ).call();

            long durationMs = System.currentTimeMillis() - start;
            InvocationResult result = new InvocationResult(res.stdout(), res.stderr(), res.exitCode(), durationMs, true);

            // 5. Persist CloudWatch Logs
            persistCloudWatchLogs(fn, result.stdout(), result.stderr());

            // 6. Persist legacy invocation log
            persistLog(fn, callerUserId, result.stdout(), result.stderr(), result.exitCode(), durationMs, "SUCCESS");

            // 6. Update function statistics
            updateStats(fn, result.exitCode(), true);

            String username = callerUserId != null
                    ? userRepository.findById(callerUserId).map(u -> u.getUsername()).orElse(callerUserId.toString())
                    : fn.getName();
            auditService.recordSuccess(username, "Lambda", "Invoke", functionName);

            log.info("Lambda '{}' invoked successfully — exit={}, duration={}ms", functionName, result.exitCode(), durationMs);
            return result;

        } catch (CallNotPermittedException e) {
            log.error("Lambda invocation blocked by Circuit Breaker for '{}': {}", functionName, e.getMessage());
            long durationMs = System.currentTimeMillis() - start;
            updateStats(fn, -2, false);
            persistLog(fn, callerUserId, "", "Circuit Breaker open. Execution blocked.", -2, durationMs, "CIRCUIT_OPEN");
            return InvocationResult.error("Circuit Breaker is OPEN. Downstream lambda service is unavailable.", -2);
        } catch (Exception e) {
            log.error("Lambda invocation failed for '{}' after resilience policies: {}", functionName, e.getMessage(), e);
            long durationMs = System.currentTimeMillis() - start;
            int exitCode = (e instanceof TimeoutException) ? 124 : -1;
            String errType = (e instanceof TimeoutException) ? "TIMEOUT" : "ERROR";
            updateStats(fn, exitCode, false);
            persistLog(fn, callerUserId, "", e.getMessage(), exitCode, durationMs, errType);
            return InvocationResult.error("Invocation error: " + e.getMessage(), exitCode);
        }
    }

    private InvocationResult runProcessAndCapture(Function fn, Path artifactPath, String payload) throws Exception {
        ProcessBuilder pb;
        boolean dockerAvailable = processManager.isDockerAvailable();
        if (dockerAvailable) {
            log.info("Docker is running. Launching Lambda function [{}] in a sandboxed container.", fn.getName());
            List<String> cmd = buildDockerCommand(fn, artifactPath);
            pb = new ProcessBuilder(cmd);
        } else {
            log.warn("Docker NOT running. Falling back to native host process for Lambda function [{}].", fn.getName());
            List<String> cmd = buildCommand(fn, artifactPath);
            pb = new ProcessBuilder(cmd);
            injectEnvironment(pb, fn);
            pb.directory(artifactPath.getParent().toFile());
        }

        pb.redirectErrorStream(false);
        Process process = pb.start();

        // Capture output concurrently with timeout
        Future<String> stdoutFuture = this.lambdaExecutor.submit(() -> readStream(process.getInputStream()));
        Future<String> stderrFuture = this.lambdaExecutor.submit(() -> readStream(process.getErrorStream()));

        // Feed payload as stdin
        if (payload != null && !payload.isBlank()) {
            try (OutputStream os = process.getOutputStream()) {
                os.write(payload.getBytes());
            }
        } else {
            process.getOutputStream().close();
        }

        boolean finished = process.waitFor(fn.getTimeoutSec(), TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new TimeoutException("Function timed out after " + fn.getTimeoutSec() + "s");
        }

        String stdout = stdoutFuture.get(2, TimeUnit.SECONDS);
        String stderr = stderrFuture.get(2, TimeUnit.SECONDS);

        int exitCode = process.exitValue();
        if (exitCode != 0) {
            throw new IOException("Process failed with non-zero exit code " + exitCode + ". Stderr: " + stderr);
        }

        return new InvocationResult(stdout, stderr, exitCode, 0, true);
    }

    private void persistCloudWatchLogs(Function fn, String stdout, String stderr) {
        try {
            String logGroupName = "/aws/lambda/" + fn.getName();
            String logStreamName = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd")) + "/[$LATEST]" + UUID.randomUUID().toString().substring(0,8);
            String accountId = fn.getAccountId() != null ? fn.getAccountId() : "unknown";
            var stream = logService.createOrGetStream(accountId, logGroupName, logStreamName);
            
            if (stdout != null && !stdout.isEmpty()) {
                for (String line : stdout.split("\n")) {
                    logService.putLogEvent(stream.getId(), line);
                }
            }
            if (stderr != null && !stderr.isEmpty()) {
                for (String line : stderr.split("\n")) {
                    logService.putLogEvent(stream.getId(), "[ERROR] " + line);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to persist CloudWatch logs: {}", e.getMessage());
        }
    }

    // ─────────────────────────── Artifact Resolution ───────────────────────────

    /**
     * Resolves the artifact path from storage.
     * Looks in the standard storage path using the function's s3Bucket/s3Key.
     * Falls back to a cached copy in the lambda-tmp directory.
     */
    private Path resolveArtifact(Function fn) throws IOException {
        Path tmpDir = Path.of(lambdaTmpDir, fn.getId().toString());
        Files.createDirectories(tmpDir);

        String artifactName = fn.getS3Key() != null
                ? Path.of(fn.getS3Key()).getFileName().toString()
                : fn.getHandler();

        if (artifactName == null || artifactName.isBlank()) {
            throw new FileNotFoundException("Function '" + fn.getName() + "' has no handler or artifact configured.");
        }

        Path dest = tmpDir.resolve(artifactName);

        // Try to read from local storage path (bucket/key directory layout)
        if (fn.getS3Bucket() != null && fn.getS3Key() != null) {
            Path storageFile = Path.of(storagePath, fn.getS3Bucket(), fn.getS3Key());
            if (Files.exists(storageFile)) {
                Files.copy(storageFile, dest, StandardCopyOption.REPLACE_EXISTING);
                log.info("Resolved artifact '{}' from storage at {}", artifactName, storageFile);
                return dest;
            }

            if (fn.getUserId() != null) {
                Path userStorageFile = Path.of(storagePath, fn.getUserId().toString(), fn.getS3Bucket(), fn.getS3Key());
                if (Files.exists(userStorageFile)) {
                    Files.copy(userStorageFile, dest, StandardCopyOption.REPLACE_EXISTING);
                    log.info("Resolved artifact '{}' from user storage at {}", artifactName, userStorageFile);
                    return dest;
                }
            }
        }

        // Try direct codePath if set
        if (fn.getCodePath() != null && !fn.getCodePath().isBlank()) {
            Path codePath = Path.of(fn.getCodePath());
            if (Files.exists(codePath)) {
                Files.copy(codePath, dest, StandardCopyOption.REPLACE_EXISTING);
                log.info("Resolved artifact '{}' from codePath at {}", artifactName, codePath);
                return dest;
            }
        }

        // Try handler if handler points directly to an existing local file
        if (fn.getHandler() != null && Files.exists(Path.of(fn.getHandler()))) {
            Path handlerPath = Path.of(fn.getHandler());
            Files.copy(handlerPath, dest, StandardCopyOption.REPLACE_EXISTING);
            log.info("Resolved artifact '{}' from handler path at {}", artifactName, handlerPath);
            return dest;
        }

        // Fallback: already materialised from a previous invocation (cached)
        if (Files.exists(dest)) {
            log.warn("Using cached artifact at {}", dest);
            return dest;
        }

        throw new FileNotFoundException(
                "Artifact not found for function '" + fn.getName() + "'. " +
                "Upload the artifact to S3 bucket '" + fn.getS3Bucket() + "' with key '" + fn.getS3Key() + "' first.");
    }

    // ─────────────────────────── Command Builder ───────────────────────────

    private List<String> buildDockerCommand(Function fn, Path artifactPath) {
        List<String> cmd = new ArrayList<>();
        cmd.add("docker");
        cmd.add("run");
        cmd.add("-i");
        cmd.add("--rm");

        // Volume mount: host directory -> /var/task
        String hostDir = artifactPath.getParent().toAbsolutePath().toString();
        cmd.add("-v");
        cmd.add(hostDir + ":/var/task");
        cmd.add("-w");
        cmd.add("/var/task");

        // Environment variables
        cmd.add("-e");
        cmd.add("MINICLOUD_STORAGE_PATH=" + storagePath);
        cmd.add("-e");
        cmd.add("MINICLOUD_REGION=local-dev-1");
        cmd.add("-e");
        cmd.add("MINICLOUD_FUNCTION_NAME=" + fn.getName());
        cmd.add("-e");
        cmd.add("MINICLOUD_FUNCTION_MEMORY_MB=" + fn.getMemoryMb());
        cmd.add("-e");
        cmd.add("MINICLOUD_FUNCTION_TIMEOUT_SEC=" + fn.getTimeoutSec());

        // Function-specific environment variables
        if (fn.getEnvironmentConfig() != null && !fn.getEnvironmentConfig().isBlank()) {
            try {
                JsonNode node = objectMapper.readTree(fn.getEnvironmentConfig());
                node.fields().forEachRemaining(entry -> {
                    cmd.add("-e");
                    cmd.add(entry.getKey() + "=" + entry.getValue().asText());
                });
            } catch (Exception e) {
                log.warn("Failed to parse environment config: {}", e.getMessage());
            }
        }

        String artifactName = artifactPath.getFileName().toString();

        // Runtime Image & command execution inside container
        switch (fn.getRuntime()) {
            case JAVA -> {
                cmd.add("openjdk:17-slim");
                cmd.add("java");
                cmd.add("-Xmx" + fn.getMemoryMb() + "m");
                cmd.add("-cp");
                cmd.add("/var/task/" + artifactName);
                cmd.add(fn.getHandler());
            }
            case NODE -> {
                cmd.add("node:18-alpine");
                cmd.add("node");
                cmd.add("/var/task/" + artifactName);
            }
            case PYTHON -> {
                cmd.add("python:3.9-slim");
                cmd.add("python");
                cmd.add("/var/task/" + artifactName);
            }
            case BASH -> {
                cmd.add("alpine");
                cmd.add("sh");
                cmd.add("/var/task/" + artifactName);
            }
            case RUBY -> {
                cmd.add("ruby:3.0-slim");
                cmd.add("ruby");
                cmd.add("/var/task/" + artifactName);
            }
            case GO -> {
                cmd.add("golang:1.19-alpine");
                cmd.add("go");
                cmd.add("run");
                cmd.add("/var/task/" + artifactName);
            }
            case DOTNET -> {
                cmd.add("mcr.microsoft.com/dotnet/sdk:6.0");
                cmd.add("dotnet");
                cmd.add("/var/task/" + artifactName);
            }
        }
        return cmd;
    }

    private List<String> buildCommand(Function fn, Path artifactPath) {
        List<String> cmd = new ArrayList<>();
        String art = artifactPath.toAbsolutePath().toString();
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");

        switch (fn.getRuntime()) {
            case JAVA -> {
                cmd.add("java");
                cmd.add("-Xmx" + fn.getMemoryMb() + "m");
                cmd.add("-cp");
                cmd.add(art);
                cmd.add(fn.getHandler());
            }
            case NODE -> {
                cmd.add("node");
                cmd.add(art);
            }
            case PYTHON -> {
                cmd.add(isWindows ? "python" : "python3");
                cmd.add(art);
            }
            case BASH -> {
                cmd.add(isWindows ? "sh" : "bash");
                cmd.add(art);
            }
            case RUBY -> {
                cmd.add("ruby");
                cmd.add(art);
            }
            case GO -> {
                cmd.add("go");
                cmd.add("run");
                cmd.add(art);
            }
            case DOTNET -> {
                cmd.add("dotnet");
                cmd.add(art);
            }
        }
        return cmd;
    }

    // ─────────────────────────── Environment & Logging ───────────────────

    private void injectEnvironment(ProcessBuilder pb, Function fn) {
        var env = pb.environment();

        // Global platform variables
        env.put("MINICLOUD_STORAGE_PATH", storagePath);
        env.put("MINICLOUD_REGION", "local-dev-1");
        env.put("MINICLOUD_FUNCTION_NAME", fn.getName());
        env.put("MINICLOUD_FUNCTION_MEMORY_MB", String.valueOf(fn.getMemoryMb()));
        env.put("MINICLOUD_FUNCTION_TIMEOUT_SEC", String.valueOf(fn.getTimeoutSec()));

        // Function-specific variables (JSON: {"KEY":"value"})
        if (fn.getEnvironmentConfig() != null && !fn.getEnvironmentConfig().isBlank()) {
            try {
                JsonNode node = objectMapper.readTree(fn.getEnvironmentConfig());
                node.fields().forEachRemaining(entry ->
                        env.put(entry.getKey(), entry.getValue().asText()));
            } catch (Exception e) {
                log.warn("Failed to parse environment config for function '{}': {}", fn.getName(), e.getMessage());
            }
        }
    }

    private void persistLog(Function fn, UUID caller, String out, String err,
                             int exit, long ms, String status) {
        try {
            LambdaInvocationLog logEntry = LambdaInvocationLog.builder()
                    .functionId(fn.getId())
                    .functionName(fn.getName())
                    .callerUserId(caller)
                    .exitCode(exit)
                    .durationMs(ms)
                    .status(status)
                    .output(truncate(out, 8000))
                    .errorOutput(truncate(err, 4000))
                    .build();
            logRepository.save(logEntry);
        } catch (Exception e) {
            log.warn("Failed to persist lambda log: {}", e.getMessage());
        }
    }

    // ─────────────────────────── Helpers ───────────────────────────

    private String readStream(InputStream is) {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append("\n");
            }
        } catch (IOException ignored) {}
        return sb.toString();
    }

    private void updateStats(Function fn, int exitCode, boolean success) {
        fn.setInvocationCount(fn.getInvocationCount() + 1);
        fn.setLastExitCode(exitCode);
        fn.setLastInvokedAt(LocalDateTime.now());
        if (!success && exitCode != 0) {
            fn.setStatus(Function.FunctionStatus.ERROR);
        } else if (success) {
            fn.setStatus(Function.FunctionStatus.ACTIVE);
        }
        functionRepository.save(fn);
    }

    /** Prevents DB column overflow for very large stdout/stderr */
    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) + "\n... [truncated]" : s;
    }

    // ─────────────────────────── Result DTO ───────────────────────────

    public record InvocationResult(
            String stdout,
            String stderr,
            int exitCode,
            long durationMs,
            boolean success
    ) {
        public static InvocationResult error(String message, int exitCode) {
            return new InvocationResult("", message, exitCode, 0, false);
        }

        public String summary() {
            return success
                    ? "✅ Exit " + exitCode + " (" + durationMs + "ms)"
                    : "❌ Failed — " + stderr;
        }
    }
}
