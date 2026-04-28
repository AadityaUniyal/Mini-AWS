package com.minicloud.api.iam;

import com.minicloud.api.dto.AccessKeyResponse;
import com.minicloud.api.dto.PolicyResponse;
import com.minicloud.api.dto.UserResponse;
import com.minicloud.api.domain.AccessKey;
import com.minicloud.api.domain.Policy;
import com.minicloud.api.domain.User;
import com.minicloud.api.domain.UserRole;
import com.minicloud.api.domain.AccessKeyRepository;
import com.minicloud.api.domain.PolicyRepository;
import com.minicloud.api.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class IamService {

    private final UserRepository userRepository;
    private final AccessKeyRepository accessKeyRepository;
    private final PolicyRepository policyRepository;
    private final PasswordEncoder passwordEncoder;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    // ── Users ──────────────────────────────────────────────────────────────────

    public List<UserResponse> listAllUsers() {
        return userRepository.findAll().stream()
                .map(this::toUserResponse)
                .collect(Collectors.toList());
    }

    public UserResponse getUserById(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found: " + id));
        return toUserResponse(user);
    }

    public UserResponse getUserByUsername(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));
        return toUserResponse(user);
    }

    @Transactional
    public void deleteUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        if (user.getRole() == UserRole.ADMIN) {
            long adminCount = userRepository.countByRole(UserRole.ADMIN);
            if (adminCount <= 1) {
                throw new IllegalArgumentException("Cannot delete the last ADMIN user.");
            }
        }

        accessKeyRepository.deleteByUser_Id(userId);
        userRepository.delete(user);
    }

    // ── Access Keys ────────────────────────────────────────────────────────────

    public AccessKeyResponse generateAccessKey(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));

        String rawKeyId = "MCAK-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
        String rawSecret = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");

        AccessKey accessKey = AccessKey.builder()
                .user(user)
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
                .orElseThrow(() -> new RuntimeException("User not found: " + username));

        return accessKeyRepository.findByUser_Id(user.getId()).stream()
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
                .orElseThrow(() -> new RuntimeException("Access key not found: " + keyId));

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!key.getUser().getId().equals(user.getId()) && user.getRole() != UserRole.ADMIN) {
            throw new RuntimeException("Access denied");
        }

        key.setActive(false);
        accessKeyRepository.save(key);
    }

    // ── Policy Management ──────────────────────────────────────────────────────

    public List<PolicyResponse> listAllPolicies() {
        return policyRepository.findAll().stream()
                .map(p -> PolicyResponse.builder()
                        .id(p.getId().toString())
                        .name(p.getName())
                        .description(p.getDescription())
                        .managed(p.isManaged())
                        .createdAt(p.getCreatedAt() != null ? p.getCreatedAt().format(FMT) : "")
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional
    public UserResponse attachPolicy(UUID userId, String policyName) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));
        Policy policy = policyRepository.findByName(policyName)
                .orElseThrow(() -> new RuntimeException("Policy not found: " + policyName));
        user.getPolicies().add(policy);
        return toUserResponse(userRepository.save(user));
    }

    @Transactional
    public UserResponse detachPolicy(UUID userId, String policyName) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));
        Policy policy = policyRepository.findByName(policyName)
                .orElseThrow(() -> new RuntimeException("Policy not found: " + policyName));
        user.getPolicies().remove(policy);
        return toUserResponse(userRepository.save(user));
    }

    @Transactional
    public void updateInlinePolicy(String username, String document) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));
        user.setInlinePolicy(document);
        userRepository.save(user);
    }


    // ── Helpers ────────────────────────────────────────────────────────────────

    private UserResponse toUserResponse(User user) {
        return UserResponse.builder()
                .id(user.getId().toString())
                .username(user.getUsername())
                .email(user.getEmail())
                .accountId(user.getAccountId())
                .role(user.getRole() != null ? user.getRole().name() : "UNKNOWN")
                .rootUser(Boolean.TRUE.equals(user.getRootUser()))
                .inlinePolicy(user.getInlinePolicy())
                .policyNames(user.getPolicies().stream()
                        .map(Policy::getName)
                        .collect(Collectors.toList()))
                .createdAt(user.getCreatedAt() != null ? user.getCreatedAt().format(FMT) : "")
                .build();
    }

}
