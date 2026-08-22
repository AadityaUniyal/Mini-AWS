-- ============================================================
--  V1__initial_schema.sql
--  MiniCloud — Canonical Enterprise Cloud Database Schema
--  100% ANSI SQL Compatible (H2 & PostgreSQL)
-- ============================================================

-- ── IAM: Users ───────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS iam_users (
    id             VARCHAR(36)  NOT NULL PRIMARY KEY,
    username       VARCHAR(100) NOT NULL UNIQUE,
    password_hash  VARCHAR(255) NOT NULL,
    email          VARCHAR(255) UNIQUE,
    account_id     VARCHAR(20),
    role           VARCHAR(30),
    root_user      BOOLEAN      DEFAULT FALSE,
    enabled        BOOLEAN      DEFAULT TRUE,
    inline_policy  TEXT,
    last_login     TIMESTAMP,
    login_count    BIGINT       DEFAULT 0,
    last_ip        VARCHAR(50),
    created_at     TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

-- ── IAM: Policies ────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS iam_policies (
    id          VARCHAR(36)  NOT NULL PRIMARY KEY,
    name        VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(255),
    document    TEXT,
    user_id     VARCHAR(36),
    managed     BOOLEAN      DEFAULT FALSE,
    created_at  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

-- ── IAM: User ↔ Policy join table ────────────────────────────
CREATE TABLE IF NOT EXISTS user_policies (
    user_id   VARCHAR(36) NOT NULL,
    policy_id VARCHAR(36) NOT NULL,
    PRIMARY KEY (user_id, policy_id),
    FOREIGN KEY (user_id)   REFERENCES iam_users(id)    ON DELETE CASCADE,
    FOREIGN KEY (policy_id) REFERENCES iam_policies(id) ON DELETE CASCADE
);

-- ── IAM: Access Keys ─────────────────────────────────────────
CREATE TABLE IF NOT EXISTS iam_access_keys (
    id               VARCHAR(36)  NOT NULL PRIMARY KEY,
    key_id           VARCHAR(100) NOT NULL UNIQUE,
    secret_key_hash  VARCHAR(255) NOT NULL,
    active           BOOLEAN      DEFAULT TRUE,
    user_id          VARCHAR(36)  NOT NULL,
    created_at       TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    last_used_at     TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES iam_users(id) ON DELETE CASCADE
);

-- ── Networking: VPCs ─────────────────────────────────────────
CREATE TABLE IF NOT EXISTS vpc_networks (
    id           VARCHAR(36)  NOT NULL PRIMARY KEY,
    name         VARCHAR(100) NOT NULL,
    cidr_block   VARCHAR(50)  NOT NULL,
    state        VARCHAR(20)  DEFAULT 'available',
    account_id   VARCHAR(20)  NOT NULL,
    is_default   BOOLEAN      DEFAULT FALSE,
    created_at   TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

-- ── Networking: Subnets ──────────────────────────────────────
CREATE TABLE IF NOT EXISTS vpc_subnets (
    id                  VARCHAR(36)  NOT NULL PRIMARY KEY,
    name                VARCHAR(100) NOT NULL,
    vpc_id              VARCHAR(36)  NOT NULL,
    cidr_block          VARCHAR(50)  NOT NULL,
    availability_zone   VARCHAR(50),
    account_id          VARCHAR(20)  NOT NULL,
    created_at          TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (vpc_id) REFERENCES vpc_networks(id) ON DELETE CASCADE
);

-- ── Compute: Security Groups ──────────────────────────────────
CREATE TABLE IF NOT EXISTS compute_security_groups (
    id          VARCHAR(36)  NOT NULL PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    user_id     VARCHAR(36),
    account_id  VARCHAR(20),
    created_at  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

-- ── Compute: Security Group Rules ─────────────────────────────
CREATE TABLE IF NOT EXISTS compute_security_group_rules (
    id                VARCHAR(36) NOT NULL PRIMARY KEY,
    security_group_id VARCHAR(36) NOT NULL,
    type              VARCHAR(10) NOT NULL,
    protocol          VARCHAR(10) NOT NULL,
    from_port         INTEGER,
    to_port           INTEGER,
    cidr_ip           VARCHAR(50),
    FOREIGN KEY (security_group_id) REFERENCES compute_security_groups(id) ON DELETE CASCADE
);

-- ── Networking: NACLs ─────────────────────────────────────────
CREATE TABLE IF NOT EXISTS network_acls (
    id           VARCHAR(36)  NOT NULL PRIMARY KEY,
    name         VARCHAR(100) NOT NULL,
    vpc_id       VARCHAR(36),
    subnet_id    VARCHAR(36),
    account_id   VARCHAR(20),
    is_default   BOOLEAN      DEFAULT FALSE,
    created_at   TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS network_acl_rules (
    id           VARCHAR(36)  NOT NULL PRIMARY KEY,
    nacl_id      VARCHAR(36)  NOT NULL,
    rule_number  INTEGER      NOT NULL,
    type         VARCHAR(10)  NOT NULL,
    protocol     VARCHAR(10)  NOT NULL,
    from_port    INTEGER,
    to_port      INTEGER,
    cidr_block   VARCHAR(50),
    allow        BOOLEAN      DEFAULT TRUE,
    FOREIGN KEY (nacl_id) REFERENCES network_acls(id) ON DELETE CASCADE
);

-- ── Compute: Instances ────────────────────────────────────────
CREATE TABLE IF NOT EXISTS compute_instances (
    id                VARCHAR(36)  NOT NULL PRIMARY KEY,
    instance_name     VARCHAR(100) NOT NULL,
    instance_type     VARCHAR(20)  NOT NULL,
    state             VARCHAR(20),
    user_id           VARCHAR(36),
    account_id        VARCHAR(20),
    subnet_id         VARCHAR(36),
    security_group_id VARCHAR(36),
    private_ip        VARCHAR(50),
    public_ip         VARCHAR(50),
    launch_command    TEXT,
    process_id        BIGINT,
    container_id      VARCHAR(100),
    cpu_cores         INTEGER      DEFAULT 1,
    ram_mb            INTEGER      DEFAULT 1024,
    disk_gb           INTEGER      DEFAULT 10,
    cpu_usage         DOUBLE       DEFAULT 0,
    memory_usage      DOUBLE       DEFAULT 0,
    network_in        BIGINT       DEFAULT 0,
    network_out       BIGINT       DEFAULT 0,
    last_heartbeat    TIMESTAMP,
    version           BIGINT       DEFAULT 0,
    launched_at       TIMESTAMP,
    created_at        TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP
);

-- ── Storage: Buckets (Canonical s3_buckets) ───────────────────
CREATE TABLE IF NOT EXISTS s3_buckets (
    id                 VARCHAR(36)  NOT NULL PRIMARY KEY,
    name               VARCHAR(100) NOT NULL UNIQUE,
    user_id            VARCHAR(36),
    account_id         VARCHAR(20),
    region             VARCHAR(50),
    public_read        BOOLEAN      DEFAULT FALSE,
    versioning_enabled BOOLEAN      DEFAULT FALSE,
    website_enabled    BOOLEAN      DEFAULT FALSE,
    index_document     VARCHAR(200) DEFAULT 'index.html',
    error_document     VARCHAR(200) DEFAULT 'error.html',
    retention_days     INTEGER      DEFAULT 0,
    spa_mode           BOOLEAN      DEFAULT FALSE,
    total_size_bytes   BIGINT       DEFAULT 0,
    object_count       BIGINT       DEFAULT 0,
    last_accessed      TIMESTAMP,
    version            BIGINT       DEFAULT 0,
    created_at         TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

-- ── Storage: Objects ──────────────────────────────────────────
CREATE TABLE IF NOT EXISTS storage_objects (
    id            VARCHAR(36)   NOT NULL PRIMARY KEY,
    bucket_id     VARCHAR(36)   NOT NULL,
    object_key    VARCHAR(500)  NOT NULL,
    content_type  VARCHAR(100),
    size_bytes    BIGINT        DEFAULT 0,
    etag          VARCHAR(100),
    local_path    VARCHAR(1000),
    content       BLOB,
    last_modified TIMESTAMP,
    version       BIGINT        DEFAULT 0,
    created_at    TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP,
    FOREIGN KEY (bucket_id) REFERENCES s3_buckets(id) ON DELETE CASCADE
);

-- ── Storage: Object Metadata ──────────────────────────────────
CREATE TABLE IF NOT EXISTS storage_object_metadata (
    object_id  VARCHAR(36)  NOT NULL,
    meta_key   VARCHAR(255) NOT NULL,
    meta_value VARCHAR(255),
    PRIMARY KEY (object_id, meta_key),
    FOREIGN KEY (object_id) REFERENCES storage_objects(id) ON DELETE CASCADE
);

-- ── RDS: Instances ────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS rds_instances (
    id                     VARCHAR(36)  NOT NULL PRIMARY KEY,
    db_instance_identifier VARCHAR(255) NOT NULL UNIQUE,
    engine                 VARCHAR(50)  DEFAULT 'h2',
    instance_class         VARCHAR(50)  DEFAULT 'db.t3.micro',
    allocated_storage_gb   INTEGER      DEFAULT 20,
    db_name                VARCHAR(255) NOT NULL,
    master_username        VARCHAR(255) NOT NULL,
    master_password        VARCHAR(255) NOT NULL,
    port                   INTEGER      NOT NULL,
    status                 VARCHAR(50)  NOT NULL,
    endpoint               VARCHAR(255),
    pid                    BIGINT,
    user_id                VARCHAR(36),
    account_id             VARCHAR(20),
    subnet_id              VARCHAR(36),
    security_group_id      VARCHAR(36),
    cpu_usage              DOUBLE       DEFAULT 0,
    memory_usage           DOUBLE       DEFAULT 0,
    connections_count      INTEGER      DEFAULT 0,
    last_backup            TIMESTAMP,
    version                BIGINT       DEFAULT 0,
    created_at             TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

-- ── Lambda: Functions ─────────────────────────────────────────
CREATE TABLE IF NOT EXISTS lambda_functions (
    id                   VARCHAR(36)  NOT NULL PRIMARY KEY,
    function_name        VARCHAR(100) NOT NULL,
    description          VARCHAR(500),
    user_id              VARCHAR(36),
    account_id           VARCHAR(20),
    runtime              VARCHAR(20)  NOT NULL,
    handler              VARCHAR(300) NOT NULL,
    code_path            VARCHAR(1000),
    s3_bucket            VARCHAR(100),
    s3_key               VARCHAR(500),
    memory_mb            INTEGER      DEFAULT 128,
    timeout_sec          INTEGER      DEFAULT 30,
    environment_vars     TEXT,
    status               VARCHAR(20)  DEFAULT 'ACTIVE',
    total_duration_ms    BIGINT       DEFAULT 0,
    error_count          BIGINT       DEFAULT 0,
    avg_duration_ms      DOUBLE       DEFAULT 0,
    invocation_count     BIGINT       DEFAULT 0,
    last_exit_code       INTEGER      DEFAULT -1,
    last_invoked_at      TIMESTAMP,
    version              BIGINT       DEFAULT 0,
    created_at           TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP,
    CONSTRAINT uq_func_account_name UNIQUE(account_id, function_name)
);

-- ── Lambda: Invocation Logs ───────────────────────────────────
CREATE TABLE IF NOT EXISTS lambda_invocation_logs (
    id              VARCHAR(36)  NOT NULL PRIMARY KEY,
    function_id     VARCHAR(36)  NOT NULL,
    function_name   VARCHAR(255) NOT NULL,
    caller_user_id  VARCHAR(36),
    account_id      VARCHAR(20),
    timestamp       TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    duration_ms     BIGINT,
    exit_code       INTEGER,
    status          VARCHAR(20),
    output          TEXT,
    error_output    TEXT,
    payload         TEXT,
    FOREIGN KEY (function_id) REFERENCES lambda_functions(id) ON DELETE CASCADE
);

-- ── S3 Lambda Triggers ────────────────────────────────────────
CREATE TABLE IF NOT EXISTS s3_lambda_triggers (
    id              VARCHAR(36)  NOT NULL PRIMARY KEY,
    bucket_id       VARCHAR(36)  NOT NULL,
    bucket_name     VARCHAR(100) NOT NULL,
    function_id     VARCHAR(36)  NOT NULL,
    function_name   VARCHAR(100) NOT NULL,
    event_type      VARCHAR(50)  NOT NULL,
    prefix          VARCHAR(255),
    suffix          VARCHAR(255),
    enabled         BOOLEAN      DEFAULT TRUE,
    created_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

-- ── Monitoring: Alarms ────────────────────────────────────────
CREATE TABLE IF NOT EXISTS monitoring_alarms (
    id                    VARCHAR(36)  NOT NULL PRIMARY KEY,
    name                  VARCHAR(100) NOT NULL,
    description           VARCHAR(255),
    metric_name           VARCHAR(255) NOT NULL,
    comparison_operator   VARCHAR(30),
    threshold             DOUBLE,
    notification_topic    VARCHAR(255),
    action                VARCHAR(30),
    target_id             VARCHAR(255),
    user_id               VARCHAR(36),
    account_id            VARCHAR(20),
    enabled               BOOLEAN      DEFAULT TRUE,
    state                 VARCHAR(30)  DEFAULT 'OK',
    created_at            TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    last_triggered_at     TIMESTAMP
);

-- ── Monitoring: Audit Logs ────────────────────────────────────
CREATE TABLE IF NOT EXISTS monitoring_audit_logs (
    id             VARCHAR(36)  NOT NULL PRIMARY KEY,
    username       VARCHAR(100),
    service        VARCHAR(50)  NOT NULL,
    action         VARCHAR(100) NOT NULL,
    resource       VARCHAR(255),
    status         VARCHAR(20),
    details        VARCHAR(500),
    user_id        VARCHAR(36),
    account_id     VARCHAR(20),
    correlation_id VARCHAR(64),
    timestamp      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

-- ── Billing: Records ─────────────────────────────────────────
CREATE TABLE IF NOT EXISTS billing_records (
    id               VARCHAR(36)  NOT NULL PRIMARY KEY,
    account_id       VARCHAR(20)  NOT NULL,
    service          VARCHAR(50),
    resource_id      VARCHAR(100),
    resource_name    VARCHAR(255),
    unit_price       DECIMAL(19, 4),
    unit_type        VARCHAR(50),
    usage_quantity   DECIMAL(19, 4),
    total_cost       DECIMAL(19, 4),
    start_time       TIMESTAMP,
    end_time         TIMESTAMP,
    created_at       TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

-- ── Billing: Invoices ─────────────────────────────────────────
CREATE TABLE IF NOT EXISTS billing_invoices (
    id              VARCHAR(36)  NOT NULL PRIMARY KEY,
    account_id      VARCHAR(20)  NOT NULL,
    invoice_number  VARCHAR(100),
    total_amount    DECIMAL(19, 4),
    status          VARCHAR(20),
    period_start    TIMESTAMP,
    period_end      TIMESTAMP,
    created_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

-- ── Networking: Routes ────────────────────────────────────────
CREATE TABLE IF NOT EXISTS routes (
    id                VARCHAR(36)  NOT NULL PRIMARY KEY,
    name              VARCHAR(100) NOT NULL UNIQUE,
    domain_or_path    VARCHAR(500),
    host_pattern      VARCHAR(255),
    target_url        VARCHAR(500),
    target_host       VARCHAR(255),
    target_port       INTEGER,
    strip_prefix      VARCHAR(255),
    type              VARCHAR(20),
    enabled           BOOLEAN      DEFAULT TRUE,
    healthy           BOOLEAN      DEFAULT TRUE,
    last_health_check TIMESTAMP,
    request_count     BIGINT       DEFAULT 0,
    user_id           VARCHAR(36),
    account_id        VARCHAR(20),
    ec2_instance_id   VARCHAR(36),
    version           BIGINT       DEFAULT 0,
    created_at        TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP
);

-- ── CloudWatch Logs: Streams ──────────────────────────────────
CREATE TABLE IF NOT EXISTS log_streams (
    id               VARCHAR(36)  NOT NULL PRIMARY KEY,
    log_group_name   VARCHAR(255) NOT NULL,
    log_stream_name  VARCHAR(255) NOT NULL,
    account_id       VARCHAR(20),
    created_at       TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    last_event_at    TIMESTAMP
);

-- ── CloudWatch Logs: Events ───────────────────────────────────
CREATE TABLE IF NOT EXISTS log_events (
    id             VARCHAR(36) NOT NULL PRIMARY KEY,
    log_stream_id  VARCHAR(36) NOT NULL,
    timestamp      BIGINT,
    message        TEXT,
    ingestion_time TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (log_stream_id) REFERENCES log_streams(id) ON DELETE CASCADE
);

-- ── Route53: Hosted Zones ─────────────────────────────────────
CREATE TABLE IF NOT EXISTS route53_hosted_zones (
    id               VARCHAR(36)  NOT NULL PRIMARY KEY,
    name             VARCHAR(255) NOT NULL,
    caller_reference VARCHAR(255),
    comment          VARCHAR(500),
    account_id       VARCHAR(20),
    created_at       TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

-- ── Route53: DNS Records ──────────────────────────────────────
CREATE TABLE IF NOT EXISTS route53_records (
    id             VARCHAR(36)  NOT NULL PRIMARY KEY,
    hosted_zone_id VARCHAR(36)  NOT NULL,
    name           VARCHAR(255) NOT NULL,
    type           VARCHAR(10)  NOT NULL,
    ttl            BIGINT       DEFAULT 300,
    record_value   TEXT,
    account_id     VARCHAR(20),
    FOREIGN KEY (hosted_zone_id) REFERENCES route53_hosted_zones(id) ON DELETE CASCADE
);

-- ── Background Tasks ─────────────────────────────────────────
CREATE TABLE IF NOT EXISTS background_tasks (
    id            VARCHAR(36)  NOT NULL PRIMARY KEY,
    type          VARCHAR(50)  NOT NULL,
    status        VARCHAR(20)  NOT NULL,
    progress      INTEGER      DEFAULT 0,
    description   VARCHAR(500),
    user_id       VARCHAR(36),
    account_id    VARCHAR(20),
    error_details TEXT,
    start_time    TIMESTAMP,
    end_time      TIMESTAMP,
    created_at    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

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
    session_type    VARCHAR(20)  DEFAULT 'WEB',
    FOREIGN KEY (user_id) REFERENCES iam_users(id) ON DELETE CASCADE
);

-- ── Resource Usage Tracking ──────────────────────────────────
CREATE TABLE IF NOT EXISTS resource_usage (
    id              VARCHAR(36)  NOT NULL PRIMARY KEY,
    account_id      VARCHAR(20)  NOT NULL,
    resource_type   VARCHAR(50)  NOT NULL,
    resource_id     VARCHAR(36)  NOT NULL,
    resource_name   VARCHAR(255),
    usage_type      VARCHAR(50)  NOT NULL,
    usage_value     DOUBLE       DEFAULT 0,
    cost_per_unit   DOUBLE       DEFAULT 0,
    total_cost      DOUBLE       DEFAULT 0,
    period_start    TIMESTAMP    NOT NULL,
    period_end      TIMESTAMP    NOT NULL,
    recorded_at     TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

-- ── Live API Request Tracking ────────────────────────────────
CREATE TABLE IF NOT EXISTS api_requests (
    id               VARCHAR(36)  NOT NULL PRIMARY KEY,
    user_id          VARCHAR(36),
    username         VARCHAR(100),
    method           VARCHAR(10)  NOT NULL,
    endpoint         VARCHAR(500) NOT NULL,
    status_code      INTEGER      NOT NULL,
    response_time_ms BIGINT       DEFAULT 0,
    request_size     BIGINT       DEFAULT 0,
    response_size    BIGINT       DEFAULT 0,
    ip_address       VARCHAR(50),
    user_agent       VARCHAR(500),
    timestamp        TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    error_message    TEXT
);

-- ── Real-Time Notifications ──────────────────────────────────
CREATE TABLE IF NOT EXISTS notifications (
    id              VARCHAR(36)  NOT NULL PRIMARY KEY,
    user_id         VARCHAR(36)  NOT NULL,
    type            VARCHAR(50)  NOT NULL,
    title           VARCHAR(255) NOT NULL,
    message         TEXT         NOT NULL,
    resource_type   VARCHAR(50),
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
    cost_type       VARCHAR(50)  NOT NULL,
    base_cost       DOUBLE       DEFAULT 0,
    usage_amount    DOUBLE       DEFAULT 0,
    calculated_cost DOUBLE       DEFAULT 0,
    billing_period  VARCHAR(20)  NOT NULL,
    last_updated    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    is_active       BOOLEAN      DEFAULT TRUE
);

-- ── Service Health Monitoring ────────────────────────────────
CREATE TABLE IF NOT EXISTS service_health (
    id               VARCHAR(36)  NOT NULL PRIMARY KEY,
    service_name     VARCHAR(50)  NOT NULL,
    status           VARCHAR(20)  NOT NULL,
    response_time_ms BIGINT       DEFAULT 0,
    error_rate       DOUBLE       DEFAULT 0,
    last_check       TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    uptime_percent   DOUBLE       DEFAULT 100.0,
    details          TEXT
);

-- ── Real-Time Event Stream ───────────────────────────────────
CREATE TABLE IF NOT EXISTS event_stream (
    id              VARCHAR(36)  NOT NULL PRIMARY KEY,
    event_type      VARCHAR(50)  NOT NULL,
    source_service  VARCHAR(50)  NOT NULL,
    user_id         VARCHAR(36),
    account_id      VARCHAR(20),
    resource_type   VARCHAR(50),
    resource_id     VARCHAR(36),
    event_data      TEXT,
    severity        VARCHAR(20)  DEFAULT 'INFO',
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

-- ── Indexes ───────────────────────────────────────────────────
CREATE INDEX idx_instances_acct       ON compute_instances(account_id);
CREATE INDEX idx_instances_user       ON compute_instances(user_id);
CREATE INDEX idx_buckets_acct         ON s3_buckets(account_id);
CREATE INDEX idx_buckets_user         ON s3_buckets(user_id);
CREATE INDEX idx_objects_bucket       ON storage_objects(bucket_id);
CREATE INDEX idx_functions_acct       ON lambda_functions(account_id);
CREATE INDEX idx_functions_user       ON lambda_functions(user_id);
CREATE INDEX idx_lambda_logs_fn       ON lambda_invocation_logs(function_id);
CREATE INDEX idx_lambda_logs_acct     ON lambda_invocation_logs(account_id);
CREATE INDEX idx_audit_logs_acct      ON monitoring_audit_logs(account_id);
CREATE INDEX idx_audit_logs_user      ON monitoring_audit_logs(user_id);
CREATE INDEX idx_audit_logs_time      ON monitoring_audit_logs(timestamp);
CREATE INDEX idx_alarms_acct          ON monitoring_alarms(account_id);
CREATE INDEX idx_alarms_user          ON monitoring_alarms(user_id);
CREATE INDEX idx_access_keys_user     ON iam_access_keys(user_id);
CREATE INDEX idx_rds_acct             ON rds_instances(account_id);
CREATE INDEX idx_rds_user             ON rds_instances(user_id);
CREATE INDEX idx_vpc_subnets_vpc      ON vpc_subnets(vpc_id);
CREATE INDEX idx_vpc_subnets_acct     ON vpc_subnets(account_id);
CREATE INDEX idx_routes_acct          ON routes(account_id);
CREATE INDEX idx_routes_user          ON routes(user_id);
CREATE INDEX idx_log_streams_acct     ON log_streams(account_id);
CREATE INDEX idx_log_streams_group    ON log_streams(log_group_name);
CREATE INDEX idx_log_events_stream    ON log_events(log_stream_id);
CREATE INDEX idx_billing_acct_time    ON billing_records(account_id, created_at);
CREATE INDEX idx_billing_res_time     ON billing_records(account_id, resource_id, created_at);
CREATE INDEX idx_route53_zones_acct   ON route53_hosted_zones(account_id);
CREATE INDEX idx_route53_records_zone ON route53_records(hosted_zone_id);
CREATE INDEX idx_tasks_acct           ON background_tasks(account_id);
CREATE INDEX idx_system_metrics_time  ON system_metrics(timestamp);
CREATE INDEX idx_user_sessions_user   ON user_sessions(user_id);
CREATE INDEX idx_user_sessions_active ON user_sessions(is_active, last_activity);
CREATE INDEX idx_api_requests_user    ON api_requests(user_id, timestamp);
CREATE INDEX idx_notifications_user   ON notifications(user_id, is_read);
CREATE INDEX idx_cost_track_acct_per  ON cost_tracking(account_id, billing_period);
