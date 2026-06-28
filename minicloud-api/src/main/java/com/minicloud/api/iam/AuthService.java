package com.minicloud.api.iam;

import com.minicloud.api.dto.CreateUserRequest;
import com.minicloud.api.dto.LoginResponse;
import com.minicloud.api.domain.*;
import com.minicloud.api.auth.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final com.minicloud.api.audit.AuditService auditService;
    private final com.minicloud.api.route.VpcService vpcService;

    public LoginResponse login(com.minicloud.api.dto.LoginRequest request) {
        User user;
        if ("ROOT".equalsIgnoreCase(request.getLoginType())) {
            user = userRepository.findByEmail(request.getEmail())
                    .orElseThrow(() -> new RuntimeException("Invalid credentials"));
        } else {
            user = userRepository.findByAccountIdAndUsername(request.getAccountId(), request.getUsername())
                    .orElseThrow(() -> new RuntimeException("Invalid credentials"));
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new RuntimeException("Invalid credentials");
        }

        String token = jwtUtil.generateToken(
                user.getUsername(),
                user.getRole().name(),
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
                .role(user.getRole().name())
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

        // Provision infrastructure
        vpcService.createDefaultVpc(accountId);

        auditService.recordSuccess(saved.getUsername(), "IAM", "CreateAccount", "Account " + accountId + " created");
        return saved;
    }

    private String generateAccountId() {
        // Generate a random 12-digit number
        StringBuilder sb = new StringBuilder();
        java.util.Random rnd = new java.util.Random();
        for (int i = 0; i < 12; i++) {
            sb.append(rnd.nextInt(10));
        }
        return sb.toString();
    }
}
