package com.minicloud.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {
    private String username;
    
    @NotBlank
    private String password;
    
    private String accountId; // For IAM users
    
    @NotBlank
    private String email;     // For Root users
    
    private String loginType; // ROOT or IAM
}
