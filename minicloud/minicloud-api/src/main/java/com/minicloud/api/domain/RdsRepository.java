package com.minicloud.api.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RdsRepository extends JpaRepository<RdsInstance, UUID> {
    Optional<RdsInstance> findByName(String name);
    Optional<RdsInstance> findByPort(int port);
    List<RdsInstance> findByUserId(UUID userId);
    List<RdsInstance> findByAccountId(String accountId);
}
