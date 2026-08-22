package com.minicloud.api.route;

import com.minicloud.api.auth.SecurityUtils;
import com.minicloud.api.domain.NetworkAcl;
import com.minicloud.api.domain.NetworkAclRepository;
import com.minicloud.api.domain.NetworkAclRule;
import com.minicloud.api.dto.AddNaclRuleRequest;
import com.minicloud.api.dto.CreateNaclRequest;
import com.minicloud.api.dto.NaclEvaluationRequest;
import com.minicloud.api.dto.NaclEvaluationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NetworkAclService {

    private final NetworkAclRepository naclRepository;

    @Transactional
    public NetworkAcl createAcl(CreateNaclRequest request, String accountId) {
        NetworkAcl acl = NetworkAcl.builder()
                .name(request.getName())
                .accountId(accountId)
                .vpcId(request.getVpcId())
                .subnetId(request.getSubnetId())
                .isDefault(false)
                .createdAt(LocalDateTime.now())
                .build();

        NetworkAclRule defaultIngress = NetworkAclRule.builder()
                .ruleNumber(100)
                .type("INGRESS")
                .protocol("ALL")
                .ruleAction("ALLOW")
                .allow(true)
                .cidrBlock("0.0.0.0/0")
                .fromPort(0)
                .toPort(65535)
                .createdAt(LocalDateTime.now())
                .build();

        NetworkAclRule defaultEgress = NetworkAclRule.builder()
                .ruleNumber(100)
                .type("EGRESS")
                .protocol("ALL")
                .ruleAction("ALLOW")
                .allow(true)
                .cidrBlock("0.0.0.0/0")
                .fromPort(0)
                .toPort(65535)
                .createdAt(LocalDateTime.now())
                .build();

        acl.addRule(defaultIngress);
        acl.addRule(defaultEgress);

        return naclRepository.save(acl);
    }

    public List<NetworkAcl> listAclsForAccount(String accountId) {
        return naclRepository.findByAccountId(accountId);
    }

    public NetworkAcl getAcl(UUID id) {
        NetworkAcl acl = naclRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Network ACL not found: " + id));
        SecurityUtils.validateAccountOwnership(acl.getAccountId());
        return acl;
    }

    @Transactional
    public NetworkAcl addRule(UUID naclId, AddNaclRuleRequest request) {
        NetworkAcl acl = getAcl(naclId);

        NetworkAclRule rule = NetworkAclRule.builder()
                .ruleNumber(request.getRuleNumber())
                .type(request.isEgress() ? "EGRESS" : "INGRESS")
                .protocol(request.getProtocol() != null ? request.getProtocol().toUpperCase() : "ALL")
                .ruleAction(request.getRuleAction() != null ? request.getRuleAction().toUpperCase() : "ALLOW")
                .allow("ALLOW".equalsIgnoreCase(request.getRuleAction()))
                .cidrBlock(request.getCidrBlock() != null ? request.getCidrBlock() : "0.0.0.0/0")
                .fromPort(request.getFromPort() != null ? request.getFromPort() : 0)
                .toPort(request.getToPort() != null ? request.getToPort() : 65535)
                .createdAt(LocalDateTime.now())
                .build();

        acl.addRule(rule);
        return naclRepository.save(acl);
    }

    @Transactional
    public void deleteAcl(UUID id) {
        NetworkAcl acl = getAcl(id);
        if (acl.isDefault()) {
            throw new IllegalArgumentException("Cannot delete default VPC Network ACL");
        }
        naclRepository.delete(acl);
    }

    public boolean isTrafficAllowed(UUID subnetId, int port, String protocol, String sourceIp) {
        if (subnetId == null) return true;
        var optionalNacl = naclRepository.findBySubnetId(subnetId);
        if (optionalNacl.isEmpty()) return true;

        NaclEvaluationRequest req = NaclEvaluationRequest.builder()
                .ip(sourceIp != null ? sourceIp : "0.0.0.0")
                .port(port)
                .protocol(protocol != null ? protocol : "TCP")
                .egress(false)
                .build();
        NaclEvaluationResponse res = evaluatePacket(optionalNacl.get().getId(), req);
        return "ALLOW".equalsIgnoreCase(res.getDecision());
    }

    public NaclEvaluationResponse evaluatePacket(UUID naclId, NaclEvaluationRequest request) {
        NetworkAcl acl = getAcl(naclId);

        List<NetworkAclRule> rules = acl.getRules().stream()
                .filter(r -> r.isEgress() == request.isEgress())
                .sorted(Comparator.comparingInt(NetworkAclRule::getRuleNumber))
                .toList();

        for (NetworkAclRule rule : rules) {
            if (!"ALL".equalsIgnoreCase(rule.getProtocol()) && request.getProtocol() != null) {
                if (!rule.getProtocol().equalsIgnoreCase(request.getProtocol())) {
                    continue;
                }
            }

            if (request.getPort() > 0) {
                if (request.getPort() < rule.getFromPort() || request.getPort() > rule.getToPort()) {
                    continue;
                }
            }

            if (CidrMatcher.matches(rule.getCidrBlock(), request.getIp())) {
                return NaclEvaluationResponse.builder()
                        .decision(rule.getRuleAction())
                        .matchedRuleNumber(rule.getRuleNumber())
                        .reason("Matched rule #" + rule.getRuleNumber() + " (" + rule.getRuleAction() + ")")
                        .build();
            }
        }

        return NaclEvaluationResponse.builder()
                .decision("DENY")
                .matchedRuleNumber(32767)
                .reason("Implicit default deny (*)")
                .build();
    }
}
