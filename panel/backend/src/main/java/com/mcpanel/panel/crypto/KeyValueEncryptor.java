package com.mcpanel.panel.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

/**
 * JPA AttributeConverter：api_keys.key_value 列的 AES-256-GCM 加解密。
 * 写入 DB 时自动加密，读取时自动解密。
 * 加密密钥通过环境变量 DB_ENCRYPT_KEY 注入（Base64 编码的 32 字节密钥）。
 */
@Component
@Converter
public class KeyValueEncryptor implements AttributeConverter<String, String> {

    private final SecretKey key;

    public KeyValueEncryptor(@Value("${app.db-encrypt-key}") String base64Key) {
        byte[] decoded = Base64.getDecoder().decode(base64Key);
        if (decoded.length != 32) {
            throw new IllegalArgumentException("DB_ENCRYPT_KEY must be a 32-byte Base64-encoded key");
        }
        this.key = new SecretKeySpec(decoded, "AES");
    }

    @Override
    public String convertToDatabaseColumn(String plain) {
        if (plain == null) return null;
        return AES256GCM.encrypt(plain, key);
    }

    @Override
    public String convertToEntityAttribute(String cipher) {
        if (cipher == null) return null;
        return AES256GCM.decrypt(cipher, key);
    }
}
