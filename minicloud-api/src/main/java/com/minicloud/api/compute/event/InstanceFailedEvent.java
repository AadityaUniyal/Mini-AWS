package com.minicloud.api.compute.event;

import com.minicloud.api.domain.Instance;
import lombok.Getter;

@Getter
public class InstanceFailedEvent extends InstanceLifecycleEvent {
    private final String errorMessage;

    public InstanceFailedEvent(Object source, Instance instance, String errorMessage) {
        super(source, instance);
        this.errorMessage = errorMessage;
    }
}
