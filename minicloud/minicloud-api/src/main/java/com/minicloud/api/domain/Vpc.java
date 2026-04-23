package com.minicloud.api.domain;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "vpc_networks")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Vpc {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String cidrBlock; // e.g., 10.0.0.0/16

    private String state; // available, pending

    @Column(nullable = false)
    private String accountId; // 12-digit owner ID

    private boolean isDefault;

    @CreatedDate
    private LocalDateTime createdAt;
}
