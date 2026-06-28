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
    
    private String engine;
    
    @Column(name = "instance_class")
    private String instanceClass;

    @Column(name = "allocated_storage_gb")
    private int allocatedStorageGb;
    
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
    
    @CreatedDate
    private LocalDateTime createdAt;
}
