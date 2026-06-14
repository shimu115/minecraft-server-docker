package middleware

import (
	"encoding/json"
	"net/http"
	"strings"

	"github.com/shimu115/minecraft-server-docker/api/model"
	"github.com/shimu115/minecraft-server-docker/api/service"
)

// skipAuthPaths 无需认证的路径
var skipAuthPaths = map[string]bool{
	"/api/health": true,
}

// Auth 认证中间件
func Auth(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if skipAuthPaths[r.URL.Path] {
			next.ServeHTTP(w, r)
			return
		}

		token := extractBearer(r)
		if token == "" || !service.ValidateAPIKey(token) {
			w.Header().Set("Content-Type", "application/json")
			w.WriteHeader(http.StatusForbidden)
			json.NewEncoder(w).Encode(model.APIResponse{
				Code:    http.StatusForbidden,
				Status:  "error",
				Message: "missing or invalid api key",
			})
			return
		}

		next.ServeHTTP(w, r)
	})
}

func extractBearer(r *http.Request) string {
	auth := r.Header.Get("Authorization")
	if auth == "" {
		return ""
	}
	parts := strings.SplitN(auth, " ", 2)
	if len(parts) == 2 && strings.EqualFold(parts[0], "Bearer") {
		return parts[1]
	}
	return ""
}
