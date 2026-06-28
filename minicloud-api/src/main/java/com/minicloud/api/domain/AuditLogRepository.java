package com.minicloud.api.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
    List<AuditLog> findAllByUserIdOrderByTimestampDesc(UUID userId);
    List<AuditLog> findAllByUsername(String username);
    List<AuditLog> findTop100ByOrderByTimestampDesc();
}
