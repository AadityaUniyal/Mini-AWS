package com.minicloud.api.iam;

import com.minicloud.core.dto.AccessKeyResponse;
import com.minicloud.core.dto.ApiResponse;
import com.minicloud.core.dto.UserResponse;
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
@RequestMapping("/iam")
@RequiredArgsConstructor
@Tag(name = "IAM", description = "Identity & Access Management — users and access keys")
@SecurityRequirement(name = "BearerAuth")
public class IAMController {

    private final IAMService iamService;

    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List all users (ADMIN only)")
    public ResponseEntity<ApiResponse<List<UserResponse>>> listUsers() {
        return ResponseEntity.ok(ApiResponse.ok(iamService.listAllUsers()));
    }

    @GetMapping("/users/{id}")
    @Operation(summary = "Get user by ID")
    public ResponseEntity<ApiResponse<UserResponse>> getUser(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(iamService.getUserById(id)));
    }

    @GetMapping("/me")
    @Operation(summary = "Get current user profile")
    public ResponseEntity<ApiResponse<UserResponse>> getCurrentUser(Authentication auth) {
        return ResponseEntity.ok(ApiResponse.ok(iamService.getUserByUsername(auth.getName())));
    }

    @PostMapping("/access-keys")
    @Operation(summary = "Generate a new access key pair (secret returned ONCE)")
    public ResponseEntity<ApiResponse<AccessKeyResponse>> generateAccessKey(Authentication auth) {
        AccessKeyResponse response = iamService.generateAccessKey(auth.getName());
        return ResponseEntity.ok(ApiResponse.ok("Access key generated — store secret safely, it won't be shown again", response));
    }

    @GetMapping("/access-keys")
    @Operation(summary = "List current user's access keys")
    public ResponseEntity<ApiResponse<List<AccessKeyResponse>>> listAccessKeys(Authentication auth) {
        return ResponseEntity.ok(ApiResponse.ok(iamService.listAccessKeys(auth.getName())));
    }

    @DeleteMapping("/access-keys/{id}")
    @Operation(summary = "Revoke an access key")
    public ResponseEntity<ApiResponse<String>> revokeAccessKey(@PathVariable UUID id, Authentication auth) {
        iamService.revokeAccessKey(id, auth.getName());
        return ResponseEntity.ok(ApiResponse.ok("Access key revoked", id.toString()));
    }
}
