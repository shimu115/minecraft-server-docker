# Spring Boot 面板基础设施方案

## 背景

在 P2 阶段，需要初始化 Spring Boot 后端项目作为统一业务层，连接用户与多个 MC 服务器实例。

### 各方职责

```text
使用者                              Spring Boot                    Docker 容器
───────                             ───────────                    ───────────
自行创建 Docker 容器          ←    不管理容器生命周期        →   每个容器 = 1 个 Go API + 1 个 MC Server
从控制台获取 API Key          ←    存储 Key → 实例映射      →   Go API 自行生成 Key（现有行为不改）
在面板中注册 Key + 实例       ←    提供 Key/实例管理界面    →   Go API 只验证自己的那一个 Key
通过面板操作 MC Server        ←    AgentClient 携带 Key 调用 →   Go API 执行操作
```

### 核心原则

- **Go API 不需要任何改动**：现有的 Key 生成 + 文件存储 + 控制台打印机制完全满足需求
- **Spring Boot 不管理 Docker 容器**：容器的创建、启动、停止由使用者自行操作
- **Spring Boot 只做通信和管理**：存储 Key→实例的映射关系，通过 AgentClient 代理调用 Go API

---

## 分层策略

将 P2 的 Spring Boot 工作拆分为两个层次：

```
┌─────────────────────────────────────────┐
│  业务逻辑层（P3/P4 实现）                 │
│  权限模型 / 文件保护 / 操作日志 / 用户管理 │
├─────────────────────────────────────────┤
│  基础设施层（P2 实现）                    │
│  API Key 注册管理 / 实例注册 / Agent 通信  │
├─────────────────────────────────────────┤
│  项目骨架（P2 实现）                      │
│  模块划分 / 数据库 / 配置                 │
└─────────────────────────────────────────┘
```

### 为什么基础设施必须先做

| 理由 | 说明 |
|------|------|
| **Agent 通信是脊柱** | 没有 AgentClient，Spring Boot 无法与任何 Go API 通信，一切业务接口都是空中楼阁 |
| **Key/实例绑定是路由前提** | 多实例场景下，用户请求必须先路由到正确的 Go API，这需要 Key 注册 + 实例注册 |
| **验证链路** | 骨架 + 基础设施打通后，可以从 Spring Boot 一路调到 Go API `/api/health`，整条链路可验证 |
| **减少返工** | 业务权限模型的精确边界可能在接入时调整，过早实现增加返工成本 |

---

## 总体架构

```text
使用者自行创建容器：
  docker run ... shimu/minecraft-server:api-dev
  → Go API 生成 Key 并打印到控制台
  → docker logs <container> 查看 Key

使用者到 Spring Boot 面板：
  1. 注册 API Key（命名 + 粘贴 Key 值）
  2. 注册 MC Server 实例（填写 host/port/类型/版本，绑定已注册的 Key）

Spring Boot 内部：
  AgentClient 从 DB 取出实例对应的 Key → Bearer 方式调用 Go API

Docker 容器（每个实例一个）：
  Go API（维持现有行为，只验证自己的那一个 Key）
  Minecraft Server
```

### Key 生命周期

```text
使用者 docker run 启动容器
       ↓
Go API 首次启动 → 生成 UUID → 写入 api_key.txt → 打印到控制台
       ↓
使用者 docker logs <container> 查看 Key，复制
       ↓
使用者到 Spring Boot 面板 → POST /api/admin/keys（命名 + 粘贴 Key 值）
       ↓
使用者创建实例 → POST /api/admin/instances（绑定已注册的 Key）
       ↓
Spring Boot AgentClient 调用 Go API 时，从 DB 取出对应 Key，Bearer 方式携带
       ↓
Go API 中间件校验 Key（单 Key 比对），通过则执行操作
```

### 关于 api_keys 独立建表的考量

用户的核心诉求是：**在面板中为每个 Go API 生成的 Key 命名，便于识别和管理**。

两种设计方案对比：

| | 方案 A：api_keys 独立表（推荐） | 方案 B：Key 内联在 server_instances |
|---|---|---|
| **结构** | `api_keys` + `server_instances.api_key_id (FK)` | `server_instances.key_name + key_value` |
| **Key 独立注册** | ✅ 可先注册 Key，后绑定实例 | ❌ 必须创建实例时才能录入 Key |
| **Key 列表管理** | ✅ 独立列表，按名称搜索/筛选 | ❌ 必须通过实例间接查看 |
| **实例删除后** | Key 记录保留，可绑定到新实例 | Key 随实例记录一同删除 |
| **Key 轮换** | 更新 FK 指向新 Key 记录即可 | 直接改字段值，无历史记录 |
| **复杂度** | 多一张表 + 一个 join | 简单 |

**推荐方案 A**。理由：

1. 用户工作流天然分两步——先从 docker logs 拿到 Key，再到面板注册。独立建表匹配这个心智模型
2. Key 是独立资源，生命周期与实例解耦。删除一个实例不应丢失 Key 记录（万一还要用）
3. 未来可扩展（Key 过期标记、使用统计、最后活跃时间等）而不影响实例表
4. 多一张表的复杂度在基础设施阶段微不足道，但灵活性收益显著

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
│   │   ├── ApiKey.java                  # API Key 实体
│   │   └── ServerInstance.java          # MC 实例实体
│   │
│   ├── repository/
│   │   ├── ApiKeyRepository.java
│   │   └── ServerInstanceRepository.java
│   │
│   ├── service/
│   │   ├── ApiKeyService.java           # Key 注册/命名/删除
│   │   └── InstanceService.java         # 实例 CRUD + Key 绑定
│   │
│   ├── controller/
│   │   ├── ApiKeyController.java        # Key 管理 REST API
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

### P2 最小表集

#### api_keys（API Key 注册表）

```sql
CREATE TABLE api_keys (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    name            VARCHAR(64)  NOT NULL,             -- 用户命名的标识，如 "1.12.2 Forge 生存服"
    key_value       VARCHAR(64)  NOT NULL,             -- Go API 生成的 Key 值（UUID v4）
    status          VARCHAR(16)  DEFAULT 'active',     -- active / revoked
    created_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX idx_key_value ON api_keys(key_value);
```

#### server_instances（MC 实例表）

```sql
CREATE TABLE server_instances (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    name            VARCHAR(64)  NOT NULL,             -- 实例展示名称
    api_key_id      BIGINT       NOT NULL,             -- FK → api_keys.id
    host            VARCHAR(255) NOT NULL,             -- Go API 地址（容器名或 IP）
    port            INT          NOT NULL DEFAULT 25560, -- Go API 端口
    server_type     VARCHAR(32)  NOT NULL,             -- vanilla/forge/fabric/neoforge/paper/purpur
    mc_version      VARCHAR(16)  NOT NULL,             -- MC 版本号
    rcon_host       VARCHAR(255),                      -- RCON 地址（预留）
    rcon_port       INT,                               -- RCON 端口（预留）
    status          VARCHAR(16)  DEFAULT 'unknown',    -- unknown/running/stopped/error
    created_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (api_key_id) REFERENCES api_keys(id)
);

CREATE UNIQUE INDEX idx_instance_name ON server_instances(name);
```

#### 字段说明

**api_keys：**

| 字段 | 说明 |
|------|------|
| `name` | 用户为 Key 起的名称，如 `"1.12.2 Forge 生存服"` |
| `key_value` | 从 Go API 容器日志中复制的 UUID，唯一 |
| `status` | `active` = 正常使用中；`revoked` = 已废弃 |

**server_instances：**

| 字段 | 说明 |
|------|------|
| `name` | 实例展示名称，如 `"1.12.2 Forge Survival"` |
| `api_key_id` | 外键指向 `api_keys`，一个 Key 只能被一个实例绑定 |
| `host` / `port` | Go API 的网络地址（Docker 网络中容器名即可作 host） |
| `server_type` | 服务端类型，用于后续资源保护初始化 |
| `mc_version` | MC 版本号，用于 JDK 选择 |
| `rcon_host` / `rcon_port` | 预留 RCON 直连通道 |
| `status` | 实例状态（通过 AgentClient 心跳更新） |

#### 表关系

```text
api_keys (1) ──── (0..1) server_instances

一个 Key 最多被一个实例使用（通过业务逻辑约束，不在 DB 层强制）
一个实例必须绑定一个 Key（NOT NULL FK）
```

#### users（用户表，P2 最小实现）

```sql
CREATE TABLE users (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    username        VARCHAR(64)  NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    role            VARCHAR(16)  NOT NULL DEFAULT 'USER',  -- ROOT / ADMIN / USER
    created_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX idx_username ON users(username);
```

#### user_instances（用户-实例绑定表）

```sql
CREATE TABLE user_instances (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id         BIGINT NOT NULL,
    instance_id     BIGINT NOT NULL,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (user_id)    REFERENCES users(id),
    FOREIGN KEY (instance_id) REFERENCES server_instances(id),
    UNIQUE (user_id, instance_id)
);
```

#### 实例级访问控制规则

```text
ROOT  → 不查 user_instances，允许访问所有实例
ADMIN → 查 user_instances，仅允许访问绑定的实例
USER  → 查 user_instances，仅允许访问绑定的实例
```

**示例：**

```text
用户 a（USER）：绑定 mc1
用户 b（ADMIN）：绑定 mc1、mc2
用户 c（ROOT）：不绑定，默认全部

场景：查看日志
  a 请求 /api/server/{mc1_id}/logs  → 查 user_instances → 有绑定 → 允许 ✅
  a 请求 /api/server/{mc2_id}/logs  → 查 user_instances → 无绑定 → 403 ❌
  b 请求 /api/server/{mc2_id}/logs  → 查 user_instances → 有绑定 → 允许 ✅
  c 请求 /api/server/{mc3_id}/logs  → ROOT → 跳过绑定检查 → 允许 ✅
```

#### 实现方式

Spring Security 方法级注解 + AOP 切面：

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireInstanceAccess {
    // 标记需要实例级访问控制的方法
}

@Aspect
public class InstanceAccessAspect {
    // Before @RequireInstanceAccess methods:
    // 1. 从 SecurityContext 获取当前用户
    // 2. 若 role == ROOT → 放行
    // 3. 否则查 user_instances(userId, instanceId) → 有记录放行，无记录 403
}
```

Controller 使用：

```java
@RequireInstanceAccess
@PostMapping("/api/server/{instanceId}/start")
public APIResponse startServer(@PathVariable Long instanceId) { ... }
```

#### 表关系总览

```text
api_keys (1) ──── (0..1) server_instances (1) ──── (N) user_instances (N) ──── (1) users

Root 用户：不在 user_instances 中，代码层面跳过检查，全局通行
Admin/User：必须在 user_instances 中绑定实例后才能操作
```

---

## 统一响应格式

所有 Spring Boot 接口使用统一响应结构：

```json
{
    "code": 200,
    "msg": "ok",
    "data": { ... }
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `code` | int | 业务状态码。`200` = 成功，其他 = 错误（详见错误码表） |
| `msg` | string | 可读消息。成功时为 `"ok"`，错误时为对应错误描述 |
| `data` | T（泛型） | 负载数据。成功时携带业务数据，错误时通常为 `null` |

### 后端实现

```java
public class ApiResponse<T> {
    private int code;
    private String msg;
    private T data;

    private ApiResponse(int code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    // === 成功 ===
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(200, "ok", data);
    }
    public static <T> ApiResponse<T> success() {
        return new ApiResponse<>(200, "ok", null);
    }

    // === 错误 ===
    public static <T> ApiResponse<T> error(ErrorCode ec) {
        return new ApiResponse<>(ec.getCode(), ec.getMsg(), null);
    }
    public static <T> ApiResponse<T> error(ErrorCode ec, T data) {
        return new ApiResponse<>(ec.getCode(), ec.getMsg(), data);
    }
}
```

### 错误码枚举（code 与 msg 强制绑定）

```java
public enum ErrorCode {

    // ==================== 通用错误 1xxx ====================
    SUCCESS(200, "ok"),
    BAD_REQUEST(1000, "请求参数错误"),
    INTERNAL_ERROR(1001, "服务器内部错误"),

    // ==================== 认证错误 2xxx ====================
    UNAUTHORIZED(2000, "未登录或登录已过期"),
    FORBIDDEN(2001, "无权限执行此操作"),
    INVALID_CREDENTIALS(2002, "用户名或密码错误"),

    // ==================== API Key 错误 3xxx ====================
    KEY_NOT_FOUND(3000, "API Key 不存在"),
    KEY_ALREADY_EXISTS(3001, "该 API Key 已注册"),
    KEY_ALREADY_BOUND(3002, "该 API Key 已被其他实例绑定"),
    KEY_INVALID_FORMAT(3003, "API Key 格式无效，需 UUID v4 格式"),
    KEY_REVOKED(3004, "该 API Key 已吊销"),
    KEY_BOUND_CANNOT_DELETE(3005, "该 Key 已绑定实例，请先解绑再删除"),

    // ==================== 实例错误 4xxx ====================
    INSTANCE_NOT_FOUND(4000, "MC 实例不存在"),
    INSTANCE_NAME_EXISTS(4001, "实例名称已存在"),
    INSTANCE_NOT_BOUND(4002, "当前用户未绑定该实例，无法操作"),

    // ==================== 用户错误 5xxx ====================
    USER_NOT_FOUND(5000, "用户不存在"),
    USERNAME_EXISTS(5001, "用户名已存在"),
    USER_NOT_BOUND(5002, "用户未绑定任何实例"),

    // ==================== Agent 通信错误 6xxx ====================
    AGENT_UNREACHABLE(6000, "无法连接到 Go API"),
    AGENT_TIMEOUT(6001, "Go API 请求超时"),
    AGENT_ERROR(6002, "Go API 返回错误"),
    AGENT_REFRESH_FAILED(6003, "刷新 Go API Key 失败"),
    ;

    private final int code;
    private final String msg;

    ErrorCode(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    public int getCode() { return code; }
    public String getMsg() { return msg; }
}
```

### 设计原则

| 原则 | 说明 |
|------|------|
| **code 与 msg 强绑定** | 每个错误码在 enum 中唯一定义，杜绝同一 code 对应不同 msg |
| **分段管理** | `1xxx` 通用、`2xxx` 认证、`3xxx` Key、`4xxx` 实例、`5xxx` 用户、`6xxx` Agent——看 code 前缀就知道哪个模块出问题 |
| **扩展方式** | 新增错误场景只需在对应分段加一个枚举值，不影响已有 code |
| **国际化预留** | `msg` 目前硬编码中文，后续可改为 `msgKey` + i18n 资源文件 |
| **与 Go API 格式的关系** | Go API 使用 `code + status + message + data`，Spring Boot 使用 `code + msg + data`——两者格式不同但互不干扰，Spring Boot 是前端唯一入口，前端只看到 Spring Boot 的格式 |

## 数据库静态加密

### 背景

`api_keys.key_value` 存储的是 Go API 的访问凭证。若服务器被入侵、数据库文件被窃取，攻击者可直接使用明文 Key 调用 Go API 控制所有 MC 服务器。

即使 Go API 仅内网监听、有防火墙保护，内网横向移动仍是常见攻击路径。需要在数据库层面做静态加密。

### 方案：AES-256-GCM 应用层加密

加密密钥通过环境变量注入，不落盘：

```
环境变量 DB_ENCRYPT_KEY → Spring Boot 加载 → AES-256-GCM

写入：明文 UUID → AES-GCM 加密 → Base64 密文 → 写入 key_value 列
读取：密文 ← AES-GCM 解密 → 明文 UUID → AgentClient 使用
```

### 为什么不依赖数据库 TDE

MySQL TDE 需要企业版，H2 不支持。应用层加密不绑定数据库，迁移无缝。

### 实现：JPA AttributeConverter

```java
@Converter
public class KeyValueEncryptor implements AttributeConverter<String, String> {

    private final SecretKey key;

    public KeyValueEncryptor(@Value("${app.db-encrypt-key}") String base64Key) {
        byte[] decoded = Base64.getDecoder().decode(base64Key);
        this.key = new SecretKeySpec(decoded, "AES");
    }

    @Override
    public String convertToDatabaseColumn(String plain) {
        if (plain == null) return null;
        return AES256GCM.encrypt(plain, key);  // → Base64 密文
    }

    @Override
    public String convertToEntityAttribute(String cipher) {
        if (cipher == null) return null;
        return AES256GCM.decrypt(cipher, key);  // → 明文 UUID
    }
}
```

实体上只需一行：

```java
@Column(name = "key_value", nullable = false)
@Convert(converter = KeyValueEncryptor.class)
private String keyValue;
```

### 加密密钥管理

| 环境 | 注入方式 |
|------|----------|
| 开发 | `application.yml` 中的 `app.db-encrypt-key`（或 `.env`） |
| 生产 | 环境变量 `DB_ENCRYPT_KEY`（Docker 启动时注入，不写进任何配置文件） |

**P2 即实施。** 成本极低（一个 Converter），越晚加密越需要写数据迁移脚本。

---

## API 设计

### 一、API Key 管理

Key 管理独立于实例管理——用户先拿到 Go API 生成的 Key，在面板中注册命名，之后再创建实例时绑定。

#### POST /api/admin/keys

注册一个新的 API Key（用户粘贴从 Go API 控制台获取的 Key 并命名）。

**请求：**

```json
{
    "name": "1.12.2 Forge 生存服",
    "key_value": "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
}
```

**响应（201）：**

```json
{
    "code": 200,
    "msg": "ok",
    "data": {
        "id": 1,
        "name": "1.12.2 Forge 生存服",
        "status": "active",
        "created_at": "2026-06-19T12:00:00Z"
    }
}
```

> **安全设计：** `key_value` 是只写字段。创建时必须传入，但任何 API 响应均不返回完整值。
> 列表/详情接口仅返回脱敏预览 `key_preview`（如 `a1b2****...****7890`），方便用户识别是哪个 Key。

**校验规则：**

- `key_value` 格式校验（UUID v4 格式）
- `key_value` 唯一性校验（不允许重复注册同一个 Key）

---

#### GET /api/admin/keys

列出所有已注册的 API Key 及其绑定状态。

**响应（200）：**

```json
{
    "code": 200,
    "msg": "ok",
    "data": [
        {
            "id": 1,
            "name": "1.12.2 Forge 生存服",
            "key_preview": "a1b2****...****7890",
            "status": "active",
            "bound_instance": {
                "id": 1,
                "name": "1.12.2 Forge Survival"
            },
            "created_at": "2026-06-19T12:00:00Z"
        },
        {
            "id": 2,
            "name": "1.20.1 Fabric 创造服",
            "key_preview": "b2c3****...****e5f6",
            "status": "active",
            "bound_instance": null,
            "created_at": "2026-06-19T13:00:00Z"
        }
    ]
}
```

`bound_instance` 为 null 表示该 Key 尚未绑定到任何实例。

---

#### GET /api/admin/keys/{id}

获取单个 Key 详情。与列表接口一致，仅返回 `key_preview` 脱敏值，不返回完整 `key_value`。

**`key_preview` 格式：** 前4位 + `****...****` + 后4位，帮助用户识别 Key 而无需暴露完整值。

---

#### DELETE /api/admin/keys/{id}

删除 Key 记录。

**约束：** 如果该 Key 已被实例绑定，拒绝删除并提示先解绑。

---

#### POST /api/admin/keys/{id}/revoke

标记 Key 为 `revoked` 状态（软删除，保留历史记录）。

---

### 二、MC Server 实例管理

#### POST /api/admin/instances

注册一个 MC Server 实例并绑定 API Key。

**请求：**

```json
{
    "name": "1.12.2 Forge Survival",
    "api_key_id": 1,
    "host": "mc-forge-1.12.2",
    "port": 25560,
    "server_type": "forge",
    "mc_version": "1.12.2"
}
```

**响应（200）：**

```json
{
    "code": 200,
    "msg": "ok",
    "data": {
        "id": 1,
        "name": "1.12.2 Forge Survival",
        "host": "mc-forge-1.12.2",
        "port": 25560,
        "server_type": "forge",
        "mc_version": "1.12.2",
        "api_key": {
            "id": 1,
            "name": "1.12.2 Forge 生存服"
        },
        "status": "unknown",
        "created_at": "2026-06-19T12:00:00Z"
    }
}
```

---

#### GET /api/admin/instances

列出所有已注册的 MC 实例。

---

#### GET /api/admin/instances/{id}

获取单个实例详情。

---

#### PUT /api/admin/instances/{id}

更新实例信息（name、host、port、server_type、mc_version）。`api_key_id` 不可通过此接口修改，需使用专门的 Key 绑定接口。

---

#### DELETE /api/admin/instances/{id}

删除实例记录。对应的 API Key 自动解除绑定（Key 记录保留，`status` 不变）。

---

#### PUT /api/admin/instances/{id}/bind-key

更换实例绑定的 API Key。

**请求：**

```json
{
    "api_key_id": 2
}
```

**逻辑：**

1. 校验新 Key 存在且为 `active` 状态
2. 旧 Key 解除绑定（仍保持 `active`，可被其他实例使用）
3. 新 Key 绑定到该实例
4. 后续 AgentClient 调用自动使用新 Key

---

#### PUT /api/admin/instances/{id}/refresh-key

刷新实例的 API Key——调用 Go API 的 `POST /api/auth/refresh` 获取新 Key，原子性地完成旧 Key 吊销 + 新 Key 注册 + 重新绑定。

**权限：** 仅 Root 角色（涉及凭证替换，前端需有二次确认弹窗）

**请求体：** 无（全自动，Spring Boot 代理调用 Go API 的 refresh 接口）

**服务端逻辑：**

1. 校验当前用户为 Root 角色
2. 查找 instance → 获取当前 `api_key`
3. AgentClient 携带当前 Key 调用 Go API `POST /api/auth/refresh`
4. Go API 生成新 Key 并返回（旧 Key 在 Go API 侧立即失效，见 [[api-doc]] 第 281 行）
5. 事务内：
   - 旧 `api_keys` 记录 → `status = revoked`
   - 创建新 `api_keys` 记录（`name` 沿用旧名，`key_value` 为新 Key，加密存储）
   - 更新 `server_instances.api_key_id` → 指向新 Key
6. 事务提交

**Go API 已有此能力：**

```
POST /api/auth/refresh
→ 旧 Key 立即失效
→ 返回 { "data": { "api_key": "新 UUID" } }
```

Spring Boot 无需额外扩展 Go API，直接调用即可。

**响应（200）：**

```json
{
    "code": 200,
    "msg": "API Key 已刷新，旧 Key 已吊销",
    "data": {
        "instance_id": 1,
        "previous_key": {
            "id": 1,
            "key_preview": "a1b2****...****7890",
            "status": "revoked"
        },
        "new_key": {
            "id": 3,
            "key_preview": "f9e8****...****3210",
            "status": "active"
        }
    }
}
```

**失败场景：**

| 场景 | 错误码 | 响应 |
|------|--------|------|
| 非 Root 用户 | `FORBIDDEN` (2001) | `{"code":2001,"msg":"无权限执行此操作","data":null}` |
| 实例不存在 | `INSTANCE_NOT_FOUND` (4000) | `{"code":4000,"msg":"MC 实例不存在","data":null}` |
| Go API 不可达 | `AGENT_UNREACHABLE` (6000) | `{"code":6000,"msg":"无法连接到 Go API","data":null}` |
| 旧 Key 已在 Go API 侧失效 | `AGENT_REFRESH_FAILED` (6003) | `{"code":6003,"msg":"刷新 Go API Key 失败","data":null}` |

**与 `bind-key` 的区别：**

| | `PUT .../bind-key` | `PUT .../refresh-key` |
|---|---|---|
| 语义 | 绑定一个已有 Key | **换一个新 Key 并吊销旧的** |
| 权限 | Admin 可用 | **仅 Root** |
| 新 Key 来源 | 用户提前在 `/api/admin/keys` 注册好的 | **Go API 现场生成** |
| 旧 Key 处理 | 不解绑（仍 active） | **标记 revoked** |
| 请求体 | `{"api_key_id": 2}` | 无（全自动） |
| 与 Go API 交互 | 无（仅 DB 操作） | AgentClient 调 `POST /api/auth/refresh` |

---

### 三、健康探测

#### GET /api/admin/instances/{id}/health

通过 AgentClient 代理调用 Go API 的 `/api/health`，验证通信链路。

**响应（200）：**

```json
{
    "code": 200,
    "msg": "ok",
    "data": {
        "instance_id": 1,
        "instance_name": "1.12.2 Forge Survival",
        "go_api_health": "ok"
    }
}
```

**失败时（Go API 不可达）：**

```json
{
    "code": 6000,
    "msg": "无法连接到 Go API",
    "data": null
}
```

---

## AgentClient 设计

### 职责

AgentClient 是 Spring Boot 与 Go API 之间的唯一通信通道。

### 调用方式

```java
// AgentClient 根据 ServerInstance 自动解析连接信息 + Key
AgentClient.health(instance);
// → Join api_keys → 获取 key_value
// → 从 ServerInstance 获取 host:port
// → 发起 GET http://host:port/api/health
// → Header: Authorization: Bearer <key_value>
```

### 核心方法（P2 最小集）

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

    // API Key 管理（调用 Go API 刷新 Key）
    public RefreshKeyResponse refreshKey(ServerInstance instance);

    // 文件管理（P3/P4 扩展）
    // public List<FileInfo> listFiles(ServerInstance instance, String path);
    // public String readFile(ServerInstance instance, String path);
    // ...
}
```

### 设计要点

| 要点 | 说明 |
|------|------|
| **自动附加 Bearer token** | 通过 `instance.api_key.key_value` 获取 Key，注入 `Authorization: Bearer <key>` |
| **连接池** | 使用 `HttpClient` 连接池，按 host 复用连接 |
| **超时与重试** | 连接超时 5s，读超时 30s，可重试的 5xx 错误最多重试 2 次 |
| **错误映射** | Go API 的 403/404/500 映射为对应的 Spring Boot 异常 |
| **状态同步** | 每次调用后更新 `server_instances.status`（异步，可选） |

---

## Go API 侧

### 不需要任何改动

Go API 现有的 Key 管理机制完全满足需求：

```text
首次启动 → 生成 UUID → 写入 api_key.txt → 打印到控制台
后续启动 → 从 api_key.txt 读取
请求校验 → 单 Key 字符串比对
```

使用者通过 `docker logs` 获取 Key 后到 Spring Boot 面板注册即可。

---

## P2 交付清单

### Spring Boot 侧

- [ ] `pom.xml`：Spring Boot 3.x + Spring Web + Spring Data JPA + H2/MySQL + Spring Security + Spring AOP
- [ ] `PanelApplication.java`：启动类
- [ ] `SecurityConfig.java`：Spring Security + JWT 认证 + 角色体系（ROOT/ADMIN/USER）
- [ ] `JwtUtil.java`：JWT 签发/校验工具
- [ ] `KeyValueEncryptor.java`：AES-256-GCM `AttributeConverter`（DB 静态加密）
- [ ] `User.java` / `ApiKey.java` / `ServerInstance.java` / `UserInstance.java`：实体
- [ ] `UserRepository.java` / `ApiKeyRepository.java` / `ServerInstanceRepository.java` / `UserInstanceRepository.java`：数据访问
- [ ] `UserService.java`：用户 CRUD + 实例绑定/解绑
- [ ] `ApiKeyService.java`：Key 注册/命名/查询/删除/吊销
- [ ] `InstanceService.java`：实例 CRUD + Key 绑定/换绑 + 刷新
- [ ] `AuthController.java`：登录接口（`POST /api/auth/login`）
- [ ] `ApiKeyController.java`：Key 管理 REST API
- [ ] `InstanceController.java`：实例管理 REST API + refresh-key 端点
- [ ] `ServerController.java`：服务端控制代理接口（start/stop/restart/status/command/logs）
- [ ] `RequireInstanceAccess.java` + `InstanceAccessAspect.java`：实例级访问控制注解 + 切面
- [ ] `AgentClient.java`：Go API HTTP 客户端（含 `refreshKey()` 方法）
- [ ] `application.yml`：配置（含 `app.db-encrypt-key` + `app.jwt-secret`）

### Go API 侧

- [ ] **无需改动**（Key 生成 + 刷新接口 `POST /api/auth/refresh` 均已存在，见 [[api-doc]]）

### 验证标准

1. Spring Boot 启动成功，数据库自动建表（`users` + `api_keys` + `server_instances` + `user_instances`）
2. `POST /api/auth/login` 登录成功，返回 JWT
3. `POST /api/admin/keys` 注册 Key 成功，DB 中 `key_value` 为密文存储
4. `POST /api/admin/instances` 创建实例并绑定 Key 成功
5. `GET /api/admin/instances/{id}/health` 通过 AgentClient 调通 Go API 的 `/api/health`
6. `PUT /api/admin/instances/{id}/refresh-key` 调 Go API `POST /api/auth/refresh` → 新 Key 注册 → 旧 Key 吊销
7. 绑定实例到用户 → 用户可访问该实例的接口；未绑定 → 返回 403
8. Root 用户不受绑定限制，可访问所有实例
9. Key 换绑后，新 Key 立即生效

---

## 有意延后到 P3+ 的内容

| 内容 | 延后原因 |
|------|----------|
| `roles` / `user_role` RBAC 表 | P2 使用 user.role 字段区分角色，够用；细粒度 RBAC 延后 |
| `protected_resource` 表 | 需要在 Agent 链路通后，结合实际文件系统设计初始化策略 |
| `operation_logs` 表 | 依赖具体业务操作定义 |
| 文件管理代理接口 | 需要权限模型先定型 |
| 玩家管理接口（kick/whitelist/op） | 纯业务功能 |
| Docker SDK 接入 | Spring Boot 不管理容器生命周期 |

---

## 参考文档

- [[planning-v1]]：总体规划文档
- [[panel-design]]：权限与安全设计（业务层参考）
