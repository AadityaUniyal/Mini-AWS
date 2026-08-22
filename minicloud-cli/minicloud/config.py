"""Configuration management for MiniCloud CLI."""

import json
import os
from pathlib import Path
from typing import Optional, Dict, Any

CONFIG_DIR = Path.home() / ".minicloud"
CONFIG_FILE = CONFIG_DIR / "config.json"
DEFAULT_ENDPOINT = "http://localhost:8080"


def get_config_dir() -> Path:
    CONFIG_DIR.mkdir(parents=True, exist_ok=True)
    return CONFIG_DIR


def load_config() -> Dict[str, Any]:
    config: Dict[str, Any] = {
        "endpoint": os.environ.get("MINICLOUD_ENDPOINT", DEFAULT_ENDPOINT),
        "token": os.environ.get("MINICLOUD_TOKEN", None),
        "username": None,
        "account_id": None,
    }
    if CONFIG_FILE.exists():
        try:
            with open(CONFIG_FILE, "r", encoding="utf-8") as f:
                data = json.load(f)
                if isinstance(data, dict):
                    if data.get("endpoint") and not os.environ.get("MINICLOUD_ENDPOINT"):
                        config["endpoint"] = data["endpoint"]
                    if data.get("token") and not os.environ.get("MINICLOUD_TOKEN"):
                        config["token"] = data["token"]
                    config["username"] = data.get("username", config["username"])
                    config["account_id"] = data.get("account_id", config["account_id"])
        except Exception:
            pass
    return config


def save_config(endpoint: Optional[str] = None, token: Optional[str] = None,
                username: Optional[str] = None, account_id: Optional[str] = None) -> Dict[str, Any]:
    get_config_dir()
    current = load_config()
    if endpoint is not None:
        current["endpoint"] = endpoint.rstrip("/")
    if token is not None:
        current["token"] = token
    if username is not None:
        current["username"] = username
    if account_id is not None:
        current["account_id"] = account_id

    with open(CONFIG_FILE, "w", encoding="utf-8") as f:
        json.dump(current, f, indent=2)
    return current


def clear_auth() -> Dict[str, Any]:
    get_config_dir()
    current = load_config()
    current["token"] = None
    current["username"] = None
    with open(CONFIG_FILE, "w", encoding="utf-8") as f:
        json.dump(current, f, indent=2)
    return current
