package com.minicloud.api.monitoring;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * AutoScalingService — Horizontal scaling engine for MiniCloud.
 *
 * Implements the scaling policy from the production spec:
 *   - Scale UP  when CPU utilization > 80% for 1 consecutive sample (aggressive)
 *   - Scale DOWN when CPU utilization < 30% for 10 consecutive minutes (conservative)
 *
 * Works by:
 *  1. Tracking CPU utilization per service group via a rolling window
 *  2. Maintaining a conceptual "replica count" per service
 *  3. Emitting scale events consumed by the orchestration layer
 *  4. Enforcing min/max replica bounds
 *
 * In this monolith/multi-module build, the "scaling" is simulated
 * (no actual K8s integration yet — see Task 7.3 for Kubernetes manifests).
 * Events are recorded and exposed via the REST API for dashboard visibility.
 */
@Slf4j
@Service
public class AutoScalingService {

    // ── Configuration ────────────────────────────────────────────────────────

    @Value("${minicloud.autoscaling.scale-up-threshold:80.0}")
    private double scaleUpThreshold;

    @Value("${minicloud.autoscaling.scale-down-threshold:30.0}")
    private double scaleDownThreshold;

    /** Number of consecutive scale-down samples needed before scaling down (~10 mins at 1min intervals) */
    @Value("${minicloud.autoscaling.scale-down-cooldown-samples:10}")
    private int scaleDownCooldownSamples;

    @Value("${minicloud.autoscaling.min-replicas:1}")
    private int minReplicas;

    @Value("${minicloud.autoscaling.max-replicas:10}")
    private int maxReplicas;

    // ── State ────────────────────────────────────────────────────────────────

    /** Current replica count per service */
    private final ConcurrentHashMap<String, AtomicInteger> replicaCounts = new ConcurrentHashMap<>();

    /** Number of consecutive below-threshold samples per service (for scale-down cooldown) */
    private final ConcurrentHashMap<String, AtomicInteger> belowThresholdCount = new ConcurrentHashMap<>();

    /** Latest CPU reading per service (populated externally or by scheduler) */
    private final ConcurrentHashMap<String, Double> latestCpuReadings = new ConcurrentHashMap<>();

    /** Audit trail of all scaling events */
    private final CopyOnWriteArrayList<ScalingEvent> eventHistory = new CopyOnWriteArrayList<>();

    // ── Known services to auto-scale ─────────────────────────────────────────

    private static final List<String> MANAGED_SERVICES = List.of(
            "minicloud-api",
            "minicloud-compute",
            "minicloud-lambda",
            "minicloud-storage",
            "minicloud-rds",
            "minicloud-iam"
    );

    // ── Scaling Loop ─────────────────────────────────────────────────────────

    /**
     * Evaluates scaling decisions every 60 seconds.
     * In production this would consume real metrics from Prometheus/Micrometer.
     * Here we use the CPU readings pushed by MetricsService.
     */
    @Scheduled(fixedDelayString = "${minicloud.autoscaling.eval-interval-ms:60000}")
    public void evaluateScalingDecisions() {
        for (String service : MANAGED_SERVICES) {
            double cpu = latestCpuReadings.getOrDefault(service, 0.0);
            evaluateService(service, cpu);
        }
    }

    /**
     * Applies scaling logic for a single service given current CPU%.
     * Called by the scheduler and can also be called externally for testing.
     */
    public ScalingDecision evaluateService(String serviceName, double cpuPercent) {
        replicaCounts.putIfAbsent(serviceName, new AtomicInteger(1));
        belowThresholdCount.putIfAbsent(serviceName, new AtomicInteger(0));

        AtomicInteger replicas = replicaCounts.get(serviceName);
        AtomicInteger belowCount = belowThresholdCount.get(serviceName);
        int current = replicas.get();

        if (cpuPercent > scaleUpThreshold && current < maxReplicas) {
            // SCALE UP immediately
            int newCount = Math.min(current + 1, maxReplicas);
            replicas.set(newCount);
            belowCount.set(0);

            ScalingEvent event = ScalingEvent.scaleUp(serviceName, current, newCount, cpuPercent);
            eventHistory.add(event);
            log.info("AUTO-SCALE UP: {} {} → {} replicas (CPU={}%)", serviceName, current, newCount, cpuPercent);
            return new ScalingDecision(ScalingAction.SCALE_UP, current, newCount, cpuPercent, serviceName);

        } else if (cpuPercent < scaleDownThreshold && current > minReplicas) {
            // Increment cooldown counter
            int count = belowCount.incrementAndGet();
            if (count >= scaleDownCooldownSamples) {
                // SCALE DOWN after sustained low CPU
                int newCount = Math.max(current - 1, minReplicas);
                replicas.set(newCount);
                belowCount.set(0);

                ScalingEvent event = ScalingEvent.scaleDown(serviceName, current, newCount, cpuPercent);
                eventHistory.add(event);
                log.info("AUTO-SCALE DOWN: {} {} → {} replicas (CPU={}%)", serviceName, current, newCount, cpuPercent);
                return new ScalingDecision(ScalingAction.SCALE_DOWN, current, newCount, cpuPercent, serviceName);
            }
        } else {
            // CPU is in acceptable range — reset cooldown counter
            belowCount.set(0);
        }

        return new ScalingDecision(ScalingAction.NO_ACTION, current, current, cpuPercent, serviceName);
    }

    // ── External API ─────────────────────────────────────────────────────────

    /** Push a new CPU reading for a service (called by monitoring integrations) */
    public void updateCpuReading(String serviceName, double cpuPercent) {
        latestCpuReadings.put(serviceName, cpuPercent);
    }

    /** Get current replica counts for all services */
    public Map<String, Integer> getAllReplicaCounts() {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (String service : MANAGED_SERVICES) {
            result.put(service, replicaCounts.getOrDefault(service, new AtomicInteger(1)).get());
        }
        return result;
    }

    /** Get replica count for a specific service */
    public int getReplicaCount(String serviceName) {
        return replicaCounts.getOrDefault(serviceName, new AtomicInteger(1)).get();
    }

    /** Manually override replica count (for admin use) */
    public void setReplicaCount(String serviceName, int count) {
        int clamped = Math.min(Math.max(count, minReplicas), maxReplicas);
        replicaCounts.computeIfAbsent(serviceName, k -> new AtomicInteger(1)).set(clamped);
        eventHistory.add(ScalingEvent.manual(serviceName, getReplicaCount(serviceName), clamped));
        log.info("MANUAL SCALE: {} set to {} replicas", serviceName, clamped);
    }

    /** Get the last N scaling events across all services */
    public List<ScalingEvent> getRecentEvents(int limit) {
        List<ScalingEvent> all = new ArrayList<>(eventHistory);
        all.sort(Comparator.comparing(ScalingEvent::getTimestamp).reversed());
        return all.subList(0, Math.min(limit, all.size()));
    }

    /** Get current CPU readings */
    public Map<String, Double> getAllCpuReadings() {
        return Collections.unmodifiableMap(latestCpuReadings);
    }

    // ── DTOs ─────────────────────────────────────────────────────────────────

    public enum ScalingAction { SCALE_UP, SCALE_DOWN, NO_ACTION, MANUAL }

    public record ScalingDecision(
            ScalingAction action,
            int previousReplicas,
            int newReplicas,
            double cpuPercent,
            String serviceName
    ) {}

    @Getter
    public static class ScalingEvent {
        private final ScalingAction action;
        private final String serviceName;
        private final int previousReplicas;
        private final int newReplicas;
        private final double cpuPercent;
        private final LocalDateTime timestamp;
        private final String reason;

        private ScalingEvent(ScalingAction action, String service, int prev, int next,
                              double cpu, String reason) {
            this.action = action;
            this.serviceName = service;
            this.previousReplicas = prev;
            this.newReplicas = next;
            this.cpuPercent = cpu;
            this.timestamp = LocalDateTime.now();
            this.reason = reason;
        }

        public static ScalingEvent scaleUp(String service, int prev, int next, double cpu) {
            return new ScalingEvent(ScalingAction.SCALE_UP, service, prev, next, cpu,
                    String.format("CPU %.1f%% exceeded %.0f%% threshold", cpu, 80.0));
        }

        public static ScalingEvent scaleDown(String service, int prev, int next, double cpu) {
            return new ScalingEvent(ScalingAction.SCALE_DOWN, service, prev, next, cpu,
                    String.format("CPU %.1f%% below %.0f%% threshold for sustained period", cpu, 30.0));
        }

        public static ScalingEvent manual(String service, int prev, int next) {
            return new ScalingEvent(ScalingAction.MANUAL, service, prev, next, -1,
                    "Manual replica count override");
        }
    }
}
