package com.minicloud.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateUserRequest {
    @NotBlank
    @Size(min=3, max=64)
    private String username;
    
    @NotBlank
    @Size(min=8, max=128)
    private String password;
    
    @NotBlank
    @Email
    private String email;
    
    private String accountName;
    private String role; // ADMIN, DEVELOPER, READONLY
}
