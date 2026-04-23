package com.minicloud.api.compute;

import com.minicloud.api.domain.*;
import com.minicloud.api.dto.ApiResponse;
import com.minicloud.api.dto.SecurityGroupResponse;
import com.minicloud.api.dto.SecurityGroupRuleDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/compute/security-groups")
@RequiredArgsConstructor
@Tag(name = "EC2 VPC Security", description = "Firewall rules management")
public class SecurityGroupController {

    private final SecurityGroupRepository securityGroupRepository;

    @PostMapping
    @Operation(summary = "Create a new security group")
    public ResponseEntity<ApiResponse<SecurityGroupResponse>> create(
            @RequestParam String name,
            @RequestParam String description,
            @RequestParam UUID userId) {
        
        SecurityGroup sg = SecurityGroup.builder()
                .name(name)
                .description(description)
                .userId(userId)
                .build();
        SecurityGroup saved = securityGroupRepository.save(sg);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Security Group created", toSgResponse(saved)));
    }

    @PostMapping("/{id}/rules")
    @Operation(summary = "Authorize Ingress (add a rule)")
    public ResponseEntity<ApiResponse<SecurityGroupResponse>> addRule(
            @PathVariable UUID id,
            @RequestBody SecurityGroupRuleDto ruleDto) {
        
        SecurityGroup sg = securityGroupRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Security Group not found"));

        SecurityGroupRule.Protocol protocol;
        try {
            protocol = SecurityGroupRule.Protocol.valueOf(ruleDto.getProtocol().toUpperCase());
        } catch (Exception e) {
            protocol = SecurityGroupRule.Protocol.TCP;
        }

        SecurityGroupRule rule = SecurityGroupRule.builder()
                .securityGroup(sg)
                .type(SecurityGroupRule.RuleType.INGRESS)
                .protocol(protocol)
                .fromPort(ruleDto.getFromPort())
                .toPort(ruleDto.getToPort())
                .cidrIp(ruleDto.getCidrIp())
                .build();
        
        sg.getRules().add(rule);
        SecurityGroup saved = securityGroupRepository.save(sg);
        return ResponseEntity.ok(ApiResponse.ok("Rule added", toSgResponse(saved)));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<ApiResponse<List<SecurityGroupResponse>>> list(@PathVariable UUID userId) {
        List<SecurityGroupResponse> responses = securityGroupRepository.findByUserId(userId).stream()
                .map(this::toSgResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(responses));
    }

    private SecurityGroupResponse toSgResponse(SecurityGroup sg) {
        return SecurityGroupResponse.builder()
                .id(sg.getId().toString())
                .name(sg.getName())
                .description(sg.getDescription())
                .userId(sg.getUserId().toString())
                .ingressRules(sg.getRules().stream().map(this::toRuleDto).collect(Collectors.toList()))
                .egressRules(List.of()) // Simplification for now
                .build();
    }

    private SecurityGroupRuleDto toRuleDto(SecurityGroupRule rule) {
        return SecurityGroupRuleDto.builder()
                .protocol(rule.getProtocol().name())
                .fromPort(rule.getFromPort())
                .toPort(rule.getToPort())
                .cidrIp(rule.getCidrIp())
                .build();
    }
}
