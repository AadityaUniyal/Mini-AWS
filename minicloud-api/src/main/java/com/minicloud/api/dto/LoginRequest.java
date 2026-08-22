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
public class LoginRequest {
    private String username;
    
    @NotBlank(message = "Password is required")
    private String password;
    
    private String accountId; // For IAM users
    
    private String email;     // For Root users
    
    private String loginType; // ROOT or IAM
}
