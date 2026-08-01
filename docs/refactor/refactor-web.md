# Minecraft Panel Web 多端 UI 重构方案

## 1. 多端 UI 设计目标

支持：

| 终端    | 定位       |
| ------- | ---------- |
| Desktop | 完整管理端 |
| Tablet  | 轻量管理端 |
| Mobile  | 运维监控端 |

原则：

```
同一套业务逻辑
不同端展示策略
```

不要：

```
PC 页面缩小到手机
```

而应该：

```
PC Layout
Tablet Layout
Mobile Layout
```

------

# 2. 前端整体架构调整

原：

```
AppLayout
 |
 Sidebar
 |
 Content
```

调整为：

```
Application

 |
 |
Device Detector

 |
 +----------------+
 |                |
Desktop Layout    Mobile Layout

 |
 |
Permission Engine

 |
 |
Business Components
```

------

# 3. Layout 分离设计

目录：

```
src/layouts

├── desktop
│   ├── DesktopLayout.vue
│   ├── Sidebar.vue
│   └── Header.vue
│

├── tablet
│   └── TabletLayout.vue
│

└── mobile
    ├── MobileLayout.vue
    └── BottomNavigation.vue
```

------

# 4. Desktop UI

目标：

完整控制台。

例如：

```
+------------------------------------------------+

 Logo

+--------+---------------------------------------+
|        |
| Menu   | Survival Server
|        |
|        | CPU
|        |
|        | Memory
|        |
|        | Console
|        |
+--------+---------------------------------------+
```

适合：

- 文件管理
- 配置编辑
- 权限管理
- Docker 监控

------

# 5. Mobile UI

手机不要保留侧边栏。

改：

```
+----------------+

 Survival


 Online


 CPU 32%


 Memory 4GB



 [启动]


 [控制台]


 [玩家]


+----------------+

       ⌂  🎮  📜  ⚙
```

底部导航：

```
Dashboard

Servers

Console

Profile
```

------

# 6. 页面响应策略

不要简单：

```
display:none
```

而应该：

组件级适配。

例如：

Desktop:

```
ServerCard

----------------

CPU
Memory
Players
TPS

按钮组
```

Mobile:

```
ServerCard

状态

玩家

操作

展开更多
```

------

# 7. Component 设计规范

所有核心组件必须支持：

```
Desktop Renderer

Mobile Renderer
```

例如：

目录：

```
components

ServerCard

├── ServerCard.vue

├── ServerCardDesktop.vue

└── ServerCardMobile.vue
```

入口：

```
<ServerCard />
```

内部：

```
if(device.desktop)

 return Desktop

else

 return Mobile
```

------

# 8. 使用 CSS 响应式还是组件响应式？

你的项目不要完全依赖 CSS。

原因：

你的页面复杂：

- 文件管理
- 表格
- Console
- 权限树

例如：

文件管理：

PC：

```
文件树 | 文件内容
```

手机：

```
文件列表

↓

点击进入

↓

文件内容
```

这不是 CSS 能解决的问题。

所以：

采用：

```
CSS Responsive

+
Component Responsive
```

------

# 9. 断点设计

统一：

```
breakpoints.ts


mobile

<768px


tablet

768-1200px


desktop

>1200px
```

------

# 10. 动态菜单适配

你的权限菜单：

之前：

```
Sidebar
```

改：

```
Navigation Provider

        |

        + Desktop Sidebar

        |

        + Mobile BottomNav
```

例如后端返回：

```
{
 menus:[
   {
    name:"Servers",
    icon:"server",
    path:"/servers",
    permission:"server:view"
   }
 ]
}
```

Desktop：

生成：

```
Sidebar
```

Mobile：

生成：

```
BottomNavigation
```

------

# 11. 表格移动端处理

你的很多页面：

例如：

操作日志：

PC:

```
|User|Action|Resource|Time|
```

手机不要横向滚动。

改：

Card List:

```
----------------

User:

Tom


Action:

DELETE


Resource:

world


Time:

12:30

----------------
```

------

# 12. Console 页面适配

Minecraft 控制台非常重要。

PC:

```
--------------------------------

> say hello


server started


TPS 20


--------------------------------

command input
```

手机：

```
日志

server started


[输入命令]
```

输入框固定底部。

------

# 13. 文件管理移动端

PC:

```
Tree     Editor
```

Mobile:

```
Directory


world


 ↓


region


 ↓


file
```

类似手机文件管理器。

------

# 14. 主题系统需要支持多端

主题不能只提供：

```
Desktop CSS
```

应该：

```
theme

├── desktop

├── tablet

└── mobile
```

例如：

二次元主题：

PC:

大背景插画

Mobile:

降低背景透明度

------

# 15. PWA 支持（推荐）

你的项目很适合 PWA。

以后：

手机：

```
添加到桌面

↓

像 APP 一样打开
```

支持：

- 快速查看状态
- 推送通知
- 离线缓存

技术：

```
Vite PWA Plugin

Service Worker

Web Notification API
```

------

# 16. 前端最终架构

调整后：

```
panel-web


src


├── api


├── layouts

│   ├── desktop

│   ├── tablet

│   └── mobile


├── components

│   ├── common

│   ├── desktop

│   └── mobile


├── permissions


├── themes


├── router


├── stores


├── views


└── utils
```

------

# 17. 重构阶段调整

之前：

```
Phase 1
UI 重构

Phase 2
权限
```

建议改：

## Phase 1：多端基础架构

完成：

- Vue3 + TS
- Layout 系统
- Responsive Engine
- Design System
- Theme 基础

------

## Phase 2：权限驱动 UI

完成：

- RBAC
- Dynamic Router
- Permission Component

------

## Phase 3：业务页面

完成：

- Server Dashboard
- Console
- Files
- Config
- Players

------

## Phase 4：平台能力

完成：

- Theme Market
- PWA
- Plugin UI