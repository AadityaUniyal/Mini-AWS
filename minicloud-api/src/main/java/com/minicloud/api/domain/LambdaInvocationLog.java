package com.minicloud.api.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "lambda_invocation_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LambdaInvocationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID functionId;
    private String functionName;
    private UUID callerUserId;

    private LocalDateTime timestamp;
    private long durationMs;
    private int exitCode;
    private String status;

    @Column(columnDefinition = "TEXT")
    private String output;

    @Column(columnDefinition = "TEXT")
    private String errorOutput;
    
    private String payload;
}
