package com.mcpanel.panel.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UserInfoResponse {
    @JsonProperty("userId")
    private Long userId;
    private String username;
    private String role;
}
