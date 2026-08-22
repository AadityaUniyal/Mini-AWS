package com.minicloud.api.lambda.runtime;

public interface LambdaRuntime {
    boolean supports(String runtimeName);
    LambdaExecutionResult execute(LambdaExecutionSpec spec);
}
