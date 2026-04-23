package com.minicloud.api.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "compute_instances")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Instance {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String name;

    @Enumerated(EnumType.STRING)
    private InstanceType type;
    
    @Enumerated(EnumType.STRING)
    private InstanceState state;
    
    private UUID userId;
    private String accountId; // 12-digit account ID
    private UUID subnetId;
    private UUID securityGroupId;
    private String privateIp;
    private String publicIp;
    private String command;
    private Long pid;
    
    private LocalDateTime launchedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
