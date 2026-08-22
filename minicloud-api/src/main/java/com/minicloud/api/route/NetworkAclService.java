package com.minicloud.api.route;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * NetworkAclService — Simulated stateless Network ACL firewall at the Subnet boundary.
 * Evaluates rules from lowest rule number to highest rule number.
 */
@Slf4j
@Service
public class NetworkAclService {

    private final Map<UUID, List<NetworkAclRule>> subnetAcls = new ConcurrentHashMap<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NetworkAclRule {
        private int ruleNumber;
        private String protocol; // TCP, UDP, ALL
        private int fromPort;
        private int toPort;
        private String cidrBlock;
        private boolean allow; // true = ALLOW, false = DENY
    }

    /**
     * Checks if traffic is allowed through the Subnet's NACL.
     */
    public boolean isTrafficAllowed(UUID subnetId, int targetPort, String protocol, String sourceCidr) {
        if (subnetId == null) {
            return true; // No subnet bound, bypass
        }

        // Lazy initialize default NACL (Allow All) for the subnet if not present
        List<NetworkAclRule> rules = subnetAcls.computeIfAbsent(subnetId, id -> {
            List<NetworkAclRule> defaultRules = new java.util.concurrent.CopyOnWriteArrayList<>();
            defaultRules.add(NetworkAclRule.builder()
                    .ruleNumber(100)
                    .protocol("ALL")
                    .fromPort(0)
                    .toPort(65535)
                    .cidrBlock("0.0.0.0/0")
                    .allow(true)
                    .build());
            return defaultRules;
        });

        // Sort rules by rule number ascending
        List<NetworkAclRule> sortedRules = rules.stream()
                .sorted(Comparator.comparingInt(NetworkAclRule::getRuleNumber))
                .collect(Collectors.toList());

        for (NetworkAclRule rule : sortedRules) {
            if (ruleMatches(rule, targetPort, protocol, sourceCidr)) {
                log.debug("NACL Rule {} matched: traffic {}. Subnet: {}, Port: {}", 
                        rule.getRuleNumber(), rule.isAllow() ? "ALLOWED" : "DENIED", subnetId, targetPort);
                return rule.isAllow();
            }
        }

        // AWS NACL default catch-all is DENY if no rule matches
        log.warn("NACL Default Deny triggered for Subnet: {}, Port: {}", subnetId, targetPort);
        return false;
    }

    /**
     * Adds or updates a rule for a subnet's NACL.
     */
    public void addOrUpdateRule(UUID subnetId, NetworkAclRule rule) {
        List<NetworkAclRule> rules = subnetAcls.computeIfAbsent(subnetId, id -> new java.util.concurrent.CopyOnWriteArrayList<>());
        // Remove existing rule with same rule number
        rules.removeIf(r -> r.getRuleNumber() == rule.getRuleNumber());
        rules.add(rule);
        log.info("NACL Rule {} added to Subnet {}: protocol={}, port={}-{}, cidr={}, allow={}",
                rule.getRuleNumber(), subnetId, rule.getProtocol(), rule.getFromPort(), rule.getToPort(), rule.getCidrBlock(), rule.isAllow());
    }

    /**
     * Resets a subnet's NACL back to default "Allow All".
     */
    public void resetToDefault(UUID subnetId) {
        subnetAcls.remove(subnetId);
    }

    private boolean ruleMatches(NetworkAclRule rule, int targetPort, String protocol, String sourceCidr) {
        // 1. Protocol check
        if (!"ALL".equalsIgnoreCase(rule.getProtocol()) && !rule.getProtocol().equalsIgnoreCase(protocol)) {
            return false;
        }

        // 2. Port check
        if (targetPort < rule.getFromPort() || targetPort > rule.getToPort()) {
            return false;
        }

        // 3. CIDR check (Basic prefix matching for simulation, e.g. "0.0.0.0/0" matches all)
        if ("0.0.0.0/0".equals(rule.getCidrBlock())) {
            return true;
        }
        
        if (sourceCidr == null) {
            return false;
        }

        // Simple CIDR match: check if source IP starts with CIDR prefix
        String ruleIpPrefix = rule.getCidrBlock().split("/")[0];
        String sourceIp = sourceCidr.split("/")[0];
        
        // Match up to subnet bounds
        if (rule.getCidrBlock().contains("/24")) {
            String[] ruleParts = ruleIpPrefix.split("\\.");
            String[] sourceParts = sourceIp.split("\\.");
            if (ruleParts.length >= 3 && sourceParts.length >= 3) {
                return ruleParts[0].equals(sourceParts[0]) &&
                       ruleParts[1].equals(sourceParts[1]) &&
                       ruleParts[2].equals(sourceParts[2]);
            }
        } else if (rule.getCidrBlock().contains("/16")) {
            String[] ruleParts = ruleIpPrefix.split("\\.");
            String[] sourceParts = sourceIp.split("\\.");
            if (ruleParts.length >= 2 && sourceParts.length >= 2) {
                return ruleParts[0].equals(sourceParts[0]) &&
                       ruleParts[1].equals(sourceParts[1]);
            }
        }

        return ruleIpPrefix.equalsIgnoreCase(sourceIp);
    }
}
