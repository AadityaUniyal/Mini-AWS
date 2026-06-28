package com.minicloud.api.route;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "route53_hosted_zones")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HostedZone {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name; // e.g. example.com.

    private String callerReference; // Unique string to prevent duplicate requests

    private String comment;

    private String accountId;

    private LocalDateTime createdAt;
}
