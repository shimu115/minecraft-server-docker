#!/bin/sh

set -e

# 环境变量配置
Xmx=${Xmx:-1024M}
Xms=${Xms:-1024M}
SERVER_TYPE=${SERVER_TYPE:-vanilla}
RUN_SCRIPT_URL=${RUN_SCRIPT_URL:-https://raw.githubusercontent.com/shimu115/minecraft-server-docker/refs/heads/main/run.sh}
EULA=${EULA:-false}

WORKDIR="/minecraft"
mkdir -p "$WORKDIR"
cd "$WORKDIR"

# JDK 下载函数
download_jdk() {
  local jdk_version="$1"
  local jdk_dir="/usr/lib/jvm/${jdk_version}"

  if [ -d "$jdk_dir" ] && [ -x "$jdk_dir/bin/java" ]; then
    echo "[info] JDK $jdk_version already present, skipping download."
    return
  fi

  echo "[info] Downloading JDK $jdk_version..."
  mkdir -p "$jdk_dir"
  wget -q -O /tmp/jdk.tar.gz \
    "https://api.adoptium.net/v3/binary/latest/${jdk_version}/ga/linux/x64/jdk/hotspot/normal/eclipse"
  tar -xzf /tmp/jdk.tar.gz -C "$jdk_dir" --strip-components=1
  rm /tmp/jdk.tar.gz
  echo "[info] JDK $jdk_version installed to $jdk_dir"
}

# 根据 SERVER_TYPE 下载对应 JDK 并设置参数
case "$SERVER_TYPE" in
  vanilla)
    JDK_VERSION=${JAVA_VERSION:-21}
    download_jdk "$JDK_VERSION"
    JAVA_HOME="/usr/lib/jvm/$JDK_VERSION"
    JAR_FILE=${JAR_FILE:-server.jar}
    if [ -z "$DOWNLOAD_URL" ] && [ ! -f "$JAR_FILE" ]; then
      DOWNLOAD_URL="https://piston-data.mojang.com/v1/objects/e6ec2f64e6080b9b5d9b471b291c33cc7f509733/server.jar"
    fi
    ;;

  forge)
    JDK_VERSION=${JAVA_VERSION:-17}
    download_jdk "$JDK_VERSION"
    JAVA_HOME="/usr/lib/jvm/$JDK_VERSION"
    JAR_FILE=${JAR_FILE:-forge-installer.jar}
    if [ -z "$DOWNLOAD_URL" ] && [ ! -f "$JAR_FILE" ]; then
      DOWNLOAD_URL="https://maven.minecraftforge.net/net/minecraftforge/forge/1.20.1-47.2.0/forge-1.20.1-47.2.0-installer.jar"
    fi
    ;;

  fabric)
    JDK_VERSION=${JAVA_VERSION:-17}
    download_jdk "$JDK_VERSION"
    JAVA_HOME="/usr/lib/jvm/$JDK_VERSION"
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

export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"

# 下载服务端 jar
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

# Forge 安装器始终重新下载，避免版本残留
if [ "$SERVER_TYPE" = "forge" ]; then
  echo "[info] Downloading $JAR_FILE..."
  wget -q "$DOWNLOAD_URL" -O "$JAR_FILE"
  echo "[info] Downloaded $JAR_FILE."
else
  download_if_needed "$JAR_FILE"
fi

echo "eula=$EULA" > eula.txt

# Forge 安装
if [ "$SERVER_TYPE" = "forge" ]; then
  # 用 libraries/ 目录判断是否已安装（新旧 Forge 都会生成）
  if [ ! -d "libraries" ]; then
    echo "[info] Installing Forge server..."
    java -jar "$JAR_FILE" --installServer

    echo "[info] Forge install finished. Files in workdir:"
    ls -la

    # Forge >=1.16 会生成 run.sh，改名避免与我们的 run.sh 冲突
    if [ -f "run.sh" ]; then
      mv run.sh forge-launcher.sh
      chmod +x forge-launcher.sh
      echo "[info] Renamed Forge run.sh to forge-launcher.sh"
    fi
  else
    echo "[info] Forge already installed. Skipping installer."
  fi

  JVM_ARGS_FILE="user_jvm_args.txt"
  if [ -f "$JVM_ARGS_FILE" ]; then
    sed -i "s/-Xmx[^ ]*/-Xmx$Xmx/" "$JVM_ARGS_FILE"
    sed -i "s/-Xms[^ ]*/-Xms$Xms/" "$JVM_ARGS_FILE"
    echo "[info] Updated memory in $JVM_ARGS_FILE"
  else
    echo "-Xmx$Xmx -Xms$Xms" > "$JVM_ARGS_FILE"
    echo "[info] Created $JVM_ARGS_FILE with memory settings"
  fi
fi

# 写入环境变量文件，供 run.sh 读取
cat > /minecraft/.env << EOF
export JAVA_HOME="$JAVA_HOME"
export Xmx="$Xmx"
export Xms="$Xms"
export SERVER_TYPE="$SERVER_TYPE"
export JAR_FILE="$JAR_FILE"
EOF

echo "[info] Environment written to /minecraft/.env"

# 从 GitHub 下载启动脚本
echo "[info] Downloading run.sh from $RUN_SCRIPT_URL ..."
wget -q -O /minecraft/run.sh "$RUN_SCRIPT_URL"
chmod +x /minecraft/run.sh
echo "[info] run.sh ready."

exec ./run.sh
