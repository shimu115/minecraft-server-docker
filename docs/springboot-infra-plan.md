# Spring Boot 面板基础设施方案

## 背景

在 P2 阶段，需要初始化 Spring Boot 后端项目作为统一业务层。当前 Go API 采用单实例单 key 模型（文件存储 `api_key.txt`），无法支持多 MC 服务器实例场景。

Spring Boot 层的引入需要解决一个核心问题：**业务端如何知道自己应该用哪个 API Key 调用哪个 MC 服务器**。

---

## 分层策略

将 P2 的 Spring Boot 工作拆分为两个层次：

```
┌─────────────────────────────────────────┐
│  业务逻辑层（P3/P4 实现）                 │
│  权限模型 / 文件保护 / 操作日志 / 用户管理 │
├─────────────────────────────────────────┤
│  基础设施层（P2 实现）                    │
│  实例注册 / API Key 绑定 / Agent 通信     │
├─────────────────────────────────────────┤
│  项目骨架（P2 实现）                      │
│  模块划分 / 数据库 / 配置                 │
└─────────────────────────────────────────┘
```

### 为什么基础设施必须先做

| 理由 | 说明 |
|------|------|
| **Agent 通信是脊柱** | 没有 AgentClient，Spring Boot 无法与任何 Go API 通信，一切业务接口都是空中楼阁 |
| **实例/Key 绑定是路由前提** | 多实例场景下，用户请求必须先路由到正确的 Go API，这需要实例注册表 |
| **验证链路** | 骨架 + 基础设施打通后，可以从 Spring Boot 一路调到 Go API `/api/health`，整条链路可验证 |
| **减少返工** | 业务权限模型的精确边界可能在接入时调整，过早实现增加返工成本 |

---

## 总体架构

```text
用户
 ↓
Vue Panel（P3）
 ↓
Spring Boot ─── 业务权限控制（P3/P4）
 ├─ InstanceService  ←── 实例/Key 管理（P2）
 ├─ AgentClient      ←── Go API 通信（P2）
 ↓
Go API（多实例，每个实例持有独立 API Key）
 ↓
Minecraft Server
```

### 请求流

```text
用户请求操作 MC 服务器
       ↓
Spring Boot Controller（鉴权）
       ↓
InstanceService（根据实例 ID 获取连接信息 + API Key）
       ↓
AgentClient（携带 API Key 发起 HTTP 请求）
       ↓
Go API（验证 API Key → 执行操作）
       ↓
Minecraft Server
```

---

## 项目结构

```
panel/backend/
├── pom.xml
├── src/main/java/com/mcpanel/panel/
│   ├── PanelApplication.java
│   │
│   ├── config/
│   │   └── SecurityConfig.java          # 基础安全配置
│   │
│   ├── entity/
│   │   └── ServerInstance.java          # MC 实例实体
│   │
│   ├── repository/
│   │   └── ServerInstanceRepository.java
│   │
│   ├── service/
│   │   └── InstanceService.java         # 实例 CRUD + Key 管理
│   │
│   ├── controller/
│   │   └── InstanceController.java      # 实例管理 REST API
│   │
│   └── agent/
│       ├── AgentClient.java             # Go API HTTP 客户端
│       └── AgentClientConfig.java       # 客户端配置
│
└── src/main/resources/
    └── application.yml
```

---

## 数据库设计

### P2 最小表集（仅基础设施）

#### server_instances（实例表）

```sql
CREATE TABLE server_instances (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    name            VARCHAR(64)  NOT NULL,             -- 实例名称
    server_type     VARCHAR(32)  NOT NULL,             -- vanilla/forge/fabric/neoforge/paper/purpur
    mc_version      VARCHAR(16)  NOT NULL,             -- MC 版本号
    host            VARCHAR(255) NOT NULL,             -- Go API 地址
    port            INT          NOT NULL DEFAULT 25560, -- Go API 端口
    api_key         VARCHAR(64)  NOT NULL,             -- 与该实例通信的 API Key
    rcon_host       VARCHAR(255),                      -- RCON 地址（可选）
    rcon_port       INT,                               -- RCON 端口（可选）
    status          VARCHAR(16)  DEFAULT 'stopped',    -- running/stopped/error
    created_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX idx_instance_name ON server_instances(name);
```

#### 字段说明

| 字段 | 说明 |
|------|------|
| `name` | 实例名称，如 `"1.12.2-forge-2"`，唯一 |
| `server_type` | 服务端类型，用于后续资源保护初始化 |
| `mc_version` | MC 版本号，用于 JDK 选择和版本解析 |
| `host` / `port` | Go API 的网络地址 |
| `api_key` | Spring Boot 调用该实例 Go API 时携带的 Bearer token |
| `rcon_host` / `rcon_port` | 预留 RCON 直连通道 |
| `status` | 实例运行状态 |

---

## API 设计

### 实例管理

#### POST /api/admin/instances

创建 MC 实例并自动生成 API Key。

**请求：**

```json
{
    "name": "1.12.2-forge-2",
    "server_type": "forge",
    "mc_version": "1.12.2",
    "host": "minecraft-server-2",
    "port": 25560
}
```

**响应：**

```json
{
    "code": 201,
    "status": "ok",
    "data": {
        "id": 1,
        "name": "1.12.2-forge-2",
        "server_type": "forge",
        "mc_version": "1.12.2",
        "host": "minecraft-server-2",
        "port": 25560,
        "api_key": "a1b2c3d4-...",
        "status": "stopped",
        "created_at": "2026-06-18T12:00:00Z"
    }
}
```

**逻辑：**

1. 生成 UUID v4 作为 API Key
2. 将实例信息 + Key 写入 `server_instances` 表
3. 调用 Go API `POST /api/auth/register-key` 将 Key 注册到对应实例（待 Go API 扩展该接口）
4. 返回实例信息（含 Key）

---

#### GET /api/admin/instances

列出所有已注册的 MC 实例。

**响应：**

```json
{
    "code": 200,
    "status": "ok",
    "data": [
        {
            "id": 1,
            "name": "1.12.2-forge-2",
            "server_type": "forge",
            "mc_version": "1.12.2",
            "host": "minecraft-server-2",
            "port": 25560,
            "api_key": "a1b2c3d4-...",
            "status": "running",
            "created_at": "2026-06-18T12:00:00Z"
        }
    ]
}
```

---

#### GET /api/admin/instances/{id}

获取单个实例详情。

---

#### PUT /api/admin/instances/{id}

更新实例信息（host、port 等可变更字段，不包含 api_key）。

---

#### DELETE /api/admin/instances/{id}

删除实例记录。同时调用 Go API 撤销该 Key。

---

#### POST /api/admin/instances/{id}/rotate-key

轮换指定实例的 API Key。

**逻辑：**

1. 生成新 Key
2. 更新数据库
3. 调用 Go API 注册新 Key / 撤销旧 Key
4. 返回新 Key

**响应：**

```json
{
    "code": 200,
    "status": "ok",
    "data": {
        "id": 1,
        "api_key": "e5f6g7h8-..."
    }
}
```

---

### 健康探测（验证链路）

#### GET /api/admin/instances/{id}/health

通过 AgentClient 代理调用 Go API 的 `/api/health`，验证整条通信链路。

**响应：**

```json
{
    "code": 200,
    "status": "ok",
    "data": {
        "instance_id": 1,
        "instance_name": "1.12.2-forge-2",
        "go_api_health": "ok",
        "go_api_version": "1.0.0"
    }
}
```

---

## AgentClient 设计

### 职责

AgentClient 是 Spring Boot 与 Go API 之间的唯一通信通道。所有对 MC 服务器的操作都经过它。

### 核心方法

```java
public class AgentClient {

    // 健康检查
    public HealthResponse health(ServerInstance instance);

    // MC 服务端管理
    public APIResponse startServer(ServerInstance instance);
    public APIResponse stopServer(ServerInstance instance);
    public APIResponse restartServer(ServerInstance instance);
    public ServerStatusResponse getStatus(ServerInstance instance);

    // 发送指令
    public APIResponse sendCommand(ServerInstance instance, String command);

    // 文件管理（后续 P3/P4 扩展）
    public List<FileInfo> listFiles(ServerInstance instance, String path);
    public String readFile(ServerInstance instance, String path);
    public APIResponse writeFile(ServerInstance instance, String path, String content);
    public APIResponse deleteFile(ServerInstance instance, String path, boolean force);
    // ...
}
```

### 设计要点

| 要点 | 说明 |
|------|------|
| **自动附加 Bearer token** | 从 `ServerInstance.api_key` 读取，注入 `Authorization: Bearer <key>` |
| **连接池** | 使用 `HttpClient` 连接池，按 instance 复用连接 |
| **超时与重试** | 连接超时 5s，读超时 30s，可重试的 5xx 错误最多重试 2 次 |
| **错误映射** | Go API 的 403/404/500 映射为对应的 Spring Boot 异常 |
| **健康状态同步** | 每次调用后更新 `server_instances.status`（可选，异步） |

---

## Go API 侧需要的配合扩展

Spring Boot 基础设施需要 Go API 侧提供以下支持（在 P2 中同步完成）：

| 扩展项 | 说明 |
|--------|------|
| `POST /api/auth/register-key` | 接收 Spring Boot 下发的 API Key 并写入本地 `api_key.txt` |
| `POST /api/auth/revoke-key` | 撤销指定 Key |
| 多 Key 支持 | 从单 key 模型改为 key 列表模型，支持多个有效 Key |

### 多 Key 模型设计

```go
// 当前: api_key.txt 存单个 key
// api-key=<uuid>

// 改为: api_keys.txt 存多个 key（每行一个）
// <uuid1>
// <uuid2>

func ValidateAPIKey(token string) bool {
    // 遍历所有已注册的 key，任一匹配即通过
}
```

---

## P2 交付清单

### Spring Boot 侧

- [ ] `pom.xml`：Spring Boot 3.x + Spring Web + Spring Data JPA + H2/MySQL + Spring Security
- [ ] `PanelApplication.java`：启动类
- [ ] `SecurityConfig.java`：基础安全配置（Admin API 需要认证）
- [ ] `ServerInstance.java`：实体
- [ ] `ServerInstanceRepository.java`：数据访问
- [ ] `InstanceService.java`：实例 CRUD + Key 生成/轮换
- [ ] `InstanceController.java`：REST 接口
- [ ] `AgentClient.java`：Go API HTTP 客户端
- [ ] `application.yml`：配置

### Go API 侧

- [ ] 多 Key 支持（`api_keys.txt` 替代 `api_key.txt`）
- [ ] `POST /api/auth/register-key` 接口
- [ ] `POST /api/auth/revoke-key` 接口

### 验证标准

1. Spring Boot 启动成功，数据库自动建表
2. `POST /api/admin/instances` 创建实例并返回 API Key
3. `GET /api/admin/instances/{id}/health` 通过 AgentClient 调通 Go API 的 `/api/health`
4. Go API 日志显示来自 Spring Boot 的请求携带了正确的 Bearer token

---

## 有意延后到 P3/P4 的内容

| 内容 | 延后原因 |
|------|----------|
| `users` / `roles` / `user_role` 表 | 属于业务权限模型，非基础设施 |
| `protected_resource` 表 | 需要在 Agent 链路通后，结合实际文件系统设计初始化策略 |
| `operation_logs` 表 | 依赖具体业务操作定义 |
| 文件管理代理接口 | 需要权限模型先定型 |
| 玩家管理接口 | 纯业务功能 |
| Vue Panel | P3 开始 |
| Dashboard / Console | P3 开始 |

---

## 参考文档

- [[planning-v1]]：总体规划文档
- [[panel-design]]：权限与安全设计（业务层参考）
