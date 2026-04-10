package com.minicloud.api.storage;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ObjectRepository extends JpaRepository<StorageObject, UUID> {
    List<StorageObject> findAllByBucketId(UUID bucketId);
    Optional<StorageObject> findByBucketIdAndObjectKey(UUID bucketId, String objectKey);
    void deleteAllByBucketId(UUID bucketId);
    long countByBucketId(UUID bucketId);
}
