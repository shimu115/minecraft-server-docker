package service

import (
	"bufio"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
)

const ScreenName = "mcserver"

// SessionExists 检查 screen 会话是否存在
func SessionExists() bool {
	cmd := exec.Command("screen", "-ls")
	out, err := cmd.Output()
	if err != nil {
		return false
	}
	return strings.Contains(string(out), ScreenName)
}

// SendCommand 向 MC 服务端发送指令
func SendCommand(command string) error {
	cmd := exec.Command("screen", "-S", ScreenName, "-X", "stuff", command+"\n")
	return cmd.Run()
}

// StartServer 在 screen 会话中启动 MC 服务端
func StartServer(javaCmd string) error {
	cmd := exec.Command("screen", "-L", "-S", ScreenName, "-dm", "sh", "-c", javaCmd)
	return cmd.Run()
}

// StopServer 发送 stop 指令关闭服务端
func StopServer() error {
	return SendCommand("stop")
}

// BuildStartCommand 从 .env 文件构建启动命令
func BuildStartCommand(mcDir string) (string, error) {
	env, err := parseEnvFile(filepath.Join(mcDir, ".env"))
	if err != nil {
		return "", fmt.Errorf("failed to read .env: %w", err)
	}

	javaHome := env["JAVA_HOME"]
	jarFile := env["JAR_FILE"]
	xmx := env["Xmx"]
	xms := env["Xms"]
	serverType := env["SERVER_TYPE"]

	if javaHome == "" || jarFile == "" {
		return "", fmt.Errorf("missing JAVA_HOME or JAR_FILE in .env")
	}
	if xmx == "" {
		xmx = "1024M"
	}
	if xms == "" {
		xms = "1024M"
	}

	javaPath := filepath.Join(javaHome, "bin", "java")

	switch serverType {
	case "forge":
		if _, err := os.Stat(filepath.Join(mcDir, "forge-launcher.sh")); err == nil {
			return fmt.Sprintf("cd %s && ./forge-launcher.sh", mcDir), nil
		}
		jar, err := findForgeJar(mcDir)
		if err != nil {
			return "", fmt.Errorf("no forge launcher found: %w", err)
		}
		return fmt.Sprintf("cd %s && %s -Xmx%s -Xms%s -jar %s nogui", mcDir, javaPath, xmx, xms, jar), nil
	default:
		return fmt.Sprintf("cd %s && %s -Xmx%s -Xms%s -jar %s nogui", mcDir, javaPath, xmx, xms, jarFile), nil
	}
}

// parseEnvFile 解析 .env 文件
func parseEnvFile(path string) (map[string]string, error) {
	f, err := os.Open(path)
	if err != nil {
		return nil, err
	}
	defer f.Close()

	env := make(map[string]string)
	scanner := bufio.NewScanner(f)
	for scanner.Scan() {
		line := strings.TrimSpace(scanner.Text())
		if line == "" || strings.HasPrefix(line, "#") {
			continue
		}
		line = strings.TrimPrefix(line, "export ")
		parts := strings.SplitN(line, "=", 2)
		if len(parts) == 2 {
			env[parts[0]] = strings.Trim(parts[1], "\"'")
		}
	}
	return env, scanner.Err()
}

// findForgeJar 查找 forge jar 文件
func findForgeJar(dir string) (string, error) {
	entries, err := os.ReadDir(dir)
	if err != nil {
		return "", err
	}
	for _, e := range entries {
		name := e.Name()
		if strings.HasPrefix(name, "forge-") && strings.HasSuffix(name, ".jar") &&
			!strings.Contains(name, "installer") {
			return name, nil
		}
	}
	return "", fmt.Errorf("no forge jar found")
}
