package service

import (
	"os/exec"
	"strings"
)

const ScreenName = "mcserver"

// SessionExists 检查 screen 会话是否存在
func SessionExists() bool {
	cmd := exec.Command("screen", "-ls")
	out, err := cmd.Output()
	if err != nil {
		return false
	}
	return strings.Contains(string(out), ScreenName)
}

// SendCommand 向 MC 服务端发送指令
func SendCommand(command string) error {
	// screen -X stuff 后需要跟一个换行符模拟回车
	cmd := exec.Command("screen", "-S", ScreenName, "-X", "stuff", command+"\n")
	return cmd.Run()
}

// StartServer 启动 MC 服务端
func StartServer(javaCmd string) error {
	cmd := exec.Command("screen", "-L", "-S", ScreenName, "-dm", "sh", "-c", javaCmd)
	return cmd.Run()
}

// StopServer 发送 stop 指令关闭服务端
func StopServer() error {
	return SendCommand("stop")
}
