package com.minicloud.api.domain;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "lambda_functions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Function {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "function_name", unique = true, nullable = false)
    private String name;

    private String description;
    private UUID userId;
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

    private int memoryMb;       // Performance config
    private int timeoutSec;     // Max execution time

    @Column(name = "environment_vars", columnDefinition = "TEXT")
    private String environmentConfig; // JSON map of env vars

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private FunctionStatus status = FunctionStatus.ACTIVE;

    @CreatedDate
    private LocalDateTime createdAt;
    private LocalDateTime lastInvokedAt;
    private long invocationCount;
    private int lastExitCode;

    public enum Runtime { JAVA, NODE, PYTHON, BASH, RUBY, GO, DOTNET }
    public enum FunctionStatus { ACTIVE, DISABLED, UPDATING, ERROR }
}
