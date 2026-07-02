package com.mcpanel.panel.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class HealthCheckResponse {
    private Long instanceId;
    private String instanceName;
    private String goApiHealth;
}
