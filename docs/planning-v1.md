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

# 十、Spring Boot 面板初始化（P2）

## 目标

初始化 Spring Boot 后端项目，作为统一业务层。

职责：

```text
用户管理

权限管理

资源保护

实例管理

操作日志

Agent通信
```

---

## 模块规划

```text
panel/backend

├─auth
├─user
├─role
├─server
├─resource
├─audit
└─agent
```

---

## 数据库规划

初始核心表：

```text
users

roles

user_role

servers

protected_resource

operation_logs
```

---

## Agent 通信

Spring Boot 不直接操作 Minecraft。

统一通过 Agent：

```text
Spring Boot
    ↓
Agent Client
    ↓
Go API
```

调用：

```http
POST /api/server/start
POST /api/server/stop
POST /api/server/command
GET  /api/server/status
```

实现服务端管理。

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

* Spring Boot初始化
* Go API扩展
* 权限系统设计
* 文件安全保护机制
* 重要资源保护机制

---

## P3

* Dashboard
* Console
* File Manager

---

## P4

* 用户管理
* 实例管理
* Docker SDK接入

---

## P5

* 多节点管理
* 集群管理
* 分布式 Agent 架构
