package com.minicloud.api.compute.runtime;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class MockComputeRuntime implements ComputeRuntime {

    private final Map<UUID, RuntimeStatus> states = new ConcurrentHashMap<>();
    private final AtomicLong pidGenerator = new AtomicLong(1000);

    @Override
    public String getRuntimeType() {
        return "MOCK";
    }

    @Override
    public RuntimeHandle launch(LaunchSpec spec) {
        long pid = pidGenerator.incrementAndGet();
        String containerId = "mock-" + spec.getInstanceId().toString().substring(0, 8);
        states.put(spec.getInstanceId(), RuntimeStatus.builder()
                .running(true)
                .pid(pid)
                .containerId(containerId)
                .statusMessage("RUNNING")
                .build());

        return RuntimeHandle.builder()
                .instanceId(spec.getInstanceId())
                .pid(pid)
                .containerId(containerId)
                .runtimeType("MOCK")
                .active(true)
                .build();
    }

    @Override
    public RuntimeStatus status(RuntimeHandle handle) {
        return states.getOrDefault(handle.getInstanceId(), RuntimeStatus.builder().running(false).statusMessage("STOPPED").build());
    }

    @Override
    public void stop(RuntimeHandle handle) {
        RuntimeStatus st = states.get(handle.getInstanceId());
        if (st != null) {
            st.setRunning(false);
            st.setStatusMessage("STOPPED");
        }
    }

    @Override
    public void terminate(RuntimeHandle handle) {
        states.remove(handle.getInstanceId());
    }

    @Override
    public CommandResult exec(RuntimeHandle handle, CommandSpec command) {
        return CommandResult.builder()
                .exitCode(0)
                .stdout("Mock execution output for command: " + command.getCommand())
                .stderr("")
                .durationMs(10)
                .build();
    }
}
