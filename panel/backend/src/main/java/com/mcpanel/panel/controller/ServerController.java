package com.mcpanel.panel.controller;

import com.mcpanel.panel.agent.AgentClient;
import com.mcpanel.panel.annotation.RequireInstanceAccess;
import com.mcpanel.panel.common.ApiResponse;
import com.mcpanel.panel.common.ErrorCode;
import com.mcpanel.panel.entity.ApiKey;
import com.mcpanel.panel.entity.ServerInstance;
import com.mcpanel.panel.exception.McPanelException;
import com.mcpanel.panel.repository.ApiKeyRepository;
import com.mcpanel.panel.repository.ServerInstanceRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 服务端控制代理控制器。
 * 所有接口需要 @RequireInstanceAccess 实例级访问控制。
 */
@RestController
@RequestMapping("/api/server")
public class ServerController {

    private final ServerInstanceRepository instanceRepository;
    private final ApiKeyRepository apiKeyRepository;
    private final AgentClient agentClient;

    public ServerController(ServerInstanceRepository instanceRepository,
                            ApiKeyRepository apiKeyRepository,
                            AgentClient agentClient) {
        this.instanceRepository = instanceRepository;
        this.apiKeyRepository = apiKeyRepository;
        this.agentClient = agentClient;
    }

    @RequireInstanceAccess
    @PostMapping("/{id}/start-server")
    public ApiResponse<Void> startServer(@PathVariable Long id) {
        var ctx = getContext(id);
        agentClient.startServer(ctx.baseUrl, ctx.keyValue);
        return ApiResponse.success();
    }

    @RequireInstanceAccess
    @PostMapping("/{id}/stop-server")
    public ApiResponse<Void> stopServer(@PathVariable Long id) {
        var ctx = getContext(id);
        agentClient.stopServer(ctx.baseUrl, ctx.keyValue);
        return ApiResponse.success();
    }

    @RequireInstanceAccess
    @PostMapping("/{id}/restart-server")
    public ApiResponse<Void> restartServer(@PathVariable Long id) {
        var ctx = getContext(id);
        agentClient.restartServer(ctx.baseUrl, ctx.keyValue);
        return ApiResponse.success();
    }

    @RequireInstanceAccess
    @GetMapping("/{id}/get-status")
    public ApiResponse<?> getStatus(@PathVariable Long id) {
        var ctx = getContext(id);
        String result = agentClient.getStatus(ctx.baseUrl, ctx.keyValue);
        return ApiResponse.success(result);
    }

    @RequireInstanceAccess
    @PostMapping("/{id}/send-command")
    public ApiResponse<Void> sendCommand(@PathVariable Long id, @Valid @RequestBody CommandRequest request) {
        var ctx = getContext(id);
        agentClient.sendCommand(ctx.baseUrl, ctx.keyValue, request.command);
        return ApiResponse.success();
    }

    /**
     * SSE 日志流代理。P2 阶段返回简单提示，完整 SSE 代理留到 P3。
     */
    @RequireInstanceAccess
    @GetMapping("/{id}/get-logs")
    public ApiResponse<String> getLogs(@PathVariable Long id) {
        return ApiResponse.success("SSE log stream proxy — full implementation in P3");
    }

    // === helper ===

    private InstanceContext getContext(Long instanceId) {
        ServerInstance inst = instanceRepository.findById(instanceId)
                .orElseThrow(() -> new McPanelException(ErrorCode.INSTANCE_NOT_FOUND));

        ApiKey apiKey = apiKeyRepository.findById(inst.getApiKeyId())
                .orElseThrow(() -> new McPanelException(ErrorCode.KEY_NOT_FOUND));

        String baseUrl = "http://" + inst.getHost() + ":" + inst.getPort();
        return new InstanceContext(baseUrl, apiKey.getKeyValue());
    }

    private record InstanceContext(String baseUrl, String keyValue) {}

    public static class CommandRequest {
        @NotBlank public String command;
    }
}
