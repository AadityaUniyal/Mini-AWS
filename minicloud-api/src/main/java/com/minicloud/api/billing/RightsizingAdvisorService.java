package com.minicloud.api.billing;

import com.minicloud.api.domain.Instance;
import com.minicloud.api.domain.InstanceRepository;
import com.minicloud.api.domain.InstanceState;
import com.minicloud.api.domain.InstanceType;
import com.minicloud.api.monitoring.MetricsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service that analyzes telemetry data (rolling CPU utilization metrics)
 * and calculates rightsizing recommendations with projected cost savings.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RightsizingAdvisorService {

    public static final double CPU_THRESHOLD_PERCENT = 10.0;
    public static final double MONTHLY_HOURS = 730.0;
    public static final int MAX_SAMPLES = 60;

    private final InstanceRepository instanceRepository;
    private final MetricsService metricsService;

    // In-memory telemetry buffer for per-instance rolling CPU metrics
    private final ConcurrentHashMap<UUID, LinkedList<Double>> instanceCpuSamples = new ConcurrentHashMap<>();

    /**
     * Record a CPU metric sample for a specific instance.
     */
    public void recordInstanceCpuMetric(UUID instanceId, double cpuUtilization) {
        if (instanceId == null) return;
        instanceCpuSamples.compute(instanceId, (id, samples) -> {
            LinkedList<Double> list = (samples != null) ? samples : new LinkedList<>();
            synchronized (list) {
                list.addLast(cpuUtilization);
                if (list.size() > MAX_SAMPLES) {
                    list.removeFirst();
                }
            }
            return list;
        });
    }

    /**
     * Clear recorded CPU metric history for a specific instance.
     */
    public void clearCpuMetrics(UUID instanceId) {
        if (instanceId != null) {
            instanceCpuSamples.remove(instanceId);
        }
    }

    /**
     * Clear all recorded per-instance CPU metric histories.
     */
    public void clearAllCpuMetrics() {
        instanceCpuSamples.clear();
    }

    /**
     * Determine the recommended right-sized instance type for an underutilized instance.
     * Downgrade hierarchy: M5_XLARGE -> T3_LARGE -> T2_MEDIUM -> T2_SMALL -> T2_MICRO
     */
    public InstanceType getDowngradeTarget(InstanceType currentType) {
        if (currentType == null) {
            return null;
        }
        if (currentType == InstanceType.M5_XLARGE) {
            return InstanceType.T3_LARGE;
        } else if (currentType == InstanceType.T3_LARGE) {
            return InstanceType.T2_MEDIUM;
        } else if (currentType == InstanceType.T2_MEDIUM) {
            return InstanceType.T2_SMALL;
        } else if (currentType == InstanceType.T2_SMALL) {
            return InstanceType.T2_MICRO;
        }
        return null;
    }

    /**
     * Calculate the rolling average CPU utilization for a given instance.
     */
    public double getAverageCpuUtilization(Instance instance) {
        if (instance == null || instance.getId() == null) {
            return 0.0;
        }

        // 1. Check if per-instance metric samples exist
        LinkedList<Double> samples = instanceCpuSamples.get(instance.getId());
        if (samples != null) {
            synchronized (samples) {
                if (!samples.isEmpty()) {
                    double sum = 0.0;
                    for (Double val : samples) {
                        sum += val;
                    }
                    return sum / samples.size();
                }
            }
        }

        // 2. Fallback to system-wide CPU history from MetricsService
        if (metricsService != null) {
            List<Double> systemHistory = metricsService.getCpuHistory();
            if (systemHistory != null && !systemHistory.isEmpty()) {
                double sum = 0.0;
                for (Double val : systemHistory) {
                    sum += val;
                }
                return sum / systemHistory.size();
            }

            // 3. Fallback to point-in-time system CPU load
            try {
                return metricsService.getSystemMetrics().getCpuLoad();
            } catch (Exception e) {
                log.debug("Could not obtain system metrics CPU load: {}", e.getMessage());
            }
        }

        return 0.0;
    }

    /**
     * Evaluate a single instance for rightsizing.
     */
    public Optional<RightsizingRecommendationDTO> evaluateInstance(Instance instance) {
        if (instance == null || instance.getState() != InstanceState.RUNNING) {
            return Optional.empty();
        }

        InstanceType currentType = instance.getType() != null ? instance.getType() : InstanceType.T2_MICRO;
        InstanceType recommendedType = getDowngradeTarget(currentType);

        // If instance is already at lowest tier, no downgrade recommendation
        if (recommendedType == null) {
            return Optional.empty();
        }

        double rawAvgCpu = getAverageCpuUtilization(instance);
        double avgCpu = round(rawAvgCpu, 1);

        // Only recommend downgrade if rolling average CPU utilization is strictly below 10.0%
        if (avgCpu >= CPU_THRESHOLD_PERCENT) {
            return Optional.empty();
        }

        double currentHourlyCost = round(currentType.getCostPerHour(), 4);
        double recommendedHourlyCost = round(recommendedType.getCostPerHour(), 4);
        double hourlySavings = round(currentHourlyCost - recommendedHourlyCost, 4);
        double estimatedMonthlySavings = round(hourlySavings * MONTHLY_HOURS, 2);

        String reason = String.format(Locale.US,
                "Instance CPU utilization averaged %.1f%% (<10.0%% threshold) over rolling window.",
                avgCpu);

        RightsizingRecommendationDTO dto = RightsizingRecommendationDTO.builder()
                .instanceId(instance.getId() != null ? instance.getId().toString() : "unknown")
                .instanceName(instance.getName() != null ? instance.getName() : "unnamed")
                .currentInstanceType(currentType.name())
                .recommendedInstanceType(recommendedType.name())
                .averageCpuUtilization(avgCpu)
                .currentHourlyCost(currentHourlyCost)
                .recommendedHourlyCost(recommendedHourlyCost)
                .hourlySavings(hourlySavings)
                .estimatedMonthlySavings(estimatedMonthlySavings)
                .reason(reason)
                .build();

        return Optional.of(dto);
    }

    /**
     * Generate rightsizing recommendations for all running instances, optionally filtered by accountId.
     */
    public RightsizingResponseDTO getRecommendations(String accountId) {
        List<Instance> instances;
        if (accountId != null && !accountId.isBlank()) {
            instances = instanceRepository.findByAccountId(accountId).stream()
                    .filter(i -> i.getState() == InstanceState.RUNNING)
                    .toList();
        } else {
            instances = instanceRepository.findByState(InstanceState.RUNNING);
        }

        List<RightsizingRecommendationDTO> recommendations = instances.stream()
                .map(this::evaluateInstance)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();

        double totalMonthlySavings = round(recommendations.stream()
                .mapToDouble(RightsizingRecommendationDTO::getEstimatedMonthlySavings)
                .sum(), 2);

        String evaluatedAt = DateTimeFormatter.ISO_INSTANT.format(Instant.now().truncatedTo(ChronoUnit.SECONDS));

        return RightsizingResponseDTO.builder()
                .totalEstimatedMonthlySavings(totalMonthlySavings)
                .recommendationsCount(recommendations.size())
                .evaluatedAt(evaluatedAt)
                .recommendations(recommendations)
                .build();
    }

    /**
     * Generate rightsizing recommendations across all accounts.
     */
    public RightsizingResponseDTO getRecommendations() {
        return getRecommendations(null);
    }

    /**
     * Helper to round double values to given decimal places.
     */
    private double round(double value, int scale) {
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP).doubleValue();
    }
}
