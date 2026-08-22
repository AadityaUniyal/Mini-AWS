"""
High-Fidelity In-Memory Mock Backend for MINI-AWS (MiniCloud).
Implements the exact REST and WebSocket contracts defined in PROJECT.md and ORIGINAL_REQUEST.md.
Used for standalone/isolated test execution when the Spring Boot service is offline.
"""
import json
import re
import socket
import threading
import time
import urllib.parse
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import Dict, List, Any, Optional
import uuid
from datetime import datetime, timezone

from .test_data import INSTANCE_HOURLY_PRICING, HOURS_PER_MONTH, generate_s3_event_notification


class MiniCloudState:
    """In-memory state store mimicking the H2 database and runtime state of MiniCloud."""
    def __init__(self):
        self.lock = threading.Lock()
        self.reset()

    def reset(self):
        with getattr(self, "lock", threading.Lock()):
            self.users = {
                "admin": {"id": "user-admin-1", "password": "password", "role": "ADMIN", "accountId": "123456789012"},
                "developer": {"id": "user-dev-2", "password": "password", "role": "USER", "accountId": "123456789012"},
                "tenant-b": {"id": "user-tenant-b", "password": "password", "role": "USER", "accountId": "987654321098"}
            }
            self.buckets: Dict[str, Dict[str, Any]] = {}
            self.objects: Dict[str, Dict[str, Any]] = {} # "bucket/key" -> {bucket, key, size, content, etag, contentType, uploadedAt}
            self.s3_triggers: Dict[str, Dict[str, Any]] = {} # id -> trigger
            self.lambda_functions: Dict[str, Dict[str, Any]] = {} # name -> function
            self.lambda_logs: Dict[str, List[Dict[str, Any]]] = {} # func_name -> logs
            self.instances: Dict[str, Dict[str, Any]] = {} # id -> instance
            self.instance_cpu_history: Dict[str, List[float]] = {} # id -> cpu readings
            self.asg_groups: Dict[str, Dict[str, Any]] = {
                "asg-default": {
                    "id": "asg-default",
                    "name": "web-fleet-asg",
                    "desiredCapacity": 2,
                    "minCapacity": 1,
                    "maxCapacity": 5,
                    "instanceType": "T2_MEDIUM",
                    "instanceIds": []
                }
            }
            self.task_events: List[Dict[str, Any]] = [] # Global task events stream
            self.metrics_events: List[Dict[str, Any]] = []

    def add_task_event(self, event_type: str, task_id: str, message: str, status: str = "COMPLETED", metadata: dict = None):
        event = {
            "taskId": task_id,
            "taskType": event_type,
            "status": status,
            "message": message,
            "metadata": metadata or {},
            "timestamp": datetime.now(timezone.utc).isoformat()
        }
        self.task_events.append(event)
        return event


STATE = MiniCloudState()


class MockRequestHandler(BaseHTTPRequestHandler):
    def log_message(self, format, *args):
        pass # Suppress default noisy HTTP access logging

    def _send_json(self, status_code: int, data: Any):
        body = json.dumps(data).encode("utf-8")
        self.send_response(status_code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Headers", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
        self.end_headers()
        self.wfile.write(body)

    def _send_bytes(self, status_code: int, data: bytes, content_type: str = "application/octet-stream"):
        self.send_response(status_code)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Access-Control-Allow-Origin", "*")
        self.end_headers()
        self.wfile.write(data)

    def _send_empty(self, status_code: int = 204):
        self.send_response(status_code)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.end_headers()

    def do_OPTIONS(self):
        self.send_response(200)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Headers", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
        self.end_headers()

    def _read_body_json(self) -> dict:
        content_len = int(self.headers.get("Content-Length", 0))
        if content_len == 0:
            return {}
        raw = self.rfile.read(content_len).decode("utf-8")
        try:
            return json.loads(raw)
        except Exception:
            return {}

    def _read_body_raw(self) -> bytes:
        content_len = int(self.headers.get("Content-Length", 0))
        if content_len == 0:
            return b""
        return self.rfile.read(content_len)

    def do_GET(self):
        parsed = urllib.parse.urlparse(self.path)
        path = urllib.parse.unquote(parsed.path)
        query = urllib.parse.parse_qs(parsed.query)

        # 1. Healthcheck
        if path in ["/actuator/health", "/health"]:
            return self._send_json(200, {
                "status": "UP",
                "components": {
                    "db": {"status": "UP", "details": {"database": "H2"}},
                    "diskSpace": {"status": "UP", "details": {"free": 10737418240}}
                }
            })

        # 2. WebSocket Events Polling / Fetch endpoint for tests
        if path == "/ws-events/tasks":
            return self._send_json(200, {"events": STATE.task_events})

        if path == "/ws-events/metrics":
            return self._send_json(200, {
                "cpuUsagePercent": 12.5,
                "memoryUsedBytes": 104857600,
                "timestamp": datetime.now(timezone.utc).isoformat()
            })

        # 3. S3 Bucket list for user
        m_user_buckets = re.match(r"^/api/v1/storage/buckets/user/(.+)$", path)
        if m_user_buckets:
            user_id = m_user_buckets.group(1)
            buckets = [b for b in STATE.buckets.values() if b.get("ownerId") == user_id or user_id == "all" or b.get("ownerId") == "user-admin-1"]
            return self._send_json(200, buckets)

        # 4. S3 Object list
        m_objects = re.match(r"^/api/v1/storage/buckets/([^/]+)/objects$", path)
        if m_objects:
            bucket_name = m_objects.group(1)
            if bucket_name not in STATE.buckets:
                return self._send_json(404, {"error": "BucketNotFound", "message": f"Bucket {bucket_name} not found"})
            objs = [
                {"key": obj["key"], "size": obj["size"], "eTag": obj["etag"], "contentType": obj["contentType"], "lastModified": obj["uploadedAt"]}
                for k, obj in STATE.objects.items() if obj["bucket"] == bucket_name
            ]
            return self._send_json(200, objs)

        # 5. S3 Object download
        m_download = re.match(r"^/api/v1/storage/buckets/([^/]+)/(.+)$", path)
        if m_download and not path.endswith("/objects") and not path.endswith("/upload") and "/triggers" not in path:
            bucket_name = m_download.group(1)
            key = m_download.group(2)
            obj_key = f"{bucket_name}/{key}"
            if obj_key not in STATE.objects:
                return self._send_json(404, {"error": "NoSuchKey", "message": f"Key {key} does not exist in bucket {bucket_name}"})
            obj = STATE.objects[obj_key]
            return self._send_bytes(200, obj["content"], obj.get("contentType", "application/octet-stream"))

        # 6. S3 Triggers list
        if path in ["/api/v1/storage/triggers", "/api/v1/lambda/triggers"]:
            bucket_filter = query.get("bucketName", [None])[0]
            with STATE.lock:
                triggers = list(STATE.s3_triggers.values())
                if bucket_filter:
                    triggers = [t for t in triggers if t.get("bucketName") == bucket_filter]
                return self._send_json(200, triggers)

        m_bucket_triggers = re.match(r"^/api/v1/storage/buckets/([^/]+)/triggers$", path)
        if m_bucket_triggers:
            bucket_name = m_bucket_triggers.group(1)
            with STATE.lock:
                triggers = [t for t in STATE.s3_triggers.values() if t.get("bucketName") == bucket_name]
                return self._send_json(200, triggers)

        # 7. Lambda list
        if path == "/api/v1/lambda":
            with STATE.lock:
                return self._send_json(200, list(STATE.lambda_functions.values()))

        # 8. Lambda get details
        m_lambda_get = re.match(r"^/api/v1/lambda/([^/]+)$", path)
        if m_lambda_get and not path.endswith("/logs"):
            name = m_lambda_get.group(1)
            if name not in STATE.lambda_functions:
                return self._send_json(404, {"error": "FunctionNotFound", "message": f"Lambda function {name} not found"})
            return self._send_json(200, STATE.lambda_functions[name])

        # 9. Lambda logs
        m_lambda_logs = re.match(r"^/api/v1/lambda/([^/]+)/logs$", path)
        if m_lambda_logs:
            name = m_lambda_logs.group(1)
            logs = STATE.lambda_logs.get(name, [])
            return self._send_json(200, logs)

        # 10. EC2 Instances list
        if path in ["/api/v1/compute/instances", "/api/v1/compute/instances/"]:
            state_filter = query.get("state", [None])[0]
            with STATE.lock:
                instances = list(STATE.instances.values())
                if state_filter:
                    instances = [i for i in instances if i.get("state") == state_filter.upper()]
                return self._send_json(200, instances)

        # 11. EC2 Instance get
        m_inst_get = re.match(r"^/api/v1/compute/instances/([^/]+)$", path)
        if m_inst_get and not path.endswith("/stop") and not path.endswith("/start"):
            inst_id = m_inst_get.group(1)
            if inst_id not in STATE.instances:
                return self._send_json(404, {"error": "InstanceNotFound", "message": f"Instance {inst_id} not found"})
            return self._send_json(200, STATE.instances[inst_id])

        # 12. Billing Summary
        m_bill_sum = re.match(r"^/api/v1/billing/summary(?:/([^/]+))?$", path)
        if m_bill_sum:
            account_id = m_bill_sum.group(1) or "123456789012"
            active_inst_count = len([i for i in STATE.instances.values() if i["state"] == "RUNNING"])
            hourly_rate = sum(INSTANCE_HOURLY_PRICING.get(i["type"], 0.046) for i in STATE.instances.values() if i["state"] == "RUNNING")
            return self._send_json(200, {
                "accountId": account_id,
                "currency": "USD",
                "monthToDateCost": round(hourly_rate * 24 * 10, 2),
                "projectedMonthlyCost": round(hourly_rate * HOURS_PER_MONTH, 2),
                "activeRunningInstances": active_inst_count,
                "storageBucketCount": len(STATE.buckets)
            })

        # 13. Billing Rightsizing Recommendations (R3)
        if path == "/api/v1/billing/recommendations":
            with STATE.lock:
                recommendations = []
                total_monthly_savings = 0.0

                for inst_id, inst in STATE.instances.items():
                    if inst.get("state") != "RUNNING":
                        continue
                    
                    cpu_readings = STATE.instance_cpu_history.get(inst_id, [4.5])
                    avg_cpu = sum(cpu_readings) / len(cpu_readings) if cpu_readings else 4.5

                    # Condition: Average CPU utilization < 10.0%
                    if avg_cpu < 10.0:
                        curr_type = inst.get("type", "T2_MEDIUM")
                        curr_rate = INSTANCE_HOURLY_PRICING.get(curr_type, 0.046)

                        # Determine recommended downsizing target
                        if curr_type in ["C5_XLARGE", "M5_LARGE", "R5_LARGE"]:
                            rec_type = "T2_SMALL"
                        elif curr_type == "T2_MEDIUM":
                            rec_type = "T2_SMALL"
                        elif curr_type == "T2_SMALL":
                            rec_type = "T2_MICRO"
                        else:
                            rec_type = "T2_MICRO"

                        rec_rate = INSTANCE_HOURLY_PRICING.get(rec_type, 0.0116)
                        hourly_savings = max(0.0, curr_rate - rec_rate)
                        est_monthly_savings = round(hourly_savings * HOURS_PER_MONTH, 2)

                        if est_monthly_savings > 0:
                            total_monthly_savings += est_monthly_savings
                            recommendations.append({
                                "instanceId": inst_id,
                                "instanceName": inst.get("name", f"instance-{inst_id}"),
                                "currentInstanceType": curr_type,
                                "recommendedInstanceType": rec_type,
                                "averageCpuUtilization": round(avg_cpu, 2),
                                "currentHourlyCost": curr_rate,
                                "recommendedHourlyCost": rec_rate,
                                "hourlySavings": round(hourly_savings, 4),
                                "estimatedMonthlySavings": est_monthly_savings,
                                "reason": f"Instance CPU utilization averaged {round(avg_cpu, 1)}% (<10.0% threshold) over rolling window."
                            })

                return self._send_json(200, {
                    "totalEstimatedMonthlySavings": round(total_monthly_savings, 2),
                    "recommendationsCount": len(recommendations),
                    "evaluatedAt": datetime.now(timezone.utc).isoformat(),
                    "recommendations": recommendations
                })

        # 14. Scaling Replicas Status
        if path == "/api/v1/scaling/replicas":
            asg = STATE.asg_groups.get("asg-default", {})
            running = [i for i in STATE.instances.values() if i["state"] == "RUNNING"]
            return self._send_json(200, {
                "autoScalingGroupId": asg.get("id", "asg-default"),
                "desiredCapacity": asg.get("desiredCapacity", 2),
                "minCapacity": asg.get("minCapacity", 1),
                "maxCapacity": asg.get("maxCapacity", 5),
                "currentRunningReplicas": len(running),
                "status": "HEALTHY" if len(running) >= asg.get("desiredCapacity", 2) else "HEALING"
            })

        return self._send_json(404, {"error": "NotFound", "path": path})

    def do_POST(self):
        parsed = urllib.parse.urlparse(self.path)
        path = urllib.parse.unquote(parsed.path)

        # 1. Auth Login
        if path == "/api/v1/auth/login":
            body = self._read_body_json()
            username = body.get("username")
            password = body.get("password")
            if username in STATE.users and STATE.users[username]["password"] == password:
                user = STATE.users[username]
                token = f"mock-jwt-token-{user['id']}-{uuid.uuid4().hex}"
                return self._send_json(200, {
                    "token": token,
                    "userId": user["id"],
                    "username": username,
                    "role": user["role"],
                    "accountId": user["accountId"]
                })
            return self._send_json(401, {"error": "Unauthorized", "message": "Invalid username or password"})

        # 2. S3 Bucket creation
        if path == "/api/v1/storage/buckets":
            body = self._read_body_json()
            name = body.get("name") or body.get("bucketName")
            if not name:
                return self._send_json(400, {"error": "BadRequest", "message": "Bucket name is required"})
            if name in STATE.buckets:
                return self._send_json(409, {"error": "BucketAlreadyExists", "message": f"Bucket {name} already exists"})
            
            bucket_record = {
                "id": f"bkt-{uuid.uuid4().hex[:8]}",
                "name": name,
                "ownerId": body.get("ownerId", "user-admin-1"),
                "createdAt": datetime.now(timezone.utc).isoformat(),
                "retentionDays": body.get("retentionDays", 30)
            }
            STATE.buckets[name] = bucket_record
            return self._send_json(201, bucket_record)

        # 3. S3 Object upload
        m_upload = re.match(r"^/api/v1/storage/buckets/([^/]+)/upload$", path)
        if m_upload:
            bucket_name = m_upload.group(1)
            if bucket_name not in STATE.buckets:
                return self._send_json(404, {"error": "BucketNotFound", "message": f"Bucket {bucket_name} not found"})

            raw_bytes = self._read_body_raw()
            key = self.headers.get("X-Object-Key")
            content_type = self.headers.get("Content-Type", "application/octet-stream")

            # Support JSON multipart/simple payload
            if not key:
                try:
                    body_json = json.loads(raw_bytes.decode("utf-8"))
                    key = body_json.get("key", f"file-{uuid.uuid4().hex[:6]}.txt")
                    raw_bytes = body_json.get("content", "").encode("utf-8")
                except Exception:
                    key = f"object-{uuid.uuid4().hex[:6]}.bin"

            etag = uuid.uuid4().hex
            obj_record = {
                "bucket": bucket_name,
                "key": key,
                "size": len(raw_bytes),
                "content": raw_bytes,
                "etag": etag,
                "contentType": content_type,
                "uploadedAt": datetime.now(timezone.utc).isoformat()
            }
            STATE.objects[f"{bucket_name}/{key}"] = obj_record

            # --- S3-to-Lambda Trigger Dispatcher (R2) ---
            matched_triggers = []
            with STATE.lock:
                for trig in STATE.s3_triggers.values():
                    if trig.get("enabled", True) and trig.get("bucketName") == bucket_name:
                        # Check prefix/suffix filters
                        prefix = trig.get("prefix")
                        suffix = trig.get("suffix")
                        if prefix and not key.startswith(prefix):
                            continue
                        if suffix and not key.endswith(suffix):
                            continue
                        matched_triggers.append(trig)

            # Dispatch asynchronous invocations for matched triggers
            for trig in matched_triggers:
                func_name = trig.get("functionName")
                event_payload = generate_s3_event_notification(
                    bucket_name=bucket_name,
                    object_key=key,
                    object_size=len(raw_bytes),
                    etag=etag,
                    configuration_id=trig.get("id")
                )
                task_id = f"task-lambda-{uuid.uuid4().hex[:8]}"
                
                # Broadcast start event
                STATE.add_task_event(
                    event_type="S3_LAMBDA_TRIGGER",
                    task_id=task_id,
                    message=f"S3 event triggered Lambda '{func_name}' for object {bucket_name}/{key}",
                    status="RUNNING",
                    metadata={"bucket": bucket_name, "key": key, "function": func_name}
                )

                # Record Lambda invocation log
                log_entry = {
                    "invocationId": str(uuid.uuid4()),
                    "functionName": func_name,
                    "status": "SUCCESS",
                    "durationMs": 42,
                    "event": event_payload,
                    "output": f"Successfully processed S3 upload {bucket_name}/{key}",
                    "errorOutput": "",
                    "timestamp": datetime.now(timezone.utc).isoformat()
                }
                STATE.lambda_logs.setdefault(func_name, []).append(log_entry)

                # Broadcast completion event
                STATE.add_task_event(
                    event_type="S3_LAMBDA_TRIGGER",
                    task_id=task_id,
                    message=f"Lambda '{func_name}' execution completed successfully.",
                    status="COMPLETED",
                    metadata={"bucket": bucket_name, "key": key, "function": func_name, "durationMs": 42}
                )

            return self._send_json(200, {
                "bucket": bucket_name,
                "key": key,
                "size": len(raw_bytes),
                "eTag": etag,
                "triggersDispatched": len(matched_triggers)
            })

        # 4. S3 Trigger Registration (R2)
        if path in ["/api/v1/storage/triggers", "/api/v1/lambda/triggers"]:
            body = self._read_body_json()
            bucket_name = body.get("bucketName")
            function_name = body.get("functionName")
            if not bucket_name or not function_name:
                return self._send_json(400, {"error": "BadRequest", "message": "bucketName and functionName are required"})

            # Check if bucket exists
            if bucket_name not in STATE.buckets:
                return self._send_json(404, {"error": "BucketNotFound", "message": f"Bucket {bucket_name} not found"})

            # Check for duplicate
            for t in STATE.s3_triggers.values():
                if t["bucketName"] == bucket_name and t["functionName"] == function_name:
                    return self._send_json(409, {"error": "DuplicateTrigger", "message": "Trigger already exists"})

            trigger_id = f"trig-{uuid.uuid4().hex[:8]}"
            trigger_record = {
                "id": trigger_id,
                "bucketName": bucket_name,
                "functionName": function_name,
                "events": body.get("events", ["s3:ObjectCreated:*"]),
                "prefix": body.get("prefix"),
                "suffix": body.get("suffix"),
                "enabled": body.get("enabled", True),
                "createdAt": datetime.now(timezone.utc).isoformat()
            }
            STATE.s3_triggers[trigger_id] = trigger_record
            return self._send_json(201, trigger_record)

        # 5. Lambda Function creation
        if path == "/api/v1/lambda":
            body = self._read_body_json()
            name = body.get("name")
            runtime = body.get("runtime", "PYTHON")
            handler = body.get("handler", "handler")
            if not name:
                return self._send_json(400, {"error": "BadRequest", "message": "Function name is required"})
            if name in STATE.lambda_functions:
                return self._send_json(409, {"error": "FunctionAlreadyExists", "message": f"Function {name} already exists"})

            func_record = {
                "id": f"func-{uuid.uuid4().hex[:8]}",
                "name": name,
                "runtime": runtime,
                "handler": handler,
                "code": body.get("code", ""),
                "memoryMb": body.get("memoryMb", 128),
                "timeoutSec": body.get("timeoutSec", 30),
                "createdAt": datetime.now(timezone.utc).isoformat()
            }
            STATE.lambda_functions[name] = func_record
            return self._send_json(201, func_record)

        # 6. Lambda Invocation
        m_lambda_invoke = re.match(r"^/api/v1/lambda/invoke/([^/]+)(?:/json)?$", path)
        if m_lambda_invoke:
            name = m_lambda_invoke.group(1)
            if name not in STATE.lambda_functions:
                return self._send_json(404, {"error": "FunctionNotFound", "message": f"Function {name} not found"})
            
            payload = self._read_body_json()
            log_entry = {
                "invocationId": str(uuid.uuid4()),
                "functionName": name,
                "status": "SUCCESS",
                "durationMs": 35,
                "output": f"Executed function {name} with payload keys: {list(payload.keys())}",
                "errorOutput": "",
                "timestamp": datetime.now(timezone.utc).isoformat()
            }
            STATE.lambda_logs.setdefault(name, []).append(log_entry)
            return self._send_json(200, {
                "statusCode": 200,
                "executed": True,
                "output": log_entry["output"],
                "durationMs": 35
            })

        # 7. EC2 Launch instance
        if path == "/api/v1/compute/instances/launch":
            body = self._read_body_json()
            inst_type = body.get("type", "T2_MICRO")
            name = body.get("name") or f"inst-{uuid.uuid4().hex[:6]}"
            inst_id = f"inst-{uuid.uuid4().hex[:8]}"

            inst_record = {
                "id": inst_id,
                "name": name,
                "type": inst_type,
                "state": "RUNNING",
                "privateIp": f"10.0.1.{len(STATE.instances) + 10}",
                "publicIp": f"54.210.10.{len(STATE.instances) + 10}",
                "launchedAt": datetime.now(timezone.utc).isoformat()
            }
            STATE.instances[inst_id] = inst_record
            # Seed default CPU history (low by default for testing rightsizing advisor)
            cpu_val = float(body.get("cpuUtilization", 4.5))
            STATE.instance_cpu_history[inst_id] = [cpu_val]

            # Add to ASG if default
            asg = STATE.asg_groups.get("asg-default")
            if asg:
                asg.setdefault("instanceIds", []).append(inst_id)

            return self._send_json(201, inst_record)

        # 8. EC2 Instance Stop / Start
        m_inst_stop = re.match(r"^/api/v1/compute/instances/([^/]+)/stop$", path)
        if m_inst_stop:
            inst_id = m_inst_stop.group(1)
            if inst_id not in STATE.instances:
                return self._send_json(404, {"error": "InstanceNotFound", "message": f"Instance {inst_id} not found"})
            STATE.instances[inst_id]["state"] = "STOPPED"
            return self._send_json(200, STATE.instances[inst_id])

        m_inst_start = re.match(r"^/api/v1/compute/instances/([^/]+)/start$", path)
        if m_inst_start:
            inst_id = m_inst_start.group(1)
            if inst_id not in STATE.instances:
                return self._send_json(404, {"error": "InstanceNotFound", "message": f"Instance {inst_id} not found"})
            STATE.instances[inst_id]["state"] = "RUNNING"
            return self._send_json(200, STATE.instances[inst_id])

        # 9. Chaos Injection: Terminate Random Instance & ASG Self-Healing (R4)
        if path == "/api/v1/chaos/terminate-random-instance":
            body = self._read_body_json()
            asg_id = body.get("autoScalingGroupId") or "asg-default"
            asg = STATE.asg_groups.get(asg_id)
            if not asg:
                return self._send_json(404, {"error": "AsgNotFound", "message": f"AutoScalingGroup {asg_id} not found"})

            # Find a running instance
            running_instances = [i for i in STATE.instances.values() if i["state"] == "RUNNING"]
            if not running_instances:
                return self._send_json(400, {"error": "NoRunningInstances", "message": "No running instances available to terminate"})

            # Terminate one
            victim = running_instances[0]
            victim["state"] = "TERMINATED"
            victim_id = victim["id"]

            # Evaluate ASG capacity deficit
            desired = max(asg.get("desiredCapacity", 2), len(running_instances))
            asg["desiredCapacity"] = desired
            active_after = [i for i in STATE.instances.values() if i["state"] == "RUNNING"]
            deficit_detected = len(active_after) < desired

            replacement_id = None
            replacement_state = None

            if deficit_detected:
                # Self-healing: provision replacement instance
                replacement_id = f"inst-heal-{uuid.uuid4().hex[:8]}"
                replacement = {
                    "id": replacement_id,
                    "name": f"healed-{replacement_id}",
                    "type": asg.get("instanceType", "T2_MEDIUM"),
                    "state": "RUNNING",
                    "privateIp": f"10.0.1.{len(STATE.instances) + 20}",
                    "publicIp": f"54.210.10.{len(STATE.instances) + 20}",
                    "launchedAt": datetime.now(timezone.utc).isoformat()
                }
                STATE.instances[replacement_id] = replacement
                STATE.instance_cpu_history[replacement_id] = [5.0]
                replacement_state = "RUNNING"

            # Broadcast Chaos & Self-Healing Events over WebSocket Tasks stream
            task_id = f"chaos-{uuid.uuid4().hex[:8]}"
            STATE.add_task_event(
                event_type="CHAOS_INSTANCE_TERMINATED",
                task_id=task_id,
                message=f"Chaos terminated instance {victim_id}.",
                status="COMPLETED",
                metadata={"victimId": victim_id, "asgId": asg_id}
            )
            if replacement_id:
                STATE.add_task_event(
                    event_type="SELF_HEALING_RECOVERY",
                    task_id=task_id,
                    message=f"Self-healing launched replacement instance {replacement_id} to restore desired capacity {desired}.",
                    status="COMPLETED",
                    metadata={"replacementId": replacement_id, "asgId": asg_id}
                )

            return self._send_json(200, {
                "chaosAction": "TERMINATE_INSTANCE",
                "terminatedInstanceId": victim_id,
                "autoScalingGroupId": asg_id,
                "previousState": "RUNNING",
                "currentState": "TERMINATED",
                "deficitDetected": deficit_detected,
                "replacementInstanceId": replacement_id,
                "replacementState": replacement_state,
                "status": "SELF_HEALING_COMPLETED",
                "timestamp": datetime.now(timezone.utc).isoformat()
            })

        return self._send_json(404, {"error": "NotFound", "path": path})

    def do_DELETE(self):
        parsed = urllib.parse.urlparse(self.path)
        path = urllib.parse.unquote(parsed.path)

        # 1. S3 Trigger deletion
        m_trig_del = re.match(r"^/api/v1/storage/triggers/([^/]+)$", path)
        if m_trig_del:
            trig_id = m_trig_del.group(1)
            if trig_id in STATE.s3_triggers:
                del STATE.s3_triggers[trig_id]
                return self._send_empty(204)
            return self._send_json(404, {"error": "TriggerNotFound", "message": f"Trigger {trig_id} not found"})

        # 2. S3 Object deletion
        m_obj_del = re.match(r"^/api/v1/storage/buckets/([^/]+)/objects/(.+)$", path)
        if m_obj_del:
            bucket_name = m_obj_del.group(1)
            key = m_obj_del.group(2)
            obj_key = f"{bucket_name}/{key}"
            if obj_key in STATE.objects:
                del STATE.objects[obj_key]
                return self._send_empty(204)
            return self._send_json(404, {"error": "ObjectNotFound", "message": f"Object {key} not found in {bucket_name}"})

        # 3. S3 Bucket deletion
        m_bkt_del = re.match(r"^/api/v1/storage/buckets/([^/]+)$", path)
        if m_bkt_del:
            bucket_name = m_bkt_del.group(1)
            if bucket_name in STATE.buckets:
                del STATE.buckets[bucket_name]
                return self._send_empty(204)
            return self._send_json(404, {"error": "BucketNotFound", "message": f"Bucket {bucket_name} not found"})

        # 4. Lambda Function deletion
        m_func_del = re.match(r"^/api/v1/lambda/([^/]+)$", path)
        if m_func_del:
            name = m_func_del.group(1)
            if name in STATE.lambda_functions:
                del STATE.lambda_functions[name]
                return self._send_empty(204)
            return self._send_json(404, {"error": "FunctionNotFound", "message": f"Function {name} not found"})

        # 5. EC2 Instance termination
        m_inst_term = re.match(r"^/api/v1/compute/instances/([^/]+)$", path)
        if m_inst_term:
            inst_id = m_inst_term.group(1)
            if inst_id in STATE.instances:
                STATE.instances[inst_id]["state"] = "TERMINATED"
                return self._send_empty(204)
            return self._send_json(404, {"error": "InstanceNotFound", "message": f"Instance {inst_id} not found"})

        return self._send_json(404, {"error": "NotFound", "path": path})


class MockMiniCloudBackend:
    """Manager for the in-process mock server."""
    def __init__(self, host: str = "127.0.0.1", port: int = 0):
        self.host = host
        self.port = port
        self.server: Optional[ThreadingHTTPServer] = None
        self.thread: Optional[threading.Thread] = None

    def start(self) -> str:
        STATE.reset()
        self.server = ThreadingHTTPServer((self.host, self.port), MockRequestHandler)
        actual_port = self.server.server_address[1]
        self.port = actual_port
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()
        return f"http://{self.host}:{self.port}"

    def stop(self):
        if self.server:
            self.server.shutdown()
            self.server.server_close()
            self.server = None

    @property
    def url(self) -> str:
        return f"http://{self.host}:{self.port}"

    @property
    def ws_url(self) -> str:
        return f"ws://{self.host}:{self.port}"
