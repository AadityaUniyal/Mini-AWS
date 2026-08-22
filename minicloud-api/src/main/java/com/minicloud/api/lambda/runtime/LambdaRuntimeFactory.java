package com.minicloud.api.lambda.runtime;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class LambdaRuntimeFactory {

    private final List<LambdaRuntime> runtimes;
    private final PythonLambdaRuntime defaultFallback;

    public LambdaRuntime getRuntime(String runtimeName) {
        if (runtimeName != null) {
            for (LambdaRuntime r : runtimes) {
                if (r.supports(runtimeName)) {
                    return r;
                }
            }
        }
        return defaultFallback;
    }
}
