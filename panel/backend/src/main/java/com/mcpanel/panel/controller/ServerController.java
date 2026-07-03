package com.mcpanel.panel.controller;

import com.mcpanel.panel.agent.AgentClient;
import com.mcpanel.panel.annotation.RequireInstanceAccess;
import com.mcpanel.panel.common.ApiResponse;
import com.mcpanel.panel.common.ErrorCode;
import com.mcpanel.panel.dto.CommandRequest;
import com.mcpanel.panel.entity.ApiKey;
import com.mcpanel.panel.entity.ServerInstance;
import com.mcpanel.panel.exception.McPanelException;
import com.mcpanel.panel.repository.ApiKeyRepository;
import com.mcpanel.panel.repository.ServerInstanceRepository;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/server")
public class ServerController {

    private static final Logger log = LoggerFactory.getLogger(ServerController.class);

    @Autowired
    private ServerInstanceRepository instanceRepository;
    @Autowired
    private ApiKeyRepository apiKeyRepository;
    @Autowired
    private AgentClient agentClient;

    @RequireInstanceAccess
    @PostMapping("/{id}/start-server")
    public ApiResponse<Void> startServer(@PathVariable Long id) {
        InstanceContext ctx = getContext(id);
        agentClient.startServer(ctx.baseUrl, ctx.keyValue);
        return ApiResponse.success();
    }

    @RequireInstanceAccess
    @PostMapping("/{id}/stop-server")
    public ApiResponse<Void> stopServer(@PathVariable Long id) {
        InstanceContext ctx = getContext(id);
        agentClient.stopServer(ctx.baseUrl, ctx.keyValue);
        return ApiResponse.success();
    }

    @RequireInstanceAccess
    @PostMapping("/{id}/restart-server")
    public ApiResponse<Void> restartServer(@PathVariable Long id) {
        InstanceContext ctx = getContext(id);
        agentClient.restartServer(ctx.baseUrl, ctx.keyValue);
        return ApiResponse.success();
    }

    @RequireInstanceAccess
    @GetMapping("/{id}/get-status")
    public ApiResponse<String> getStatus(@PathVariable Long id) {
        InstanceContext ctx = getContext(id);
        String result = agentClient.getStatus(ctx.baseUrl, ctx.keyValue);
        return ApiResponse.success(result);
    }

    @RequireInstanceAccess
    @PostMapping("/{id}/send-command")
    public ApiResponse<Void> sendCommand(@PathVariable Long id, @Valid @RequestBody CommandRequest request) {
        InstanceContext ctx = getContext(id);
        agentClient.sendCommand(ctx.baseUrl, ctx.keyValue, request.getCommand());
        return ApiResponse.success();
    }

    @RequireInstanceAccess
    @GetMapping(value = "/{id}/get-logs", produces = "text/event-stream")
    public SseEmitter getLogs(@PathVariable Long id, @RequestParam(defaultValue = "100") int tail) {
        InstanceContext ctx = getContext(id);
        SseEmitter emitter = new SseEmitter(0L);

        CompletableFuture.runAsync(() -> {
            HttpURLConnection conn = null;
            try {
                URI uri = new URI(ctx.baseUrl + "/api/logs?tail=" + tail);
                conn = (HttpURLConnection) uri.toURL().openConnection();
                conn.setRequestProperty("Authorization", "Bearer " + ctx.keyValue);
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(0); // no timeout for SSE

                HttpURLConnection finalConn = conn;
                emitter.onCompletion(finalConn::disconnect);
                emitter.onTimeout(finalConn::disconnect);
                emitter.onError(e -> finalConn.disconnect());

                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("data:")) {
                        String data = line.substring(5).trim();
                        if (!data.isEmpty()) {
                            emitter.send(SseEmitter.event().data(data));
                        }
                    }
                }
                emitter.complete();
            } catch (Exception e) {
                log.debug("[mc-panel] SSE 日志流异常 | {} | {}", ctx.baseUrl, e.getMessage());
                if (conn != null) conn.disconnect();
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    private InstanceContext getContext(Long instanceId) {
        ServerInstance inst = instanceRepository.findById(instanceId)
                .orElseThrow(() -> new McPanelException(ErrorCode.INSTANCE_NOT_FOUND));
        ApiKey apiKey = apiKeyRepository.findById(inst.getApiKeyId())
                .orElseThrow(() -> new McPanelException(ErrorCode.KEY_NOT_FOUND));
        String baseUrl = "http://" + inst.getHost() + ":" + inst.getPort();
        return new InstanceContext(baseUrl, apiKey.getKeyValue());
    }

    private record InstanceContext(String baseUrl, String keyValue) {}
}
