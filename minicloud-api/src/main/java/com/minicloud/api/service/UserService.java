package com.minicloud.api.service;

import com.minicloud.api.domain.User;
import com.minicloud.api.domain.UserRepository;
import com.minicloud.api.domain.UserRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Service for managing IAM users.
 * All methods are transactional for proper database session management.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<User> getAllUsers() {
        log.info("[UserService] Loading all users from database");
        List<User> users = userRepository.findAll();
        log.info("[UserService] Found {} users", users.size());
        return users;
    }

    @Transactional(readOnly = true)
    public List<User> getUsersByAccountId(String accountId) {
        log.info("[UserService] Loading users for account: {}", accountId);
        return userRepository.findByAccountId(accountId);
    }

    @Transactional(readOnly = true)
    public User getUserById(UUID id) {
        log.info("[UserService] Loading user by ID: {}", id);
        return userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found: " + id));
    }

    @Transactional(readOnly = true)
    public User getUserByUsername(String username) {
        log.info("[UserService] Loading user by username: {}", username);
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));
    }

    @Transactional
    public User createUser(String username, String email, String passwordHash, 
                          String accountId, UserRole role, Boolean rootUser) {
        log.info("[UserService] Creating user: {} for account: {}", username, accountId);
        
        if (userRepository.findByUsername(username).isPresent()) {
            throw new RuntimeException("Username already exists: " + username);
        }

        if (userRepository.findByEmail(email).isPresent()) {
            throw new RuntimeException("Email already exists: " + email);
        }

        User user = User.builder()
                .username(username)
                .email(email)
                .passwordHash(passwordHash)
                .accountId(accountId)
                .role(role != null ? role : UserRole.USER)
                .rootUser(rootUser != null ? rootUser : false)
                .enabled(true)
                .build();

        User saved = userRepository.save(user);
        log.info("[UserService] User created successfully: {}", saved.getId());
        return saved;
    }

    @Transactional
    public User updateUser(UUID userId, String email, UserRole role, Boolean enabled) {
        log.info("[UserService] Updating user: {}", userId);
        
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        if (email != null && !email.equals(user.getEmail())) {
            if (userRepository.findByEmail(email).isPresent()) {
                throw new RuntimeException("Email already exists: " + email);
            }
            user.setEmail(email);
        }

        if (role != null) {
            user.setRole(role);
        }

        if (enabled != null) {
            user.setEnabled(enabled);
        }

        User updated = userRepository.save(user);
        log.info("[UserService] User updated successfully: {}", userId);
        return updated;
    }

    @Transactional
    public void deleteUser(UUID userId) {
        log.info("[UserService] Deleting user: {}", userId);
        
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        if (user.getRole() == UserRole.ADMIN) {
            long adminCount = userRepository.countByRole(UserRole.ADMIN);
            if (adminCount <= 1) {
                throw new RuntimeException("Cannot delete the last ADMIN user");
            }
        }

        userRepository.delete(user);
        log.info("[UserService] User deleted successfully: {}", userId);
    }
}
