"""
Tier 3: Cross-Feature Flow — S3 Upload -> Lambda Trigger -> WebSocket Task Stream.
Combines Storage (R2), Lambda (R2), and Real-Time WebSocket Streaming (R2).
"""
import uuid
import pytest
from utils.api_client import MiniCloudApiClient
from utils.ws_client import WebSocketEventListener
from utils.test_data import SAMPLE_PYTHON_LAMBDA


@pytest.mark.tier3
class TestS3LambdaWebSocketFlow:
    """Integration flow combining S3 bucket upload, asynchronous Lambda dispatch, and WebSocket streaming."""

    def test_complete_s3_lambda_websocket_pipeline(
        self,
        api_client: MiniCloudApiClient,
        ws_listener: WebSocketEventListener
    ):
        """
        Step 1: Create S3 Bucket
        Step 2: Create Lambda Function
        Step 3: Register S3-to-Lambda Trigger for prefix 'incoming/'
        Step 4: Upload matching file to S3
        Step 5: Verify WebSocket task frames captured in real-time
        Step 6: Verify Lambda execution log persisted with output
        """
        bucket_name = f"flow-bkt-{uuid.uuid4().hex[:6]}"
        func_name = f"flow-processor-{uuid.uuid4().hex[:6]}"

        # Step 1: Create S3 Bucket
        bkt_res = api_client.create_bucket(bucket_name)
        assert bkt_res.status_code == 201

        # Step 2: Create Lambda Function
        fn_res = api_client.create_lambda_function(
            name=func_name,
            runtime="PYTHON",
            code=SAMPLE_PYTHON_LAMBDA
        )
        assert fn_res.status_code == 201

        # Step 3: Register Trigger
        trig_res = api_client.create_s3_trigger(
            bucket_name=bucket_name,
            function_name=func_name,
            prefix="incoming/",
            suffix=".json"
        )
        assert trig_res.status_code in [200, 201]
        trig_id = trig_res.json()["id"]

        # Step 4: Clear WS queue and upload file
        ws_listener.clear()
        upload_res = api_client.upload_object(
            bucket_name=bucket_name,
            key="incoming/metrics-payload.json",
            content=b'{"device_id": "sensor-99", "temp": 24.5}',
            content_type="application/json"
        )
        assert upload_res.status_code == 200

        # Step 5: Verify WebSocket event received
        ws_event = ws_listener.wait_for_event(
            predicate=lambda e: e.get("taskType") == "S3_LAMBDA_TRIGGER" and (
                e.get("metadata", {}).get("bucket") == bucket_name or
                bucket_name in e.get("message", "")
            ),
            timeout=4.0
        )
        assert ws_event is not None, "Did not receive WebSocket notification for S3 Lambda execution"
        assert ws_event.get("status") in ["RUNNING", "COMPLETED"]

        # Step 6: Verify Lambda invocation logs
        logs_res = api_client.get_lambda_logs(func_name)
        assert logs_res.status_code == 200
        logs = logs_res.json()
        assert len(logs) >= 1
        last_log = logs[-1]
        assert last_log["functionName"] == func_name
        assert last_log["status"] == "SUCCESS"
