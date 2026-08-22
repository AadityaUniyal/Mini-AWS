package com.minicloud.api.domain;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "system_metrics")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class SystemMetricEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private double cpuUsagePercent;
    private long memoryUsedMb;
    private long memoryTotalMb;
    private double diskUsedGb;
    private double diskTotalGb;
    private int activeThreads;
    private double heapUsedMb;
    private double heapMaxMb;
    private long uptimeSeconds;
    private long requestCount;
    private long errorCount;

    @CreatedDate
    private LocalDateTime timestamp;
}
