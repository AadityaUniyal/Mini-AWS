package com.minicloud.api.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "lambda_functions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Function {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true, nullable = false)
    private String name;

    private String description;
    private UUID userId;
    private String accountId;

    @Enumerated(EnumType.STRING)
    private Runtime runtime;

    private String handler;     // e.g. "Main.handler" or "index.js"
    @Column(name = "s3_bucket")
    private String s3Bucket;    // Location of deployment artifact
    @Column(name = "s3_key")
    private String s3Key;       // Key of deployment artifact

    private int memoryMb;       // Performance config
    private int timeoutSec;     // Max execution time

    @Column(columnDefinition = "TEXT")
    private String environmentConfig; // JSON map of env vars

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private FunctionStatus status = FunctionStatus.ACTIVE;

    private LocalDateTime createdAt;
    private LocalDateTime lastInvokedAt;
    private long invocationCount;
    private int lastExitCode;

    public enum Runtime { JAVA, NODE, PYTHON, BASH, RUBY, GO, DOTNET }
    public enum FunctionStatus { ACTIVE, DISABLED, UPDATING, ERROR }
}
