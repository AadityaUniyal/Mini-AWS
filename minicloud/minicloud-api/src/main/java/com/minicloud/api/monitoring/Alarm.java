package com.minicloud.api.monitoring;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "mc_alarms")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Alarm {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @EqualsAndHashCode.Include
    private UUID id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "metric_name", nullable = false)
    private String metricName; // e.g., "CPUUtilization", "RAMUsage"

    @Column(name = "threshold")
    private Double threshold;

    @Enumerated(EnumType.STRING)
    @Column(name = "comparison_operator")
    private ComparisonOperator comparisonOperator; // GREATER_THAN, LESS_THAN

    @Enumerated(EnumType.STRING)
    @Column(name = "state")
    @Builder.Default
    private AlarmState state = AlarmState.OK;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public enum ComparisonOperator {
        GREATER_THAN, LESS_THAN
    }

    public enum AlarmState {
        OK, ALARM, INSUFFICIENT_DATA
    }
}
