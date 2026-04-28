# 🔥 **MiniCloud Real-Time Database Guide**

## **Live Database Architecture Overview**

MiniCloud features a comprehensive real-time database system that automatically tracks and updates all user activities, resource usage, and system metrics in real-time.

---

## 🗄️ **Database Tables & Real-Time Features**

### **Core Tables (Always Live-Updated)**

#### **1. User Management & Sessions**
```sql
-- Live user tracking
iam_users              -- Auto-updated: last_login, login_count, last_ip
user_sessions          -- Real-time: login/logout, session activity
notifications          -- Live: system alerts, cost warnings, resource notifications

-- Example: See live user activity
SELECT username, last_login, login_count, last_ip, 
       (SELECT COUNT(*) FROM user_sessions WHERE user_id = u.id AND is_active = TRUE) as active_sessions
FROM iam_users u ORDER BY last_login DESC;
```

#### **2. Resource Tracking (Live Updates)**
```sql
-- EC2 Instances - Live metrics
compute_instances      -- Real-time: state, cpu_usage, memory_usage, network_in/out, last_heartbeat

-- S3 Storage - Live usage
iam_buckets           -- Auto-updated: total_size_bytes, object_count, last_accessed
storage_objects       -- Real-time: size tracking, access patterns

-- Lambda Functions - Live performance
lambda_functions      -- Real-time: invocation_count, total_duration_ms, error_count, avg_duration_ms
lambda_invocation_logs -- Live: every function call logged with performance metrics

-- RDS Databases - Live monitoring  
rds_instances         -- Real-time: cpu_usage, memory_usage, connections_count, last_backup
```

#### **3. Real-Time Monitoring Tables**
```sql
-- System Performance (Updated every 1 minute)
system_metrics        -- Live: CPU, memory, disk, threads, uptime

-- API Activity (Every request logged)
api_requests          -- Real-time: method, endpoint, response_time, status_code, user

-- Event Stream (All actions tracked)
event_stream          -- Live: user_login, resource_created, cost_alert, system_events

-- Resource Usage (Updated continuously)
resource_usage        -- Real-time: compute hours, storage GB, API requests, costs
```

#### **4. Live Cost & Billing**
```sql
-- Cost Tracking (Updated every minute for running resources)
billing_records       -- Real-time: per-minute cost accumulation
cost_tracking         -- Live: hourly/daily/monthly cost calculations
dashboard_metrics     -- Updated every 5 minutes: account summaries

-- Example: Live cost monitoring
SELECT account_id, service, SUM(total_cost) as current_cost,
       COUNT(*) as billing_events
FROM billing_records 
WHERE created_at > NOW() - INTERVAL 1 HOUR
GROUP BY account_id, service;
```

---

## ⚡ **Real-Time Update Mechanisms**

### **1. Automatic Scheduled Updates**
```java
@Scheduled(fixedRate = 60000)   // Every 1 minute
- System metrics collection (CPU, RAM, disk)
- Billing cost accumulation for running resources
- Resource usage calculations

@Scheduled(fixedRate = 300000)  // Every 5 minutes  
- Dashboard metrics aggregation
- Account resource summaries
- Cost projections

@Scheduled(fixedRate = 3600000) // Every 1 hour
- Database cleanup (old metrics, logs)
- Performance optimization
- Health checks
```

### **2. Event-Driven Updates (Instant)**
```java
// User Actions → Immediate DB Updates
- User login/logout → user_sessions, iam_users.last_login
- Resource creation → compute_instances, iam_buckets, lambda_functions
- API calls → api_requests, audit_logs
- State changes → instance.state, function.status
- File operations → storage_objects, bucket.object_count
```

### **3. Live Database Views**
```sql
-- Pre-computed live views for instant access
CREATE VIEW live_system_status AS ...    -- Current system health
CREATE VIEW live_user_activity AS ...    -- Active users and recent actions  
CREATE VIEW live_resource_summary AS ... -- Resource counts per account
CREATE VIEW live_cost_summary AS ...     -- Real-time cost breakdown
```

---

## 🔍 **Real-Time Database Access Methods**

### **Method 1: H2 Web Console (Live Browser Access)**
```
URL: http://localhost:8080/h2-console
JDBC URL: jdbc:h2:file:./minicloud-data/db/miniclouddb
Username: sa
Password: (blank)

✅ See changes instantly as users interact with the system
✅ Run live queries while the application is running
✅ Monitor real-time metrics and user activity
```

### **Method 2: External JDBC Tools (DBeaver, IntelliJ, etc.)**
```
JDBC URL: jdbc:h2:tcp://localhost:9092/./minicloud-data/db/miniclouddb
Port: 9092 (H2 TCP Server)
Username: sa
Password: (blank)

✅ Connect from external tools while app is running
✅ Real-time monitoring and alerting
✅ Custom dashboards and reports
```

### **Method 3: REST API Endpoints (Live Data)**
```bash
# Live system status
GET /api/v1/dashboard/live-status

# Live user activity  
GET /api/v1/dashboard/live-users

# Live resource summary
GET /api/v1/dashboard/live-resources/{accountId}

# Live cost tracking
GET /api/v1/dashboard/live-costs/{accountId}
```

---

## 📊 **Live Monitoring Queries**

### **Real-Time System Health**
```sql
-- Current system performance
SELECT * FROM live_system_status;

-- Active user sessions
SELECT username, session_type, last_activity, ip_address
FROM user_sessions 
WHERE is_active = TRUE 
ORDER BY last_activity DESC;

-- Recent API activity
SELECT method, endpoint, status_code, response_time_ms, username, timestamp
FROM api_requests 
WHERE timestamp > NOW() - INTERVAL 10 MINUTE
ORDER BY timestamp DESC;
```

### **Live Resource Monitoring**
```sql
-- Running EC2 instances with live metrics
SELECT name, state, type, cpu_usage, memory_usage, 
       network_in, network_out, last_heartbeat
FROM compute_instances 
WHERE state = 'RUNNING';

-- S3 storage usage by account
SELECT account_id, COUNT(*) as buckets, 
       SUM(object_count) as total_objects,
       SUM(total_size_bytes)/1024/1024/1024 as storage_gb
FROM iam_buckets 
GROUP BY account_id;

-- Lambda function performance
SELECT name, invocation_count, avg_duration_ms, error_count,
       last_invoked_at
FROM lambda_functions 
ORDER BY last_invoked_at DESC;
```

### **Live Cost Analysis**
```sql
-- Real-time cost accumulation (last hour)
SELECT account_id, service, resource_name, 
       SUM(total_cost) as hourly_cost
FROM billing_records 
WHERE created_at > NOW() - INTERVAL 1 HOUR
GROUP BY account_id, service, resource_name
ORDER BY hourly_cost DESC;

-- Daily cost trends
SELECT DATE(created_at) as date, 
       SUM(total_cost) as daily_cost,
       COUNT(*) as billing_events
FROM billing_records 
WHERE created_at > NOW() - INTERVAL 7 DAY
GROUP BY DATE(created_at)
ORDER BY date DESC;
```

### **Live Event Stream**
```sql
-- Recent system events
SELECT event_type, source_service, severity, 
       event_data, timestamp
FROM event_stream 
WHERE timestamp > NOW() - INTERVAL 1 HOUR
ORDER BY timestamp DESC;

-- User activity timeline
SELECT username, event_type, source_service, 
       resource_type, timestamp
FROM event_stream e
JOIN iam_users u ON e.user_id = u.id
WHERE e.timestamp > NOW() - INTERVAL 24 HOUR
ORDER BY timestamp DESC;
```

---

## 🚀 **Real-Time Features in Action**

### **When You Login:**
1. `iam_users.last_login` updated instantly
2. `user_sessions` record created
3. `event_stream` logs "USER_LOGIN" event
4. `api_requests` tracks login API call
5. Dashboard shows you as "Active User"

### **When You Create an EC2 Instance:**
1. `compute_instances` gets new record
2. `audit_logs` records the action
3. `event_stream` logs "RESOURCE_CREATED" 
4. `billing_records` starts cost tracking
5. `dashboard_metrics` updates instance count
6. `notifications` may alert about quota usage

### **Every Minute (Automatic):**
1. `system_metrics` records CPU/RAM/disk usage
2. `billing_records` accumulates costs for running resources
3. `resource_usage` calculates hourly usage
4. Instance `cpu_usage` and `memory_usage` updated

### **When You Upload to S3:**
1. `storage_objects` gets new record
2. `iam_buckets.object_count` incremented
3. `iam_buckets.total_size_bytes` updated
4. `billing_records` tracks storage cost
5. `event_stream` logs "OBJECT_UPLOADED"

---

## 🎯 **Database Performance & Optimization**

### **Automatic Cleanup (Every Hour)**
- System metrics: Keep last 7 days
- API requests: Keep last 30 days  
- Event stream: Keep last 90 days
- Old sessions marked inactive after 24 hours

### **Optimized Indexes**
- All tables have performance indexes
- Time-based queries optimized
- User-based lookups fast
- Account-based aggregations efficient

### **Real-Time Views**
- Pre-computed aggregations
- Instant dashboard data
- No complex joins needed
- Cached for performance

---

## 🔧 **How to Monitor Your Database Live**

### **1. Start MiniCloud**
```bash
cd minicloud
java -jar minicloud-api/target/minicloud-api-1.0.0.jar --mode=DESKTOP
```

### **2. Open H2 Console**
```
Browser: http://localhost:8080/h2-console
JDBC URL: jdbc:h2:file:./minicloud-data/db/miniclouddb
```

### **3. Run Live Queries**
```sql
-- See live user activity
SELECT * FROM live_user_activity;

-- Monitor system performance  
SELECT * FROM system_metrics ORDER BY timestamp DESC LIMIT 10;

-- Track resource usage
SELECT * FROM live_resource_summary;

-- Watch cost accumulation
SELECT * FROM billing_records ORDER BY created_at DESC LIMIT 20;
```

### **4. Use the Swing UI**
- Create EC2 instances → Watch `compute_instances` table
- Upload S3 files → Monitor `storage_objects` table  
- Invoke Lambda → See `lambda_invocation_logs` table
- Check billing → View `billing_records` updates

---

## 🎉 **The Result: Fully Live Database**

✅ **Every user action** → Instant database update  
✅ **Every resource change** → Real-time tracking  
✅ **Every API call** → Logged and monitored  
✅ **Every minute** → System metrics recorded  
✅ **Every cost** → Tracked and accumulated  
✅ **Every event** → Streamed and stored  

**Your MiniCloud database is now a living, breathing system that captures everything in real-time!**