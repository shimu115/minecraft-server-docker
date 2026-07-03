package com.mcpanel.panel.service.impl;

import com.mcpanel.panel.agent.AgentClient;
import com.mcpanel.panel.common.ErrorCode;
import com.mcpanel.panel.dto.*;
import com.mcpanel.panel.entity.ApiKey;
import com.mcpanel.panel.entity.ServerInstance;
import com.mcpanel.panel.exception.McPanelException;
import com.mcpanel.panel.repository.ApiKeyRepository;
import com.mcpanel.panel.repository.ServerInstanceRepository;
import com.mcpanel.panel.service.InstanceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class InstanceServiceImpl implements InstanceService {

    private static final Logger log = LoggerFactory.getLogger(InstanceServiceImpl.class);

    @Autowired
    private ServerInstanceRepository instanceRepository;
    @Autowired
    private ApiKeyRepository apiKeyRepository;
    @Autowired
    private AgentClient agentClient;

    @Override
    @Transactional
    public InstanceResponse createInstance(String name, Long apiKeyId, String host, Integer port,
                                           String serverType, String mcVersion) {
        if (instanceRepository.existsByName(name)) {
            throw new McPanelException(ErrorCode.INSTANCE_NAME_EXISTS);
        }

        ApiKey apiKey = apiKeyRepository.findById(apiKeyId)
                .orElseThrow(() -> new McPanelException(ErrorCode.KEY_NOT_FOUND));

        if ("revoked".equals(apiKey.getStatus())) {
            throw new McPanelException(ErrorCode.KEY_REVOKED);
        }

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
        instance = instanceRepository.save(instance);

        return toInstanceResponse(instance);
    }

    @Override
    public List<InstanceResponse> listInstances() {
        return instanceRepository.findAllByOrderByCreatedAtDesc()
                .stream().map(this::toInstanceResponse).toList();
    }

    @Override
    public InstanceResponse getInstance(Long id) {
        ServerInstance inst = instanceRepository.findById(id)
                .orElseThrow(() -> new McPanelException(ErrorCode.INSTANCE_NOT_FOUND));
        return toInstanceResponse(inst);
    }

    @Override
    @Transactional
    public InstanceResponse updateInstance(Long id, String name, String host, Integer port,
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

        inst = instanceRepository.save(inst);
        return toInstanceResponse(inst);
    }

    @Override
    @Transactional
    public void deleteInstance(Long id) {
        if (!instanceRepository.existsById(id)) {
            throw new McPanelException(ErrorCode.INSTANCE_NOT_FOUND);
        }
        instanceRepository.deleteById(id);
    }

    @Override
    @Transactional
    public InstanceBindKeyResponse bindKey(Long instanceId, Long newApiKeyId) {
        ServerInstance inst = instanceRepository.findById(instanceId)
                .orElseThrow(() -> new McPanelException(ErrorCode.INSTANCE_NOT_FOUND));

        ApiKey newKey = apiKeyRepository.findById(newApiKeyId)
                .orElseThrow(() -> new McPanelException(ErrorCode.KEY_NOT_FOUND));

        if ("revoked".equals(newKey.getStatus())) {
            throw new McPanelException(ErrorCode.KEY_REVOKED);
        }

        instanceRepository.findByApiKeyId(newApiKeyId).ifPresent(existing -> {
            if (!existing.getId().equals(instanceId)) {
                throw new McPanelException(ErrorCode.KEY_ALREADY_BOUND);
            }
        });

        inst.setApiKeyId(newApiKeyId);
        inst = instanceRepository.save(inst);
        return new InstanceBindKeyResponse(inst.getId(), inst.getApiKeyId());
    }

    @Override
    @Transactional
    public RefreshKeyResponse refreshKey(Long instanceId) {
        ServerInstance inst = instanceRepository.findById(instanceId)
                .orElseThrow(() -> new McPanelException(ErrorCode.INSTANCE_NOT_FOUND));

        ApiKey oldKey = apiKeyRepository.findById(inst.getApiKeyId())
                .orElseThrow(() -> new McPanelException(ErrorCode.KEY_NOT_FOUND));

        String goApiBaseUrl = "http://" + inst.getHost() + ":" + inst.getPort();
        String newKeyValue;
        try {
            newKeyValue = agentClient.refreshKey(goApiBaseUrl, oldKey.getKeyValue());
        } catch (Exception e) {
            log.error("[mc-panel] 刷新 Key 失败 | instance={} | {}", inst.getName(), e.getMessage());
            throw new McPanelException(ErrorCode.AGENT_REFRESH_FAILED, e.getMessage());
        }

        KeyInfo previousKeyInfo = new KeyInfo(oldKey.getId(), oldKey.getName(),
                ApiKeyServiceImpl.keyPreview(oldKey.getKeyValue()), "revoked");
        oldKey.setStatus("revoked");
        apiKeyRepository.save(oldKey);

        ApiKey newKey = new ApiKey();
        newKey.setName(oldKey.getName());
        newKey.setKeyValue(newKeyValue);
        newKey.setStatus("active");
        newKey = apiKeyRepository.save(newKey);

        inst.setApiKeyId(newKey.getId());
        instanceRepository.save(inst);

        KeyInfo newKeyInfo = new KeyInfo(newKey.getId(), newKey.getName(),
                ApiKeyServiceImpl.keyPreview(newKey.getKeyValue()), "active");

        return RefreshKeyResponse.builder()
                .instanceId(instanceId)
                .previousKey(previousKeyInfo)
                .newKey(newKeyInfo)
                .build();
    }

    @Override
    public HealthCheckResponse healthCheck(Long instanceId) {
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

        return new HealthCheckResponse(instanceId, inst.getName(), goHealth);
    }

    private InstanceResponse toInstanceResponse(ServerInstance inst) {
        KeyInfo keyInfo = apiKeyRepository.findById(inst.getApiKeyId())
                .map(key -> new KeyInfo(key.getId(), key.getName(),
                        ApiKeyServiceImpl.keyPreview(key.getKeyValue()), key.getStatus()))
                .orElse(null);

        return InstanceResponse.builder()
                .id(inst.getId())
                .name(inst.getName())
                .host(inst.getHost())
                .port(inst.getPort())
                .serverType(inst.getServerType())
                .mcVersion(inst.getMcVersion())
                .apiKey(keyInfo)
                .status(inst.getStatus())
                .createdAt(inst.getCreatedAt())
                .updatedAt(inst.getUpdatedAt())
                .build();
    }
}
