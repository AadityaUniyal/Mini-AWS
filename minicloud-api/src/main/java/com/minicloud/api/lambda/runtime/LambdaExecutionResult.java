package com.minicloud.api.lambda.runtime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LambdaExecutionResult {
    private int exitCode;
    private String stdout;
    private String stderr;
    private long durationMs;
    private boolean timedOut;

    public boolean isSuccess() {
        return exitCode == 0 && !timedOut;
    }
}
