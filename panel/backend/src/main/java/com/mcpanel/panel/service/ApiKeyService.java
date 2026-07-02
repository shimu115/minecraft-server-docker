package com.mcpanel.panel.service;

import com.mcpanel.panel.common.ErrorCode;
import com.mcpanel.panel.crypto.AES256GCM;
import com.mcpanel.panel.entity.ApiKey;
import com.mcpanel.panel.entity.ServerInstance;
import com.mcpanel.panel.exception.McPanelException;
import com.mcpanel.panel.repository.ApiKeyRepository;
import com.mcpanel.panel.repository.ServerInstanceRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class ApiKeyService {

    // UUID v4 格式正则
    private static final Pattern UUID_V4_PATTERN =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-4[0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$");

    private final ApiKeyRepository apiKeyRepository;
    private final ServerInstanceRepository serverInstanceRepository;
    private final SecretKey encryptKey;

    public ApiKeyService(ApiKeyRepository apiKeyRepository,
                         ServerInstanceRepository serverInstanceRepository,
                         @Value("${app.db-encrypt-key}") String base64Key) {
        this.apiKeyRepository = apiKeyRepository;
        this.serverInstanceRepository = serverInstanceRepository;
        byte[] decoded = Base64.getDecoder().decode(base64Key);
        this.encryptKey = new SecretKeySpec(decoded, "AES");
    }

    /**
     * 注册 API Key。
     */
    @Transactional
    public ApiKey registerKey(String name, String keyValue) {
        // 格式校验
        if (keyValue == null || !UUID_V4_PATTERN.matcher(keyValue).matches()) {
            throw new McPanelException(ErrorCode.KEY_INVALID_FORMAT);
        }

        // 唯一性校验：所有 active Key 解密比对
        String encrypted = AES256GCM.encrypt(keyValue, encryptKey);
        boolean exists = apiKeyRepository.findByStatus("active").stream()
                .anyMatch(k -> keyValue.equals(k.getKeyValue())); // getKeyValue() 自动解密
        if (exists) {
            throw new McPanelException(ErrorCode.KEY_ALREADY_EXISTS);
        }

        ApiKey apiKey = new ApiKey();
        apiKey.setName(name);
        apiKey.setKeyValue(keyValue); // AttributeConverter 自动加密
        apiKey.setStatus("active");

        return apiKeyRepository.save(apiKey);
    }

    /**
     * 列出所有已注册的 Key，含绑定实例信息。
     */
    public List<Map<String, Object>> listKeys() {
        List<ApiKey> keys = apiKeyRepository.findAll();
        List<Map<String, Object>> result = new ArrayList<>();

        for (ApiKey key : keys) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", key.getId());
            item.put("name", key.getName());
            item.put("key_preview", keyPreview(key.getKeyValue())); // 解密后脱敏
            item.put("status", key.getStatus());

            // 查找绑定的实例
            serverInstanceRepository.findByApiKeyId(key.getId()).ifPresentOrElse(
                    instance -> item.put("bound_instance", Map.of(
                            "id", instance.getId(),
                            "name", instance.getName()
                    )),
                    () -> item.put("bound_instance", null)
            );

            item.put("created_at", key.getCreatedAt());
            item.put("updated_at", key.getUpdatedAt());
            result.add(item);
        }

        return result;
    }

    /**
     * 获取单个 Key 详情（key_preview 脱敏）。
     */
    public Map<String, Object> getKey(Long id) {
        ApiKey key = apiKeyRepository.findById(id)
                .orElseThrow(() -> new McPanelException(ErrorCode.KEY_NOT_FOUND));

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", key.getId());
        item.put("name", key.getName());
        item.put("key_preview", keyPreview(key.getKeyValue()));
        item.put("status", key.getStatus());

        serverInstanceRepository.findByApiKeyId(key.getId()).ifPresentOrElse(
                instance -> item.put("bound_instance", Map.of(
                        "id", instance.getId(),
                        "name", instance.getName()
                )),
                () -> item.put("bound_instance", null)
        );

        item.put("created_at", key.getCreatedAt());
        item.put("updated_at", key.getUpdatedAt());
        return item;
    }

    /**
     * 删除 Key，已绑定则拒绝。
     */
    @Transactional
    public void deleteKey(Long id) {
        ApiKey key = apiKeyRepository.findById(id)
                .orElseThrow(() -> new McPanelException(ErrorCode.KEY_NOT_FOUND));

        if (serverInstanceRepository.findByApiKeyId(id).isPresent()) {
            throw new McPanelException(ErrorCode.KEY_BOUND_CANNOT_DELETE);
        }

        apiKeyRepository.delete(key);
    }

    /**
     * 吊销 Key。
     */
    @Transactional
    public ApiKey revokeKey(Long id) {
        ApiKey key = apiKeyRepository.findById(id)
                .orElseThrow(() -> new McPanelException(ErrorCode.KEY_NOT_FOUND));

        key.setStatus("revoked");
        return apiKeyRepository.save(key);
    }

    /**
     * key_preview 脱敏：前4位 + ****...**** + 后4位
     */
    public static String keyPreview(String keyValue) {
        if (keyValue == null || keyValue.length() < 9) {
            return "****";
        }
        return keyValue.substring(0, 4) + "****...****" + keyValue.substring(keyValue.length() - 4);
    }
}
