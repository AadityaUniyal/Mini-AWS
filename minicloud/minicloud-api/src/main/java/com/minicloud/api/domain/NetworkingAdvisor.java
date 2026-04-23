package com.minicloud.api.domain;

import org.springframework.stereotype.Component;

import java.util.Random;

/**
 * NetworkingAdvisor — VPC simulate subnet/IP assignment and SG evaluation.
 */
@Component
public class NetworkingAdvisor {

    private final Random random = new Random();

    public String assignPrivateIp() {
        return "172.31." + random.nextInt(255) + "." + random.nextInt(255);
    }

    public String assignPublicIp() {
        return "54.210." + random.nextInt(255) + "." + random.nextInt(255);
    }

    public boolean isIngressAllowed(SecurityGroup sg, int port, SecurityGroupRule.Protocol protocol, String sourceRange) {
        // Standard rule: if no SG, permit nothing (or everything? MiniCloud defaults to permitting unmanaged)
        if (sg == null) return true;

        // Simple evaluation: check if any ingress rule matches
        return sg.getRules().stream()
                .filter(r -> r.getType() == SecurityGroupRule.RuleType.INGRESS)
                .filter(r -> r.getProtocol() == protocol || r.getProtocol() == SecurityGroupRule.Protocol.ALL)
                .anyMatch(r -> (port >= r.getFromPort() && port <= r.getToPort()) || r.getFromPort() == -1);
    }
}
