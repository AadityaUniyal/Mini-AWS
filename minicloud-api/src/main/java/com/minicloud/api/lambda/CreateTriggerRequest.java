package com.minicloud.api.lambda;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

@Schema(description = "Request body to register an S3 to Lambda event trigger")
public record CreateTriggerRequest(
        @Schema(description = "Target S3 bucket name", example = "my-bucket", requiredMode = Schema.RequiredMode.REQUIRED)
        String bucketName,

        @Schema(description = "Target Lambda function name", example = "image-processor", requiredMode = Schema.RequiredMode.REQUIRED)
        String functionName,

        @Schema(description = "List of S3 event types", example = "[\"s3:ObjectCreated:*\"]")
        List<String> events,

        @Schema(description = "Whether the trigger is active", example = "true")
        Boolean enabled,

        @Schema(description = "Optional user ID")
        UUID userId
) {}
