# 💻 MiniCloud CLI (`minicloud`)

A modern, ergonomic command-line interface for **MiniCloud (Mini-AWS)** built with Python, Click, and Rich. Manage compute instances, S3 storage buckets, Lambda functions, billing insights, chaos engineering, and system telemetry directly from your terminal.

---

## 🚀 Installation & Setup

### 1. Requirements
- Python 3.9+
- Running MiniCloud backend (`http://localhost:8080` by default)

### 2. Local Installation
```bash
cd minicloud-cli
pip install -e .
```

Verify installation:
```bash
minicloud --help
```

---

## 🛠️ Global Configuration & Authentication

Configure your backend URL and authentication credentials:

```bash
# Login to MiniCloud
minicloud auth login --username root --password password

# Check active profile & session info
minicloud auth whoami

# Configure custom endpoint
minicloud config set-url http://localhost:8080
```

---

## 📋 Available Commands & Features

### 1. 💻 Compute (EC2)
Manage virtual machines, state transitions, and security groups.

```bash
# Launch a new EC2 instance
minicloud ec2 launch --name web-server --type t2.micro --ami alpine-3.18

# List all instances
minicloud ec2 list

# Stop an instance
minicloud ec2 stop <instance-id>

# Terminate an instance
minicloud ec2 terminate <instance-id>
```

### 2. 🪣 Storage (S3)
Create buckets, upload files, download objects, and configure static hosting.

```bash
# Create an S3 bucket
minicloud s3 mb my-app-bucket

# List all buckets
minicloud s3 ls

# Upload an object
minicloud s3 cp ./app.zip s3://my-app-bucket/app.zip

# List objects inside a bucket
minicloud s3 ls s3://my-app-bucket

# Download an object
minicloud s3 cp s3://my-app-bucket/app.zip ./downloaded.zip

# Delete an object / bucket
minicloud s3 rm s3://my-app-bucket/app.zip
minicloud s3 rb my-app-bucket
```

### 3. ⚡ Serverless (Lambda)
Create, list, and invoke serverless functions with event payloads.

```bash
# Create a Lambda function
minicloud lambda create --name image-resizer --runtime python3.9 --handler handler.py --code ./code.py

# List functions
minicloud lambda list

# Invoke function synchronously
minicloud lambda invoke --name image-resizer --payload '{"bucket": "images", "key": "photo.jpg"}'

# Configure S3 Event Trigger
minicloud lambda add-trigger --function image-resizer --bucket images --event OBJECT_CREATED
```

### 4. 💸 Billing & Rightsizing Advisor
Inspect accrued charges and obtain AI/telemetry-driven downsizing recommendations.

```bash
# View current billing summary and cost breakdown
minicloud billing summary

# Get automated rightsizing recommendations (underutilized instance detection)
minicloud billing recommendations
```

### 5. 💥 Chaos Engineering & Self-Healing
Inject controlled failures to validate Auto Scaling and self-healing resilience.

```bash
# Terminate a random instance within an Auto Scaling group to trigger recovery
minicloud chaos terminate-random
```

### 6. 📊 System Diagnostics & Health
Inspect backend health, active services, and telemetry.

```bash
minicloud status
```

---

## 🧪 Testing

Run CLI unit and smoke tests:
```bash
pytest
```
