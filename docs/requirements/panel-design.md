# Minecraft Panel 文件删除权限设计

## 背景

在 Minecraft 服务端管理面板中，文件管理功能允许用户对服务端目录进行浏览、编辑、上传、下载和删除操作。

由于部分文件和目录属于服务端核心资源，例如：

* world
* world_nether
* world_the_end
* server.properties
* eula.txt
* ops.json
* whitelist.json
* banned-players.json
* plugins
* mods
* config

一旦误删，可能导致：

* 世界数据永久丢失
* 服务端无法启动
* 权限配置丢失
* 插件或 Mod 失效

因此需要建立完整的权限控制与安全保护机制。

---

# 总体设计

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

其中：

* Spring Boot 负责业务权限控制
* Go API 负责底层安全保护

即使上层权限出现问题，也能够通过 Go API 的安全校验避免重要文件被误删。

---

# 用户权限模型

系统定义三种角色：

## Root

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

## Admin

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

## User

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

创建资源保护表：

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

例如：

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

用户点击删除：

```text
logs/latest.log
```

Spring Boot 校验：

```text
资源类型 = LOG
```

权限：

```text
Admin
```

允许删除。

随后调用 Go API：

```http
DELETE /api/files/delete
```

执行删除。

---

## 重要文件删除

用户点击删除：

```text
world
```

Spring Boot 查询：

```text
资源类型 = WORLD
```

权限：

```text
Admin
```

直接拒绝：

```json
{
  "code": 403,
  "message": "无权限删除世界存档"
}
```

---

## Root 删除重要资源

Root 用户删除：

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

输入正确后：

```http
DELETE /api/files/delete
```

```json
{
  "path": "world",
  "force": true
}
```

发送至 Go API。

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

# 设计目标

通过双层保护机制实现：

* 防止普通用户误删核心文件
* 防止管理员误删世界数据
* 防止 Spring Boot 权限逻辑异常导致误删
* 防止未来 CLI、App 或第三方调用绕过权限
* 保证 Minecraft 核心资源安全

最终形成：

```text
Spring Boot
    ↓
业务权限控制

Go API
    ↓
底层安全兜底
```

即使上层出现问题，核心资源仍然能够得到保护。
