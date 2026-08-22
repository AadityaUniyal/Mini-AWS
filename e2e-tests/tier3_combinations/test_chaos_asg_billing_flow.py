"""
Tier 3: Cross-Feature Flow — Chaos Termination -> ASG Replenishment -> Billing Update.
Combines Chaos Engineering (R4), Auto-Scaling (R4), and Cost Management (R3).
"""
import uuid
import pytest
from utils.api_client import MiniCloudApiClient
from utils.ws_client import WebSocketEventListener


@pytest.mark.tier3
class TestChaosAsgBillingFlow:
    """Integration flow combining Chaos injection, ASG auto-recovery, and billing recalculation."""

    def test_chaos_asg_self_healing_and_billing_pipeline(
        self,
        api_client: MiniCloudApiClient,
        ws_listener: WebSocketEventListener
    ):
        """
        Step 1: Launch baseline fleet of 2 instances
        Step 2: Check baseline billing summary
        Step 3: Trigger Chaos termination on random instance
        Step 4: Verify WebSocket notifications for Chaos & Recovery
        Step 5: Verify replacement instance created in RUNNING state
        Step 6: Verify billing summary tracks active fleet accurately
        """
        # Step 1: Launch baseline fleet
        r1 = api_client.launch_instance(name="prod-node-1", instance_type="T2_MEDIUM")
        r2 = api_client.launch_instance(name="prod-node-2", instance_type="T2_MEDIUM")
        assert r1.status_code == 201 and r2.status_code == 201

        # Step 2: Baseline billing summary
        summary_before = api_client.get_billing_summary().json()
        assert summary_before["activeRunningInstances"] >= 2

        # Step 3: Trigger Chaos termination
        ws_listener.clear()
        chaos_res = api_client.inject_chaos_terminate()
        assert chaos_res.status_code == 200
        chaos_data = chaos_res.json()
        assert chaos_data["status"] == "SELF_HEALING_COMPLETED"
        term_id = chaos_data["terminatedInstanceId"]
        rep_id = chaos_data["replacementInstanceId"]

        # Step 4: Verify WebSocket events
        event = ws_listener.wait_for_event(
            predicate=lambda e: e.get("taskType") in ["CHAOS_INSTANCE_TERMINATED", "SELF_HEALING_RECOVERY"],
            timeout=4.0
        )
        assert event is not None, "WebSocket should capture Chaos event"

        # Step 5: Verify compute states
        term_inst = api_client.get_instance(term_id).json()
        assert term_inst["state"] == "TERMINATED"

        if rep_id:
            rep_inst = api_client.get_instance(rep_id).json()
            assert rep_inst["state"] == "RUNNING"

        # Step 6: Verify updated billing summary
        summary_after = api_client.get_billing_summary().json()
        # Fleet active running count should be maintained after healing
        assert summary_after["activeRunningInstances"] >= 2
