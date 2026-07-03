package handler

import (
	"fmt"
	"io"
	"log"
	"net/http"
	"net/url"
	"path/filepath"
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

// Upload 上传文件
func (h *fileHandler) Upload() http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		if err := r.ParseMultipartForm(100 << 20); err != nil { // 100MB limit
			writeError(w, "failed to parse form: "+err.Error(), http.StatusBadRequest)
			return
		}
		file, header, err := r.FormFile("file")
		if err != nil {
			writeError(w, "missing file field", http.StatusBadRequest)
			return
		}
		defer file.Close()

		// 支持子目录上传：使用 FormFile 的原始文件名，或通过 dir 参数指定
		dir := r.FormValue("dir")
		filename := filepath.Join(dir, header.Filename)

		if err := service.SaveUploadedFile(h.baseDir, filename, file); err != nil {
			writeError(w, err.Error(), http.StatusInternalServerError)
			return
		}
		writeJSON(w, model.APIResponse{
			Code:    http.StatusOK,
			Status:  "ok",
			Message: "File uploaded",
			Data:    map[string]string{"filename": filename},
		})
	}
}

// Download 下载文件
func (h *fileHandler) Download() http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		relPath := r.URL.Query().Get("path")
		if relPath == "" {
			writeError(w, "path is required", http.StatusBadRequest)
			return
		}
		f, info, err := service.OpenFile(h.baseDir, relPath)
		if err != nil {
			writeError(w, err.Error(), http.StatusNotFound)
			return
		}
		defer f.Close()

		w.Header().Set("Content-Disposition", fmt.Sprintf(`attachment; filename="%s"`, filepath.Base(relPath)))
		w.Header().Set("Content-Type", "application/octet-stream")
		w.Header().Set("Content-Length", fmt.Sprintf("%d", info.Size()))
		io.Copy(w, f)
	}
}

// Export 导出目录为压缩文件
func (h *fileHandler) Export() http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		r.ParseForm()
		format := r.FormValue("format")
		if format == "" {
			var body struct {
				Format string `json:"format"`
			}
			jsonDecode(r, &body)
			format = body.Format
		}
		if format == "" {
			writeError(w, "format is required (zip or tar.gz)", http.StatusBadRequest)
			return
		}

		filename := "minecraft." + format
		if format == "tar.gz" {
			filename = "minecraft.tar.gz"
		}
		w.Header().Set("Content-Disposition", fmt.Sprintf(`attachment; filename="%s"`, filename))
		w.Header().Set("Content-Type", "application/octet-stream")
		if err := service.ExportDir(h.baseDir, format, w); err != nil {
			log.Printf("[mc-api] export failed: %v", err)
		}
	}
}
