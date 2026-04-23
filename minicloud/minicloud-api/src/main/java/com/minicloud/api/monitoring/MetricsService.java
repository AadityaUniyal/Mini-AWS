package com.minicloud.api.monitoring;

import com.minicloud.api.dto.MetricsResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.software.os.OSFileStore;
import oshi.software.os.OperatingSystem;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MetricsService {

    private final SystemInfo systemInfo = new SystemInfo();
    private final HardwareAbstractionLayer hardware = systemInfo.getHardware();
    private final OperatingSystem os = systemInfo.getOperatingSystem();
    private long[] prevTicks = new long[CentralProcessor.TickType.values().length];

    private final ConcurrentHashMap<String, AtomicInteger> eventCounters = new ConcurrentHashMap<>();
    
    // Rolling history for dashboard graphs (last 60 data points, e.g. 5 minutes at 5s interval)
    private final LinkedList<Double> cpuHistory = new LinkedList<>();
    private final LinkedList<Double> ramHistory = new LinkedList<>();
    private static final int MAX_HISTORY = 60;


    public void recordEvent(String service, String event) {
        String key = service + ":" + event;
        eventCounters.computeIfAbsent(key, k -> new AtomicInteger(0)).incrementAndGet();
    }

    /**
     * Periodically sample system metrics to maintain live history for CloudWatch dashboard graphs.
     * Requirement: 17.1 (Resource_Pool monitoring)
     */
    @org.springframework.scheduling.annotation.Scheduled(fixedRate = 5000)
    public void sampleMetrics() {
        getSystemMetrics(); // This triggers updateHistory()
        log.debug("Sampled system metrics for live tracking");
    }

    public MetricsResponse getSystemMetrics() {
        CentralProcessor processor = hardware.getProcessor();
        double cpuLoad = processor.getSystemCpuLoadBetweenTicks(prevTicks) * 100;
        prevTicks = processor.getSystemCpuLoadTicks();

        GlobalMemory memory = hardware.getMemory();
        double totalRamGb = memory.getTotal() / (1024.0 * 1024.0 * 1024.0);
        double availableRamGb = memory.getAvailable() / (1024.0 * 1024.0 * 1024.0);
        double usedRamGb = totalRamGb - availableRamGb;

        List<OSFileStore> fileStores = os.getFileSystem().getFileStores();
        double totalDiskGb = 0;
        double usedDiskGb = 0;
        for (OSFileStore store : fileStores) {
            totalDiskGb += store.getTotalSpace() / (1024.0 * 1024.0 * 1024.0);
            usedDiskGb += (store.getTotalSpace() - store.getUsableSpace()) / (1024.0 * 1024.0 * 1024.0);
        }

        updateHistory(cpuLoad, usedRamGb);

        return MetricsResponse.builder()
                .cpuLoad(cpuLoad)
                .usedHeapMb(usedRamGb * 1024.0) // Kept Mb for interface compatibility
                .diskUsedGb(usedDiskGb)
                .activeThreads(Thread.activeCount())
                .uptimeSeconds(os.getSystemUptime())
                .build();
    }


    private synchronized void updateHistory(double cpu, double ram) {
        cpuHistory.addLast(cpu);
        ramHistory.addLast(ram);
        if (cpuHistory.size() > MAX_HISTORY) cpuHistory.removeFirst();
        if (ramHistory.size() > MAX_HISTORY) ramHistory.removeFirst();
    }

    public List<Double> getCpuHistory() {
        return new ArrayList<>(cpuHistory);
    }

    public List<Double> getRamHistory() {
        return new ArrayList<>(ramHistory);
    }

    public List<String> getRecentLogs(int lines) {
        try {
            Path logFile = Path.of("logs/minicloud.log");
            if (!Files.exists(logFile)) return List.of("Log file not found at " + logFile.toAbsolutePath());
            
            List<String> allLines = Files.readAllLines(logFile);
            int start = Math.max(0, allLines.size() - lines);
            return allLines.subList(start, allLines.size());
        } catch (IOException e) {
            log.error("Failed to read logs", e);
            return List.of("Error reading logs: " + e.getMessage());
        }
    }

    public MetricsResponse getInstanceMetrics() {
        // High-fidelity simulation of CloudWatch Instance Metrics using OSHI
        return MetricsResponse.builder()
                .cpuLoad(hardware.getProcessor().getSystemCpuLoadBetweenTicks(prevTicks) * 100)
                .uptimeSeconds(os.getSystemUptime())
                .build();
    }
}

