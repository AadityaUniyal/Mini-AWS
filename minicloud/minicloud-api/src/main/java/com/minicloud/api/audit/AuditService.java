package com.minicloud.api.audit;

import com.minicloud.api.domain.AuditLog;
import com.minicloud.api.domain.AuditLogRepository;
import com.minicloud.api.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    @Lazy
    @Autowired
    private UserRepository userRepository;

    @Autowired
    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public void recordAction(String username, String service, String action, String resource, String status, String details) {
        try {
            // Resolve userId from username if possible
            UUID userId = null;
            if (username != null) {
                userId = userRepository.findByUsername(username)
                        .map(u -> u.getId())
                        .orElse(null);
            }

            AuditLog auditLog = AuditLog.builder()
                    .username(username)
                    .userId(userId)
                    .service(service)
                    .action(action)
                    .resource(resource)
                    .status(status)
                    .details(details)
                    .timestamp(LocalDateTime.now())
                    .build();
            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.warn("Failed to record audit log for user {}: {} - {}", username, action, e.getMessage());
        }
    }

    public void recordSuccess(String username, String service, String action, String resource) {
        recordAction(username, service, action, resource, "SUCCESS", null);
    }

    public void recordFailure(String username, String service, String action, String resource, String error) {
        recordAction(username, service, action, resource, "FAILURE", error);
    }

    public List<AuditLog> getLogsForUser(String username) {
        return auditLogRepository.findAllByUsername(username);
    }

    public List<AuditLog> getRecentLogs() {
        return auditLogRepository.findTop100ByOrderByTimestampDesc();
    }
}
