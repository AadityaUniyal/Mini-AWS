package com.minicloud.api.domain;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "vpc_subnets")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Subnet {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(name = "vpc_id", nullable = false)
    private UUID vpcId;

    @Column(nullable = false)
    private String cidrBlock; // e.g., 10.0.1.0/24

    private String availabilityZone;

    @Column(nullable = false)
    private String accountId;

    @CreatedDate
    private LocalDateTime createdAt;
}
