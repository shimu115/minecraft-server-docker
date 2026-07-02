package com.mcpanel.panel.controller;

import com.mcpanel.panel.common.ApiResponse;
import com.mcpanel.panel.common.ErrorCode;
import com.mcpanel.panel.config.JwtAuthFilter;
import com.mcpanel.panel.exception.McPanelException;
import com.mcpanel.panel.service.InstanceService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class InstanceController {

    private final InstanceService instanceService;

    public InstanceController(InstanceService instanceService) {
        this.instanceService = instanceService;
    }

    @PostMapping("/instances/create")
    public ApiResponse<?> createInstance(@Valid @RequestBody CreateInstanceRequest request) {
        var inst = instanceService.createInstance(
                request.name, request.apiKeyId, request.host,
                request.port, request.serverType, request.mcVersion);
        return ApiResponse.success(Map.of(
                "id", inst.getId(),
                "name", inst.getName(),
                "host", inst.getHost(),
                "port", inst.getPort(),
                "server_type", inst.getServerType(),
                "mc_version", inst.getMcVersion(),
                "status", inst.getStatus(),
                "created_at", inst.getCreatedAt()
        ));
    }

    @GetMapping("/instances/list")
    public ApiResponse<List<Map<String, Object>>> listInstances() {
        return ApiResponse.success(instanceService.listInstances());
    }

    @GetMapping("/instances/{id}/get")
    public ApiResponse<Map<String, Object>> getInstance(@PathVariable Long id) {
        return ApiResponse.success(instanceService.getInstance(id));
    }

    @PutMapping("/instances/{id}/update")
    public ApiResponse<?> updateInstance(@PathVariable Long id, @Valid @RequestBody UpdateInstanceRequest request) {
        var inst = instanceService.updateInstance(id, request.name, request.host,
                request.port, request.serverType, request.mcVersion);
        return ApiResponse.success(Map.of(
                "id", inst.getId(),
                "name", inst.getName(),
                "host", inst.getHost(),
                "port", inst.getPort(),
                "server_type", inst.getServerType(),
                "mc_version", inst.getMcVersion(),
                "updated_at", inst.getUpdatedAt()
        ));
    }

    @DeleteMapping("/instances/{id}/delete")
    public ApiResponse<Void> deleteInstance(@PathVariable Long id) {
        instanceService.deleteInstance(id);
        return ApiResponse.success();
    }

    @PutMapping("/instances/{id}/bind-key")
    public ApiResponse<?> bindKey(@PathVariable Long id, @Valid @RequestBody BindKeyRequest request) {
        var inst = instanceService.bindKey(id, request.apiKeyId);
        return ApiResponse.success(Map.of(
                "id", inst.getId(),
                "api_key_id", inst.getApiKeyId()
        ));
    }

    @PutMapping("/instances/{id}/refresh-key")
    public ApiResponse<Map<String, Object>> refreshKey(@PathVariable Long id) {
        checkRoot();
        return ApiResponse.success(instanceService.refreshKey(id));
    }

    @GetMapping("/instances/{id}/health-check")
    public ApiResponse<Map<String, Object>> healthCheck(@PathVariable Long id) {
        return ApiResponse.success(instanceService.healthCheck(id));
    }

    // === helper ===

    private void checkRoot() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof JwtAuthFilter.JwtUserPrincipal user) || !user.isRoot()) {
            throw new McPanelException(ErrorCode.FORBIDDEN);
        }
    }

    // === request DTOs ===

    public static class CreateInstanceRequest {
        @NotBlank public String name;
        @NotNull public Long apiKeyId;
        @NotBlank public String host;
        public Integer port = 25560;
        @NotBlank public String serverType;
        @NotBlank public String mcVersion;
    }

    public static class UpdateInstanceRequest {
        public String name;
        public String host;
        public Integer port;
        public String serverType;
        public String mcVersion;
    }

    public static class BindKeyRequest {
        @NotNull public Long apiKeyId;
    }
}
