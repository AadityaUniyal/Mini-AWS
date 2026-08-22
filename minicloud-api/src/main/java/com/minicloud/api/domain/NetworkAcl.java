package com.minicloud.api.domain;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "network_acls")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class NetworkAcl {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    private UUID vpcId;
    private UUID subnetId;
    private String accountId;

    @Builder.Default
    private boolean isDefault = false;

    @Builder.Default
    @OneToMany(mappedBy = "networkAcl", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<NetworkAclRule> rules = new ArrayList<>();

    @CreatedDate
    private LocalDateTime createdAt;

    public void addRule(NetworkAclRule rule) {
        if (this.rules == null) {
            this.rules = new ArrayList<>();
        }
        rule.setNetworkAcl(this);
        this.rules.add(rule);
    }
}
