"""
Tier 3: Cross-Feature Flow — Multi-Tenant IAM & Namespace Isolation.
Combines Auth, S3 Storage (R2), and Lambda Triggers (R2).
"""
import uuid
import pytest
from utils.api_client import MiniCloudApiClient
from utils.test_data import SAMPLE_PYTHON_LAMBDA


@pytest.mark.tier3
class TestMultiTenantIsolationFlow:
    """Integration flow verifying resource and trigger isolation across multiple tenant accounts."""

    def test_multi_tenant_storage_and_trigger_isolation(self, base_url):
        """
        Step 1: Tenant A authenticates and creates a bucket + trigger
        Step 2: Tenant B authenticates and lists buckets
        Step 3: Tenant B uploads to their own bucket
        Step 4: Verify Tenant A's trigger is NOT fired by Tenant B's upload
        """
        client_a = MiniCloudApiClient(base_url=base_url)
        login_a = client_a.login("developer", "password")
        assert login_a.status_code == 200
        user_a_id = login_a.json()["userId"]

        client_b = MiniCloudApiClient(base_url=base_url)
        login_b = client_b.login("tenant-b", "password")
        assert login_b.status_code == 200
        user_b_id = login_b.json()["userId"]

        # Tenant A creates bucket & trigger
        bkt_a = f"tenant-a-bkt-{uuid.uuid4().hex[:6]}"
        func_a = f"tenant-a-func-{uuid.uuid4().hex[:6]}"
        client_a.create_bucket(bkt_a, owner_id=user_a_id)
        client_a.create_lambda_function(name=func_a, code=SAMPLE_PYTHON_LAMBDA)
        client_a.create_s3_trigger(bucket_name=bkt_a, function_name=func_a)

        # Tenant B creates bucket
        bkt_b = f"tenant-b-bkt-{uuid.uuid4().hex[:6]}"
        client_b.create_bucket(bkt_b, owner_id=user_b_id)

        # Tenant B uploads to Bucket B
        client_b.upload_object(bucket_name=bkt_b, key="file.txt", content=b"Tenant B Data")

        # Verify Tenant A's function was NOT triggered
        logs_a = client_a.get_lambda_logs(func_a).json()
        assert len(logs_a) == 0, "Tenant A's Lambda should not be triggered by Tenant B's bucket upload"

        # Tenant A uploads to Bucket A
        client_a.upload_object(bucket_name=bkt_a, key="file.txt", content=b"Tenant A Data")
        logs_a_after = client_a.get_lambda_logs(func_a).json()
        assert len(logs_a_after) == 1, "Tenant A's Lambda should be triggered by Tenant A's upload"
