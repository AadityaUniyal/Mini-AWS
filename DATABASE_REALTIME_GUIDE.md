# 🔥 MiniCloud: Real-Time Database & Telemetry Guide

MiniCloud features an integrated real-time database architecture backed by **Flyway Schema Migrations** and **Spring Data JPA**. The persistence tier tracks user sessions, infrastructure metrics, S3 triggers, and financial accruals with sub-second precision.

---

## 🗄️ Database Schema & Flyway Migrations

MiniCloud migrations are located in `minicloud-api/src/main/resources/db/migration/` and automatically execute upon startup:

```
db/migration/
├── V1__initial_schema.sql             ← Core entities (IAM, EC2, S3, RDS, Lambda, VPC, Billing)
├── V2__nullable_audit_user_id.sql     ← Extended audit flexibility for unauthenticated probes
└── V3__enhanced_realtime_features.sql ← Real-time metrics, live user sessions, and S3 triggers
```

---

## 📊 Core Tables & Real-Time Tracking

### 1. Identity & Session Tracking
| Table | Description | Real-Time Fields |
|---|---|---|
| `iam_users` | IAM user accounts | `last_login`, `login_count`, `last_ip`, `enabled` |
| `user_sessions` | Active interactive sessions | `login_time`, `last_activity`, `logout_time`, `is_active`, `session_type` |
| `iam_policies` | AWS JSON Policy documents | `name`, `document`, `managed` |
| `iam_access_keys` | API access key credentials | `key_id`, `secret_key_hash`, `status` |

### 2. Compute & Storage Tracking
| Table | Description | Real-Time Fields |
|---|---|---|
| `compute_instances` | EC2 virtual machines | `state`, `cpu_usage`, `memory_usage`, `public_ip`, `private_ip` |
| `iam_buckets` | S3 storage buckets | `total_size_bytes`, `object_count`, `last_accessed` |
| `storage_objects` | S3 file entries | `size_bytes`, `etag`, `content_type`, `last_modified` |
| `s3_lambda_triggers` | Asynchronous event links | `bucket_name`, `function_name`, `event_type`, `created_at` |

### 3. Serverless & Database Tracking
| Table | Description | Real-Time Fields |
|---|---|---|
| `lambda_functions` | Serverless function definitions | `invocation_count`, `error_count`, `total_duration_ms` |
| `lambda_invocation_logs` | Execution log entries | `execution_time_ms`, `billed_duration_ms`, `status` |
| `rds_instances` | Managed database instances | `engine`, `status`, `allocated_storage_gb`, `endpoint` |

### 4. Telemetry & Analytics Tables
| Table | Description | Real-Time Fields |
|---|---|---|
| `system_metrics` | Host/JVM system metrics | `cpu_usage_percent`, `memory_used_mb`, `disk_used_gb`, `active_threads` |
| `api_requests` | Detailed API audit log | `method`, `endpoint`, `status_code`, `response_time_ms`, `ip_address` |
| `event_stream` | Cloud event bus records | `event_type`, `source_service`, `resource_type`, `severity` |
| `resource_usage` | Hourly metering ledger | `usage_type`, `usage_value`, `cost_per_unit`, `total_cost` |
| `dashboard_metrics` | Pre-aggregated summaries | `total_instances`, `running_instances`, `storage_used_gb`, `daily_cost` |

---

## 🔍 Useful Database Queries & Diagnostics

### Inspect Live System Health & Metric History
```sql
SELECT timestamp, cpu_usage_percent, memory_used_mb, active_threads, uptime_seconds 
FROM system_metrics 
ORDER BY timestamp DESC 
LIMIT 10;
```

### Inspect Active S3-to-Lambda Triggers
```sql
SELECT t.id, t.bucket_name, t.function_name, t.event_type, t.created_at, f.runtime, f.invocation_count
FROM s3_lambda_triggers t
JOIN lambda_functions f ON t.function_name = f.name;
```

### Audit Incurred Charges Across Active Accounts
```sql
SELECT account_id, resource_type, usage_type, SUM(usage_value) as total_usage, SUM(total_cost) as total_accrued_cost
FROM resource_usage
GROUP BY account_id, resource_type, usage_type
ORDER BY total_accrued_cost DESC;
```

### Trace Recent User Logins & Active Sessions
```sql
SELECT u.username, u.account_id, s.ip_address, s.session_type, s.login_time, s.is_active
FROM user_sessions s
JOIN iam_users u ON s.user_id = u.id
WHERE s.is_active = TRUE;
```

---

## 🧹 Automated Data Lifecycle & Retention

`RealTimeDbService` executes background retention tasks to prevent database bloat:
- **System Metrics**: Retained for 7 days.
- **API Request Logs**: Retained for 30 days.
- **Event Stream**: Retained for 90 days.
- **Inactive User Sessions**: Expired after 24 hours of inactivity.