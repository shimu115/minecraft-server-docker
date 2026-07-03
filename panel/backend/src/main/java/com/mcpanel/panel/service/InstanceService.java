package com.mcpanel.panel.service;

import com.mcpanel.panel.dto.*;

import java.util.List;

public interface InstanceService {

    InstanceResponse createInstance(String name, Long apiKeyId, String host, Integer port,
                                    String serverType, String mcVersion);

    List<InstanceResponse> listInstances();

    InstanceResponse getInstance(Long id);

    InstanceResponse updateInstance(Long id, String name, String host, Integer port,
                                    String serverType, String mcVersion);

    void deleteInstance(Long id);

    InstanceBindKeyResponse bindKey(Long instanceId, Long newApiKeyId);

    RefreshKeyResponse refreshKey(Long instanceId);

    HealthCheckResponse healthCheck(Long instanceId);
}
