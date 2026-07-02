package com.mcpanel.panel.controller;

import com.mcpanel.panel.common.ApiResponse;
import com.mcpanel.panel.common.ErrorCode;
import com.mcpanel.panel.config.JwtAuthFilter;
import com.mcpanel.panel.dto.*;
import com.mcpanel.panel.exception.McPanelException;
import com.mcpanel.panel.service.InstanceService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class InstanceController {

    @Autowired
    private InstanceService instanceService;

    @PostMapping("/instances/create")
    public ApiResponse<InstanceResponse> createInstance(@Valid @RequestBody CreateInstanceRequest request) {
        return ApiResponse.success(instanceService.createInstance(
                request.getName(), request.getApiKeyId(), request.getHost(),
                request.getPort(), request.getServerType(), request.getMcVersion()));
    }

    @GetMapping("/instances/list")
    public ApiResponse<List<InstanceResponse>> listInstances() {
        return ApiResponse.success(instanceService.listInstances());
    }

    @GetMapping("/instances/{id}/get")
    public ApiResponse<InstanceResponse> getInstance(@PathVariable Long id) {
        return ApiResponse.success(instanceService.getInstance(id));
    }

    @PutMapping("/instances/{id}/update")
    public ApiResponse<InstanceResponse> updateInstance(@PathVariable Long id, @Valid @RequestBody UpdateInstanceRequest request) {
        return ApiResponse.success(instanceService.updateInstance(id, request.getName(), request.getHost(),
                request.getPort(), request.getServerType(), request.getMcVersion()));
    }

    @DeleteMapping("/instances/{id}/delete")
    public ApiResponse<Void> deleteInstance(@PathVariable Long id) {
        instanceService.deleteInstance(id);
        return ApiResponse.success();
    }

    @PutMapping("/instances/{id}/bind-key")
    public ApiResponse<InstanceBindKeyResponse> bindKey(@PathVariable Long id, @Valid @RequestBody BindKeyRequest request) {
        return ApiResponse.success(instanceService.bindKey(id, request.getApiKeyId()));
    }

    @PutMapping("/instances/{id}/refresh-key")
    public ApiResponse<RefreshKeyResponse> refreshKey(@PathVariable Long id) {
        checkRoot();
        return ApiResponse.success(instanceService.refreshKey(id));
    }

    @GetMapping("/instances/{id}/health-check")
    public ApiResponse<HealthCheckResponse> healthCheck(@PathVariable Long id) {
        return ApiResponse.success(instanceService.healthCheck(id));
    }

    private void checkRoot() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof JwtAuthFilter.JwtUserPrincipal user) || !user.isRoot()) {
            throw new McPanelException(ErrorCode.FORBIDDEN);
        }
    }
}
