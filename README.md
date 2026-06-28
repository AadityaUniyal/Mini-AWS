# ☁️ MiniCloud: AWS-Equivalent Desktop Cloud Platform

MiniCloud is a production-grade, self-hosted simulation of **Amazon Web Services (AWS)**. It runs entirely on your local laptop as a **Modular Monolith** using Spring Boot and Java Swing—providing a complete AWS-equivalent cloud ecosystem without any expensive remote servers or complicated infrastructure.

---

## 📂 Repository & Project Structure

The project is organized into structured packages separating AWS simulation services, database mapping layers, and the desktop user interface.

```
Mini-AWS/
├── minicloud-api/                        ← Core Spring Boot Application Module
│   ├── src/main/java/com/minicloud/api/
│   │   ├── MiniCloudApiApplication.java  ← Main entry point (WEB / DESKTOP mode selector)
│   │   ├── auth/                         ← Security configurations, filters & JWT utilities
│   │   ├── audit/                        ← CloudTrail-style audit log recorder (immutable events)
│   │   ├── billing/                      ← Cost accumulation engine & invoicing system
│   │   ├── compute/                      ← EC2 simulation (VM manager, security groups, command sanitizer)
│   │   ├── domain/                       ← JPA entities mapping to H2/MySQL tables
│   │   ├── dto/                          ← REST request & response payloads
│   │   ├── iam/                      ← IAM users, access keys, policies & PolicyEvaluator
│   │   ├── lambda/                   ← Serverless engines (Subprocess & Docker runner)
│   │   ├── monitoring/               ← Diagnostics, CloudWatch metrics, alarms & auto-scaling
│   │   ├── rds/                      ← Managed H2 database instances per account
│   │   ├── route/                    ← Network (VPC, Subnet, Route53, ProxyService, NACL)
│   │   ├── storage/                  ← S3 bucket storage & static website hosting
│   │   └── ui/                       ← FlatLaf-styled Java Swing Desktop UI console
│   ├── src/main/resources/
│   │   ├── application.properties        ← Central application config parameters
│   │   └── db/migration/                 ← Database migration scripts (Flyway)
│   └── pom.xml                           ← API module Maven dependencies
├── pom.xml                               ← Parent Maven project configuration
├── start-desktop.bat                     ← Batch launcher script (Swing Dashboard UI)
├── start.bat                             ← Multi-mode launcher (WEB or DESKTOP)
├── setup-minicloud.ps1                   ← Initial setup & provisioning script
└── LICENSE                               ← MIT License
```

---

## 🎯 Key Capabilities & Parity

MiniCloud implements real-world AWS behaviors and patterns:

*   **🔐 Identity & Access Management (IAM)**: Authenticate as the Root user (email) or an IAM User (12-digit Account ID + username). Evaluate access permissions using AWS-style JSON Policy documents (supporting `Effect`, `Action`, `Resource`, `Condition` blocks, and context variables like `${aws:username}`).
*   **💻 Elastic Compute Cloud (EC2)**: Launch, stop, and terminate instance runtimes. Commands are executed inside a secure, lightweight **Docker Alpine container sandbox** with automatic fallback to local host subprocesses if Docker is inactive.
*   **⚡ AWS Lambda**: Upload code scripts (Python, Node.js, Java, Go, Bash, etc.) and run them on-demand inside isolated Docker runtime environments (mounting directories to `/var/task`) or local interpreters.
*   **🪣 Simple Storage Service (S3)**: Programmatically create buckets, upload/retrieve objects, and configure S3 Static Website Hosting (serving HTML documents at `/site/...`).
*   **🌐 Virtual Private Cloud (VPC)**: Segment infrastructure across VPC CIDRs, subnets, Route 53 DNS records, and enforce security boundaries using **stateful Security Groups** and **stateless Network ACLs (NACLs)**.
*   **📊 CloudWatch & CloudTrail**: Track real-time OS CPU/RAM sampling, configure threshold alarms, view rolling log streams, and audit every single action in immutable audit logs.
*   **💸 Billing**: Real-time cost accumulation engine tracking compute uptime, storage footprints, and invoice generations.

---

## 🚀 Quick Start Guide

### 1. Prerequisites
Ensure you have the following installed on your laptop:
- **Java 17+** (OpenJDK)
- **Maven** (optional, wrapper is included)
- **Docker Desktop** (optional, recommended for sandboxing isolation)
- **MySQL Server** (optional, default configuration runs on a file-based H2 database out of the box)

### 2. Configure Database (Optional)
By default, the application runs on a local H2 database (`miniclouddb` files inside `minicloud-data/db`). If you prefer to run on MySQL:
1. Open your database config, create a scheme named `minicloud_db`.
2. Edit configuration parameters inside [application.properties](file:///c:/Users/HP/OneDrive/Desktop/MINI-AWS/Mini-AWS/minicloud-api/src/main/resources/application.properties) to point to your local MySQL datasource URL.

### 3. Run the Platform

To start the platform in **Desktop Dashboard Mode** (Java Swing GUI):
```bash
# Using the launcher batch script
./start-desktop.bat

# Or run manually via maven
./mvnw.cmd spring-boot:run -pl minicloud-api -Dspring-boot.run.arguments=--mode=DESKTOP
```

To run in **Headless Web API Mode** (accessible via Swagger):
```bash
./mvnw.cmd spring-boot:run -pl minicloud-api
```

---

## ⚙️ Diagnostics & Advanced Security Operations

### Startup Diagnostics
During boot, MiniCloud automatically runs the environment checker, outputting a diagnostic status block of your tools in the startup console:
```
==================================================
         MINICLOUD ENVIRONMENT DIAGNOSTICS        
==================================================
  DOCKER     : ✅ ACTIVE
  PYTHON     : ✅ ACTIVE
  NODE       : ✅ ACTIVE
  JAVA       : ✅ ACTIVE
  RUBY       : ❌ INACTIVE (Fallback Mode)
  GO         : ❌ INACTIVE (Fallback Mode)
  DOTNET     : ❌ INACTIVE (Fallback Mode)
==================================================
```
You can query this dynamically via `GET /api/v1/diagnostics`.

### Sandbox Command Sanitization
Any command sent to start an EC2 instance or execute functions is automatically processed by `CommandSanitizer`. It prevents remote shell injections by blocking dangerous characters (like `;`, `&&`, `||`, backticks, `$()`).

### Subnet NACL Enforcement
Traffic passing through MiniRoute to your virtual resources traverses stateless subnet boundaries:
- By default, all subnets are allocated an "Allow All" (Rule 100) NACL.
- You can dynamically add rules (e.g. Rule 50: Deny protocol TCP, source port range 22-22 from `192.168.1.0/24`) using `NetworkAclService` to isolate subnet components.
