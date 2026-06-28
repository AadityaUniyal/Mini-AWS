package com.minicloud.api.domain;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "background_tasks")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Task {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String type; // e.g., "BUCKET_UPLOAD", "INSTANCE_LAUNCH", "RDS_CREATION"

    @Column(nullable = false)
    private String status; // PENDING, RUNNING, COMPLETED, FAILED, CANCELLED

    private int progress; // 0 to 100

    private String description;

    private UUID userId;
    
    private String accountId;

    private String errorDetails;

    private LocalDateTime startTime;
    private LocalDateTime endTime;

    @CreatedDate
    private LocalDateTime createdAt;
}
