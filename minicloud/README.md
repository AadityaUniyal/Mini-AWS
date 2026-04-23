# ☁ MiniCloud

A self-hosted, AWS-equivalent cloud platform built entirely in Java. Runs as a single Spring Boot application on your laptop — no Docker, no Kubernetes, no cloud account required.

[![Java](https://img.shields.io/badge/Java-17-orange)](https://openjdk.org/projects/jdk/17/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.5-brightgreen)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/License-MIT-blue)](LICENSE)

---

## What is MiniCloud?

MiniCloud replicates the core AWS services as a modular monolith — one JAR, one database, one process. It ships with both a **REST API** (web service mode) and a **Swing desktop UI** (management console mode), styled after the AWS Management Console.

### Services

| MiniCloud | AWS Equivalent |
|-----------|---------------|
| IAM — users, policies, access keys | AWS IAM |
| EC2 — virtual instances + security groups | Amazon EC2 |
| S3 — buckets, objects, static website hosting | Amazon S3 |
| RDS — managed database instances (H2/MySQL) | Amazon RDS |
| Lambda — serverless function execution | AWS Lambda |
| CloudTrail — audit event log | AWS CloudTrail |
| CloudWatch — metrics, alarms, logs | Amazon CloudWatch |
| VPC — networks and subnets | Amazon VPC |
| Route 53 — hosted zones and DNS records | Amazon Route 53 |
| MiniRoute — reverse proxy with health checks | AWS ALB |
| Auto Scaling — CPU-based replica management | AWS Auto Scaling |
| Billing — per-minute cost accumulation | AWS Cost Explorer |
| Multi-tenancy — quota enforcement per tenant | AWS Organizations |

---

## Quick Start

**Requirements:** Java 17+, Maven (or use the included wrapper)

```bash
# Clone
git clone https://github.com/your-org/minicloud.git
cd minicloud

# Start (interactive — choose WEB or DESKTOP mode)
start.bat

# Or directly
.\mvnw.cmd spring-boot:run -pl minicloud-api
```

The platform starts on **http://localhost:8080** in about 10 seconds.

---

## Startup Modes

```bash
# Headless web service (default)
start.bat web

# Swing desktop UI
start.bat desktop
```

You can also set the `MINICLOUD_MODE` environment variable to `WEB` or `DESKTOP`.

---

## Default Credentials

| Field | Value |
|-------|-------|
| Email | `admin@minicloud.io` |
| Password | `admin123` |
| Account ID | `123456789012` |
| Login type | Root user |

> Change the JWT secret in `application.properties` before any real deployment.

---

## Endpoints

| URL | Description |
|-----|-------------|
| `http://localhost:8080` | Web Management Console |
| `http://localhost:8080/swagger-ui.html` | Full API documentation |
| `http://localhost:8080/h2-console` | Database browser |
| `http://localhost:8080/actuator/health` | Health check |
| `http://localhost:8080/auth/login` | Login (POST) |

---

## API Examples

**Login:**
```bash
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"loginType":"ROOT","email":"admin@minicloud.io","password":"admin123"}'
```

**Launch an EC2 instance:**
```bash
curl -X POST "http://localhost:8080/api/compute/instances/launch?name=my-vm&type=T2_MICRO&userId=<userId>&accountId=123456789012" \
  -H "Authorization: Bearer <token>"
```

**Create an S3 bucket:**
```bash
curl -X POST "http://localhost:8080/storage/buckets?name=my-bucket&userId=<userId>" \
  -H "Authorization: Bearer <token>"
```

**Upload a file:**
```bash
curl -X POST "http://localhost:8080/storage/buckets/my-bucket/upload?userId=<userId>" \
  -H "Authorization: Bearer <token>" \
  -F "file=@myfile.txt"
```

**Deploy a Lambda function:**
```bash
curl -X POST http://localhost:8080/lambda \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"name":"hello","runtime":"BASH","handler":"hello.sh","memoryMb":128,"timeoutSec":10}'
```

**Invoke a Lambda function (public):**
```bash
curl -X POST http://localhost:8080/lambda/invoke/hello \
  -d '{"key":"value"}'
```

---

## Project Structure

```
minicloud/
├── minicloud-api/                  ← Spring Boot application
│   ├── src/main/java/
│   │   └── com/minicloud/api/
│   │       ├── auth/               ← JWT filter
│   │       ├── audit/              ← CloudTrail
│   │       ├── billing/            ← Cost tracking
│   │       ├── compute/            ← EC2 + security groups
│   │       ├── config/             ← Security, JPA, cache, WebSocket
│   │       ├── domain/             ← JPA entities + repositories
│   │       ├── dto/                ← Request/response objects
│   │       ├── exception/          ← Global error handling
│   │       ├── iam/                ← Users, policies, access keys
│   │       ├── lambda/             ← Serverless execution engine
│   │       ├── monitoring/         ← Metrics, alarms, logs, auto-scaling
│   │       ├── rds/                ← Managed databases
│   │       ├── route/              ← VPC, Route53, reverse proxy
│   │       ├── storage/            ← S3 + static website hosting
│   │       └── ui/                 ← Swing desktop UI
│   ├── src/main/resources/
│   │   ├── application.properties
│   │   ├── db/migration/           ← Flyway SQL migrations
│   │   └── static/                 ← Web frontend
│   └── pom.xml
├── pom.xml                         ← Parent build
├── mvnw / mvnw.cmd                 ← Maven wrapper
├── start.bat                       ← Launcher script
├── ARCHITECTURE.md                 ← Detailed architecture docs
└── README.md                       ← This file
```

---

## Configuration

All configuration lives in `minicloud-api/src/main/resources/application.properties`.

**Switch to PostgreSQL:**
```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/minicloud
spring.datasource.username=postgres
spring.datasource.password=yourpassword
spring.datasource.driver-class-name=org.postgresql.Driver
```

**Switch to MySQL:**
```properties
spring.datasource.url=jdbc:mysql://localhost:3306/minicloud
spring.datasource.username=root
spring.datasource.password=yourpassword
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
```

**Change JWT secret (required for production):**
```properties
minicloud.jwt.secret=your-very-long-secret-key-at-least-32-chars
```

---

## Desktop UI

Launch with `start.bat desktop` to open the Swing management console — an AWS-styled dark-theme dashboard with panels for every service.

The UI communicates with the embedded REST API over `localhost:8080` using JWT authentication. All panels support full CRUD operations.

---

## Data Storage

Runtime data is stored under `minicloud-api/minicloud-data/`:

```
minicloud-data/
├── db/           ← H2 database files
├── logs/         ← Rolling application logs
├── storage/      ← S3 object files
├── lambda-tmp/   ← Lambda artifact cache
└── rds/          ← Per-instance RDS databases
```

---

## Building a Production JAR

```bash
.\mvnw.cmd clean package -pl minicloud-api -am -DskipTests
java -Xmx512m -jar minicloud-api/target/minicloud-api-1.0.0.jar --mode=WEB
```

---

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    MiniCloud Architecture                    │
│                     (Single JVM Process)                    │
└─────────────────────────────────────────────────────────────┘

┌──────────┐   ┌──────────┐   ┌──────────────────────────┐
│ Browser  │   │ Swing UI │   │       CLI Client         │
│ Web UI   │   │ Desktop  │   │  (java MiniCloudCli)     │
└────┬─────┘   └────┬─────┘   └────────────┬─────────────┘
     │              │                       │
     └──────────────┼───────────────────────┘
                    │  HTTP/REST + JWT
                    ▼
┌──────────────────────────────────────────────┐
│              Spring Boot 3.2                  │
│  ┌────────┐ ┌───────┐ ┌────────┐ ┌────────┐  │
│  │  IAM   │ │  EC2  │ │   S3   │ │ Lambda │  │
│  │ /auth  │ │/compute│ │/storage│ │/lambda │  │
│  └────────┘ └───────┘ └────────┘ └────────┘  │
│  ┌────────┐ ┌───────┐ ┌────────┐ ┌────────┐  │
│  │  RDS   │ │  VPC  │ │Billing │ │Monitor │  │
│  │  /rds  │ │ /vpc  │ │/billing│ │/metrics│  │
│  └────────┘ └───────┘ └────────┘ └────────┘  │
│                                               │
│  ┌──────────────────────────────────────┐    │
│  │    H2 File Database (via JDBC)        │    │
│  │    Flyway-managed schema migrations   │    │
│  └──────────────────────────────────────┘    │
└──────────────────────────────────────────────┘
                    │                         │
┌──────┴──────┐         ┌────────┴────────┐
│ minicloud-  │         │  minicloud-     │
│ data/db/    │         │  data/storage/  │
│ (database)  │         │  (S3 files)     │
└─────────────┘         └─────────────────┘
```

**Key Design Patterns:**
- **Modular Monolith** — All AWS services in one JVM process
- **Repository Pattern** — Spring Data JPA abstracts database access
- **Factory Pattern** — Instance type selection and resource creation
- **State Machine** — EC2 lifecycle (PENDING→RUNNING→STOPPED→TERMINATED)
- **Observer Pattern** — WebSocket event broadcasting for real-time updates
- **Strategy Pattern** — Different billing rates per resource type

See [ARCHITECTURE.md](ARCHITECTURE.md) for the full technical architecture, database schema, security model, and service mapping.
