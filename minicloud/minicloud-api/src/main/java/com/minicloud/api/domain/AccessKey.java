package com.minicloud.api.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "iam_access_keys")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccessKey {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true, nullable = false)
    private String keyId;
    
    @Column(nullable = false)
    private String secretKeyHash;
    
    private boolean active;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    private LocalDateTime createdAt;
    private LocalDateTime lastUsedAt;
}
