package com.minicloud.api.compute.event;

import com.minicloud.api.domain.Instance;

public class InstanceStoppedEvent extends InstanceLifecycleEvent {
    public InstanceStoppedEvent(Object source, Instance instance) {
        super(source, instance);
    }
}
