package com.minicloud.api.lambda;

import com.minicloud.api.audit.AuditService;
import com.minicloud.api.compute.ProcessManager;
import com.minicloud.api.domain.Function;
import com.minicloud.api.domain.FunctionRepository;
import com.minicloud.api.domain.LambdaInvocationLog;
import com.minicloud.api.domain.LambdaInvocationLogRepository;
import com.minicloud.api.domain.UserRepository;
import com.minicloud.api.lambda.runtime.LambdaExecutionResult;
import com.minicloud.api.lambda.runtime.LambdaExecutionSpec;
import com.minicloud.api.lambda.runtime.LambdaRuntime;
import com.minicloud.api.lambda.runtime.LambdaRuntimeFactory;
import com.minicloud.api.monitoring.logs.LogService;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class LambdaExecutionService {

    private final FunctionRepository functionRepository;
    private final LambdaInvocationLogRepository logRepository;
    private final ProcessManager processManager;
    private final AuditService auditService;
    private final UserRepository userRepository;
    private final LogService logService;
    private final LambdaRuntimeFactory runtimeFactory;

    @Value("${minicloud.storage.base-path:./minicloud-data/s3}")
    private String storagePath;

    @Value("${minicloud.lambda.tmp-dir:./minicloud-data/lambda-tmp}")
    private String lambdaTmpDir;

    private Path lambdaTmpRoot;
    private final Map<String, Semaphore> accountSemaphores = new ConcurrentHashMap<>();

    private CircuitBreaker lambdaCircuitBreaker;
    private Retry lambdaRetry;

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
            return success ? "SUCCESS" : "FAILED (code=" + exitCode + ")";
        }
    }

    @PostConstruct
    public void init() {
        try {
            lambdaTmpRoot = Path.of(lambdaTmpDir).toAbsolutePath().normalize();
            Files.createDirectories(lambdaTmpRoot);
        } catch (IOException e) {
            log.error("Failed to initialize lambda tmp directory", e);
        }

        CircuitBreakerConfig cbConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(50.0f)
                .waitDurationInOpenState(Duration.ofSeconds(10))
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .build();
        this.lambdaCircuitBreaker = CircuitBreakerRegistry.of(cbConfig).circuitBreaker("lambdaExecution");

        RetryConfig retryConfig = RetryConfig.custom()
                .maxAttempts(2)
                .waitDuration(Duration.ofMillis(300))
                .retryExceptions(IOException.class, TimeoutException.class)
                .build();
        this.lambdaRetry = RetryRegistry.of(retryConfig).retry("lambdaExecution");
    }

    public InvocationResult invoke(String functionName, String payload, UUID callerUserId) {
        long start = System.currentTimeMillis();

        Function fn = functionRepository.findByName(functionName)
                .orElseThrow(() -> new IllegalArgumentException("Function not found: " + functionName));

        if (fn.getStatus() == Function.FunctionStatus.DISABLED) {
            return InvocationResult.error("Function '" + functionName + "' is DISABLED.", 0);
        }

        String accountId = fn.getAccountId() != null ? fn.getAccountId() : "default";
        Semaphore semaphore = accountSemaphores.computeIfAbsent(accountId, k -> new Semaphore(50));

        if (!semaphore.tryAcquire()) {
            return InvocationResult.error("Account concurrency limit reached (50 concurrent invocations)", 429);
        }

        try {
            Path artifactPath = resolveArtifact(fn);

            LambdaExecutionSpec spec = LambdaExecutionSpec.builder()
                    .functionId(fn.getId())
                    .functionName(fn.getName())
                    .runtime(fn.getRuntime() != null ? fn.getRuntime().name() : "PYTHON")
                    .handler(fn.getHandler())
                    .artifactPath(artifactPath)
                    .payload(payload)
                    .environmentVars(parseEnvVars(fn.getEnvironmentConfig()))
                    .memoryMb(fn.getMemoryMb())
                    .timeoutSec(fn.getTimeoutSec())
                    .accountId(accountId)
                    .build();

            LambdaRuntime runtime = runtimeFactory.getRuntime(spec.getRuntime());

            InvocationResult res = CircuitBreaker.decorateCallable(
                lambdaCircuitBreaker,
                Retry.decorateCallable(lambdaRetry, () -> {
                    LambdaExecutionResult lr = runtime.execute(spec);
                    if (!lr.isSuccess() && lr.isTimedOut()) {
                        throw new TimeoutException("Function timed out after " + spec.getTimeoutSec() + "s");
                    }
                    return new InvocationResult(lr.getStdout(), lr.getStderr(), lr.getExitCode(), lr.getDurationMs(), true);
                })
            ).call();

            long durationMs = System.currentTimeMillis() - start;
            InvocationResult result = new InvocationResult(res.stdout(), res.stderr(), res.exitCode(), durationMs, true);

            persistCloudWatchLogs(fn, result.stdout(), result.stderr());
            persistLog(fn, callerUserId, result.stdout(), result.stderr(), result.exitCode(), durationMs, "SUCCESS");
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
            log.error("Lambda invocation failed for '{}': {}", functionName, e.getMessage(), e);
            long durationMs = System.currentTimeMillis() - start;
            int exitCode = (e instanceof TimeoutException) ? 124 : -1;
            String errType = (e instanceof TimeoutException) ? "TIMEOUT" : "ERROR";
            updateStats(fn, exitCode, false);
            persistLog(fn, callerUserId, "", e.getMessage(), exitCode, durationMs, errType);
            return InvocationResult.error("Invocation error: " + e.getMessage(), exitCode);
        } finally {
            semaphore.release();
        }
    }

    private Path resolveArtifact(Function fn) throws IOException {
        if (fn.getCodePath() != null) {
            try {
                Path cp = Path.of(fn.getCodePath());
                if (Files.exists(cp)) {
                    return cp;
                }
            } catch (Exception ignored) {}
        }
        if (fn.getHandler() != null) {
            try {
                Path hp = Path.of(fn.getHandler());
                if (Files.exists(hp)) {
                    return hp;
                }
            } catch (Exception ignored) {}
        }

        String accountId = fn.getAccountId() != null ? fn.getAccountId() : "default";
        Path tmpDir = lambdaTmpRoot.resolve(accountId).resolve(fn.getId().toString()).normalize();
        if (!tmpDir.startsWith(lambdaTmpRoot)) {
            throw new SecurityException("Invalid path traversal in function artifact resolution");
        }
        Files.createDirectories(tmpDir);

        String artifactName = fn.getS3Key() != null
                ? Path.of(fn.getS3Key()).getFileName().toString()
                : (fn.getHandler() != null && !fn.getHandler().isBlank() ? fn.getHandler() : "index.py");

        Path dest = tmpDir.resolve(artifactName).normalize();
        if (!dest.startsWith(tmpDir)) {
            throw new SecurityException("Invalid artifact filename");
        }

        if (fn.getS3Bucket() != null && fn.getS3Key() != null) {
            Path storageFile = Path.of(storagePath, fn.getS3Bucket(), fn.getS3Key());
            if (Files.exists(storageFile)) {
                Files.copy(storageFile, dest, StandardCopyOption.REPLACE_EXISTING);
                return dest;
            }

            if (fn.getUserId() != null) {
                Path userStorageFile = Path.of(storagePath, fn.getUserId().toString(), fn.getS3Bucket(), fn.getS3Key());
                if (Files.exists(userStorageFile)) {
                    Files.copy(userStorageFile, dest, StandardCopyOption.REPLACE_EXISTING);
                    return dest;
                }
            }
        }

        if (!Files.exists(dest)) {
            String defaultCode = getDefaultCodeForRuntime(fn.getRuntime() != null ? fn.getRuntime().name() : "PYTHON");
            Files.writeString(dest, defaultCode);
        }

        return dest;
    }

    private String getDefaultCodeForRuntime(String runtime) {
        if ("NODE".equalsIgnoreCase(runtime)) {
            return "const fs = require('fs'); const input = fs.readFileSync(0, 'utf-8'); console.log(JSON.stringify({ message: 'Hello from Node Lambda!', input: input }));";
        } else if ("JAVA".equalsIgnoreCase(runtime)) {
            return "public class Main { public static void main(String[] args) { System.out.println(\"Hello from Java Lambda!\"); } }";
        }
        return "import sys, json\ninput_data = sys.stdin.read()\nprint(json.dumps({'message': 'Hello from Python Lambda!', 'input': input_data}))\n";
    }

    private Map<String, String> parseEnvVars(String raw) {
        if (raw == null || raw.isBlank()) return Collections.emptyMap();
        Map<String, String> map = new HashMap<>();
        for (String pair : raw.split(",")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                map.put(kv[0].trim(), kv[1].trim());
            }
        }
        return map;
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

    private void updateStats(Function fn, int exitCode, boolean success) {
        try {
            fn.setInvocationCount(fn.getInvocationCount() + 1);
            if (!success || exitCode != 0) {
                fn.setErrorCount(fn.getErrorCount() + 1);
            }
            fn.setLastInvokedAt(LocalDateTime.now());
            functionRepository.save(fn);
        } catch (Exception e) {
            log.warn("Failed to update function stats for '{}': {}", fn.getName(), e.getMessage());
        }
    }

    private void persistLog(Function fn, UUID callerUserId, String stdout, String stderr,
                            int exitCode, long durationMs, String status) {
        try {
            LambdaInvocationLog logEntry = LambdaInvocationLog.builder()
                    .functionId(fn.getId())
                    .functionName(fn.getName())
                    .exitCode(exitCode)
                    .durationMs(durationMs)
                    .output(truncate(stdout, 4000))
                    .errorOutput(truncate(stderr, 4000))
                    .status(status)
                    .timestamp(LocalDateTime.now())
                    .build();
            logRepository.save(logEntry);
        } catch (Exception e) {
            log.warn("Failed to persist invocation log: {}", e.getMessage());
        }
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) + "...[TRUNCATED]" : s;
    }
}
