package com.minicloud.api.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PolicyStatement {

    private String sid;
    private Effect effect;
    private List<String> action;
    private List<String> resource;

    public enum Effect {
        ALLOW,
        DENY
    }
}
