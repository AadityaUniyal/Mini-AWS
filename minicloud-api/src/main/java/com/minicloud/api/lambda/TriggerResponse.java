package com.minicloud.api.lambda;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "S3 to Lambda trigger response")
public record TriggerResponse(
        @Schema(description = "Trigger unique ID", example = "550e8400-e29b-41d4-a716-446655440000")
        String id,

        @Schema(description = "Target S3 bucket name", example = "my-bucket")
        String bucketName,

        @Schema(description = "Target Lambda function name", example = "image-processor")
        String functionName,

        @Schema(description = "Configured event patterns", example = "[\"s3:ObjectCreated:*\"]")
        List<String> events,

        @Schema(description = "Trigger active status", example = "true")
        boolean enabled,

        @Schema(description = "Timestamp when the trigger was created", example = "2026-08-22T06:00:00Z")
        String createdAt
) {}
