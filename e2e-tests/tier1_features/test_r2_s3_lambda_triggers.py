"""
Tier 1: Feature R2 — S3-to-Lambda Asynchronous Event Triggers & WebSocket Streaming.
Source: ORIGINAL_REQUEST §2, PROJECT.md §Feature Inventory R2, §Interface Contracts 1.
"""
import uuid
import pytest
from utils.api_client import MiniCloudApiClient
from utils.ws_client import WebSocketEventListener
from utils.test_data import SAMPLE_PYTHON_LAMBDA, generate_s3_event_notification


@pytest.mark.tier1
@pytest.mark.r2
class TestFeatureR2S3LambdaTriggers:
    """Validates S3 trigger registration, deletion, event dispatching, and WebSocket streaming."""

    def test_s3_trigger_registration_and_retrieval(self, api_client: MiniCloudApiClient):
        """Verify registering an S3-to-Lambda trigger and retrieving it via REST API."""
        bucket_name = f"bkt-trig-test-{uuid.uuid4().hex[:6]}"
        func_name = f"func-thumb-{uuid.uuid4().hex[:6]}"

        # Setup bucket and lambda
        bkt_resp = api_client.create_bucket(bucket_name)
        assert bkt_resp.status_code == 201, f"Failed to create bucket: {bkt_resp.text}"

        func_resp = api_client.create_lambda_function(
            name=func_name,
            runtime="PYTHON",
            code=SAMPLE_PYTHON_LAMBDA
        )
        assert func_resp.status_code == 201, f"Failed to create Lambda: {func_resp.text}"

        # Register trigger
        trig_resp = api_client.create_s3_trigger(
            bucket_name=bucket_name,
            function_name=func_name,
            events=["s3:ObjectCreated:*"],
            prefix="uploads/",
            suffix=".png"
        )
        assert trig_resp.status_code in [200, 201], f"Failed to create trigger: {trig_resp.text}"
        data = trig_resp.json()
        assert "id" in data, "Trigger response must contain 'id'"
        assert data["bucketName"] == bucket_name
        assert data["functionName"] == func_name
        assert data.get("enabled", True) is True

        # Retrieve triggers
        list_resp = api_client.list_s3_triggers(bucket_name=bucket_name)
        assert list_resp.status_code == 200
        triggers = list_resp.json()
        assert any(t["id"] == data["id"] for t in triggers)

    def test_s3_trigger_deletion(self, api_client: MiniCloudApiClient):
        """Verify deleting an existing S3 trigger returns 204 and removes it."""
        bucket_name = f"bkt-del-trig-{uuid.uuid4().hex[:6]}"
        func_name = f"func-del-{uuid.uuid4().hex[:6]}"

        api_client.create_bucket(bucket_name)
        api_client.create_lambda_function(name=func_name, code=SAMPLE_PYTHON_LAMBDA)

        trig_resp = api_client.create_s3_trigger(bucket_name=bucket_name, function_name=func_name)
        trig_id = trig_resp.json()["id"]

        # Delete trigger
        del_resp = api_client.delete_s3_trigger(trig_id)
        assert del_resp.status_code == 204, f"Expected 204 No Content, got {del_resp.status_code}"

        # Confirm not in list
        list_resp = api_client.list_s3_triggers(bucket_name=bucket_name)
        assert list_resp.status_code == 200
        assert not any(t["id"] == trig_id for t in list_resp.json())

    def test_s3_upload_dispatches_trigger_event(self, api_client: MiniCloudApiClient):
        """Verify uploading an object triggers asynchronous Lambda dispatch."""
        bucket_name = f"bkt-event-test-{uuid.uuid4().hex[:6]}"
        func_name = f"func-event-{uuid.uuid4().hex[:6]}"

        api_client.create_bucket(bucket_name)
        api_client.create_lambda_function(name=func_name, code=SAMPLE_PYTHON_LAMBDA)
        api_client.create_s3_trigger(bucket_name=bucket_name, function_name=func_name)

        # Upload object
        upload_resp = api_client.upload_object(
            bucket_name=bucket_name,
            key="test-image.png",
            content=b"\x89PNG\r\n\x1a\nFakeImageData",
            content_type="image/png"
        )
        assert upload_resp.status_code == 200
        data = upload_resp.json()
        assert data.get("bucket") == bucket_name
        assert data.get("key") == "test-image.png"

        # Check that Lambda log was generated
        logs_resp = api_client.get_lambda_logs(func_name)
        assert logs_resp.status_code == 200
        logs = logs_resp.json()
        assert len(logs) >= 1
        assert logs[-1]["functionName"] == func_name

    def test_aws_s3_event_notification_json_structure(self):
        """Verify AWS S3 Event Notification schema conformity."""
        payload = generate_s3_event_notification(
            bucket_name="production-assets",
            object_key="avatars/user-42.jpg",
            object_size=4096,
            etag="5d41402abc4b2a76b9719d911017c592"
        )

        assert "Records" in payload
        assert isinstance(payload["Records"], list) and len(payload["Records"]) == 1
        rec = payload["Records"][0]
        assert rec["eventVersion"] == "2.1"
        assert rec["eventSource"] == "aws:s3"
        assert rec["eventName"] == "ObjectCreated:Put"
        assert "s3" in rec
        assert rec["s3"]["bucket"]["name"] == "production-assets"
        assert rec["s3"]["object"]["key"] == "avatars/user-42.jpg"
        assert rec["s3"]["object"]["size"] == 4096
        assert rec["s3"]["object"]["eTag"] == "5d41402abc4b2a76b9719d911017c592"

    def test_s3_trigger_websocket_task_streaming(self, api_client: MiniCloudApiClient, ws_listener: WebSocketEventListener):
        """Verify that S3 trigger execution emits task notifications over WebSocket (/ws-events/tasks)."""
        bucket_name = f"bkt-ws-test-{uuid.uuid4().hex[:6]}"
        func_name = f"func-ws-{uuid.uuid4().hex[:6]}"

        api_client.create_bucket(bucket_name)
        api_client.create_lambda_function(name=func_name, code=SAMPLE_PYTHON_LAMBDA)
        api_client.create_s3_trigger(bucket_name=bucket_name, function_name=func_name)

        ws_listener.clear()

        # Upload file to trigger flow
        api_client.upload_object(
            bucket_name=bucket_name,
            key="stream-test.json",
            content=b'{"action": "test"}'
        )

        # Wait for WebSocket notification frame
        event = ws_listener.wait_for_event(
            predicate=lambda e: e.get("taskType") == "S3_LAMBDA_TRIGGER" and (
                e.get("status") in ["RUNNING", "COMPLETED"]
            ),
            timeout=3.0
        )
        assert event is not None, "WebSocket did not receive S3_LAMBDA_TRIGGER task event within timeout"
        assert "taskId" in event
        assert event.get("taskType") == "S3_LAMBDA_TRIGGER"
