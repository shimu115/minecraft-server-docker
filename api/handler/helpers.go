package handler

import (
	"encoding/json"
	"net/http"
)

// jsonDecode 解析 JSON 请求体
func jsonDecode(r *http.Request, v any) error {
	defer r.Body.Close()
	return json.NewDecoder(r.Body).Decode(v)
}
