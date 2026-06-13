package main

import (
	"fmt"
	"log"
	"net/http"
	"os"
	"path/filepath"
	"strings"
	"time"

	"github.com/shimu115/minecraft-server-docker/api/handler"
	"github.com/shimu115/minecraft-server-docker/api/middleware"
	"github.com/shimu115/minecraft-server-docker/api/service"
)

func main() {
	port := getEnv("API_PORT", "25560")
	mcDir := getEnv("MC_DIR", "/minecraft")
	logPath := filepath.Join(mcDir, "logs", "latest.log")

	// 初始化 API Key
	apiKey, err := service.InitAPIKey()
	if err != nil {
		log.Fatalf("[mc-api] failed to init api key: %v", err)
	}
	_ = apiKey // 已由 InitAPIKey 打印到控制台

	mux := http.NewServeMux()

	// 健康检查
	mux.HandleFunc("GET /api/health", handler.Health())

	// MC 服务端管理
	serverHandler := handler.NewServerHandler(mcDir)
	mux.HandleFunc("POST /api/server/start", serverHandler.Start())
	mux.HandleFunc("POST /api/server/stop", serverHandler.Stop())
	mux.HandleFunc("POST /api/server/restart", serverHandler.Restart())
	mux.HandleFunc("GET /api/server/status", serverHandler.Status())

	// 日志流 (SSE)
	logHandler := handler.NewLogHandler(logPath)
	mux.HandleFunc("GET /api/logs", logHandler.Stream())

	// 发送指令
	mux.HandleFunc("POST /api/command", handler.Command())

	h := middleware.Auth(middleware.CORS(mux))

	// HTTP 服务在 goroutine 中启动
	go func() {
		addr := fmt.Sprintf(":%s", port)
		log.Printf("[mc-api] listening on %s", addr)
		if err := http.ListenAndServe(addr, h); err != nil {
			log.Fatalf("[mc-api] failed: %v", err)
		}
	}()

	// 自动启动 MC 服务端
	if getEnv("AUTO_START", "true") == "true" {
		go func() {
			time.Sleep(2 * time.Second) // 等 HTTP 服务就绪

			if service.SessionExists() {
				log.Printf("[mc-api] MC server already running, skip auto-start")
				return
			}

			javaCmd, err := service.BuildStartCommand(mcDir)
			if err != nil {
				log.Printf("[mc-api] auto-start failed: %v", err)
				return
			}

			log.Printf("[mc-api] auto-starting MC server...")
			if err := service.StartServer(javaCmd); err != nil {
				log.Printf("[mc-api] auto-start failed: %v", err)
				return
			}
			log.Printf("[mc-api] MC server started")
		}()
	}

	// 日志镜像：MC 日志 → stdout（带 [mc-server] 前缀）
	go mirrorMCLogs(logPath)

	// 主线程阻塞
	select {}
}

// mirrorMCLogs 轮询 MC 日志文件，带前缀输出到 stdout
func mirrorMCLogs(logPath string) {
	for {
		time.Sleep(2 * time.Second) // 等 LogReader 就绪的文件

		reader, err := service.NewLogReader(logPath)
		if err != nil {
			continue
		}

		ticker := time.NewTicker(200 * time.Millisecond)
		for range ticker.C {
			lines, err := reader.ReadNewLines()
			if err != nil {
				reader.Close()
				break // 日志轮转，重新打开
			}
			for _, line := range lines {
				fmt.Println(formatLogLine(line))
			}
		}
		ticker.Stop()
		reader.Close()
	}
}

// formatLogLine 格式化日志行，尝试识别已有的时间前缀
func formatLogLine(line string) string {
	// MC 日志通常格式: [HH:MM:SS] [thread/LEVEL]: message
	// 如果已有时间戳格式，直接加前缀
	if strings.HasPrefix(line, "[") {
		return "[mc-server] " + line
	}
	return "[mc-server] " + line
}

func getEnv(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}
