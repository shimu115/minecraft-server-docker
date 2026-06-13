# Changelog

## v1.0.0-beta (2026-06-07) `Pre-release`

> Git tag: `v1.0.0-beta` · Commit: `38d2572`

### 🚀 首个 Docker 镜像版本 (测试版)

基于 `debian:bookworm` 构建，支持 Vanilla、Forge、Fabric 三种 Minecraft 服务端类型，容器启动时自动下载对应 JDK（Eclipse Temurin）和服务端 jar。

### ✨ 功能特性

- **多核心支持**：支持 `vanilla` / `forge` / `fabric` 三种服务端类型。
- **灵活环境**：通过 `JAVA_VERSION` 环境变量指定 JDK 版本（8 / 17 / 21）。
- **动态下载**：支持自定义 `DOWNLOAD_URL` 下载任意版本的服务端 jar。
- **性能配置**：可配置 JVM 内存参数 `Xmx` / `Xms`。
- **热更新逻辑**：`run.sh` 从 GitHub 仓库拉取，更新启动逻辑无需重建镜像。

---

### ⚠️ 合规性说明与法律免责声明 (Important Notice)

**关于 EULA 协议自动同意行为：**
* **技术逻辑**：此测试版本（v1.0.0）的 `start.sh` 脚本中直接硬编码写入了 `echo "eula=true" > eula.txt`，未通过环境变量交由用户显式同意 Minecraft EULA。
* **免责条款**：本项目基于 **Apache-2.0 许可证** 开源，软件按“原样”提供。任何下载、部署并运行此版本镜像的最终用户，**即代表您已完整阅读、知晓并自愿遵守** [Minecraft 最终用户许可协议 (EULA)](https://aka.ms/MinecraftEULA)。因使用本历史版本产生的任何版权或合规争议，均由最终用户自行承担，项目作者不承担任何连带法律责任。
* **解决方案**：该默认同意的合规瑕疵已在后续正式稳定版中修复（新版已通过 `EULA` 环境变量控制，默认为 `false`，要求用户显式设置为 `true` 才能启动）。**强烈建议您停止使用 v1.0.0 镜像，并切换至最新稳定版。**

> 附：`eula.txt` 官方原文参考：
> *By changing the setting below to TRUE you are indicating your agreement to our EULA (https://aka.ms/MinecraftEULA).*
