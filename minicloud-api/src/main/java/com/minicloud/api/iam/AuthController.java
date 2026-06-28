package com.minicloud.api.iam;

import com.minicloud.api.dto.ApiResponse;
import com.minicloud.api.dto.CreateUserRequest;
import com.minicloud.api.dto.LoginRequest;
import com.minicloud.api.dto.LoginResponse;
import com.minicloud.api.domain.User;
import com.minicloud.api.iam.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Login and registration endpoints")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    @Operation(summary = "Login and obtain JWT token")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.ok("Login successful", response));
    }

    @PostMapping("/register")
    @Operation(summary = "Register a new user account")
    public ResponseEntity<ApiResponse<java.util.Map<String, String>>> register(@RequestBody CreateUserRequest request) {
        User user = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Account created successfully", java.util.Map.of(
                        "userId",    user.getId().toString(),
                        "accountId", user.getAccountId(),
                        "username",  user.getUsername()
                )));
    }
}
