package handler

import (
	"encoding/json"
	"net/http"

	"github.com/shimu115/minecraft-server-docker/api/model"
)

// jsonDecode 解析 JSON 请求体
func jsonDecode(r *http.Request, v any) error {
	defer r.Body.Close()
	return json.NewDecoder(r.Body).Decode(v)
}

func writeJSON(w http.ResponseWriter, resp model.APIResponse) {
	if resp.Code == 0 {
		resp.Code = http.StatusOK
	}
	w.Header().Set("Content-Type", "application/json")
	if resp.Code >= 400 {
		w.WriteHeader(resp.Code)
	}
	json.NewEncoder(w).Encode(resp)
}

func writeError(w http.ResponseWriter, message string, code int) {
	writeJSON(w, model.APIResponse{
		Code:    code,
		Status:  "error",
		Message: message,
	})
}

func writeOK(w http.ResponseWriter, message string) {
	writeJSON(w, model.APIResponse{
		Code:    http.StatusOK,
		Status:  "ok",
		Message: message,
	})
}
