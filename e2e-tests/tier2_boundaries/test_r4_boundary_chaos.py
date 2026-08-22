"""
Tier 2: Boundary & Corner Cases — R4 Chaos Engineering & Self-Healing.
"""
import uuid
import pytest
from utils.api_client import MiniCloudApiClient


@pytest.mark.tier2
@pytest.mark.r4
class TestR4BoundaryChaos:
    """Boundary conditions for chaos injection, fleet limits, and edge error states."""

    def test_chaos_injection_zero_running_instances(self, api_client: MiniCloudApiClient):
        """Verify calling terminate-random when no instances exist returns 400 with a clear error."""
        resp = api_client.inject_chaos_terminate()
        assert resp.status_code in [400, 404, 409]
        data = resp.json()
        assert "error" in data or "message" in data

    def test_chaos_with_invalid_asg_id(self, api_client: MiniCloudApiClient):
        """Verify passing a non-existent AutoScalingGroup ID returns 404 Not Found."""
        resp = api_client.inject_chaos_terminate(auto_scaling_group_id="asg-nonexistent-9999")
        assert resp.status_code == 404
        assert "error" in resp.json()

    def test_rapid_consecutive_chaos_injections(self, api_client: MiniCloudApiClient):
        """Verify rapid successive chaos injections execute safely without deadlocks."""
        # Provision fleet
        for idx in range(3):
            api_client.launch_instance(name=f"rapid-node-{idx}", instance_type="T2_MEDIUM")

        # Rapidly trigger 3 chaos injections
        responses = []
        for _ in range(3):
            r = api_client.inject_chaos_terminate()
            responses.append(r)

        for r in responses:
            assert r.status_code == 200
            assert r.json()["status"] == "SELF_HEALING_COMPLETED"

    def test_chaos_only_targets_running_instances(self, api_client: MiniCloudApiClient):
        """Verify chaos monkey does not target stopped or already terminated instances."""
        # Launch and stop instance
        launch_res = api_client.launch_instance(name="stopped-target", instance_type="T2_MEDIUM")
        inst_id = launch_res.json()["id"]
        api_client.stop_instance(inst_id)

        # Launch another that is RUNNING
        launch_res2 = api_client.launch_instance(name="active-target", instance_type="T2_MEDIUM")
        running_id = launch_res2.json()["id"]

        chaos_res = api_client.inject_chaos_terminate()
        assert chaos_res.status_code == 200
        term_id = chaos_res.json()["terminatedInstanceId"]

        # Terminated instance must have been the running one, not the stopped one
        assert term_id == running_id
        assert term_id != inst_id

    def test_asg_capacity_does_not_exceed_max_bounds(self, api_client: MiniCloudApiClient):
        """Verify self-healing respects ASG max capacity bounds."""
        for idx in range(2):
            api_client.launch_instance(name=f"bound-node-{idx}", instance_type="T2_MEDIUM")

        api_client.inject_chaos_terminate()
        asg_status = api_client.get_asg_status()
        assert asg_status.status_code == 200
        data = asg_status.json()
        assert data["currentRunningReplicas"] <= data["maxCapacity"]
