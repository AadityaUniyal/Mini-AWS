package com.minicloud.api.iam;

import com.minicloud.api.domain.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Dual-write sync service for zero-downtime migration.
 *
 * During migration, the monolith continues writing to its own DB.
 * This service accepts sync events from the monolith (via REST or messaging)
 * and replicates user data into the IAM service's own DB.
 *
 * Once migration is complete, the monolith's IAM writes are disabled
 * and all traffic is routed to this service.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DualWriteSyncService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Sync a user from the monolith into the IAM service DB.
     * Called during migration to replicate existing users.
     */
    @Transactional
    public void syncUser(UUID externalId, String username, String passwordHash, String role) {
        if (userRepository.existsByUsername(username)) {
            log.debug("IAM sync: user '{}' already exists, skipping", username);
            return;
        }

        UserRole userRole;
        try {
            userRole = UserRole.valueOf(role.toUpperCase());
        } catch (IllegalArgumentException e) {
            userRole = UserRole.DEVELOPER;
        }

        User user = User.builder()
                .username(username)
                .passwordHash(passwordHash) // reuse existing hash — same BCrypt secret
                .role(userRole)
                .build();

        userRepository.save(user);
        log.info("IAM sync: replicated user '{}' (role={})", username, userRole);
    }

    /**
     * Delete a user from the IAM service DB (called when monolith deletes a user).
     */
    @Transactional
    public void deleteUser(String username) {
        userRepository.findByUsername(username).ifPresent(user -> {
            userRepository.delete(user);
            log.info("IAM sync: deleted user '{}'", username);
        });
    }
}
