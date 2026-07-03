package com.mcpanel.panel.service.impl;

import com.mcpanel.panel.common.ErrorCode;
import com.mcpanel.panel.dto.BoundInstanceInfo;
import com.mcpanel.panel.dto.KeyResponse;
import com.mcpanel.panel.dto.KeyRevokeResponse;
import com.mcpanel.panel.entity.ApiKey;
import com.mcpanel.panel.exception.McPanelException;
import com.mcpanel.panel.repository.ApiKeyRepository;
import com.mcpanel.panel.repository.ServerInstanceRepository;
import com.mcpanel.panel.service.ApiKeyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.regex.Pattern;

@Service
public class ApiKeyServiceImpl implements ApiKeyService {

    private static final Pattern UUID_V4_PATTERN =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-4[0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$");

    @Autowired
    private ApiKeyRepository apiKeyRepository;
    @Autowired
    private ServerInstanceRepository serverInstanceRepository;

    @Override
    @Transactional
    public KeyResponse registerKey(String name, String keyValue) {
        if (keyValue == null || !UUID_V4_PATTERN.matcher(keyValue).matches()) {
            throw new McPanelException(ErrorCode.KEY_INVALID_FORMAT);
        }

        boolean exists = apiKeyRepository.findByStatus("active").stream()
                .anyMatch(k -> keyValue.equals(k.getKeyValue()));
        if (exists) {
            throw new McPanelException(ErrorCode.KEY_ALREADY_EXISTS);
        }

        ApiKey apiKey = new ApiKey();
        apiKey.setName(name);
        apiKey.setKeyValue(keyValue);
        apiKey.setStatus("active");
        apiKey = apiKeyRepository.save(apiKey);

        return toKeyResponse(apiKey);
    }

    @Override
    public List<KeyResponse> listKeys() {
        return apiKeyRepository.findAll().stream().map(this::toKeyResponse).toList();
    }

    @Override
    public KeyResponse getKey(Long id) {
        ApiKey key = apiKeyRepository.findById(id)
                .orElseThrow(() -> new McPanelException(ErrorCode.KEY_NOT_FOUND));
        return toKeyResponse(key);
    }

    @Override
    @Transactional
    public void deleteKey(Long id) {
        ApiKey key = apiKeyRepository.findById(id)
                .orElseThrow(() -> new McPanelException(ErrorCode.KEY_NOT_FOUND));
        if (serverInstanceRepository.findByApiKeyId(id).isPresent()) {
            throw new McPanelException(ErrorCode.KEY_BOUND_CANNOT_DELETE);
        }
        apiKeyRepository.delete(key);
    }

    @Override
    @Transactional
    public KeyRevokeResponse revokeKey(Long id) {
        ApiKey key = apiKeyRepository.findById(id)
                .orElseThrow(() -> new McPanelException(ErrorCode.KEY_NOT_FOUND));
        key.setStatus("revoked");
        key = apiKeyRepository.save(key);
        return new KeyRevokeResponse(key.getId(), key.getStatus());
    }

    private KeyResponse toKeyResponse(ApiKey key) {
        BoundInstanceInfo bound = serverInstanceRepository.findByApiKeyId(key.getId())
                .map(inst -> new BoundInstanceInfo(inst.getId(), inst.getName()))
                .orElse(null);

        return KeyResponse.builder()
                .id(key.getId())
                .name(key.getName())
                .keyPreview(keyPreview(key.getKeyValue()))
                .status(key.getStatus())
                .boundInstance(bound)
                .createdAt(key.getCreatedAt())
                .updatedAt(key.getUpdatedAt())
                .build();
    }

    public static String keyPreview(String keyValue) {
        if (keyValue == null || keyValue.length() < 9) {
            return "****";
        }
        return keyValue.substring(0, 4) + "****...****" + keyValue.substring(keyValue.length() - 4);
    }
}
