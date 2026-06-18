package service

import (
	"os"
	"path/filepath"
	"testing"
)

func TestGenerateUUID(t *testing.T) {
	u1 := generateUUID()
	u2 := generateUUID()

	// UUID v4 format: xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx
	if len(u1) != 36 {
		t.Errorf("UUID length = %d, want 36", len(u1))
	}
	if u1[14] != '4' {
		t.Errorf("UUID version byte = %c, want '4'", u1[14])
	}
	// variant byte should be 8, 9, a, or b
	v := u1[19]
	if v != '8' && v != '9' && v != 'a' && v != 'b' {
		t.Errorf("UUID variant byte = %c, want 8/9/a/b", v)
	}
	if u1 == u2 {
		t.Error("two UUIDs should be different")
	}
}

func TestParseAPIKey(t *testing.T) {
	tests := []struct {
		content  string
		expected string
	}{
		{"api-key=abc123\n", "abc123"},
		{"# comment\napi-key=xyz789\n", "xyz789"},
		{"", ""},
		{"no-key-here\n", ""},
	}

	for _, tt := range tests {
		got := parseAPIKey(tt.content)
		if got != tt.expected {
			t.Errorf("parseAPIKey(%q) = %q, want %q", tt.content, got, tt.expected)
		}
	}
}

func TestInitAndValidateAPIKey(t *testing.T) {
	// Use temp directory to isolate test
	tmpDir := t.TempDir()
	origAuthDir := authDir
	defer func() { authDir = origAuthDir }()

	// Override auth dir to use temp
	authDir = tmpDir
	keyPath := filepath.Join(authDir, authFile)

	// Ensure clean state
	os.Remove(keyPath)
	os.Remove(authDir)

	// First init generates a key
	key1, err := InitAPIKey()
	if err != nil {
		t.Fatalf("InitAPIKey: %v", err)
	}
	if key1 == "" {
		t.Fatal("InitAPIKey returned empty key")
	}

	// Validate returns true for correct key
	if !ValidateAPIKey(key1) {
		t.Error("ValidateAPIKey should return true for correct key")
	}

	// Validate returns false for wrong key
	if ValidateAPIKey("wrong-key") {
		t.Error("ValidateAPIKey should return false for wrong key")
	}

	// Second init reads the same key
	key2, err := InitAPIKey()
	if err != nil {
		t.Fatalf("InitAPIKey (2nd): %v", err)
	}
	if key2 != key1 {
		t.Errorf("InitAPIKey returned different key: %s vs %s", key2, key1)
	}

	// Refresh generates new key
	newKey, err := RefreshAPIKey()
	if err != nil {
		t.Fatalf("RefreshAPIKey: %v", err)
	}
	if newKey == key1 {
		t.Error("RefreshAPIKey should generate new key")
	}

	// Old key should be invalid after refresh
	if ValidateAPIKey(key1) {
		t.Error("old key should be invalid after refresh")
	}

	// New key should be valid
	if !ValidateAPIKey(newKey) {
		t.Error("new key should be valid after refresh")
	}
}

func TestValidateAPIKeyEmpty(t *testing.T) {
	if ValidateAPIKey("") {
		t.Error("ValidateAPIKey should return false for empty token")
	}
}

func TestValidateAPIKeyNoFile(t *testing.T) {
	tmpDir := t.TempDir()
	origAuthDir := authDir
	defer func() { authDir = origAuthDir }()

	authDir = tmpDir
	os.RemoveAll(filepath.Join(authDir, authFile))

	if ValidateAPIKey("anything") {
		t.Error("ValidateAPIKey should return false when key file doesn't exist")
	}
}
