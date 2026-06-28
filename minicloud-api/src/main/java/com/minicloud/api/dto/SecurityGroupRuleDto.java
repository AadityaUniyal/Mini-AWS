package com.minicloud.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecurityGroupRuleDto {
    private String protocol; // TCP, UDP, ICMP, ALL
    private Integer fromPort;
    private Integer toPort;
    private String cidrIp;
}
