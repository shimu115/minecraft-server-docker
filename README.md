# Minecraft Server Docker

一键部署 Minecraft 服务端的 Docker 镜像 + Web 管理面板，支持 Vanilla、Forge、Fabric、NeoForge、Paper、Purpur。

## 项目概述

本项目提供 Minecraft 服务端的完整容器化部署与管理方案，分为两个组件：

| 组件 | 目录 | 说明 |
|------|------|------|
| **Minecraft Server 容器** | `mc-server/` | Docker 镜像，内置 Go HTTP API Agent，一键启动 MC 服务端 |
| **Web 管理面板** | `panel/` | Spring Boot + Vue 3，多实例统一管理（开发中） |

```
浏览器 ──→ Vue 3 面板 ──→ Spring Boot ──→ Go API (mc-api) ──→ Minecraft Server
                ↑                ↑                ↑
           Naive UI        JWT + RBAC        GNU Screen + SSE
```

- **Go API**：运行在 MC 容器内的 Agent，提供 REST API + SSE 实时日志流，零外部依赖
- **Spring Boot**：业务中间层，用户/Key/实例管理，权限控制，代理转发请求到 Go API
- **Vue 3 前端**：Web 管理面板，Naive UI 组件库，TypeScript 类型安全

> 详细架构说明见 [docs/architecture.md](docs/architecture.md)

---

## 快速开始（Minecraft Server 容器）

```bash
docker run -d \
  --name mc-server \
  -p 25565:25565 \
  -v $(pwd)/minecraft:/minecraft \
  -e SERVER_TYPE=vanilla \
  -e EULA=true \
  shimu/minecraft-server:api-dev
```

> **注意**：设置 `EULA=true` 即代表您同意 [Minecraft 最终用户许可协议 (EULA)](https://aka.ms/MinecraftEULA)。

首次启动会自动下载 JDK 和服务端 jar，稍等片刻即可连接 `localhost:25565`。

---

## 支持的服务端类型

| SERVER_TYPE | 默认 MC 版本 | 默认 JDK | 说明 |
|-------------|-------------|----------|------|
| `vanilla` | 1.21 | 21 | 原版服务端（默认） |
| `forge` | 1.20.1 | 17 | Forge 模组服务端 |
| `fabric` | 1.20.1 | 17 | Fabric 模组服务端 |
| `neoforge` | 1.21.1 | 17 | NeoForge 模组服务端 |
| `paper` | 1.21 | 21 | Paper 插件服务端 |
| `purpur` | 1.21 | 21 | Purpur 插件服务端 |

---

## 环境变量

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `SERVER_TYPE` | `vanilla` | 服务端类型：`vanilla` / `forge` / `fabric` / `neoforge` / `paper` / `purpur` |
| `MC_VERSION` | — | Minecraft 版本号，如 `1.12.2`、`1.21.1`，用于自动推断 JDK 并解析下载地址 |
| `JAVA_VERSION` | `auto` | JDK 大版本号：`8` / `16` / `17` / `21`，设为 `auto` 时根据 `MC_VERSION` 自动选择 |
| `DOWNLOAD_URL` | 自动解析 | 服务端 jar 下载地址（仅需 `MC_VERSION` 即可自动获取，也可手动覆盖） |
| `JAR_FILE` | 按类型自动选择 | 服务端 jar 文件名 |
| `Xmx` | `1024M` | 最大内存 |
| `Xms` | `1024M` | 初始内存 |
| `TZ` | — | 时区，如 `Asia/Shanghai` |

### JDK 自动选择

当 `JAVA_VERSION` 未设置或设为 `auto` 时，系统根据 `MC_VERSION` 自动选择合适的 JDK：

| Minecraft 版本 | 自动选择 JDK |
|---------------|-------------|
| 1.7.x ~ 1.16.x | JDK 8 |
| 1.17.x | JDK 16 |
| 1.18.x ~ 1.20.4 | JDK 17 |
| 1.20.5+ | JDK 21 |

高级用户可通过 `JAVA_VERSION=17` 强制覆盖。

### 服务端版本自动解析

只需配置 `SERVER_TYPE` + `MC_VERSION`，系统自动从上游 API 获取下载地址：

| 服务端类型 | 上游 API |
|-----------|----------|
| Vanilla | Mojang Version Manifest |
| Forge | Forge Maven Metadata |
| Fabric | Fabric Meta API |
| NeoForge | NeoForge Maven Metadata |
| Paper | Paper API |
| Purpur | Purpur API |

---

## 使用示例

### 最简配置（Vanilla 1.21）

```bash
docker run -d \
  --name mc-vanilla \
  -p 25565:25565 \
  -v $(pwd)/minecraft:/minecraft \
  -e SERVER_TYPE=vanilla \
  -e MC_VERSION=1.21 \
  -e EULA=true \
  shimu/minecraft-server:api-dev
```

### Forge 模组服

```bash
docker run -d \
  --name mc-forge \
  -p 25565:25565 \
  -v $(pwd)/minecraft:/minecraft \
  -e SERVER_TYPE=forge \
  -e MC_VERSION=1.12.2 \
  -e Xms=1024M \
  -e Xmx=4096M \
  -e TZ=Asia/Shanghai \
  -e EULA=true \
  shimu/minecraft-server:api-dev
```

---

## Docker Compose

项目提供 `docker-compose.yaml`，包含 Minecraft Server 容器和 Web 管理面板容器：

```bash
# 下载 compose 文件
curl -O https://raw.githubusercontent.com/shimu115/minecraft-server-docker/refs/heads/main/docker-compose.yaml

# 启动全部服务
docker compose up -d
```

根据需求修改 `.env` 或 `docker-compose.yaml` 中的环境变量后启动即可。

> **面板容器说明**：Web 管理面板目前处于开发阶段（`api-dev`），仅用于本地开发调试，不建议直接暴露公网。生产部署方案见 P4 规划。

---

## Go API（mc-api）

每个 MC 容器内运行一个 Go HTTP API（监听 `25560`），提供 REST 接口操控 Minecraft 服务端。

### API 路由

| 方法 | 路径 | 认证 | 功能 |
|------|------|------|------|
| `GET` | `/api/health` | 否 | 健康检查 |
| `GET` | `/api/server/status` | 是 | 服务端状态（运行/玩家数/版本/uptime） |
| `POST` | `/api/server/start` | 是 | 启动 MC |
| `POST` | `/api/server/stop` | 是 | 停止 MC |
| `POST` | `/api/server/restart` | 是 | 重启 MC |
| `POST` | `/api/command` | 是 | 发送控制台指令 |
| `GET` | `/api/logs` | 是 | SSE 实时日志流 |
| `POST` | `/api/auth/refresh` | 是 | 刷新 API Key |
| `GET` | `/api/files/list` | 是 | 列出目录 |
| `GET` | `/api/files/read` | 是 | 读取文件 |
| `POST` | `/api/files/write` | 是 | 写入文件 |
| `DELETE` | `/api/files/delete` | 是 | 删除文件 |
| `POST` | `/api/files/upload` | 是 | 上传文件（multipart） |
| `GET` | `/api/files/download` | 是 | 下载文件 |
| `POST` | `/api/files/export` | 是 | 导出目录（zip/tar.gz） |

完整 API 文档见 [mc-server/api-doc/api.md](mc-server/api-doc/api.md)

### 认证

首次启动自动生成 UUID v4 格式的 API Key 并打印到控制台。通过 `docker logs <container>` 查看：

```
[mc-api] New API Key generated: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
```

所有需要认证的接口携带 `Authorization: Bearer <api-key>` 请求头。

---

## Web 管理面板（开发中）

面板提供多实例、多用户的 Web 管理界面。技术栈：

| 层级 | 技术 | 说明 |
|------|------|------|
| 后端 | Spring Boot 3.x | JWT 认证 + JPA + H2/MySQL |
| 前端 | Vue 3 + Vite + TypeScript | Naive UI 组件库 |
| 通信 | AgentClient | Spring Boot → Go API 代理转发 |

### 功能概览

- **用户管理**：ROOT / ADMIN / USER 三层角色，实例级访问控制
- **API Key 管理**：注册、吊销、脱敏展示，AES-256-GCM 静态加密存储
- **实例管理**：多 MC 实例注册、Key 绑定、健康检查、Key 在线刷新
- **服务端控制**：启动/停止/重启、发送指令、SSE 实时日志
- **权限体系（P3）**：路径级 ACL + 受保护资源 + 操作审计日志

### 本地开发

```bash
# 1. Spring Boot 后端
cd panel/backend
mvn spring-boot:run

# 2. Vue 前端（开发服务器，热更新）
cd panel/web
npm install
npm run dev          # → localhost:5173，代理 /api → localhost:8080

# 3. Go API（直接运行，用于调试）
cd mc-server/api
go run .             # → localhost:25560
```

---

## 项目结构

```
minecraft-server-docker/
├── mc-server/                        # Minecraft 服务端容器
│   ├── Dockerfile                    # 多阶段构建（Go 编译 → Debian 运行）
│   ├── scripts/
│   │   ├── start.sh                  # 容器入口脚本
│   │   ├── run.sh                    # 重启 MC 服务端
│   │   ├── jdk.sh                    # JDK 版本自动选择
│   │   └── version-resolve.sh        # 服务端下载地址自动解析
│   ├── api/                          # Go HTTP API
│   │   ├── main.go                   # 入口（路由 + 中间件链）
│   │   ├── handler/                  # HTTP 处理器
│   │   ├── service/                  # 业务逻辑
│   │   ├── middleware/               # Auth + CORS 中间件
│   │   └── model/                    # 数据模型
│   ├── api-doc/api.md                # Go API 接口文档
│   └── test/                         # Vue 测试页面（API 调试用）
│
├── panel/                            # Web 管理面板
│   ├── backend/                      # Spring Boot 后端
│   │   └── src/main/java/com/mcpanel/panel/
│   │       ├── common/               # ApiResponse + ErrorCode
│   │       ├── config/               # Security + JWT + 全局异常处理
│   │       ├── entity/               # JPA 实体
│   │       ├── repository/           # 数据访问层
│   │       ├── service/              # 业务逻辑层
│   │       ├── controller/           # REST API 控制器
│   │       ├── agent/                # Go API HTTP 客户端
│   │       ├── crypto/               # AES-256-GCM 加密
│   │       ├── annotation/           # @RequireInstanceAccess AOP
│   │       └── dto/                  # 数据传输对象
│   └── web/                          # Vue 3 前端
│       └── src/
│           ├── api/                  # axios 客户端 + API 模块
│           ├── views/                # 页面组件
│           ├── components/           # 公共组件
│           ├── router/               # 路由配置
│           └── utils/                # 工具函数
│
├── docs/                             # 项目文档
│   ├── architecture.md               # 项目架构文档
│   ├── requirements/                 # 需求文档
│   │   ├── planning-v1.md            # 总体规划（P0~P4）
│   │   └── panel-design.md           # 权限与安全设计
│   ├── development/                  # 开发文档
│   │   ├── develop-process.md        # Go API 开发过程
│   │   ├── springboot-infra-plan.md  # Spring Boot 基础设施方案
│   │   ├── frontend-plan.md          # Vue 3 前端技术方案
│   │   └── p3-plan.md                # P3 业务逻辑深化方案
│   └── specification/                # 开发规范文档
│
├── docker-compose.yaml               # Docker Compose 编排
├── CHANGELOG.md                      # 变更日志
└── README.md                         # 本文件
```

---

## 工作原理

### 容器启动流程

1. `scripts/start.sh` 执行：根据 `MC_VERSION` 自动选择 JDK → 从上游 API 解析下载地址 → 下载服务端 jar
2. Forge / NeoForge 类型自动执行 `--installServer` 完成安装
3. 环境变量写入 `/minecraft/.env`
4. Go HTTP API（`mc-api`）接管容器主进程，通过 GNU Screen 管理 MC 进程
5. 配置 `AUTO_START=true` 时 API 启动后自动启动 MC 服务端

### 请求链路（以启动 MC 为例）

```
浏览器 → Vue SPA (axios + JWT)
          → Spring Boot Controller (@RequireInstanceAccess AOP)
            → AgentClient (从 DB 取出 Key, Bearer 携带)
              → Go API (:25560, Auth 中间件校验 Key)
                → screen -X stuff "启动命令" → Minecraft Server
```

### 安全架构

双层安全保护：

- **Spring Boot（业务层）**：JWT 认证 → 角色校验（ROOT/ADMIN/USER）→ 实例绑定检查 → 路径级 ACL
- **Go API（底层兜底）**：Bearer Token 校验 → 硬编码保护路径列表 → `force` 参数控制危险操作

即使 Spring Boot 层权限逻辑出现异常，Go API 的硬编码保护仍能阻止核心资源（world/配置文件等）被误删。

---

## 构建

```bash
# Minecraft Server 镜像
cd mc-server
docker build -t minecraft-server .

# Web 管理面板镜像（开发中）
cd panel/backend
mvn package -DskipTests
```

GitHub Actions 在推送 tag（`v*`）时自动构建并推送到 Docker Hub。

---

## 路线图

| 阶段 | 内容 | 状态 |
|------|------|------|
| **P0** | 自动 JDK 选择、项目目录重构 | ✅ 已完成 |
| **P1** | 服务端版本自动解析、Go API 模块化、单元测试 | ✅ 已完成 |
| **P2** | Spring Boot + Vue 3 全栈基础设施（用户/Key/实例管理） | 🔄 进行中 |
| **P3** | 权限体系（三层模型 + 路径级 ACL）+ 文件管理 + 玩家管理 + 操作审计 | 📋 规划中 |
| **P4** | 多节点/集群管理、nginx 反向代理、分布式 Agent | 📋 规划中 |

---

## 文档

完整文档见 [docs/](docs/) 目录：

| 文档 | 说明 |
|------|------|
| [架构文档](docs/architecture.md) | 系统分层架构、数据流、安全设计、技术栈 |
| [总体规划](docs/requirements/planning-v1.md) | P0~P4 规划蓝图、Go API 扩展、权限安全体系 |
| [权限设计](docs/requirements/panel-design.md) | 三层角色模型、双重安全校验、文件删除流程 |
| [Go API 开发过程](docs/development/develop-process.md) | 9 个开发阶段、问题与解决方案 |
| [Spring Boot 方案](docs/development/springboot-infra-plan.md) | 后端项目结构、数据库设计、API 设计、AgentClient |
| [Vue 前端方案](docs/development/frontend-plan.md) | 技术选型、路由设计、axios 拦截器、页面交互 |
| [Go API 接口文档](mc-server/api-doc/api.md) | 完整 API 参考（健康检查/服务端控制/文件管理/SSE） |
| [开发规范](docs/specification/README.md) | 项目结构、实体对象、REST API、ApiResponse 规范 |

---

## License

[Apache-2.0](LICENSE)
