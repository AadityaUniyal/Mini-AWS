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

    private int cpuCores;
    private int ramMb;
    private int diskGb;
    
    private LocalDateTime launchedAt;
    
    @CreatedDate
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    private LocalDateTime updatedAt;
}
