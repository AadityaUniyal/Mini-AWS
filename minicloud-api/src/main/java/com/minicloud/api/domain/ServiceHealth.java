package com.minicloud.api.domain;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "service_health")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class ServiceHealth {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String serviceName;

    @Column(nullable = false)
    private String status;

    @Builder.Default
    private long responseTimeMs = 0;
    @Builder.Default
    private double errorRate = 0;

    @CreatedDate
    private LocalDateTime lastCheck;

    @Builder.Default
    private double uptimePercent = 100.0;

    @Column(columnDefinition = "TEXT")
    private String details;
}
