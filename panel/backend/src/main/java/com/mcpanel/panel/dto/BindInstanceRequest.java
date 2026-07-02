package com.mcpanel.panel.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 共用：bindInstance + unbindInstance */
@Data
public class BindInstanceRequest {
    @NotNull
    private Long instanceId;
}
