package service

import (
	"archive/tar"
	"archive/zip"
	"compress/gzip"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"sort"
	"strings"

	"github.com/shimu115/minecraft-server-docker/api/model"
)

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
func ListDir(baseDir, relPath string) ([]model.FileInfo, error) {
	dir, err := safePath(baseDir, relPath)
	if err != nil {
		return nil, err
	}

	entries, err := os.ReadDir(dir)
	if err != nil {
		return nil, err
	}

	files := make([]model.FileInfo, 0)
	for _, e := range entries {
		info, err := e.Info()
		if err != nil {
			continue
		}
		files = append(files, model.FileInfo{
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

// SaveUploadedFile 保存上传的文件
func SaveUploadedFile(baseDir, filename string, src io.Reader) error {
	fullPath, err := safePath(baseDir, filename)
	if err != nil {
		return err
	}
	os.MkdirAll(filepath.Dir(fullPath), 0755)
	dst, err := os.Create(fullPath)
	if err != nil {
		return err
	}
	defer dst.Close()
	_, err = io.Copy(dst, src)
	return err
}

// OpenFile 打开文件用于下载
func OpenFile(baseDir, relPath string) (*os.File, os.FileInfo, error) {
	fullPath, err := safePath(baseDir, relPath)
	if err != nil {
		return nil, nil, err
	}
	f, err := os.Open(fullPath)
	if err != nil {
		return nil, nil, err
	}
	info, err := f.Stat()
	if err != nil {
		f.Close()
		return nil, nil, err
	}
	return f, info, nil
}

// ExportDir 直接写压缩文件到 writer
func ExportDir(baseDir, format string, w io.Writer) error {
	base, err := resolveBase(baseDir)
	if err != nil {
		return err
	}
	switch format {
	case "zip":
		return writeZip(base, w)
	case "tar.gz":
		return writeTarGz(base, w)
	default:
		return fmt.Errorf("unsupported format: %s (use zip or tar.gz)", format)
	}
}

func writeZip(base string, w io.Writer) error {
	zw := zip.NewWriter(w)
	defer zw.Close()
	return filepath.Walk(base, func(path string, info os.FileInfo, err error) error {
		if err != nil {
			return err
		}
		rel, _ := filepath.Rel(base, path)
		if rel == "." {
			return nil
		}
		header, err := zip.FileInfoHeader(info)
		if err != nil {
			return err
		}
		header.Name = rel
		header.Method = zip.Deflate
		if info.IsDir() {
			header.Name += "/"
			_, err = zw.CreateHeader(header)
			return err
		}
		entry, err := zw.CreateHeader(header)
		if err != nil {
			return err
		}
		f, err := os.Open(path)
		if err != nil {
			return err
		}
		defer f.Close()
		_, err = io.Copy(entry, f)
		return err
	})
}

func writeTarGz(base string, w io.Writer) error {
	gw := gzip.NewWriter(w)
	defer gw.Close()
	tw := tar.NewWriter(gw)
	defer tw.Close()
	return filepath.Walk(base, func(path string, info os.FileInfo, err error) error {
		if err != nil {
			return err
		}
		rel, _ := filepath.Rel(base, path)
		if rel == "." {
			return nil
		}
		header, err := tar.FileInfoHeader(info, "")
		if err != nil {
			return err
		}
		header.Name = rel
		if err := tw.WriteHeader(header); err != nil {
			return err
		}
		if !info.IsDir() {
			f, err := os.Open(path)
			if err != nil {
				return err
			}
			defer f.Close()
			_, err = io.Copy(tw, f)
			return err
		}
		return nil
	})
}

// DeleteFile 删除文件或目录
func DeleteFile(baseDir, relPath string) error {
	fullPath, err := safePath(baseDir, relPath)
	if err != nil {
		return err
	}
	return os.RemoveAll(fullPath)
}
