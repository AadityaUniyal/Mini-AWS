package com.minicloud.api.monitoring;

import com.minicloud.api.dto.MetricsResponse;
import com.minicloud.api.domain.Alarm;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlarmEvaluator {

    private final AlarmService alarmService;
    private final MetricsService metricsService;

    @Scheduled(fixedRate = 30000) // Evaluate every 30 seconds
    public void evaluateAlarms() {
        log.debug("Evaluating all metric alarms...");
        List<Alarm> alarms = alarmService.getAllAlarms();
        MetricsResponse metrics = metricsService.getSystemMetrics();

        for (Alarm alarm : alarms) {
            double currentVal = getMetricValue(metrics, alarm.getMetricName());
            boolean isTriggered = checkCondition(currentVal, alarm.getThreshold(), alarm.getComparisonOperator());
            
            Alarm.AlarmState newState = isTriggered ? Alarm.AlarmState.ALARM : Alarm.AlarmState.OK;
            alarmService.updateAlarmState(alarm.getId(), newState);
        }
    }

    private double getMetricValue(MetricsResponse metrics, String metricName) {
        return switch (metricName.toUpperCase()) {
            case "CPU" -> metrics.getCpuLoad();
            case "RAM" -> metrics.getUsedHeapMb();
            case "DISK" -> metrics.getDiskUsedGb();
            default -> 0.0;
        };
    }

    private boolean checkCondition(double current, double threshold, Alarm.ComparisonOperator op) {
        return switch (op) {
            case GREATER_THAN -> current > threshold;
            case GREATER_THAN_OR_EQUAL -> current >= threshold;
            case LESS_THAN -> current < threshold;
            case LESS_THAN_OR_EQUAL -> current <= threshold;
        };
    }
}
