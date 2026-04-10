package com.minicloud.api.storage;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "mc_objects")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class StorageObject {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @EqualsAndHashCode.Include
    private UUID id;

    @Column(name = "bucket_id", nullable = false)
    private UUID bucketId;

    @Column(name = "object_key", nullable = false, length = 500)
    private String objectKey;

    @Column(name = "size_bytes")
    private long sizeBytes;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Column(name = "local_path", length = 1000)
    private String localPath;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "mc_object_metadata", joinColumns = @JoinColumn(name = "object_id"))
    @MapKeyColumn(name = "meta_key")
    @Column(name = "meta_value")
    @Builder.Default
    private java.util.Map<String, String> metadata = new java.util.HashMap<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
