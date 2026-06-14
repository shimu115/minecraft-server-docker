package handler

import (
	"net/http"

	"github.com/shimu115/minecraft-server-docker/api/model"
	"github.com/shimu115/minecraft-server-docker/api/service"
)

// FTPStart 启动 FTP
func FTPStart() http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		var req model.FTPStartRequest
		if err := jsonDecode(r, &req); err != nil {
			// 允许空 body，使用默认值
			req.Port = 21
			req.Username = "root"
			req.Password = "minecraft"
		}
		if req.Port == 0 {
			req.Port = 21
		}
		if req.Username == "" {
			req.Username = "root"
		}
		if req.Password == "" {
			req.Password = "minecraft"
		}

		if err := service.StartFTP(req.Port, req.Username, req.Password); err != nil {
			writeError(w, err.Error(), http.StatusConflict)
			return
		}

		writeJSON(w, model.APIResponse{
			Code:    http.StatusOK,
			Status:  "ok",
			Message: "FTP started",
			Data: model.FTPStatusResponse{
				Running: true,
				Port:    req.Port,
			},
		})
	}
}

// FTPStop 停止 FTP
func FTPStop() http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		if err := service.StopFTP(); err != nil {
			writeError(w, err.Error(), http.StatusServiceUnavailable)
			return
		}
		writeOK(w, "FTP stopped")
	}
}

// FTPStatus FTP 状态
func FTPStatus() http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		running, port := service.FTPStatus()
		writeJSON(w, model.APIResponse{
			Code:   http.StatusOK,
			Status: "ok",
			Data: model.FTPStatusResponse{
				Running: running,
				Port:    port,
			},
		})
	}
}
