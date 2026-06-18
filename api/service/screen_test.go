package service

import (
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func TestParseEnvFile(t *testing.T) {
	tmpDir := t.TempDir()
	envPath := filepath.Join(tmpDir, ".env")

	content := `export JAVA_HOME="/usr/lib/jvm/17"
export Xmx="2048M"
export Xms="1024M"
export SERVER_TYPE="forge"
export JAR_FILE="forge-installer.jar"
# this is a comment

`
	if err := os.WriteFile(envPath, []byte(content), 0644); err != nil {
		t.Fatalf("write .env: %v", err)
	}

	env, err := parseEnvFile(envPath)
	if err != nil {
		t.Fatalf("parseEnvFile: %v", err)
	}

	if env["JAVA_HOME"] != "/usr/lib/jvm/17" {
		t.Errorf("JAVA_HOME = %q, want /usr/lib/jvm/17", env["JAVA_HOME"])
	}
	if env["Xmx"] != "2048M" {
		t.Errorf("Xmx = %q, want 2048M", env["Xmx"])
	}
	if env["SERVER_TYPE"] != "forge" {
		t.Errorf("SERVER_TYPE = %q, want forge", env["SERVER_TYPE"])
	}
	if env["JAR_FILE"] != "forge-installer.jar" {
		t.Errorf("JAR_FILE = %q, want forge-installer.jar", env["JAR_FILE"])
	}
}

func TestParseEnvFileMissing(t *testing.T) {
	_, err := parseEnvFile("/nonexistent/.env")
	if err == nil {
		t.Error("parseEnvFile should fail for missing file")
	}
}

func TestBuildStartCommandVanilla(t *testing.T) {
	tmpDir := t.TempDir()
	envPath := filepath.Join(tmpDir, ".env")

	content := `export JAVA_HOME="/usr/lib/jvm/21"
export Xmx="1024M"
export Xms="512M"
export SERVER_TYPE="vanilla"
export JAR_FILE="server.jar"
`
	os.WriteFile(envPath, []byte(content), 0644)

	cmd, err := BuildStartCommand(tmpDir)
	if err != nil {
		t.Fatalf("BuildStartCommand: %v", err)
	}

	if !strings.Contains(cmd, "-Xmx1024M") {
		t.Error("command should contain -Xmx1024M")
	}
	if !strings.Contains(cmd, "-Xms512M") {
		t.Error("command should contain -Xms512M")
	}
	if !strings.Contains(cmd, "server.jar") {
		t.Error("command should contain server.jar")
	}
	if !strings.Contains(cmd, "nogui") {
		t.Error("command should contain nogui")
	}
}

func TestBuildStartCommandForge(t *testing.T) {
	tmpDir := t.TempDir()
	envPath := filepath.Join(tmpDir, ".env")

	content := `export JAVA_HOME="/usr/lib/jvm/17"
export Xmx="4096M"
export Xms="1024M"
export SERVER_TYPE="forge"
export JAR_FILE="forge-installer.jar"
`
	os.WriteFile(envPath, []byte(content), 0644)

	cmd, err := BuildStartCommand(tmpDir)
	if err != nil {
		t.Fatalf("BuildStartCommand: %v", err)
	}

	// Without forge-launcher.sh, should fall back to java -jar command
	if !strings.Contains(cmd, "-jar") {
		t.Error("forge command should contain -jar")
	}
}

func TestBuildStartCommandMissingEnv(t *testing.T) {
	_, err := BuildStartCommand("/nonexistent")
	if err == nil {
		t.Error("BuildStartCommand should fail without .env")
	}
}

func TestBuildStartCommandMissingFields(t *testing.T) {
	tmpDir := t.TempDir()
	envPath := filepath.Join(tmpDir, ".env")

	// Missing JAVA_HOME
	content := `export Xmx="1024M"
export SERVER_TYPE="vanilla"
`
	os.WriteFile(envPath, []byte(content), 0644)

	_, err := BuildStartCommand(tmpDir)
	if err == nil {
		t.Error("BuildStartCommand should fail without JAVA_HOME")
	}
}
