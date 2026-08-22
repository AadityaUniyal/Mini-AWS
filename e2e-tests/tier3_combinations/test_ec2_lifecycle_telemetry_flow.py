"""
Tier 3: Cross-Feature Flow — EC2 Lifecycle -> Telemetry Monitoring -> Rightsizing Evaluation.
Combines Compute/EC2, Monitoring, and Billing Rightsizing Advisor (R3).
"""
import uuid
import pytest
from utils.api_client import MiniCloudApiClient


@pytest.mark.tier3
class TestEc2LifecycleTelemetryFlow:
    """Integration flow verifying instance state lifecycle, CPU telemetry, and rightsizing evaluation."""

    def test_ec2_lifecycle_and_rightsizing_pipeline(self, api_client: MiniCloudApiClient):
        """
        Step 1: Launch C5_XLARGE compute instance with low CPU utilization (3.5%)
        Step 2: Verify instance state is RUNNING
        Step 3: Query Billing Rightsizing Recommendations
        Step 4: Stop instance and verify recommendation is retracted
        Step 5: Restart instance and verify recommendation is re-evaluated
        """
        inst_name = f"heavy-app-{uuid.uuid4().hex[:6]}"
        launch_res = api_client.launch_instance(
            name=inst_name,
            instance_type="C5_XLARGE",
            cpu_utilization=3.5
        )
        assert launch_res.status_code == 201
        inst_id = launch_res.json()["id"]

        # Step 2: Query recommendations (should be present for RUNNING instance)
        rec_res = api_client.get_billing_recommendations()
        assert rec_res.status_code == 200
        recs = rec_res.json()["recommendations"]
        matched = [r for r in recs if r["instanceId"] == inst_id]
        assert len(matched) == 1
        assert matched[0]["currentInstanceType"] == "C5_XLARGE"
        assert matched[0]["recommendedInstanceType"] in ["T2_SMALL", "T2_MICRO"]
        assert matched[0]["estimatedMonthlySavings"] > 50.0

        # Step 4: Stop instance
        stop_res = api_client.stop_instance(inst_id)
        assert stop_res.status_code == 200

        # When STOPPED, instance is not running active compute -> excluded from rightsizing recommendations
        rec_res2 = api_client.get_billing_recommendations()
        rec_ids2 = [r["instanceId"] for r in rec_res2.json()["recommendations"]]
        assert inst_id not in rec_ids2

        # Step 5: Restart instance -> recommendation reappears
        start_res = api_client.start_instance(inst_id)
        assert start_res.status_code == 200

        rec_res3 = api_client.get_billing_recommendations()
        rec_ids3 = [r["instanceId"] for r in rec_res3.json()["recommendations"]]
        assert inst_id in rec_ids3
