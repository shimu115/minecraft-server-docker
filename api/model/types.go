package model

// APIResponse 统一响应格式
type APIResponse struct {
	Code    int    `json:"code"`              // HTTP 状态码
	Status  string `json:"status"`            // "ok" | "error"
	Message string `json:"message,omitempty"` // 可读消息
	Data    any    `json:"data,omitempty"`    // 负载数据
}

// ServerStatusResponse 服务端状态
type ServerStatusResponse struct {
	Running bool   `json:"running"`
	Players int    `json:"players"`
	Uptime  string `json:"uptime,omitempty"`
	Version string `json:"version,omitempty"`
}

// CommandRequest 发送指令请求
type CommandRequest struct {
	Command string `json:"command"`
}

// FileInfo 文件信息
type FileInfo struct {
	Name    string `json:"name"`
	Path    string `json:"path"`
	IsDir   bool   `json:"isDir"`
	Size    int64  `json:"size"`
	ModTime string `json:"modTime"`
}
