# Minecraft Panel 前端技术方案

## 背景

P2 阶段需要完成 Spring Boot 基础设施 + Vue 前端初始化的全栈打通。前端负责提供 API Key 管理、实例管理的基础交互界面，与 Spring Boot 后端完成联调。

---

## 部署架构

P2 阶段采用 **同容器部署**：Spring Boot 内嵌 Vue 静态资源，单端口对外。

```text
Docker 容器（panel）
├── Spring Boot（:8080）
│   ├── /api/*          → REST API
│   └── /*              → Vue 静态资源（classpath:/static）
```

| 优势 | 说明 |
|------|------|
| 部署简单 | 一个 JAR = 一个容器 = 一个端口 |
| 无 CORS | 同源访问，不需要跨域配置 |
| 开发效率 | 前后端在一个项目里，调试方便 |
| P2 适合 | 先跑通链路，nginx 反向代理留到 P4+ |

| 公网部署 | 说明 |
|----------|------|
| 风险 | Spring Boot 直接暴露公网（与 Go API 现状一致） |
| 防护 | Spring Security 认证保护所有非登录接口 |
| 加固（P4+） | 加一层 nginx：SSL 终止 + 静态资源优化 + 反向代理 |

### 请求流

```text
浏览器
  ↓  http://<host>:8080/
Vue SPA（Spring Boot 返回 index.html）
  ↓  axios → /api/admin/*
Spring Boot Controller（Spring Security 校验）
  ↓  AgentClient → Go API
Go API → Minecraft Server
```

---

## 技术选型

| 项 | 选择 | 理由 |
|----|------|------|
| 框架 | Vue 3 (Composition API) | 生态好、上手快、TypeScript 支持 |
| 构建 | Vite | 比 Webpack 快一个数量级 |
| UI 组件库 | Naive UI | 适合管理面板风格、Tree-shaking、TypeScript 原生 |
| HTTP 客户端 | axios | 拦截器统一处理认证和错误 |
| 路由 | Vue Router 4 | SPA 页面切换 |
| 状态管理 | Pinia（按需引入） | 轻量，P2 阶段可能不需要全局状态 |
| 语言 | TypeScript | 类型安全，减少运行时错误 |
| 图标 | Naive UI 内置 / 或 @vicons/ionicons5 | 足够的图标选择 |

---

## 项目结构

```
panel/web/
├── index.html
├── package.json
├── vite.config.ts
├── tsconfig.json
├── src/
│   ├── main.ts                    # 入口
│   ├── App.vue                    # 根组件
│   │
│   ├── router/
│   │   └── index.ts               # 路由配置
│   │
│   ├── api/
│   │   ├── client.ts              # axios 实例（baseURL、拦截器）
│   │   ├── auth.ts                # 登录相关 API
│   │   ├── keys.ts                # API Key 管理 API
│   │   └── instances.ts           # 实例管理 API
│   │
│   ├── views/
│   │   ├── LoginView.vue          # 登录页
│   │   ├── DashboardView.vue      # 总览
│   │   ├── keys/
│   │   │   ├── KeyListView.vue    # Key 列表
│   │   │   └── KeyCreateView.vue  # 注册新 Key（弹窗或独立页）
│   │   └── instances/
│   │       ├── InstanceListView.vue     # 实例列表
│   │       ├── InstanceDetailView.vue   # 实例详情 + 控制台
│   │       └── InstanceCreateView.vue   # 注册新实例
│   │
│   ├── components/                # 公共组件
│   │   ├── AppLayout.vue          # 布局（侧边栏 + 顶栏 + 内容区）
│   │   ├── ConfirmDialog.vue      # 二次确认弹窗（refresh-key 等危险操作）
│   │   └── StatusBadge.vue        # 实例状态标签
│   │
│   └── utils/
│       └── format.ts              # 日期、Key 脱敏显示等工具函数
│
└── public/
    └── favicon.ico
```

---

## 路由设计

```typescript
const routes = [
    {
        path: '/login',
        component: () => import('@/views/LoginView.vue'),
        meta: { requiresAuth: false }
    },
    {
        path: '/',
        component: () => import('@/components/AppLayout.vue'),
        meta: { requiresAuth: true },
        children: [
            { path: '',           component: () => import('@/views/DashboardView.vue') },
            { path: 'keys',       component: () => import('@/views/keys/KeyListView.vue') },
            { path: 'instances',  component: () => import('@/views/instances/InstanceListView.vue') },
            { path: 'instances/:id', component: () => import('@/views/instances/InstanceDetailView.vue') },
        ]
    },
    { path: '/:pathMatch(.*)*', redirect: '/' }
];
```

---

## P2 页面与交互

### 1. 登录页

- 用户名/密码表单
- 登录成功 → 存储 JWT（localStorage）→ 跳转 Dashboard
- 登录失败 → 错误提示
- Spring Boot 提供 `POST /api/auth/login`（P2 先做简单的用户名密码认证）

### 2. Dashboard（总览）

- 卡片展示：已注册 Key 数量、实例数量、运行中实例数
- 快速操作入口：注册 Key、注册实例

### 3. API Key 管理

**列表页：**

- 表格展示：名称、key_preview（脱敏显示 `a1b2****...****7890`）、状态（active/revoked）、绑定实例、创建时间
- 操作：查看详情、吊销（二次确认）、删除（已绑定则提示先解绑）
- 顶部按钮：注册新 Key

**注册（弹窗形式）：**

- 名称输入框
- Key 值输入框（粘贴 Go API 生成的值）
- 格式校验（UUID v4）、唯一性校验（后端返回错误提示）

**吊销（二次确认弹窗）：**

- 提示："吊销后该 Key 将无法使用，已绑定的实例将失去连接。确认吊销？"
- 确认 → 调用 `POST /api/admin/keys/{id}/revoke`

### 4. 实例管理

**列表页：**

- 表格展示：名称、host:port、服务端类型、MC 版本、状态（含颜色标识）、创建时间
- 操作：查看详情、删除
- 顶部按钮：注册新实例 + 健康检查全部

**详情页（核心交互页）：**

- 基本信息卡片（名称、地址、类型、版本、状态）
- 绑定的 Key 信息（名称 + key_preview）
- 操作按钮组：
  - **刷新 API Key**（仅 Root 可见）：点击 → 二次确认弹窗（"此操作将使旧 Key 失效，确认刷新？"）→ 确认后调 `PUT .../refresh-key` → 成功提示 + 更新显示
  - **更换绑定 Key**：下拉选择已有 active Key → 确认
  - **健康检查**：点击 → 调 `/health` → 显示结果（成功/失败 + 延迟）
- 控制台区域（P2 做基础版）：
  - 发送指令输入框 + 发送按钮（调 `/api/command`）
  - 指令历史列表
  - 服务端启停按钮（start/stop/restart）

**注册（独立页或弹窗）：**

- 表单：名称、host、port（默认 25560）、server_type 下拉（vanilla/forge/fabric/neoforge/paper/purpur）、mc_version
- API Key 选择器（下拉选择已有的 active 且未绑定的 Key）
- 提交 → 成功后跳转列表

---

## axios 客户端设计

```typescript
// api/client.ts
const apiClient = axios.create({
    baseURL: '/api',
    timeout: 30000,
});

// 请求拦截器：自动附加 JWT
apiClient.interceptors.request.use(config => {
    const token = localStorage.getItem('token');
    if (token) {
        config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
});

// 响应拦截器：统一错误处理
apiClient.interceptors.response.use(
    response => response,
    error => {
        if (error.response?.status === 401) {
            localStorage.removeItem('token');
            router.push('/login');
        }
        return Promise.reject(error);
    }
);
```

**关键：** 前端不处理 Go API Key。所有 /api/* 请求发往同源的 Spring Boot，由 AgentClient 在服务端携带 Go API Key 转发。

---

## P2 前端交付清单

- [ ] Vue 3 + Vite + TypeScript 项目初始化
- [ ] Naive UI 集成 + 全局主题配置
- [ ] 路由配置 + 导航守卫（JWT 认证）
- [ ] axios 客户端 + 拦截器
- [ ] LoginView（登录页）
- [ ] AppLayout（侧边栏导航：Dashboard / API Keys / Instances）
- [ ] DashboardView（总览卡片）
- [ ] KeyListView + KeyCreate（弹窗）+ Revoke（二次确认弹窗）
- [ ] InstanceListView + InstanceCreate（弹窗）
- [ ] InstanceDetailView（详情 + 刷新 Key + 更换 Key + 健康检查 + 指令控制台）
- [ ] 构建产物输出到 `panel/backend/src/main/resources/static/`
- [ ] 与 Spring Boot 联调，全链路通过

---

## 与 Spring Boot 的联调约定

| 约定项 | 说明 |
|--------|------|
| 静态资源位置 | Vite build → `panel/backend/src/main/resources/static/` |
| 路由 fallback | Spring Boot 将所有非 `/api/*` 的 GET 请求 fallback 到 `index.html`（Vue SPA） |
| API 前缀 | 所有后端接口统一 `/api/*`，避免与前端路由冲突 |
| JWT | Spring Boot 签发，前端存储在 localStorage，请求时 Bearer 携带 |
| 错误格式 | 统一 `{ "code": number, "status": "ok"|"error", "message": string, "data": any }` |

---

## 参考文档

- [[springboot-infra-plan]]：Spring Boot 基础设施方案
- [[planning-v1]]：总体规划
- [[panel-design]]：权限与安全设计
