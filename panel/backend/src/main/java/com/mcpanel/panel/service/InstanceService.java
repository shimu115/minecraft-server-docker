package com.mcpanel.panel.service;

import com.mcpanel.panel.agent.AgentClient;
import com.mcpanel.panel.common.ErrorCode;
import com.mcpanel.panel.entity.ApiKey;
import com.mcpanel.panel.entity.ServerInstance;
import com.mcpanel.panel.exception.McPanelException;
import com.mcpanel.panel.repository.ApiKeyRepository;
import com.mcpanel.panel.repository.ServerInstanceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class InstanceService {

    private static final Logger log = LoggerFactory.getLogger(InstanceService.class);

    private final ServerInstanceRepository instanceRepository;
    private final ApiKeyRepository apiKeyRepository;
    private final AgentClient agentClient;

    public InstanceService(ServerInstanceRepository instanceRepository,
                           ApiKeyRepository apiKeyRepository,
                           AgentClient agentClient) {
        this.instanceRepository = instanceRepository;
        this.apiKeyRepository = apiKeyRepository;
        this.agentClient = agentClient;
    }

    /**
     * 注册 MC 实例并绑定已有 Key。
     */
    @Transactional
    public ServerInstance createInstance(String name, Long apiKeyId, String host, Integer port,
                                         String serverType, String mcVersion) {
        if (instanceRepository.existsByName(name)) {
            throw new McPanelException(ErrorCode.INSTANCE_NAME_EXISTS);
        }

        ApiKey apiKey = apiKeyRepository.findById(apiKeyId)
                .orElseThrow(() -> new McPanelException(ErrorCode.KEY_NOT_FOUND));

        if ("revoked".equals(apiKey.getStatus())) {
            throw new McPanelException(ErrorCode.KEY_REVOKED);
        }

        // 检查 Key 是否已被其他实例绑定
        instanceRepository.findByApiKeyId(apiKeyId).ifPresent(existing -> {
            throw new McPanelException(ErrorCode.KEY_ALREADY_BOUND);
        });

        ServerInstance instance = new ServerInstance();
        instance.setName(name);
        instance.setApiKeyId(apiKeyId);
        instance.setHost(host);
        instance.setPort(port != null ? port : 25560);
        instance.setServerType(serverType);
        instance.setMcVersion(mcVersion);
        instance.setStatus("unknown");

        return instanceRepository.save(instance);
    }

    /**
     * 列出所有实例。
     */
    public List<Map<String, Object>> listInstances() {
        List<ServerInstance> instances = instanceRepository.findAllByOrderByCreatedAtDesc();
        List<Map<String, Object>> result = new ArrayList<>();

        for (ServerInstance inst : instances) {
            result.add(buildInstanceMap(inst));
        }

        return result;
    }

    /**
     * 获取单个实例详情。
     */
    public Map<String, Object> getInstance(Long id) {
        ServerInstance inst = instanceRepository.findById(id)
                .orElseThrow(() -> new McPanelException(ErrorCode.INSTANCE_NOT_FOUND));
        return buildInstanceMap(inst);
    }

    /**
     * 更新实例信息（不可修改 apiKeyId）。
     */
    @Transactional
    public ServerInstance updateInstance(Long id, String name, String host, Integer port,
                                         String serverType, String mcVersion) {
        ServerInstance inst = instanceRepository.findById(id)
                .orElseThrow(() -> new McPanelException(ErrorCode.INSTANCE_NOT_FOUND));

        if (name != null && !name.equals(inst.getName())) {
            if (instanceRepository.existsByName(name)) {
                throw new McPanelException(ErrorCode.INSTANCE_NAME_EXISTS);
            }
            inst.setName(name);
        }
        if (host != null) inst.setHost(host);
        if (port != null) inst.setPort(port);
        if (serverType != null) inst.setServerType(serverType);
        if (mcVersion != null) inst.setMcVersion(mcVersion);

        return instanceRepository.save(inst);
    }

    /**
     * 删除实例，Key 自动解绑。
     */
    @Transactional
    public void deleteInstance(Long id) {
        if (!instanceRepository.existsById(id)) {
            throw new McPanelException(ErrorCode.INSTANCE_NOT_FOUND);
        }
        instanceRepository.deleteById(id);
    }

    /**
     * 更换实例绑定的 Key。
     */
    @Transactional
    public ServerInstance bindKey(Long instanceId, Long newApiKeyId) {
        ServerInstance inst = instanceRepository.findById(instanceId)
                .orElseThrow(() -> new McPanelException(ErrorCode.INSTANCE_NOT_FOUND));

        ApiKey newKey = apiKeyRepository.findById(newApiKeyId)
                .orElseThrow(() -> new McPanelException(ErrorCode.KEY_NOT_FOUND));

        if ("revoked".equals(newKey.getStatus())) {
            throw new McPanelException(ErrorCode.KEY_REVOKED);
        }

        // 检查新 Key 是否已被其他实例绑定
        instanceRepository.findByApiKeyId(newApiKeyId).ifPresent(existing -> {
            if (!existing.getId().equals(instanceId)) {
                throw new McPanelException(ErrorCode.KEY_ALREADY_BOUND);
            }
        });

        inst.setApiKeyId(newApiKeyId);
        return instanceRepository.save(inst);
    }

    /**
     * 刷新 API Key（仅 Root）。
     * 调用 Go API POST /api/auth/refresh → 旧 Key 吊销 → 新 Key 注册 → 重新绑定
     */
    @Transactional
    public Map<String, Object> refreshKey(Long instanceId) {
        ServerInstance inst = instanceRepository.findById(instanceId)
                .orElseThrow(() -> new McPanelException(ErrorCode.INSTANCE_NOT_FOUND));

        ApiKey oldKey = apiKeyRepository.findById(inst.getApiKeyId())
                .orElseThrow(() -> new McPanelException(ErrorCode.KEY_NOT_FOUND));

        // 调用 Go API 刷新 Key
        String goApiBaseUrl = "http://" + inst.getHost() + ":" + inst.getPort();
        String newKeyValue;
        try {
            newKeyValue = agentClient.refreshKey(goApiBaseUrl, oldKey.getKeyValue());
        } catch (Exception e) {
            log.error("[mc-panel] 刷新 Key 失败 | instance={} | {}", inst.getName(), e.getMessage());
            throw new McPanelException(ErrorCode.AGENT_REFRESH_FAILED, e.getMessage());
        }

        // 旧 Key → revoked
        oldKey.setStatus("revoked");
        apiKeyRepository.save(oldKey);

        // 新 Key → 注册
        ApiKey newKey = new ApiKey();
        newKey.setName(oldKey.getName());
        newKey.setKeyValue(newKeyValue);
        newKey.setStatus("active");
        newKey = apiKeyRepository.save(newKey);

        // 重新绑定
        inst.setApiKeyId(newKey.getId());
        instanceRepository.save(inst);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("instance_id", instanceId);
        result.put("previous_key", Map.of(
                "id", oldKey.getId(),
                "key_preview", ApiKeyService.keyPreview(oldKey.getKeyValue()),
                "status", "revoked"
        ));
        result.put("new_key", Map.of(
                "id", newKey.getId(),
                "key_preview", ApiKeyService.keyPreview(newKey.getKeyValue()),
                "status", "active"
        ));
        return result;
    }

    /**
     * 健康探测：AgentClient 调 Go API /api/health。
     */
    public Map<String, Object> healthCheck(Long instanceId) {
        ServerInstance inst = instanceRepository.findById(instanceId)
                .orElseThrow(() -> new McPanelException(ErrorCode.INSTANCE_NOT_FOUND));

        ApiKey apiKey = apiKeyRepository.findById(inst.getApiKeyId())
                .orElseThrow(() -> new McPanelException(ErrorCode.KEY_NOT_FOUND));

        String goApiBaseUrl = "http://" + inst.getHost() + ":" + inst.getPort();
        String goHealth;
        try {
            goHealth = agentClient.health(goApiBaseUrl);
        } catch (Exception e) {
            throw new McPanelException(ErrorCode.AGENT_UNREACHABLE, e.getMessage());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("instance_id", instanceId);
        result.put("instance_name", inst.getName());
        result.put("go_api_health", goHealth);
        return result;
    }

    // === helper ===

    private Map<String, Object> buildInstanceMap(ServerInstance inst) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", inst.getId());
        map.put("name", inst.getName());
        map.put("host", inst.getHost());
        map.put("port", inst.getPort());
        map.put("server_type", inst.getServerType());
        map.put("mc_version", inst.getMcVersion());

        // 绑定 Key 信息
        apiKeyRepository.findById(inst.getApiKeyId()).ifPresentOrElse(
                key -> map.put("api_key", Map.of(
                        "id", key.getId(),
                        "name", key.getName(),
                        "key_preview", ApiKeyService.keyPreview(key.getKeyValue()),
                        "status", key.getStatus()
                )),
                () -> map.put("api_key", null)
        );

        map.put("status", inst.getStatus());
        map.put("created_at", inst.getCreatedAt());
        map.put("updated_at", inst.getUpdatedAt());
        return map;
    }
}
