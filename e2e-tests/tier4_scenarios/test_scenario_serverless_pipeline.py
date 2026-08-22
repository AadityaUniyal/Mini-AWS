"""
Tier 4: Scenario 1 — Serverless Thumbnail Processing Pipeline.
Features: S3 Storage, Lambda Serverless, S3 Event Triggers, WebSocket Streaming.
Complexity: High
"""
import uuid
import pytest
from utils.api_client import MiniCloudApiClient
from utils.ws_client import WebSocketEventListener
from utils.test_data import SAMPLE_PYTHON_LAMBDA


@pytest.mark.tier4
class TestScenarioServerlessPipeline:
    """End-to-End Simulation of a Serverless Image Processing and Thumbnail Pipeline."""

    def test_complete_serverless_thumbnail_pipeline(
        self,
        api_client: MiniCloudApiClient,
        ws_listener: WebSocketEventListener
    ):
        """
        Stage 1: Provision Source and Destination S3 Buckets
        Stage 2: Deploy Thumbnail Generation Serverless Function
        Stage 3: Bind S3 Upload Trigger to Lambda for PNG images
        Stage 4: Upload non-matching document -> verify trigger is skipped
        Stage 5: Upload valid PNG image -> verify trigger executes asynchronously
        Stage 6: Monitor live WebSocket stream for task execution progress
        Stage 7: Verify Lambda invocation logs and processed outputs
        """
        raw_bucket = f"user-uploads-{uuid.uuid4().hex[:6]}"
        proc_bucket = f"processed-thumbnails-{uuid.uuid4().hex[:6]}"
        fn_name = f"generate-thumbnail-{uuid.uuid4().hex[:6]}"

        # Stage 1: Provision Buckets
        r1 = api_client.create_bucket(raw_bucket)
        r2 = api_client.create_bucket(proc_bucket)
        assert r1.status_code == 201 and r2.status_code == 201

        # Stage 2: Deploy Lambda Function
        fn_code = '''
def handler(event, context):
    records = event.get("Records", [])
    for rec in records:
        key = rec["s3"]["object"]["key"]
        print(f"Generating 128x128 thumbnail for {key}")
    return {"status": "SUCCESS", "thumbnailsCreated": len(records)}
'''
        fn_res = api_client.create_lambda_function(
            name=fn_name,
            runtime="PYTHON",
            code=fn_code,
            memory_mb=256,
            timeout_sec=15
        )
        assert fn_res.status_code == 201

        # Stage 3: Bind Trigger with prefix & suffix
        trig_res = api_client.create_s3_trigger(
            bucket_name=raw_bucket,
            function_name=fn_name,
            prefix="uploads/",
            suffix=".png"
        )
        assert trig_res.status_code in [200, 201]

        # Stage 4: Upload non-matching file (PDF document)
        api_client.upload_object(
            bucket_name=raw_bucket,
            key="docs/terms.pdf",
            content=b"%PDF-1.4 Fake PDF Content",
            content_type="application/pdf"
        )
        # Ensure function was not invoked
        logs_before = api_client.get_lambda_logs(fn_name).json()
        assert len(logs_before) == 0

        # Stage 5: Upload valid PNG image
        ws_listener.clear()
        upload_res = api_client.upload_object(
            bucket_name=raw_bucket,
            key="uploads/user-avatar.png",
            content=b"\x89PNG\r\n\x1a\nRawPngImageData",
            content_type="image/png"
        )
        assert upload_res.status_code == 200

        # Stage 6: Verify WebSocket stream
        ws_event = ws_listener.wait_for_event(
            predicate=lambda e: e.get("taskType") == "S3_LAMBDA_TRIGGER" and fn_name in str(e),
            timeout=4.0
        )
        assert ws_event is not None, "WebSocket did not receive image processing task notification"

        # Stage 7: Verify Lambda logs
        logs_after = api_client.get_lambda_logs(fn_name).json()
        assert len(logs_after) >= 1
        assert logs_after[-1]["status"] == "SUCCESS"
