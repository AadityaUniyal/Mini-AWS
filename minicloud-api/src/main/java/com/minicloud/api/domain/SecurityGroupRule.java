package com.minicloud.api.domain;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "compute_security_group_rules")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecurityGroupRule {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "security_group_id")
    private SecurityGroup securityGroup;

    @Enumerated(EnumType.STRING)
    private RuleType type; // INGRESS, EGRESS

    @Enumerated(EnumType.STRING)
    private Protocol protocol;

    private int fromPort;
    private int toPort;
    private String cidrIp; // e.g., "0.0.0.0/0"

    public enum RuleType { INGRESS, EGRESS }
    public enum Protocol { TCP, UDP, ICMP, ALL }
}
