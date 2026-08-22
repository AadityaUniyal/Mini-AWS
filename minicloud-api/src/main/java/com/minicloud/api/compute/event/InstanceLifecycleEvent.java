package com.minicloud.api.compute.event;

import com.minicloud.api.domain.Instance;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public abstract class InstanceLifecycleEvent extends ApplicationEvent {
    private final Instance instance;
    private final String accountId;

    public InstanceLifecycleEvent(Object source, Instance instance) {
        super(source);
        this.instance = instance;
        this.accountId = instance != null ? instance.getAccountId() : null;
    }
}
