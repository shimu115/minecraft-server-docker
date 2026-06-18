package handler

import (
	"net/http"

	"github.com/shimu115/minecraft-server-docker/api/model"
	"github.com/shimu115/minecraft-server-docker/api/service"
)

// Health 健康检查
func Health() http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		writeJSON(w, model.APIResponse{
			Code:   http.StatusOK,
			Status: "ok",
			Data: map[string]bool{
				"mc_server_running": service.SessionExists(),
			},
		})
	}
}

// RefreshKey 刷新 API Key
func RefreshKey() http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		key, err := service.RefreshAPIKey()
		if err != nil {
			writeError(w, err.Error(), http.StatusInternalServerError)
			return
		}
		writeJSON(w, model.APIResponse{
			Code:    http.StatusOK,
			Status:  "ok",
			Message: "API Key refreshed",
			Data:    map[string]string{"api_key": key},
		})
	}
}
