# 八、Minecraft Runtime 配置简化与自动版本解析

## 背景

当前配置方式：

```yaml
environment:
  JAVA_VERSION: "8"

  SERVER_TYPE: forge

  DOWNLOAD_URL: "https://maven.minecraftforge.net/net/minecraftforge/forge/1.12.2-14.23.5.2864/forge-1.12.2-14.23.5.2864-installer.jar"
```

存在以下问题：

### 用户体验较差

用户需要自行查找：

* Forge 下载链接
* Fabric 下载链接
* NeoForge 下载链接

并手动填写：

```yaml
DOWNLOAD_URL
```

增加了使用门槛。

---

### JDK 版本需要用户维护

用户需要了解：

```text
Minecraft 1.7.x ~ 1.16.x -> Java 8

Minecraft 1.17.x -> Java 16

Minecraft 1.18.x ~ 1.20.4 -> Java 17

Minecraft 1.20.5+ -> Java 21
```

否则容易出现：

```text
Java Version Error
Unsupported Class Version
```

等兼容性问题。

---

### 不利于后续面板开发

未来面板创建服务器时，用户只需要选择：

```text
服务端类型：
Forge

Minecraft版本：
1.12.2
```

即可完成创建。

面板不应该要求用户填写：

```text
下载链接
JDK版本
```

等底层实现细节。

---

# 目标

配置简化为：

```yaml
environment:
  SERVER_TYPE: forge

  MC_VERSION: 1.12.2
```

由 Runtime 自动完成：

```text
解析服务端版本

解析下载地址

解析JDK版本

下载服务端

启动服务端
```

---

# 自动JDK选择（P0）

## 配置方式

允许：

```yaml
JAVA_VERSION: auto
```

或者直接省略：

```yaml
MC_VERSION: 1.12.2
```

---

## 自动映射规则

```text
1.7.x ~ 1.16.x
↓
Java 8

1.17.x
↓
Java 16

1.18.x ~ 1.20.4
↓
Java 17

1.20.5+
↓
Java 21
```

---

## 用户强制指定

高级用户允许覆盖自动检测：

```yaml
JAVA_VERSION: 17
```

优先使用用户配置。

---

## 镜像要求

镜像内预装：

```text
Java 8

Java 17

Java 21
```

启动时动态切换：

```bash
JAVA_HOME=/usr/lib/jvm/java-17
```

无需用户参与。

---

## 优先级

P0（最高优先级）

原因：

* 实现成本低
* 用户收益高
* 能显著降低配置复杂度
* 为后续自动版本解析提供基础能力

---

# 服务端版本自动解析（P1）

## 新配置格式

### Vanilla

```yaml
SERVER_TYPE: vanilla

MC_VERSION: 1.21.1
```

---

### Forge

```yaml
SERVER_TYPE: forge

MC_VERSION: 1.12.2
```

---

### Fabric

```yaml
SERVER_TYPE: fabric

MC_VERSION: 1.20.1
```

---

### NeoForge

```yaml
SERVER_TYPE: neoforge

MC_VERSION: 1.21.1
```

---

# 自动获取服务端版本

## Forge

查询：

```text
https://maven.minecraftforge.net/net/minecraftforge/forge/maven-metadata.xml
```

根据：

```text
MC_VERSION
```

获取对应最新 Forge 版本。

例如：

```text
1.12.2
```

自动解析：

```text
14.23.5.2864
```

并生成：

```text
https://maven.minecraftforge.net/net/minecraftforge/forge/1.12.2-14.23.5.2864/forge-1.12.2-14.23.5.2864-installer.jar
```

---

## Fabric

查询：

```text
Fabric Meta API
```

获取：

```text
Latest Loader
Latest Installer
```

自动生成安装参数。

---

## NeoForge

查询：

```text
NeoForge Maven Metadata
```

获取：

```text
Latest Recommended Version
```

自动生成下载地址。

---

## Vanilla

查询：

```text
Mojang Version Manifest
```

获取：

```text
Server Download URL
```

自动下载服务端。

---

# 实现方式

## 第一阶段

使用：

```text
bash

curl

grep

awk

sed
```

实现。

无需引入额外运行时。

---

## 第二阶段

新增：

```text
agent/bootstrap
```

模块。

职责：

```text
版本解析

下载地址生成

JDK选择

服务端安装
```

由 Go 实现。

---

## 目标架构

```text
start.sh
    ↓

bootstrap
    ↓

获取版本信息
    ↓

选择JDK
    ↓

下载服务端
    ↓

启动Minecraft
```

---

# 长期目标

最终用户配置：

```yaml
environment:

  SERVER_TYPE: forge

  MC_VERSION: 1.12.2
```

即可启动服务端。

无需配置：

```yaml
JAVA_VERSION

DOWNLOAD_URL
```

Runtime 自动完成全部解析逻辑。

---

# 九、Go API 扩展与权限安全体系（P2）

## 背景

随着 Spring Boot 面板的引入，Go API 将不再仅作为 Minecraft 服务端控制接口，而是作为整个系统的底层 Agent。

因此需要建立：

* 完整的权限模型
* 文件安全保护机制
* 重要资源保护机制
* 面板与 Agent 双层安全校验

确保即使上层权限逻辑出现问题，也不会导致核心资源被误删。

---

# 总体架构

采用双层保护机制：

```text
用户
 ↓
Vue Panel
 ↓
Spring Boot
 ↓
Go API
 ↓
Minecraft Server
```

职责划分：

```text
Spring Boot
    ↓
业务权限控制

Go API
    ↓
底层安全保护
```

---

# 用户权限模型

系统定义三种角色：

```text
ROOT
ADMIN
USER
```

---

## ROOT

系统最高权限。

允许：

* 查看所有文件
* 编辑所有文件
* 上传文件
* 删除普通文件
* 删除重要文件
* 删除世界目录
* 修改系统配置

---

## ADMIN

服务器管理员。

允许：

* 查看文件
* 编辑配置文件
* 上传插件和 Mod
* 删除普通文件

禁止：

* 删除重要文件
* 删除世界目录
* 删除核心配置

---

## USER

普通用户。

允许：

* 查看日志
* 查看文件（可选）

禁止：

* 编辑文件
* 上传文件
* 删除文件
* 执行危险操作

---

# 重要资源管理

## 数据库设计

新增保护资源表：

```sql
CREATE TABLE protected_resource (
    id BIGINT PRIMARY KEY,
    path VARCHAR(255) NOT NULL,
    resource_type VARCHAR(50) NOT NULL,
    deletable BOOLEAN DEFAULT FALSE,
    editable BOOLEAN DEFAULT TRUE
);
```

---

## Resource Type

定义资源分类：

```text
WORLD
CONFIG
PLUGIN
MOD
CORE
LOG
OTHER
```

示例：

| Path              | Type   |
| ----------------- | ------ |
| world             | WORLD  |
| world_nether      | WORLD  |
| world_the_end     | WORLD  |
| server.properties | CONFIG |
| eula.txt          | CORE   |
| ops.json          | CORE   |
| whitelist.json    | CORE   |
| plugins           | PLUGIN |
| mods              | MOD    |

---

## 初始化策略

服务端首次启动时，根据服务端类型自动导入保护规则。

### Vanilla

```text
world
world_nether
world_the_end
server.properties
eula.txt
ops.json
whitelist.json
```

### Paper / Purpur

额外增加：

```text
plugins
```

### Forge / Fabric / NeoForge

额外增加：

```text
mods
config
```

未来支持新的服务端类型时，仅需增加初始化模板，无需修改业务代码。

---

# 删除流程设计

## 普通文件删除

用户删除：

```text
logs/latest.log
```

Spring Boot 校验：

```text
资源类型 = LOG
权限 = ADMIN
```

允许删除。

随后调用：

```http
DELETE /api/files/delete
```

执行删除。

---

## 重要文件删除

用户删除：

```text
world
```

Spring Boot 查询：

```text
资源类型 = WORLD
权限 = ADMIN
```

直接拒绝：

```json
{
  "code": 403,
  "message": "无权限删除世界存档"
}
```

---

## ROOT 删除重要资源

ROOT 用户删除：

```text
world
```

系统弹出警告：

```text
⚠ 该目录包含 Minecraft 世界数据

删除后无法恢复。

请输入：

DELETE WORLD

确认删除。
```

确认后调用：

```http
DELETE /api/files/delete
```

请求：

```json
{
  "path": "world",
  "force": true
}
```

---

# Go API 安全保护

Go API 不负责用户权限。

但负责最终安全校验。

维护保护资源列表：

```go
func IsProtected(path string) bool
```

删除逻辑：

```go
if IsProtected(path) && !force {
    return 403
}
```

只有：

```json
{
  "force": true
}
```

时才允许删除。

---

# API 扩展规划

新增接口：

```http
GET  /api/server/players
POST /api/files/mkdir
POST /api/files/rename
POST /api/files/move
POST /api/files/unzip
```

后续扩展：

```http
POST /api/server/kick
POST /api/server/whitelist
POST /api/server/op
```

用于支持玩家管理与权限管理功能。

---

# 十、Spring Boot 面板基础设施初始化（P2）

## 目标

初始化 Spring Boot 后端项目，优先构建基础设施层（实例管理 + API Key 绑定 + Agent 通信），
业务权限逻辑延后到 P3/P4 实现。

详细方案见：[[springboot-infra-plan]]

---

## 分层策略

将 Spring Boot 工作拆分为两个层次：

```text
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

Go API 每个容器内自行生成 Key，不需要改动。但多个容器意味着多个 Key，业务端需要知道"用哪个 Key 调哪个实例"。

基础设施层的三个要素是 Spring Boot 存在的"脊柱"：

| 要素 | 为什么必须先做 |
|------|----------------|
| **API Key 注册管理** | 用户从 docker logs 获取 Key 后，需在面板中命名注册，否则无法区分多个 Key |
| **实例注册** | 多实例场景下，业务端需要知道有哪些 MC 服务器可操作，以及各自绑定哪个 Key |
| **Agent 通信** | Spring Boot 与 Go API 的唯一通道，没有它一切业务接口都是空中楼阁 |

Spring Boot **不管理 Docker 容器**——容器的创建/启动/停止由使用者自行操作。Spring Boot 只做 Key→实例的映射管理和 API 代理通信。

---

## P2 模块规划

```text
panel/backend

├─config          # 安全配置
├─entity          # ApiKey + ServerInstance 实体
├─repository      # 数据访问
├─service         # Key 注册管理 + 实例管理
├─controller      # Key 管理 + 实例管理 REST API
└─agent           # Go API HTTP 客户端
```

---

## P2 数据库规划

仅建基础设施表：

```sql
-- api_keys：API Key 注册表（用户从 Go API 获取 Key 后在面板中命名注册）
CREATE TABLE api_keys (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    name            VARCHAR(64)  NOT NULL,
    key_value       VARCHAR(64)  NOT NULL,
    status          VARCHAR(16)  DEFAULT 'active',
    created_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

-- server_instances：MC 实例注册表（绑定已注册的 Key）
CREATE TABLE server_instances (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    name            VARCHAR(64)  NOT NULL,
    api_key_id      BIGINT       NOT NULL,
    host            VARCHAR(255) NOT NULL,
    port            INT          NOT NULL DEFAULT 25560,
    server_type     VARCHAR(32)  NOT NULL,
    mc_version      VARCHAR(16)  NOT NULL,
    rcon_host       VARCHAR(255),
    rcon_port       INT,
    status          VARCHAR(16)  DEFAULT 'unknown',
    created_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (api_key_id) REFERENCES api_keys(id)
);
```

延后到 P3/P4 的表：

```text
users
roles
user_role
protected_resource
operation_logs
```

### 数据库静态加密

`api_keys.key_value` 使用 **AES-256-GCM** 应用层加密存储（JPA `AttributeConverter`），加密密钥通过环境变量 `DB_ENCRYPT_KEY` 注入。

详细实现见 [[springboot-infra-plan]]。

---

## P2 基础设施 API

### Key 管理

| 接口 | 用途 |
|------|------|
| `POST /api/admin/keys` | 注册 API Key（命名 + 粘贴从 Go API 获取的 Key 值） |
| `GET /api/admin/keys` | 列出所有已注册 Key 及其绑定状态 |
| `GET /api/admin/keys/{id}` | 获取单个 Key 详情 |
| `DELETE /api/admin/keys/{id}` | 删除 Key（已绑定实例则拒绝） |
| `POST /api/admin/keys/{id}/revoke` | 吊销 Key |

### 实例管理

| 接口 | 用途 |
|------|------|
| `POST /api/admin/instances` | 注册 MC 实例并绑定已有 Key |
| `GET /api/admin/instances` | 列出所有已注册实例 |
| `GET /api/admin/instances/{id}` | 获取单个实例详情 |
| `PUT /api/admin/instances/{id}` | 更新实例信息 |
| `DELETE /api/admin/instances/{id}` | 删除实例（自动解绑 Key） |
| `PUT /api/admin/instances/{id}/bind-key` | 更换实例绑定的 Key |
| `PUT /api/admin/instances/{id}/refresh-key` | **仅 Root**，调 Go API `POST /api/auth/refresh` 自动刷新 Key（旧 Key 吊销），需前端二次确认 |
| `GET /api/admin/instances/{id}/health` | 代理探测 Go API 健康状态（验证链路） |

---

## Agent 通信

Spring Boot 不直接操作 Minecraft，也不管理 Docker 容器。

统一通过 AgentClient 代理调用 Go API：

```text
Spring Boot
    ↓
AgentClient（从 DB 取出实例绑定的 Key，Bearer 方式携带）
    ↓
Go API（单 Key 比对验证 → 执行操作）
    ↓
Minecraft Server
```

P2 阶段 AgentClient 最小实现：

```text
health()        → GET  /api/health
startServer()   → POST /api/server/start
stopServer()    → POST /api/server/stop
restartServer() → POST /api/server/restart
getStatus()     → GET  /api/server/status
sendCommand()   → POST /api/command
refreshKey()    → POST /api/auth/refresh
```

---

## Go API 侧

**不需要任何改动。** Go API 现有的 Key 生成 + `POST /api/auth/refresh` 刷新接口均已满足需求（见 [[api-doc]]）。使用者通过 `docker logs` 获取 Key 后到 Spring Boot 面板注册即可。

---

## P2 验证标准

1. Spring Boot 启动成功，数据库自动建表（`api_keys` + `server_instances`）
2. `POST /api/admin/keys` 注册 Key 成功，DB 中 `key_value` 为密文存储
3. `POST /api/admin/instances` 创建实例并绑定 Key 成功
4. `GET /api/admin/instances/{id}/health` 通过 AgentClient 调通 Go API 的 `/api/health`
5. `PUT /api/admin/instances/{id}/refresh-key` 调 Go API 刷新 Key → 旧 Key 吊销 → 新 Key 生效
6. Key 换绑后，新 Key 立即生效

---

# 优先级调整

## P0

* 自动JDK选择
* 项目目录重构

---

## P1

* Go API模块化
* Go API单元测试
* 服务端版本自动解析

---

## P2

**基础设施层（架构搭建，详细方案见 [[springboot-infra-plan]]）：**

* Spring Boot 项目骨架搭建
* API Key 注册管理（`api_keys` 表——用户命名 + 粘贴从 Go API 获取的 Key）
* MC Server 实例注册（`server_instances` 表——绑定已注册的 Key）
* 数据库静态加密（AES-256-GCM `AttributeConverter`，密钥通过 `DB_ENCRYPT_KEY` 环境变量注入）
* `PUT /api/admin/instances/{id}/refresh-key`（仅 Root，自动调 Go API `POST /api/auth/refresh` 刷新 Key）
* AgentClient（Spring Boot → Go API 通信客户端，含 `refreshKey()` 方法）
* Go API **无需改动**（Key 生成 + `POST /api/auth/refresh` 均已存在）
* Spring Boot **不管理 Docker 容器**（由使用者自行操作）
* Spring Boot → Go API 通信链路验证

**设计层（仅出方案，实现在 P3/P4）：**

* 权限系统设计（见 [[panel-design]]）
* 文件安全保护机制设计（见 [[panel-design]]）
* 重要资源保护机制设计（见 [[panel-design]]）

---

## P3

**业务逻辑实现 + 前端初始化：**

* 用户/角色/权限 CRUD（users / roles / user_role 表）
* 资源保护规则初始化（protected_resource 表）
* 文件管理代理接口（Spring Boot 鉴权 → Go API 执行）
* 操作日志（operation_logs 表）
* Vue Panel 初始化
* Dashboard
* Console

---

## P4

* File Manager
* 用户管理 UI
* 实例管理 UI
* Docker SDK 接入
* 玩家管理（kick / whitelist / op）

---

## P5

* 多节点管理
* 集群管理
* 分布式 Agent 架构
