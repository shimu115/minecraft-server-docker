package main

import (
	"fmt"
	"log"
	"net/http"
	"os"
	"path/filepath"

	"github.com/shimu115/minecraft-server-docker/api/handler"
	"github.com/shimu115/minecraft-server-docker/api/middleware"
)

func main() {
	port := getEnv("API_PORT", "25560")
	mcDir := getEnv("MC_DIR", "/minecraft")
	logPath := filepath.Join(mcDir, "logs", "latest.log")

	mux := http.NewServeMux()

	// 健康检查
	mux.HandleFunc("GET /api/health", handler.Health())

	// MC 服务端管理
	serverHandler := handler.NewServerHandler(mcDir)
	mux.HandleFunc("POST /api/server/start", serverHandler.Start())
	mux.HandleFunc("POST /api/server/stop", serverHandler.Stop())
	mux.HandleFunc("POST /api/server/restart", serverHandler.Restart())
	mux.HandleFunc("GET /api/server/status", serverHandler.Status())

	// 日志流
	logHandler := handler.NewLogHandler(logPath)
	mux.HandleFunc("GET /api/logs", logHandler.Stream())

	// 发送指令
	mux.HandleFunc("POST /api/command", handler.Command())

	// CORS
	h := middleware.CORS(mux)

	addr := fmt.Sprintf(":%s", port)
	log.Printf("[mc-api] listening on %s", addr)
	if err := http.ListenAndServe(addr, h); err != nil {
		log.Fatalf("[mc-api] failed to start: %v", err)
	}
}

func getEnv(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}
