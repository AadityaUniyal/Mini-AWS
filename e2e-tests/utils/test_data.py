"""
Test data, constant schemas, AWS S3 event templates, and pricing tables for MINI-AWS E2E tests.
"""
import uuid
from datetime import datetime, timezone

# Standard hourly pricing table for EC2 instance types in MINI-AWS
INSTANCE_HOURLY_PRICING = {
    "T2_NANO": 0.0058,
    "T2_MICRO": 0.0116,
    "T2_SMALL": 0.0230,
    "T2_MEDIUM": 0.0460,
    "M5_LARGE": 0.0960,
    "C5_XLARGE": 0.1700,
    "R5_LARGE": 0.1260,
}

HOURS_PER_MONTH = 730

def generate_s3_event_notification(
    bucket_name: str,
    object_key: str,
    object_size: int,
    etag: str = "d41d8cd98f00b204e9800998ecf8427e",
    event_name: str = "ObjectCreated:Put",
    configuration_id: str = None,
    user_id: str = "test-user-1",
    region: str = "us-east-1"
) -> dict:
    """Generates an AWS S3 compliant Event Notification JSON payload."""
    config_id = configuration_id or f"trig-{uuid.uuid4().hex[:8]}"
    return {
        "Records": [
            {
                "eventVersion": "2.1",
                "eventSource": "aws:s3",
                "awsRegion": region,
                "eventTime": datetime.now(timezone.utc).isoformat(),
                "eventName": event_name,
                "userIdentity": {"principalId": user_id},
                "s3": {
                    "s3SchemaVersion": "1.0",
                    "configurationId": config_id,
                    "bucket": {
                        "name": bucket_name,
                        "ownerIdentity": {"principalId": user_id},
                        "arn": f"arn:aws:s3:::{bucket_name}"
                    },
                    "object": {
                        "key": object_key,
                        "size": object_size,
                        "eTag": etag,
                        "sequencer": "0055AED6DCD90281E5"
                    }
                }
            }
        ]
    }

SAMPLE_PYTHON_LAMBDA = '''
import json
import sys

def handler(event, context):
    print("Lambda triggered with event:")
    print(json.dumps(event))
    records = event.get("Records", [])
    for rec in records:
        s3_info = rec.get("s3", {})
        bucket = s3_info.get("bucket", {}).get("name")
        key = s3_info.get("object", {}).get("key")
        print(f"Processed S3 object {bucket}/{key}")
    return {"statusCode": 200, "body": f"Successfully processed {len(records)} records"}
'''

SAMPLE_NODE_LAMBDA = '''
exports.handler = async (event) => {
    console.log("Processing S3 Event:", JSON.stringify(event, null, 2));
    return {
        statusCode: 200,
        body: JSON.stringify({ message: "Processed" })
    };
};
'''
