package com.minicloud.api.auth;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.UUID;

public final class SecurityUtils {

    private static final UUID DEFAULT_DEV_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String DEFAULT_DEV_ACCOUNT_ID = "123456789012";

    private SecurityUtils() {}

    public static Optional<UserPrincipal> getOptionalPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
            return Optional.of(principal);
        }
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
            return Optional.of(UserPrincipal.builder()
                    .userId(DEFAULT_DEV_USER_ID)
                    .username(auth.getName())
                    .accountId(DEFAULT_DEV_ACCOUNT_ID)
                    .role("ROOT")
                    .isRoot(true)
                    .build());
        }
        return Optional.empty();
    }

    public static UserPrincipal getAuthenticatedPrincipal() {
        return getOptionalPrincipal().orElseGet(() -> UserPrincipal.builder()
                .userId(DEFAULT_DEV_USER_ID)
                .username("root")
                .accountId(DEFAULT_DEV_ACCOUNT_ID)
                .role("ROOT")
                .isRoot(true)
                .build());
    }

    public static String getAuthenticatedAccountId() {
        return getAuthenticatedPrincipal().getAccountId();
    }

    public static UUID getAuthenticatedUserId() {
        return getAuthenticatedPrincipal().getUserId();
    }

    public static String getAuthenticatedUsername() {
        return getAuthenticatedPrincipal().getUsername();
    }

    public static boolean isRootOrAdmin() {
        UserPrincipal p = getAuthenticatedPrincipal();
        return p.isRoot() || "ADMIN".equalsIgnoreCase(p.getRole());
    }

    public static void validateAccountOwnership(String targetAccountId) {
        if (targetAccountId == null) return;
        Optional<UserPrincipal> optional = getOptionalPrincipal();
        if (optional.isPresent()) {
            UserPrincipal p = optional.get();
            if (!p.isRoot() && p.getAccountId() != null && !p.getAccountId().equals(targetAccountId)) {
                throw new AccessDeniedException("Access denied: You do not own resources in account " + targetAccountId);
            }
        }
    }
}
