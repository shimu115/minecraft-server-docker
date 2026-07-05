# P3 业务逻辑深化 + 权限体系方案

## 背景

P2 已完成全栈基础设施，打通了 认证 → Key 管理 → 实例注册 → Agent 通信 全链路。P3 的核心是在基础设施之上建立**完整的权限体系**，并以此为底座实现文件管理、玩家管理、操作审计等业务功能。

P3 相较于 P2 的本质区别：P2 解决「能不能通信」，P3 解决「谁能做什么」。

---

## 1. 关键设计决策

| # | 决策点 | 决策 | 理由 |
|---|--------|------|------|
| 1 | RBAC 粒度 | 保持 `users.role` 单字段（ROOT/ADMIN/USER），叠加**路径级 ACL** 实现细粒度控制 | 角色定义身份边界，ACL 定义操作边界，各司其职 |
| 2 | server.properties 编辑 | **表格式编辑器**，每个配置项带权限标记，非纯文本编辑 | 降低误操作风险，ADMIN 可改 motd/pvp 等安全项，不可改 server-port 等危险项 |
| 3 | 文件管理代理 | 全量代理 Go API 文件端点，Spring Boot 叠加权限校验 | P2 AgentClient 模式已验证可行 |
| 4 | Go API 改动 | 仅 3 处：delete 加 `force` 参数、新增 mkdir、新增 rename | 最小改动原则 |
| 5 | Docker SDK | 仅做容器资源监控（CPU/内存/网络/uptime），不管理容器生命周期 | 保持 P2 定位 |
| 6 | 玩家管理 | 复用 `POST /api/command` + 文件读取，不新增 Go API 端点 | 现有能力足够 |
| 7 | USER 角色 | 只读观察者（仪表盘 + 控制台日志 + 玩家列表） | 见 §2.5 |
| 8 | 动态路由 | 侧边栏根据 role 动态渲染，不同角色看到不同菜单 | 见 §4 |
| 9 | ACL 可见性 | 无 ACL 记录 = 路径**完全不可见**（前端不展示、API 不返回） | 安全性：不知道路径存在就无法尝试攻击 |
| 10 | ACL 粒度 | 仅目录级，前缀匹配。不支持单文件权限 | 降低管理复杂度、减少 ACL 数据量 |
| 11 | ADMIN 默认模板 | 新建 ADMIN 用户时自动套用，默认可管理 mods/config/plugins | 见 §2.6 |

---

## 2. 权限体系设计（核心）

### 2.1 三层权限模型

```text
┌──────────────────────────────────────────────┐
│  第一层：角色边界 (users.role)                │
│  ROOT → 全局通行                              │
│  ADMIN → 仅绑定实例，操作受 ACL 约束           │
│  USER → 只读观察                              │
├──────────────────────────────────────────────┤
│  第二层：路径级 ACL (user_path_permissions)    │
│  决定 ADMIN 在特定实例的特定路径能否管理        │
│  ROOT 可为每个 ADMIN 逐实例逐路径配置           │
├──────────────────────────────────────────────┤
│  第三层：资源保护兜底 (protected_resources)     │
│  世界存档 / 核心配置等不可恢复资源              │
│  WORLD 类资源仅 ROOT+force 可删                │
│  Go API 侧硬编码保护列表作为最后一道防线         │
└──────────────────────────────────────────────┘
```

**权限检查链路（以 ADMIN 删除文件为例）：**

```text
请求：ADMIN 删除 /mc1/world/region/r.0.0.mca
       ↓
1. 角色边界：ADMIN → 允许操作文件，继续
2. 实例绑定：查 user_instances → 已绑定 mc1 → 继续
3. 路径 ACL：查 user_path_permissions(user=a, instance=mc1, path=world)
   ├── can_manage=true  → 继续（有管理权限）
   └── can_manage=false → 403 "无权管理此路径"
4. 资源保护：查 protected_resources(instance=mc1, path=world)
   └── resource_type=WORLD, deletable=false → ADMIN → 403 "无权限删除世界存档"
5. (ROOT+force=true 才可到达) AgentClient.deleteFile(force=true)
6. Go API 最终检查：IsProtected("world") && !force → 403
```

### 2.2 路径级 ACL（user_path_permissions）

ROOT 可以为每个 ADMIN 用户在每个实例上设置不同路径的管理权限。

```text
示例配置：

a (ADMIN)
  mc1 (1.12.2 Forge 生存服)
    mods   → can_manage = true   ✅ 可以上传/删除 mod
    config → can_manage = true   ✅ 可以修改配置
    world  → can_manage = false  ❌ 不能碰世界存档
  mc2 (1.20.1 Fabric 创造服)
    mods   → can_manage = false  ❌ 不能管理 mod
    world  → can_manage = false  ❌ 不能碰世界存档

b (ADMIN)
  mc1
    mods   → can_manage = false  ❌ 不能管理 mod
    world  → can_manage = true   ✅ 可以管理世界存档
  mc2
    mods   → can_manage = true   ✅ 可以管理 mod
    world  → can_manage = false  ❌ 不能碰世界存档
```

**数据库设计：**

```sql
CREATE TABLE user_path_permissions (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id         BIGINT       NOT NULL,
    instance_id     BIGINT       NOT NULL,
    path            VARCHAR(255) NOT NULL,           -- 路径，如 "mods", "world", "config"
    can_manage      BOOLEAN      DEFAULT FALSE,      -- true=可读写删, false=只读
    created_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (user_id)    REFERENCES users(id),
    FOREIGN KEY (instance_id) REFERENCES server_instances(id) ON DELETE CASCADE,
    UNIQUE (user_id, instance_id, path)
);

CREATE INDEX idx_upp_user ON user_path_permissions(user_id);
CREATE INDEX idx_upp_instance ON user_path_permissions(instance_id);
```

**匹配规则（前缀匹配）：**

```text
用户设置了 path="mods" can_manage=true

则：
  mods/                    → 匹配 ✅
  mods/example.jar         → 匹配 ✅
  mods/1.12.2/             → 匹配 ✅
  mods/1.12.2/example.jar  → 匹配 ✅

未在 ACL 表中配置的路径 → **对用户完全不可见**
```

**ACL 可见性规则（核心安全设计）：**

| 条件 | 结果 |
|------|------|
| 路径在 ACL 中 + `can_manage=true` | 可见，可读写删 |
| 路径在 ACL 中 + `can_manage=false` | **不可见**（目录/文件从列表中排除） |
| 路径不在 ACL 中 | **不可见**（等同于 `can_manage=false`） |
| ROOT 用户 | 全部可见（跳过 ACL 检查） |

**设计意图：** 不知道路径存在就无法尝试访问。如果一个 ADMIN 无权管理 `world`，他不仅不能删除 world，连 world 在文件列表中**根本看不到**。

**粒度限制：** 仅支持目录级 ACL，不支持单文件级别。`mods` 的权限覆盖 `mods/` 下所有子目录和文件。管理粒度已经足够精细——如果需要禁止修改某个特定文件，将其放入独立的子目录进行控制。

**后端过滤逻辑（`FileServiceImpl.listFiles`）：**

```java
// ADMIN 用户列出文件时，过滤掉不可见路径
List<FileInfo> allFiles = agentClient.listFiles(baseUrl, key, path);
if (!currentUser.isRoot()) {
    List<UserPathPermission> acl = permissionRepo.findByUserIdAndInstanceId(userId, instanceId);
    Set<String> visiblePaths = acl.stream()
        .filter(p -> p.getCanManage())
        .map(UserPathPermission::getPath)
        .collect(Collectors.toSet());
    allFiles = allFiles.stream()
        .filter(f -> isVisible(f.getPath(), visiblePaths))
        .toList();
}
```

### 2.3 配置文件属性权限（config_property_permissions）

`server.properties` 采用表格式编辑器。每个配置项的编辑权限独立控制：

```text
属性分类：
  server-port  → editable_by = ROOT   (仅 ROOT 可改，改错端口会导致连接中断)
  motd         → editable_by = ADMIN  (欢迎语，安全可改)
  pvp          → editable_by = ADMIN  (游戏设置)
  difficulty   → editable_by = ADMIN
  gamemode     → editable_by = ADMIN
  max-players  → editable_by = ROOT   (资源分配)
  white-list   → editable_by = ADMIN
  enable-command-block → editable_by = ROOT  (安全性)
  ...
```

ROOT 可在「权限管理 → 配置文件权限」页面修改每个属性的 `editable_by` 值。

**数据库设计：**

```sql
CREATE TABLE config_property_permissions (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    property_key    VARCHAR(128) NOT NULL UNIQUE,    -- 如 "server-port", "motd"
    editable_by     VARCHAR(16)  NOT NULL DEFAULT 'ROOT',  -- ROOT / ADMIN
    description     VARCHAR(255),                    -- 中文说明，如 "服务器端口"
    created_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);
```

**预初始化数据：**

| property_key | editable_by | description |
|---|---|---|
| server-port | ROOT | 服务器端口 |
| server-ip | ROOT | 绑定 IP |
| motd | ADMIN | 服务器欢迎语 (MOTD) |
| pvp | ADMIN | 是否开启 PVP |
| difficulty | ADMIN | 游戏难度 |
| gamemode | ADMIN | 默认游戏模式 |
| max-players | ROOT | 最大玩家数 |
| white-list | ADMIN | 是否开启白名单 |
| enable-command-block | ROOT | 是否启用命令方块 |
| spawn-protection | ROOT | 出生点保护范围 |
| view-distance | ROOT | 视距 |
| online-mode | ROOT | 正版验证 |
| allow-nether | ADMIN | 是否开启下界 |
| allow-flight | ROOT | 是否允许飞行 |
| spawn-npcs | ADMIN | 是否生成 NPC |
| spawn-animals | ADMIN | 是否生成动物 |
| spawn-monsters | ADMIN | 是否生成怪物 |
| level-name | ROOT | 世界名称 |
| level-seed | ROOT | 世界种子 |
| resource-pack | ADMIN | 资源包 URL |
| enforce-whitelist | ADMIN | 强制白名单 |

### 2.4 角色默认权限矩阵

| 操作 | ROOT | ADMIN | USER |
|------|------|-------|------|
| **仪表盘** | 全部实例 | 仅绑定实例 | 仅绑定实例（无操作按钮） |
| **控制台日志** | ✅ | ✅ | ✅（只读） |
| **发送指令** | ✅ | ✅（绑定实例） | ❌ |
| **服务端启停** | ✅ | ✅（绑定实例） | ❌ |
| **文件浏览** | ✅ 全部 | ✅ 绑定实例（仅 ACL 中存在的路径） | ❌ |
| **文件读取** | ✅ 全部 | ✅ 绑定实例（仅 ACL 中存在的路径） | ❌ |
| **文件编辑** | ✅ 全部 | ✅ 绑定实例（仅 ACL can_manage=true 的路径） | ❌ |
| **文件上传** | ✅ 全部 | ✅ 绑定实例（仅 ACL can_manage=true 的路径） | ❌ |
| **文件删除** | ✅ 全部（受保护资源需 force） | ✅ 绑定实例（ACL can_manage=true 且不受保护资源约束） | ❌ |
| **server.properties 编辑** | ✅ 全部属性 | ✅ 仅 editable_by=ADMIN 的属性 | ❌ |
| **玩家 kick** | ✅ 全部 | ✅ 绑定实例 | ❌ |
| **白名单管理** | ✅ 全部 | ✅ 绑定实例 | ❌ |
| **OP 管理** | ✅ 仅 ROOT | ❌ | ❌ |
| **API Key 管理** | ✅ | ❌ | ❌ |
| **实例管理（CRUD）** | ✅ | ❌ | ❌ |
| **用户管理** | ✅ | ❌ | ❌ |
| **权限管理（ACL + 配置属性）** | ✅ | ❌ | ❌ |
| **操作日志** | ✅ 全部 | ✅ 仅绑定实例 | ❌ |

### 2.5 USER 角色建议

USER = **只读观察者**。设计思路：USER 通常是服务器的普通玩家或社区成员，只需要知道服务器状态而不需要任何管理能力。

| 功能 | 权限 | 说明 |
|------|------|------|
| 仪表盘 | ✅ 仅绑定实例 | 显示绑定的服务器是否在线、玩家数、版本 |
| 控制台日志 | ✅ 只读 | SSE 实时日志流，不可发送指令 |
| 在线玩家列表 | ✅ 只读 | 查看当前在线玩家 |
| 文件管理 | ❌ | 不可浏览、编辑、上传、删除 |
| 服务端控制 | ❌ | 不可启动/停止/重启 |
| 发送指令 | ❌ | 不可通过控制台发任何指令 |
| 玩家管理 | ❌ | 不可 kick/whitelist/op |
| 配置编辑 | ❌ | 不可修改 server.properties |
| 系统管理 | ❌ | 不可管理 Key/实例/用户/权限 |

**适用场景：**
- 服务器普通玩家：连接前先看面板确认服务器是否在线
- 社区观察员：通过日志了解服务器动态
- 实习管理员考察期：先开放只读权限熟悉系统，后续升级为 ADMIN

USER 在侧边栏仅看到：**仪表盘**（仅绑定实例的状态卡片）。

### 2.6 ADMIN 默认权限模板

新建 ADMIN 用户并绑定实例时，自动套用默认权限模板。ROOT 可在创建后自定义调整。

**模板规则：**

```text
所有服务端类型通用：
  mods     → can_manage = true    ✅ 可管理模组
  config   → can_manage = true    ✅ 可管理配置
  plugins  → can_manage = true    ✅ 可管理插件（仅 Paper/Purpur 实例存在此目录）
  logs     → can_manage = true    ✅ 可查看/下载日志
  world    → can_manage = false   ❌ 不可见（世界存档）
  world_nether  → can_manage = false
  world_the_end → can_manage = false

其他未列出的路径 → 无 ACL 记录 → 不可见
```

**设计说明：**
- ADMIN 默认可以管理 `mods`、`config`、`plugins` —— 这是他们最日常的操作（安装/更新模组和插件）
- `world` 及其变体默认不可见 —— 防止误删，绝大多数情况下 ADMIN 不需要操作世界文件
- `logs` 可管理 —— ADMIN 可能需要下载日志排查问题
- ROOT 可在「权限管理 → 用户权限」页面调整任意 ADMIN 的 ACL

**实现方式：**

```java
// UserServiceImpl.createUser() 中，创建用户并绑定实例后：
if ("ADMIN".equals(user.getRole())) {
    permissionService.applyDefaultTemplate(user.getId(), instanceId);
}

// PermissionServiceImpl.applyDefaultTemplate():
void applyDefaultTemplate(Long userId, Long instanceId) {
    // 全部模板路径，can_manage=true 即为默认允许的路径
    Map<String, Boolean> template = Map.of(
        "mods",    true,
        "config",  true,
        "plugins", true,
        "logs",    true,
        "world",        false,  // false = 不可见
        "world_nether", false,
        "world_the_end",false
    );
    for (var entry : template.entrySet()) {
        UserPathPermission p = new UserPathPermission();
        p.setUserId(userId);
        p.setInstanceId(instanceId);
        p.setPath(entry.getKey());
        p.setCanManage(entry.getValue());
        repo.save(p);
    }
}
```

> `world` 等 `false` 条目也写入表——确保这些路径显式不可见。如果不在表中，ROOT 在权限管理 UI 中就看不到这些路径的记录，无法方便地「开启」。

---

## 3. server.properties 表格式编辑器

### 3.1 设计原则

- **不**使用纯文本编辑器（避免误改危险配置）
- 以表格形式展示所有属性，每行一个 `key = value`
- 每个属性带编辑权限标识：🔒 = 仅 ROOT，✏️ = ROOT + ADMIN
- USER 进入页面时所有行均不可编辑（或直接隐藏此功能入口）

### 3.2 UI 设计

```
┌─────────────────────────────────────────────────────────┐
│  server.properties 编辑器                          [保存]│
│                                                         │
│  ┌──────────────────────────────────────────────────┐  │
│  │ 属性                    值              权限      │  │
│  ├──────────────────────────────────────────────────┤  │
│  │ server-port           │ 25565         │ 🔒 ROOT  │  │
│  │ motd                  │ A Minecraft.. │ ✏️ ADMIN │  │
│  │ pvp                   │ true          │ ✏️ ADMIN │  │
│  │ difficulty            │ normal        │ ✏️ ADMIN │  │
│  │ gamemode              │ survival      │ ✏️ ADMIN │  │
│  │ max-players           │ 20            │ 🔒 ROOT  │  │
│  │ enable-command-block  │ false         │ 🔒 ROOT  │  │
│  │ ...                   │ ...           │ ...      │  │
│  └──────────────────────────────────────────────────┘  │
│                                                         │
│  🔒 = 仅 ROOT 可编辑    ✏️ = ROOT/ADMIN 可编辑         │
└─────────────────────────────────────────────────────────┘
```

**交互规则：**
- ROOT 用户：所有行可编辑
- ADMIN 用户：仅 `editable_by=ADMIN` 的行可编辑，🔒 行灰显 disabled
- 修改过的行高亮标记（左侧蓝色竖线）
- 点击「保存」→ 确认弹窗显示变更列表 → 确认后写入 Go API

**实现方式：**
1. 通过 Go API `GET /api/files/read?path=server.properties` 读取原始内容
2. 前端解析 `key=value` 格式为表格数据
3. 调用 Spring Boot `GET /api/admin/config-properties` 获取每个 key 的 `editable_by`
4. 前端根据当前用户 role 决定每行是否可编辑
5. 保存时构建完整的 properties 文本，通过 Go API `POST /api/files/write?path=server.properties` 写入

### 3.3 入口位置

实例详情页的「基本信息」卡片中新增按钮：

```text
实例详情页
├── 基本信息卡片
│   └── [编辑 server.properties] 按钮  ← 新增
├── 绑定的 API Key 卡片
├── Docker 资源监控卡片               ← P3 新增
├── Tab: 控制台 | 文件管理 | 玩家管理   ← P3 新增 Tab
```

点击「编辑 server.properties」→ 弹出 Modal（二级窗口），内含上述表格编辑器。

---

## 4. 动态路由与侧边栏

### 4.1 设计原则

不同 role 看到不同侧边栏菜单。菜单配置在后端 `/api/auth/get-me` 中随用户信息一起返回，前端动态渲染。

### 4.2 各角色菜单

```
ROOT 侧边栏：
├── 📊 仪表盘            → /
├── 🖥️ 实例管理           → /instances
├── 🔑 API Keys          → /keys
├── 📋 操作日志           → /logs              ← P3 新增
├── 🔐 权限管理           │                    ← P3 新增
│   ├── 用户权限          → /permissions/users
│   └── 配置文件权限       → /permissions/config
└── 👤 用户管理           → /admin/users

ADMIN 侧边栏：
├── 📊 仪表盘            → /                  （仅显示绑定实例）
└── 🖥️ 服务器管理         → /instances         （仅显示绑定实例）

USER 侧边栏：
└── 📊 仪表盘            → /                  （仅显示绑定实例状态，无操作按钮）
```

### 4.3 实现方式

**后端：** `/api/auth/get-me` 响应中新增 `menus` 字段：

```json
{
    "userId": 1,
    "username": "root",
    "role": "ROOT",
    "menus": [
        {"key": "dashboard", "label": "仪表盘", "path": "/"},
        {"key": "instances", "label": "实例管理", "path": "/instances"},
        {"key": "keys", "label": "API Keys", "path": "/keys"},
        {"key": "logs", "label": "操作日志", "path": "/logs"},
        {
            "key": "permissions", "label": "权限管理",
            "children": [
                {"key": "perm-users", "label": "用户权限", "path": "/permissions/users"},
                {"key": "perm-config", "label": "配置文件权限", "path": "/permissions/config"}
            ]
        },
        {"key": "admin-users", "label": "用户管理", "path": "/admin/users"}
    ]
}
```

**前端：** `AppLayout.vue` 从 `getMe()` 获取 menus 并动态渲染，替代当前硬编码的 `menuOptions`。

**路由：** 使用 Vue Router 的动态路由（`router.addRoute`），在 `getMe()` 返回后根据 role 注册对应路由。未注册路由的路径自动跳转 404 或首页。

---

## 5. 文件管理代理

### 5.1 Spring Boot 代理层

所有文件操作通过 Spring Boot Controller 鉴权后，由 AgentClient 转发 Go API。

```
Vue Panel → /api/server/{id}/files/* → FilesController
    → 权限检查（角色 + 路径 ACL + 受保护资源）
    → AgentClient → Go API → 文件系统
    → 操作日志记录
```

### 5.2 文件管理 API

#### GET /api/server/{id}/files/list

列出目录。**ADMIN 用户仅能看到 ACL 中配置了 `can_manage=true` 的路径**，其余路径在响应中完全排除。

**后端过滤流程：**

```text
1. AgentClient 调 Go API → 获取完整文件列表
2. 若当前用户是 ROOT → 直接返回完整列表
3. 若当前用户是 ADMIN：
   a. 查询 user_path_permissions(user, instance) → can_manage=true 的路径集合
   b. 对每个文件/目录执行前缀匹配：文件路径是否匹配集合中任意前缀
   c. 匹配成功 → 保留；匹配失败 → 从结果中移除
4. 附加 protected / resourceType / canManage 标记
```

**响应：**

```json
{
    "code": 200,
    "data": [
        {
            "name": "mods", "path": "mods", "isDir": true,
            "size": 4096, "modTime": "2026-07-01 15:30:00",
            "protected": true, "resourceType": "MOD",
            "canManage": true
        },
        {
            "name": "config", "path": "config", "isDir": true,
            "size": 4096, "modTime": "2026-07-01 15:30:00",
            "protected": false, "resourceType": null,
            "canManage": true
        }
    ]
}
```

> 对 ADMIN 而言，`world`、`server.properties` 等未授权的路径**根本不会出现**在响应中。`canManage` 字段供前端决定是否显示编辑/删除按钮。

#### POST /api/server/{id}/files/read

```json
// Request
{ "path": "server.properties" }

// Response
{ "code": 200, "data": { "path": "...", "content": "..." } }
```

#### POST /api/server/{id}/files/write

```json
{ "path": "server.properties", "content": "..." }
```

权限：ADMIN 需 ACL `can_manage=true`。

#### DELETE /api/server/{id}/files/delete

```json
{ "path": "old_mod.jar", "force": false }
```

权限矩阵：

| 角色 | 普通文件 | 受保护资源 (deletable=false) |
|------|---------|---------------------------|
| ROOT | ✅ | ✅（需 force=true） |
| ADMIN | ✅（需 ACL can_manage=true） | ❌ 403 |
| USER | ❌ 403 | ❌ 403 |

#### POST /api/server/{id}/files/upload

multipart/form-data，流式转发。权限：ADMIN 需 ACL `can_manage=true`。

#### GET /api/server/{id}/files/download

流式中转。权限：ADMIN 需绑定实例。

#### POST /api/server/{id}/files/export

```json
{ "format": "zip" }
```

流式中转。权限：ROOT / ADMIN（需绑定实例）。

#### POST /api/server/{id}/files/mkdir

```json
{ "path": "plugins/NewPlugin" }
```

Go API 需新增 `POST /api/files/mkdir` 端点。

#### POST /api/server/{id}/files/rename

```json
{ "path": "old.txt", "newPath": "new.txt" }
```

Go API 需新增 `POST /api/files/rename` 端点。

---

## 6. 受保护资源系统

### 6.1 数据库设计

```sql
CREATE TABLE protected_resources (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    instance_id     BIGINT       NOT NULL,
    path            VARCHAR(255) NOT NULL,
    resource_type   VARCHAR(50)  NOT NULL,      -- WORLD / CONFIG / PLUGIN / MOD / CORE / LOG / OTHER
    deletable       BOOLEAN      DEFAULT FALSE,
    created_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (instance_id) REFERENCES server_instances(id) ON DELETE CASCADE,
    UNIQUE (instance_id, path)
);
```

### 6.2 初始化模板

实例注册时根据 `server_type` 自动插入保护规则：

| 服务端类型 | 额外保护项（Vanilla 基础上增加） |
|-----------|-------------------------------|
| Vanilla | world, world_nether, world_the_end, server.properties, eula.txt, ops.json, whitelist.json, banned-players.json |
| Paper/Purpur | + plugins, bukkit.yml, spigot.yml |
| Forge/Fabric/NeoForge | + mods, config |

### 6.3 Go API 硬编码兜底

```go
var protectedPaths = []string{
    "world", "world_nether", "world_the_end",
    "server.properties", "eula.txt",
    "ops.json", "whitelist.json", "banned-players.json",
    "plugins", "mods", "config",
}

func IsProtected(path string) bool { ... }
func DeleteFile(baseDir, relPath string, force bool) error { ... }
```

---

## 7. 玩家管理

复用 Go API 现有能力，不新增端点。

| 操作 | 实现方式 |
|------|---------|
| 踢出玩家 | `POST /api/command` → `/kick <player> <reason>` |
| 白名单列表 | `GET /api/files/read?path=whitelist.json` → 解析 JSON |
| 添加/移除白名单 | `POST /api/command` → `/whitelist add/remove <player>` |
| OP 列表 | `GET /api/files/read?path=ops.json` → 解析 JSON |
| 设置/取消 OP | `POST /api/command` → `/op <player>` / `/deop <player>` |

OP 管理权限仅 ROOT（OP 可执行 `/stop` 等危险指令，属于服务器级管理）。

### API 设计

#### POST /api/server/{id}/players/kick

```json
{ "player": "Steve", "reason": "违反规则" }
```

权限：ROOT / ADMIN（需绑定实例）。

#### GET /api/server/{id}/players/whitelist

权限：ROOT / ADMIN（需绑定实例）。

#### POST /api/server/{id}/players/whitelist

```json
{ "player": "Steve", "action": "add" }
```

#### GET /api/server/{id}/players/ops

权限：仅 ROOT。

#### POST /api/server/{id}/players/op

```json
{ "player": "Steve", "action": "op" }
```

权限：仅 ROOT。

---

## 8. 操作日志

### 8.1 数据库设计

```sql
CREATE TABLE operation_logs (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id         BIGINT       NOT NULL,
    username        VARCHAR(64)  NOT NULL,
    instance_id     BIGINT,
    instance_name   VARCHAR(64),
    operation       VARCHAR(64)  NOT NULL,       -- FILE_DELETE / PLAYER_KICK / ...
    target          VARCHAR(255),
    detail          TEXT,                         -- JSON 附加信息
    ip_address      VARCHAR(45),
    created_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE INDEX idx_log_user ON operation_logs(user_id);
CREATE INDEX idx_log_instance ON operation_logs(instance_id);
CREATE INDEX idx_log_operation ON operation_logs(operation);
CREATE INDEX idx_log_created ON operation_logs(created_at);
```

### 8.2 操作类型

| 类型 | 说明 | target 示例 |
|------|------|------------|
| `FILE_READ` | 读取文件 | `server.properties` |
| `FILE_WRITE` | 编辑文件 | `server.properties` |
| `FILE_DELETE` | 删除文件 | `world` |
| `FILE_UPLOAD` | 上传文件 | `mods/example.jar` |
| `FILE_DOWNLOAD` | 下载文件 | `server.jar` |
| `FILE_EXPORT` | 导出目录 | `zip` |
| `FILE_MKDIR` | 创建目录 | `plugins/X` |
| `FILE_RENAME` | 重命名 | `old.txt → new.txt` |
| `PLAYER_KICK` | 踢玩家 | `Steve` |
| `PLAYER_WHITELIST_ADD/REMOVE` | 白名单 | `Steve` |
| `PLAYER_OP/DEOP` | OP 管理 | `Steve` |
| `SERVER_START/STOP/RESTART` | 服务端控制 | — |
| `SERVER_COMMAND` | 发送指令 | `say Hello` |
| `CONFIG_EDIT` | 编辑 server.properties | `motd` |
| `PERMISSION_UPDATE` | 权限变更 | `user a, mc1, mods=true` |
| `KEY_REFRESH` | 刷新 Key | — |
| `INSTANCE_CREATE/DELETE` | 实例 CRUD | — |

### 8.3 查询 API

**GET /api/admin/logs**

| 参数 | 说明 |
|------|------|
| instanceId | 实例 ID（可选） |
| operation | 操作类型（可选） |
| userId | 用户 ID（可选，仅 Root） |
| page / size | 分页（默认 1/20） |
| startDate / endDate | 日期范围（可选） |

权限：ROOT 全部，ADMIN 仅绑定实例。

---

## 9. Docker SDK 集成

### 9.1 目标

通过 Docker API 获取容器资源指标，在实例详情页展示。

### 9.2 技术方案

| 项 | 选择 |
|----|------|
| SDK | docker-java |
| 连接方式 | 环境变量 `DOCKER_HOST` 配置（默认 `unix:///var/run/docker.sock`） |
| 容器匹配 | 通过 `server_instances.name` 匹配 Docker 容器名 |

### 9.3 展示指标

```
┌──────────────────────────────────────────┐
│ 📊 容器资源                               │
│                                           │
│ CPU:  ████████░░░░  45.2%                │
│ RAM:  ██████████░░  2048MB / 4096MB      │
│ NET:  ↓ 1.2GB  ↑ 340MB                   │
│ UP:   3 days 12:30:00                     │
│ Status: running                           │
└──────────────────────────────────────────┘
```

Docker 不可达时降级显示「Docker 监控未接入」。

### 9.4 API

**GET /api/admin/instances/{id}/stats**

```json
{
    "code": 200,
    "data": {
        "instanceId": 1,
        "cpuPercent": 45.2,
        "memoryUsage": "2048MB",
        "memoryLimit": "4096MB",
        "networkRx": "1.2GB",
        "networkTx": "340MB",
        "uptime": "3 days 12:30:00",
        "containerStatus": "running"
    }
}
```

---

## 10. Go API 改动

P3 仅需 3 处小改动，总计约 55 行代码。

### 10.1 delete 增加 force 参数

`handler/files.go` — `Delete()` 解析 `force` 查询参数：
```go
force := r.URL.Query().Get("force") == "true"
```

`service/files.go` — `DeleteFile()` 增加 `force` 参数 + `IsProtected()`：
```go
func DeleteFile(baseDir, relPath string, force bool) error { ... }
func IsProtected(path string) bool { ... }
```

### 10.2 新增 mkdir 端点

`POST /api/files/mkdir?path=<相对路径>`（约 15 行）

### 10.3 新增 rename 端点

`POST /api/files/rename`（约 20 行）
```json
{ "path": "old.txt", "new_path": "new.txt" }
```

### 10.4 单元测试

`service/files_test.go` 新增 protected resource + force 测试。

---

## 11. 数据库增量总览

P2 已有：`users` / `api_keys` / `server_instances` / `user_instances`

P3 新增 4 张表：

| 表 | 用途 |
|----|------|
| `user_path_permissions` | 路径级 ACL（用户×实例×路径） |
| `config_property_permissions` | server.properties 属性编辑权限 |
| `protected_resources` | 受保护资源注册表 |
| `operation_logs` | 操作审计日志 |

---

## 12. 前端页面与交互

### 12.1 动态侧边栏（AppLayout 改造）

- 从 `getMe()` 获取 `menus` 数组
- 动态渲染 `<n-menu>` options
- 支持嵌套子菜单（权限管理 → 用户权限 / 配置文件权限）
- 路由通过 `router.addRoute()` 动态注册

### 12.2 Dashboard 按角色差异化

| 角色 | 展示内容 |
|------|---------|
| ROOT | 全部 Key 数量、全部实例数量、运行中数量，System Info |
| ADMIN | 仅绑定实例的状态卡片（running/stopped/players/version），无全局统计 |
| USER | 仅绑定实例的状态卡片（running/players/version，纯展示，无按钮） |

### 12.3 实例详情页增强

在 P2 基础上增加 Tab 结构：

```
实例详情页
├── 基本信息卡片（含 [编辑 server.properties] 按钮）
├── API Key 卡片
├── Docker 资源监控卡片
└── Tab 栏
    ├── 控制台 (P2 已有)
    ├── 文件管理 (P3 新增)
    └── 玩家管理 (P3 新增)
```

### 12.4 File Manager

双栏布局：左侧目录树 + 右侧文件列表。核心交互：

- 文件列表显示 🔒 图标（受保护资源）+ resource_type 标签
- 右键菜单：下载 / 重命名 / 删除
- 拖拽上传 + 进度条
- 文本文件点击 → Monaco Editor 侧边栏编辑
- 删除受保护资源 → 二次确认弹窗（Root 需输入 `DELETE WORLD`）

### 12.5 server.properties 表格式编辑器（Modal）

表格列：属性名 | 当前值（可编辑） | 权限标识（🔒/✏️）
- ADMIN 用户：🔒 行灰显不可编辑
- 修改过的行高亮
- 保存时显示变更 diff 确认

### 12.6 玩家管理 Tab

- 在线玩家卡片（头像 + 玩家名 + kick 按钮）
- 白名单面板（列表 + 添加/移除）
- OP 面板（列表 + 添加/移除，仅 Root 可见）

### 12.7 权限管理页面（仅 Root）

#### 用户权限（/permissions/users）

- 用户列表（表格）→ 点击用户 → 展开权限编辑面板
- 权限编辑面板：左侧选择实例，右侧为路径-权限矩阵
- 每行：路径 | can_manage 开关
- 可新增路径、删除路径权限条目
- 操作日志自动记录

#### 配置文件权限（/permissions/config）

- server.properties 属性列表（表格）
- 每行：属性 key | 描述 | editable_by 下拉选择（ROOT/ADMIN）
- 修改后即时生效

### 12.8 操作日志页面（/logs）

- 筛选栏：实例、操作类型、用户（Root）、日期范围
- 日志表格（分页）
- 导出 CSV（Root）

### 12.9 前端交付清单

- [ ] `AppLayout.vue` 改造：动态菜单 + 动态路由注册
- [ ] `DashboardView.vue` 改造：按角色差异化展示
- [ ] `InstanceDetailView.vue` 改造：Tab 结构 + Docker 卡片 + properties 编辑按钮
- [ ] `FileManagerView.vue`：双栏文件浏览器
- [ ] `FileEditorModal.vue`：Monaco Editor 在线编辑
- [ ] `FileUpload.vue`：拖拽上传组件
- [ ] `DeleteConfirmDialog.vue`：增强版（资源类型 + 不可恢复警告 + 确认文本输入）
- [ ] `ServerPropertiesEditor.vue`：表格式编辑器 Modal
- [ ] `PlayerManagementTab.vue`：在线玩家 + 白名单 + OP
- [ ] `OperationLogView.vue`：日志筛选 + 表格 + 分页 + 导出
- [ ] `UserPermissionView.vue`：用户权限管理（路径 ACL）
- [ ] `ConfigPropertyView.vue`：配置文件属性权限管理
- [ ] `DockerStatsCard.vue`：容器资源指标展示
- [ ] `api/files.ts`：文件管理 API 模块
- [ ] `api/players.ts`：玩家管理 API 模块
- [ ] `api/logs.ts`：操作日志 API 模块
- [ ] `api/permissions.ts`：权限管理 API 模块
- [ ] `api/types.ts` 扩展：新增类型定义

---

## 13. 后端交付清单

### Spring Boot 新增

- [ ] `UserPathPermission.java`：路径 ACL 实体
- [ ] `ConfigPropertyPermission.java`：配置属性权限实体
- [ ] `ProtectedResource.java`：受保护资源实体
- [ ] `OperationLog.java`：操作日志实体
- [ ] 对应 4 个 Repository
- [ ] `ProtectedResourceInitializer.java`：实例注册时自动初始化保护规则
- [ ] `ConfigPropertyInitializer.java`：预初始化 server.properties 属性权限
- [ ] `FileService.java` + `FileServiceImpl.java`：文件管理（含 ACL + 保护资源检查 + 日志）
- [ ] `PlayerService.java` + `PlayerServiceImpl.java`：玩家管理
- [ ] `PermissionService.java` + `PermissionServiceImpl.java`：路径 ACL + 配置属性 CRUD
- [ ] `OperationLogService.java` + `OperationLogServiceImpl.java`：日志记录/查询
- [ ] `DockerService.java`：Docker 容器监控
- [ ] `FilesController.java`：文件管理 REST API
- [ ] `PlayerController.java`：玩家管理 REST API
- [ ] `PermissionController.java`：权限管理 REST API
- [ ] `LogController.java`：操作日志查询/导出 API
- [ ] `AgentClient.java` 扩展：文件操作 + 玩家管理方法
- [ ] `AuthController.getMe()` 扩展：返回 `menus` 字段
- [ ] `InstanceServiceImpl.java` 扩展：创建实例时初始化保护规则
- [ ] `ErrorCode.java` 扩展：新增文件/玩家/权限相关错误码

### Go API 改动

- [ ] `handler/files.go`：`Delete()` 解析 `force` 参数
- [ ] `handler/files.go`：新增 `Mkdir()` / `Rename()` handler
- [ ] `service/files.go`：`DeleteFile()` 加 `force` + `IsProtected()`
- [ ] `service/files.go`：新增 `CreateDir()` / `RenameFile()`
- [ ] `main.go`：注册 mkdir / rename 路由
- [ ] `service/files_test.go`：force + protected 测试

---

## 14. 验证标准

| # | 验证项 | 预期结果 |
|---|--------|---------|
| 1 | ROOT 登录 | 侧边栏显示全部菜单（仪表盘/实例/Keys/日志/权限管理/用户管理） |
| 2 | ADMIN 登录 | 侧边栏仅显示仪表盘 + 服务器管理，仪表盘只展示绑定实例 |
| 3 | USER 登录 | 侧边栏仅显示仪表盘，纯状态展示无操作按钮 |
| 4 | ROOT 编辑 server.properties | 所有属性可编辑，表格式展示 |
| 5 | ADMIN 编辑 server.properties | motd/pvp 等可编辑，server-port/max-players 灰显不可编辑 |
| 6 | ROOT 设置用户路径权限 | a 用户 → mc1 → mods=true, world=false → 保存成功 |
| 7 | ADMIN a 上传 mod | mc1 的 mods 目录 → ACL can_manage=true → 上传成功 |
| 8 | ADMIN a 删除 world | mc1 → ACL can_manage=false → 403 |
| 9 | ADMIN 删除受保护资源 | 即使 ACL can_manage=true，WORLD 类型仍被 protected_resources 拒绝 |
| 10 | ROOT 强制删除 world | force=true + 二次确认 → 成功 |
| 11 | Go API 兜底 | 直接调 `DELETE /api/files/delete?path=world` 无 force → 403 |
| 12 | 玩家 kick | ADMIN 在绑定实例上 kick 玩家 → 玩家被踢出 |
| 13 | OP 管理 | ROOT 设置 OP → ops.json 更新；ADMIN 调 OP 接口 → 403 |
| 14 | 操作日志 | 文件删除后 → `/api/admin/logs` 可查到记录 |
| 15 | Docker 监控 | 实例详情页展示 CPU/内存/网络指标 |
| 16 | mkdir/rename | 文件管理页面创建目录 / 重命名文件 → 成功 |

---

## 15. 有意延后到 P4+ 的内容

| 内容 | 延后原因 |
|------|----------|
| 用户管理 UI（用户 CRUD 前端页面） | P2 已有后端 API，P3 通过 API/SQL 管理 |
| 受保护资源自定义管理 UI（Root 增删保护规则的前端） | P3 后端已支持，前端延后 |
| 批量文件操作（多选删除/下载/移动） | 交互复杂度较高 |
| nginx 反向代理 | 基础设施优化 |
| 多节点/集群管理 | 架构升级 |

---

## 16. 参考文档

- [[planning-v1]]：总体规划
- [[springboot-infra-plan]]：Spring Boot 基础设施方案（P2）
- [[frontend-plan]]：前端技术方案（P2）
- [[panel-design]]：权限与安全设计
- [[develop-process]]：开发过程文档