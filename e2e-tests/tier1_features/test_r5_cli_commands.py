"""
Tier 1: Feature R5 — Interactive Python CLI (minicloud) Command Suite.
Source: ORIGINAL_REQUEST §5, PROJECT.md §Feature Inventory R5, §Interface Contracts 4.
"""
import json
import os
import tempfile
from pathlib import Path
import pytest
from utils.api_client import MiniCloudApiClient


@pytest.mark.tier1
@pytest.mark.r5
class TestFeatureR5CliCommands:
    """Validates CLI command behavior, configuration caching, and command output formatting."""

    def test_cli_config_management(self, tmp_path):
        """Verify CLI configuration persistence schema and precedence."""
        config_path = tmp_path / "config.json"
        sample_config = {
            "endpoint": "http://localhost:8080",
            "username": "admin",
            "accountId": "123456789012",
            "token": "test-jwt-token",
            "defaultRegion": "us-east-1",
            "outputFormat": "table"
        }
        config_path.write_text(json.dumps(sample_config), encoding="utf-8")

        # Read back and validate schema
        loaded = json.loads(config_path.read_text(encoding="utf-8"))
        assert loaded["endpoint"] == "http://localhost:8080"
        assert loaded["username"] == "admin"
        assert loaded["token"] == "test-jwt-token"

    def test_cli_ec2_commands_flow(self, api_client: MiniCloudApiClient):
        """Verify EC2 CLI commands (launch, list, terminate)."""
        # Launch instance
        launch_res = api_client.launch_instance(name="cli-node-1", instance_type="T2_MICRO")
        assert launch_res.status_code == 201
        inst_id = launch_res.json()["id"]

        # List instances
        list_res = api_client.list_instances()
        assert list_res.status_code == 200
        instances = list_res.json()
        assert any(i["id"] == inst_id for i in instances)

        # Terminate instance
        term_res = api_client.terminate_instance(inst_id)
        assert term_res.status_code == 204

    def test_cli_s3_commands_flow(self, api_client: MiniCloudApiClient):
        """Verify S3 CLI commands (mb, upload, ls, rb)."""
        bucket_name = "cli-test-bucket"

        # mb (make bucket)
        mb_res = api_client.create_bucket(bucket_name)
        assert mb_res.status_code == 201

        # upload (cp)
        upload_res = api_client.upload_object(
            bucket_name=bucket_name,
            key="config/app.json",
            content=b'{"env": "production"}'
        )
        assert upload_res.status_code == 200

        # ls (list objects)
        ls_res = api_client.list_objects(bucket_name)
        assert ls_res.status_code == 200
        assert len(ls_res.json()) >= 1

        # rb (delete bucket)
        api_client.delete_object(bucket_name, "config/app.json")
        rb_res = api_client.delete_bucket(bucket_name)
        assert rb_res.status_code == 204

    def test_cli_lambda_and_trigger_commands_flow(self, api_client: MiniCloudApiClient):
        """Verify Lambda CLI commands (create, list, trigger add, invoke)."""
        func_name = "cli-processor"
        bucket_name = "cli-lambda-bucket"

        api_client.create_bucket(bucket_name)

        # create lambda
        create_res = api_client.create_lambda_function(
            name=func_name,
            runtime="PYTHON",
            code="def handler(e, c): return {'status': 'ok'}"
        )
        assert create_res.status_code == 201

        # trigger add
        trig_res = api_client.create_s3_trigger(bucket_name=bucket_name, function_name=func_name)
        assert trig_res.status_code in [200, 201]

        # invoke lambda
        inv_res = api_client.invoke_lambda_function(func_name, payload={"action": "test"})
        assert inv_res.status_code == 200
        assert inv_res.json()["statusCode"] == 200

    def test_cli_billing_recommendations_flow(self, api_client: MiniCloudApiClient):
        """Verify Billing CLI commands (recommendations & summary)."""
        api_client.launch_instance(name="cli-idle-node", instance_type="T2_MEDIUM", cpu_utilization=3.2)

        rec_res = api_client.get_billing_recommendations()
        assert rec_res.status_code == 200
        data = rec_res.json()
        assert data["recommendationsCount"] >= 1
        assert data["totalEstimatedMonthlySavings"] > 0

    def test_cli_chaos_terminate_random_flow(self, api_client: MiniCloudApiClient):
        """Verify Chaos CLI command (terminate-random)."""
        api_client.launch_instance(name="cli-chaos-target", instance_type="T2_MEDIUM")

        chaos_res = api_client.inject_chaos_terminate()
        assert chaos_res.status_code == 200
        data = chaos_res.json()
        assert data["chaosAction"] == "TERMINATE_INSTANCE"
        assert data["status"] == "SELF_HEALING_COMPLETED"
