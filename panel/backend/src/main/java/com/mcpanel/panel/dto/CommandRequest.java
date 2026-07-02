package com.mcpanel.panel.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CommandRequest {
    @NotBlank
    private String command;
}
