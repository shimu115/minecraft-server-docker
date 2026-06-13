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

		javaCmd, err := service.BuildStartCommand(h.mcDir)
		if err != nil {
			writeError(w, err.Error(), http.StatusInternalServerError)
			return
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
		if err := service.StopServer(); err != nil {
			writeError(w, fmt.Sprintf("Failed to send stop command: %v", err), http.StatusInternalServerError)
			return
		}

		for i := 0; i < 30; i++ {
			time.Sleep(1 * time.Second)
			if !service.SessionExists() {
				break
			}
		}

		exec.Command("screen", "-wipe").Run()
		time.Sleep(2 * time.Second)

		h.Start().ServeHTTP(w, r)
	}
}

// Status 获取服务端状态
func (h *serverHandler) Status() http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		running := service.SessionExists()

		resp := model.ServerStatusResponse{
			Running: running,
			Players: 0,
		}

		if running {
			resp.Uptime = getScreenUptime()
			resp.Version = detectVersion(filepath.Join(h.mcDir, "logs", "latest.log"))
			resp.Players = service.GetPlayerCount(filepath.Join(h.mcDir, "logs", "latest.log"))
		}

		writeJSON(w, model.APIResponse{
			Code:   http.StatusOK,
			Status: "ok",
			Data:   resp,
		})
	}
}

func getScreenUptime() string {
	cmd := exec.Command("screen", "-ls")
	out, err := cmd.Output()
	if err != nil {
		return ""
	}
	for _, line := range strings.Split(string(out), "\n") {
		if strings.Contains(line, "mcserver") {
			for _, p := range strings.Fields(line) {
				if strings.Contains(p, ":") && !strings.Contains(p, ".") {
					return strings.TrimRight(p, "()")
				}
			}
		}
	}
	return ""
}

func detectVersion(logPath string) string {
	f, err := os.Open(logPath)
	if err != nil {
		return ""
	}
	defer f.Close()

	scanner := bufio.NewScanner(f)
	for scanner.Scan() {
		line := scanner.Text()
		if strings.Contains(line, "Starting minecraft server version") {
			parts := strings.Fields(line)
			if len(parts) > 0 {
				return parts[len(parts)-1]
			}
		}
	}
	return ""
}
