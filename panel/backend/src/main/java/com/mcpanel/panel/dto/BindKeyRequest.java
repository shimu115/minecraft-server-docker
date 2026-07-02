package com.mcpanel.panel.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class BindKeyRequest {
    @NotNull
    private Long apiKeyId;
}
