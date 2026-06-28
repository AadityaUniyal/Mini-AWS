package com.minicloud.api.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BucketRepository extends JpaRepository<Bucket, UUID> {
    Optional<Bucket> findByName(String name);
    Optional<Bucket> findByUserIdAndName(UUID userId, String name);
    Optional<Bucket> findByAccountIdAndName(String accountId, String name);
    List<Bucket> findByUserId(UUID userId);
    List<Bucket> findByAccountId(String accountId);
    boolean existsByName(String name);
    boolean existsByUserIdAndName(UUID userId, String name);
    boolean existsByAccountIdAndName(String accountId, String name);
}
