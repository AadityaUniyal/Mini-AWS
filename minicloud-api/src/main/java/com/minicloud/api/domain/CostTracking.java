package com.minicloud.api.domain;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "cost_tracking")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class CostTracking {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String accountId;

    @Column(nullable = false)
    private String service;

    private UUID resourceId;
    private String resourceName;

    @Column(nullable = false)
    private String costType;

    @Builder.Default
    private double baseCost = 0;
    @Builder.Default
    private double usageAmount = 0;
    @Builder.Default
    private double calculatedCost = 0;

    @Column(nullable = false)
    private String billingPeriod;

    @CreatedDate
    private LocalDateTime lastUpdated;

    @Builder.Default
    private boolean isActive = true;
}
