package com.minicloud.api.compute.runtime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuntimeHandle {
    private UUID instanceId;
    private String containerId;
    private Long pid;
    private String runtimeType; // DOCKER, LOCAL, MOCK
    private boolean active;
}
