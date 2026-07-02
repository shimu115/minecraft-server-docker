package com.mcpanel.panel.controller;

import com.alibaba.fastjson.JSON;
import com.mcpanel.panel.common.ApiResponse;
import com.mcpanel.panel.common.ErrorCode;
import com.mcpanel.panel.config.JwtAuthFilter;
import com.mcpanel.panel.dto.*;
import com.mcpanel.panel.exception.McPanelException;
import com.mcpanel.panel.service.ApiKeyService;
import com.mcpanel.panel.service.UserService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
    public ApiResponse<KeyResponse> registerKey(@Valid @RequestBody RegisterKeyRequest request) {
        log.info("request: {}", JSON.toJSONString(request));
        return ApiResponse.success(apiKeyService.registerKey(request.getName(), request.getKeyValue()));
    }

    @GetMapping("/keys/list")
    public ApiResponse<List<KeyResponse>> listKeys() {
        return ApiResponse.success(apiKeyService.listKeys());
    }

    @GetMapping("/keys/{id}/get")
    public ApiResponse<KeyResponse> getKey(@PathVariable Long id) {
        return ApiResponse.success(apiKeyService.getKey(id));
    }

    @DeleteMapping("/keys/{id}/delete")
    public ApiResponse<Void> deleteKey(@PathVariable Long id) {
        apiKeyService.deleteKey(id);
        return ApiResponse.success();
    }

    @PostMapping("/keys/{id}/revoke")
    public ApiResponse<KeyRevokeResponse> revokeKey(@PathVariable Long id) {
        return ApiResponse.success(apiKeyService.revokeKey(id));
    }

    // === 用户管理（仅 Root） ===

    @PostMapping("/users/create")
    public ApiResponse<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        checkRoot();
        return ApiResponse.success(userService.createUser(request.getUsername(), request.getPassword(), request.getRole()));
    }

    @GetMapping("/users/list")
    public ApiResponse<List<UserResponse>> listUsers() {
        checkRoot();
        return ApiResponse.success(userService.listUsers());
    }

    @PutMapping("/users/{id}/bind-instance")
    public ApiResponse<Void> bindInstance(@PathVariable Long id, @Valid @RequestBody BindInstanceRequest request) {
        checkRoot();
        userService.bindInstance(id, request.getInstanceId());
        return ApiResponse.success();
    }

    @DeleteMapping("/users/{id}/unbind-instance")
    public ApiResponse<Void> unbindInstance(@PathVariable Long id, @Valid @RequestBody BindInstanceRequest request) {
        checkRoot();
        userService.unbindInstance(id, request.getInstanceId());
        return ApiResponse.success();
    }

    private void checkRoot() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof JwtAuthFilter.JwtUserPrincipal user) || !user.isRoot()) {
            throw new McPanelException(ErrorCode.FORBIDDEN);
        }
    }
}
