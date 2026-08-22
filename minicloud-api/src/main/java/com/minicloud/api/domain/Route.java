package com.minicloud.api.domain;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "routes")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Route {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true, nullable = false)
    private String name;

    private String domainOrPath;
    private String hostPattern;
    private String targetUrl;
    private String targetHost;
    private int targetPort;
    private String stripPrefix;
    private String type; // e.g., "PROXY", "STATIC", "LAMBDA"
    private boolean enabled;
    private boolean healthy;
    private LocalDateTime lastHealthCheck;
    private long requestCount;
    
    private UUID userId;
    private String accountId;
    
    @Column(name = "ec2_instance_id")
    private UUID ec2InstanceId;
    
    @Version
    private Long version;

    @CreatedDate
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    private LocalDateTime updatedAt;
}
