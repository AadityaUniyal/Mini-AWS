package com.minicloud.api.domain;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "event_stream")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class EventStream {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String eventType;

    @Column(nullable = false)
    private String sourceService;

    private UUID userId;
    private String accountId;
    private String resourceType;
    private UUID resourceId;

    @Column(columnDefinition = "TEXT")
    private String eventData;

    @Builder.Default
    private String severity = "INFO";

    @CreatedDate
    private LocalDateTime timestamp;

    @Builder.Default
    private boolean processed = false;
}
