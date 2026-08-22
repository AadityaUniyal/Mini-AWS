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
public class RdsRequest {
    @NotBlank
    private String name;
    private String databaseName;
    @NotBlank
    private String engine;
    private String masterUsername;
    private String masterPassword;
    private String securityGroupId;
}
