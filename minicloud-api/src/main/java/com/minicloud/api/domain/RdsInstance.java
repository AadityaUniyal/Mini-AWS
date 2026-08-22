package com.minicloud.api.domain;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "rds_instances")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class RdsInstance {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "db_instance_identifier", unique = true, nullable = false)
    private String name;
    
    @Builder.Default
    private String engine = "h2";
    
    @Column(name = "instance_class")
    @Builder.Default
    private String instanceClass = "db.t3.micro";

    @Column(name = "allocated_storage_gb")
    @Builder.Default
    private int allocatedStorageGb = 20;
    
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

    @Builder.Default
    private double cpuUsage = 0;
    @Builder.Default
    private double memoryUsage = 0;
    @Builder.Default
    private int connectionsCount = 0;
    private LocalDateTime lastBackup;

    @Version
    private Long version;
    
    @CreatedDate
    private LocalDateTime createdAt;
}
