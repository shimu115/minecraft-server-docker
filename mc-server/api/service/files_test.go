package service

import (
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/shimu115/minecraft-server-docker/api/model"
)

func TestResolveBase(t *testing.T) {
	abs, err := resolveBase(".")
	if err != nil {
		t.Fatalf("resolveBase(.): %v", err)
	}
	if !filepath.IsAbs(abs) {
		t.Errorf("resolveBase should return absolute path, got %s", abs)
	}
}

func TestSafePath(t *testing.T) {
	tmpDir := t.TempDir()

	// Normal path should work
	p, err := safePath(tmpDir, "test.txt")
	if err != nil {
		t.Fatalf("safePath: %v", err)
	}
	if p != filepath.Join(tmpDir, "test.txt") {
		t.Errorf("safePath = %s, want %s", p, filepath.Join(tmpDir, "test.txt"))
	}

	// Directory traversal should fail
	_, err = safePath(tmpDir, "../etc/passwd")
	if err == nil {
		t.Error("safePath should reject directory traversal")
	}

	// Base dir itself should pass
	p, err = safePath(tmpDir, ".")
	if err != nil {
		t.Fatalf("safePath(.): %v", err)
	}
}

func TestListDir(t *testing.T) {
	tmpDir := t.TempDir()

	// Create some files and dirs
	os.WriteFile(filepath.Join(tmpDir, "a.txt"), []byte("a"), 0644)
	os.WriteFile(filepath.Join(tmpDir, "b.txt"), []byte("bb"), 0644)
	os.MkdirAll(filepath.Join(tmpDir, "subdir"), 0755)
	os.WriteFile(filepath.Join(tmpDir, "subdir", "c.txt"), []byte("ccc"), 0644)

	files, err := ListDir(tmpDir, ".")
	if err != nil {
		t.Fatalf("ListDir: %v", err)
	}

	if len(files) != 3 {
		t.Errorf("ListDir returned %d entries, want 3", len(files))
	}

	// Dirs should come first
	if !files[0].IsDir {
		t.Error("first entry should be a directory")
	}

	// Check subdir listing
	subFiles, err := ListDir(tmpDir, "subdir")
	if err != nil {
		t.Fatalf("ListDir(subdir): %v", err)
	}
	if len(subFiles) != 1 {
		t.Errorf("ListDir(subdir) returned %d entries, want 1", len(subFiles))
	}
}

func TestReadWriteFile(t *testing.T) {
	tmpDir := t.TempDir()

	err := WriteFile(tmpDir, "test.txt", "hello world")
	if err != nil {
		t.Fatalf("WriteFile: %v", err)
	}

	content, err := ReadFile(tmpDir, "test.txt")
	if err != nil {
		t.Fatalf("ReadFile: %v", err)
	}
	if content != "hello world" {
		t.Errorf("ReadFile = %q, want %q", content, "hello world")
	}
}

func TestWriteFileCreatesDir(t *testing.T) {
	tmpDir := t.TempDir()

	err := WriteFile(tmpDir, "newdir/nested/file.txt", "content")
	if err != nil {
		t.Fatalf("WriteFile: %v", err)
	}

	content, err := ReadFile(tmpDir, "newdir/nested/file.txt")
	if err != nil {
		t.Fatalf("ReadFile: %v", err)
	}
	if content != "content" {
		t.Errorf("ReadFile = %q, want %q", content, "content")
	}
}

func TestDeleteFile(t *testing.T) {
	tmpDir := t.TempDir()

	err := WriteFile(tmpDir, "deleteme.txt", "bye")
	if err != nil {
		t.Fatalf("WriteFile: %v", err)
	}

	err = DeleteFile(tmpDir, "deleteme.txt")
	if err != nil {
		t.Fatalf("DeleteFile: %v", err)
	}

	_, err = ReadFile(tmpDir, "deleteme.txt")
	if err == nil {
		t.Error("ReadFile should fail after delete")
	}
}

func TestDeleteDir(t *testing.T) {
	tmpDir := t.TempDir()

	os.MkdirAll(filepath.Join(tmpDir, "todelete", "nested"), 0755)
	os.WriteFile(filepath.Join(tmpDir, "todelete", "file.txt"), []byte("x"), 0644)

	err := DeleteFile(tmpDir, "todelete")
	if err != nil {
		t.Fatalf("DeleteFile: %v", err)
	}

	_, err = os.Stat(filepath.Join(tmpDir, "todelete"))
	if !os.IsNotExist(err) {
		t.Error("directory should not exist after delete")
	}
}

func TestSaveUploadedFile(t *testing.T) {
	tmpDir := t.TempDir()

	content := "uploaded content"
	err := SaveUploadedFile(tmpDir, "uploads/file.txt", strings.NewReader(content))
	if err != nil {
		t.Fatalf("SaveUploadedFile: %v", err)
	}

	read, err := ReadFile(tmpDir, "uploads/file.txt")
	if err != nil {
		t.Fatalf("ReadFile: %v", err)
	}
	if read != content {
		t.Errorf("content = %q, want %q", read, content)
	}
}

func TestOpenFile(t *testing.T) {
	tmpDir := t.TempDir()

	os.WriteFile(filepath.Join(tmpDir, "download.bin"), []byte("binary"), 0644)

	f, info, err := OpenFile(tmpDir, "download.bin")
	if err != nil {
		t.Fatalf("OpenFile: %v", err)
	}
	defer f.Close()

	if info.Size() != 6 {
		t.Errorf("Size = %d, want 6", info.Size())
	}
}

func TestExportDir(t *testing.T) {
	tmpDir := t.TempDir()

	os.WriteFile(filepath.Join(tmpDir, "data.txt"), []byte("test data"), 0644)

	// Test zip export
	var buf strings.Builder
	err := ExportDir(tmpDir, "zip", &buf)
	if err != nil {
		t.Fatalf("ExportDir(zip): %v", err)
	}
	if buf.Len() == 0 {
		t.Error("zip export should produce output")
	}

	// Test tar.gz export
	var buf2 strings.Builder
	err = ExportDir(tmpDir, "tar.gz", &buf2)
	if err != nil {
		t.Fatalf("ExportDir(tar.gz): %v", err)
	}
	if buf2.Len() == 0 {
		t.Error("tar.gz export should produce output")
	}

	// Test invalid format
	var buf3 strings.Builder
	err = ExportDir(tmpDir, "invalid", &buf3)
	if err == nil {
		t.Error("ExportDir should reject invalid format")
	}
}

// Verify model.FileInfo is used (compile-time check)
var _ []model.FileInfo
