package handler

import (
	"fmt"
	"net/http"

	"github.com/shimu115/minecraft-server-docker/api/model"
	"github.com/shimu115/minecraft-server-docker/api/service"
)

// Command 发送 MC 指令
func Command() http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		var req model.CommandRequest
		if err := jsonDecode(r, &req); err != nil {
			writeError(w, "Invalid request body", http.StatusBadRequest)
			return
		}
		if req.Command == "" {
			writeError(w, "Command is required", http.StatusBadRequest)
			return
		}
		if !service.SessionExists() {
			writeError(w, "Server is not running", http.StatusServiceUnavailable)
			return
		}
		if err := service.SendCommand(req.Command); err != nil {
			writeError(w, fmt.Sprintf("Failed to send command: %v", err), http.StatusInternalServerError)
			return
		}
		writeOK(w, "Command sent")
	}
}
