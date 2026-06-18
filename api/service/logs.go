package service

import (
	"bufio"
	"io"
	"os"
	"strings"
	"sync"
)

// LogReader 追踪日志文件，只读取新增行
type LogReader struct {
	path   string
	file   *os.File
	offset int64
	mu     sync.Mutex
	buf    []byte // 不完整的尾行
}

// NewLogReader 创建日志读取器
func NewLogReader(path string) (*LogReader, error) {
	f, err := os.Open(path)
	if err != nil {
		return nil, err
	}
	// 定位到文件末尾，只读新内容
	offset, err := f.Seek(0, io.SeekEnd)
	if err != nil {
		f.Close()
		return nil, err
	}
	return &LogReader{
		path:   path,
		file:   f,
		offset: offset,
	}, nil
}

// ReadNewLines 读取上次偏移量到 EOF 之间的新增行
func (r *LogReader) ReadNewLines() ([]string, error) {
	r.mu.Lock()
	defer r.mu.Unlock()

	// 重新打开文件（处理日志轮转）
	current, err := os.Open(r.path)
	if err != nil {
		return nil, err
	}
	defer current.Close()

	fi, err := current.Stat()
	if err != nil {
		return nil, err
	}

	if fi.Size() < r.offset {
		// 日志轮转：文件变小，从新文件头开始读
		r.offset = 0
		// 关闭旧文件，用新文件替换
		r.file.Close()
		r.file, _ = os.Open(r.path)
		r.file.Seek(0, io.SeekStart)
	}

	if fi.Size() == r.offset {
		return nil, nil // 没有新内容
	}

	// 从上次位置读取
	buf := make([]byte, fi.Size()-r.offset)
	_, err = r.file.ReadAt(buf, r.offset)
	if err != nil && err != io.EOF {
		return nil, err
	}
	r.offset = fi.Size()

	// 拼接上次残留 + 本次读取，按行分割
	full := string(r.buf) + string(buf)
	lines := strings.Split(full, "\n")

	// 最后一行可能不完整，留到下次
	r.buf = []byte(lines[len(lines)-1])
	lines = lines[:len(lines)-1]

	// 过滤空行
	var result []string
	for _, line := range lines {
		if line != "" {
			result = append(result, line)
		}
	}
	return result, nil
}

// Close 关闭日志读取器
func (r *LogReader) Close() error {
	return r.file.Close()
}

// DetectVersion 从日志文件检测 Minecraft 版本
func DetectVersion(logPath string) string {
	f, err := os.Open(logPath)
	if err != nil {
		return ""
	}
	defer f.Close()

	scanner := bufio.NewScanner(f)
	for scanner.Scan() {
		line := scanner.Text()
		if strings.Contains(line, "Starting minecraft server version") {
			parts := strings.Fields(line)
			if len(parts) > 0 {
				return parts[len(parts)-1]
			}
		}
	}
	return ""
}
