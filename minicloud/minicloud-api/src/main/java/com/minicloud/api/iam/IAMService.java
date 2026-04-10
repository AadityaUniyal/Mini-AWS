package com.minicloud.api.iam;

import com.minicloud.api.exception.ResourceNotFoundException;
import com.minicloud.core.dto.AccessKeyResponse;
import com.minicloud.core.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class IAMService {

    private final UserRepository userRepository;
    private final AccessKeyRepository accessKeyRepository;
    private final PasswordEncoder passwordEncoder;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    public List<UserResponse> listAllUsers() {
        return userRepository.findAll().stream()
                .map(this::toUserResponse)
                .collect(Collectors.toList());
    }

    public UserResponse getUserById(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
        return toUserResponse(user);
    }

    public UserResponse getUserByUsername(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
        return toUserResponse(user);
    }

    /**
     * Generate a new access key pair for the user.
     * Returns the raw secretKey ONCE — it is never stored in plaintext.
     */
    public AccessKeyResponse generateAccessKey(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));

        String rawKeyId = "MCAK-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
        String rawSecret = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");

        AccessKey accessKey = AccessKey.builder()
                .userId(user.getId())
                .keyId(rawKeyId)
                .secretKeyHash(passwordEncoder.encode(rawSecret))
                .active(true)
                .build();

        AccessKey saved = accessKeyRepository.save(accessKey);

        return AccessKeyResponse.builder()
                .id(saved.getId().toString())
                .keyId(rawKeyId)
                .secretKey(rawSecret) // returned ONCE only
                .active(true)
                .createdAt(saved.getCreatedAt() != null ? saved.getCreatedAt().format(FMT) : "")
                .build();
    }

    public List<AccessKeyResponse> listAccessKeys(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));

        return accessKeyRepository.findByUserId(user.getId()).stream()
                .map(k -> AccessKeyResponse.builder()
                        .id(k.getId().toString())
                        .keyId(k.getKeyId())
                        .secretKey("***HIDDEN***")
                        .active(k.isActive())
                        .createdAt(k.getCreatedAt() != null ? k.getCreatedAt().format(FMT) : "")
                        .build())
                .collect(Collectors.toList());
    }

    public void revokeAccessKey(UUID keyId, String username) {
        AccessKey key = accessKeyRepository.findById(keyId)
                .orElseThrow(() -> new ResourceNotFoundException("Access key not found: " + keyId));

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Ensure the key belongs to this user (or user is ADMIN)
        if (!key.getUserId().equals(user.getId()) && user.getRole() != UserRole.ADMIN) {
            throw new RuntimeException("Access denied");
        }

        key.setActive(false);
        accessKeyRepository.save(key);
    }

    private UserResponse toUserResponse(User user) {
        return UserResponse.builder()
                .id(user.getId().toString())
                .username(user.getUsername())
                .role(user.getRole().name())
                .createdAt(user.getCreatedAt() != null ? user.getCreatedAt().format(FMT) : "")
                .build();
    }
}
