"""
Tier 1: Feature R3 — Telemetry-Driven Cost & Rightsizing Advisor.
Source: ORIGINAL_REQUEST §3, PROJECT.md §Feature Inventory R3, §Interface Contracts 2.
"""
import uuid
import pytest
from utils.api_client import MiniCloudApiClient
from utils.test_data import INSTANCE_HOURLY_PRICING, HOURS_PER_MONTH


@pytest.mark.tier1
@pytest.mark.r3
class TestFeatureR3BillingAdvisor:
    """Validates Rightsizing recommendations, <10% CPU threshold, and cost savings calculations."""

    def test_billing_recommendations_endpoint_returns_200(self, api_client: MiniCloudApiClient):
        """Verify GET /api/v1/billing/recommendations returns 200 and required top-level fields."""
        resp = api_client.get_billing_recommendations()
        assert resp.status_code == 200, f"Expected 200, got {resp.status_code}: {resp.text}"
        data = resp.json()
        assert "totalEstimatedMonthlySavings" in data
        assert "recommendationsCount" in data
        assert "recommendations" in data
        assert isinstance(data["recommendations"], list)

    def test_underutilized_instance_detected_below_10_percent_cpu(self, api_client: MiniCloudApiClient):
        """Verify that an instance running with CPU < 10.0% is flagged for rightsizing."""
        inst_name = f"idle-server-{uuid.uuid4().hex[:6]}"
        launch_resp = api_client.launch_instance(
            name=inst_name,
            instance_type="T2_MEDIUM",
            cpu_utilization=3.8
        )
        assert launch_resp.status_code == 201
        inst_id = launch_resp.json()["id"]

        # Fetch recommendations
        rec_resp = api_client.get_billing_recommendations()
        assert rec_resp.status_code == 200
        data = rec_resp.json()

        matched = [r for r in data["recommendations"] if r["instanceId"] == inst_id]
        assert len(matched) == 1, f"Instance {inst_id} with 3.8% CPU was not included in recommendations"
        rec = matched[0]
        assert rec["averageCpuUtilization"] < 10.0
        assert rec["currentInstanceType"] == "T2_MEDIUM"
        assert "reason" in rec

    def test_correct_downsizing_target_type_selected(self, api_client: MiniCloudApiClient):
        """Verify appropriate downsizing target selection (e.g. C5_XLARGE / T2_MEDIUM -> T2_SMALL / T2_MICRO)."""
        launch_resp = api_client.launch_instance(
            name="oversized-app",
            instance_type="C5_XLARGE",
            cpu_utilization=2.1
        )
        inst_id = launch_resp.json()["id"]

        rec_resp = api_client.get_billing_recommendations()
        data = rec_resp.json()
        rec = next(r for r in data["recommendations"] if r["instanceId"] == inst_id)

        assert rec["recommendedInstanceType"] in ["T2_SMALL", "T2_MICRO"]
        assert rec["recommendedHourlyCost"] < rec["currentHourlyCost"]

    def test_cost_savings_calculation_accuracy(self, api_client: MiniCloudApiClient):
        """Verify hourly savings and 730-hour monthly savings mathematics."""
        launch_resp = api_client.launch_instance(
            name="math-check-server",
            instance_type="T2_MEDIUM",
            cpu_utilization=4.0
        )
        inst_id = launch_resp.json()["id"]

        rec_resp = api_client.get_billing_recommendations()
        data = rec_resp.json()
        rec = next(r for r in data["recommendations"] if r["instanceId"] == inst_id)

        expected_hourly_savings = round(rec["currentHourlyCost"] - rec["recommendedHourlyCost"], 4)
        assert abs(rec["hourlySavings"] - expected_hourly_savings) < 1e-4

        expected_monthly_savings = round(expected_hourly_savings * HOURS_PER_MONTH, 2)
        assert abs(rec["estimatedMonthlySavings"] - expected_monthly_savings) <= 0.05

    def test_total_estimated_monthly_savings_aggregate(self, api_client: MiniCloudApiClient):
        """Verify totalEstimatedMonthlySavings equals sum of individual item savings."""
        api_client.launch_instance(name="idle-1", instance_type="T2_MEDIUM", cpu_utilization=2.5)
        api_client.launch_instance(name="idle-2", instance_type="M5_LARGE", cpu_utilization=1.8)

        rec_resp = api_client.get_billing_recommendations()
        data = rec_resp.json()

        item_sum = round(sum(r["estimatedMonthlySavings"] for r in data["recommendations"]), 2)
        assert abs(data["totalEstimatedMonthlySavings"] - item_sum) <= 0.05
        assert data["recommendationsCount"] == len(data["recommendations"])

    def test_billing_summary_reflects_active_fleet(self, api_client: MiniCloudApiClient):
        """Verify GET /api/v1/billing/summary reflects running instance counts and projected costs."""
        api_client.launch_instance(name="srv-1", instance_type="T2_MICRO")
        api_client.launch_instance(name="srv-2", instance_type="T2_SMALL")

        summary_resp = api_client.get_billing_summary()
        assert summary_resp.status_code == 200
        summary = summary_resp.json()
        assert summary["activeRunningInstances"] >= 2
        assert summary["projectedMonthlyCost"] > 0
