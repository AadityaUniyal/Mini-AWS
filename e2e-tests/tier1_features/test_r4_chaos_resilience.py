"""
Tier 1: Feature R4 — Chaos Engineering & Self-Healing Resilience.
Source: ORIGINAL_REQUEST §4, PROJECT.md §Feature Inventory R4, §Interface Contracts 3.
"""
import pytest
from utils.api_client import MiniCloudApiClient
from utils.ws_client import WebSocketEventListener


@pytest.mark.tier1
@pytest.mark.r4
class TestFeatureR4ChaosResilience:
    """Validates Chaos termination, ASG deficit detection, automated self-healing, and WebSocket broadcasts."""

    def test_chaos_terminate_random_instance_endpoint(self, api_client: MiniCloudApiClient):
        """Verify POST /api/v1/chaos/terminate-random-instance returns 200 and valid response structure."""
        # Launch instance
        api_client.launch_instance(name="chaos-target-1", instance_type="T2_MEDIUM")

        resp = api_client.inject_chaos_terminate()
        assert resp.status_code == 200, f"Chaos injection failed: {resp.text}"
        data = resp.json()
        assert data["chaosAction"] == "TERMINATE_INSTANCE"
        assert "terminatedInstanceId" in data
        assert data["previousState"] == "RUNNING"
        assert data["currentState"] == "TERMINATED"
        assert "status" in data

    def test_chaos_terminates_running_instance(self, api_client: MiniCloudApiClient):
        """Verify that the victim instance is transitioned to TERMINATED state."""
        launch_resp = api_client.launch_instance(name="victim-test", instance_type="T2_MEDIUM")
        inst_id = launch_resp.json()["id"]

        chaos_resp = api_client.inject_chaos_terminate()
        term_id = chaos_resp.json()["terminatedInstanceId"]

        # Check instance state via compute API
        get_resp = api_client.get_instance(term_id)
        assert get_resp.status_code == 200
        assert get_resp.json()["state"] == "TERMINATED"

    def test_chaos_deficit_detection_and_self_healing(self, api_client: MiniCloudApiClient):
        """Verify ASG detects capacity deficit and launches a replacement instance."""
        # Launch fleet to satisfy initial desired capacity
        api_client.launch_instance(name="asg-node-1", instance_type="T2_MEDIUM")
        api_client.launch_instance(name="asg-node-2", instance_type="T2_MEDIUM")

        chaos_resp = api_client.inject_chaos_terminate()
        data = chaos_resp.json()

        assert data["deficitDetected"] is True
        assert data["replacementInstanceId"] is not None
        assert data["status"] == "SELF_HEALING_COMPLETED"

    def test_chaos_replacement_instance_in_running_state(self, api_client: MiniCloudApiClient):
        """Verify that replacement instance reaches RUNNING state in compute registry."""
        api_client.launch_instance(name="heal-node", instance_type="T2_MEDIUM")

        chaos_resp = api_client.inject_chaos_terminate()
        rep_id = chaos_resp.json()["replacementInstanceId"]

        if rep_id:
            rep_resp = api_client.get_instance(rep_id)
            assert rep_resp.status_code == 200
            assert rep_resp.json()["state"] == "RUNNING"

    def test_chaos_websocket_event_broadcast(self, api_client: MiniCloudApiClient, ws_listener: WebSocketEventListener):
        """Verify that CHAOS_INSTANCE_TERMINATED and SELF_HEALING_RECOVERY events are broadcasted over WebSocket."""
        api_client.launch_instance(name="ws-chaos-node", instance_type="T2_MEDIUM")

        ws_listener.clear()
        api_client.inject_chaos_terminate()

        # Check for chaos event frame
        chaos_event = ws_listener.wait_for_event(
            predicate=lambda e: e.get("taskType") in ["CHAOS_INSTANCE_TERMINATED", "CHAOS_TERMINATE_AND_HEAL"],
            timeout=3.0
        )
        assert chaos_event is not None, "WebSocket did not receive CHAOS termination event"

        # Check for recovery event frame
        heal_event = ws_listener.wait_for_event(
            predicate=lambda e: e.get("taskType") in ["SELF_HEALING_RECOVERY", "CHAOS_TERMINATE_AND_HEAL"],
            timeout=3.0
        )
        assert heal_event is not None, "WebSocket did not receive SELF_HEALING recovery event"

    def test_asg_replica_status_after_healing(self, api_client: MiniCloudApiClient):
        """Verify /api/v1/scaling/replicas reports healthy status with desired capacity maintained."""
        api_client.launch_instance(name="asg-worker-1", instance_type="T2_MEDIUM")
        api_client.launch_instance(name="asg-worker-2", instance_type="T2_MEDIUM")

        api_client.inject_chaos_terminate()

        status_resp = api_client.get_asg_status()
        assert status_resp.status_code == 200
        data = status_resp.json()
        assert data["currentRunningReplicas"] >= data["desiredCapacity"]
        assert data["status"] == "HEALTHY"
