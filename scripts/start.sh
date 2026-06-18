#!/bin/sh

set -e

# Source JDK utilities (auto-detection + download)
. /scripts/jdk.sh

# Source version resolution (auto-detect download URLs)
. /scripts/version-resolve.sh

# 环境变量配置
Xmx=${Xmx:-1024M}
Xms=${Xms:-1024M}
SERVER_TYPE=${SERVER_TYPE:-vanilla}
EULA=${EULA:-false}

WORKDIR="/minecraft"
mkdir -p "$WORKDIR"
cd "$WORKDIR"

# 自动检测 JDK 版本
JDK_VERSION=$(detect_java_version "$MC_VERSION" "$SERVER_TYPE")
echo "[info] MC_VERSION=${MC_VERSION:-unset} SERVER_TYPE=$SERVER_TYPE → JDK $JDK_VERSION"
download_jdk "$JDK_VERSION"
JAVA_HOME="/usr/lib/jvm/$JDK_VERSION"

# 根据 SERVER_TYPE 设置 JAR_FILE 和 DOWNLOAD_URL
case "$SERVER_TYPE" in
  vanilla)
    JAR_FILE=${JAR_FILE:-server.jar}
    if [ -z "$DOWNLOAD_URL" ] && [ -n "$MC_VERSION" ]; then
      echo "[info] Resolving vanilla download URL for $MC_VERSION..."
      DOWNLOAD_URL=$(resolve_vanilla_url "$MC_VERSION")
    fi
    if [ -z "$DOWNLOAD_URL" ] && [ ! -f "$JAR_FILE" ]; then
      DOWNLOAD_URL="https://piston-data.mojang.com/v1/objects/e6ec2f64e6080b9b5d9b471b291c33cc7f509733/server.jar"
    fi
    ;;

  forge)
    JAR_FILE=${JAR_FILE:-forge-installer.jar}
    if [ -z "$DOWNLOAD_URL" ] && [ -n "$MC_VERSION" ]; then
      echo "[info] Resolving Forge download URL for $MC_VERSION..."
      DOWNLOAD_URL=$(resolve_forge_url "$MC_VERSION")
    fi
    if [ -z "$DOWNLOAD_URL" ] && [ ! -f "$JAR_FILE" ]; then
      DOWNLOAD_URL="https://maven.minecraftforge.net/net/minecraftforge/forge/1.20.1-47.2.0/forge-1.20.1-47.2.0-installer.jar"
    fi
    ;;

  fabric)
    JAR_FILE=${JAR_FILE:-fabric-server-launch.jar}
    if [ -z "$DOWNLOAD_URL" ] && [ -n "$MC_VERSION" ]; then
      echo "[info] Resolving Fabric download URL for $MC_VERSION..."
      DOWNLOAD_URL=$(resolve_fabric_url "$MC_VERSION")
    fi
    if [ -z "$DOWNLOAD_URL" ] && [ ! -f "$JAR_FILE" ]; then
      DOWNLOAD_URL="https://meta.fabricmc.net/v2/versions/loader/1.20.1/0.14.21/1.0.0/server/jar"
    fi
    ;;

  neoforge)
    JAR_FILE=${JAR_FILE:-neoforge-installer.jar}
    if [ -z "$DOWNLOAD_URL" ] && [ -n "$MC_VERSION" ]; then
      echo "[info] Resolving NeoForge download URL for $MC_VERSION..."
      DOWNLOAD_URL=$(resolve_neoforge_url "$MC_VERSION")
    fi
    if [ -z "$DOWNLOAD_URL" ] && [ ! -f "$JAR_FILE" ]; then
      echo "[error] DOWNLOAD_URL not set and MC_VERSION not provided. Cannot continue."
      exit 1
    fi
    ;;

  *)
    echo "[error] Unknown server type: $SERVER_TYPE"
    exit 1
    ;;
esac

if [ -z "$DOWNLOAD_URL" ] && [ ! -f "$JAR_FILE" ]; then
  echo "[error] Could not determine download URL for $SERVER_TYPE (MC_VERSION=${MC_VERSION:-unset})"
  echo "[error] Please set DOWNLOAD_URL manually or provide a valid MC_VERSION."
  exit 1
fi

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

# Forge / NeoForge 安装器始终重新下载，避免版本残留
case "$SERVER_TYPE" in
  forge|neoforge)
    echo "[info] Downloading $JAR_FILE..."
    echo "[info] Download url $DOWNLOAD_URL"
    wget -q "$DOWNLOAD_URL" -O "$JAR_FILE"
    echo "[info] Downloaded $DOWNLOAD_URL."
    echo "[info] Downloaded $JAR_FILE."
    ;;
  *)
    download_if_needed "$JAR_FILE"
    ;;
esac

echo "eula=$EULA" > eula.txt

# Forge / NeoForge 安装
case "$SERVER_TYPE" in
  forge|neoforge)
    if [ ! -d "libraries" ]; then
      echo "[info] Installing $SERVER_TYPE server..."
      java -jar "$JAR_FILE" --installServer

      echo "[info] $SERVER_TYPE install finished. Files in workdir:"
      ls -la

      if [ "$SERVER_TYPE" = "forge" ] && [ -f "run.sh" ]; then
        mv run.sh forge-launcher.sh
        chmod +x forge-launcher.sh
        echo "[info] Renamed Forge run.sh to forge-launcher.sh"
      fi
    else
      echo "[info] $SERVER_TYPE already installed. Skipping installer."
    fi

    if [ "$SERVER_TYPE" = "forge" ]; then
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
    ;;
esac

# 写入环境变量文件，供 API 服务读取
cat > /minecraft/.env << EOF
export JAVA_HOME="$JAVA_HOME"
export Xmx="$Xmx"
export Xms="$Xms"
export SERVER_TYPE="$SERVER_TYPE"
export JAR_FILE="$JAR_FILE"
EOF

echo "[info] Environment written to /minecraft/.env"
echo "[info] Starting mc-api..."

exec /usr/local/bin/mc-api
