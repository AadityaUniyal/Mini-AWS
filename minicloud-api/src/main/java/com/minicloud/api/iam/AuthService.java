package com.minicloud.api.iam;

import com.minicloud.api.auth.JwtUtil;
import com.minicloud.api.domain.User;
import com.minicloud.api.domain.UserRepository;
import com.minicloud.api.domain.UserRole;
import com.minicloud.api.dto.CreateUserRequest;
import com.minicloud.api.dto.IamLoginRequest;
import com.minicloud.api.dto.LoginRequest;
import com.minicloud.api.dto.LoginResponse;
import com.minicloud.api.dto.RootLoginRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final com.minicloud.api.audit.AuditService auditService;
    private final com.minicloud.api.route.VpcService vpcService;

    public LoginResponse login(LoginRequest request) {
        User user;
        if ("ROOT".equalsIgnoreCase(request.getLoginType()) || (request.getEmail() != null && !request.getEmail().isBlank() && request.getAccountId() == null)) {
            user = userRepository.findByEmail(request.getEmail())
                    .orElseThrow(() -> new RuntimeException("Invalid credentials"));
        } else if (request.getAccountId() != null && !request.getAccountId().isBlank()) {
            user = userRepository.findByAccountIdAndUsername(request.getAccountId(), request.getUsername())
                    .orElseThrow(() -> new RuntimeException("Invalid credentials"));
        } else if (request.getUsername() != null && !request.getUsername().isBlank()) {
            user = userRepository.findByUsername(request.getUsername())
                    .orElseThrow(() -> new RuntimeException("Invalid credentials"));
        } else {
            throw new RuntimeException("Invalid credentials: username/email or accountId required");
        }

        return createLoginResponse(user, request.getPassword());
    }

    public LoginResponse loginRoot(RootLoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Invalid credentials"));
        return createLoginResponse(user, request.getPassword());
    }

    public LoginResponse loginIam(IamLoginRequest request) {
        User user = userRepository.findByAccountIdAndUsername(request.getAccountId(), request.getUsername())
                .orElseThrow(() -> new RuntimeException("Invalid credentials"));
        return createLoginResponse(user, request.getPassword());
    }

    private LoginResponse createLoginResponse(User user, String rawPassword) {
        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new RuntimeException("Invalid credentials");
        }

        user.setLastLogin(LocalDateTime.now());
        user.setLoginCount(user.getLoginCount() + 1);
        userRepository.save(user);

        String token = jwtUtil.generateToken(
                user.getUsername(),
                user.getRole() != null ? user.getRole().name() : "USER",
                user.getId().toString(),
                user.getAccountId(),
                Boolean.TRUE.equals(user.getRootUser())
        );

        auditService.recordSuccess(user.getUsername(), "IAM", "Login", "Successful sign-in");

        return LoginResponse.builder()
                .token(token)
                .userId(user.getId().toString())
                .username(user.getUsername())
                .accountId(user.getAccountId())
                .rootUser(Boolean.TRUE.equals(user.getRootUser()))
                .role(user.getRole() != null ? user.getRole().name() : "USER")
                .expiresIn(jwtUtil.getExpiryMs())
                .build();
    }

    public User register(CreateUserRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already exists: " + request.getEmail());
        }

        String accountId = generateAccountId();
        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .accountId(accountId)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(UserRole.ADMIN)
                .rootUser(Boolean.TRUE)
                .enabled(true)
                .build();

        User saved = userRepository.save(user);

        // Provision default infrastructure for this newly registered AWS account owner
        vpcService.createDefaultVpc(accountId);

        auditService.recordSuccess(saved.getUsername(), "IAM", "CreateAccount", "Account " + accountId + " created");
        return saved;
    }

    private String generateAccountId() {
        StringBuilder sb = new StringBuilder();
        java.util.Random rnd = new java.util.Random();
        for (int i = 0; i < 12; i++) {
            sb.append(rnd.nextInt(10));
        }
        return sb.toString();
    }
}
