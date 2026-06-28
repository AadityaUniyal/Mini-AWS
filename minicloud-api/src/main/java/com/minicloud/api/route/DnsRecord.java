package com.minicloud.api.route;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "route53_records")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DnsRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "hosted_zone_id")
    private HostedZone hostedZone;

    @Column(nullable = false)
    private String name; // e.g. www.example.com.

    @Column(nullable = false)
    private String type; // A, CNAME, TXT, etc.

    private Long ttl;

    @Column(name = "record_value", columnDefinition = "TEXT")
    private String recordValue; // IP address or target domain

    private String accountId;
}
