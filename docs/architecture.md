# 项目架构文档

## 概述

Minecraft Server Docker 是一个完整的 Minecraft 服务端容器化部署与 Web 管理面板系统。用户通过 Docker 一键部署 Minecraft 服务端，并通过 Web 面板对多个服务器实例进行统一管理。

系统采用 **四层架构**：Docker 运行时 → Go Agent API → Spring Boot 业务层 → Vue 3 前端。

---

## 系统分层架构

```text
┌──────────────────────────────────────────────────────────────────┐
│                        浏览器 (Browser)                           │
│                    Vue 3 SPA (Naive UI)                           │
│                http://<panel-host>:8080                           │
└──────────────────────────┬───────────────────────────────────────┘
                           │  HTTP (JWT Bearer)
                           ▼
┌──────────────────────────────────────────────────────────────────┐
│                  Docker 容器: panel                               │
│                                                                   │
│  ┌─────────────────────────────────────────────────────────────┐ │
│  │  Spring Boot (:8080)                                         │ │
│  │                                                               │ │
│  │  ┌──────────┐  ┌──────────┐  ┌────────────┐  ┌───────────┐ │ │
│  │  │ Security  │  │ Controller│  │  Service   │  │  Agent    │ │ │
│  │  │ JWT +    │──│ REST API │──│  Business  │──│  Client   │ │ │
│  │  │ RBAC     │  │          │  │  Logic     │  │  (HTTP)   │ │ │
│  │  └──────────┘  └──────────┘  └────────────┘  └─────┬─────┘ │ │
│  │                                                      │       │ │
│  │  ┌────────────────────────────────────────────────┐ │       │ │
│  │  │  数据库 (H2 / MySQL)                             │ │       │ │
│  │  │  users / api_keys / server_instances /          │ │       │ │
│  │  │  user_instances / user_path_permissions /       │ │       │ │
│  │  │  config_property_permissions /                  │ │       │ │
│  │  │  protected_resources / operation_logs           │ │       │ │
│  │  └────────────────────────────────────────────────┘ │       │ │
│  │                                                               │ │
│  │  ┌──────────────────────────────────────────────────────┐    │ │
│  │  │  Vue 静态资源 (classpath:/static)                     │    │ │
│  │  └──────────────────────────────────────────────────────┘    │ │
│  └─────────────────────────────────────────────────────────────┘ │
└──────────────────────────┬───────────────────────────────────────┘
                           │  HTTP (Bearer API Key)
                           ▼
┌──────────────────────────────────────────────────────────────────┐
│             Docker 容器: mc-server (每个 MC 实例一个)              │
│                                                                   │
│  ┌─────────────────────────────────────────────────────────────┐ │
│  │  Go HTTP API (mc-api :25560)                                 │ │
│  │                                                               │ │
│  │  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌───────────┐  │ │
│  │  │ CORS     │──│ Auth     │──│ Handler  │──│ Service   │  │ │
│  │  │ 中间件   │  │ Bearer   │  │ REST    │  │ Screen    │  │ │
│  │  │          │  │ Token    │  │ 路由    │  │ Logs      │  │ │
│  │  │          │  │          │  │         │  │ Files     │  │ │
│  │  └──────────┘  └──────────┘  └──────────┘  └─────┬─────┘  │ │
│  │                                                    │        │ │
│  │  ┌──────────────────────────────────────────────┐ │        │ │
│  │  │  API Key 存储 (auth/api_key.txt)              │ │        │ │
│  │  └──────────────────────────────────────────────┘ │        │ │
│  └──────────────────────────────────────────────────────┬────┘ │
│                                                          │       │
│  ┌──────────────────────────────────────────────────────┐│       │
│  │  GNU Screen 会话 (mcserver)                           ││       │
│  │  ┌────────────────────────────────────────────────┐  ││       │
│  │  │  Minecraft Server (java -jar ...)               │  ││       │
│  │  │  /minecraft/                                    │  ││       │
│  │  │  ├── world/          ├── mods/                  │  ││       │
│  │  │  ├── config/         ├── plugins/               │  ││       │
│  │  │  ├── logs/           ├── server.properties      │  ││       │
│  │  │  └── ...                                        │  ││       │
│  │  └────────────────────────────────────────────────┘  ││       │
│  └──────────────────────────────────────────────────────┘│       │
└──────────────────────────────────────────────────────────────────┘
```

---

## 各层详解

### 第 1 层：Docker 运行时（Minecraft 容器）

**定位**：Minecraft 服务端的运行环境，每个容器 = 1 个 MC 实例。

**目录**：`mc-server/`

**核心组件**：

| 组件 | 路径 | 职责 |
|------|------|------|
| 启动脚本 | `scripts/start.sh` | 容器入口：JDK 检测 → 服务端下载 → 安装 → 启动 |
| JDK 选择 | `scripts/jdk.sh` | 根据 `MC_VERSION` 自动映射 JDK 版本（8/16/17/21） |
| 版本解析 | `scripts/version-resolve.sh` | 根据 `SERVER_TYPE` + `MC_VERSION` 从上游 API 自动获取下载地址 |
| 重启脚本 | `scripts/run.sh` | 重启 screen 中的 MC 服务端 |
| Go API | `api/` | 编译为静态二进制 `mc-api`，容器主进程 |

**JDK 自动映射规则**：

| Minecraft 版本 | JDK |
|---------------|-----|
| 1.7.x ~ 1.16.x | 8 |
| 1.17.x | 16 |
| 1.18.x ~ 1.20.4 | 17 |
| 1.20.5+ | 21 |

**支持的服务端类型**：Vanilla / Forge / Fabric / NeoForge / Paper / Purpur

---

### 第 2 层：Go Agent API

**定位**：运行在 MC 容器内的底层 Agent，提供 REST API 操控 Minecraft 服务端，并作为最后一道安全防线。

**目录**：`mc-server/api/`

**技术特点**：

- **零外部依赖**：Go 标准库 `net/http`，静态编译为单一二进制
- **多阶段构建**：`golang:1.23` 编译 → `debian:bookworm` 运行
- **认证**：首次启动自动生成 UUID v4 API Key，`Bearer Token` 校验
- **安全兜底**：硬编码保护路径列表，`force` 参数控制危险操作

**模块结构**：

```text
api/
├── main.go              入口：路由注册、中间件链、启动逻辑
├── model/
│   └── types.go         统一响应结构体 APIResponse
├── middleware/
│   ├── auth.go          Bearer Token 认证
│   └── cors.go          跨域中间件
├── handler/
│   ├── health.go        健康检查 + 公共 helper
│   ├── mcserver.go      服务端管理（start/stop/restart/status）
│   ├── command.go       向 MC 发送指令
│   ├── logs.go          SSE 实时日志流
│   └── files.go         文件管理（list/read/write/delete/upload/download/export）
└── service/
    ├── screen.go        Screen 会话操作 + MC 启动命令构建
    ├── auth.go          API Key 初始化/校验/刷新
    ├── logs.go          LogReader：追踪日志偏移量、处理轮转
    └── files.go         文件操作 + 目录压缩（zip/tar.gz）
```

**API 路由**：

| 方法 | 路径 | 功能 |
|------|------|------|
| `GET` | `/api/health` | 健康检查（免认证） |
| `GET` | `/api/server/status` | 服务端状态 |
| `POST` | `/api/server/start` | 启动 MC |
| `POST` | `/api/server/stop` | 停止 MC |
| `POST` | `/api/server/restart` | 重启 MC |
| `POST` | `/api/command` | 发送指令 |
| `GET` | `/api/logs` | SSE 日志流 |
| `POST` | `/api/auth/refresh` | 刷新 API Key |
| `GET` | `/api/files/list` | 列出目录 |
| `GET` | `/api/files/read` | 读取文件 |
| `POST` | `/api/files/write` | 写入文件 |
| `DELETE` | `/api/files/delete` | 删除文件（支持 force） |
| `POST` | `/api/files/upload` | 上传文件 |
| `GET` | `/api/files/download` | 下载文件 |
| `POST` | `/api/files/export` | 导出目录（zip/tar.gz） |
| `POST` | `/api/files/mkdir` | 创建目录（P3） |
| `POST` | `/api/files/rename` | 重命名（P3） |

---

### 第 3 层：Spring Boot 业务层

**定位**：统一业务入口，管理多实例、多用户、权限体系，代理转发请求到 Go API。

**目录**：`panel/backend/`

**技术栈**：Spring Boot 3.x + Spring Security + Spring Data JPA + JWT

**模块结构**：

```text
panel/backend/src/main/java/com/mcpanel/panel/
├── PanelApplication.java           启动类
├── common/
│   ├── ApiResponse.java            统一响应包装 {code, msg, data}
│   └── ErrorCode.java              错误码枚举（6 分段，23+ 错误码）
├── config/
│   ├── SecurityConfig.java         Spring Security + JWT 配置
│   └── TryCatchGlobalException.java 全局异常捕捉器
├── exception/
│   └── McPanelException.java       自定义业务异常（携带 ErrorCode）
├── entity/
│   ├── User.java                   用户实体
│   ├── ApiKey.java                 API Key 实体（key_value 加密存储）
│   ├── ServerInstance.java         MC 实例实体
│   ├── UserInstance.java           用户-实例绑定
│   ├── UserPathPermission.java     路径级 ACL（P3）
│   ├── ConfigPropertyPermission.java server.properties 属性权限（P3）
│   ├── ProtectedResource.java      受保护资源（P3）
│   └── OperationLog.java           操作日志（P3）
├── repository/                     Spring Data JPA 数据访问
├── service/                        业务逻辑层
├── controller/
│   ├── AuthController.java         登录 + getMe（含动态菜单）
│   ├── ApiKeyController.java       Key 管理 REST API
│   ├── InstanceController.java     实例管理 REST API
│   ├── ServerController.java       服务端控制代理（转发 Go API）
│   ├── FilesController.java        文件管理代理（P3）
│   ├── PlayerController.java       玩家管理（P3）
│   ├── PermissionController.java   权限管理（P3）
│   └── LogController.java          操作日志（P3）
└── agent/
    ├── AgentClient.java            Go API HTTP 客户端
    └── AgentClientConfig.java      客户端配置（连接池/超时/重试）
```

**核心设计决策**：

| 决策 | 说明 |
|------|------|
| 不管理容器生命周期 | Docker 容器由使用者自行创建/启动/停止 |
| Key 独立建表 | `api_keys` 与 `server_instances` 分离，Key 生命周期独立于实例 |
| AES-256-GCM 静态加密 | `api_keys.key_value` 加密存储，密钥通过环境变量 `DB_ENCRYPT_KEY` 注入 |
| HTTP 层始终 200 | 业务成功/失败由 `body.code` 区分，避免前端同时处理 HTTP 状态码和业务码 |
| code 与 msg 强绑定 | ErrorCode 枚举确保同一 code 不会对应不同 msg |

---

### 第 4 层：Vue 3 前端

**定位**：Web 管理面板的用户界面，SPA 单页应用，同容器部署在 Spring Boot 中。

**目录**：`panel/web/`

**技术栈**：Vue 3 (Composition API) + Vite + TypeScript + Naive UI

**模块结构**：

```text
panel/web/src/
├── main.ts                          入口
├── App.vue                          根组件
├── router/
│   └── index.ts                     路由配置 + 导航守卫
├── api/
│   ├── types.ts                     ApiResponse 类型 + ErrorCode 枚举
│   ├── client.ts                    axios 实例 + 请求/响应拦截器
│   ├── auth.ts                      登录 API
│   ├── keys.ts                      Key 管理 API
│   └── instances.ts                 实例管理 API
├── views/
│   ├── LoginView.vue                登录页
│   ├── DashboardView.vue            总览（按角色差异化）
│   ├── keys/                        Key 列表 + 注册
│   └── instances/                   实例列表 + 详情（控制台/文件/玩家）
├── components/
│   ├── AppLayout.vue                布局（动态侧边栏 + 顶栏 + 内容区）
│   ├── ConfirmDialog.vue            二次确认弹窗
│   └── StatusBadge.vue              实例状态标签
└── utils/
    └── format.ts                    日期、Key 脱敏等工具函数
```

**动态路由**：侧边栏根据用户 `role` 动态渲染 —— ROOT 见全部功能，ADMIN 见服务器管理 + 仪表盘，USER 仅见仪表盘。

---

## 数据流

### 请求流（以启动 MC 服务端为例）

```text
浏览器                                Vue SPA
  │ 点击「启动」按钮                     │
  │── POST /api/server/{id}/start ──→ Spring Boot ServerController
  │                                      │
  │                              @RequireInstanceAccess AOP
  │                              ├─ 用户 role 检查
  │                              ├─ 用户-实例绑定检查
  │                              └─ AgentClient.startServer(instance)
  │                                      │
  │                                      │ 从 DB 取出实例绑定的 Key
  │                                      │ 构造 Authorization: Bearer <key>
  │                                      │
  │                              ┌───────┘
  │                              │ HTTP POST /api/server/start
  │                              ▼
  │                          Go API (:25560)
  │                              │
  │                              ├─ Auth 中间件校验 Key
  │                              ├─ 检查 screen 会话状态
  │                              ├─ screen -S mcserver -X stuff "..." 发送启动命令
  │                              └─ 返回 { status: "ok" }
  │                                      │
  │  ←── { code: 200, msg: "ok" } ──────┘
  ▼
显示「启动成功」
```

### 日志流（SSE 实时推送）

```text
浏览器                                Vue SPC
  │ 进入实例详情页                       │
  │── GET /api/server/{id}/logs ────→ Spring Boot ServerController
  │                                      │
  │                              AgentClient 代理
  │                              GET /api/logs?tail=100
  │                                      │
  │                                      ▼
  │                          Go API LogsHandler
  │                              │
  │                              ├─ LogReader 200ms 轮询 logs/latest.log
  │                              ├─ SSE 格式输出 (text/event-stream)
  │                              ├─ 15s 心跳保活
  │                              └─ 检测日志轮转自动重新打开
  │                                      │
  │  ←── SSE stream ────────────────────┘
  │  data: [12:30:45] Steve joined the game
  │  data: [12:31:02] <Steve> Hello!
  ▼
实时渲染日志行
```

---

## 部署架构

### 容器拓扑

```text
┌─────────────────────────────────────────────────────────────────┐
│  Docker Host                                                     │
│                                                                  │
│  ┌───────────────────────────┐  ┌────────────────────────────┐  │
│  │  panel (面板容器)          │  │  mc-forge (MC 实例容器)     │  │
│  │                           │  │                            │  │
│  │  Spring Boot :8080        │  │  Go API :25560             │  │
│  │  ├── /api/*  REST API     │  │  ├── Screen → MC Server    │  │
│  │  └── /*     Vue 静态资源   │  │  └── /minecraft/ 数据卷    │  │
│  │                           │  │                            │  │
│  │  H2 数据库（内嵌）         │  │  端口映射:                  │  │
│  │  或 MySQL（生产）          │  │  25560:25560 (API，不暴露) │  │
│  └───────────────────────────┘  │  25565:25565 (MC 游戏端口) │  │
│                                  └────────────────────────────┘  │
│  ┌────────────────────────────┐                                  │
│  │  mc-fabric (另一个实例)     │  ┌────────────────────────────┐  │
│  │  Go API :25561             │  │  mc-vanilla (另一个实例)    │  │
│  │  端口映射: 25565→25566     │  │  Go API :25562             │  │
│  └────────────────────────────┘  │  端口映射: 25565→25567     │  │
│                                  └────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

### 开发环境

- Go API：`localhost:25560`，直接运行 `go run .`
- Spring Boot：`localhost:8080`，`mvn spring-boot:run`
- Vue 开发服务器：`localhost:5173`，`npm run dev`，Vite 代理 `/api` → `localhost:8080`

### 生产部署

- **面板容器**：`docker run -p 8080:8080 -e DB_ENCRYPT_KEY=... shimu/minecraft-panel`
- **MC 容器**：`docker run -p 25565:25565 -v ./minecraft:/minecraft -e SERVER_TYPE=forge -e MC_VERSION=1.12.2 -e EULA=true shimu778/minecraft-server`
- 面板容器通过 Docker 内部网络访问 MC 容器（容器名作为 host）

---

## 安全架构

### 三层权限模型（P3）

```text
┌──────────────────────────────────────────────────┐
│  第一层：角色边界 (users.role)                    │
│  ROOT → 全局通行                                  │
│  ADMIN → 仅绑定实例，操作受 ACL 约束               │
│  USER → 只读观察                                  │
├──────────────────────────────────────────────────┤
│  第二层：路径级 ACL (user_path_permissions)        │
│  决定 ADMIN 在特定实例的特定路径能否管理            │
│  无 ACL 记录 = 路径完全不可见                       │
├──────────────────────────────────────────────────┤
│  第三层：资源保护兜底 (protected_resources)         │
│  世界存档 / 核心配置等不可恢复资源                  │
│  WORLD 类资源仅 ROOT + force 可删                 │
│  Go API 硬编码保护列表作为最后一道防线              │
└──────────────────────────────────────────────────┘
```

### 双重安全校验

```text
Spring Boot（业务权限控制）
    ├─ JWT 认证
    ├─ 角色校验（ROOT/ADMIN/USER）
    ├─ 实例绑定检查（@RequireInstanceAccess）
    ├─ 路径级 ACL 检查
    └─ 受保护资源检查
         │
         ▼
Go API（底层安全兜底）
    ├─ Bearer Token 单 Key 比对
    ├─ IsProtected(path) 硬编码检查
    └─ force 参数强制确认
```

即使 Spring Boot 层权限逻辑出现异常，Go API 的硬编码保护列表仍能阻止核心资源被误删。

---

## 数据库设计总览

### P2 基础表（4 张）

| 表 | 用途 |
|----|------|
| `users` | 用户表（username / password_hash / role） |
| `api_keys` | API Key 注册表（key_value 加密存储） |
| `server_instances` | MC 实例注册表（绑定 api_key_id） |
| `user_instances` | 用户-实例多对多绑定表 |

### P3 增量表（4 张）

| 表 | 用途 |
|----|------|
| `user_path_permissions` | 路径级 ACL（用户×实例×路径） |
| `config_property_permissions` | server.properties 属性编辑权限 |
| `protected_resources` | 受保护资源注册表 |
| `operation_logs` | 操作审计日志 |

### 表关系

```text
users (1) ──── (N) user_instances (N) ──── (1) server_instances (1) ──── (0..1) api_keys

user_path_permissions ──── user_id → users
                      ──── instance_id → server_instances

protected_resources ──── instance_id → server_instances

operation_logs ──── user_id → users
               ──── instance_id → server_instances
```

---

## 通信协议

| 通信方 | 协议 | 认证方式 |
|--------|------|---------|
| Browser → Spring Boot | HTTP/REST | JWT Bearer Token |
| Spring Boot → Go API | HTTP/REST | Bearer API Key（从 DB 取出） |
| Browser → Go API（SSE 日志） | SSE (Server-Sent Events) | 表单 key 参数或 Bearer |
| Go API → Minecraft | Screen IPC | `screen -X stuff` 发送控制台指令 |

---

## 技术栈总览

| 层级 | 技术 | 版本/说明 |
|------|------|----------|
| **容器运行时** | Docker | debian:bookworm 基础镜像 |
| **MC 进程管理** | GNU Screen | 会话持久化，`screen -X stuff` 通信 |
| **Agent API** | Go | 1.23，零外部依赖，标准库 `net/http` |
| **业务后端** | Spring Boot | 3.x + Spring Security + JWT + JPA |
| **数据库** | H2 / MySQL | 开发用 H2（内嵌），生产用 MySQL |
| **静态加密** | AES-256-GCM | JPA AttributeConverter，密钥环境变量注入 |
| **前端框架** | Vue 3 | Composition API + TypeScript |
| **构建工具** | Vite | 开发热更新 + 生产构建 |
| **UI 组件库** | Naive UI | Tree-shaking + TypeScript 原生 |
| **HTTP 客户端** | axios | 拦截器统一 JWT 注入 + 响应解包 |
| **容器通信** | Docker 内部网络 | 容器名 DNS 解析 |
| **CI/CD** | GitHub Actions | tag 推送自动构建 Docker 镜像 |

---

## 项目目录结构

```text
minecraft-server-docker/
├── mc-server/                        # Minecraft 服务端容器
│   ├── Dockerfile                    # 多阶段构建（Go 编译 → Debian 运行）
│   ├── scripts/
│   │   ├── start.sh                  # 容器入口脚本
│   │   ├── run.sh                    # 重启 MC 服务端
│   │   ├── jdk.sh                    # JDK 版本自动选择
│   │   └── version-resolve.sh        # 服务端下载地址自动解析
│   ├── api/                          # Go HTTP API
│   │   ├── main.go                   # 入口
│   │   ├── go.mod
│   │   ├── handler/                  # HTTP 处理器
│   │   ├── service/                  # 业务逻辑
│   │   ├── middleware/               # 中间件（Auth + CORS）
│   │   └── model/                    # 数据模型
│   ├── api-doc/
│   │   └── api.md                    # Go API 接口文档
│   └── test/                         # Vue 测试页面（API 调试用）
│
├── panel/                            # Web 管理面板
│   ├── backend/                      # Spring Boot 后端
│   │   ├── pom.xml
│   │   └── src/main/java/com/mcpanel/panel/
│   │       ├── common/               # 通用类（ApiResponse + ErrorCode）
│   │       ├── config/               # 安全配置 + 全局异常处理
│   │       ├── entity/               # JPA 实体
│   │       ├── repository/           # 数据访问层
│   │       ├── service/              # 业务逻辑层
│   │       ├── controller/           # REST API 控制器
│   │       ├── agent/                # Go API HTTP 客户端
│   │       └── exception/            # 自定义异常
│   └── web/                          # Vue 3 前端
│       ├── package.json
│       ├── vite.config.ts
│       └── src/
│           ├── api/                  # axios 客户端 + API 模块
│           ├── views/                # 页面组件
│           ├── components/           # 公共组件
│           ├── router/               # 路由配置
│           └── utils/                # 工具函数
│
├── data/                             # 开发用持久化数据
├── docs/                             # 项目文档
│   ├── README.md                     # 文档索引
│   ├── architecture.md               # 架构文档（本文件）
│   ├── requirements/                 # 需求文档
│   │   ├── planning-v1.md            # 总体规划
│   │   └── panel-design.md           # 权限与安全设计
│   ├── development/                  # 开发文档
│   │   ├── develop-process.md        # Go API 开发过程
│   │   ├── springboot-infra-plan.md  # Spring Boot 基础设施方案
│   │   ├── frontend-plan.md          # Vue 3 前端技术方案
│   │   └── p3-plan.md                # P3 业务逻辑深化方案
│   └── standards/                    # 开发规范文档
│
├── docker-compose.yaml               # Docker Compose 编排示例
├── README.md                         # 项目 README
└── CHANGELOG.md                      # 变更日志
```
