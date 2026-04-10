package com.minicloud.api.storage;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BucketRepository extends JpaRepository<Bucket, UUID> {
    List<Bucket> findAllByUserId(UUID userId);
    Optional<Bucket> findByUserIdAndName(UUID userId, String name);
    boolean existsByUserIdAndName(UUID userId, String name);
}
