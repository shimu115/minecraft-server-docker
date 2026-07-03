package com.mcpanel.panel.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcpanel.panel.common.ErrorCode;
import com.mcpanel.panel.exception.McPanelException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.time.Duration;

/**
 * Go API HTTP 客户端。
 * Spring Boot 与 Go API 之间的唯一通信通道。
 * 自动附加 Authorization: Bearer <keyValue>。
 */
@Component
public class AgentClient {

    private static final Logger log = LoggerFactory.getLogger(AgentClient.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AgentClient() {
        this.restClient = RestClient.builder()
                .requestFactory(clientHttpRequestFactory())
                .build();
    }

    private static org.springframework.http.client.JdkClientHttpRequestFactory clientHttpRequestFactory() {
        org.springframework.http.client.JdkClientHttpRequestFactory factory = new org.springframework.http.client.JdkClientHttpRequestFactory();
        factory.setReadTimeout(Duration.ofSeconds(30));
        return factory;
    }

    // === Go API 方法 ===

    /**
     * 健康检查。Go API 无需认证。
     */
    public String health(String baseUrl) {
        try {
            String body = restClient.get()
                    .uri(baseUrl + "/api/health")
                    .retrieve()
                    .body(String.class);
            return extractStatus(body);
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    /**
     * 启动 MC 服务端。
     */
    public void startServer(String baseUrl, String keyValue) {
        post(baseUrl, "/api/server/start", keyValue, null);
    }

    /**
     * 停止 MC 服务端。
     */
    public void stopServer(String baseUrl, String keyValue) {
        post(baseUrl, "/api/server/stop", keyValue, null);
    }

    /**
     * 重启 MC 服务端。
     */
    public void restartServer(String baseUrl, String keyValue) {
        post(baseUrl, "/api/server/restart", keyValue, null);
    }

    /**
     * 获取服务端状态。
     */
    public String getStatus(String baseUrl, String keyValue) {
        try {
            String body = restClient.get()
                    .uri(baseUrl + "/api/server/status")
                    .header("Authorization", "Bearer " + keyValue)
                    .retrieve()
                    .body(String.class);
            return body;
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    /**
     * 发送 MC 指令。
     */
    public void sendCommand(String baseUrl, String keyValue, String command) {
        String body;
        try {
            body = objectMapper.writeValueAsString(new CommandBody(command));
        } catch (Exception e) {
            throw new McPanelException(ErrorCode.INTERNAL_ERROR);
        }
        post(baseUrl, "/api/command", keyValue, body);
    }

    /**
     * 刷新 API Key。调用 Go API POST /api/auth/refresh，返回新 Key。
     */
    public String refreshKey(String baseUrl, String keyValue) {
        try {
            String body = restClient.post()
                    .uri(baseUrl + "/api/auth/refresh")
                    .header("Authorization", "Bearer " + keyValue)
                    .retrieve()
                    .body(String.class);

            JsonNode node = objectMapper.readTree(body);
            JsonNode data = node.get("data");
            if (data != null && data.has("api_key")) {
                return data.get("api_key").asText();
            }

            throw new McPanelException(ErrorCode.AGENT_REFRESH_FAILED, "Go API 未返回新 Key");
        } catch (McPanelException e) {
            throw e;
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    // === internal ===

    private void post(String baseUrl, String path, String keyValue, String requestBody) {
        try {
            RestClient.RequestBodySpec request = restClient.post()
                    .uri(baseUrl + path)
                    .header("Authorization", "Bearer " + keyValue);

            if (requestBody != null) {
                request.header("Content-Type", "application/json");
                request.body(requestBody);
            }

            request.retrieve().toBodilessEntity();
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    private String extractStatus(String jsonBody) {
        try {
            JsonNode node = objectMapper.readTree(jsonBody);
            return node.has("status") ? node.get("status").asText() : "ok";
        } catch (Exception e) {
            return "ok";
        }
    }

    private McPanelException handleException(Exception e) {
        log.error("[mc-panel] AgentClient 调用失败: {}", e.getMessage());

        if (e instanceof ConnectException) {
            return new McPanelException(ErrorCode.AGENT_UNREACHABLE);
        }
        if (e instanceof SocketTimeoutException) {
            return new McPanelException(ErrorCode.AGENT_TIMEOUT);
        }
        if (e instanceof McPanelException mp) {
            return mp;
        }
        return new McPanelException(ErrorCode.AGENT_ERROR, e.getMessage());
    }

    private record CommandBody(String command) {}
}
