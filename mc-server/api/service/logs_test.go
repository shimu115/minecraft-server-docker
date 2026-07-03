package service

import (
	"os"
	"path/filepath"
	"testing"
)

func TestNewLogReader(t *testing.T) {
	tmpDir := t.TempDir()
	logPath := filepath.Join(tmpDir, "test.log")

	// Create log file with initial content
	if err := os.WriteFile(logPath, []byte("line 1\nline 2\n"), 0644); err != nil {
		t.Fatalf("write log: %v", err)
	}

	reader, err := NewLogReader(logPath)
	if err != nil {
		t.Fatalf("NewLogReader: %v", err)
	}
	defer reader.Close()

	// Should start at end, so no new lines
	lines, err := reader.ReadNewLines()
	if err != nil {
		t.Fatalf("ReadNewLines: %v", err)
	}
	if len(lines) != 0 {
		t.Errorf("should have 0 new lines, got %d", len(lines))
	}

	// Append more content
	f, _ := os.OpenFile(logPath, os.O_APPEND|os.O_WRONLY, 0644)
	f.WriteString("line 3\nline 4\n")
	f.Close()

	lines, err = reader.ReadNewLines()
	if err != nil {
		t.Fatalf("ReadNewLines (2): %v", err)
	}
	if len(lines) != 2 {
		t.Errorf("should have 2 new lines, got %d: %v", len(lines), lines)
	}
}

func TestDetectVersion(t *testing.T) {
	tmpDir := t.TempDir()
	logPath := filepath.Join(tmpDir, "latest.log")

	content := `[12:00:00] [Server thread/INFO]: Starting minecraft server version 1.21.1
[12:00:01] [Server thread/INFO]: Loading properties
`
	os.WriteFile(logPath, []byte(content), 0644)

	version := DetectVersion(logPath)
	if version != "1.21.1" {
		t.Errorf("DetectVersion = %q, want %q", version, "1.21.1")
	}
}

func TestDetectVersionNotFound(t *testing.T) {
	tmpDir := t.TempDir()
	logPath := filepath.Join(tmpDir, "empty.log")
	os.WriteFile(logPath, []byte("some random log\n"), 0644)

	version := DetectVersion(logPath)
	if version != "" {
		t.Errorf("DetectVersion should return empty for unknown version, got %q", version)
	}
}

func TestDetectVersionMissingFile(t *testing.T) {
	version := DetectVersion("/nonexistent/latest.log")
	if version != "" {
		t.Errorf("DetectVersion should return empty for missing file, got %q", version)
	}
}

func TestLogReaderRotation(t *testing.T) {
	tmpDir := t.TempDir()
	logPath := filepath.Join(tmpDir, "rotate.log")

	os.WriteFile(logPath, []byte("old data\n"), 0644)

	reader, err := NewLogReader(logPath)
	if err != nil {
		t.Fatalf("NewLogReader: %v", err)
	}
	defer reader.Close()

	// Read existing content
	lines, _ := reader.ReadNewLines()
	if len(lines) != 0 {
		t.Error("should start at end of file")
	}

	// Simulate rotation: truncate and write new
	os.WriteFile(logPath, []byte("fresh start\n"), 0644)

	lines, err = reader.ReadNewLines()
	if err != nil {
		t.Fatalf("ReadNewLines after rotation: %v", err)
	}
	// Should handle the rotation (file got smaller)
	if len(lines) == 0 {
		t.Error("should read lines after rotation")
	}
}
