package com.mcpanel.panel.dto;

import lombok.Data;

@Data
public class UpdateInstanceRequest {
    private String name;
    private String host;
    private Integer port;
    private String serverType;
    private String mcVersion;
}
