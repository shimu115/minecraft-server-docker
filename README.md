# Minecraft Server Docker

一键部署 Minecraft 服务端的 Docker 镜像，支持 Vanilla、Forge、Fabric。

## 快速开始

```bash
docker run -d \
  --name mc-server \
  -p 25565:25565 \
  -v $(pwd)/minecraft:/minecraft \
  -e SERVER_TYPE=vanilla \
  -e EULA=true \
  shimu778/minecraft-server:1.0.0
```
**注意**：设置 `EULA=true` 即代表您同意 [Minecraft 最终用户许可协议 (EULA)](https://aka.ms/MinecraftEULA)。

首次启动会自动下载 JDK 和服务端 jar，稍等片刻即可连接 `localhost:25565`。

## 支持的服务端类型

| SERVER_TYPE | 默认 Minecraft 版本 | 默认 JDK | 说明 |
|-------------|---------------------|----------|------|
| `vanilla` | 1.21 | 21 | 原版服务端（默认） |
| `forge` | 1.20.1 | 17 | Forge 模组服务端 |
| `fabric` | 1.20.1 | 17 | Fabric 模组服务端 |

## 环境变量

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `SERVER_TYPE` | `vanilla` | 服务端类型：`vanilla` / `forge` / `fabric` |
| `MC_VERSION` | — | Minecraft 版本号，如 `1.12.2`、`1.21.1`，用于自动推断 JDK 版本 |
| `JAVA_VERSION` | `auto` | JDK 大版本号：`8` / `16` / `17` / `21`，设为 `auto` 或省略时根据 `MC_VERSION` 自动选择 |
| `DOWNLOAD_URL` | 按类型自动选择 | 服务端 jar 下载地址 |
| `JAR_FILE` | 按类型自动选择 | 服务端 jar 文件名 |
| `Xmx` | `1024M` | 最大内存 |
| `Xms` | `1024M` | 初始内存 |
| `TZ` | — | 时区，如 `Asia/Shanghai` |

> **JDK 自动选择**：当 `JAVA_VERSION` 未设置或设为 `auto` 时，系统根据 `MC_VERSION` 自动选择合适的 JDK：
>
> | Minecraft 版本 | 自动选择 JDK |
> |---------------|-------------|
> | 1.7.x ~ 1.16.x | JDK 8 |
> | 1.17.x | JDK 16 |
> | 1.18.x ~ 1.20.4 | JDK 17 |
> | 1.20.5+ | JDK 21 |
>
> 高级用户可通过 `JAVA_VERSION=17` 强制覆盖自动选择。

## 使用示例

```bash
docker pull shimu778/minecraft-server:v1.0.0

docker run -d \
  --name mc-forge \
  -p 25565:25565 \
  -v $(pwd)/minecraft:/minecraft \
  -e SERVER_TYPE=forge \
  -e MC_VERSION=1.12.2 \
  -e DOWNLOAD_URL="https://maven.minecraftforge.net/net/minecraftforge/forge/1.12.2-14.23.5.2864/forge-1.12.2-14.23.5.2864-installer.jar" \
  -e Xms=1024M \
  -e Xmx=4096M \
  -e TZ=Asia/Shanghai \
  -e EULA=true \
  shimu778/minecraft-server:1.0.0
```
**注意**：设置 `EULA=true` 即代表您同意 [Minecraft 最终用户许可协议 (EULA)](https://aka.ms/MinecraftEULA)。

### Docker Compose

可直接下载项目中的 `docker-compose.yaml` 文件使用：

```bash
# Linux / macOS / Windows (PowerShell)
curl -O https://raw.githubusercontent.com/shimu115/minecraft-server-docker/refs/heads/main/docker-compose.yaml

docker compose up -d
```

根据需求修改 `docker-compose.yaml` 中的环境变量（如 `SERVER_TYPE`、`JAVA_VERSION`、`DOWNLOAD_URL`、`Xmx` 等）后启动即可。

## 重启服务端

服务端运行在 screen 会话中，重启命令：

```bash
docker exec mc-server /scripts/run.sh
```

## 工作原理

1. 容器启动执行 `scripts/start.sh`，根据 `MC_VERSION` 自动选择 JDK 版本，按需下载服务端 jar
2. Forge 类型会自动执行 `--installServer` 完成安装
3. 环境变量写入 `/minecraft/.env`
4. `/scripts/run.sh` 读取 `.env` 在 screen 会话中启动服务端并 tail 日志

镜像不捆绑 JDK，所有依赖在容器启动时按需下载，镜像体积小且无许可证风险。

## 构建

```bash
docker build -t minecraft-server .
```

GitHub Actions 在推送 tag（`v*`）时自动构建并推送到 Docker Hub。
