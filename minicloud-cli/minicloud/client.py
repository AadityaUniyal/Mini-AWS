"""HTTP and WebSocket client for MiniCloud API."""

import json
from typing import Any, Dict, Optional, Tuple, Union
import httpx
from rich.console import Console

from minicloud.config import load_config

console = Console()


class MiniCloudClient:
    def __init__(self, endpoint: Optional[str] = None, token: Optional[str] = None):
        cfg = load_config()
        self.endpoint = (endpoint or cfg["endpoint"]).rstrip("/")
        self.token = token or cfg["token"]

    def _headers(self, custom: Optional[Dict[str, str]] = None) -> Dict[str, str]:
        h = {"Accept": "application/json"}
        if self.token:
            h["Authorization"] = f"Bearer {self.token}"
        if custom:
            h.update(custom)
        return h

    def request(
        self,
        method: str,
        path: str,
        params: Optional[Dict[str, Any]] = None,
        json_data: Optional[Any] = None,
        data: Optional[Dict[str, Any]] = None,
        files: Optional[Any] = None,
        timeout: float = 30.0,
    ) -> Tuple[int, Any]:
        url = f"{self.endpoint}{path}"
        headers = self._headers()
        try:
            with httpx.Client(timeout=timeout) as client:
                response = client.request(
                    method=method,
                    url=url,
                    params=params,
                    json=json_data,
                    data=data,
                    files=files,
                    headers=headers,
                )
                try:
                    res_json = response.json()
                except Exception:
                    res_json = {"raw": response.text}
                return response.status_code, res_json
        except httpx.ConnectError:
            return 503, {"error": f"Cannot connect to MiniCloud server at {self.endpoint}. Is it running?"}
        except httpx.TimeoutException:
            return 504, {"error": f"Request to {url} timed out after {timeout}s"}
        except Exception as e:
            return 500, {"error": str(e)}

    def get(self, path: str, params: Optional[Dict[str, Any]] = None) -> Tuple[int, Any]:
        return self.request("GET", path, params=params)

    def post(self, path: str, json_data: Optional[Any] = None, params: Optional[Dict[str, Any]] = None, files: Optional[Any] = None) -> Tuple[int, Any]:
        return self.request("POST", path, json_data=json_data, params=params, files=files)

    def delete(self, path: str, params: Optional[Dict[str, Any]] = None) -> Tuple[int, Any]:
        return self.request("DELETE", path, params=params)
