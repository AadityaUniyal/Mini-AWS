"""
Tier 2: Boundary & Corner Cases — R2 S3-to-Lambda Triggers & Events.
"""
import uuid
import pytest
from utils.api_client import MiniCloudApiClient
from utils.test_data import SAMPLE_PYTHON_LAMBDA


@pytest.mark.tier2
@pytest.mark.r2
class TestR2BoundaryTriggers:
    """Boundary conditions for S3 triggers, filtering, duplicate handling, and edge-case payloads."""

    def test_trigger_on_empty_bucket_without_objects(self, api_client: MiniCloudApiClient):
        """Verify trigger creation on an empty bucket succeeds and lists 0 initial invocations."""
        bucket_name = f"bkt-empty-{uuid.uuid4().hex[:6]}"
        func_name = f"func-empty-{uuid.uuid4().hex[:6]}"

        api_client.create_bucket(bucket_name)
        api_client.create_lambda_function(name=func_name, code=SAMPLE_PYTHON_LAMBDA)

        trig_resp = api_client.create_s3_trigger(bucket_name=bucket_name, function_name=func_name)
        assert trig_resp.status_code in [200, 201]

        # Logs should be empty
        logs_resp = api_client.get_lambda_logs(func_name)
        assert logs_resp.status_code == 200
        assert len(logs_resp.json()) == 0

    def test_trigger_with_nonexistent_bucket_returns_404(self, api_client: MiniCloudApiClient):
        """Verify attempting to create a trigger on a non-existent bucket returns 404 Not Found."""
        resp = api_client.create_s3_trigger(
            bucket_name="nonexistent-phantom-bucket-xyz",
            function_name="any-function"
        )
        assert resp.status_code == 404
        data = resp.json()
        assert "error" in data or "message" in data

    def test_trigger_duplicate_conflict_handling(self, api_client: MiniCloudApiClient):
        """Verify registering the exact same trigger twice returns 409 Conflict."""
        bucket_name = f"bkt-dup-{uuid.uuid4().hex[:6]}"
        func_name = f"func-dup-{uuid.uuid4().hex[:6]}"

        api_client.create_bucket(bucket_name)
        api_client.create_lambda_function(name=func_name, code=SAMPLE_PYTHON_LAMBDA)

        r1 = api_client.create_s3_trigger(bucket_name=bucket_name, function_name=func_name)
        assert r1.status_code in [200, 201]

        r2 = api_client.create_s3_trigger(bucket_name=bucket_name, function_name=func_name)
        assert r2.status_code in [409, 400], f"Expected 409 Conflict, got {r2.status_code}"

    def test_upload_zero_byte_file_dispatches_trigger(self, api_client: MiniCloudApiClient):
        """Verify uploading an empty (0 bytes) file dispatches trigger with size 0."""
        bucket_name = f"bkt-zerobyte-{uuid.uuid4().hex[:6]}"
        func_name = f"func-zerobyte-{uuid.uuid4().hex[:6]}"

        api_client.create_bucket(bucket_name)
        api_client.create_lambda_function(name=func_name, code=SAMPLE_PYTHON_LAMBDA)
        api_client.create_s3_trigger(bucket_name=bucket_name, function_name=func_name)

        upload_resp = api_client.upload_object(
            bucket_name=bucket_name,
            key="empty-marker.touch",
            content=b""
        )
        assert upload_resp.status_code == 200
        assert upload_resp.json()["size"] == 0

        # Verify lambda was dispatched
        logs_resp = api_client.get_lambda_logs(func_name)
        assert len(logs_resp.json()) == 1

    def test_upload_special_characters_and_deep_prefix(self, api_client: MiniCloudApiClient):
        """Verify object keys with spaces, UTF-8 chars, and deep nested folders work properly."""
        bucket_name = f"bkt-special-{uuid.uuid4().hex[:6]}"
        func_name = f"func-special-{uuid.uuid4().hex[:6]}"
        complex_key = "media/2026/summer vacation/photo #1 & 2 (final).png"

        api_client.create_bucket(bucket_name)
        api_client.create_lambda_function(name=func_name, code=SAMPLE_PYTHON_LAMBDA)
        api_client.create_s3_trigger(
            bucket_name=bucket_name,
            function_name=func_name,
            prefix="media/",
            suffix=".png"
        )

        upload_resp = api_client.upload_object(
            bucket_name=bucket_name,
            key=complex_key,
            content=b"PNG_DATA_STREAM"
        )
        assert upload_resp.status_code == 200
        assert upload_resp.json()["key"] == complex_key

        # Download back and verify key match
        dl_resp = api_client.download_object(bucket_name, complex_key)
        assert dl_resp.status_code == 200
        assert dl_resp.content == b"PNG_DATA_STREAM"

    def test_trigger_prefix_suffix_filter_mismatch_ignored(self, api_client: MiniCloudApiClient):
        """Verify trigger with .png suffix filter does NOT execute when .txt file is uploaded."""
        bucket_name = f"bkt-filter-{uuid.uuid4().hex[:6]}"
        func_name = f"func-filter-{uuid.uuid4().hex[:6]}"

        api_client.create_bucket(bucket_name)
        api_client.create_lambda_function(name=func_name, code=SAMPLE_PYTHON_LAMBDA)
        api_client.create_s3_trigger(
            bucket_name=bucket_name,
            function_name=func_name,
            prefix="images/",
            suffix=".png"
        )

        # Upload non-matching file
        upload_resp = api_client.upload_object(
            bucket_name=bucket_name,
            key="documents/notes.txt",
            content=b"Hello text"
        )
        assert upload_resp.status_code == 200

        # Ensure lambda logs are still empty
        logs_resp = api_client.get_lambda_logs(func_name)
        assert len(logs_resp.json()) == 0
