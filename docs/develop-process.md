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
