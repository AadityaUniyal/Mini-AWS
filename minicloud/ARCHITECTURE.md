# MiniCloud — Architecture Overview

MiniCloud is a production-grade **AWS-equivalent cloud platform** built as a **Modular Monolith** on Spring Boot 3.2. It runs entirely in a single JVM process, optimized for laptop hardware (~512MB RAM), with a dual-mode startup: headless web service or full Swing desktop UI.

---

## Project Structure

```
minicloud/
├── minicloud-api/                        ← Single deployable Spring Boot JAR
│   ├── src/main/java/com/minicloud/api/
│   │   ├── MiniCloudApiApplication.java  ← Entry point (WEB / DESKTOP mode)
│   │   ├── auth/                         ← JWT filter + UserPrincipal
│   │   ├── audit/                        ← CloudTrail-style event logging
│   │   ├── billing/                      ← Cost accumulation + invoicing
│   │   ├── compute/                      ← EC2 instances + security groups
│   │   ├── config/                       ← Spring Security, JPA, Cache, WebSocket
│   │   ├── domain/                       ← JPA entities + Spring Data repositories
│   │   ├── dto/                          ← Request/response DTOs
│   │   ├── exception/                    ← Global exception handler
│   │   ├── iam/                          ← Auth, IAM users, policies, access keys
│   │   ├── lambda/                       ← Serverless function execution engine
│   │   ├── monitoring/                   ← CloudWatch metrics, alarms, auto-scaling
│   │   │   └── logs/                     ← CloudWatch Logs (streams + events)
│   │   ├── rds/                          ← Managed H2 database lifecycle
│   │   ├── route/                        ← VPC, Route53, reverse proxy (MiniRoute)
│   │   ├── storage/                      ← S3 buckets, objects, static website hosting
│   │   └── ui/                           ← Swing desktop UI (FlatLaf dark theme)
│   │       └── panels/                   ← EC2, S3, Lambda, RDS, IAM, Billing panels
│   ├── src/main/resources/
│   │   ├── application.properties        ← All configuration
│   │   ├── db/migration/                 ← Flyway SQL migrations (V1, V2)
│   │   └── static/                       ← Web frontend (HTML/CSS/JS)
│   └── pom.xml
├── pom.xml                               ← Parent Maven build
├── mvnw / mvnw.cmd                       ← Maven wrapper
└── start.bat                             ← One-click launcher (WEB or DESKTOP)
```

---

## Startup Modes

MiniCloud supports two startup modes resolved from `--mode=` CLI arg or `MINICLOUD_MODE` env var:

| Mode | Description | How to start |
|------|-------------|--------------|
| `WEB` (default) | Headless REST API + system tray icon | `start.bat` or `--mode=WEB` |
| `DESKTOP` | Swing dashboard + embedded API | `start.bat desktop` or `--mode=DESKTOP` |

---

## AWS Service Parity

| MiniCloud | AWS Equivalent | Endpoint Prefix | Notes |
|-----------|---------------|-----------------|-------|
| `AuthService` / `IamService` | IAM | `/auth`, `/api/iam` | JWT tokens, users, policies, access keys |
| `ComputeService` | EC2 | `/api/compute/instances` | Launch/stop/terminate instances |
| `SecurityGroupController` | EC2 Security Groups | `/api/compute/security-groups` | Ingress/egress rules |
| `StorageController` | S3 | `/storage` | Buckets, upload, download, delete |
| `WebsiteController` | S3 Static Hosting | `/site` | SPA mode, index/error documents |
| `RdsService` | RDS | `/rds/instances` | Embedded H2 per database instance |
| `LambdaController` | Lambda | `/lambda` | BASH, Python, Node, Java, Go, Ruby, .NET |
| `AuditService` | CloudTrail | `/monitoring/audit` | Immutable event log with userId |
| `MetricsService` | CloudWatch Metrics | `/monitoring` | OSHI-based CPU/RAM/disk sampling |
| `AlarmService` | CloudWatch Alarms | `/monitoring/alarms` | Threshold-based metric alarms |
| `LogService` | CloudWatch Logs | `/api/logs` | Log streams + events |
| `AutoScalingService` | Auto Scaling | `/scaling` | CPU-based horizontal scaling decisions |
| `BillingService` | AWS Billing | `/api/billing` | Per-minute cost accumulation |
| `VpcService` / `NetworkController` | VPC | `/api/vpc` | VPCs + subnets per account |
| `Route53Service` | Route 53 | `/api/route53` | Hosted zones + DNS records |
| `ProxyService` | ALB / API Gateway | `/routes`, `/proxy` | Reverse proxy with health checks |
| `TenantService` | AWS Organizations | `/tenants` | Multi-tenant quota enforcement |

---

## Technology Stack

| Layer | Technology |
|-------|-----------|
| Runtime | Java 17, Spring Boot 3.2.5 |
| Security | Spring Security 6 + JJWT 0.12 (HS256 JWT) |
| Database | H2 (dev, file-based) / MySQL / PostgreSQL |
| ORM | Hibernate 6 + Spring Data JPA + Flyway |
| Connection Pool | HikariCP (2× CPU cores pool size) |
| Naming Strategy | `CamelCaseToUnderscoresNamingStrategy` |
| Caching | Caffeine (in-memory, no Redis required) |
| API Docs | SpringDoc OpenAPI 2.5 at `/swagger-ui.html` |
| Real-time | Spring WebSocket (STOMP + raw WS handler) |
| Metrics | OSHI 6.6 (OS-level CPU/RAM/disk) |
| Desktop UI | Swing + FlatLaf 3.4 (AWS dark theme) |
| Build | Maven 3 + Maven Wrapper |

---

## Database Schema

Managed by Flyway. Two migrations:

- **V1** — Full initial schema (20+ tables, all aligned with JPA entity `@Table` names)
- **V2** — Makes `monitoring_audit_logs.user_id` nullable

Key naming conventions:
- IAM tables: `iam_users`, `iam_policies`, `iam_access_keys`, `iam_buckets`
- Compute: `compute_instances`, `compute_security_groups`, `compute_security_group_rules`
- Storage: `storage_objects`, `storage_object_metadata`
- Lambda: `lambda_functions`, `lambda_invocation_logs`
- Monitoring: `monitoring_alarms`, `monitoring_audit_logs`
- Networking: `vpc_networks`, `vpc_subnets`, `routes`, `route53_hosted_zones`, `route53_records`
- Billing: `billing_records`, `billing_invoices`
- Logs: `log_streams`, `log_events`

---

## Security

- All endpoints require JWT Bearer token except: `/auth/**`, `/h2-console/**`, `/swagger-ui/**`, `/actuator/**`, `/site/**`, `/lambda/invoke/**`, `/ws-events/**`
- CORS enabled for all origins (configurable for production)
- BCrypt password hashing
- `@PreAuthorize("hasRole('ADMIN')")` on admin-only endpoints
- Policy evaluation via AWS-style JSON policy documents (Allow/Deny/Effect)

---

## Key Configuration (`application.properties`)

```properties
server.port=8080
spring.datasource.url=jdbc:h2:file:./minicloud-data/db/miniclouddb
minicloud.jwt.secret=<change-in-production>
minicloud.jwt.expiry-ms=3600000
minicloud.storage.base-path=./minicloud-data/storage
minicloud.lambda.tmp-dir=./minicloud-data/lambda-tmp
minicloud.h2.tcp.port=9092          # External H2 TCP access
spring.flyway.enabled=true
spring.cache.type=caffeine
server.shutdown=graceful
```

---

## Running Locally

```bash
# Quick start (interactive mode selection)
start.bat

# Web service mode
start.bat web

# Desktop UI mode
start.bat desktop

# Manual Maven run
.\mvnw.cmd spring-boot:run -pl minicloud-api

# With explicit mode
.\mvnw.cmd spring-boot:run -pl minicloud-api -Dspring-boot.run.arguments=--mode=DESKTOP
```

**Endpoints after startup:**

| URL | Description |
|-----|-------------|
| `http://localhost:8080` | Web Management Console |
| `http://localhost:8080/swagger-ui.html` | API Documentation |
| `http://localhost:8080/h2-console` | Database Browser (JDBC: `jdbc:h2:file:./minicloud-data/db/miniclouddb`) |
| `http://localhost:8080/actuator/health` | Health Check |
| `http://localhost:9092` | H2 TCP Server (external JDBC access) |

**Default credentials:**
- Email: `admin@minicloud.io`
- Password: `admin123`
- Account ID: `123456789012`

---

## Data Directory

All runtime data is stored under `minicloud-api/minicloud-data/`:

```
minicloud-data/
├── db/           ← H2 database files
├── logs/         ← Rolling application logs (10MB, 30 days)
├── storage/      ← S3 object storage (file system)
├── lambda-tmp/   ← Lambda function artifact cache
└── rds/          ← Per-instance H2 RDS databases
```
