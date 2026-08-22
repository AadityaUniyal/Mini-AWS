package com.minicloud.api.domain;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "s3_buckets")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Bucket {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true, nullable = false)
    private String name;

    private UUID userId;
    private String accountId;
    private String region;
    
    private boolean publicRead;
    private boolean versioningEnabled;
    private boolean websiteEnabled;
    private String indexDocument;
    private String errorDocument;
    private Integer retentionDays;
    private boolean spaMode;

    @Builder.Default
    private long totalSizeBytes = 0;

    @Builder.Default
    private long objectCount = 0;

    private LocalDateTime lastAccessed;

    @Version
    private Long version;

    @CreatedDate
    private LocalDateTime createdAt;
}
