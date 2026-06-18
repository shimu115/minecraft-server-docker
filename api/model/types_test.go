package model

import (
	"encoding/json"
	"testing"
)

func TestAPIResponseJSON(t *testing.T) {
	resp := APIResponse{
		Code:    200,
		Status:  "ok",
		Message: "success",
		Data:    map[string]string{"key": "value"},
	}

	b, err := json.Marshal(resp)
	if err != nil {
		t.Fatalf("marshal: %v", err)
	}

	var decoded APIResponse
	if err := json.Unmarshal(b, &decoded); err != nil {
		t.Fatalf("unmarshal: %v", err)
	}

	if decoded.Code != 200 {
		t.Errorf("Code = %d, want 200", decoded.Code)
	}
	if decoded.Status != "ok" {
		t.Errorf("Status = %s, want ok", decoded.Status)
	}
	if decoded.Message != "success" {
		t.Errorf("Message = %s, want success", decoded.Message)
	}
}

func TestServerStatusResponse(t *testing.T) {
	resp := ServerStatusResponse{
		Running: true,
		Players: 5,
		Uptime:  "12:34:56",
		Version: "1.21.1",
	}

	if !resp.Running {
		t.Error("Running should be true")
	}
	if resp.Players != 5 {
		t.Errorf("Players = %d, want 5", resp.Players)
	}
}

func TestCommandRequest(t *testing.T) {
	body := `{"command":"say Hello"}`
	var req CommandRequest
	if err := json.Unmarshal([]byte(body), &req); err != nil {
		t.Fatalf("unmarshal: %v", err)
	}
	if req.Command != "say Hello" {
		t.Errorf("Command = %s, want 'say Hello'", req.Command)
	}
}

func TestFileInfo(t *testing.T) {
	fi := FileInfo{
		Name:    "server.jar",
		Path:    "server.jar",
		IsDir:   false,
		Size:    1024,
		ModTime: "2026-06-01 12:00:00",
	}

	b, err := json.Marshal(fi)
	if err != nil {
		t.Fatalf("marshal: %v", err)
	}

	var decoded FileInfo
	if err := json.Unmarshal(b, &decoded); err != nil {
		t.Fatalf("unmarshal: %v", err)
	}

	if decoded.Name != "server.jar" {
		t.Errorf("Name = %s, want server.jar", decoded.Name)
	}
	if decoded.IsDir {
		t.Error("IsDir should be false")
	}
}
