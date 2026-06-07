#!/bin/sh

set -e

# ========= 加载环境变量 =========

if [ -f /minecraft/.env ]; then
  . /minecraft/.env
fi

: "${JAVA_HOME:?JAVA_HOME not set — run start.sh first}"
: "${Xmx:=1024M}"
: "${Xms:=1024M}"
: "${SERVER_TYPE:=vanilla}"
: "${JAR_FILE:?JAR_FILE not set — run start.sh first}"

export PATH="$JAVA_HOME/bin:$PATH"
cd /minecraft

echo "[info] Starting $SERVER_TYPE Minecraft server..."
echo "[info] JAVA_HOME=$JAVA_HOME"
echo "[info] Xmx=$Xmx Xms=$Xms"
echo "[info] JAR=$JAR_FILE"

# 清理旧日志，避免读到上一次的残留
rm -f logs/latest.log

case "$SERVER_TYPE" in
  forge)
    if [ -f "./forge-launcher.sh" ]; then
      echo "[info] Forge launcher: forge-launcher.sh"
      screen -L -S mcserver -dm ./forge-launcher.sh
    else
      # 旧版 Forge (1.12.2 及更早) — 直接运行 forge-*.jar
      FORGE_JAR=$(find . -maxdepth 1 -name "forge-*.jar" -not -name "*installer*" 2>/dev/null | head -1)
      if [ -n "$FORGE_JAR" ]; then
        echo "[info] Forge launcher: $FORGE_JAR"
        screen -L -S mcserver -dm java -Xmx$Xmx -Xms$Xms -jar "$FORGE_JAR" nogui
      else
        echo "[error] No Forge launcher found"
        ls -la
        exit 1
      fi
    fi
    ;;
  vanilla|fabric)
    screen -L -S mcserver -dm java -Xmx$Xmx -Xms$Xms -jar "$JAR_FILE" nogui
    ;;
  *)
    echo "[error] Unknown server type: $SERVER_TYPE"
    exit 1
    ;;
esac

# 等待日志文件出现，超时 30 秒
LOG_FILE="logs/latest.log"
TIMEOUT=30
i=0
while [ ! -f "$LOG_FILE" ]; do
  sleep 1
  i=$((i + 1))
  if [ "$i" -ge "$TIMEOUT" ]; then
    echo "[error] Server failed to start within ${TIMEOUT}s."
    echo "[error] --- screenlog ---"
    cat screenlog.0 2>/dev/null || true
    echo "[error] --- end screenlog ---"
    exit 1
  fi
done

echo "[info] Server is running. Streaming logs..."
tail -F "$LOG_FILE"
