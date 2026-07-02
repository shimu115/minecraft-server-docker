package com.mcpanel.panel.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class RefreshKeyResponse {
    private Long instanceId;
    private KeyInfo previousKey;
    private KeyInfo newKey;
}
