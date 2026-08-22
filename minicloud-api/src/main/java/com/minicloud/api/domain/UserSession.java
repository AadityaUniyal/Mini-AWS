package com.minicloud.api.domain;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_sessions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class UserSession {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String username;

    @Column(length = 500)
    private String sessionToken;

    private String ipAddress;

    @Column(length = 500)
    private String userAgent;

    @CreatedDate
    private LocalDateTime loginTime;

    private LocalDateTime lastActivity;
    private LocalDateTime logoutTime;

    @Builder.Default
    private boolean isActive = true;

    @Builder.Default
    private String sessionType = "WEB";
}
