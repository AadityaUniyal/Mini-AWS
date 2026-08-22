# 🏛️ MiniCloud: Architecture & Engineering Deep-Dive

MiniCloud is engineered as a high-performance **Modular Monolith** in Java 17 using **Spring Boot 3.2.5** and **FlatLaf Swing**. This document outlines the architectural paradigms, internal subsystems, thread model, data pipeline, and security mechanisms that enable accurate, local AWS emulation.

---

## 📐 Architectural Design Principles

1. **Modular Monolith Topology**: All AWS services (Compute, Storage, Serverless, IAM, Network, Telemetry) are modeled as decoupled domains with clean interfaces, compiled into a single executable artifact (`minicloud-api-1.0.0.jar`).
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
|  |    Lambda Serverless   |  |   Telemetry & Advisor  |  |   Network & Security    |  |
|  | * Polyglot Runners    |  | * OSHI Hardware Probes |  | * CIDR IP Allocator     |  |
|  | * S3 Event Trigger     |  | * Rightsizing Advisor  |  | * Stateful Sec Groups   |  |
|  | * WS Log Broadcast     |  | * CloudWatch Alarms    |  | * Stateless NACLs       |  |
|  +------------------------+  +------------------------+  +-------------------------+  |
+---------------------------------------------------------------------------------------+
```

---

### 1. Identity & Access Management (IAM) Engine

MiniCloud replicates AWS IAM evaluation logic:
- **Account Hierarchy**: Every tenant has a 12-digit AWS Account ID. The account owner is the `Root User`. Sub-users are `IAM Users`.
- **AWS Policy Evaluator**:
  - Accepts standard AWS JSON policy documents containing `Statement` arrays with `Effect` (`Allow`/`Deny`), `Action` (e.g., `s3:GetObject`, `ec2:*`), and `Resource` ARNs (e.g., `arn:aws:s3:::my-bucket/*`).
  - Implements **Explicit Deny Precedence**: If any policy denies an action, access is rejected even if another policy allows it.
  - Supports context variable expansion such as `${aws:username}` and `${aws:PrincipalAccount}`.
- **CloudTrail Audit Recorder**: Every authenticated API request logs an immutable event (`audit_events` table) recording timestamp, user ID, client IP, action, resource ARN, and request parameters.

---

### 2. Compute Engine (EC2 & Auto Scaling)

- **Lifecycle State Machine**: Transitions instances across `PENDING ➔ RUNNING ➔ STOPPING ➔ STOPPED ➔ TERMINATED`.
- **Process Sandbox**:
  - Instances run with CPU and memory quotas.
  - Commands sent via instance console are executed inside isolated Docker containers or sandboxed subprocesses with command sanitization preventing host escape.
- **Auto Scaling & Chaos Self-Healing**:
  - Background scheduler (`eval-interval-ms=60000`) continuously queries target groups and compute instance health.
  - When an instance is terminated unexpectedly or via Chaos Engineering (`/api/v1/chaos/terminate-random-instance`), the loop detects the capacity deficit and launches a replacement instance automatically.

---

### 3. Storage Engine (S3) & Event Trigger Dispatcher

- **Storage Structure**:
  - Buckets are created as subdirectories under `minicloud-data/storage/<bucket-name>/`.
  - Object metadata, content types, ETags (MD5 checksums), and size bytes are persisted in relational storage while byte payloads are stored on disk.
- **S3-to-Lambda Event Dispatcher**:
  - Triggers are registered in `s3_lambda_triggers` table mapping a source bucket and event type (`OBJECT_CREATED`, `OBJECT_DELETED`) to a target Lambda function.
  - `S3TriggerService` detects uploads and constructs an AWS-compliant JSON payload:
    ```json
    {
      "Records": [
        {
          "eventVersion": "2.1",
          "eventSource": "aws:s3",
          "eventName": "ObjectCreated:Put",
          "s3": {
            "bucket": { "name": "my-bucket", "arn": "arn:aws:s3:::my-bucket" },
            "object": { "key": "data.csv", "size": 1024, "eTag": "d41d8cd98f00b204e9800998ecf8427e" }
          }
        }
      ]
    }
    ```
  - The dispatcher asynchronously invokes the Lambda runner in a background worker thread (`TaskExecutor`), preventing upload blocking.

---

### 4. Serverless Execution Engine (AWS Lambda)

- **Polyglot Runtime Support**:
  - **Python** (`python3.9`, `python3.11`)
  - **Node.js** (`nodejs18.x`, `nodejs20.x`)
  - **Java** (`java17`)
  - **Bash / Shell** (`provided.al2`)
- **Execution Isolation**:
  - In Docker mode, Lambda spins up a runtime container mounting function code to `/var/task` with ephemeral execution limits.
  - In Host mode, code is written to isolated temporary execution directories (`minicloud-data/lambda-tmp/`) and executed with timeout safeguards.
- **Real-Time WebSocket Output**:
  - Stderr/stdout output from functions is published to WebSocket topic `/topic/lambda-logs/{functionName}` for live console debugging.

---

### 5. Telemetry & Cost Rightsizing Advisor

- **Hardware Telemetry (OSHI)**:
  - Background probe samples system-wide and process-level CPU, RAM, Disk I/O, and Thread counts every 60 seconds.
  - Metrics are written to `system_metrics` and exposed via `/actuator/metrics`.
- **Rightsizing Algorithm**:
  - Samples historical CPU and memory utilization across active instances.
  - An instance is flagged as `DOWNSIZE` if its average CPU utilization is below 10.0% over active sampling windows.
  - Calculates predicted monthly cost savings based on instance family delta (e.g., `t2.large` ($0.0928/hr) ➔ `t2.nano` ($0.0058/hr)).

---

## 🗄️ Database & Concurrency Model

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

- **HikariCP Connection Pool**: Configured with 5 connections maximum for optimized laptop footprint.
- **Caffeine L2 Cache**: High-performance in-memory cache for policy resolution and IAM lookups (500 max entries, 300s TTL).
- **Graceful Shutdown**: Lifecycle timeout ensures executing Lambda tasks and database transactions flush cleanly before termination.
