package com.mcpanel.panel.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
public class InstanceResponse {
    private Long id;
    private String name;
    private String host;
    private Integer port;
    private String serverType;
    private String mcVersion;
    private KeyInfo apiKey;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
