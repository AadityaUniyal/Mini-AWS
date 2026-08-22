package com.minicloud.api.domain;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "lambda_functions", uniqueConstraints = {
    @UniqueConstraint(name = "uq_func_account_name", columnNames = {"account_id", "function_name"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Function {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "function_name", nullable = false)
    private String name;

    private String description;
    private UUID userId;
    
    @Column(name = "account_id")
    private String accountId;

    @Enumerated(EnumType.STRING)
    private Runtime runtime;

    private String handler;     // e.g. "Main.handler" or "index.js"
    
    @Column(name = "code_path", length = 1000)
    private String codePath;

    @Column(name = "s3_bucket")
    private String s3Bucket;    // Location of deployment artifact
    @Column(name = "s3_key")
    private String s3Key;       // Key of deployment artifact

    @Builder.Default
    private int memoryMb = 128;       // Performance config
    @Builder.Default
    private int timeoutSec = 30;     // Max execution time

    @Column(name = "environment_vars", columnDefinition = "TEXT")
    private String environmentConfig; // JSON map of env vars

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private FunctionStatus status = FunctionStatus.ACTIVE;

    @Builder.Default
    private long totalDurationMs = 0;
    @Builder.Default
    private long errorCount = 0;
    @Builder.Default
    private double avgDurationMs = 0;

    @CreatedDate
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    private LocalDateTime updatedAt;
    
    private LocalDateTime lastInvokedAt;
    @Builder.Default
    private long invocationCount = 0;
    @Builder.Default
    private int lastExitCode = -1;

    @Version
    private Long version;

    public enum Runtime { JAVA, NODE, PYTHON, BASH, RUBY, GO, DOTNET }
    public enum FunctionStatus { ACTIVE, DISABLED, UPDATING, ERROR }
}
