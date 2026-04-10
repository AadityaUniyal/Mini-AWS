package com.minicloud.api.iam;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccessKeyRepository extends JpaRepository<AccessKey, UUID> {
    List<AccessKey> findByUserId(UUID userId);
    Optional<AccessKey> findByKeyId(String keyId);
    List<AccessKey> findByUserIdAndActive(UUID userId, boolean active);
}
