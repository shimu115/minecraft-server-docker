package com.mcpanel.panel.service;

import com.mcpanel.panel.dto.KeyResponse;
import com.mcpanel.panel.dto.KeyRevokeResponse;

import java.util.List;

public interface ApiKeyService {

    KeyResponse registerKey(String name, String keyValue);

    List<KeyResponse> listKeys();

    KeyResponse getKey(Long id);

    void deleteKey(Long id);

    KeyRevokeResponse revokeKey(Long id);
}
