package com.minicloud.api.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "compute_security_groups")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecurityGroup {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String name;
    private String description;
    private UUID userId;
    @Builder.Default
    @OneToMany(mappedBy = "securityGroup", cascade = CascadeType.ALL, orphanRemoval = true, fetch = jakarta.persistence.FetchType.EAGER)
    private java.util.List<SecurityGroupRule> rules = new java.util.ArrayList<>();

    private LocalDateTime createdAt;
}
