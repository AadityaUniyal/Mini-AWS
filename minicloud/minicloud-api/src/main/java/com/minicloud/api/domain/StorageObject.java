package com.minicloud.api.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "storage_objects")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StorageObject {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID bucketId;
    
    @Column(name = "object_key")
    private String objectKey;
    
    private String contentType;
    private long sizeBytes;
    private String etag;
    private String localPath;

    @Lob
    private byte[] content;

    @ElementCollection
    @CollectionTable(name = "storage_object_metadata", joinColumns = @JoinColumn(name = "object_id"))
    @MapKeyColumn(name = "meta_key")
    @Column(name = "meta_value")
    private java.util.Map<String, String> metadata;
    
    private LocalDateTime lastModified;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
