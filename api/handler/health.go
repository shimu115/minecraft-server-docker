package handler

import (
	"encoding/json"
	"net/http"

	"github.com/shimu115/minecraft-server-docker/api/model"
	"github.com/shimu115/minecraft-server-docker/api/service"
)

// Health 健康检查
func Health() http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		writeJSON(w, model.APIResponse{
			Status: "ok",
			Data: map[string]bool{
				"mc_server_running": service.SessionExists(),
			},
		})
	}
}

func writeJSON(w http.ResponseWriter, resp model.APIResponse) {
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(resp)
}

func writeError(w http.ResponseWriter, message string, code int) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(code)
	json.NewEncoder(w).Encode(model.APIResponse{
		Status:  "error",
		Message: message,
	})
}

func writeOK(w http.ResponseWriter, message string) {
	writeJSON(w, model.APIResponse{Status: "ok", Message: message})
}
