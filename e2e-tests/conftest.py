"""
Global Pytest fixtures and configuration for MINI-AWS E2E Test Suite.
"""
import os
import tempfile
import time
from pathlib import Path
import pytest
import requests

from utils.api_client import MiniCloudApiClient
from utils.ws_client import WebSocketEventListener
from utils.mock_backend import MockMiniCloudBackend, STATE
from utils.docker_validator import DockerComposeValidator, DockerfileValidator


def is_live_server_running(url: str) -> bool:
    """Checks whether the live Spring Boot server is reachable."""
    try:
        r = requests.get(f"{url.rstrip('/')}/actuator/health", timeout=1.0)
        return r.status_code in [200, 204]
    except Exception:
        return False


@pytest.fixture(scope="session")
def base_url():
    """
    Returns the base URL of the MiniCloud backend.
    If an external server is running at MINICLOUD_API_URL or http://localhost:8080, uses that.
    Otherwise, starts the embedded mock backend server.
    """
    configured_url = os.environ.get("MINICLOUD_API_URL", "http://localhost:8080")
    if is_live_server_running(configured_url):
        yield configured_url
    else:
        # Start embedded high-fidelity test backend
        mock = MockMiniCloudBackend()
        server_url = mock.start()
        os.environ["MINICLOUD_API_URL"] = server_url
        time.sleep(0.05)
        yield server_url
        mock.stop()


@pytest.fixture(scope="session")
def ws_url(base_url):
    """Returns the WebSocket URL for the MiniCloud backend."""
    return base_url.replace("http://", "ws://").replace("https://", "wss://")


@pytest.fixture
def api_client(base_url):
    """Provides an authenticated REST API client instance."""
    client = MiniCloudApiClient(base_url=base_url)
    client.login("admin", "password")
    return client


@pytest.fixture
def unauthenticated_client(base_url):
    """Provides an unauthenticated client instance."""
    return MiniCloudApiClient(base_url=base_url)


@pytest.fixture
def ws_listener(ws_url):
    """Provides an active WebSocketEventListener for /ws-events/tasks."""
    listener = WebSocketEventListener(ws_url=ws_url, endpoint="/ws-events/tasks")
    listener.start()
    yield listener
    listener.stop()


@pytest.fixture
def compose_validator():
    """Provides DockerComposeValidator instance."""
    return DockerComposeValidator()


@pytest.fixture
def dockerfile_validator():
    """Provides DockerfileValidator instance."""
    return DockerfileValidator()


@pytest.fixture
def sample_upload_file(tmp_path):
    """Creates a temporary sample text file for S3 upload tests."""
    fpath = tmp_path / "sample_test_doc.txt"
    fpath.write_text("Hello from MiniCloud E2E test suite!", encoding="utf-8")
    return str(fpath)


@pytest.fixture(autouse=True)
def reset_state_if_mock():
    """Ensures state isolation between tests when using mock backend."""
    STATE.reset()
    yield
