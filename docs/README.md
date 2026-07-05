# 文档索引

> Minecraft Server Docker 项目文档目录，按类型分为需求文档、开发文档、开发规范文档。

---

## 目录结构

```text
docs/
├── requirements/       # 需求文档
├── development/        # 开发文档
├── standards/          # 开发规范文档
├── README.md           # 文档索引（本文件）
└── architecture.md     # 项目架构文档
```

---

## 需求文档 (requirements/)

定义项目愿景、功能规划、权限模型与安全设计。

| 文档 | 说明 |
|------|------|
| [planning-v1.md](requirements/planning-v1.md) | **总体规划文档** — 项目蓝图，定义 P0~P4 优先级、功能路线图、技术方向。涵盖自动 JDK 选择、服务端版本解析、Go API 扩展、全栈基础设施、业务逻辑深化等各阶段目标 |
| [panel-design.md](requirements/panel-design.md) | **权限与安全设计** — 三层角色模型（ROOT / ADMIN / USER）、重要资源保护机制、双层安全校验（Spring Boot 业务权限 + Go API 底层兜底）、文件删除流程设计 |

---

## 开发文档 (development/)

记录技术实现方案、开发过程与实施细节。

| 文档 | 说明 |
|------|------|
| [develop-process.md](development/develop-process.md) | **Go API 开发过程文档** — 记录 9 个开发阶段（基础框架 → MC 管理 → SSE 日志 → 认证 → 文件管理 → Docker 集成 → Vue 测试 → JDK 自动选择 → 版本解析 + 模块化 + 测试），包含每个阶段的问题与解决方案、API 路由总览 |
| [springboot-infra-plan.md](development/springboot-infra-plan.md) | **Spring Boot 基础设施方案（P2）** — 项目结构、数据库设计（users / api_keys / server_instances / user_instances）、统一响应格式与错误码、全局异常处理、AES-256-GCM 静态加密、API Key 与实例管理 API、AgentClient 设计、Key 刷新机制 |
| [frontend-plan.md](development/frontend-plan.md) | **Vue 3 前端技术方案（P2）** — 技术选型（Vue 3 + Vite + Naive UI + TypeScript）、项目结构、路由设计、axios 拦截器与 ErrorCode 枚举、登录/仪表盘/Key 管理/实例管理页面交互设计、前后端联调约定 |
| [p3-plan.md](development/p3-plan.md) | **P3 业务逻辑深化方案** — 三层权限模型（角色边界 → 路径级 ACL → 资源保护兜底）、server.properties 表格式编辑器、动态路由与侧边栏、文件管理代理 API、受保护资源系统、玩家管理、操作审计日志、Docker SDK 资源监控、Go API 最小改动（3 处约 55 行） |

---

## 开发规范文档 (standards/)

项目代码编写规范与约定。

| 文档 | 说明 |
|------|------|
| *(待编写)* | 代码开发规范 |

---

## 项目架构文档

参见 [architecture.md](architecture.md) — 系统分层架构、数据流、部署模型、技术栈总览。

---

## 文档关系图

```text
requirements/planning-v1.md（总体规划）
    │
    ├── requirements/panel-design.md（权限与安全需求）
    │
    ├── development/develop-process.md（Go API 开发过程）
    │
    ├── development/springboot-infra-plan.md（P2 后端方案）
    │       │
    │       └── development/frontend-plan.md（P2 前端方案）
    │
    └── development/p3-plan.md（P3 业务深化方案）
            │
            └── requirements/panel-design.md（引用权限设计）
```

## 版本阶段对应

| 阶段 | 对应文档 | 核心内容 |
|------|---------|---------|
| **P0** | requirements/planning-v1.md §八 | 自动 JDK 选择、项目目录重构 |
| **P1** | requirements/planning-v1.md §八 + development/develop-process.md §第九阶段 | 服务端版本自动解析、Go API 模块化、单元测试 |
| **P2** | development/springboot-infra-plan.md + development/frontend-plan.md | 全栈基础设施（Spring Boot + Vue 3）|
| **P3** | development/p3-plan.md | 权限体系 + 文件管理 + 玩家管理 + 操作审计 |
| **P4** | requirements/planning-v1.md §P4 | 多节点/集群管理、nginx 反向代理 |
