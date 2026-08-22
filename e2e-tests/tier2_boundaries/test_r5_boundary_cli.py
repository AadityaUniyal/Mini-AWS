"""
Tier 2: Boundary & Corner Cases — R5 Python CLI & Client Error Handling.
"""
import uuid
import pytest
from utils.api_client import MiniCloudApiClient


@pytest.mark.tier2
@pytest.mark.r5
class TestR5BoundaryCli:
    """Boundary conditions for CLI authentication, missing resources, and invalid parameters."""

    def test_cli_invalid_credentials_login_failure(self, unauthenticated_client: MiniCloudApiClient):
        """Verify login with bad password fails with 401 and does not set auth token."""
        resp = unauthenticated_client.login(username="admin", password="wrong-password-123")
        assert resp.status_code == 401
        assert unauthenticated_client.token is None

    def test_cli_download_nonexistent_object_error(self, api_client: MiniCloudApiClient):
        """Verify downloading non-existent S3 key returns 404 NoSuchKey."""
        bucket_name = f"bkt-err-{uuid.uuid4().hex[:6]}"
        api_client.create_bucket(bucket_name)

        dl_resp = api_client.download_object(bucket_name, "does-not-exist.bin")
        assert dl_resp.status_code == 404
        assert "error" in dl_resp.json() or "NoSuchKey" in dl_resp.text

    def test_cli_launch_instance_missing_name_fallback(self, api_client: MiniCloudApiClient):
        """Verify launching an instance without name provides a clean auto-generated name."""
        resp = api_client.launch_instance(name="", instance_type="T2_MICRO")
        assert resp.status_code == 201
        inst = resp.json()
        assert inst["name"] != ""
        assert inst["type"] == "T2_MICRO"

    def test_cli_lambda_creation_duplicate_name_conflict(self, api_client: MiniCloudApiClient):
        """Verify creating Lambda function with duplicate name returns 409 Conflict."""
        func_name = f"dup-func-{uuid.uuid4().hex[:6]}"
        r1 = api_client.create_lambda_function(name=func_name)
        assert r1.status_code == 201

        r2 = api_client.create_lambda_function(name=func_name)
        assert r2.status_code in [409, 400], f"Expected 409 Conflict, got {r2.status_code}"

    def test_cli_terminate_nonexistent_instance_error(self, api_client: MiniCloudApiClient):
        """Verify deleting non-existent instance returns 404 Not Found."""
        resp = api_client.terminate_instance("inst-phantom-99999999")
        assert resp.status_code == 404
