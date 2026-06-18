package service

import (
	"bufio"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"strconv"
	"strings"
	"time"
)

const ScreenName = "mcserver"

// SessionExists 检查 screen 会话是否存在且存活（非 Dead 状态）
func SessionExists() bool {
	cmd := exec.Command("screen", "-ls")
	out, err := cmd.Output()
	if err != nil {
		return false
	}
	output := string(out)
	// 检查是否包含会话名且非 Dead 状态
	for _, line := range strings.Split(output, "\n") {
		if strings.Contains(line, ScreenName) && !strings.Contains(line, "Dead") {
			return true
		}
	}
	return false
}

// CleanupDeadSessions 清理残留的死 screen 会话
func CleanupDeadSessions() {
	exec.Command("screen", "-wipe").Run()
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

// GetPlayerCount 获取当前在线玩家数（发送 list 指令后解析日志）
func GetPlayerCount(logPath string) int {
	// 发送 list 指令
	_ = SendCommand("list")

	// 等待响应写入日志
	time.Sleep(500 * time.Millisecond)

	// 从日志末尾向前搜索最近的 list 响应
	f, err := os.Open(logPath)
	if err != nil {
		return 0
	}
	defer f.Close()

	// 读取文件尾部 (~4KB)
	stat, _ := f.Stat()
	offset := stat.Size() - 4096
	if offset < 0 {
		offset = 0
	}
	buf := make([]byte, stat.Size()-offset)
	_, _ = f.ReadAt(buf, offset)

	// 按行解析，找 "There are X/Y players online"
	lines := strings.Split(string(buf), "\n")
	for i := len(lines) - 1; i >= 0; i-- {
		line := lines[i]
		if idx := strings.Index(line, "There are "); idx != -1 {
			rest := line[idx+len("There are "):]
			if slash := strings.Index(rest, "/"); slash != -1 {
				if n, err := strconv.Atoi(rest[:slash]); err == nil {
					return n
				}
			}
		}
	}
	return 0
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

// GetScreenUptime 获取 screen 会话运行时长
func GetScreenUptime() string {
	cmd := exec.Command("screen", "-ls")
	out, err := cmd.Output()
	if err != nil {
		return ""
	}
	for _, line := range strings.Split(string(out), "\n") {
		if strings.Contains(line, ScreenName) {
			for _, p := range strings.Fields(line) {
				if strings.Contains(p, ":") && !strings.Contains(p, ".") {
					return strings.TrimRight(p, "()")
				}
			}
		}
	}
	return ""
}
