package com.minicloud.api.auth;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.security.Principal;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserPrincipal implements Principal {
    private String username;
    private UUID userId;

    @Override
    public String getName() {
        return username;
    }
}
