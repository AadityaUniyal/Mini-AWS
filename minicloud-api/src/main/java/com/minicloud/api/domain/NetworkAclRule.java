package com.minicloud.api.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "network_acl_rules")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NetworkAclRule {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "nacl_id")
    @JsonIgnore
    @ToString.Exclude
    private NetworkAcl networkAcl;

    @Column(name = "rule_number", nullable = false)
    private int ruleNumber;

    @Builder.Default
    @Column(nullable = false)
    private String type = "INGRESS"; // INGRESS, EGRESS

    @Builder.Default
    @Column(nullable = false)
    private String protocol = "ALL"; // TCP, UDP, ALL, ICMP

    @Builder.Default
    private int fromPort = 0;
    @Builder.Default
    private int toPort = 65535;

    @Builder.Default
    private String cidrBlock = "0.0.0.0/0";

    @Builder.Default
    private boolean allow = true; // true = ALLOW, false = DENY

    @Column(name = "rule_action")
    private String ruleAction; // "ALLOW" or "DENY"

    private LocalDateTime createdAt;

    public boolean isEgress() {
        return "EGRESS".equalsIgnoreCase(type);
    }

    public void setEgress(boolean egress) {
        this.type = egress ? "EGRESS" : "INGRESS";
    }

    public String getRuleAction() {
        if (ruleAction != null) return ruleAction.toUpperCase();
        return allow ? "ALLOW" : "DENY";
    }

    public void setRuleAction(String action) {
        this.ruleAction = action;
        this.allow = "ALLOW".equalsIgnoreCase(action);
    }
}
