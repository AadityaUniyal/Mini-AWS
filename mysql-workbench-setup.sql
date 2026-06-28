-- ============================================================
--  MINICLOUD DATABASE SETUP SCRIPT FOR MYSQL WORKBENCH
--  Run this ENTIRELY inside MySQL Workbench ONLY.
--  The Java backend will ONLY hold connection config.
-- ============================================================

-- Drop and recreate database for clean setup
DROP DATABASE IF EXISTS minicloud_db;
CREATE DATABASE minicloud_db
CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;

USE minicloud_db;

-- ============================================================
-- IAM TABLES
-- ============================================================

-- Users table
CREATE TABLE iam_users (
    id BINARY(16) PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    email VARCHAR(150) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role ENUM('ADMIN', 'USER') DEFAULT 'USER',
    account_id VARCHAR(50) NOT NULL,
    root_user BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_account_id (account_id),
    INDEX idx_email (email)
) ENGINE=InnoDB;

-- Policies table
CREATE TABLE iam_policies (
    id BINARY(16) PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    policy_document TEXT NOT NULL,
    account_id VARCHAR(50) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_account_id (account_id)
) ENGINE=InnoDB;

-- Access Keys table
CREATE TABLE iam_access_keys (
    id BINARY(16) PRIMARY KEY,
    user_id BINARY(16) NOT NULL,
    access_key_id VARCHAR(100) NOT NULL UNIQUE,
    secret_access_key VARCHAR(255) NOT NULL,
    status ENUM('ACTIVE', 'INACTIVE') DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES iam_users(id) ON DELETE CASCADE,
    INDEX idx_access_key_id (access_key_id)
) ENGINE=InnoDB;

-- ============================================================
-- STORAGE TABLES (S3-like)
-- ============================================================

-- Buckets table
CREATE TABLE iam_buckets (
    id BINARY(16) PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    owner_id BINARY(16) NOT NULL,
    account_id VARCHAR(50) NOT NULL,
    region VARCHAR(100) DEFAULT 'us-east-1',
    versioning_enabled BOOLEAN DEFAULT FALSE,
    website_enabled BOOLEAN DEFAULT FALSE,
    index_document VARCHAR(255),
    error_document VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (owner_id) REFERENCES iam_users(id) ON DELETE CASCADE,
    INDEX idx_owner_id (owner_id),
    INDEX idx_account_id (account_id)
) ENGINE=InnoDB;

-- Storage Objects table
CREATE TABLE storage_objects (
    id BINARY(16) PRIMARY KEY,
    bucket_id BINARY(16) NOT NULL,
    object_key VARCHAR(500) NOT NULL,
    file_path VARCHAR(1000),
    size_bytes BIGINT DEFAULT 0,
    content_type VARCHAR(200),
    etag VARCHAR(100),
    version_id VARCHAR(100),
    uploaded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (bucket_id) REFERENCES iam_buckets(id) ON DELETE CASCADE,
    INDEX idx_bucket_id (bucket_id),
    INDEX idx_object_key (object_key(255)),
    UNIQUE KEY unique_bucket_key (bucket_id, object_key(255))
) ENGINE=InnoDB;

-- Storage Object Metadata table
CREATE TABLE storage_object_metadata (
    id BINARY(16) PRIMARY KEY,
    object_id BINARY(16) NOT NULL,
    meta_key VARCHAR(255) NOT NULL,
    meta_value TEXT,
    FOREIGN KEY (object_id) REFERENCES storage_objects(id) ON DELETE CASCADE,
    INDEX idx_object_id (object_id)
) ENGINE=InnoDB;

-- ============================================================
-- COMPUTE TABLES (EC2-like)
-- ============================================================

-- EC2 Instances table
CREATE TABLE compute_instances (
    id BINARY(16) PRIMARY KEY,
    instance_name VARCHAR(200) NOT NULL,
    owner_id BINARY(16) NOT NULL,
    account_id VARCHAR(50) NOT NULL,
    instance_type VARCHAR(50) NOT NULL,
    state VARCHAR(50) DEFAULT 'PENDING',
    public_ip VARCHAR(50),
    private_ip VARCHAR(50),
    subnet_id BINARY(16),
    security_group_id BINARY(16),
    launch_command TEXT,
    process_id BIGINT,
    cpu_cores INT DEFAULT 1,
    ram_mb INT DEFAULT 512,
    disk_gb INT DEFAULT 8,
    launched_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    stopped_at TIMESTAMP NULL,
    terminated_at TIMESTAMP NULL,
    FOREIGN KEY (owner_id) REFERENCES iam_users(id) ON DELETE CASCADE,
    INDEX idx_owner_id (owner_id),
    INDEX idx_account_id (account_id),
    INDEX idx_state (state)
) ENGINE=InnoDB;

-- Security Groups table
CREATE TABLE compute_security_groups (
    id BINARY(16) PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    account_id VARCHAR(50) NOT NULL,
    vpc_id BINARY(16),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_account_id (account_id),
    UNIQUE KEY unique_name_account (name, account_id)
) ENGINE=InnoDB;

-- Security Group Rules table
CREATE TABLE compute_security_group_rules (
    id BINARY(16) PRIMARY KEY,
    security_group_id BINARY(16) NOT NULL,
    rule_type ENUM('INGRESS', 'EGRESS') NOT NULL,
    protocol VARCHAR(20) NOT NULL,
    from_port INT,
    to_port INT,
    cidr_block VARCHAR(50),
    description TEXT,
    FOREIGN KEY (security_group_id) REFERENCES compute_security_groups(id) ON DELETE CASCADE,
    INDEX idx_security_group_id (security_group_id)
) ENGINE=InnoDB;

-- ============================================================
-- LAMBDA TABLES
-- ============================================================

-- Lambda Functions table
CREATE TABLE lambda_functions (
    id BINARY(16) PRIMARY KEY,
    function_name VARCHAR(200) NOT NULL,
    runtime VARCHAR(50) NOT NULL,
    handler VARCHAR(255) NOT NULL,
    code_path VARCHAR(1000),
    memory_mb INT DEFAULT 128,
    timeout_sec INT DEFAULT 30,
    environment_vars TEXT,
    account_id VARCHAR(50) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_account_id (account_id),
    UNIQUE KEY unique_function_name_account (function_name, account_id)
) ENGINE=InnoDB;

-- Lambda Invocation Logs table
CREATE TABLE lambda_invocation_logs (
    id BINARY(16) PRIMARY KEY,
    function_id BINARY(16) NOT NULL,
    request_id VARCHAR(100) NOT NULL,
    status VARCHAR(50) NOT NULL,
    duration_ms BIGINT,
    memory_used_mb INT,
    log_output TEXT,
    error_message TEXT,
    invoked_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (function_id) REFERENCES lambda_functions(id) ON DELETE CASCADE,
    INDEX idx_function_id (function_id),
    INDEX idx_invoked_at (invoked_at)
) ENGINE=InnoDB;

-- ============================================================
-- RDS TABLES
-- ============================================================

-- RDS Instances table
CREATE TABLE rds_instances (
    id BINARY(16) PRIMARY KEY,
    db_instance_identifier VARCHAR(200) NOT NULL,
    engine VARCHAR(50) NOT NULL,
    instance_class VARCHAR(50) NOT NULL,
    allocated_storage_gb INT DEFAULT 20,
    master_username VARCHAR(100),
    master_password VARCHAR(255),
    db_name VARCHAR(100),
    endpoint VARCHAR(500),
    port INT DEFAULT 3306,
    status VARCHAR(50) DEFAULT 'CREATING',
    account_id VARCHAR(50) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_account_id (account_id),
    UNIQUE KEY unique_db_identifier_account (db_instance_identifier, account_id)
) ENGINE=InnoDB;

-- ============================================================
-- NETWORKING TABLES (VPC, Subnets, Routes)
-- ============================================================

-- VPC Networks table
CREATE TABLE vpc_networks (
    id BINARY(16) PRIMARY KEY,
    vpc_name VARCHAR(200) NOT NULL,
    cidr_block VARCHAR(50) NOT NULL,
    account_id VARCHAR(50) NOT NULL,
    is_default BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_account_id (account_id)
) ENGINE=InnoDB;

-- VPC Subnets table
CREATE TABLE vpc_subnets (
    id BINARY(16) PRIMARY KEY,
    subnet_name VARCHAR(200) NOT NULL,
    vpc_id BINARY(16) NOT NULL,
    cidr_block VARCHAR(50) NOT NULL,
    availability_zone VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (vpc_id) REFERENCES vpc_networks(id) ON DELETE CASCADE,
    INDEX idx_vpc_id (vpc_id)
) ENGINE=InnoDB;

-- Routes table
CREATE TABLE routes (
    id BINARY(16) PRIMARY KEY,
    path VARCHAR(500) NOT NULL,
    target_url VARCHAR(1000) NOT NULL,
    health_check_path VARCHAR(500),
    health_check_interval_sec INT DEFAULT 30,
    account_id VARCHAR(50) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_account_id (account_id)
) ENGINE=InnoDB;

-- ============================================================
-- MONITORING TABLES (CloudWatch-like)
-- ============================================================

-- Monitoring Alarms table
CREATE TABLE monitoring_alarms (
    id BINARY(16) PRIMARY KEY,
    alarm_name VARCHAR(200) NOT NULL,
    metric_name VARCHAR(100) NOT NULL,
    threshold_value DOUBLE NOT NULL,
    comparison_operator VARCHAR(50) NOT NULL,
    evaluation_periods INT DEFAULT 1,
    state VARCHAR(50) DEFAULT 'OK',
    account_id VARCHAR(50) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_account_id (account_id)
) ENGINE=InnoDB;

-- Monitoring Audit Logs table (CloudTrail-like)
CREATE TABLE monitoring_audit_logs (
    id BINARY(16) PRIMARY KEY,
    user_id BINARY(16),
    username VARCHAR(100),
    service VARCHAR(100) NOT NULL,
    action VARCHAR(200) NOT NULL,
    resource VARCHAR(500),
    status VARCHAR(50) NOT NULL,
    details TEXT,
    ip_address VARCHAR(50),
    user_agent TEXT,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES iam_users(id) ON DELETE SET NULL,
    INDEX idx_user_id (user_id),
    INDEX idx_timestamp (timestamp),
    INDEX idx_service (service)
) ENGINE=InnoDB;

-- ============================================================
-- BILLING TABLES
-- ============================================================

-- Billing Records table
CREATE TABLE billing_records (
    id BINARY(16) PRIMARY KEY,
    account_id VARCHAR(50) NOT NULL,
    service VARCHAR(100) NOT NULL,
    resource_id VARCHAR(100),
    resource_name VARCHAR(200),
    cost DECIMAL(10, 4) NOT NULL,
    unit VARCHAR(50),
    recorded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_account_id (account_id),
    INDEX idx_recorded_at (recorded_at)
) ENGINE=InnoDB;

-- Billing Invoices table
CREATE TABLE billing_invoices (
    id BINARY(16) PRIMARY KEY,
    account_id VARCHAR(50) NOT NULL,
    billing_period VARCHAR(50) NOT NULL,
    total_amount DECIMAL(10, 2) NOT NULL,
    status VARCHAR(50) DEFAULT 'PENDING',
    generated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_account_id (account_id)
) ENGINE=InnoDB;

-- ============================================================
-- LOGS TABLES (CloudWatch Logs-like)
-- ============================================================

-- Log Streams table
CREATE TABLE log_streams (
    id BINARY(16) PRIMARY KEY,
    log_group_name VARCHAR(200) NOT NULL,
    log_stream_name VARCHAR(200) NOT NULL,
    account_id VARCHAR(50) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_log_group (log_group_name),
    INDEX idx_account_id (account_id),
    UNIQUE KEY unique_group_stream (log_group_name, log_stream_name)
) ENGINE=InnoDB;

-- Log Events table
CREATE TABLE log_events (
    id BINARY(16) PRIMARY KEY,
    log_stream_id BINARY(16) NOT NULL,
    message TEXT NOT NULL,
    timestamp BIGINT NOT NULL,
    ingestion_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (log_stream_id) REFERENCES log_streams(id) ON DELETE CASCADE,
    INDEX idx_log_stream_id (log_stream_id),
    INDEX idx_timestamp (timestamp)
) ENGINE=InnoDB;

-- ============================================================
-- SAMPLE DATA
-- ============================================================

-- Insert admin user
INSERT INTO iam_users (id, username, email, password_hash, role, account_id, root_user, created_at)
VALUES (
    UNHEX(REPLACE(UUID(), '-', '')),
    'admin',
    'admin@minicloud.io',
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', -- BCrypt hash of 'admin123'
    'ADMIN',
    '123456789012',
    TRUE,
    CURRENT_TIMESTAMP
);

-- Insert test user
INSERT INTO iam_users (id, username, email, password_hash, role, account_id, root_user, created_at)
VALUES (
    UNHEX(REPLACE(UUID(), '-', '')),
    'testuser',
    'test@minicloud.io',
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', -- BCrypt hash of 'admin123'
    'USER',
    '123456789012',
    FALSE,
    CURRENT_TIMESTAMP
);

-- Insert sample buckets
INSERT INTO iam_buckets (id, name, owner_id, account_id, region, created_at)
SELECT 
    UNHEX(REPLACE(UUID(), '-', '')),
    'my-first-bucket',
    id,
    account_id,
    'us-east-1',
    CURRENT_TIMESTAMP
FROM iam_users WHERE username = 'admin';

INSERT INTO iam_buckets (id, name, owner_id, account_id, region, created_at)
SELECT 
    UNHEX(REPLACE(UUID(), '-', '')),
    'media-storage',
    id,
    account_id,
    'ap-south-1',
    CURRENT_TIMESTAMP
FROM iam_users WHERE username = 'testuser';

-- Insert sample EC2 instances
INSERT INTO compute_instances (id, instance_name, owner_id, account_id, instance_type, state, cpu_cores, ram_mb, launched_at)
SELECT 
    UNHEX(REPLACE(UUID(), '-', '')),
    'web-server-1',
    id,
    account_id,
    'T2_SMALL',
    'RUNNING',
    2,
    2048,
    CURRENT_TIMESTAMP
FROM iam_users WHERE username = 'admin';

INSERT INTO compute_instances (id, instance_name, owner_id, account_id, instance_type, state, cpu_cores, ram_mb, launched_at)
SELECT 
    UNHEX(REPLACE(UUID(), '-', '')),
    'db-server-1',
    id,
    account_id,
    'T2_MEDIUM',
    'STOPPED',
    2,
    4096,
    CURRENT_TIMESTAMP
FROM iam_users WHERE username = 'admin';

-- ============================================================
-- VERIFICATION QUERIES
-- ============================================================

-- Show all tables
SHOW TABLES;

-- Verify data
SELECT id, username, email, role, account_id, root_user FROM iam_users;
SELECT id, name, account_id, region FROM iam_buckets;
SELECT id, instance_name, instance_type, state, cpu_cores, ram_mb FROM compute_instances;

-- Show table counts
SELECT 'iam_users' AS table_name, COUNT(*) AS row_count FROM iam_users
UNION ALL
SELECT 'iam_buckets', COUNT(*) FROM iam_buckets
UNION ALL
SELECT 'compute_instances', COUNT(*) FROM compute_instances
UNION ALL
SELECT 'lambda_functions', COUNT(*) FROM lambda_functions
UNION ALL
SELECT 'rds_instances', COUNT(*) FROM rds_instances;

-- ============================================================
-- NOTES
-- ============================================================
-- 1. All UUIDs are stored as BINARY(16) for efficiency
-- 2. Use UNHEX(REPLACE(UUID(), '-', '')) to generate UUIDs in MySQL
-- 3. Use HEX(id) to view UUIDs in readable format
-- 4. All timestamps use TIMESTAMP with DEFAULT CURRENT_TIMESTAMP
-- 5. Foreign keys have ON DELETE CASCADE or SET NULL as appropriate
-- 6. Indexes are created on frequently queried columns
-- 7. Character set is utf8mb4 for full Unicode support
-- ============================================================
