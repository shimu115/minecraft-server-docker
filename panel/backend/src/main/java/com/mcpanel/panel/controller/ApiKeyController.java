package com.mcpanel.panel.controller;

import com.alibaba.fastjson.JSON;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.mcpanel.panel.common.ApiResponse;
import com.mcpanel.panel.common.ErrorCode;
import com.mcpanel.panel.config.JwtAuthFilter;
import com.mcpanel.panel.exception.McPanelException;
import com.mcpanel.panel.service.ApiKeyService;
import com.mcpanel.panel.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/admin")
public class ApiKeyController {

    @Autowired
    private ApiKeyService apiKeyService;
    @Autowired
    private UserService userService;

    // === API Key 管理 ===

    @PostMapping("/keys/register")
    public ApiResponse<?> registerKey(@Valid @RequestBody RegisterKeyRequest request) {
        log.info("request: {}", JSON.toJSONString(request));
        var key = apiKeyService.registerKey(request.name, request.keyValue);
        return ApiResponse.success(Map.of(
                "id", key.getId(),
                "name", key.getName(),
                "status", key.getStatus(),
                "created_at", key.getCreatedAt()
        ));
    }

    @GetMapping("/keys/list")
    public ApiResponse<List<Map<String, Object>>> listKeys() {
        return ApiResponse.success(apiKeyService.listKeys());
    }

    @GetMapping("/keys/{id}/get")
    public ApiResponse<Map<String, Object>> getKey(@PathVariable Long id) {
        return ApiResponse.success(apiKeyService.getKey(id));
    }

    @DeleteMapping("/keys/{id}/delete")
    public ApiResponse<Void> deleteKey(@PathVariable Long id) {
        apiKeyService.deleteKey(id);
        return ApiResponse.success();
    }

    @PostMapping("/keys/{id}/revoke")
    public ApiResponse<?> revokeKey(@PathVariable Long id) {
        var key = apiKeyService.revokeKey(id);
        return ApiResponse.success(Map.of(
                "id", key.getId(),
                "status", key.getStatus()
        ));
    }

    // === 用户管理（仅 Root） ===

    @PostMapping("/users/create")
    public ApiResponse<?> createUser(@Valid @RequestBody CreateUserRequest request) {
        checkRoot();
        var user = userService.createUser(request.username, request.password, request.role);
        return ApiResponse.success(Map.of(
                "id", user.getId(),
                "username", user.getUsername(),
                "role", user.getRole(),
                "created_at", user.getCreatedAt()
        ));
    }

    @GetMapping("/users/list")
    public ApiResponse<?> listUsers() {
        checkRoot();
        var users = userService.listUsers();
        return ApiResponse.success(users.stream().map(u -> Map.of(
                "id", u.getId(),
                "username", u.getUsername(),
                "role", u.getRole(),
                "created_at", u.getCreatedAt()
        )).toList());
    }

    @PutMapping("/users/{id}/bind-instance")
    public ApiResponse<Void> bindInstance(@PathVariable Long id, @Valid @RequestBody BindInstanceRequest request) {
        checkRoot();
        userService.bindInstance(id, request.instanceId);
        return ApiResponse.success();
    }

    @DeleteMapping("/users/{id}/unbind-instance")
    public ApiResponse<Void> unbindInstance(@PathVariable Long id, @Valid @RequestBody BindInstanceRequest request) {
        checkRoot();
        userService.unbindInstance(id, request.instanceId);
        return ApiResponse.success();
    }

    // === helper ===

    private void checkRoot() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof JwtAuthFilter.JwtUserPrincipal user) || !user.isRoot()) {
            throw new McPanelException(ErrorCode.FORBIDDEN);
        }
    }

    // === request DTOs ===

    @Data
    public static class RegisterKeyRequest {
        @NotBlank private String name;
        @JsonProperty("keyValue")
        @NotBlank private String keyValue;
    }

    @Data
    public static class CreateUserRequest {
        @NotBlank private String username;
        @NotBlank private String password;
        private String role;
    }

    @Data
    public static class BindInstanceRequest {
        @NotBlank private Long instanceId;
    }
}
