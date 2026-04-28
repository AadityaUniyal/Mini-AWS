-- ============================================================
--  V3__enhanced_realtime_features.sql
--  Enhanced Real-Time Database Features for MiniCloud
--  Live tracking, metrics, and comprehensive audit system
-- ============================================================

-- ── Real-Time System Metrics ─────────────────────────────────
CREATE TABLE IF NOT EXISTS system_metrics (
    id                VARCHAR(36)  NOT NULL PRIMARY KEY,
    timestamp         TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    cpu_usage_percent DOUBLE       DEFAULT 0,
    memory_used_mb    BIGINT       DEFAULT 0,
    memory_total_mb   BIGINT       DEFAULT 0,
    disk_used_gb      DOUBLE       DEFAULT 0,
    disk_total_gb     DOUBLE       DEFAULT 0,
    active_threads    INTEGER      DEFAULT 0,
    heap_used_mb      DOUBLE       DEFAULT 0,
    heap_max_mb       DOUBLE       DEFAULT 0,
    uptime_seconds    BIGINT       DEFAULT 0,
    request_count     BIGINT       DEFAULT 0,
    error_count       BIGINT       DEFAULT 0
);

-- ── Live User Sessions ───────────────────────────────────────
CREATE TABLE IF NOT EXISTS user_sessions (
    id              VARCHAR(36)  NOT NULL PRIMARY KEY,
    user_id         VARCHAR(36)  NOT NULL,
    username        VARCHAR(100) NOT NULL,
    session_token   VARCHAR(500),
    ip_address      VARCHAR(50),
    user_agent      VARCHAR(500),
    login_time      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    last_activity   TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    logout_time     TIMESTAMP,
    is_active       BOOLEAN      DEFAULT TRUE,
    session_type    VARCHAR(20)  DEFAULT 'WEB', -- WEB, DESKTOP, API
    FOREIGN KEY (user_id) REFERENCES iam_users(id) ON DELETE CASCADE
);

-- ── Resource Usage Tracking ──────────────────────────────────
CREATE TABLE IF NOT EXISTS resource_usage (
    id              VARCHAR(36)  NOT NULL PRIMARY KEY,
    account_id      VARCHAR(20)  NOT NULL,
    resource_type   VARCHAR(50)  NOT NULL, -- EC2, S3, RDS, LAMBDA
    resource_id     VARCHAR(36)  NOT NULL,
    resource_name   VARCHAR(255),
    usage_type      VARCHAR(50)  NOT NULL, -- COMPUTE_HOURS, STORAGE_GB, REQUESTS
    usage_value     DOUBLE       DEFAULT 0,
    cost_per_unit   DOUBLE       DEFAULT 0,
    total_cost      DOUBLE       DEFAULT 0,
    period_start    TIMESTAMP    NOT NULL,
    period_end      TIMESTAMP    NOT NULL,
    recorded_at     TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

-- ── Live API Request Tracking ────────────────────────────────
CREATE TABLE IF NOT EXISTS api_requests (
    id              VARCHAR(36)  NOT NULL PRIMARY KEY,
    user_id         VARCHAR(36),
    username        VARCHAR(100),
    method          VARCHAR(10)  NOT NULL,
    endpoint        VARCHAR(500) NOT NULL,
    status_code     INTEGER      NOT NULL,
    response_time_ms BIGINT      DEFAULT 0,
    request_size    BIGINT       DEFAULT 0,
    response_size   BIGINT       DEFAULT 0,
    ip_address      VARCHAR(50),
    user_agent      VARCHAR(500),
    timestamp       TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    error_message   TEXT
);

-- ── Real-Time Notifications ──────────────────────────────────
CREATE TABLE IF NOT EXISTS notifications (
    id              VARCHAR(36)  NOT NULL PRIMARY KEY,
    user_id         VARCHAR(36)  NOT NULL,
    type            VARCHAR(50)  NOT NULL, -- INFO, WARNING, ERROR, SUCCESS
    title           VARCHAR(255) NOT NULL,
    message         TEXT         NOT NULL,
    resource_type   VARCHAR(50), -- EC2, S3, RDS, LAMBDA, BILLING
    resource_id     VARCHAR(36),
    is_read         BOOLEAN      DEFAULT FALSE,
    is_dismissed    BOOLEAN      DEFAULT FALSE,
    created_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    read_at         TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES iam_users(id) ON DELETE CASCADE
);

-- ── Live Cost Tracking ───────────────────────────────────────
CREATE TABLE IF NOT EXISTS cost_tracking (
    id              VARCHAR(36)  NOT NULL PRIMARY KEY,
    account_id      VARCHAR(20)  NOT NULL,
    service         VARCHAR(50)  NOT NULL,
    resource_id     VARCHAR(36),
    resource_name   VARCHAR(255),
    cost_type       VARCHAR(50)  NOT NULL, -- HOURLY, MONTHLY, PER_REQUEST, STORAGE
    base_cost       DOUBLE       DEFAULT 0,
    usage_amount    DOUBLE       DEFAULT 0,
    calculated_cost DOUBLE       DEFAULT 0,
    billing_period  VARCHAR(20)  NOT NULL, -- YYYY-MM format
    last_updated    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    is_active       BOOLEAN      DEFAULT TRUE
);

-- ── Service Health Monitoring ────────────────────────────────
CREATE TABLE IF NOT EXISTS service_health (
    id              VARCHAR(36)  NOT NULL PRIMARY KEY,
    service_name    VARCHAR(50)  NOT NULL, -- EC2, S3, RDS, LAMBDA, IAM
    status          VARCHAR(20)  NOT NULL, -- HEALTHY, DEGRADED, UNHEALTHY
    response_time_ms BIGINT      DEFAULT 0,
    error_rate      DOUBLE       DEFAULT 0,
    last_check      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    uptime_percent  DOUBLE       DEFAULT 100.0,
    details         TEXT
);

-- ── Real-Time Event Stream ───────────────────────────────────
CREATE TABLE IF NOT EXISTS event_stream (
    id              VARCHAR(36)  NOT NULL PRIMARY KEY,
    event_type      VARCHAR(50)  NOT NULL, -- USER_LOGIN, RESOURCE_CREATED, COST_ALERT
    source_service  VARCHAR(50)  NOT NULL,
    user_id         VARCHAR(36),
    account_id      VARCHAR(20),
    resource_type   VARCHAR(50),
    resource_id     VARCHAR(36),
    event_data      TEXT, -- JSON payload
    severity        VARCHAR(20)  DEFAULT 'INFO', -- INFO, WARNING, ERROR, CRITICAL
    timestamp       TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    processed       BOOLEAN      DEFAULT FALSE
);

-- ── Live Dashboard Metrics ───────────────────────────────────
CREATE TABLE IF NOT EXISTS dashboard_metrics (
    id                  VARCHAR(36)  NOT NULL PRIMARY KEY,
    account_id          VARCHAR(20)  NOT NULL,
    metric_date         DATE         NOT NULL,
    total_instances     INTEGER      DEFAULT 0,
    running_instances   INTEGER      DEFAULT 0,
    total_buckets       INTEGER      DEFAULT 0,
    total_objects       BIGINT       DEFAULT 0,
    storage_used_gb     DOUBLE       DEFAULT 0,
    lambda_functions    INTEGER      DEFAULT 0,
    lambda_invocations  BIGINT       DEFAULT 0,
    rds_instances       INTEGER      DEFAULT 0,
    daily_cost          DOUBLE       DEFAULT 0,
    api_requests        BIGINT       DEFAULT 0,
    last_updated        TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(account_id, metric_date)
);

-- ── Performance Indexes ──────────────────────────────────────
CREATE INDEX IF NOT EXISTS idx_system_metrics_time     ON system_metrics(timestamp);
CREATE INDEX IF NOT EXISTS idx_user_sessions_user      ON user_sessions(user_id);
CREATE INDEX IF NOT EXISTS idx_user_sessions_active    ON user_sessions(is_active, last_activity);
CREATE INDEX IF NOT EXISTS idx_resource_usage_account  ON resource_usage(account_id, resource_type);
CREATE INDEX IF NOT EXISTS idx_resource_usage_period   ON resource_usage(period_start, period_end);
CREATE INDEX IF NOT EXISTS idx_api_requests_user       ON api_requests(user_id, timestamp);
CREATE INDEX IF NOT EXISTS idx_api_requests_endpoint   ON api_requests(endpoint, timestamp);
CREATE INDEX IF NOT EXISTS idx_notifications_user      ON notifications(user_id, is_read);
CREATE INDEX IF NOT EXISTS idx_cost_tracking_account   ON cost_tracking(account_id, billing_period);
CREATE INDEX IF NOT EXISTS idx_service_health_service  ON service_health(service_name, last_check);
CREATE INDEX IF NOT EXISTS idx_event_stream_type       ON event_stream(event_type, timestamp);
CREATE INDEX IF NOT EXISTS idx_event_stream_user       ON event_stream(user_id, timestamp);
CREATE INDEX IF NOT EXISTS idx_dashboard_metrics_acct  ON dashboard_metrics(account_id, metric_date);

-- ── Add missing columns to existing tables ───────────────────
ALTER TABLE iam_users ADD COLUMN IF NOT EXISTS last_login TIMESTAMP;
ALTER TABLE iam_users ADD COLUMN IF NOT EXISTS login_count BIGINT DEFAULT 0;
ALTER TABLE iam_users ADD COLUMN IF NOT EXISTS last_ip VARCHAR(50);

ALTER TABLE compute_instances ADD COLUMN IF NOT EXISTS cpu_usage DOUBLE DEFAULT 0;
ALTER TABLE compute_instances ADD COLUMN IF NOT EXISTS memory_usage DOUBLE DEFAULT 0;
ALTER TABLE compute_instances ADD COLUMN IF NOT EXISTS network_in BIGINT DEFAULT 0;
ALTER TABLE compute_instances ADD COLUMN IF NOT EXISTS network_out BIGINT DEFAULT 0;
ALTER TABLE compute_instances ADD COLUMN IF NOT EXISTS last_heartbeat TIMESTAMP;

ALTER TABLE iam_buckets ADD COLUMN IF NOT EXISTS total_size_bytes BIGINT DEFAULT 0;
ALTER TABLE iam_buckets ADD COLUMN IF NOT EXISTS object_count BIGINT DEFAULT 0;
ALTER TABLE iam_buckets ADD COLUMN IF NOT EXISTS last_accessed TIMESTAMP;

ALTER TABLE lambda_functions ADD COLUMN IF NOT EXISTS total_duration_ms BIGINT DEFAULT 0;
ALTER TABLE lambda_functions ADD COLUMN IF NOT EXISTS error_count BIGINT DEFAULT 0;
ALTER TABLE lambda_functions ADD COLUMN IF NOT EXISTS avg_duration_ms DOUBLE DEFAULT 0;

ALTER TABLE rds_instances ADD COLUMN IF NOT EXISTS cpu_usage DOUBLE DEFAULT 0;
ALTER TABLE rds_instances ADD COLUMN IF NOT EXISTS memory_usage DOUBLE DEFAULT 0;
ALTER TABLE rds_instances ADD COLUMN IF NOT EXISTS connections_count INTEGER DEFAULT 0;
ALTER TABLE rds_instances ADD COLUMN IF NOT EXISTS last_backup TIMESTAMP;

-- ── Real-Time Views for Quick Access ─────────────────────────
DROP VIEW IF EXISTS live_system_status;
CREATE VIEW live_system_status AS
SELECT 
    'SYSTEM' as component,
    CASE 
        WHEN cpu_usage_percent > 90 THEN 'CRITICAL'
        WHEN cpu_usage_percent > 70 THEN 'WARNING'
        ELSE 'HEALTHY'
    END as status,
    cpu_usage_percent,
    memory_used_mb,
    disk_used_gb,
    active_threads,
    timestamp
FROM system_metrics 
WHERE timestamp > DATEADD('MINUTE', -5, CURRENT_TIMESTAMP)
ORDER BY timestamp DESC 
LIMIT 1;

DROP VIEW IF EXISTS live_user_activity;
CREATE VIEW live_user_activity AS
SELECT 
    u.username,
    u.email,
    u.last_login,
    s.session_type,
    s.last_activity,
    s.is_active,
    COUNT(a.id) as recent_actions
FROM iam_users u
LEFT JOIN user_sessions s ON u.id = s.user_id AND s.is_active = TRUE
LEFT JOIN monitoring_audit_logs a ON u.username = a.username 
    AND a.timestamp > DATEADD('HOUR', -1, CURRENT_TIMESTAMP)
GROUP BY u.id, u.username, u.email, u.last_login, s.session_type, s.last_activity, s.is_active
ORDER BY s.last_activity DESC;

DROP VIEW IF EXISTS live_resource_summary;
CREATE VIEW live_resource_summary AS
SELECT 
    account_id,
    COUNT(CASE WHEN state = 'RUNNING' THEN 1 END) as running_instances,
    COUNT(CASE WHEN state != 'TERMINATED' THEN 1 END) as total_instances,
    (SELECT COUNT(*) FROM iam_buckets WHERE account_id = ci.account_id) as total_buckets,
    (SELECT COUNT(*) FROM lambda_functions WHERE account_id = ci.account_id) as total_functions,
    (SELECT COUNT(*) FROM rds_instances WHERE account_id = ci.account_id) as total_rds
FROM compute_instances ci
WHERE ci.account_id IS NOT NULL
GROUP BY account_id;

DROP VIEW IF EXISTS live_cost_summary;
CREATE VIEW live_cost_summary AS
SELECT 
    account_id,
    SUM(CASE WHEN CAST(created_at AS DATE) = CURRENT_DATE THEN total_cost ELSE 0 END) as today_cost,
    SUM(CASE WHEN MONTH(created_at) = MONTH(CURRENT_DATE) THEN total_cost ELSE 0 END) as month_cost,
    COUNT(*) as total_billing_records
FROM billing_records
GROUP BY account_id;