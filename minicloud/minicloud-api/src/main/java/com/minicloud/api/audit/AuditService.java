package com.minicloud.api.audit;

import com.minicloud.api.domain.AuditLog;
import com.minicloud.api.domain.AuditLogRepository;
import com.minicloud.api.domain.UserRepository;
import com.minicloud.api.monitoring.RealTimeDbService;
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
    
    @Lazy
    @Autowired
    private RealTimeDbService realTimeDbService;

    @Autowired
    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public void recordAction(String username, String service, String action, String resource, String status, String details) {
        try {
            // Resolve userId from username if possible
            UUID userId = null;
            String accountId = null;
            if (username != null) {
                var user = userRepository.findByUsername(username).orElse(null);
                if (user != null) {
                    userId = user.getId();
                    accountId = user.getAccountId();
                }
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
            
            // Record in real-time event stream
            if (realTimeDbService != null) {
                String eventType = status.equals("SUCCESS") ? "ACTION_SUCCESS" : "ACTION_FAILURE";
                String severity = status.equals("SUCCESS") ? "INFO" : "WARNING";
                String eventData = String.format("{\"service\":\"%s\",\"action\":\"%s\",\"resource\":\"%s\",\"details\":\"%s\"}", 
                    service, action, resource != null ? resource : "", details != null ? details : "");
                
                realTimeDbService.recordEvent(eventType, service, 
                    userId != null ? userId.toString() : null, accountId, 
                    service, resource, eventData, severity);
            }
            
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
