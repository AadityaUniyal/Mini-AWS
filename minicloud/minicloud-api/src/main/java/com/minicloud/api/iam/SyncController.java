package com.minicloud.api.iam;

import com.minicloud.api.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Internal sync endpoint for dual-write migration pattern.
 * Only accessible by ADMIN role (monolith uses admin credentials to sync).
 */
@RestController
@RequestMapping("/iam/sync")
@RequiredArgsConstructor
@Tag(name = "Sync", description = "Internal dual-write sync endpoints for migration")
public class SyncController {

    private final DualWriteSyncService syncService;

    @PostMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Sync a user from the monolith (dual-write migration)")
    public ResponseEntity<ApiResponse<String>> syncUser(@RequestBody SyncUserRequest request) {
        syncService.syncUser(
                request.getExternalId(),
                request.getUsername(),
                request.getPasswordHash(),
                request.getRole()
        );
        return ResponseEntity.ok(ApiResponse.ok("User synced", request.getUsername()));
    }

    @DeleteMapping("/users/{username}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete a synced user (dual-write migration)")
    public ResponseEntity<ApiResponse<String>> deleteUser(@PathVariable String username) {
        syncService.deleteUser(username);
        return ResponseEntity.ok(ApiResponse.ok("User deleted", username));
    }

    @Data
    public static class SyncUserRequest {
        private UUID externalId;
        private String username;
        private String passwordHash;
        private String role;
    }
}
