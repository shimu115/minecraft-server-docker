package service

import (
	"crypto/rand"
	"fmt"
	"log"
	"os"
	"path/filepath"
	"strings"
)

const authDir = "./auth"
const authFile = "api_key.txt"

// InitAPIKey 初始化 API Key（首次生成，后续从文件读取）
func InitAPIKey() (string, error) {
	if err := os.MkdirAll(authDir, 0755); err != nil {
		return "", fmt.Errorf("failed to create auth dir: %w", err)
	}

	keyPath := filepath.Join(authDir, authFile)

	// 文件已存在，读取
	if data, err := os.ReadFile(keyPath); err == nil {
		key := parseAPIKey(string(data))
		if key != "" {
			return key, nil
		}
	}

	// 生成新 key
	key := generateUUID()
	content := fmt.Sprintf("api-key=%s\n", key)
	if err := os.WriteFile(keyPath, []byte(content), 0600); err != nil {
		return "", fmt.Errorf("failed to write api key: %w", err)
	}

	log.Printf("[mc-api] ========================================")
	log.Printf("[mc-api] New API Key generated: %s", key)
	log.Printf("[mc-api] Stored at: %s", keyPath)
	log.Printf("[mc-api] Use: Authorization: Bearer %s", key)
	log.Printf("[mc-api] ========================================")

	return key, nil
}

// ValidateAPIKey 校验 API Key
func ValidateAPIKey(token string) bool {
	if token == "" {
		return false
	}
	keyPath := filepath.Join(authDir, authFile)
	data, err := os.ReadFile(keyPath)
	if err != nil {
		return false
	}
	stored := parseAPIKey(string(data))
	return stored != "" && token == stored
}

// parseAPIKey 从文件内容解析 api-key
func parseAPIKey(content string) string {
	for _, line := range strings.Split(content, "\n") {
		line = strings.TrimSpace(line)
		if strings.HasPrefix(line, "api-key=") {
			return strings.TrimPrefix(line, "api-key=")
		}
	}
	return ""
}

// generateUUID 生成 UUID v4 格式字符串
func generateUUID() string {
	b := make([]byte, 16)
	_, _ = rand.Read(b)
	// UUID v4: 设置 version 4 和 variant bits
	b[6] = (b[6] & 0x0f) | 0x40
	b[8] = (b[8] & 0x3f) | 0x80
	return fmt.Sprintf("%08x-%04x-%04x-%04x-%012x",
		b[0:4], b[4:6], b[6:8], b[8:10], b[10:16])
}
