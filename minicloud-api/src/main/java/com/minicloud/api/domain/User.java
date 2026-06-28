package com.minicloud.api.domain;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "iam_users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(nullable = false)
    private String passwordHash;

    @Column(unique = true)
    private String email;

    @Column(unique = true)
    private String accountId; // 12-digit AWS account ID

    private Boolean rootUser; // True for the account owner

    @Enumerated(EnumType.STRING)
    private UserRole role;

    private boolean enabled;

    @Column(columnDefinition = "TEXT")
    private String inlinePolicy; // Optional user-specific JSON policy document

    @Builder.Default
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "user_policies",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "policy_id")
    )
    private java.util.Set<Policy> policies = new java.util.HashSet<>();

    @CreatedDate
    private LocalDateTime createdAt;
}
