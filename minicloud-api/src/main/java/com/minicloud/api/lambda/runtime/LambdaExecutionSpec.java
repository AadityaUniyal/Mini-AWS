package com.minicloud.api.lambda.runtime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LambdaExecutionSpec {
    private UUID functionId;
    private String functionName;
    private String runtime;
    private String handler;
    private Path artifactPath;
    private String payload;
    private Map<String, String> environmentVars;
    private int memoryMb;
    private int timeoutSec;
    private String accountId;
}
