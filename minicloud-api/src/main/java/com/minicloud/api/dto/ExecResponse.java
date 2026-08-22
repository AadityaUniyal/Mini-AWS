package com.minicloud.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecResponse {
    private String instanceId;
    private int exitCode;
    @Builder.Default
    private String stdout = "";
    @Builder.Default
    private String stderr = "";
    private long durationMs;
    private boolean timedOut;
}
