package com.minicloud.api.auth;

import com.minicloud.api.iam.User;
import com.minicloud.api.iam.UserRepository;
import com.minicloud.api.iam.UserRole;
import com.minicloud.core.dto.CreateUserRequest;
import com.minicloud.core.dto.LoginResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public LoginResponse login(String username, String password) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Invalid credentials"));

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new RuntimeException("Invalid credentials");
        }

        String token = jwtUtil.generateToken(user.getUsername(), user.getRole().name());

        return LoginResponse.builder()
                .token(token)
                .username(user.getUsername())
                .role(user.getRole().name())
                .expiresIn(jwtUtil.getExpiryMs())
                .build();
    }

    public User register(CreateUserRequest request) {
        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            throw new RuntimeException("Username already exists: " + request.getUsername());
        }

        UserRole role;
        try {
            role = UserRole.valueOf(request.getRole() != null ? request.getRole().toUpperCase() : "DEVELOPER");
        } catch (IllegalArgumentException e) {
            role = UserRole.DEVELOPER;
        }

        User user = User.builder()
                .username(request.getUsername())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(role)
                .build();

        return userRepository.save(user);
    }
}
