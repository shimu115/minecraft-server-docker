package com.mcpanel.panel.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class KeyInfo {
    private Long id;
    private String name;
    private String keyPreview;
    private String status;
}
