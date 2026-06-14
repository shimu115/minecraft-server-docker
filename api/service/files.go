package service

import (
	"fmt"
	"os"
	"path/filepath"
	"sort"
	"strings"
)

// FileInfo 文件信息
type FileInfo struct {
	Name    string `json:"name"`
	Path    string `json:"path"`
	IsDir   bool   `json:"isDir"`
	Size    int64  `json:"size"`
	ModTime string `json:"modTime"`
}

// resolveBase 将 baseDir 转为绝对路径
func resolveBase(baseDir string) (string, error) {
	abs, err := filepath.Abs(baseDir)
	if err != nil {
		return "", err
	}
	return filepath.Clean(abs), nil
}

// safePath 安全检查：不允许访问 baseDir 之外的路径
func safePath(baseDir, relPath string) (string, error) {
	base, err := resolveBase(baseDir)
	if err != nil {
		return "", err
	}
	full := filepath.Join(base, relPath)
	full, err = filepath.Abs(full)
	if err != nil {
		return "", err
	}
	full = filepath.Clean(full)
	if !strings.HasPrefix(full, base+string(os.PathSeparator)) && full != base {
		return "", fmt.Errorf("access denied")
	}
	return full, nil
}

// ListDir 列出目录内容
func ListDir(baseDir, relPath string) ([]FileInfo, error) {
	dir, err := safePath(baseDir, relPath)
	if err != nil {
		return nil, err
	}

	entries, err := os.ReadDir(dir)
	if err != nil {
		return nil, err
	}

	files := make([]FileInfo, 0)
	for _, e := range entries {
		info, err := e.Info()
		if err != nil {
			continue
		}
		files = append(files, FileInfo{
			Name:    e.Name(),
			Path:    filepath.Join(relPath, e.Name()),
			IsDir:   e.IsDir(),
			Size:    info.Size(),
			ModTime: info.ModTime().Format("2006-01-02 15:04:05"),
		})
	}

	sort.Slice(files, func(i, j int) bool {
		if files[i].IsDir != files[j].IsDir {
			return files[i].IsDir
		}
		return files[i].Name < files[j].Name
	})

	return files, nil
}

// ReadFile 读取文件内容
func ReadFile(baseDir, relPath string) (string, error) {
	fullPath, err := safePath(baseDir, relPath)
	if err != nil {
		return "", err
	}
	data, err := os.ReadFile(fullPath)
	if err != nil {
		return "", err
	}
	return string(data), nil
}

// WriteFile 写入文件
func WriteFile(baseDir, relPath, content string) error {
	fullPath, err := safePath(baseDir, relPath)
	if err != nil {
		return err
	}
	os.MkdirAll(filepath.Dir(fullPath), 0755)
	return os.WriteFile(fullPath, []byte(content), 0644)
}

// DeleteFile 删除文件或目录
func DeleteFile(baseDir, relPath string) error {
	fullPath, err := safePath(baseDir, relPath)
	if err != nil {
		return err
	}
	return os.RemoveAll(fullPath)
}
