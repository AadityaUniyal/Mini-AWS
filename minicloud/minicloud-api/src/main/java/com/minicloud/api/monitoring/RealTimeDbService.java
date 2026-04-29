package com.minicloud.api.monitoring;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Real-Time Database Service
 * Handles live updates, metrics collection, and real-time monitoring
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RealTimeDbService {

    private final JdbcTemplate jdbcTemplate;
    private final MetricsService metricsService;

    /**
     * Record system metrics every minute for real-time monitoring
     */
    @Scheduled(fixedRate = 60000) // Every 1 minute
    @Transactional
    public void recordSystemMetrics() {
        try {
            var metrics = metricsService.getSystemMetrics();
            
            String sql = """
                INSERT INTO system_metrics (
                    id, cpu_usage_percent, memory_used_mb, memory_total_mb,
                    disk_used_gb, disk_total_gb, active_threads, heap_used_mb,
                    heap_max_mb, uptime_seconds, request_count
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
            
            jdbcTemplate.update(sql,
                UUID.randomUUID().toString(),
                metrics.getCpuLoad(),
                metrics.getUsedHeapMb(),
                metrics.getUsedHeapMb() * 2, // Estimate total heap as 2x used
                metrics.getDiskUsedGb(),
                metrics.getDiskUsedGb() * 2, // Estimate total disk as 2x used
                metrics.getActiveThreads(),
                metrics.getUsedHeapMb(),
                metrics.getUsedHeapMb() * 2, // Estimate max heap
                metrics.getUptimeSeconds(),
                0 // Will be updated by API request tracking
            );
            
            log.debug("System metrics recorded: CPU={}%, Memory={}MB", 
                metrics.getCpuLoad(), metrics.getUsedHeapMb());
                
        } catch (Exception e) {
            log.error("Failed to record system metrics", e);
        }
    }

    /**
     * Update dashboard metrics for all accounts every 5 minutes.
     * Each account is processed independently with its own filtered SQL query.
     */
    @Scheduled(fixedRate = 300000) // Every 5 minutes
    @Transactional
    public void updateDashboardMetrics() {
        try {
            List<String> accountIds = jdbcTemplate.queryForList(
                "SELECT DISTINCT account_id FROM compute_instances WHERE account_id IS NOT NULL",
                String.class
            );

            for (String accountId : accountIds) {
                String sql = """
                    MERGE INTO dashboard_metrics (id, account_id, metric_date,
                        total_instances, running_instances, total_buckets,
                        total_objects, storage_used_gb, lambda_functions,
                        lambda_invocations, rds_instances, daily_cost)
                    KEY(account_id, metric_date)
                    SELECT
                        ?, ?, CURRENT_DATE,
                        COUNT(ci.id),
                        COUNT(CASE WHEN ci.state = 'RUNNING' THEN 1 END),
                        COALESCE((SELECT COUNT(*) FROM iam_buckets WHERE account_id = ?), 0),
                        COALESCE((SELECT SUM(object_count) FROM iam_buckets WHERE account_id = ?), 0),
                        COALESCE((SELECT SUM(total_size_bytes)/1073741824.0 FROM iam_buckets WHERE account_id = ?), 0),
                        COALESCE((SELECT COUNT(*) FROM lambda_functions WHERE account_id = ?), 0),
                        COALESCE((SELECT SUM(invocation_count) FROM lambda_functions WHERE account_id = ?), 0),
                        COALESCE((SELECT COUNT(*) FROM rds_instances WHERE account_id = ?), 0),
                        COALESCE((SELECT SUM(total_cost) FROM billing_records
                                  WHERE account_id = ? AND CAST(created_at AS DATE) = CURRENT_DATE), 0)
                    FROM compute_instances ci WHERE ci.account_id = ?
                    """;
                jdbcTemplate.update(sql,
                    UUID.randomUUID().toString(), accountId,
                    accountId, accountId, accountId,
                    accountId, accountId, accountId,
                    accountId, accountId);
            }

            log.debug("Dashboard metrics updated for {} accounts", accountIds.size());

        } catch (Exception e) {
            log.error("Failed to update dashboard metrics", e);
        }
    }

    /**
     * Record user login event
     */
    @Transactional
    public void recordUserLogin(String userId, String username, String sessionToken, 
                               String ipAddress, String userAgent, String sessionType) {
        try {
            // Update user last login
            jdbcTemplate.update(
                "UPDATE iam_users SET last_login = CURRENT_TIMESTAMP, login_count = login_count + 1, last_ip = ? WHERE id = ?",
                ipAddress, userId
            );
            
            // Create session record
            String sql = """
                INSERT INTO user_sessions (
                    id, user_id, username, session_token, ip_address, 
                    user_agent, session_type, is_active
                ) VALUES (?, ?, ?, ?, ?, ?, ?, TRUE)
                """;
            
            jdbcTemplate.update(sql,
                UUID.randomUUID().toString(),
                userId, username, sessionToken, ipAddress, userAgent, sessionType
            );
            
            // Record event
            recordEvent("USER_LOGIN", "IAM", userId, null, null, null, 
                String.format("User %s logged in from %s", username, ipAddress), "INFO");
            
            log.info("User login recorded: {} from {}", username, ipAddress);
            
        } catch (Exception e) {
            log.error("Failed to record user login", e);
        }
    }

    /**
     * Record user logout event
     */
    @Transactional
    public void recordUserLogout(String userId, String sessionToken) {
        try {
            jdbcTemplate.update(
                "UPDATE user_sessions SET logout_time = CURRENT_TIMESTAMP, is_active = FALSE WHERE user_id = ? AND session_token = ?",
                userId, sessionToken
            );
            
            log.debug("User logout recorded for user: {}", userId);
            
        } catch (Exception e) {
            log.error("Failed to record user logout", e);
        }
    }

    /**
     * Record API request for real-time monitoring
     */
    @Transactional
    public void recordApiRequest(String userId, String username, String method, String endpoint,
                                int statusCode, long responseTimeMs, String ipAddress, String userAgent) {
        try {
            String sql = """
                INSERT INTO api_requests (
                    id, user_id, username, method, endpoint, status_code,
                    response_time_ms, ip_address, user_agent
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
            
            jdbcTemplate.update(sql,
                UUID.randomUUID().toString(),
                userId, username, method, endpoint, statusCode,
                responseTimeMs, ipAddress, userAgent
            );
            
        } catch (Exception e) {
            log.error("Failed to record API request", e);
        }
    }

    /**
     * Record real-time event in event stream
     */
    @Transactional
    public void recordEvent(String eventType, String sourceService, String userId, 
                           String accountId, String resourceType, String resourceId, 
                           String eventData, String severity) {
        try {
            String sql = """
                INSERT INTO event_stream (
                    id, event_type, source_service, user_id, account_id,
                    resource_type, resource_id, event_data, severity
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
            
            jdbcTemplate.update(sql,
                UUID.randomUUID().toString(),
                eventType, sourceService, userId, accountId,
                resourceType, resourceId, eventData, severity
            );
            
        } catch (Exception e) {
            log.error("Failed to record event", e);
        }
    }

    /**
     * Update resource usage in real-time
     */
    @Transactional
    public void updateResourceUsage(String accountId, String resourceType, String resourceId,
                                   String resourceName, String usageType, double usageValue, double cost) {
        try {
            String sql = """
                INSERT INTO resource_usage (
                    id, account_id, resource_type, resource_id, resource_name,
                    usage_type, usage_value, total_cost, period_start, period_end
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
            
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime periodStart = now.withMinute(0).withSecond(0).withNano(0);
            LocalDateTime periodEnd = periodStart.plusHours(1);
            
            jdbcTemplate.update(sql,
                UUID.randomUUID().toString(),
                accountId, resourceType, resourceId, resourceName,
                usageType, usageValue, cost, periodStart, periodEnd
            );
            
        } catch (Exception e) {
            log.error("Failed to update resource usage", e);
        }
    }

    /**
     * Create notification for user
     */
    @Transactional
    public void createNotification(String userId, String type, String title, String message,
                                  String resourceType, String resourceId) {
        try {
            String sql = """
                INSERT INTO notifications (
                    id, user_id, type, title, message, resource_type, resource_id
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
            
            jdbcTemplate.update(sql,
                UUID.randomUUID().toString(),
                userId, type, title, message, resourceType, resourceId
            );
            
            log.debug("Notification created for user {}: {}", userId, title);
            
        } catch (Exception e) {
            log.error("Failed to create notification", e);
        }
    }

    /**
     * Get live system status
     */
    public Map<String, Object> getLiveSystemStatus() {
        try {
            return jdbcTemplate.queryForMap("SELECT * FROM live_system_status");
        } catch (Exception e) {
            log.error("Failed to get live system status", e);
            return Map.of("status", "ERROR", "message", "Unable to fetch system status");
        }
    }

    /**
     * Get live user activity
     */
    public List<Map<String, Object>> getLiveUserActivity() {
        try {
            return jdbcTemplate.queryForList("SELECT * FROM live_user_activity LIMIT 20");
        } catch (Exception e) {
            log.error("Failed to get live user activity", e);
            return List.of();
        }
    }

    /**
     * Get live resource summary for account
     */
    public Map<String, Object> getLiveResourceSummary(String accountId) {
        try {
            return jdbcTemplate.queryForMap(
                "SELECT * FROM live_resource_summary WHERE account_id = ?", 
                accountId
            );
        } catch (Exception e) {
            log.error("Failed to get live resource summary for account: {}", accountId, e);
            return Map.of();
        }
    }

    /**
     * Get live cost summary for account
     */
    public Map<String, Object> getLiveCostSummary(String accountId) {
        try {
            return jdbcTemplate.queryForMap(
                "SELECT * FROM live_cost_summary WHERE account_id = ?", 
                accountId
            );
        } catch (Exception e) {
            log.error("Failed to get live cost summary for account: {}", accountId, e);
            return Map.of();
        }
    }

    /**
     * Cleanup old data to keep database performant
     */
    @Scheduled(fixedRate = 3600000) // Every 1 hour
    @Transactional
    public void cleanupOldData() {
        try {
            // Keep only last 7 days of system metrics
            jdbcTemplate.update(
                "DELETE FROM system_metrics WHERE timestamp < DATEADD('DAY', -7, CURRENT_TIMESTAMP)"
            );
            
            // Keep only last 30 days of API requests
            jdbcTemplate.update(
                "DELETE FROM api_requests WHERE timestamp < DATEADD('DAY', -30, CURRENT_TIMESTAMP)"
            );
            
            // Keep only last 90 days of event stream
            jdbcTemplate.update(
                "DELETE FROM event_stream WHERE timestamp < DATEADD('DAY', -90, CURRENT_TIMESTAMP)"
            );
            
            // Mark old sessions as inactive
            jdbcTemplate.update(
                "UPDATE user_sessions SET is_active = FALSE WHERE last_activity < DATEADD('HOUR', -24, CURRENT_TIMESTAMP)"
            );
            
            log.debug("Database cleanup completed");
            
        } catch (Exception e) {
            log.error("Failed to cleanup old data", e);
        }
    }
}