package com.minicloud.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddNaclRuleRequest {
    @NotNull(message = "Rule number is required")
    private int ruleNumber;

    @NotBlank(message = "Protocol is required (e.g. ALL, TCP, UDP, ICMP)")
    private String protocol;

    @NotBlank(message = "Rule action is required (ALLOW or DENY)")
    private String ruleAction;

    @Builder.Default
    private boolean egress = false;

    @NotBlank(message = "CIDR block is required (e.g. 0.0.0.0/0)")
    private String cidrBlock;

    private Integer fromPort;
    private Integer toPort;
}
