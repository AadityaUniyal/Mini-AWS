"""
Comprehensive Python REST API client for MINI-AWS (MiniCloud).
"""
import io
import json
import os
from typing import Optional, Dict, Any, List, Union
import requests


class MiniCloudApiClient:
    """Client wrapping all REST APIs of MINI-AWS."""

    def __init__(self, base_url: str = "http://localhost:8080", token: Optional[str] = None):
        self.base_url = base_url.rstrip("/")
        self.session = requests.Session()
        self.token = token
        if token:
            self.session.headers.update({"Authorization": f"Bearer {token}"})

    def _headers(self, custom: Optional[Dict[str, str]] = None) -> Dict[str, str]:
        h = {"Accept": "application/json"}
        if self.token:
            h["Authorization"] = f"Bearer {self.token}"
        if custom:
            h.update(custom)
        return h

    # ── Actuator & System ────
    def health_check(self) -> requests.Response:
        return self.session.get(f"{self.base_url}/actuator/health", headers=self._headers(), timeout=5.0)

    # ── Authentication ────
    def login(self, username: str = "admin", password: str = "password") -> requests.Response:
        resp = self.session.post(
            f"{self.base_url}/api/v1/auth/login",
            json={"username": username, "password": password},
            headers=self._headers(),
            timeout=5.0
        )
        if resp.status_code == 200:
            data = resp.json()
            if "token" in data:
                self.token = data["token"]
                self.session.headers.update({"Authorization": f"Bearer {self.token}"})
        return resp

    # ── S3 Storage ────
    def create_bucket(self, bucket_name: str, retention_days: int = 30, owner_id: Optional[str] = None) -> requests.Response:
        payload = {"name": bucket_name, "retentionDays": retention_days}
        if owner_id:
            payload["ownerId"] = owner_id
        return self.session.post(
            f"{self.base_url}/api/v1/storage/buckets",
            json=payload,
            headers=self._headers(),
            timeout=5.0
        )

    def list_buckets(self, user_id: str = "all") -> requests.Response:
        return self.session.get(
            f"{self.base_url}/api/v1/storage/buckets/user/{user_id}",
            headers=self._headers(),
            timeout=5.0
        )

    def delete_bucket(self, bucket_name: str) -> requests.Response:
        return self.session.delete(
            f"{self.base_url}/api/v1/storage/buckets/{bucket_name}",
            headers=self._headers(),
            timeout=5.0
        )

    def upload_object(
        self,
        bucket_name: str,
        key: str,
        content: Union[str, bytes, io.BytesIO],
        content_type: str = "application/octet-stream"
    ) -> requests.Response:
        if isinstance(content, str):
            raw_data = content.encode("utf-8")
        elif isinstance(content, io.BytesIO):
            raw_data = content.getvalue()
        else:
            raw_data = content

        headers = self._headers({
            "Content-Type": content_type,
            "X-Object-Key": key
        })
        return self.session.post(
            f"{self.base_url}/api/v1/storage/buckets/{bucket_name}/upload",
            data=raw_data,
            headers=headers,
            timeout=5.0
        )

    def download_object(self, bucket_name: str, key: str) -> requests.Response:
        import urllib.parse
        encoded_key = urllib.parse.quote(key, safe="/")
        return self.session.get(
            f"{self.base_url}/api/v1/storage/buckets/{bucket_name}/{encoded_key}",
            headers=self._headers(),
            timeout=5.0
        )

    def list_objects(self, bucket_name: str) -> requests.Response:
        return self.session.get(
            f"{self.base_url}/api/v1/storage/buckets/{bucket_name}/objects",
            headers=self._headers(),
            timeout=5.0
        )

    def delete_object(self, bucket_name: str, key: str) -> requests.Response:
        import urllib.parse
        encoded_key = urllib.parse.quote(key, safe="/")
        return self.session.delete(
            f"{self.base_url}/api/v1/storage/buckets/{bucket_name}/objects/{encoded_key}",
            headers=self._headers(),
            timeout=5.0
        )

    # ── S3-to-Lambda Triggers (R2) ────
    def create_s3_trigger(
        self,
        bucket_name: str,
        function_name: str,
        events: Optional[List[str]] = None,
        prefix: Optional[str] = None,
        suffix: Optional[str] = None,
        enabled: bool = True
    ) -> requests.Response:
        payload = {
            "bucketName": bucket_name,
            "functionName": function_name,
            "events": events or ["s3:ObjectCreated:*"],
            "enabled": enabled
        }
        if prefix:
            payload["prefix"] = prefix
        if suffix:
            payload["suffix"] = suffix
        return self.session.post(
            f"{self.base_url}/api/v1/storage/triggers",
            json=payload,
            headers=self._headers(),
            timeout=5.0
        )

    def list_s3_triggers(self, bucket_name: Optional[str] = None) -> requests.Response:
        params = {}
        if bucket_name:
            params["bucketName"] = bucket_name
        return self.session.get(
            f"{self.base_url}/api/v1/storage/triggers",
            params=params,
            headers=self._headers(),
            timeout=5.0
        )

    def delete_s3_trigger(self, trigger_id: str) -> requests.Response:
        return self.session.delete(
            f"{self.base_url}/api/v1/storage/triggers/{trigger_id}",
            headers=self._headers(),
            timeout=5.0
        )

    # ── Lambda Functions ────
    def create_lambda_function(
        self,
        name: str,
        runtime: str = "PYTHON",
        handler: str = "handler",
        code: str = "",
        memory_mb: int = 128,
        timeout_sec: int = 30
    ) -> requests.Response:
        payload = {
            "name": name,
            "runtime": runtime,
            "handler": handler,
            "code": code,
            "memoryMb": memory_mb,
            "timeoutSec": timeout_sec
        }
        return self.session.post(
            f"{self.base_url}/api/v1/lambda",
            json=payload,
            headers=self._headers(),
            timeout=5.0
        )

    def list_lambda_functions(self) -> requests.Response:
        return self.session.get(f"{self.base_url}/api/v1/lambda", headers=self._headers(), timeout=5.0)

    def get_lambda_function(self, name: str) -> requests.Response:
        return self.session.get(f"{self.base_url}/api/v1/lambda/{name}", headers=self._headers(), timeout=5.0)

    def invoke_lambda_function(self, name: str, payload: Optional[Dict[str, Any]] = None) -> requests.Response:
        return self.session.post(
            f"{self.base_url}/api/v1/lambda/invoke/{name}/json",
            json=payload or {},
            headers=self._headers(),
            timeout=5.0
        )

    def get_lambda_logs(self, name: str) -> requests.Response:
        return self.session.get(f"{self.base_url}/api/v1/lambda/{name}/logs", headers=self._headers(), timeout=5.0)

    def delete_lambda_function(self, name: str) -> requests.Response:
        return self.session.delete(f"{self.base_url}/api/v1/lambda/{name}", headers=self._headers(), timeout=5.0)

    # ── Compute / EC2 ────
    def launch_instance(
        self,
        name: str,
        instance_type: str = "T2_MICRO",
        cpu_utilization: float = 4.5,
        security_group_id: Optional[str] = None
    ) -> requests.Response:
        payload = {
            "name": name,
            "type": instance_type,
            "cpuUtilization": cpu_utilization
        }
        if security_group_id:
            payload["securityGroupId"] = security_group_id
        return self.session.post(
            f"{self.base_url}/api/v1/compute/instances/launch",
            json=payload,
            headers=self._headers(),
            timeout=5.0
        )

    def list_instances(self, state: Optional[str] = None) -> requests.Response:
        params = {}
        if state:
            params["state"] = state
        return self.session.get(
            f"{self.base_url}/api/v1/compute/instances",
            params=params,
            headers=self._headers(),
            timeout=5.0
        )

    def get_instance(self, instance_id: str) -> requests.Response:
        return self.session.get(f"{self.base_url}/api/v1/compute/instances/{instance_id}", headers=self._headers(), timeout=5.0)

    def stop_instance(self, instance_id: str) -> requests.Response:
        return self.session.post(f"{self.base_url}/api/v1/compute/instances/{instance_id}/stop", headers=self._headers(), timeout=5.0)

    def start_instance(self, instance_id: str) -> requests.Response:
        return self.session.post(f"{self.base_url}/api/v1/compute/instances/{instance_id}/start", headers=self._headers(), timeout=5.0)

    def terminate_instance(self, instance_id: str) -> requests.Response:
        return self.session.delete(f"{self.base_url}/api/v1/compute/instances/{instance_id}", headers=self._headers(), timeout=5.0)

    # ── Billing & Rightsizing Advisor (R3) ────
    def get_billing_summary(self, account_id: Optional[str] = None) -> requests.Response:
        path = f"/api/v1/billing/summary/{account_id}" if account_id else "/api/v1/billing/summary"
        return self.session.get(f"{self.base_url}{path}", headers=self._headers(), timeout=5.0)

    def get_billing_recommendations(self, account_id: Optional[str] = None) -> requests.Response:
        params = {}
        if account_id:
            params["accountId"] = account_id
        return self.session.get(
            f"{self.base_url}/api/v1/billing/recommendations",
            params=params,
            headers=self._headers(),
            timeout=5.0
        )

    # ── Chaos Engineering & Self-Healing (R4) ────
    def inject_chaos_terminate(self, auto_scaling_group_id: Optional[str] = None) -> requests.Response:
        payload = {}
        if auto_scaling_group_id:
            payload["autoScalingGroupId"] = auto_scaling_group_id
        return self.session.post(
            f"{self.base_url}/api/v1/chaos/terminate-random-instance",
            json=payload,
            headers=self._headers(),
            timeout=5.0
        )

    def get_asg_status(self) -> requests.Response:
        return self.session.get(f"{self.base_url}/api/v1/scaling/replicas", headers=self._headers(), timeout=5.0)
