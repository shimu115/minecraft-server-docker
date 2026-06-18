# API 开发过程文档

## 项目概述

为 Minecraft Server Docker 项目构建 Go 语言 HTTP API 服务，实现在浏览器端远程管理 Minecraft 服务端。

## 技术栈

| 层级 | 技术 | 说明 |
|------|------|------|
| API 服务 | Go 1.23 | 标准库 `net/http`，零外部依赖 |
| 容器运行时 | Docker (debian:bookworm) | 多阶段构建，静态编译 Go 二进制 |
| 服务端操控 | GNU Screen | 通过 `screen -X stuff` 向 MC 控制台发送指令 |
| 日志推送 | SSE (Server-Sent Events) | `fetch` + `ReadableStream` 实现实时日志流 |
| 认证 | Bearer Token + UUID | API Key 存储在文件中，首次启动自动生成 |
| 前端测试 | Vue 3 + Vite | 单文件组件，通过代理转发 API 请求 |
| 文件压缩 | archive/zip + archive/tar + compress/gzip | 标准库实现 zip / tar.gz 导出 |

## 架构设计

```
┌─────────────────────────────────────────────────┐
│ Docker Container (debian:bookworm)               │
│                                                  │
│  ┌──────────────┐     ┌──────────────────┐      │
│  │  mc-api (:25560) │───>│ screen mcserver  │      │
│  │  Go HTTP API  │     │  java -jar ...   │      │
│  │               │     │                  │      │
│  │  handlers/ ───┤     │  /minecraft/     │      │
│  │  service/ ────┤     │  ├── logs/       │      │
│  │  middleware/ ──┤     │  ├── world/      │      │
│  │  model/ ──────┘     │  └── ...         │      │
│  └──────────────┘     └──────────────────┘      │
│         │                      │                 │
│         │ SSE                  │ 日志文件        │
│         ▼                      ▼                 │
│  HTTP Client (Vue 测试页)      logs/latest.log  │
└──────────────────────────────────────────────────┘
```

## 目录结构

```
api/
├── main.go              # 入口：路由注册、中间件链、启动逻辑
├── go.mod               # 模块定义，go 1.23
├── model/
│   └── types.go         # 统一响应结构体 APIResponse
├── middleware/
│   ├── auth.go          # Bearer Token 认证 + 表单 key 参数
│   └── cors.go          # 跨域中间件（CORS + Auth 顺序处理）
├── handler/
│   ├── health.go        # 健康检查 + 公共 helper（writeJSON/writeError/writeOK）
│   ├── mcserver.go      # 服务端管理：start / stop / restart / status
│   ├── command.go       # 向 MC 发送指令
│   ├── logs.go          # SSE 日志流 + 心跳保活
│   └── files.go         # 文件管理：list / read / write / delete / upload / download / export
└── service/
    ├── screen.go        # Screen 会话操作 + MC 启动命令构建
    ├── auth.go          # API Key 初始化 / 校验 / 刷新
    ├── logs.go          # LogReader：追踪日志文件偏移量、处理轮转
    └── files.go         # 文件操作 + 目录压缩（zip / tar.gz）
```

## 开发流程

### 第一阶段：基础框架搭建

**目标**：建立 HTTP 服务骨架，确定中间件顺序。

1. 创建 `main.go`，使用 Go 1.22+ 标准库 `http.ServeMux` 的方法匹配路由（如 `"GET /api/health"`）
2. 实现 CORS 中间件，设置 `Access-Control-Allow-Origin`、`Access-Control-Allow-Methods`、`Access-Control-Allow-Headers`
3. 实现 Auth 中间件，解析 `Authorization: Bearer <token>` 请求头，校验 API Key
4. 中间件顺序：`CORS(Auth(mux))` — CORS 先处理 OPTIONS 预检，Auth 后校验实际请求

**问题与解决**：
- 初始中间件顺序为 `Auth(CORS(mux))`，导致 OPTIONS 预检被 Auth 拦截返回 403 → 调换顺序为 `CORS(Auth(mux))`

### 第二阶段：MC 服务端管理

**目标**：通过 GNU Screen 操控 Minecraft 服务端进程。

1. 实现 `service/screen.go`：`SessionExists()`、`SendCommand()`、`StartServer()`、`StopServer()`
2. 通过 `screen -S mcserver -X stuff "command\n"` 向 MC 控制台发送指令
3. 通过 `screen -ls` 检查会话状态，排除 `Dead` 状态
4. 从 `.env` 文件读取 `JAVA_HOME`、`JAR_FILE` 等参数构建启动命令

**问题与解决**：
- 容器重启后残留死会话，`SessionExists()` 误判为"已运行" → 启动时执行 `screen -wipe`，检查时排除 `Dead` 状态
- Forge 与 vanilla/fabric 启动命令不同 → `BuildStartCommand()` 根据 `SERVER_TYPE` 构建

### 第三阶段：实时日志推送

**目标**：通过 SSE 将 MC 日志实时推送到浏览器。

1. 实现 `service/logs.go`：`LogReader` 结构体追踪文件偏移量
2. 200ms 轮询 `logs/latest.log`，读取新增行并以 SSE 格式输出
3. 检测日志轮转（文件 inode 变化或大小回退），自动重新打开
4. 15 秒心跳保活，防止代理/浏览器超时断开

**问题与解决**：
- 浏览器原生 `EventSource` 不支持自定义请求头 → 改用 `fetch` + `ReadableStream` 实现 SSE 客户端
- `http.Flusher` 接口不能直接用作 `io.Writer` → heartbeat 函数接收独立的 `ResponseWriter` 和 `Flusher` 参数

### 第四阶段：认证体系

**目标**：API Key 管理，未认证请求拒绝访问。

1. 使用 `crypto/rand` 生成 UUID v4 格式的 API Key
2. 首次启动生成并写入 `./auth/api_key.txt`，控制台打印
3. 实现 `POST /api/auth/refresh` 刷新 Key，旧 Key 立即失效
4. `/api/health` 免认证，其余接口必须携带有效 Key

**问题与解决**：
- EventSource 不支持请求头 → 用 `fetch` + `ReadableStream` 替代
- 表单下载（导出接口）无法携带 Authorization → Auth 中间件额外支持 `r.FormValue("key")`

### 第五阶段：文件管理

**目标**：浏览器端管理 MC 数据目录文件。

1. 实现目录列表 `GET /api/files/list`、读取 `GET /api/files/read`、写入 `POST /api/files/write`、删除 `DELETE /api/files/delete`
2. 路径安全检查：`filepath.Abs()` 转绝对路径 + `strings.HasPrefix()` 防目录穿越
3. 实现上传 `POST /api/files/upload`（multipart）、下载 `GET /api/files/download`（流式）
4. 实现导出 `POST /api/files/export`：标准库 `archive/zip` 和 `archive/tar` + `compress/gzip` 直接写响应流

**问题与解决**：
- 相对路径 `"."` 导致安全校验 `HasPrefix("logs", ".")` 失败 → 改用 `filepath.Abs()` 转为绝对路径后比较
- 导出接口 `fetch + blob` 处理大文件时 `broken pipe` → 改用表单提交触发浏览器原生下载
- 导出写入临时文件再流式传输导致断连 → 改为直接写 `http.ResponseWriter`，省去中间文件

### 第六阶段：Docker 集成

**目标**：Go API 编译为静态二进制，拷入 Minecraft Server 镜像。

1. 多阶段构建：`golang:1.23` 编译（`CGO_ENABLED=0`）→ `debian:bookworm` 运行
2. `start.sh` 在初始化完成后 `exec /usr/local/bin/mc-api`，API 成为容器主进程
3. `AUTO_START=true` 时 API 启动后自动启动 MC 服务端
4. API 日志与 MC 日志加前缀 `[mc-api]` / `[mc-server]` 统一输出到 stdout

**问题与解决**：
- `AUTO_START` 在容器重启时误判 → 先 `screen -wipe` 清理死会话再检查
- 本地开发时绝对路径 `/minecraft` 不存在 → 所有路径改用相对路径，`MC_DIR` 默认 `"."`

### 第七阶段：Vue 测试页面

**目标**：可视化测试所有 API 接口。

1. Vue 3 + Vite 单文件组件
2. Vite 代理 `/api/*` → `localhost:25560`，开发环境免跨域
3. 左右双栏布局：左栏控制面板（连接、状态、指令、文件管理），右栏实时日志
4. 文件管理支持多选、上传、下载、删除、在线编辑、目录导出

**问题与解决**：
- 日志面板撑开页面 → `main-layout` 设 `height: calc(100vh - 120px)`，左栏 `overflow-y: auto`
- 删除请求触发 CORS 预检失败 → 允许 `DELETE` 方法
- 上传用独立 fetch（FormData，不带 `Content-Type: application/json`）

### 第八阶段：自动 JDK 选择与目录重构（P0）

**目标**：根据 `MC_VERSION` 自动推断 JDK 版本，重构项目目录为 bootstrap 架构做准备。

1. 新增 `scripts/jdk.sh`：提取 `download_jdk` 函数 + 新增 `detect_java_version` 版本解析函数
2. 重构 `scripts/start.sh`：统一 JDK 检测逻辑，移除各 `case` 分支中分散的 `JAVA_VERSION` 默认值
3. 优先级链：`JAVA_VERSION` 显式指定 > `MC_VERSION` 自动映射 > `SERVER_TYPE` 默认兜底
4. 映射规则：1.16- → Java 8，1.17 → Java 16，1.18~1.20.4 → Java 17，1.20.5+ → Java 21
5. 目录重构：脚本移入 `scripts/`（`start.sh`、`run.sh`、`jdk.sh`），文档整合到 `docs/`
6. Dockerfile 改为 `COPY ./scripts/ /scripts/`，CMD 更新为 `/scripts/start.sh`
7. `docker-compose.yaml` 示例改用 `MC_VERSION: "1.12.2"` 替代 `JAVA_VERSION: "8"`

**问题与解决**：
- POSIX sh 不支持 Bash 的 `[[ ]]` 和 `${var:-default}` 高级语法 → 全部使用 `[ ]` + `-n`/`-eq`/`-le` 标准写法
- Minecraft 版本号解析："1.20.5" 的 patch 数字 "5" 可能被 `cut -d. -f3` 解析为空（两段版本如 "1.20"） → `patch="${patch:-0}"` 兜底，保证整数比较安全
- 1.20.5 边界处理：minor=20 时需要额外检查 patch≥5 → 嵌套 if 判断精确分流
- `local` 关键字在 POSIX 中非标准但被 `dash`/`ash` 广泛支持 → 沿用项目现有惯例（`start.sh` 已使用）
- Forge 安装器生成的 `run.sh` 与项目 `run.sh` 同名冲突 → 项目中 `start.sh` 已将 Forge 的 `run.sh` 重命名为 `forge-launcher.sh`，且项目 `run.sh` 移入 `scripts/` 后不再混淆
- `docker exec mc-server ./run.sh` 路径失效 → 更新为绝对路径 `/scripts/run.sh`，避免与 `/minecraft` 目录下的文件混淆

### 第九阶段：服务端版本自动解析 + Go API 模块化 + 单元测试（P1）

**目标**：用户只需 `SERVER_TYPE` + `MC_VERSION` 即可启动，无需手动填写 `DOWNLOAD_URL`；Go API 代码分层规范化并添加测试覆盖。

**Part A: Go API 模块化**（6 项重构）：
1. 移除 `model/types.go` 中未使用的 `FTPStartRequest`、`FTPStatusResponse` 类型，替换为 `FileInfo`（从 service 层迁入）
2. 合并 handler helper：`writeJSON`/`writeError`/`writeOK` 从 `health.go` 移至 `helpers.go`
3. `getScreenUptime()` 从 handler 下沉到 `service/screen.go`，导出为 `GetScreenUptime()`
4. `detectVersion()` 从 handler 下沉到 `service/logs.go`，导出为 `DetectVersion()`
5. handler 中重复的 `exec.Command("screen", "-wipe")` 替换为 `service.CleanupDeadSessions()`
6. `FileInfo` 结构体从 `service/files.go` 迁至 `model/types.go`，service 通过 `model.FileInfo` 引用

**Part B: 单元测试**（8 个测试文件）：
- `model/types_test.go`：APIResponse JSON 序列化、FileInfo 结构体验证
- `service/auth_test.go`：API Key 生成/校验/刷新/UUID 格式，使用 `t.TempDir()` 隔离文件系统
- `service/files_test.go`：路径安全校验、目录列表、读写删除、上传、导出 zip/tar.gz
- `service/screen_test.go`：`.env` 解析、启动命令构建（vanilla/forge）
- `service/logs_test.go`：LogReader 偏移量追踪、DetectVersion 版本检测
- `handler/health_test.go`：健康检查、writeJSON/writeError/writeOK 辅助函数
- `middleware/auth_test.go`：免认证路径跳过、无 token 拒绝、Bearer 提取、表单 key 参数
- `middleware/cors_test.go`：CORS 响应头、OPTIONS 预检返回 204
- `const` 改 `var`：`authDir` 从 `const` 改为 `var` 以便测试覆盖文件路径

**Part C: 服务端版本自动解析**：
1. 新增 `scripts/version-resolve.sh`：4 个解析函数通过上游 API 自动获取下载地址
   - `resolve_vanilla_url`：查询 Mojang Version Manifest → 版本详情 → downloads.server.url
   - `resolve_forge_url`：查询 Forge Maven metadata XML → 解析版本列表 → installer jar URL
   - `resolve_fabric_url`：查询 Fabric Meta API → 最新 stable loader + installer → server/jar URL
   - `resolve_neoforge_url`：转换 MC 版本前缀（去 "1."）→ NeoForge Maven metadata → installer jar URL
2. 新增 `neoforge` 服务端类型：`scripts/jdk.sh` 默认 JDK 17，`scripts/start.sh` 支持安装流程
3. `scripts/start.sh` 集成：`DOWNLOAD_URL` 为空时自动调用对应 resolve 函数，Forge/NeoForge 共享安装逻辑
4. 兜底机制：API 解析失败时回退到硬编码默认 URL，绝对兜底报错提示用户手动设置

**问题与解决**：
- Fabric loader API 返回的 JSON 结构嵌套复杂（loader 版本在对象 key 中） → 用 `grep -o` 匹配 `"stable":true` 标记定位最新稳定版本
- NeoForge 版本号展平格式（`1.21.1` → `21.1.`）与 beta 后缀混杂 → 优先 `grep -v -- '-beta\|-alpha'` 过滤测试版，无稳定版再回退包含
- Forge maven-metadata.xml 中 `<latest>` 是全局最新而非特定 MC 版本 → 只用 `<version>` 列表 + `grep "^<mc_version>-"` 精确过滤
- Vanilla 版本清单两级查询（manifest → detail）→ 分两步：先通过 version `id` 定位 detail URL，再取 `downloads.server.url`
- handler 中 `getScreenUptime`/`detectVersion` 直接操作 OS 命令/文件违反分层 → 下沉到 service 层，handler 仅调用 service 导出函数
- `const authDir` 导致测试无法隔离文件系统 → 改为 `var` 允许测试覆盖路径
- handler/mcserver.go 移除了 `getScreenUptime`、`detectVersion` 及不再需要的 import（`bufio`/`os`/`os/exec`/`strings`）

## API 路由总览

| 方法 | 路径 | 认证 | 功能 |
|------|------|------|------|
| `GET` | `/api/health` | 否 | 健康检查 |
| `GET` | `/api/server/status` | 是 | 服务端状态 |
| `POST` | `/api/server/start` | 是 | 启动 MC |
| `POST` | `/api/server/stop` | 是 | 停止 MC |
| `POST` | `/api/server/restart` | 是 | 重启 MC |
| `POST` | `/api/command` | 是 | 发送指令 |
| `GET` | `/api/logs?tail=N` | 是 | SSE 日志流 |
| `POST` | `/api/auth/refresh` | 是 | 刷新 API Key |
| `GET` | `/api/files/list?path=` | 是 | 列出目录 |
| `GET` | `/api/files/read?path=` | 是 | 读取文件 |
| `POST` | `/api/files/write?path=` | 是 | 写入文件 |
| `DELETE` | `/api/files/delete?path=` | 是 | 删除文件 |
| `POST` | `/api/files/upload` | 是 | 上传文件 |
| `GET` | `/api/files/download?path=` | 是 | 下载文件 |
| `POST` | `/api/files/export` | 是 | 导出目录 |
