package com.minicloud.api.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.security.Principal;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserPrincipal implements Principal {
    private String username;
    private UUID userId;
    private String accountId;
    private String role;
    private boolean isRoot;

    public UserPrincipal(String username, UUID userId) {
        this.username = username;
        this.userId = userId;
    }

    @Override
    public String getName() {
        return username;
    }
}
