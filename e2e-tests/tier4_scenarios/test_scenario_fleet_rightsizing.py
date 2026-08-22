"""
Tier 4: Scenario 2 — Fleet Rightsizing Optimization Cycle.
Features: EC2 Compute, CloudWatch Telemetry, Cost Advisor, Rightsizing Engine (R3).
Complexity: Medium
"""
import uuid
import pytest
from utils.api_client import MiniCloudApiClient


@pytest.mark.tier4
class TestScenarioFleetRightsizing:
    """End-to-End Simulation of a Cloud Cost Optimization & Fleet Rightsizing Lifecycle."""

    def test_complete_fleet_rightsizing_cycle(self, api_client: MiniCloudApiClient):
        """
        Stage 1: Provision heterogeneous fleet (T2_MICRO, T2_MEDIUM, M5_LARGE, C5_XLARGE)
        Stage 2: Inject varied CPU workloads:
                 - C5_XLARGE: 2.5% CPU (idle heavy instance) -> recommend T2_SMALL
                 - T2_MEDIUM #1: 4.0% CPU (underutilized web node) -> recommend T2_SMALL
                 - T2_MEDIUM #2: 45.0% CPU (healthy active node) -> no recommendation
                 - T2_MICRO: 3.0% CPU (already smallest type) -> no downgrade
        Stage 3: Query Rightsizing Recommendations API
        Stage 4: Validate identified instances, recommended downsizing types, and cost savings
        Stage 5: Validate total monthly dollar savings aggregate math
        """
        # Stage 1 & 2: Provision Fleet
        # 1. Idle C5_XLARGE
        r_c5 = api_client.launch_instance(name="prod-c5-batch", instance_type="C5_XLARGE", cpu_utilization=2.5)
        c5_id = r_c5.json()["id"]

        # 2. Idle T2_MEDIUM #1
        r_t2_idle = api_client.launch_instance(name="prod-t2-web-1", instance_type="T2_MEDIUM", cpu_utilization=4.0)
        t2_idle_id = r_t2_idle.json()["id"]

        # 3. Healthy T2_MEDIUM #2 (High load)
        r_t2_busy = api_client.launch_instance(name="prod-t2-web-2", instance_type="T2_MEDIUM", cpu_utilization=45.0)
        t2_busy_id = r_t2_busy.json()["id"]

        # 4. T2_MICRO (Baseline)
        r_micro = api_client.launch_instance(name="prod-t2-micro", instance_type="T2_MICRO", cpu_utilization=3.0)
        micro_id = r_micro.json()["id"]

        # Stage 3: Query Rightsizing Recommendations API
        rec_res = api_client.get_billing_recommendations()
        assert rec_res.status_code == 200
        data = rec_res.json()

        rec_map = {r["instanceId"]: r for r in data["recommendations"]}

        # Stage 4: Validate recommendations
        assert c5_id in rec_map, "C5_XLARGE with 2.5% CPU must be recommended for downsizing"
        rec_c5 = rec_map[c5_id]
        assert rec_c5["currentInstanceType"] == "C5_XLARGE"
        assert rec_c5["recommendedInstanceType"] in ["T2_SMALL", "T2_MICRO"]
        assert rec_c5["estimatedMonthlySavings"] > 50.0

        assert t2_idle_id in rec_map, "T2_MEDIUM with 4.0% CPU must be recommended for downsizing"
        rec_t2 = rec_map[t2_idle_id]
        assert rec_t2["currentInstanceType"] == "T2_MEDIUM"
        assert rec_t2["recommendedInstanceType"] == "T2_SMALL"

        assert t2_busy_id not in rec_map, "Busy T2_MEDIUM with 45% CPU must NOT be recommended"

        # Stage 5: Aggregate savings verification
        calc_total = round(sum(r["estimatedMonthlySavings"] for r in data["recommendations"]), 2)
        assert abs(data["totalEstimatedMonthlySavings"] - calc_total) <= 0.05
