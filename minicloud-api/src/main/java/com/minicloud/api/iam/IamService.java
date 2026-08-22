package com.minicloud.api.iam;

import com.minicloud.api.auth.SecurityUtils;
import com.minicloud.api.auth.UserPrincipal;
import com.minicloud.api.domain.*;
import com.minicloud.api.dto.AccessKeyResponse;
import com.minicloud.api.dto.CreateIamUserRequest;
import com.minicloud.api.dto.PolicyResponse;
import com.minicloud.api.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class IamService {

    private final UserRepository userRepository;
    private final AccessKeyRepository accessKeyRepository;
    private final PolicyRepository policyRepository;
    private final PasswordEncoder passwordEncoder;
    private final com.minicloud.api.audit.AuditService auditService;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    public List<UserResponse> listUsersForAccount(String accountId) {
        return userRepository.findByAccountId(accountId).stream()
                .map(this::toUserResponse)
                .collect(Collectors.toList());
    }

    public List<UserResponse> listAllUsers() {
        UserPrincipal principal = SecurityUtils.getAuthenticatedPrincipal();
        if (principal.getAccountId() != null) {
            return listUsersForAccount(principal.getAccountId());
        }
        return userRepository.findAll().stream()
                .map(this::toUserResponse)
                .collect(Collectors.toList());
    }

    public UserResponse getUserById(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found: " + id));
        SecurityUtils.validateAccountOwnership(user.getAccountId());
        return toUserResponse(user);
    }

    public UserResponse getUserByUsername(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));
        SecurityUtils.validateAccountOwnership(user.getAccountId());
        return toUserResponse(user);
    }

    @Transactional
    public UserResponse createIamUser(CreateIamUserRequest request) {
        UserPrincipal principal = SecurityUtils.getAuthenticatedPrincipal();
        String accountId = principal.getAccountId();
        if (accountId == null || accountId.isBlank()) {
            throw new IllegalStateException("Cannot create IAM user: No active account context");
        }

        if (userRepository.existsByAccountIdAndUsername(accountId, request.getUsername())) {
            throw new IllegalArgumentException("IAM user with username '" + request.getUsername() + "' already exists in this account");
        }

        UserRole role = UserRole.USER;
        if (request.getRole() != null) {
            try {
                role = UserRole.valueOf(request.getRole().toUpperCase());
            } catch (Exception ignored) {
                role = UserRole.USER;
            }
        }

        Set<Policy> attachedPolicies = new HashSet<>();
        if (request.getPolicies() != null) {
            for (String policyName : request.getPolicies()) {
                policyRepository.findByName(policyName).ifPresent(attachedPolicies::add);
            }
        }

        User iamUser = User.builder()
                .username(request.getUsername())
                .accountId(accountId)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(role)
                .rootUser(Boolean.FALSE)
                .enabled(true)
                .policies(attachedPolicies)
                .createdAt(LocalDateTime.now())
                .build();

        User saved = userRepository.save(iamUser);
        auditService.recordSuccess(principal.getUsername(), "IAM", "CreateUser", saved.getUsername());
        return toUserResponse(saved);
    }

    @Transactional
    public void deleteUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));
        SecurityUtils.validateAccountOwnership(user.getAccountId());

        if (Boolean.TRUE.equals(user.getRootUser())) {
            throw new IllegalArgumentException("Cannot delete the root account owner.");
        }

        if (user.getRole() == UserRole.ADMIN) {
            long adminCount = userRepository.countByAccountIdAndRole(user.getAccountId(), UserRole.ADMIN);
            if (adminCount <= 1) {
                throw new IllegalArgumentException("Cannot delete the last ADMIN user in the account.");
            }
        }

        accessKeyRepository.deleteByUser_Id(userId);
        userRepository.delete(user);
        auditService.recordSuccess(SecurityUtils.getAuthenticatedUsername(), "IAM", "DeleteUser", user.getUsername());
    }

    public AccessKeyResponse generateAccessKey(String targetUsername) {
        User user = userRepository.findByUsername(targetUsername)
                .orElseThrow(() -> new RuntimeException("User not found: " + targetUsername));
        
        UserPrincipal principal = SecurityUtils.getAuthenticatedPrincipal();
        if (!user.getUsername().equals(principal.getUsername()) && !SecurityUtils.isRootOrAdmin()) {
            throw new AccessDeniedException("Cannot generate access key for another user");
        }
        SecurityUtils.validateAccountOwnership(user.getAccountId());

        String rawKeyId = "MCAK-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
        String rawSecret = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");

        AccessKey accessKey = AccessKey.builder()
                .user(user)
                .keyId(rawKeyId)
                .secretKeyHash(passwordEncoder.encode(rawSecret))
                .active(true)
                .build();

        AccessKey saved = accessKeyRepository.save(accessKey);
        auditService.recordSuccess(principal.getUsername(), "IAM", "CreateAccessKey", targetUsername);

        return AccessKeyResponse.builder()
                .id(saved.getId().toString())
                .keyId(rawKeyId)
                .secretKey(rawSecret)
                .active(true)
                .createdAt(saved.getCreatedAt() != null ? saved.getCreatedAt().format(FMT) : "")
                .build();
    }

    public List<AccessKeyResponse> listAccessKeys(String targetUsername) {
        User user = userRepository.findByUsername(targetUsername)
                .orElseThrow(() -> new RuntimeException("User not found: " + targetUsername));
        
        UserPrincipal principal = SecurityUtils.getAuthenticatedPrincipal();
        if (!user.getUsername().equals(principal.getUsername()) && !SecurityUtils.isRootOrAdmin()) {
            throw new AccessDeniedException("Cannot list access keys for another user");
        }
        SecurityUtils.validateAccountOwnership(user.getAccountId());

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

        UserPrincipal principal = SecurityUtils.getAuthenticatedPrincipal();
        if (!key.getUser().getId().equals(user.getId()) && !SecurityUtils.isRootOrAdmin()) {
            throw new AccessDeniedException("Access denied to revoke key");
        }

        key.setActive(false);
        accessKeyRepository.save(key);
        auditService.recordSuccess(principal.getUsername(), "IAM", "DeleteAccessKey", key.getKeyId());
    }

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
        SecurityUtils.validateAccountOwnership(user.getAccountId());
        Policy policy = policyRepository.findByName(policyName)
                .orElseThrow(() -> new RuntimeException("Policy not found: " + policyName));
        user.getPolicies().add(policy);
        return toUserResponse(userRepository.save(user));
    }

    @Transactional
    public UserResponse detachPolicy(UUID userId, String policyName) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));
        SecurityUtils.validateAccountOwnership(user.getAccountId());
        Policy policy = policyRepository.findByName(policyName)
                .orElseThrow(() -> new RuntimeException("Policy not found: " + policyName));
        user.getPolicies().remove(policy);
        return toUserResponse(userRepository.save(user));
    }

    @Transactional
    public void updateInlinePolicy(String username, String document) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));
        SecurityUtils.validateAccountOwnership(user.getAccountId());
        user.setInlinePolicy(document);
        userRepository.save(user);
    }

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