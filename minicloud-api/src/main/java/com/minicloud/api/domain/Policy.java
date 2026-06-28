package com.minicloud.api.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "iam_policies")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Policy {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true, nullable = false)
    private String name;

    private String description;

    @Column(columnDefinition = "TEXT")
    private String document; // Raw AWS-style JSON Policy Document

    private UUID userId; // Owner

    public String getDocument() {
        return document;
    }
 
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    private boolean managed;

}
