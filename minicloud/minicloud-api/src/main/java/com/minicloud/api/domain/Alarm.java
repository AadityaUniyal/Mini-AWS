package com.minicloud.api.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "monitoring_alarms")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Alarm {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String name;
    private String description;
    
    private String metricName; // e.g., "CPU", "RAM", "DISK"
    
    @Enumerated(EnumType.STRING)
    private ComparisonOperator comparisonOperator;
    
    private double threshold;
    
    private String notificationTopic; // Used for notifications
    
    private String action;      // e.g., "STOP", "TERMINATE", "RESTART", "LOG"
    private String targetId;    // The ID of the instance/resource to act upon
    
    private UUID userId;
    private boolean enabled;
    
    @Enumerated(EnumType.STRING)
    private AlarmState state;
    
    private LocalDateTime createdAt;
    private LocalDateTime lastTriggeredAt;

    public enum AlarmState { OK, ALARM, INSUFFICIENT_DATA }
    public enum ComparisonOperator { GREATER_THAN, GREATER_THAN_OR_EQUAL, LESS_THAN, LESS_THAN_OR_EQUAL }
}
