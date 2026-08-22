package com.minicloud.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecRequest {
    @NotBlank(message = "Command is required")
    private String command;

    @Builder.Default
    private int timeoutSeconds = 30;

    @Builder.Default
    private int maxOutputBytes = 65536;
}
