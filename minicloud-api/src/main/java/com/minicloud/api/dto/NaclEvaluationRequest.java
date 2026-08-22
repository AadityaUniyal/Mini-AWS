package com.minicloud.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NaclEvaluationRequest {
    @NotBlank(message = "Source or destination IP is required")
    private String ip;

    private int port;
    private String protocol; // TCP, UDP, ICMP
    private boolean egress;
}
