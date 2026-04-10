package com.minicloud.api.monitoring;

import com.minicloud.api.compute.Instance;
import com.minicloud.api.compute.InstanceRepository;
import com.minicloud.api.compute.InstanceState;
import com.minicloud.api.compute.ProcessManager;
import com.minicloud.core.dto.MetricsResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * CloudWatch-equivalent metrics service.
 *
 * Background collection (spec §11 — Spring @Scheduled + ConcurrentLinkedDeque):
 *   Every 5 seconds, @collectMetrics() samples CPU load and heap usage
 *   and appends to thread-safe rolling deques (max 60 entries = 5 minutes).
 *   The history is served via getCpuHistory() / getRamHistory().
 *
 * All metric sources are from the JDK standard library — no external dependencies:
 *   - java.lang.management.OperatingSystemMXBean  → CPU load
 *   - java.lang.management.MemoryMXBean           → heap usage
 *   - java.io.File                                → disk usage
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MetricsService {

    private final InstanceRepository instanceRepository;
    private final ProcessManager processManager;

    @Value("${logging.file.name:./minicloud-data/logs/app.log}")
    private String logFilePath;

    /** Max number of history points to keep (720 × 5s = 1 hour of history). */
    private static final int MAX_HISTORY = 720;

    // ConcurrentLinkedDeque — thread-safe per spec §11 requirement
    private final ConcurrentLinkedDeque<Double> cpuHistory  = new ConcurrentLinkedDeque<>();
    private final ConcurrentLinkedDeque<Double> ramHistory  = new ConcurrentLinkedDeque<>();

    private final OperatingSystemMXBean osMxBean  = ManagementFactory.getOperatingSystemMXBean();
    private final MemoryMXBean          memoryMxBean = ManagementFactory.getMemoryMXBean();

    // ─────────────── Background scheduled collection (spec §11) ────────────────

    /**
     * Runs every 5 seconds — samples CPU and heap, pushes to rolling deques.
     * @EnableScheduling must be present on MiniCloudApiApplication for this to fire.
     */
    @Scheduled(fixedRate = 5000)
    public void collectMetrics() {
        double cpu = readCpuPercent();
        double heapMb = memoryMxBean.getHeapMemoryUsage().getUsed() / (1024.0 * 1024.0);

        // Push new reading, evict oldest if at capacity
        cpuHistory.addLast(Math.round(cpu * 10.0) / 10.0);
        if (cpuHistory.size() > MAX_HISTORY) cpuHistory.pollFirst();

        ramHistory.addLast(Math.round(heapMb * 10.0) / 10.0);
        if (ramHistory.size() > MAX_HISTORY) ramHistory.pollFirst();
    }

    // ─────────────── Public API ─────────────────────────────────────────────────

    /** Current point-in-time system metrics snapshot. */
    public MetricsResponse getSystemMetrics() {
        double cpu      = readCpuPercent();
        long heapUsed   = memoryMxBean.getHeapMemoryUsage().getUsed();
        long heapMax    = memoryMxBean.getHeapMemoryUsage().getMax();

        File root       = new File(".");
        long diskTotal  = root.getTotalSpace();
        long diskFree   = root.getFreeSpace();
        long diskUsed   = diskTotal - diskFree;

        return MetricsResponse.builder()
                .cpuPercent(Math.round(cpu * 10.0) / 10.0)
                .heapUsedMb(heapUsed  / (1024 * 1024))
                .heapMaxMb(heapMax    / (1024 * 1024))
                .diskUsedGb(diskUsed  / (1024L * 1024 * 1024))
                .diskTotalGb(diskTotal / (1024L * 1024 * 1024))
                .diskFreeGb(diskFree  / (1024L * 1024 * 1024))
                .cpuHistory(new ArrayList<>(cpuHistory))
                .ramHistory(new ArrayList<>(ramHistory))
                .build();
    }

    /** Returns live per-instance metrics (state, PID, uptime, alive check). */
    public MetricsResponse getInstanceMetrics() {
        List<Instance> instances = instanceRepository.findAllByStateNot(InstanceState.TERMINATED);

        List<MetricsResponse.InstanceMetric> metrics = instances.stream()
                .map(instance -> {
                    boolean alive = false;
                    if (instance.getPid() != null) {
                        alive = processManager.isAlive(instance.getPid());
                    }
                    long uptime = 0;
                    if (instance.getLaunchedAt() != null) {
                        uptime = ChronoUnit.SECONDS.between(instance.getLaunchedAt(), LocalDateTime.now());
                    }
                    return MetricsResponse.InstanceMetric.builder()
                            .id(instance.getId().toString())
                            .name(instance.getName())
                            .state(instance.getState().name())
                            .pid(instance.getPid())
                            .uptimeSeconds(Math.max(0, uptime))
                            .alive(alive)
                            .build();
                })
                .toList();

        return MetricsResponse.builder()
                .instances(metrics)
                .build();
    }

    /** Returns the rolling CPU% history (last MAX_HISTORY samples). */
    public List<Double> getCpuHistory() {
        return new ArrayList<>(cpuHistory);
    }

    /** Returns the rolling heap RAM history in MB (last MAX_HISTORY samples). */
    public List<Double> getRamHistory() {
        return new ArrayList<>(ramHistory);
    }

    /**
     * Reads the last {@code lines} lines from the application log file
     * using a reverse-read algorithm (RandomAccessFile from end of file).
     */
    public List<String> getRecentLogs(int lines) {
        List<String> result = new ArrayList<>();
        File logFile = new File(logFilePath);
        if (!logFile.exists()) {
            result.add("[No log file found at: " + logFilePath + "]");
            return result;
        }

        try (RandomAccessFile raf = new RandomAccessFile(logFile, "r")) {
            long fileLen = raf.length();
            if (fileLen == 0) return result;

            long pointer   = fileLen - 1;
            int linesFound = 0;
            StringBuilder sb = new StringBuilder();

            while (pointer >= 0 && linesFound < lines) {
                raf.seek(pointer);
                char c = (char) raf.read();
                if (c == '\n' && sb.length() > 0) {
                    result.add(0, sb.reverse().toString());
                    sb.setLength(0);
                    linesFound++;
                } else if (c != '\r') {
                    sb.append(c);
                }
                pointer--;
            }

            if (sb.length() > 0 && linesFound < lines) {
                result.add(0, sb.reverse().toString());
            }

        } catch (IOException e) {
            log.warn("Could not read log file: {}", e.getMessage());
            result.add("[Error reading logs: " + e.getMessage() + "]");
        }

        return result;
    }

    // ─────────────── Private helpers ────────────────────────────────────────────

    private double readCpuPercent() {
        if (osMxBean instanceof com.sun.management.OperatingSystemMXBean sunOs) {
            double cpu = sunOs.getCpuLoad() * 100.0;
            return cpu < 0 ? 0.0 : cpu; // -1 = not yet available
        }
        return 0.0;
    }
}
