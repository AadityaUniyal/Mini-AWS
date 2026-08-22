package com.minicloud.api.iam;

import com.minicloud.api.domain.User;
import com.minicloud.api.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Login and registration endpoints")
public class AuthController {
    private final AuthService authService;

    @PostMapping("/login")
    @Operation(summary = "Login and obtain JWT token (ROOT or IAM)")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.ok("Login successful", response));
    }

    @PostMapping("/login/root")
    @Operation(summary = "Root account owner login with Email and Password")
    public ResponseEntity<ApiResponse<LoginResponse>> loginRoot(@Valid @RequestBody RootLoginRequest request) {
        LoginResponse response = authService.loginRoot(request);
        return ResponseEntity.ok(ApiResponse.ok("Root login successful", response));
    }

    @PostMapping("/login/iam")
    @Operation(summary = "IAM child user login with Account ID, Username, and Password")
    public ResponseEntity<ApiResponse<LoginResponse>> loginIam(@Valid @RequestBody IamLoginRequest request) {
        LoginResponse response = authService.loginIam(request);
        return ResponseEntity.ok(ApiResponse.ok("IAM login successful", response));
    }

    @PostMapping("/register")
    @Operation(summary = "Register a new root user and account")
    public ResponseEntity<ApiResponse<Map<String, String>>> register(@Valid @RequestBody CreateUserRequest request) {
        User user = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Account created successfully", Map.of(
                        "userId",    user.getId().toString(),
                        "accountId", user.getAccountId(),
                        "username",  user.getUsername()
                )));
    }
}
