package com.minicloud.core.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InstanceRequest {
    private String name;
    private String type;    // MICRO, SMALL, MEDIUM
    private String command; // command to execute as the "instance"
}
