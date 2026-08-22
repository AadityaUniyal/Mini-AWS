package com.minicloud.api.monitoring;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Real-Time Database Service
 * Handles live updates, metrics collection, and real-time monitoring.
 * Fully portable ANSI SQL compatible with H2 and PostgreSQL.
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
    @Scheduled(fixedRate = 60000)
    @Transactional
    public void recordSystemMetrics() {
        try {
            var metrics = metricsService.getSystemMetrics();
            
            String sql = """
                INSERT INTO system_metrics (
                    id, cpu_usage_percent, memory_used_mb, memory_total_mb,
                    disk_used_gb, disk_total_gb, active_threads, heap_used_mb,
                    heap_max_mb, uptime_seconds, request_count, error_count, timestamp
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
            
            jdbcTemplate.update(sql,
                UUID.randomUUID().toString(),
                metrics.getCpuLoad(),
                metrics.getUsedHeapMb(),
                metrics.getUsedHeapMb() * 2,
                metrics.getDiskUsedGb(),
                metrics.getDiskUsedGb() * 2,
                metrics.getActiveThreads(),
                metrics.getUsedHeapMb(),
                metrics.getUsedHeapMb() * 2,
                metrics.getUptimeSeconds(),
                0,
                0,
                LocalDateTime.now()
            );
            
            log.debug("System metrics recorded: CPU={}%, Memory={}MB", 
                metrics.getCpuLoad(), metrics.getUsedHeapMb());
                
        } catch (Exception e) {
            log.error("Failed to record system metrics", e);
        }
    }

    /**
     * Update dashboard metrics for all accounts every 5 minutes.
     */
    @Scheduled(fixedRate = 300000)
    @Transactional
    public void updateDashboardMetrics() {
        try {
            List<String> accountIds = jdbcTemplate.queryForList(
                "SELECT DISTINCT account_id FROM compute_instances WHERE account_id IS NOT NULL",
                String.class
            );

            LocalDate today = LocalDate.now();

            for (String accountId : accountIds) {
                Integer countInstances = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM compute_instances WHERE account_id = ?", Integer.class, accountId);
                Integer runningInstances = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM compute_instances WHERE account_id = ? AND state = 'RUNNING'", Integer.class, accountId);
                Integer totalBuckets = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM s3_buckets WHERE account_id = ?", Integer.class, accountId);
                Long totalObjects = jdbcTemplate.queryForObject(
                    "SELECT COALESCE(SUM(object_count), 0) FROM s3_buckets WHERE account_id = ?", Long.class, accountId);
                Double storageUsedGb = jdbcTemplate.queryForObject(
                    "SELECT COALESCE(SUM(total_size_bytes) / 1073741824.0, 0.0) FROM s3_buckets WHERE account_id = ?", Double.class, accountId);
                Integer totalFunctions = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM lambda_functions WHERE account_id = ?", Integer.class, accountId);
                Long lambdaInvocations = jdbcTemplate.queryForObject(
                    "SELECT COALESCE(SUM(invocation_count), 0) FROM lambda_functions WHERE account_id = ?", Long.class, accountId);
                Integer totalRds = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM rds_instances WHERE account_id = ?", Integer.class, accountId);
                Double dailyCost = jdbcTemplate.queryForObject(
                    "SELECT COALESCE(SUM(total_cost), 0.0) FROM billing_records WHERE account_id = ? AND created_at >= ?", 
                    Double.class, accountId, today.atStartOfDay());

                // Portable upsert: delete existing for today if present, then insert
                jdbcTemplate.update(
                    "DELETE FROM dashboard_metrics WHERE account_id = ? AND metric_date = ?",
                    accountId, today
                );

                String insertSql = """
                    INSERT INTO dashboard_metrics (
                        id, account_id, metric_date, total_instances, running_instances,
                        total_buckets, total_objects, storage_used_gb, lambda_functions,
                        lambda_invocations, rds_instances, daily_cost, last_updated
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """;

                jdbcTemplate.update(insertSql,
                    UUID.randomUUID().toString(), accountId, today,
                    countInstances != null ? countInstances : 0,
                    runningInstances != null ? runningInstances : 0,
                    totalBuckets != null ? totalBuckets : 0,
                    totalObjects != null ? totalObjects : 0,
                    storageUsedGb != null ? storageUsedGb : 0.0,
                    totalFunctions != null ? totalFunctions : 0,
                    lambdaInvocations != null ? lambdaInvocations : 0,
                    totalRds != null ? totalRds : 0,
                    dailyCost != null ? dailyCost : 0.0,
                    LocalDateTime.now()
                );
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
            jdbcTemplate.update(
                "UPDATE iam_users SET last_login = CURRENT_TIMESTAMP, login_count = COALESCE(login_count, 0) + 1, last_ip = ? WHERE id = ?",
                ipAddress, userId
            );
            
            String sql = """
                INSERT INTO user_sessions (
                    id, user_id, username, session_token, ip_address, 
                    user_agent, session_type, is_active, login_time, last_activity
                ) VALUES (?, ?, ?, ?, ?, ?, ?, TRUE, ?, ?)
                """;
            
            LocalDateTime now = LocalDateTime.now();
            jdbcTemplate.update(sql,
                UUID.randomUUID().toString(),
                userId, username, sessionToken, ipAddress, userAgent, sessionType, now, now
            );
            
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
                    response_time_ms, ip_address, user_agent, timestamp
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
            
            jdbcTemplate.update(sql,
                UUID.randomUUID().toString(),
                userId, username, method, endpoint, statusCode,
                responseTimeMs, ipAddress, userAgent, LocalDateTime.now()
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
                    resource_type, resource_id, event_data, severity, timestamp
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
            
            jdbcTemplate.update(sql,
                UUID.randomUUID().toString(),
                eventType, sourceService, userId, accountId,
                resourceType, resourceId, eventData, severity, LocalDateTime.now()
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
                    usage_type, usage_value, total_cost, period_start, period_end, recorded_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
            
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime periodStart = now.withMinute(0).withSecond(0).withNano(0);
            LocalDateTime periodEnd = periodStart.plusHours(1);
            
            jdbcTemplate.update(sql,
                UUID.randomUUID().toString(),
                accountId, resourceType, resourceId, resourceName,
                usageType, usageValue, cost, periodStart, periodEnd, now
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
                    id, user_id, type, title, message, resource_type, resource_id, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
            
            jdbcTemplate.update(sql,
                UUID.randomUUID().toString(),
                userId, type, title, message, resourceType, resourceId, LocalDateTime.now()
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
            LocalDateTime cutoff = LocalDateTime.now().minusMinutes(5);
            List<Map<String, Object>> results = jdbcTemplate.queryForList(
                "SELECT * FROM system_metrics WHERE timestamp > ? ORDER BY timestamp DESC",
                cutoff
            );
            if (!results.isEmpty()) {
                Map<String, Object> row = results.get(0);
                double cpu = row.get("cpu_usage_percent") instanceof Number n ? n.doubleValue() : 0.0;
                String status = cpu > 90 ? "CRITICAL" : (cpu > 70 ? "WARNING" : "HEALTHY");
                row.put("component", "SYSTEM");
                row.put("status", status);
                return row;
            }
            return Map.of("component", "SYSTEM", "status", "HEALTHY", "cpu_usage_percent", 0.0);
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
            return jdbcTemplate.queryForList("""
                SELECT u.username, u.email, u.last_login, s.session_type, s.last_activity, s.is_active
                FROM iam_users u
                LEFT JOIN user_sessions s ON u.id = s.user_id AND s.is_active = TRUE
                ORDER BY s.last_activity DESC
                """);
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
            Integer totalInst = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM compute_instances WHERE account_id = ? AND state != 'TERMINATED'", Integer.class, accountId);
            Integer runningInst = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM compute_instances WHERE account_id = ? AND state = 'RUNNING'", Integer.class, accountId);
            Integer totalBuckets = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM s3_buckets WHERE account_id = ?", Integer.class, accountId);
            Integer totalFunctions = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM lambda_functions WHERE account_id = ?", Integer.class, accountId);
            Integer totalRds = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM rds_instances WHERE account_id = ?", Integer.class, accountId);

            return Map.of(
                "accountId", accountId,
                "totalInstances", totalInst != null ? totalInst : 0,
                "runningInstances", runningInst != null ? runningInst : 0,
                "totalBuckets", totalBuckets != null ? totalBuckets : 0,
                "totalFunctions", totalFunctions != null ? totalFunctions : 0,
                "totalRds", totalRds != null ? totalRds : 0
            );
        } catch (Exception e) {
            log.error("Failed to get live resource summary for account: {}", accountId, e);
            return Map.of("accountId", accountId);
        }
    }

    /**
     * Get live cost summary for account
     */
    public Map<String, Object> getLiveCostSummary(String accountId) {
        try {
            LocalDate today = LocalDate.now();
            LocalDateTime startOfDay = today.atStartOfDay();
            LocalDateTime startOfMonth = today.withDayOfMonth(1).atStartOfDay();

            Double todayCost = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(total_cost), 0.0) FROM billing_records WHERE account_id = ? AND created_at >= ?",
                Double.class, accountId, startOfDay);
            Double monthCost = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(total_cost), 0.0) FROM billing_records WHERE account_id = ? AND created_at >= ?",
                Double.class, accountId, startOfMonth);
            Long totalRecords = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM billing_records WHERE account_id = ?",
                Long.class, accountId);

            return Map.of(
                "accountId", accountId,
                "todayCost", todayCost != null ? todayCost : 0.0,
                "monthCost", monthCost != null ? monthCost : 0.0,
                "totalBillingRecords", totalRecords != null ? totalRecords : 0L
            );
        } catch (Exception e) {
            log.error("Failed to get live cost summary for account: {}", accountId, e);
            return Map.of("accountId", accountId);
        }
    }

    /**
     * Cleanup old data to keep database performant
     */
    @Scheduled(fixedRate = 3600000)
    @Transactional
    public void cleanupOldData() {
        try {
            LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
            LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
            LocalDateTime ninetyDaysAgo = LocalDateTime.now().minusDays(90);
            LocalDateTime oneDayAgo = LocalDateTime.now().minusHours(24);

            jdbcTemplate.update("DELETE FROM system_metrics WHERE timestamp < ?", sevenDaysAgo);
            jdbcTemplate.update("DELETE FROM api_requests WHERE timestamp < ?", thirtyDaysAgo);
            jdbcTemplate.update("DELETE FROM event_stream WHERE timestamp < ?", ninetyDaysAgo);
            jdbcTemplate.update("UPDATE user_sessions SET is_active = FALSE WHERE last_activity < ?", oneDayAgo);
            
            log.debug("Database cleanup completed");
        } catch (Exception e) {
            log.error("Failed to cleanup old data", e);
        }
    }
}