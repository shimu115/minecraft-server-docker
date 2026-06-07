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

case "$SERVER_TYPE" in
  forge)
    screen -S mcserver -dm ./run.sh
    ;;
  vanilla|fabric)
    screen -S mcserver -dm java -Xmx$Xmx -Xms$Xms -jar "$JAR_FILE" nogui
    ;;
  *)
    echo "[error] Unknown server type: $SERVER_TYPE"
    exit 1
    ;;
esac

# 等待日志文件出现
LOG_FILE="logs/latest.log"
while [ ! -f "$LOG_FILE" ]; do
  sleep 1
done

echo "[info] Server is running. Streaming logs..."
tail -F "$LOG_FILE"
