package com.mcpanel.panel.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcpanel.panel.common.ErrorCode;
import com.mcpanel.panel.exception.McPanelException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class AgentClient {

    private static final Logger log = LoggerFactory.getLogger(AgentClient.class);

    /**
     * Go API 错误消息 → 中文翻译。
     * key 为消息前缀（精确匹配优先，前缀匹配兜底）。
     */
    private static final Map<String, String> GO_ERROR_TRANSLATION = new LinkedHashMap<>();

    static {
        GO_ERROR_TRANSLATION.put("Server is already running", "服务端已在运行中");
        GO_ERROR_TRANSLATION.put("Server is not running", "服务端未运行");
        GO_ERROR_TRANSLATION.put("Command is required", "指令不能为空");
        GO_ERROR_TRANSLATION.put("Invalid request body", "请求体格式无效");
        GO_ERROR_TRANSLATION.put("missing or invalid api key", "API Key 无效或缺失");
        GO_ERROR_TRANSLATION.put("Failed to start server:", "启动服务端失败");
        GO_ERROR_TRANSLATION.put("Failed to send stop command:", "停止服务端失败");
        GO_ERROR_TRANSLATION.put("Failed to send command:", "发送指令失败");
        GO_ERROR_TRANSLATION.put("path is required", "路径不能为空");
        GO_ERROR_TRANSLATION.put("invalid body", "请求体无效");
        GO_ERROR_TRANSLATION.put("missing file field", "缺少文件字段");
        GO_ERROR_TRANSLATION.put("failed to parse form:", "表单解析失败");
        GO_ERROR_TRANSLATION.put("format is required (zip or tar.gz)", "需要指定压缩格式（zip 或 tar.gz）");
    }

    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AgentClient() {
        this.restClient = RestClient.builder()
                .requestFactory(clientHttpRequestFactory())
                .build();
    }

    private static org.springframework.http.client.JdkClientHttpRequestFactory clientHttpRequestFactory() {
        org.springframework.http.client.JdkClientHttpRequestFactory factory =
                new org.springframework.http.client.JdkClientHttpRequestFactory();
        factory.setReadTimeout(Duration.ofSeconds(30));
        return factory;
    }

    // === Go API 方法 ===

    public String health(String baseUrl) {
        try {
            return restClient.get()
                    .uri(baseUrl + "/api/health")
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw translateGoError(readErrorBody(res));
                    })
                    .body(String.class);
        } catch (McPanelException e) {
            throw e;
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    public void startServer(String baseUrl, String keyValue) {
        post(baseUrl, "/api/server/start", keyValue, null);
    }

    public void stopServer(String baseUrl, String keyValue) {
        post(baseUrl, "/api/server/stop", keyValue, null);
    }

    public void restartServer(String baseUrl, String keyValue) {
        post(baseUrl, "/api/server/restart", keyValue, null);
    }

    public String getStatus(String baseUrl, String keyValue) {
        try {
            return restClient.get()
                    .uri(baseUrl + "/api/server/status")
                    .header("Authorization", "Bearer " + keyValue)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw translateGoError(readErrorBody(res));
                    })
                    .body(String.class);
        } catch (McPanelException e) {
            throw e;
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    public void sendCommand(String baseUrl, String keyValue, String command) {
        String body;
        try {
            body = objectMapper.writeValueAsString(new CommandBody(command));
        } catch (Exception e) {
            throw new McPanelException(ErrorCode.INTERNAL_ERROR);
        }
        post(baseUrl, "/api/command", keyValue, body);
    }

    public String refreshKey(String baseUrl, String keyValue) {
        try {
            String body = restClient.post()
                    .uri(baseUrl + "/api/auth/refresh")
                    .header("Authorization", "Bearer " + keyValue)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw translateGoError(readErrorBody(res));
                    })
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

            request.retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw translateGoError(readErrorBody(res));
                    })
                    .toBodilessEntity();
        } catch (McPanelException e) {
            throw e;
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    /**
     * 从 error response 中读取 body 并解析 Go API 的 message 字段。
     */
    private String readErrorBody(org.springframework.http.client.ClientHttpResponse res) {
        try {
            byte[] bytes = res.getBody().readAllBytes();
            String json = new String(bytes, StandardCharsets.UTF_8);
            JsonNode node = objectMapper.readTree(json);
            if (node.has("message")) {
                return node.get("message").asText();
            }
            return json;
        } catch (Exception e) {
            return "Go API 返回错误";
        }
    }

    /**
     * 翻译 Go API 错误消息：先精确匹配，再前缀匹配，无匹配返回原文。
     */
    private McPanelException translateGoError(String goMessage) {
        // 精确匹配
        String translated = GO_ERROR_TRANSLATION.get(goMessage);
        if (translated != null) {
            return new McPanelException(ErrorCode.AGENT_ERROR, translated);
        }

        // 前缀匹配（处理 "Failed to start server: xxx" 等带变量的消息）
        for (Map.Entry<String, String> entry : GO_ERROR_TRANSLATION.entrySet()) {
            if (goMessage.startsWith(entry.getKey())) {
                return new McPanelException(ErrorCode.AGENT_ERROR, entry.getValue());
            }
        }

        // 无匹配，返回原文
        return new McPanelException(ErrorCode.AGENT_ERROR, goMessage);
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
