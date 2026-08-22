package com.minicloud.api.lambda;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * S3LambdaTrigger entity linking S3 bucket upload events to Lambda function invocations.
 */
@Entity
@Table(name = "s3_lambda_triggers")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class S3LambdaTrigger {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "bucket_name", nullable = false)
    private String bucketName;

    @Column(name = "function_name", nullable = false)
    private String functionName;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "s3_trigger_events", joinColumns = @JoinColumn(name = "trigger_id"))
    @Column(name = "event_name")
    @Builder.Default
    private List<String> events = new ArrayList<>();

    @Builder.Default
    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "account_id")
    private String accountId;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
