package com.mcpanel.panel.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateInstanceRequest {
    @NotBlank
    private String name;
    @NotNull
    private Long apiKeyId;
    @NotBlank
    private String host;
    private Integer port = 25560;
    @NotBlank
    private String serverType;
    @NotBlank
    private String mcVersion;
}
