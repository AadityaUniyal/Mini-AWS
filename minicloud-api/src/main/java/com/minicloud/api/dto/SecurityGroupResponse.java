package com.minicloud.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecurityGroupResponse {
    private String id;
    private String name;
    private String description;
    private String userId;
    private List<SecurityGroupRuleDto> ingressRules;
    private List<SecurityGroupRuleDto> egressRules;
    private String createdAt;
}
