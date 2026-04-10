package com.minicloud.api.monitoring;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlarmService {

    private final AlarmRepository alarmRepository;
    private final MetricsService metricsService;

    /**
     * Check all alarms every 10 seconds.
     */
    @Scheduled(fixedRate = 10000)
    public void evaluateAlarms() {
        List<Alarm> alarms = alarmRepository.findAll();
        if (alarms.isEmpty()) return;

        log.debug("Evaluating {} CloudWatch alarms...", alarms.size());

        for (Alarm alarm : alarms) {
            evaluateAlarm(alarm);
        }
    }

    private void evaluateAlarm(Alarm alarm) {
        Double currentMetricValue = getCurrentMetricValue(alarm.getMetricName());
        if (currentMetricValue == null) {
            updateAlarmState(alarm, Alarm.AlarmState.INSUFFICIENT_DATA);
            return;
        }

        boolean isTriggered = false;
        if (alarm.getComparisonOperator() == Alarm.ComparisonOperator.GREATER_THAN) {
            isTriggered = currentMetricValue > alarm.getThreshold();
        } else if (alarm.getComparisonOperator() == Alarm.ComparisonOperator.LESS_THAN) {
            isTriggered = currentMetricValue < alarm.getThreshold();
        }

        if (isTriggered) {
            updateAlarmState(alarm, Alarm.AlarmState.ALARM);
        } else {
            updateAlarmState(alarm, Alarm.AlarmState.OK);
        }
    }

    private Double getCurrentMetricValue(String name) {
        var metrics = metricsService.getSystemMetrics();
        return switch (name) {
            case "CPUUtilization" -> metrics.getCpuPercent();
            case "RAMUsage" -> (double) metrics.getHeapUsedMb();
            case "DiskUsage" -> (double) metrics.getDiskUsedGb();
            default -> null;
        };
    }

    private void updateAlarmState(Alarm alarm, Alarm.AlarmState newState) {
        if (alarm.getState() != newState) {
            log.info("ALARM STATE CHANGE: '{}' moved from {} to {} (Value: {})", 
                    alarm.getName(), alarm.getState(), newState, getCurrentMetricValue(alarm.getMetricName()));
            alarm.setState(newState);
            alarmRepository.save(alarm);
        }
    }
}
