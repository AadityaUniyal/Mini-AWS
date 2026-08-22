package com.minicloud.api.compute;

import com.minicloud.api.auth.SecurityUtils;
import com.minicloud.api.auth.UserPrincipal;
import com.minicloud.api.domain.*;
import com.minicloud.api.dto.ApiResponse;
import com.minicloud.api.dto.SecurityGroupResponse;
import com.minicloud.api.dto.SecurityGroupRuleDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/compute/security-groups")
@RequiredArgsConstructor
@Tag(name = "EC2 VPC Security", description = "Firewall rules management")
@SecurityRequirement(name = "BearerAuth")
public class SecurityGroupController {

    private final SecurityGroupRepository securityGroupRepository;

    @GetMapping
    @Operation(summary = "List all security groups for current account")
    public ResponseEntity<ApiResponse<List<SecurityGroupResponse>>> listCurrentAccount() {
        UserPrincipal principal = SecurityUtils.getAuthenticatedPrincipal();
        List<SecurityGroup> groups = principal.getAccountId() != null
                ? securityGroupRepository.findByAccountId(principal.getAccountId())
                : securityGroupRepository.findByUserId(principal.getUserId());

        List<SecurityGroupResponse> responses = groups.stream()
                .map(this::toSgResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(responses));
    }

    @PostMapping
    @Operation(summary = "Create a new security group")
    public ResponseEntity<ApiResponse<SecurityGroupResponse>> create(
            @RequestParam String name,
            @RequestParam(required = false, defaultValue = "") String description,
            @RequestParam(required = false) UUID userId) {
        
        UserPrincipal principal = SecurityUtils.getAuthenticatedPrincipal();
        UUID effectiveUserId = principal.getUserId() != null ? principal.getUserId() : userId;
        String effectiveAccountId = principal.getAccountId();

        SecurityGroup sg = SecurityGroup.builder()
                .name(name)
                .description(description)
                .userId(effectiveUserId)
                .accountId(effectiveAccountId)
                .createdAt(LocalDateTime.now())
                .build();
        SecurityGroup saved = securityGroupRepository.save(sg);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Security Group created", toSgResponse(saved)));
    }

    @PostMapping("/{id}/rules")
    @Operation(summary = "Authorize Ingress or Egress (add a rule)")
    public ResponseEntity<ApiResponse<SecurityGroupResponse>> addRule(
            @PathVariable UUID id,
            @RequestBody SecurityGroupRuleDto ruleDto) {
        
        SecurityGroup sg = securityGroupRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Security Group not found: " + id));

        SecurityUtils.validateAccountOwnership(sg.getAccountId());

        SecurityGroupRule.Protocol protocol;
        try {
            protocol = SecurityGroupRule.Protocol.valueOf(ruleDto.getProtocol().toUpperCase());
        } catch (Exception e) {
            protocol = SecurityGroupRule.Protocol.TCP;
        }

        SecurityGroupRule.RuleType type = SecurityGroupRule.RuleType.INGRESS;
        if (ruleDto.getType() != null && "EGRESS".equalsIgnoreCase(ruleDto.getType())) {
            type = SecurityGroupRule.RuleType.EGRESS;
        }

        SecurityGroupRule rule = SecurityGroupRule.builder()
                .securityGroup(sg)
                .type(type)
                .protocol(protocol)
                .fromPort(ruleDto.getFromPort())
                .toPort(ruleDto.getToPort())
                .cidrIp(ruleDto.getCidrIp() != null ? ruleDto.getCidrIp() : "0.0.0.0/0")
                .build();
        
        sg.getRules().add(rule);
        SecurityGroup saved = securityGroupRepository.save(sg);
        return ResponseEntity.ok(ApiResponse.ok("Rule added", toSgResponse(saved)));
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "List security groups by user ID")
    public ResponseEntity<ApiResponse<List<SecurityGroupResponse>>> list(@PathVariable UUID userId) {
        List<SecurityGroupResponse> responses = securityGroupRepository.findByUserId(userId).stream()
                .map(this::toSgResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(responses));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete security group")
    public ResponseEntity<ApiResponse<String>> delete(@PathVariable UUID id) {
        SecurityGroup sg = securityGroupRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Security Group not found: " + id));
        SecurityUtils.validateAccountOwnership(sg.getAccountId());
        securityGroupRepository.delete(sg);
        return ResponseEntity.ok(ApiResponse.ok("Security Group deleted", id.toString()));
    }

    private SecurityGroupResponse toSgResponse(SecurityGroup sg) {
        List<SecurityGroupRuleDto> ingress = sg.getRules().stream()
                .filter(r -> r.getType() == SecurityGroupRule.RuleType.INGRESS)
                .map(this::toRuleDto)
                .collect(Collectors.toList());

        List<SecurityGroupRuleDto> egress = sg.getRules().stream()
                .filter(r -> r.getType() == SecurityGroupRule.RuleType.EGRESS)
                .map(this::toRuleDto)
                .collect(Collectors.toList());

        return SecurityGroupResponse.builder()
                .id(sg.getId().toString())
                .name(sg.getName())
                .description(sg.getDescription())
                .userId(sg.getUserId() != null ? sg.getUserId().toString() : null)
                .ingressRules(ingress)
                .egressRules(egress)
                .build();
    }

    private SecurityGroupRuleDto toRuleDto(SecurityGroupRule rule) {
        return SecurityGroupRuleDto.builder()
                .type(rule.getType() != null ? rule.getType().name() : "INGRESS")
                .protocol(rule.getProtocol().name())
                .fromPort(rule.getFromPort())
                .toPort(rule.getToPort())
                .cidrIp(rule.getCidrIp())
                .build();
    }
}
