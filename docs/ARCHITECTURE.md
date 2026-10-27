# 🏛️ MiniCloud: Architecture & Engineering Deep-Dive

MiniCloud is engineered as a high-performance **Modular Monolith** in Java 17 using **Spring Boot 3.2.5** and **FlatLaf Swing**. This document details the architectural paradigms, internal subsystems, thread model, data pipeline, and security mechanisms that enable accurate, local AWS emulation.

---

## 📐 Architectural Design Principles

1. **Modular Monolith Topology**: All AWS services (Compute, Storage, Messaging, Serverless, IAM, Network, Telemetry) are modeled as decoupled domains with clean interfaces, compiled into a single executable artifact (`minicloud-api-1.0.0.jar`).
2. **Dual-Mode Startup (Headless / Desktop)**:
   - **`DESKTOP` Mode**: Initializes an embedded Tomcat REST server alongside a Pure Java Swing management dashboard styled with FlatLaf Dark.
   - **`WEB` Mode**: Runs headless for containerized production deployments, CLI automation, and CI pipelines.
3. **Resilient Sandboxing & Fallbacks**:
   - Compute and Lambda runtimes prefer **Docker Alpine sandboxing** when the Docker daemon is responsive.
   - If Docker is unavailable, the platform seamlessly falls back to isolated host subprocesses (`ProcessBuilder`), ensuring 100% functionality on any developer machine.
4. **Pluggable Persistence**:
   - Zero-configuration local mode uses file-based **H2 Database** (`minicloud-data/db/miniclouddb`).
   - Enterprise mode supports cloud-native **PostgreSQL** via profile activation (`--spring.profiles.active=postgres`).
   - Automated schema versioning and real-time database views are managed via **Flyway** (`V1`, `V2`, `V3`).

---

## 🔍 Subsystem Deep-Dive

```
+---------------------------------------------------------------------------------------+
|                                    MiniCloud Subsystems                               |
|                                                                                       |
|  +------------------------+  +------------------------+  +-------------------------+  |
|  |     IAM & Security     |  |    Compute Engine      |  |     Storage Engine      |  |
|  | * JWT (JJWT 0.12.x)    |  | * State Machine        |  | * Local File Storage    |  |
|  | * PolicyEvaluator      |  | * Docker / Subprocess  |  | * ETag MD5 Hasher       |  |
|  | * CloudTrail Recorder  |  | * Auto-Healing Loop    |  | * S3TriggerDispatcher   |  |
|  +------------------------+  +------------------------+  +-------------------------+  |
|                                                                                       |
|  +------------------------+  +------------------------+  +-------------------------+  |
|  |    Lambda Serverless   |  |   Messaging Subsystem  |  |   Network & Security    |  |
|  | * Polyglot Runners    |  | * SQS (FIFO / DLQ)     |  | * CIDR IP Allocator     |  |
|  | * S3 Event Trigger     |  | * Lock-Free RingBuffer |  | * Stateful Sec Groups   |  |
|  | * WS Log Broadcast     |  | * SNS Topic Fan-out    |  | * Stateless NACLs       |  |
|  +------------------------+  +------------------------+  +-------------------------+  |
|                                                                                       |
|  +------------------------+  +------------------------+  +-------------------------+  |
|  |   Telemetry & Advisor  |  |   AWS Wire Adapter     |  |   Database (RDS)        |  |
|  | * OSHI Hardware Probes |  | * S3 REST XML Protocol |  | * Managed Instances     |  |
|  | * Rightsizing Advisor  |  | * SigV4 Authenticator  |  | * Automated Backups     |  |
|  | * CloudWatch Alarms    |  | * RFC 7233 Range Stream|  | * Storage Quota Engine  |  |
|  +------------------------+  +------------------------+  +-------------------------+  |
+---------------------------------------------------------------------------------------+
```

---

### 1. Identity & Access Management (IAM) Engine
- **Account Hierarchy**: Every tenant has a 12-digit AWS Account ID. The account owner is the `Root User`. Sub-users are `IAM Users`.
- **AWS Policy Evaluator**:
  - Accepts standard AWS JSON policy documents containing `Statement` arrays with `Effect` (`Allow`/`Deny`), `Action` (e.g., `s3:GetObject`, `ec2:*`), and `Resource` ARNs (e.g., `arn:aws:s3:::my-bucket/*`).
  - Implements **Explicit Deny Precedence**: If any policy denies an action, access is rejected even if another policy allows it.
  - Supports context variable expansion such as `${aws:username}` and `${aws:PrincipalAccount}`.
- **CloudTrail Audit Recorder**: Every authenticated API request logs an immutable event (`audit_events` table) recording timestamp, user ID, client IP, action, resource ARN, and request parameters.

---

### 2. Compute Engine (EC2 & Auto Scaling)
- **Lifecycle State Machine**: Transitions instances across `PENDING ➔ RUNNING ➔ STOPPING ➔ STOPPED ➔ TERMINATED` with strict validation matrices.
- **Process Sandbox**:
  - Instances run with CPU and memory quotas.
  - Commands sent via instance console are executed inside isolated Docker containers or sandboxed subprocesses with command sanitization preventing host escape.
- **Auto Scaling & Chaos Self-Healing**:
  - Background scheduler (`eval-interval-ms=60000`) continuously queries target groups and compute instance health.
  - When an instance is terminated unexpectedly or via Chaos Engineering (`/api/v1/chaos/terminate-random-instance`), the loop detects the capacity deficit and launches a replacement instance automatically.

---

### 3. Storage Engine (S3) & Event Trigger Dispatcher
- **Storage Structure**:
  - Buckets are created as subdirectories under `minicloud-data/storage/<account-id>/<bucket-name>/`.
  - Object metadata, content types, ETags (MD5 checksums), and size bytes are persisted in relational storage while byte payloads are stored on disk.
- **Zero-Copy RFC 7233 Byte-Range Streaming**:
  - Slices file channels directly over disk descriptors without loading full files into JVM memory, returning HTTP 206 `PARTIAL_CONTENT` with `Content-Range`.
- **S3-to-Lambda Event Dispatcher**:
  - Triggers are registered in `s3_lambda_triggers` table mapping a source bucket and event type (`OBJECT_CREATED`, `OBJECT_DELETED`) to a target Lambda function.
  - Dispatches standard AWS S3 event schema asynchronously via `TaskExecutor`.

---

### 4. Distributed Messaging Engine (SQS, SNS & RingBuffer)
- **SQS Emulation**:
  - In-memory standard and FIFO queues with visibility timeouts, 5-minute deduplication windows, and Dead Letter Queue (DLQ) redrive policies.
- **Lock-Free Circular RingBuffer**:
  - Power-of-two capacity with bitwise modulo `(seq & mask)` and atomic CAS cursors (`AtomicLong`).
  - Benchmarked at **>1.47 million msgs/sec** concurrent MPMC throughput on standard laptop hardware with zero lock contention.
- **SNS Topic Pub/Sub**:
  - Topic fan-out to SQS queues, webhooks, and Lambda functions.

---

### 5. Serverless Execution Engine (AWS Lambda)
- **Polyglot Runtime Support**: Python (`python3.9`, `python3.11`), Node.js (`nodejs18.x`, `nodejs20.x`), Java (`java17`), Bash (`provided.al2`).
- **Execution Isolation**: Runs in ephemeral Docker containers or isolated temporary host directories (`minicloud-data/lambda-tmp/`).
- **WebSocket Streaming**: Real-time stdout/stderr log broadcast to `/topic/lambda-logs/{functionName}`.

---

### 6. FinOps Telemetry & Rightsizing Advisor
- **Hardware Telemetry (OSHI)**: Samples CPU, RAM, Disk I/O, and Thread counts every 60 seconds into `system_metrics`.
- **Rightsizing Algorithm**: Flags instances with <10% average CPU usage as `DOWNSIZE` and calculates exact dollar savings.
- **CUR Exporter**: Exports Cost & Usage Reports in standard CSV and JSON formats (`GET /api/v1/billing/export`).

---

## 🗄️ Database Schema & Flyway Migrations

MiniCloud migrations are located in `minicloud-api/src/main/resources/db/migration/` and automatically execute upon startup:

```
db/migration/
├── V1__initial_schema.sql             ← Core entities (IAM, EC2, S3, RDS, Lambda, VPC, Billing)
├── V2__nullable_audit_user_id.sql     ← Extended audit flexibility for unauthenticated probes
└── V3__enhanced_realtime_features.sql ← Real-time metrics, live user sessions, and S3 triggers
```

### Core Relational Schema

| Domain | Table | Key Fields | Description |
|---|---|---|---|
| **Identity** | `iam_users` | `id`, `username`, `account_id`, `role`, `enabled` | User accounts and credentials |
| **Identity** | `user_sessions` | `session_token`, `login_time`, `last_activity`, `is_active` | Interactive sessions |
| **Identity** | `iam_policies` | `name`, `document`, `managed` | AWS JSON policy definitions |
| **Compute** | `compute_instances`| `instance_id`, `state`, `instance_type`, `private_ip`, `cpu_usage`| Virtual machine state |
| **Storage** | `iam_buckets` | `name`, `account_id`, `total_size_bytes`, `object_count` | S3 bucket metadata |
| **Storage** | `storage_objects` | `bucket_id`, `object_key`, `size_bytes`, `etag`, `content_type`| S3 object metadata |
| **Serverless**| `lambda_functions` | `name`, `runtime`, `handler`, `timeout_seconds`, `memory_mb` | Function definitions |
| **Serverless**| `s3_lambda_triggers`| `bucket_name`, `function_name`, `event_type` | Event routing rules |
| **Database** | `rds_instances` | `instance_identifier`, `engine`, `status`, `endpoint` | Managed databases |
| **Network** | `vpcs`, `subnets` | `cidr_block`, `vpc_id`, `state`, `route_table_id` | Software-defined network |
| **Audit** | `audit_events` | `timestamp`, `principal`, `service`, `action`, `resource_arn` | CloudTrail audit ledger |
| **Billing** | `billing_records` | `account_id`, `service`, `amount`, `period_start`, `period_end`| Cost records |

---

## 🗄️ Concurrency & Resource Model

```
+-------------------------------------------------------------------+
|                        Concurrency Architecture                   |
|                                                                   |
|   [ Inbound HTTP / WS Requests ]                                  |
|                 |                                                 |
|                 v                                                 |
|   [ Spring Thread Pool: Core 4, Max 8, Queue 50 ]                 |
|                 |                                                 |
|        +--------+--------+                                        |
|        v                 v                                        |
|  [ JPA / HikariCP ]  [ Async TaskExecutor ]  [ Scheduled Tasks ]  |
|  (Max 5, Min 2)      (Lambda Dispatch)       (Metric Sampling)    |
|        |                 |                          |             |
|        +-----------------+--------------------------+             |
|                          v                                        |
|           [ H2 Database / PostgreSQL Engine ]                     |
+-------------------------------------------------------------------+
```

- **HikariCP Connection Pool**: Configured with 5 connections maximum for optimized laptop footprint (<250MB heap RAM).
- **Caffeine L2 Cache**: High-performance in-memory cache for policy resolution and IAM lookups (500 max entries, 300s TTL).
- **Graceful Shutdown**: Lifecycle timeout ensures executing Lambda tasks and database transactions flush cleanly before termination.
