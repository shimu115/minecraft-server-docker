package handler

import (
	"fmt"
	"net/http"
	"os"
	"strconv"
	"time"

	"github.com/shimu115/minecraft-server-docker/api/service"
)

type logHandler struct {
	logPath string
}

// NewLogHandler 创建日志 handler
func NewLogHandler(logPath string) *logHandler {
	return &logHandler{logPath: logPath}
}

// Stream SSE 日志流
func (h *logHandler) Stream() http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		flusher, ok := w.(http.Flusher)
		if !ok {
			http.Error(w, "Streaming unsupported", http.StatusInternalServerError)
			return
		}

		w.Header().Set("Content-Type", "text/event-stream")
		w.Header().Set("Cache-Control", "no-cache")
		w.Header().Set("Connection", "keep-alive")
		w.Header().Set("X-Accel-Buffering", "no") // 禁用 nginx 缓冲
		w.WriteHeader(http.StatusOK)
		flusher.Flush()

		reader, err := service.NewLogReader(h.logPath)
		if err != nil {
			fmt.Fprintf(w, "event: error\ndata: %v\n\n", err)
			flusher.Flush()
			return
		}
		defer reader.Close()

		// 可选 tail 参数回放最近 N 行
		tailParam := r.URL.Query().Get("tail")
		if tailN, err := strconv.Atoi(tailParam); err == nil && tailN > 0 {
			lines, _ := readLastLines(h.logPath, tailN)
			for _, line := range lines {
				fmt.Fprintf(w, "data: %s\n\n", line)
			}
			flusher.Flush()
		}

		// 心跳：每 15 秒发送 comment 防止代理/浏览器断开
		go heartbeat(r.Context().Done(), flusher)

		ticker := time.NewTicker(200 * time.Millisecond)
		defer ticker.Stop()

		for {
			select {
			case <-r.Context().Done():
				return
			case <-ticker.C:
				lines, err := reader.ReadNewLines()
				if err != nil {
					reader.Close()
					reader, err = service.NewLogReader(h.logPath)
					if err != nil {
						return
					}
					fmt.Fprintf(w, "event: rotation\ndata: Log rotated\n\n")
					flusher.Flush()
					continue
				}
				for _, line := range lines {
					fmt.Fprintf(w, "data: %s\n\n", line)
				}
				if len(lines) > 0 {
					flusher.Flush()
				}
			}
		}
	}
}

// heartbeat 定期发送 SSE comment 保持连接
func heartbeat(done <-chan struct{}, flusher http.Flusher) {
	ticker := time.NewTicker(15 * time.Second)
	defer ticker.Stop()
	for {
		select {
		case <-done:
			return
		case <-ticker.C:
			fmt.Fprintf(flusher, ": heartbeat\n\n")
			if f, ok := flusher.(http.Flusher); ok {
				f.Flush()
			}
		}
	}
}

// readLastLines 读取文件最后 N 行
func readLastLines(path string, n int) ([]string, error) {
	data, err := os.ReadFile(path)
	if err != nil {
		return nil, err
	}
	lines := splitLines(string(data))
	if len(lines) <= n {
		return lines, nil
	}
	return lines[len(lines)-n:], nil
}

func splitLines(s string) []string {
	var result []string
	start := 0
	for i := 0; i < len(s); i++ {
		if s[i] == '\n' {
			if line := s[start:i]; line != "" {
				result = append(result, line)
			}
			start = i + 1
		}
	}
	if start < len(s) && s[start:] != "" {
		result = append(result, s[start:])
	}
	return result
}
