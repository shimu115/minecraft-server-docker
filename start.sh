#!/bin/sh

set -e

# ========= 环境变量配置 =========

JAVA_VERSION=${JAVA_VERSION:-21}
export JAVA_HOME="/usr/lib/jvm/$JAVA_VERSION"

case "$JAVA_VERSION" in
  8|17|21)
    ;;
  *)
    echo "[ERROR] Unsupported JAVA_VERSION: $JAVA_VERSION (only 8, 17, 21 allowed)"
    exit 1
    ;;
esac

export PATH="$JAVA_HOME/bin:$PATH"

Xmx=${Xmx:-1024M}
Xms=${Xms:-1024M}
SERVER_TYPE=${SERVER_TYPE:-vanilla}

WORKDIR="/minecraft"
mkdir -p "$WORKDIR"
cd "$WORKDIR"

# 根据 SERVER_TYPE 设置默认 JAR_FILE 和 DOWNLOAD_URL
case "$SERVER_TYPE" in
  vanilla)
    JAR_FILE=${JAR_FILE:-server.jar}
    if [ -z "$DOWNLOAD_URL" ] && [ ! -f "$JAR_FILE" ]; then
      DOWNLOAD_URL="https://piston-data.mojang.com/v1/objects/e6ec2f64e6080b9b5d9b471b291c33cc7f509733/server.jar"
    fi
    ;;

  forge)
    JAR_FILE=${JAR_FILE:-forge-installer.jar}
    if [ -z "$DOWNLOAD_URL" ] && [ ! -f "$JAR_FILE" ]; then
      DOWNLOAD_URL="https://maven.minecraftforge.net/net/minecraftforge/forge/1.20.1-47.2.0/forge-1.20.1-47.2.0-installer.jar"
    fi
    ;;

  fabric)
    JAR_FILE=${JAR_FILE:-fabric-server-launch.jar}
    if [ -z "$DOWNLOAD_URL" ] && [ ! -f "$JAR_FILE" ]; then
      DOWNLOAD_URL="https://meta.fabricmc.net/v2/versions/loader/1.20.1/0.14.21/1.0.0/server/jar"
    fi
    ;;

  *)
    echo "[error] Unknown server type: $SERVER_TYPE"
    exit 1
    ;;
esac

# 下载函数
download_if_needed() {
  if [ ! -f "$1" ]; then
    if [ -z "$DOWNLOAD_URL" ]; then
      echo "[error] DOWNLOAD_URL not set and $1 does not exist. Cannot continue."
      exit 1
    fi
    echo "[info] Downloading $1..."
    wget -q "$DOWNLOAD_URL" -O "$1"
    echo "[info] Downloaded $1."
  else
    echo "[info] $1 already exists. Skipping download."
  fi
}

# Forge 内存调整
adjust_forge_memory() {
  JVM_ARGS_FILE="user_jvm_args.txt"
  if [ -f "$JVM_ARGS_FILE" ]; then
    sed -i "s/-Xmx[^ ]*/-Xmx$Xmx/" "$JVM_ARGS_FILE"
    sed -i "s/-Xms[^ ]*/-Xms$Xms/" "$JVM_ARGS_FILE"
    echo "[info] Updated memory in $JVM_ARGS_FILE"
  else
    echo "-Xmx$Xmx -Xms$Xms" > "$JVM_ARGS_FILE"
    echo "[info] Created $JVM_ARGS_FILE with memory settings"
  fi
}

# 执行下载 & 安装
download_if_needed "$JAR_FILE"
echo "eula=true" > eula.txt

if [ "$SERVER_TYPE" = "forge" ]; then
  if [ ! -f "run.sh" ]; then
    echo "[info] Installing Forge server..."
    java -jar "$JAR_FILE" --installServer
    chmod +x run.sh
  else
    echo "[info] Forge already installed. Skipping installer."
  fi

  adjust_forge_memory
fi

# 启动服务端
echo "[info] Starting $SERVER_TYPE Minecraft server..."

case "$SERVER_TYPE" in
  forge)
    screen -S mcserver -dm ./run.sh
    ;;
  vanilla|fabric)
    screen -S mcserver -dm java -Xmx$Xmx -Xms$Xms -jar "$JAR_FILE" nogui
    ;;
esac

# 日志输出
LOG_FILE="logs/latest.log"
while [ ! -f "$LOG_FILE" ]; do
  sleep 1
done

echo "[info] Server is running. Streaming logs..."
tail -F "$LOG_FILE"

