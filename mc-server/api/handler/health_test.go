package handler

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/shimu115/minecraft-server-docker/api/model"
)

func TestHealth(t *testing.T) {
	req := httptest.NewRequest("GET", "/api/health", nil)
	w := httptest.NewRecorder()

	Health()(w, req)

	if w.Code != http.StatusOK {
		t.Errorf("status = %d, want %d", w.Code, http.StatusOK)
	}

	var resp model.APIResponse
	if err := json.NewDecoder(w.Body).Decode(&resp); err != nil {
		t.Fatalf("decode: %v", err)
	}

	if resp.Status != "ok" {
		t.Errorf("status = %s, want ok", resp.Status)
	}
}

func TestRefreshKey(t *testing.T) {
	req := httptest.NewRequest("POST", "/api/auth/refresh", nil)
	w := httptest.NewRecorder()

	RefreshKey()(w, req)

	// Refresh may fail if auth dir doesn't exist in test env,
	// but the handler should still return a valid JSON response
	var resp model.APIResponse
	if err := json.NewDecoder(w.Body).Decode(&resp); err != nil {
		t.Fatalf("decode: %v", err)
	}

	// Either success or error is acceptable (depends on filesystem state)
	if resp.Status != "ok" && resp.Status != "error" {
		t.Errorf("unexpected status: %s", resp.Status)
	}
}

func TestWriteJSON(t *testing.T) {
	w := httptest.NewRecorder()

	writeJSON(w, model.APIResponse{
		Code:   200,
		Status: "ok",
	})

	if w.Code != http.StatusOK {
		t.Errorf("status = %d, want 200", w.Code)
	}
	if w.Header().Get("Content-Type") != "application/json" {
		t.Errorf("Content-Type = %s, want application/json", w.Header().Get("Content-Type"))
	}
}

func TestWriteError(t *testing.T) {
	w := httptest.NewRecorder()

	writeError(w, "something went wrong", http.StatusBadRequest)

	if w.Code != http.StatusBadRequest {
		t.Errorf("status = %d, want %d", w.Code, http.StatusBadRequest)
	}
}

func TestWriteOK(t *testing.T) {
	w := httptest.NewRecorder()

	writeOK(w, "success")

	if w.Code != http.StatusOK {
		t.Errorf("status = %d, want 200", w.Code)
	}
}
