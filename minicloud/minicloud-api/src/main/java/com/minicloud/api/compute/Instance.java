package com.minicloud.api.compute;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "mc_instances")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Instance {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @EqualsAndHashCode.Include
    private UUID id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 20)
    private InstanceType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", length = 20)
    private InstanceState state;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "pid")
    private Integer pid;

    @Column(name = "command", length = 500)
    private String command;

    @Column(name = "security_group_id")
    private UUID securityGroupId;

    @Column(name = "launched_at")
    private LocalDateTime launchedAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
