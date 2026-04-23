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

    private final FunctionRepository functionRepository;
    private final LambdaInvocationLogRepository logRepository;
    private final ObjectMapper objectMapper;
    private final com.minicloud.api.audit.AuditService auditService;
    private final com.minicloud.api.domain.UserRepository userRepository;
    private final com.minicloud.api.monitoring.logs.LogService logService;

    @Value("${minicloud.storage.base-path}")
    private String storagePath;

    @Value("${minicloud.lambda.tmp-dir:./minicloud-data/lambda-tmp}")
    private String lambdaTmpDir;

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

            // 2. Build command
            List<String> cmd = buildCommand(fn, artifactPath);

            // 3. Execute subprocess
            ProcessBuilder pb = new ProcessBuilder(cmd);
            injectEnvironment(pb, fn);
            pb.directory(artifactPath.getParent().toFile());
            pb.redirectErrorStream(false);

            Process process = pb.start();

            // Feed payload as stdin
            if (payload != null && !payload.isBlank()) {
                try (OutputStream os = process.getOutputStream()) {
                    os.write(payload.getBytes());
                }
            } else {
                process.getOutputStream().close();
            }

            // 4. Capture output concurrently with timeout
            ExecutorService exec = Executors.newFixedThreadPool(2);
            Future<String> stdoutFuture = exec.submit(() -> readStream(process.getInputStream()));
            Future<String> stderrFuture = exec.submit(() -> readStream(process.getErrorStream()));

            boolean finished = process.waitFor(fn.getTimeoutSec(), TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                exec.shutdownNow();
                updateStats(fn, 124, false);
                persistLog(fn, callerUserId, "", "Function timed out after " + fn.getTimeoutSec() + "s", 124,
                        System.currentTimeMillis() - start, "TIMEOUT");
                return InvocationResult.error("Function timed out after " + fn.getTimeoutSec() + "s", 124);
            }

            String stdout = stdoutFuture.get(2, TimeUnit.SECONDS);
            String stderr = stderrFuture.get(2, TimeUnit.SECONDS);
            exec.shutdown();

            int exitCode = process.exitValue();
            long durationMs = System.currentTimeMillis() - start;
            boolean success = exitCode == 0;

            // 5. Persist CloudWatch Logs
            String logGroupName = "/aws/lambda/" + fn.getName();
            String logStreamName = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd")) + "/[$LATEST]" + UUID.randomUUID().toString().substring(0,8);
            String accountId = fn.getAccountId() != null ? fn.getAccountId() : "unknown";
            var stream = logService.createOrGetStream(accountId, logGroupName, logStreamName);
            
            if (!stdout.isEmpty()) {
                for (String line : stdout.split("\n")) {
                    logService.putLogEvent(stream.getId(), line);
                }
            }
            if (!stderr.isEmpty()) {
                for (String line : stderr.split("\n")) {
                    logService.putLogEvent(stream.getId(), "[ERROR] " + line);
                }
            }

            // 6. Persist legacy invocation log
            String status = success ? "SUCCESS" : "ERROR";
            persistLog(fn, callerUserId, stdout, stderr, exitCode, durationMs, status);

            // 6. Update function statistics
            updateStats(fn, exitCode, success);

            String username = callerUserId != null
                    ? userRepository.findById(callerUserId).map(u -> u.getUsername()).orElse(callerUserId.toString())
                    : fn.getName();
            auditService.recordSuccess(username, "Lambda", "Invoke", functionName);

            log.info("Lambda '{}' invoked — exit={}, duration={}ms", functionName, exitCode, durationMs);
            return new InvocationResult(stdout, stderr, exitCode, durationMs, success);

        } catch (Exception e) {
            log.error("Lambda invocation failed for '{}': {}", functionName, e.getMessage(), e);
            updateStats(fn, -1, false);
            return InvocationResult.error("Invocation error: " + e.getMessage(), -1);
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
