package handler

import (
	"bufio"
	"fmt"
	"net/http"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"time"

	"github.com/shimu115/minecraft-server-docker/api/model"
	"github.com/shimu115/minecraft-server-docker/api/service"
)

type serverHandler struct {
	mcDir string
}

// NewServerHandler 创建服务端管理 handler
func NewServerHandler(mcDir string) *serverHandler {
	return &serverHandler{mcDir: mcDir}
}

// Start 启动 MC 服务端
func (h *serverHandler) Start() http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		if service.SessionExists() {
			writeError(w, "Server is already running", http.StatusConflict)
			return
		}

		envFile := filepath.Join(h.mcDir, ".env")
		env, err := parseEnvFile(envFile)
		if err != nil {
			writeError(w, fmt.Sprintf("Failed to read .env: %v", err), http.StatusInternalServerError)
			return
		}

		javaHome := env["JAVA_HOME"]
		jarFile := env["JAR_FILE"]
		xmx := env["Xmx"]
		xms := env["Xms"]
		serverType := env["SERVER_TYPE"]

		if javaHome == "" || jarFile == "" {
			writeError(w, "Missing JAVA_HOME or JAR_FILE in .env", http.StatusInternalServerError)
			return
		}
		if xmx == "" {
			xmx = "1024M"
		}
		if xms == "" {
			xms = "1024M"
		}

		javaPath := filepath.Join(javaHome, "bin", "java")
		var javaCmd string

		switch serverType {
		case "forge":
			if _, err := os.Stat(filepath.Join(h.mcDir, "forge-launcher.sh")); err == nil {
				javaCmd = fmt.Sprintf("cd %s && ./forge-launcher.sh", h.mcDir)
			} else {
				jar, _ := findForgeJar(h.mcDir)
				if jar == "" {
					writeError(w, "No forge launcher found", http.StatusInternalServerError)
					return
				}
				javaCmd = fmt.Sprintf("cd %s && %s -Xmx%s -Xms%s -jar %s nogui", h.mcDir, javaPath, xmx, xms, jar)
			}
		default:
			javaCmd = fmt.Sprintf("cd %s && %s -Xmx%s -Xms%s -jar %s nogui", h.mcDir, javaPath, xmx, xms, jarFile)
		}

		if err := service.StartServer(javaCmd); err != nil {
			writeError(w, fmt.Sprintf("Failed to start server: %v", err), http.StatusInternalServerError)
			return
		}

		writeOK(w, "Server starting")
	}
}

// Stop 关闭 MC 服务端
func (h *serverHandler) Stop() http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		if !service.SessionExists() {
			writeError(w, "Server is not running", http.StatusServiceUnavailable)
			return
		}
		if err := service.StopServer(); err != nil {
			writeError(w, fmt.Sprintf("Failed to send stop command: %v", err), http.StatusInternalServerError)
			return
		}
		writeOK(w, "Stop command sent")
	}
}

// Restart 重启 MC 服务端
func (h *serverHandler) Restart() http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		if !service.SessionExists() {
			writeError(w, "Server is not running", http.StatusServiceUnavailable)
			return
		}
		// 发送 stop 指令
		if err := service.StopServer(); err != nil {
			writeError(w, fmt.Sprintf("Failed to send stop command: %v", err), http.StatusInternalServerError)
			return
		}

		// 等待最多 30 秒，直到 screen 会话结束
		for i := 0; i < 30; i++ {
			time.Sleep(1 * time.Second)
			if !service.SessionExists() {
				break
			}
		}

		// 清理可能残留的 screen
		exec.Command("screen", "-wipe").Run()

		time.Sleep(2 * time.Second)

		// 重新启动
		h.Start().ServeHTTP(w, r)
	}
}

// Status 获取服务端状态
func (h *serverHandler) Status() http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		running := service.SessionExists()

		resp := model.ServerStatusResponse{
			Running: running,
			Players: -1,
		}

		if running {
			resp.Uptime = getScreenUptime()
			resp.Version = detectVersion(filepath.Join(h.mcDir, "logs", "latest.log"))
		}

		writeJSON(w, model.APIResponse{
			Status: "ok",
			Data:   resp,
		})
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
		// 移除 export 前缀
		line = strings.TrimPrefix(line, "export ")
		parts := strings.SplitN(line, "=", 2)
		if len(parts) == 2 {
			// 移除引号
			val := strings.Trim(parts[1], "\"'")
			env[parts[0]] = val
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

// getScreenUptime 获取 screen 会话运行时间
func getScreenUptime() string {
	cmd := exec.Command("screen", "-ls")
	out, err := cmd.Output()
	if err != nil {
		return ""
	}
	output := string(out)
	for _, line := range strings.Split(output, "\n") {
		if strings.Contains(line, "mcserver") {
			// 提取时间信息
			parts := strings.Fields(line)
			for _, p := range parts {
				if strings.Contains(p, ":") && !strings.Contains(p, ".") {
					return p
				}
			}
		}
	}
	return ""
}

// detectVersion 尝试从日志中检测 MC 版本
func detectVersion(logPath string) string {
	f, err := os.Open(logPath)
	if err != nil {
		return ""
	}
	defer f.Close()

	scanner := bufio.NewScanner(f)
	for scanner.Scan() {
		line := scanner.Text()
		// Minecraft 日志格式: [time] [Server thread/INFO]: Starting minecraft server version 1.12.2
		if strings.Contains(line, "Starting minecraft server version") {
			parts := strings.Fields(line)
			if len(parts) > 0 {
				return parts[len(parts)-1]
			}
		}
	}
	return ""
}
