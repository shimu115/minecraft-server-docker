package com.mcpanel.panel.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
public class KeyResponse {
    private Long id;
    private String name;
    private String keyPreview;
    private String status;
    private BoundInstanceInfo boundInstance;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
