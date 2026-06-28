package com.minicloud.api.service;

import com.minicloud.api.domain.AuditLog;
import com.minicloud.api.domain.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Service for managing CloudTrail-like audit logs.
 * All methods are transactional for proper database session management.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    @Transactional(readOnly = true)
    public List<AuditLog> getAllLogs() {
        log.info("[AuditLogService] Loading all audit logs from database");
        List<AuditLog> logs = auditLogRepository.findTop100ByOrderByTimestampDesc();
        log.info("[AuditLogService] Found {} audit logs", logs.size());
        return logs;
    }

    @Transactional(readOnly = true)
    public List<AuditLog> getLogsByUserId(UUID userId) {
        log.info("[AuditLogService] Loading audit logs for user: {}", userId);
        return auditLogRepository.findAllByUserIdOrderByTimestampDesc(userId);
    }

    @Transactional(readOnly = true)
    public List<AuditLog> getLogsByUsername(String username) {
        log.info("[AuditLogService] Loading audit logs for username: {}", username);
        return auditLogRepository.findAllByUsername(username);
    }

    @Transactional(readOnly = true)
    public AuditLog getLogById(UUID id) {
        log.info("[AuditLogService] Loading audit log by ID: {}", id);
        return auditLogRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Audit log not found: " + id));
    }
}
