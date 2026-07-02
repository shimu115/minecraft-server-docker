package com.mcpanel.panel.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RegisterKeyRequest {
    @NotBlank
    private String name;
    @JsonProperty("keyValue")
    @NotBlank
    private String keyValue;
}
