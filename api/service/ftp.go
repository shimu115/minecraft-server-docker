package service

import (
	"fmt"
	"os"
	"os/exec"
	"strconv"
	"strings"
)

const (
	ftpConfigPath  = "/tmp/vsftpd.conf"
	ftpPidFile     = "/tmp/vsftpd.pid"
	ftpDefaultPort = 21
)

// FTPStatus 返回 FTP 运行状态
func FTPStatus() (bool, uint16) {
	cmd := exec.Command("pgrep", "vsftpd")
	if err := cmd.Run(); err != nil {
		return false, 0
	}
	port := ftpDefaultPort
	if data, err := os.ReadFile(ftpConfigPath); err == nil {
		for _, line := range strings.Split(string(data), "\n") {
			if strings.HasPrefix(strings.TrimSpace(line), "listen_port=") {
				p := strings.TrimPrefix(strings.TrimSpace(line), "listen_port=")
				if n, err := strconv.Atoi(p); err == nil {
					port = n
				}
			}
		}
	}
	return true, uint16(port)
}

// StartFTP 启动 FTP 服务，ftpRoot 为 FTP 根目录（绝对路径）
func StartFTP(port uint16, username, password string) error {
	if running, _ := FTPStatus(); running {
		return fmt.Errorf("FTP is already running")
	}
	if port == 0 {
		port = ftpDefaultPort
	}

	// 设置 root 密码
	setPassCmd := fmt.Sprintf("echo '%s:%s' | chpasswd", username, password)
	if err := exec.Command("sh", "-c", setPassCmd).Run(); err != nil {
		return fmt.Errorf("failed to set password: %w", err)
	}

	// 获取当前工作目录作为 FTP 根目录
	ftpRoot, err := os.Getwd()
	if err != nil {
		ftpRoot = "/minecraft"
	}

	// 生成 vsftpd 配置
	config := fmt.Sprintf(`listen=YES
listen_port=%d
anonymous_enable=NO
local_enable=YES
write_enable=YES
local_umask=022
chroot_local_user=YES
allow_writeable_chroot=YES
local_root=%s
pasv_enable=YES
pasv_min_port=30000
pasv_max_port=30009
xferlog_enable=YES
xferlog_file=%s/logs/vsftpd.log
`, port, ftpRoot, ftpRoot)

	if err := os.WriteFile(ftpConfigPath, []byte(config), 0644); err != nil {
		return fmt.Errorf("failed to write vsftpd config: %w", err)
	}

	// 启动 vsftpd
	cmd := exec.Command("vsftpd", ftpConfigPath)
	if err := cmd.Start(); err != nil {
		return fmt.Errorf("failed to start vsftpd: %w", err)
	}
	go cmd.Wait()

	return nil
}

// StopFTP 停止 FTP 服务
func StopFTP() error {
	if running, _ := FTPStatus(); !running {
		return fmt.Errorf("FTP is not running")
	}
	if err := exec.Command("pkill", "vsftpd").Run(); err != nil {
		return fmt.Errorf("failed to stop vsftpd: %w", err)
	}
	os.Remove(ftpPidFile)
	return nil
}
