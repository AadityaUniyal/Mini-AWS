# ☁️ MiniCloud: Self-Hosted AWS-Equivalent Cloud Platform

[![Java](https://img.shields.io/badge/Java-17%20LTS-ED8B00?logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.5-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Docker](https://img.shields.io/badge/Docker-Enabled-2496ED?logo=docker&logoColor=white)](https://www.docker.com/)
[![FlatLaf](https://img.shields.io/badge/UI-Java%20Swing%20%2B%20FlatLaf-blue)](https://www.formdev.com/flatlaf/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

**MiniCloud** is an enterprise-grade, self-hosted simulation of **Amazon Web Services (AWS)** packaged as a **Modular Monolith**. It runs completely on your local machine with zero external cloud dependencies or surprise bills, providing full architectural and functional parity with core AWS services—including **IAM, EC2, S3, RDS, Lambda, VPC, CloudWatch, Billing, Chaos Engineering, and Rightsizing Recommendations**.

---

## 🏗️ High-Level System Architecture

```
                                  +-------------------------------------------------------+
                                  |                     Client Layer                      |
                                  |  +------------------------+  +---------------------+  |
                                  |  | Java Swing Desktop App |  | Python CLI / cURL   |  |
                                  |  | (FlatLaf Dark Theme)   |  | (Click + Rich UI)   |  |
                                  |  +-----------+------------+  +----------+----------+  |
                                  +--------------|--------------------------|-------------+
                                                 | REST / WebSocket         |
                                                 v                          v
+-----------------------------------------------------------------------------------------+
|                               MiniCloud Core Engine (Port 8080)                         |
|                                                                                         |
|  +-----------------------------------------------------------------------------------+  |
|  |                                  Security & IAM                                   |  |
|  |  [ JWT Authentication Filter ]  [ AWS JSON Policy Evaluator ]  [ CloudTrail Audit]|  |
|  +-----------------------------------------------------------------------------------+  |
|                                                                                         |
|  +---------------------+  +---------------------+  +---------------------------------+  |
|  |   Compute (EC2)     |  |   Storage (S3)      |  |      Serverless (Lambda)        |  |
|  |  * Lifecycle Engine |  |  * Bucket Manager   |  |  * Docker & Host Runtimes       |  |
|  |  * Process Sandbox  |  |  * Static Web Host  |  |  * S3 Async Event Dispatcher    |  |
|  |  * Auto Scaling     |  |  * Multipart Upload |  |  * WebSocket Log Streaming      |  |
|  +---------------------+  +---------------------+  +---------------------------------+  |
|                                                                                         |
|  +---------------------+  +---------------------+  +---------------------------------+  |
|  |   Database (RDS)    |  |    Network (VPC)    |  |    Observability & Intel        |  |
|  |  * Managed Instances|  |  * Subnets & CIDRs  |  |  * CloudWatch Metric Alarms     |  |
|  |  * Storage Engine   |  |  * Security Groups  |  |  * Rightsizing Advisor Engine   |  |
|  |  * Auto Backups     |  |  * Network ACLs     |  |  * Chaos Failure Injection      |  |
|  +---------------------+  +---------------------+  +---------------------------------+  |
|                                                                                         |
|  +-----------------------------------------------------------------------------------+  |
|  |                        Persistence & Real-Time Telemetry                          |  |
|  |  [ Flyway Schema (V1, V2, V3) ]  [ H2 Zero-Install Engine / Neon PostgreSQL ]     |  |
|  +-----------------------------------------------------------------------------------+  |
+-----------------------------------------------------------------------------------------+
```

---

## 🌟 Standout Capabilities

MiniCloud offers standout capabilities that bridge simulation and production readiness:

### 1. 🪣 ➔ ⚡ S3-to-Lambda Asynchronous Event Triggers
- Link bucket events (`OBJECT_CREATED`, `OBJECT_DELETED`) directly to Lambda function invocations.
- Upon file upload, an event payload matching the standard AWS S3 event schema is asynchronously dispatched.
- Execution logs (stdout/stderr) are streamed in real-time over WebSockets to connected listeners.

### 2. 🤖 Telemetry-Driven Cost & Rightsizing Advisor
- Rolling CPU metrics gathered via OSHI and synthetic usage metrics are evaluated against cost models.
- Generates actionable rightsizing recommendations (`GET /api/v1/billing/recommendations`), flagging underutilized instances (<10% average CPU) with exact dollar savings.

### 3. 💥 Chaos Engineering & Self-Healing Auto-Scaling
- Trigger controlled failure injections (`POST /api/v1/chaos/terminate-random-instance`).
- The autonomous Auto Scaling background loop detects capacity deficits, spins up healthy replacements, and broadcasts recovery events via WebSocket.

### 4. 💻 Ergonomic Python CLI (`minicloud`)
- Full-featured command-line utility built with Python, Click, and Rich.
- Interactive commands for EC2, S3, Lambda, Billing, Chaos testing, and System status.

### 5. 🐳 1-Command Docker Compose Setup
- Single command `docker compose up` brings up the complete serverless cloud monolith in containerized mode with volume persistence and health checks.

---

## 📂 Repository Structure

```
Mini-AWS/
├── docker-compose.yml              ← 1-command containerized cloud monolith deployment
├── pom.xml                         ← Root Maven BOM & dependency management
├── start.bat                       ← Multi-mode interactive launcher (WEB / DESKTOP)
├── start-desktop.bat               ← Dedicated Java Swing Desktop UI launcher
├── start.sh                        ← Linux / macOS shell launcher
├── minicloud-api/                  ← Core Spring Boot Cloud Platform
│   ├── Dockerfile                  ← Multi-stage production container build
│   ├── pom.xml                     ← Spring Boot module dependencies & repackage config
│   └── src/
│       ├── main/
│       │   ├── java/com/minicloud/api/
│       │   │   ├── audit/          ← CloudTrail immutable event logger
│       │   │   ├── auth/           ← Spring Security, JWT filters & token provider
│       │   │   ├── billing/        ← Cost ledger & Rightsizing Advisor
│       │   │   ├── chaos/          ← Chaos Engineering failure injection service
│       │   │   ├── compute/        ← EC2 VM manager & Docker sandbox
│       │   │   ├── domain/         ← JPA domain entities
│       │   │   ├── iam/            ← IAM accounts, users, policies & evaluator
│       │   │   ├── lambda/         ← Serverless execution engine & S3 triggers
│       │   │   ├── monitoring/     ← CloudWatch metrics, alarms & OSHI probes
│       │   │   ├── rds/            ← Managed database instance manager
│       │   │   ├── route/          ← VPC, Subnets, Route 53 & Security Groups
│       │   │   ├── storage/        ← S3 object storage & trigger dispatcher
│       │   │   └── ui/             ← FlatLaf AWS Dark/Light Java Swing Console
│       │   └── resources/
│       │       ├── application.properties ← Default H2 zero-install config
│       │       └── db/migration/   ← Flyway database migrations (V1, V2, V3)
│       └── test/                   ← Comprehensive unit & integration test suites
├── minicloud-cli/                  ← Interactive Python CLI package
│   ├── pyproject.toml              ← CLI package manifest
│   ├── README.md                   ← CLI command reference
│   └── minicloud/                  ← CLI command implementations
└── e2e-tests/                      ← End-to-end integration test suites
```

---

## 🚀 Quick Start Guide

### Prerequisites
- **Java 17 LTS** (OpenJDK / Eclipse Adoptium / Oracle JDK)
- **Maven 3.8+** (or use included `./mvnw`)
- **Docker Desktop** (optional, recommended for sandboxed Lambda/EC2 containers)
- **Python 3.9+** (optional, for CLI and E2E tests)

---

### Option 1: Java Swing Desktop UI Mode (Recommended)

Run the desktop launcher:
```powershell
.\start-desktop.bat
```
*Launches the Spring Boot backend and pops up the AWS-styled Swing Desktop Management Console.*

---

### Option 2: Headless Web API Mode

Run headless web service (ideal for CI/CD, CLI access, or Swagger UI):
```bash
./mvnw clean compile spring-boot:run -pl minicloud-api
```

- **Swagger UI / OpenAPI**: [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)
- **Actuator Health**: [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health)

---

### Option 3: 1-Command Docker Compose

```bash
docker compose up --build
```
*Instantly boots the platform with H2 storage, port 8080 exposed, and persistent data volumes.*

---

## 🛠️ CLI Quick Start

```bash
cd minicloud-cli
pip install -e .

# Log in
minicloud auth login --username root --password password

# Check cloud status
minicloud status

# Launch an EC2 instance
minicloud ec2 launch --name test-vm --type t2.micro

# Upload to S3
minicloud s3 mb my-bucket
minicloud s3 cp README.md s3://my-bucket/README.md

# Inspect Rightsizing Recommendations
minicloud billing recommendations
```

---

## 📊 Core API Route Reference

| Service | Method | Path | Description |
|---|---|---|---|
| **Auth** | `POST` | `/api/v1/auth/login` | Authenticate and obtain JWT token |
| **Auth** | `POST` | `/api/v1/auth/register` | Register a new root account |
| **IAM** | `GET` | `/api/v1/iam/users` | List IAM users for active account |
| **IAM** | `POST` | `/api/v1/iam/policies` | Create AWS JSON policy |
| **EC2** | `GET` | `/api/v1/compute/instances` | List all compute instances |
| **EC2** | `POST` | `/api/v1/compute/instances` | Launch a new VM instance |
| **EC2** | `DELETE` | `/api/v1/compute/instances/{id}` | Terminate an EC2 instance |
| **S3** | `GET` | `/api/v1/storage/buckets` | List all S3 buckets |
| **S3** | `POST` | `/api/v1/storage/buckets/{name}/objects` | Upload S3 object |
| **Lambda** | `GET` | `/api/v1/lambda/functions` | List serverless functions |
| **Lambda** | `POST` | `/api/v1/lambda/functions/{name}/invoke` | Synchronously invoke a function |
| **Lambda** | `POST` | `/api/v1/lambda/triggers` | Create S3-to-Lambda trigger |
| **Billing** | `GET` | `/api/v1/billing/summary` | Get aggregated account cost ledger |
| **Billing** | `GET` | `/api/v1/billing/recommendations` | Get telemetry rightsizing advice |
| **Chaos** | `POST` | `/api/v1/chaos/terminate-random-instance` | Inject random instance termination |
| **Health** | `GET` | `/actuator/health` | Service health & component probes |

---

## 🧪 Running Automated Tests

Run the full Maven test suite (Unit & Integration tests):
```bash
./mvnw clean test
```

Run Python CLI tests:
```bash
cd minicloud-cli
pytest
```

---

## 📄 License
This project is open-source software licensed under the [MIT License](LICENSE).
