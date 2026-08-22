"""Unit and integration tests for MiniCloud CLI."""

import json
import pytest
from typer.testing import CliRunner
from unittest.mock import patch, MagicMock

from minicloud.main import app
from minicloud.config import load_config, save_config, clear_auth

runner = CliRunner()


@pytest.fixture(autouse=True)
def clean_config(tmp_path, monkeypatch):
    """Use a temporary config directory for every test."""
    config_file = tmp_path / "config.json"
    monkeypatch.setattr("minicloud.config.CONFIG_DIR", tmp_path)
    monkeypatch.setattr("minicloud.config.CONFIG_FILE", config_file)
    monkeypatch.delenv("MINICLOUD_ENDPOINT", raising=False)
    monkeypatch.delenv("MINICLOUD_TOKEN", raising=False)


def test_root_help():
    result = runner.invoke(app, ["--help"])
    assert result.exit_code == 0
    assert "MINI-AWS (MiniCloud) CLI" in result.stdout
    assert "ec2" in result.stdout
    assert "s3" in result.stdout
    assert "lambda" in result.stdout
    assert "billing" in result.stdout
    assert "chaos" in result.stdout


def test_version_command():
    result = runner.invoke(app, ["--version"])
    assert result.exit_code == 0
    assert "MiniCloud CLI version" in result.stdout


def test_config_commands():
    # Test set-endpoint
    result = runner.invoke(app, ["config", "set-endpoint", "http://127.0.0.1:8080", "--token", "test-jwt"])
    assert result.exit_code == 0
    assert "Endpoint set to" in result.stdout

    # Test config view
    result = runner.invoke(app, ["config", "view", "--json"])
    assert result.exit_code == 0
    data = json.loads(result.stdout)
    assert data["endpoint"] == "http://127.0.0.1:8080"
    assert data["token"] == "test-jwt"

    # Test clear-token
    result = runner.invoke(app, ["config", "clear-token"])
    assert result.exit_code == 0
    cfg = load_config()
    assert cfg["token"] is None


@patch("minicloud.client.MiniCloudClient.post")
def test_auth_login(mock_post):
    mock_post.return_value = (200, {
        "status": "SUCCESS",
        "data": {"token": "jwt-token-123", "username": "admin", "accountId": "acc-1"}
    })
    result = runner.invoke(app, ["auth", "login", "-u", "admin", "-p", "secret", "--json"])
    assert result.exit_code == 0
    data = json.loads(result.stdout)
    assert data["status"] == "success"
    assert data["token"] == "jwt-token-123"

    cfg = load_config()
    assert cfg["token"] == "jwt-token-123"
    assert cfg["username"] == "admin"


@patch("minicloud.client.MiniCloudClient.get")
def test_ec2_list(mock_get):
    mock_get.return_value = (200, {
        "status": "SUCCESS",
        "data": [
            {
                "id": "i-123456",
                "name": "web-server",
                "instanceType": "T2_MICRO",
                "state": "RUNNING",
                "privateIp": "10.0.0.5",
                "publicIp": "54.12.34.56"
            }
        ]
    })
    # Table output
    result = runner.invoke(app, ["ec2", "list"])
    assert result.exit_code == 0
    assert "i-123456" in result.stdout
    assert "RUNNING" in result.stdout

    # JSON output
    result_json = runner.invoke(app, ["ec2", "list", "--json"])
    assert result_json.exit_code == 0
    data = json.loads(result_json.stdout)
    assert len(data) == 1
    assert data[0]["id"] == "i-123456"


@patch("minicloud.client.MiniCloudClient.post")
def test_ec2_launch(mock_post):
    mock_post.return_value = (201, {
        "status": "SUCCESS",
        "data": {
            "id": "i-abcdef",
            "name": "test-inst",
            "instanceType": "T2_SMALL",
            "state": "RUNNING"
        }
    })
    result = runner.invoke(app, ["ec2", "launch", "--type", "T2_SMALL", "--name", "test-inst"])
    assert result.exit_code == 0
    assert "Launched instance" in result.stdout
    assert "i-abcdef" in result.stdout


@patch("minicloud.client.MiniCloudClient.post")
def test_s3_make_bucket(mock_post):
    mock_post.return_value = (201, {
        "status": "SUCCESS",
        "data": {"name": "demo-bucket", "createdAt": "2026-08-22T08:00:00Z"}
    })
    result = runner.invoke(app, ["s3", "mb", "demo-bucket"])
    assert result.exit_code == 0
    assert "s3://demo-bucket" in result.stdout


@patch("minicloud.client.MiniCloudClient.get")
def test_s3_list(mock_get):
    mock_get.return_value = (200, {
        "status": "SUCCESS",
        "data": [{"name": "photos", "objectCount": 3, "createdAt": "2026-08-22T08:00:00Z"}]
    })
    result = runner.invoke(app, ["s3", "ls"])
    assert result.exit_code == 0
    assert "photos" in result.stdout


@patch("minicloud.client.MiniCloudClient.post")
def test_lambda_create_and_invoke(mock_post):
    mock_post.return_value = (201, {
        "status": "SUCCESS",
        "data": {"functionName": "resize-func", "runtime": "PYTHON3"}
    })
    result = runner.invoke(app, ["lambda", "create", "-n", "resize-func", "-r", "PYTHON3"])
    assert result.exit_code == 0
    assert "resize-func" in result.stdout

    mock_post.return_value = (200, {
        "status": "SUCCESS",
        "data": {"statusCode": 200, "result": "processed 1 image"}
    })
    inv_res = runner.invoke(app, ["lambda", "invoke", "resize-func", "-p", '{"image": "test.jpg"}'])
    assert inv_res.exit_code == 0
    assert "processed 1 image" in inv_res.stdout


@patch("minicloud.client.MiniCloudClient.post")
def test_lambda_trigger_add(mock_post):
    mock_post.return_value = (201, {
        "status": "SUCCESS",
        "data": {
            "id": "trig-12345",
            "bucketName": "uploads",
            "functionName": "processor",
            "events": ["s3:ObjectCreated:*"],
            "enabled": True
        }
    })
    result = runner.invoke(app, ["lambda", "trigger", "add", "-b", "uploads", "-f", "processor"])
    assert result.exit_code == 0
    assert "trig-12345" in result.stdout
    assert "uploads" in result.stdout


@patch("minicloud.client.MiniCloudClient.get")
def test_billing_recommendations(mock_get):
    mock_get.return_value = (200, {
        "status": "SUCCESS",
        "data": {
            "totalEstimatedMonthlySavings": 33.58,
            "recommendationsCount": 1,
            "recommendations": [
                {
                    "instanceId": "i-987654",
                    "instanceName": "idle-worker",
                    "currentInstanceType": "T2_MEDIUM",
                    "recommendedInstanceType": "T2_SMALL",
                    "averageCpuUtilization": 3.2,
                    "currentHourlyCost": 0.046,
                    "recommendedHourlyCost": 0.023,
                    "estimatedMonthlySavings": 16.79
                }
            ]
        }
    })
    result = runner.invoke(app, ["billing", "recommendations"])
    assert result.exit_code == 0
    assert "Compute Optimizer & Rightsizing Advisor" in result.stdout
    assert "T2_MEDIUM" in result.stdout
    assert "T2_SMALL" in result.stdout
    assert "$33.58" in result.stdout


@patch("minicloud.client.MiniCloudClient.post")
def test_chaos_terminate_random(mock_post):
    mock_post.return_value = (200, {
        "status": "SUCCESS",
        "data": {
            "chaosAction": "TERMINATE_INSTANCE",
            "terminatedInstanceId": "i-victim-001",
            "autoScalingGroupId": "asg-primary",
            "deficitDetected": True,
            "replacementInstanceId": "i-healed-002",
            "replacementState": "RUNNING",
            "status": "SELF_HEALING_COMPLETED"
        }
    })
    result = runner.invoke(app, ["chaos", "terminate-random", "--asg", "asg-primary"])
    assert result.exit_code == 0
    assert "Chaos Monkey Injected" in result.stdout
    assert "i-victim-001" in result.stdout
    assert "i-healed-002" in result.stdout
    assert "self-healing loop" in result.stdout
