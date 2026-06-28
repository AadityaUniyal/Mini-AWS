-- ============================================================
--  V1__initial_schema.sql
--  MiniCloud — corrected schema aligned with JPA entities
--  Compatible with H2, MySQL, and PostgreSQL
-- ============================================================

-- ── IAM: Users ───────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS iam_users (
    id             VARCHAR(36)  NOT NULL PRIMARY KEY,
    username       VARCHAR(100) NOT NULL UNIQUE,
    password_hash  VARCHAR(255) NOT NULL,
    email          VARCHAR(255) UNIQUE,
    account_id     VARCHAR(20)  UNIQUE,
    role           VARCHAR(30),
    root_user      BOOLEAN      DEFAULT FALSE,
    enabled        BOOLEAN      DEFAULT TRUE,
    inline_policy  TEXT,
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
    user_id     VARCHAR(36)  NOT NULL,
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

-- ── Compute: Instances ────────────────────────────────────────
CREATE TABLE IF NOT EXISTS compute_instances (
    id                VARCHAR(36)  NOT NULL PRIMARY KEY,
    name              VARCHAR(100) NOT NULL,
    type              VARCHAR(20),
    state             VARCHAR(20),
    user_id           VARCHAR(36)  NOT NULL,
    account_id        VARCHAR(20),
    subnet_id         VARCHAR(36),
    security_group_id VARCHAR(36),
    private_ip        VARCHAR(50),
    public_ip         VARCHAR(50),
    command           VARCHAR(500),
    pid               BIGINT,
    launched_at       TIMESTAMP,
    created_at        TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP,
    FOREIGN KEY (security_group_id) REFERENCES compute_security_groups(id)
);

-- ── Storage: Buckets ──────────────────────────────────────────
CREATE TABLE IF NOT EXISTS iam_buckets (
    id              VARCHAR(36)  NOT NULL PRIMARY KEY,
    name            VARCHAR(100) NOT NULL UNIQUE,
    user_id         VARCHAR(36)  NOT NULL,
    account_id      VARCHAR(20),
    public_read     BOOLEAN      DEFAULT FALSE,
    website_enabled BOOLEAN      DEFAULT FALSE,
    index_document  VARCHAR(200) DEFAULT 'index.html',
    error_document  VARCHAR(200) DEFAULT 'error.html',
    retention_days  INTEGER      DEFAULT 0,
    spa_mode        BOOLEAN      DEFAULT FALSE,
    created_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

-- ── Storage: Objects ──────────────────────────────────────────
CREATE TABLE IF NOT EXISTS storage_objects (
    id           VARCHAR(36)   NOT NULL PRIMARY KEY,
    bucket_id    VARCHAR(36)   NOT NULL,
    object_key   VARCHAR(500)  NOT NULL,
    content_type VARCHAR(100),
    size_bytes   BIGINT        DEFAULT 0,
    etag         VARCHAR(100),
    local_path   VARCHAR(1000),
    content      BLOB,
    last_modified TIMESTAMP,
    created_at   TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP,
    FOREIGN KEY (bucket_id) REFERENCES iam_buckets(id) ON DELETE CASCADE
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
    id                VARCHAR(36)  NOT NULL PRIMARY KEY,
    name              VARCHAR(255) NOT NULL UNIQUE,
    db_name           VARCHAR(255) NOT NULL,
    master_username   VARCHAR(255) NOT NULL,
    master_password   VARCHAR(255) NOT NULL,
    port              INTEGER      NOT NULL,
    status            VARCHAR(50)  NOT NULL,
    endpoint          VARCHAR(255),
    pid               BIGINT,
    user_id           VARCHAR(36)  NOT NULL,
    account_id        VARCHAR(20),
    subnet_id         VARCHAR(36),
    security_group_id VARCHAR(36),
    created_at        TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (security_group_id) REFERENCES compute_security_groups(id)
);

-- ── Lambda: Functions ─────────────────────────────────────────
CREATE TABLE IF NOT EXISTS lambda_functions (
    id                   VARCHAR(36)  NOT NULL PRIMARY KEY,
    name                 VARCHAR(100) NOT NULL UNIQUE,
    description          VARCHAR(500),
    user_id              VARCHAR(36)  NOT NULL,
    account_id           VARCHAR(20),
    runtime              VARCHAR(20)  NOT NULL,
    handler              VARCHAR(300) NOT NULL,
    s3_bucket            VARCHAR(100),
    s3_key               VARCHAR(500),
    memory_mb            INTEGER      DEFAULT 128,
    timeout_sec          INTEGER      DEFAULT 30,
    environment_config   TEXT,
    status               VARCHAR(20)  DEFAULT 'ACTIVE',
    created_at           TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    last_invoked_at      TIMESTAMP,
    invocation_count     BIGINT       DEFAULT 0,
    last_exit_code       INTEGER      DEFAULT -1
);

-- ── Lambda: Invocation Logs ───────────────────────────────────
CREATE TABLE IF NOT EXISTS lambda_invocation_logs (
    id              VARCHAR(36)  NOT NULL PRIMARY KEY,
    function_id     VARCHAR(36)  NOT NULL,
    function_name   VARCHAR(255) NOT NULL,
    caller_user_id  VARCHAR(36),
    timestamp       TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    duration_ms     BIGINT,
    exit_code       INTEGER,
    status          VARCHAR(20),
    output          TEXT,
    error_output    TEXT,
    payload         TEXT,
    FOREIGN KEY (function_id) REFERENCES lambda_functions(id) ON DELETE CASCADE
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
    user_id               VARCHAR(36)  NOT NULL,
    enabled               BOOLEAN      DEFAULT TRUE,
    state                 VARCHAR(30)  DEFAULT 'OK',
    created_at            TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    last_triggered_at     TIMESTAMP
);

-- ── Monitoring: Audit Logs ────────────────────────────────────
CREATE TABLE IF NOT EXISTS monitoring_audit_logs (
    id         VARCHAR(36)  NOT NULL PRIMARY KEY,
    username   VARCHAR(100),
    service    VARCHAR(50)  NOT NULL,
    action     VARCHAR(100) NOT NULL,
    resource   VARCHAR(255),
    status     VARCHAR(20),
    details    VARCHAR(500),
    user_id    VARCHAR(36),
    timestamp  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

-- ── Billing: Records ─────────────────────────────────────────
CREATE TABLE IF NOT EXISTS billing_records (
    id               VARCHAR(36)  NOT NULL PRIMARY KEY,
    account_id       VARCHAR(20)  NOT NULL,
    service          VARCHAR(50),
    resource_id      VARCHAR(100),
    resource_name    VARCHAR(255),
    unit_price       DOUBLE,
    unit_type        VARCHAR(50),
    usage_quantity   DOUBLE,
    total_cost       DOUBLE,
    start_time       TIMESTAMP,
    end_time         TIMESTAMP,
    created_at       TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

-- ── Billing: Invoices ─────────────────────────────────────────
CREATE TABLE IF NOT EXISTS billing_invoices (
    id              VARCHAR(36)  NOT NULL PRIMARY KEY,
    account_id      VARCHAR(20)  NOT NULL,
    invoice_number  VARCHAR(100),
    total_amount    DOUBLE,
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
    ec2_instance_id   VARCHAR(36),
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
    ingested_at    TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (log_stream_id) REFERENCES log_streams(id) ON DELETE CASCADE
);

-- ── Indexes ───────────────────────────────────────────────────
CREATE INDEX idx_instances_user       ON compute_instances(user_id);
CREATE INDEX idx_buckets_user         ON iam_buckets(user_id);
CREATE INDEX idx_objects_bucket       ON storage_objects(bucket_id);
CREATE INDEX idx_functions_user       ON lambda_functions(user_id);
CREATE INDEX idx_lambda_logs_fn       ON lambda_invocation_logs(function_id);
CREATE INDEX idx_audit_logs_user      ON monitoring_audit_logs(user_id);
CREATE INDEX idx_audit_logs_time      ON monitoring_audit_logs(timestamp);
CREATE INDEX idx_alarms_user          ON monitoring_alarms(user_id);
CREATE INDEX idx_access_keys_user     ON iam_access_keys(user_id);
CREATE INDEX idx_rds_user             ON rds_instances(user_id);
CREATE INDEX idx_vpc_subnets_vpc      ON vpc_subnets(vpc_id);
CREATE INDEX idx_routes_user          ON routes(user_id);
CREATE INDEX idx_log_streams_group    ON log_streams(log_group_name);
CREATE INDEX idx_log_events_stream    ON log_events(log_stream_id);
CREATE INDEX idx_billing_records_acct ON billing_records(account_id);

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

CREATE INDEX idx_route53_zones_account ON route53_hosted_zones(account_id);
CREATE INDEX idx_route53_records_zone  ON route53_records(hosted_zone_id);
