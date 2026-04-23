package com.minicloud.api.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "rds_instances")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RdsInstance {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true, nullable = false)
    private String name;
    
    private String dbName;
    private String masterUsername;
    private String masterPassword;
    private int port;
    private String status;
    private String endpoint;
    private Long pid;
    
    private UUID userId;
    private String accountId;
    private UUID subnetId;
    private UUID securityGroupId;
    
    private LocalDateTime createdAt;
}
