package com.minicloud.api.domain;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "dashboard_metrics", uniqueConstraints = {
    @UniqueConstraint(name = "uq_dashboard_acct_date", columnNames = {"account_id", "metric_date"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class DashboardMetrics {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "account_id", nullable = false)
    private String accountId;

    @Column(name = "metric_date", nullable = false)
    private LocalDate metricDate;

    @Builder.Default
    private int totalInstances = 0;
    @Builder.Default
    private int runningInstances = 0;
    @Builder.Default
    private int totalBuckets = 0;
    @Builder.Default
    private long totalObjects = 0;
    @Builder.Default
    private double storageUsedGb = 0;
    @Builder.Default
    private int lambdaFunctions = 0;
    @Builder.Default
    private long lambdaInvocations = 0;
    @Builder.Default
    private int rdsInstances = 0;
    @Builder.Default
    private double dailyCost = 0;
    @Builder.Default
    private long apiRequests = 0;

    @CreatedDate
    private LocalDateTime lastUpdated;
}
