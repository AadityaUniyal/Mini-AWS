"""
Tier 4: Scenario 3 — Chaos-Resilient Web Service Fleet.
Features: Auto Scaling Groups, Chaos Injection, Self-Healing Engine (R4), WebSocket Real-Time Alerts.
Complexity: High
"""
import pytest
from utils.api_client import MiniCloudApiClient
from utils.ws_client import WebSocketEventListener


@pytest.mark.tier4
class TestScenarioResilientWebFleet:
    """End-to-End Simulation of a High-Availability Web Fleet subjected to Chaos Engineering Outages."""

    def test_complete_chaos_and_self_healing_resilience_cycle(
        self,
        api_client: MiniCloudApiClient,
        ws_listener: WebSocketEventListener
    ):
        """
        Stage 1: Provision Web Service Fleet (3 instances running)
        Stage 2: Check ASG Health & Desired Capacity baseline
        Stage 3: Inject Chaos Round 1 -> terminate random web node
        Stage 4: Verify WebSocket real-time incident & recovery event broadcasts
        Stage 5: Verify automatic replacement launched and ASG restored to healthy capacity
        Stage 6: Inject Chaos Round 2 -> verify successive self-healing without cluster degradation
        """
        # Stage 1: Provision Fleet
        for idx in range(3):
            api_client.launch_instance(name=f"web-fleet-node-{idx}", instance_type="T2_MEDIUM")

        # Stage 2: Check ASG Health
        asg_init = api_client.get_asg_status().json()
        assert asg_init["currentRunningReplicas"] >= 2

        # Stage 3: Inject Chaos Round 1
        ws_listener.clear()
        chaos_1 = api_client.inject_chaos_terminate().json()
        assert chaos_1["status"] == "SELF_HEALING_COMPLETED"
        assert chaos_1["deficitDetected"] is True
        victim_1 = chaos_1["terminatedInstanceId"]

        # Stage 4: Verify WebSocket events
        ws_event_1 = ws_listener.wait_for_event(
            predicate=lambda e: e.get("taskType") in ["CHAOS_INSTANCE_TERMINATED", "SELF_HEALING_RECOVERY"],
            timeout=4.0
        )
        assert ws_event_1 is not None, "WebSocket should receive chaos incident broadcast"

        # Verify victim is terminated
        victim_state = api_client.get_instance(victim_1).json()
        assert victim_state["state"] == "TERMINATED"

        # Stage 5: Verify ASG restored
        asg_after_1 = api_client.get_asg_status().json()
        assert asg_after_1["currentRunningReplicas"] >= asg_after_1["desiredCapacity"]
        assert asg_after_1["status"] == "HEALTHY"

        # Stage 6: Inject Chaos Round 2 (Successive failure handling)
        ws_listener.clear()
        chaos_2 = api_client.inject_chaos_terminate().json()
        assert chaos_2["status"] == "SELF_HEALING_COMPLETED"
        victim_2 = chaos_2["terminatedInstanceId"]
        assert victim_2 != victim_1, "Successive chaos should terminate another running instance"

        asg_after_2 = api_client.get_asg_status().json()
        assert asg_after_2["status"] == "HEALTHY"
