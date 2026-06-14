package handler

import (
	"net/http"
	"net/url"
	"strings"

	"github.com/shimu115/minecraft-server-docker/api/model"
	"github.com/shimu115/minecraft-server-docker/api/service"
)

type fileHandler struct {
	baseDir string
}

// NewFileHandler 创建文件管理 handler
func NewFileHandler(baseDir string) *fileHandler {
	return &fileHandler{baseDir: baseDir}
}

// List 列出目录
func (h *fileHandler) List() http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		relPath := r.URL.Query().Get("path")
		files, err := service.ListDir(h.baseDir, relPath)
		if err != nil {
			writeError(w, err.Error(), http.StatusInternalServerError)
			return
		}
		writeJSON(w, model.APIResponse{
			Code:   http.StatusOK,
			Status: "ok",
			Data:   files,
		})
	}
}

// Read 读取文件
func (h *fileHandler) Read() http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		relPath := r.URL.Query().Get("path")
		if relPath == "" {
			writeError(w, "path is required", http.StatusBadRequest)
			return
		}
		content, err := service.ReadFile(h.baseDir, relPath)
		if err != nil {
			writeError(w, err.Error(), http.StatusInternalServerError)
			return
		}
		writeJSON(w, model.APIResponse{
			Code:   http.StatusOK,
			Status: "ok",
			Data: map[string]string{
				"path":    relPath,
				"content": content,
			},
		})
	}
}

// Write 写入文件
func (h *fileHandler) Write() http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		relPath := r.URL.Query().Get("path")
		if relPath == "" {
			writeError(w, "path is required", http.StatusBadRequest)
			return
		}

		var body struct {
			Content string `json:"content"`
		}
		if err := jsonDecode(r, &body); err != nil {
			writeError(w, "invalid body", http.StatusBadRequest)
			return
		}

		// 解码路径（支持中文）
		relPath, _ = url.QueryUnescape(relPath)
		relPath = strings.TrimPrefix(relPath, "/")

		if err := service.WriteFile(h.baseDir, relPath, body.Content); err != nil {
			writeError(w, err.Error(), http.StatusInternalServerError)
			return
		}
		writeOK(w, "File saved")
	}
}

// Delete 删除文件
func (h *fileHandler) Delete() http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		relPath := r.URL.Query().Get("path")
		if relPath == "" {
			writeError(w, "path is required", http.StatusBadRequest)
			return
		}
		if err := service.DeleteFile(h.baseDir, relPath); err != nil {
			writeError(w, err.Error(), http.StatusInternalServerError)
			return
		}
		writeOK(w, "Deleted")
	}
}
