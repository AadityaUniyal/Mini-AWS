package com.minicloud.api.compute.event;

import com.minicloud.api.domain.Instance;

public class InstanceTerminatedEvent extends InstanceLifecycleEvent {
    public InstanceTerminatedEvent(Object source, Instance instance) {
        super(source, instance);
    }
}
