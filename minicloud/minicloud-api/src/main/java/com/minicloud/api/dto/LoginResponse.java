package com.minicloud.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {
    private String token;
    private String userId;
    private String username;
    private String accountId;
    private boolean rootUser;
    private String role;
    private long expiresIn;
}
