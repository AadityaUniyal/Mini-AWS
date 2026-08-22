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
public class InstanceRequest {
    @NotBlank
    private String name;
    private String type;    // MICRO, SMALL, MEDIUM
    private String command; // command to execute as the "instance"
    private String securityGroupId; 
}
