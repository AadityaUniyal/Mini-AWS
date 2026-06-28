package com.minicloud.api.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccessKeyRepository extends JpaRepository<AccessKey, UUID> {
    Optional<AccessKey> findByKeyId(String keyId);
    List<AccessKey> findByUser_Id(UUID userId);
    void deleteByUser_Id(UUID userId);
}
