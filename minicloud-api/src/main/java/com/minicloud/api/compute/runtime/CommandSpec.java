package com.minicloud.api.compute.runtime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommandSpec {
    private String command;
    @Builder.Default
    private int timeoutSeconds = 30;
    @Builder.Default
    private int maxOutputBytes = 65536; // 64 KB output limit
    private Map<String, String> environment;
}
