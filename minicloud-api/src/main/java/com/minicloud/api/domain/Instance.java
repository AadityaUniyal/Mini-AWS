package com.minicloud.api.domain;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "compute_instances")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Instance {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "instance_name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "instance_type", nullable = false)
    private InstanceType type;
    
    @Enumerated(EnumType.STRING)
    private InstanceState state;
    
    private UUID userId;
    private String accountId; // 12-digit account ID
    private UUID subnetId;
    private UUID securityGroupId;
    private String privateIp;
    private String publicIp;

    @Column(name = "launch_command", columnDefinition = "TEXT")
    private String command;

    @Column(name = "process_id")
    private Long pid;

    @Column(name = "container_id")
    private String containerId;

    @Builder.Default
    private int cpuCores = 1;
    @Builder.Default
    private int ramMb = 1024;
    @Builder.Default
    private int diskGb = 10;

    @Builder.Default
    private double cpuUsage = 0;
    @Builder.Default
    private double memoryUsage = 0;
    @Builder.Default
    private long networkIn = 0;
    @Builder.Default
    private long networkOut = 0;

    private LocalDateTime lastHeartbeat;

    @Version
    private Long version;
    
    private LocalDateTime launchedAt;
    
    @CreatedDate
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    private LocalDateTime updatedAt;
}
