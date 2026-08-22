package com.minicloud.api.compute.runtime;

public interface ComputeRuntime {
    RuntimeHandle launch(LaunchSpec spec);
    RuntimeStatus status(RuntimeHandle handle);
    void stop(RuntimeHandle handle);
    void terminate(RuntimeHandle handle);
    CommandResult exec(RuntimeHandle handle, CommandSpec command);
    String getRuntimeType();
}
