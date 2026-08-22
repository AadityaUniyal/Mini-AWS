package com.minicloud.api.compute.event;

import com.minicloud.api.domain.Instance;

public class InstanceStartedEvent extends InstanceLifecycleEvent {
    public InstanceStartedEvent(Object source, Instance instance) {
        super(source, instance);
    }
}
