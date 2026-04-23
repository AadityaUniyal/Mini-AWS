package com.minicloud.api.monitoring;

import com.minicloud.api.domain.Alarm;
import com.minicloud.api.domain.AlarmRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlarmService {

    private final AlarmRepository alarmRepository;
    private final NotificationService notificationService;

    public List<Alarm> getAlarmsForUser(UUID userId) {
        return alarmRepository.findByUserId(userId);
    }

    public Alarm createAlarm(Alarm alarm) {
        alarm.setState(Alarm.AlarmState.INSUFFICIENT_DATA);
        return alarmRepository.save(alarm);
    }

    public void updateAlarmState(UUID alarmId, Alarm.AlarmState newState) {
        alarmRepository.findById(alarmId).ifPresent(alarm -> {
            if (alarm.getState() != newState) {
                log.info("Alarm '{}' changed state from {} to {}", alarm.getName(), alarm.getState(), newState);
                alarm.setState(newState);
                alarmRepository.save(alarm);
                
                if (newState == Alarm.AlarmState.ALARM && alarm.getNotificationTopic() != null) {
                    notificationService.sendNotification(alarm.getNotificationTopic(), 
                        "ALARM: " + alarm.getName() + " triggered! Threshold: " + alarm.getThreshold());
                }
            }
        });
    }

    public List<Alarm> getAllAlarms() {
        return alarmRepository.findAll();
    }
}
