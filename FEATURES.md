# ⚡ MiniCloud: Complete Features & Parity Matrix

This document provides a comprehensive specification of every cloud capability simulated within **MiniCloud**, including technical parameters, API routes, JSON payloads, and real-world AWS equivalents.

---

## 📋 Comprehensive Feature Matrix

| Domain | AWS Service | MiniCloud Simulation Feature | Status |
|---|---|---|---|
| **Identity** | AWS IAM | Root vs IAM Users, JSON Policies, Access Keys, RBAC | ✅ Complete |
| **Compute** | Amazon EC2 | Virtual Machines, Instance Types, State Machine, Security Groups | ✅ Complete |
| **Compute** | Auto Scaling | Capacity Management, Health Checks, Self-Healing Recovery | ✅ Complete |
| **Storage** | Amazon S3 | Buckets, Object Storage, Static Web Hosting, Pre-signed URLs | ✅ Complete |
| **Serverless** | AWS Lambda | Polyglot Runtimes, Synchronous/Async Invocations, WebSocket Logs | ✅ Complete |
| **Eventing** | EventBridge / S3 Events | Asynchronous S3-to-Lambda Event Triggers | ✅ Complete |
| **Database** | Amazon RDS | Managed H2/MySQL Engines, Automated Backups, Read Replicas | ✅ Complete |
| **Networking** | Amazon VPC | CIDR Block Allocation, Subnets, Route Tables, Route 53 DNS | ✅ Complete |
| **Security** | Security Groups & NACLs | Stateful firewall rules & Stateless subnet packet filters | ✅ Complete |
| **Monitoring** | Amazon CloudWatch | Rolling Metric Aggregations, Alarm Thresholds, Notifications | ✅ Complete |
| **Auditing** | AWS CloudTrail | Immutable Audit Event Logging, IP/Principal Tracing | ✅ Complete |
| **FinOps** | Cost Explorer | Real-time Cost Ledger, Daily Accrual, Invoice Generation | ✅ Complete |
| **Intelligence**| Compute Optimizer | Rightsizing Advisor with OSHI CPU Telemetry | ✅ Complete |
| **Resilience** | Chaos Engineering | Targeted & Random Instance Termination Injections | ✅ Complete |
| **Tooling** | AWS CLI | Full-featured Python CLI (`minicloud`) with Click + Rich | ✅ Complete |
| **Desktop UI** | AWS Console | Pure Java Swing Desktop Dashboard with FlatLaf Theme | ✅ Complete |

---

## 🛠️ Feature Deep-Dives

### 1. Identity & Access Management (IAM)
- **Root Account Creation**: Register with root email and secure password. Receives a unique 12-digit AWS Account ID.
- **IAM Users**: Create sub-users with scoped permissions, individual login passwords, or API access keys.
- **JSON Policy Engine**: Supports full AWS Policy syntax:
```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": ["s3:GetObject", "s3:PutObject"],
      "Resource": "arn:aws:s3:::prod-bucket/*"
    },
    {
      "Effect": "Deny",
      "Action": "ec2:TerminateInstances",
      "Resource": "*"
    }
  ]
}
```

---

### 2. Elastic Compute Cloud (EC2)
- **Instance Types**:
  - `t2.nano` (1 vCPU, 0.5 GB RAM, $0.0058/hr)
  - `t2.micro` (1 vCPU, 1.0 GB RAM, $0.0116/hr)
  - `t2.small` (1 vCPU, 2.0 GB RAM, $0.0230/hr)
  - `t2.medium` (2 vCPU, 4.0 GB RAM, $0.0464/hr)
  - `t2.large` (2 vCPU, 8.0 GB RAM, $0.0928/hr)
- **Instance Lifecycle**: Launch (`POST /api/v1/compute/instances`), Stop (`POST /api/v1/compute/instances/{id}/stop`), Start (`POST /api/v1/compute/instances/{id}/start`), Terminate (`DELETE /api/v1/compute/instances/{id}`).
- **Sandboxed Execution**: Run interactive commands inside VM instances via REST endpoint `/api/v1/compute/instances/{id}/exec`.

---

### 3. Simple Storage Service (S3) & Event Triggers
- **Bucket Management**: Create, list, configure public read access, and delete buckets.
- **Object Operations**: Upload binary/text files, download files with MD5 ETag validation, and stream file contents.
- **Static Website Hosting**: Host single-page websites or static assets accessible at `http://localhost:8080/site/{bucket-name}/index.html`.
- **S3-to-Lambda Event Triggers**:
  - Create trigger: `POST /api/v1/lambda/triggers`
  - Payload:
    ```json
    {
      "bucketName": "media-uploads",
      "functionName": "image-resizer",
      "eventType": "OBJECT_CREATED"
    }
    ```
  - Automatically dispatches events to Lambda whenever files matching the event pattern are uploaded.

---

### 4. AWS Lambda (Serverless Compute)
- **Supported Runtimes**: Python (`python3.9`, `python3.11`), Node.js (`nodejs18.x`), Java (`java17`), Shell (`provided.al2`).
- **Synchronous Invocations**:
  - Endpoint: `POST /api/v1/lambda/functions/{name}/invoke`
  - Request Body: JSON payload passed as `event` parameter to the entry point handler.
  - Response: Function return value, execution duration (ms), memory consumed (MB), and billed duration.
- **Live Output Streaming**: Real-time console logs streamed over WebSocket (`/topic/lambda-logs/{name}`).

---

### 5. Telemetry, Rightsizing Advisor & Billing
- **Real-Time Cost Tracking**: Every second of compute instance uptime, gigabyte of S3 storage, and millisecond of Lambda execution is recorded in the billing ledger.
- **Rightsizing Advisor Endpoint**: `GET /api/v1/billing/recommendations`
  - Scans instances with average CPU < 10% and computes potential monthly cost reduction:
  ```json
  {
    "recommendations": [
      {
        "instanceId": "i-0a8b9c1d2e",
        "instanceName": "analytics-worker",
        "currentType": "t2.large",
        "recommendedType": "t2.nano",
        "averageCpuUtilization": 4.2,
        "currentMonthlyCost": 66.82,
        "projectedMonthlyCost": 4.18,
        "estimatedMonthlySavings": 62.64,
        "action": "DOWNSIZE",
        "reason": "Instance CPU utilization is below 10% over sampling window."
      }
    ],
    "totalPotentialMonthlySavings": 62.64
  }
  ```

---

### 6. Chaos Engineering & Self-Healing Auto Scaling
- **Chaos Injection Endpoint**: `POST /api/v1/chaos/terminate-random-instance`
  - Randomly selects and terminates an active instance belonging to an Auto Scaling Group.
- **Self-Healing Verification**:
  - The Auto Scaling evaluation engine detects the capacity drop during its periodic health evaluation cycle.
  - Automatically launches a replacement VM instance to maintain the desired instance count and broadcasts the recovery event over WebSocket.

---

### 7. Java Swing Desktop Console
- Built with **FlatLaf Dark** theming matching the modern AWS Management Console look and feel.
- Features:
  - Visual resource cards for Compute, S3, RDS, Lambda, and IAM.
  - Live system performance graphs (CPU load, Heap usage, Active threads).
  - Tabbed dashboards for launching instances, uploading files, inspecting policies, and monitoring billing.
