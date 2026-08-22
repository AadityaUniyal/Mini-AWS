"""
Tier 2: Boundary & Corner Cases — R3 Cost & Rightsizing Advisor.
"""
import uuid
import pytest
from utils.api_client import MiniCloudApiClient


@pytest.mark.tier2
@pytest.mark.r3
class TestR3BoundaryBilling:
    """Boundary conditions for rightsizing thresholds, CPU boundaries, and empty fleets."""

    def test_rightsizing_zero_percent_cpu(self, api_client: MiniCloudApiClient):
        """Verify an instance running with exactly 0.0% CPU produces valid recommendation without arithmetic errors."""
        launch_resp = api_client.launch_instance(
            name="zero-cpu-node",
            instance_type="T2_MEDIUM",
            cpu_utilization=0.0
        )
        inst_id = launch_resp.json()["id"]

        rec_resp = api_client.get_billing_recommendations()
        assert rec_resp.status_code == 200
        data = rec_resp.json()

        matched = [r for r in data["recommendations"] if r["instanceId"] == inst_id]
        assert len(matched) == 1
        assert matched[0]["averageCpuUtilization"] == 0.0
        assert matched[0]["estimatedMonthlySavings"] > 0

    def test_rightsizing_threshold_boundary_9_9_vs_10_0(self, api_client: MiniCloudApiClient):
        """Verify strict <10.0% threshold: 9.9% is included, 10.0% is excluded."""
        # 9.9% CPU -> should be recommended
        r1 = api_client.launch_instance(name="sub-10-node", instance_type="T2_MEDIUM", cpu_utilization=9.9)
        id_sub = r1.json()["id"]

        # 10.0% CPU -> should NOT be recommended
        r2 = api_client.launch_instance(name="at-10-node", instance_type="T2_MEDIUM", cpu_utilization=10.0)
        id_at = r2.json()["id"]

        rec_resp = api_client.get_billing_recommendations()
        data = rec_resp.json()
        rec_ids = [r["instanceId"] for r in data["recommendations"]]

        assert id_sub in rec_ids, "Instance with 9.9% CPU must be included in recommendations"
        assert id_at not in rec_ids, "Instance with 10.0% CPU must NOT be included in recommendations"

    def test_rightsizing_no_running_instances(self, api_client: MiniCloudApiClient):
        """Verify empty fleet returns 0 recommendations and 0.0 savings without errors."""
        rec_resp = api_client.get_billing_recommendations()
        assert rec_resp.status_code == 200
        data = rec_resp.json()
        assert data["recommendationsCount"] == 0
        assert data["totalEstimatedMonthlySavings"] == 0.0
        assert data["recommendations"] == []

    def test_rightsizing_stopped_instances_excluded(self, api_client: MiniCloudApiClient):
        """Verify stopped instances are excluded from active compute rightsizing recommendations."""
        launch_resp = api_client.launch_instance(name="stopped-node", instance_type="T2_MEDIUM", cpu_utilization=2.0)
        inst_id = launch_resp.json()["id"]

        # Stop the instance
        stop_resp = api_client.stop_instance(inst_id)
        assert stop_resp.status_code == 200

        rec_resp = api_client.get_billing_recommendations()
        rec_ids = [r["instanceId"] for r in rec_resp.json()["recommendations"]]
        assert inst_id not in rec_ids, "Stopped instances must not receive active compute rightsizing recommendations"

    def test_rightsizing_extreme_high_cpu_100_percent(self, api_client: MiniCloudApiClient):
        """Verify heavy-workload instance at 100.0% CPU produces zero downsizing recommendations."""
        launch_resp = api_client.launch_instance(name="heavy-load-node", instance_type="T2_MEDIUM", cpu_utilization=100.0)
        inst_id = launch_resp.json()["id"]

        rec_resp = api_client.get_billing_recommendations()
        rec_ids = [r["instanceId"] for r in rec_resp.json()["recommendations"]]
        assert inst_id not in rec_ids
