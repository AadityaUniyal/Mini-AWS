package com.minicloud.api.iam;

import com.minicloud.api.dto.AccessKeyResponse;
import com.minicloud.api.dto.ApiResponse;
import com.minicloud.api.dto.PolicyResponse;
import com.minicloud.api.dto.UserResponse;
import com.minicloud.api.iam.IamService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/iam")
@RequiredArgsConstructor
@Tag(name = "IAM", description = "Identity & Access Management — users, policies, and access keys")
@SecurityRequirement(name = "BearerAuth")
public class IamController {

    private final IamService iamService;

    // ── Users ──────────────────────────────────────────────────────────────────

    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List all users (ADMIN only)")
    public ResponseEntity<ApiResponse<List<UserResponse>>> listUsers() {
        return ResponseEntity.ok(ApiResponse.ok(iamService.listAllUsers()));
    }

    @GetMapping("/users/{identifier}")
    @Operation(summary = "Get user profile by ID or Username")
    public ResponseEntity<ApiResponse<UserResponse>> getUser(@PathVariable String identifier) {
        try {
            UUID id = UUID.fromString(identifier);
            return ResponseEntity.ok(ApiResponse.ok(iamService.getUserById(id)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.ok(ApiResponse.ok(iamService.getUserByUsername(identifier)));
        }
    }


    @GetMapping("/me")
    @Operation(summary = "Get current user profile with attached policies")
    public ResponseEntity<ApiResponse<UserResponse>> getCurrentUser(Authentication auth) {
        return ResponseEntity.ok(ApiResponse.ok(iamService.getUserByUsername(auth.getName())));
    }


    @DeleteMapping("/users/by-username/{username}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete a user by username (ADMIN only)")
    public ResponseEntity<ApiResponse<String>> deleteUserByUsername(@PathVariable String username) {
        UserResponse user = iamService.getUserByUsername(username);
        iamService.deleteUser(UUID.fromString(user.getId()));
        return ResponseEntity.ok(ApiResponse.ok("User deleted", username));
    }

    @DeleteMapping("/users/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete a user (ADMIN only — cannot delete last admin)")
    public ResponseEntity<ApiResponse<String>> deleteUser(@PathVariable UUID id) {
        iamService.deleteUser(id);
        return ResponseEntity.ok(ApiResponse.ok("User deleted", id.toString()));
    }

    // ── Policy Management ──────────────────────────────────────────────────────

    @GetMapping("/policies")
    @Operation(summary = "List all available IAM policies")
    public ResponseEntity<ApiResponse<List<PolicyResponse>>> listPolicies() {
        return ResponseEntity.ok(ApiResponse.ok(iamService.listAllPolicies()));
    }

    @PostMapping("/users/{id}/attach-policy")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Attach a policy to a user by policy name (ADMIN only)")
    public ResponseEntity<ApiResponse<UserResponse>> attachPolicy(
            @PathVariable UUID id,
            @RequestParam String policyName) {
        return ResponseEntity.ok(ApiResponse.ok("Policy attached", iamService.attachPolicy(id, policyName)));
    }

    @PostMapping("/users/{id}/detach-policy")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Detach a policy from a user by policy name (ADMIN only)")
    public ResponseEntity<ApiResponse<UserResponse>> detachPolicy(
            @PathVariable UUID id,
            @RequestParam String policyName) {
        return ResponseEntity.ok(ApiResponse.ok("Policy detached", iamService.detachPolicy(id, policyName)));
    }

    @PostMapping("/users/{username}/policy")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update a user's inline JSON policy")
    public ResponseEntity<ApiResponse<Void>> updatePolicy(
            @PathVariable String username,
            @RequestBody java.util.Map<String, String> body) {
        iamService.updateInlinePolicy(username, body.get("document"));
        return ResponseEntity.ok(ApiResponse.ok("Policy updated", null));
    }


    // ── Access Keys ────────────────────────────────────────────────────────────

    @PostMapping("/access-keys")
    @Operation(summary = "Generate a new access key pair for current user (secret returned ONCE)")
    public ResponseEntity<ApiResponse<AccessKeyResponse>> generateAccessKey(Authentication auth) {
        AccessKeyResponse response = iamService.generateAccessKey(auth.getName());
        return ResponseEntity.ok(ApiResponse.ok("Access key generated — store secret safely, it won't be shown again", response));
    }

    @PostMapping("/users/{username}/access-keys")
    @Operation(summary = "Generate a new access key pair for a specific user (ADMIN)")
    public ResponseEntity<ApiResponse<AccessKeyResponse>> generateAccessKeyForUser(@PathVariable String username) {
        AccessKeyResponse response = iamService.generateAccessKey(username);
        return ResponseEntity.ok(ApiResponse.ok("Access key generated", response));
    }

    @GetMapping("/access-keys")
    @Operation(summary = "List current user's access keys")
    public ResponseEntity<ApiResponse<List<AccessKeyResponse>>> listAccessKeys(Authentication auth) {
        return ResponseEntity.ok(ApiResponse.ok(iamService.listAccessKeys(auth.getName())));
    }

    @GetMapping("/users/{username}/access-keys")
    @Operation(summary = "List access keys for a specific user (ADMIN)")
    public ResponseEntity<ApiResponse<List<AccessKeyResponse>>> listAccessKeysForUser(@PathVariable String username) {
        return ResponseEntity.ok(ApiResponse.ok(iamService.listAccessKeys(username)));
    }

    @DeleteMapping("/access-keys/{id}")
    @Operation(summary = "Revoke an access key")
    public ResponseEntity<ApiResponse<String>> revokeAccessKey(@PathVariable UUID id, Authentication auth) {
        iamService.revokeAccessKey(id, auth.getName());
        return ResponseEntity.ok(ApiResponse.ok("Access key revoked", id.toString()));
    }
}
