package com.minicloud.api.compute;

import jakarta.persistence.*;
import lombok.*;

@Embeddable
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecurityGroupRule {

    @Enumerated(EnumType.STRING)
    @Column(name = "protocol", nullable = false)
    private Protocol protocol; // TCP, UDP, ICMP, ALL

    @Column(name = "from_port")
    private Integer fromPort;

    @Column(name = "to_port")
    private Integer toPort;

    @Column(name = "cidr_ip", length = 50)
    private String cidrIp; // e.g., "0.0.0.0/0"

    public enum Protocol {
        TCP, UDP, ICMP, ALL
    }
}
