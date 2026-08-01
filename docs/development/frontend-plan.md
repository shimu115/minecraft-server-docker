# Minecraft Panel 前端技术方案（P3 多端重构）

## 概述

P3 阶段的核心任务是将管理面板从**纯桌面端**改造为**桌面 + 移动端双端适配**。

**动机**：Minecraft 服务器运维不只是坐在电脑前的操作——服务器崩了、玩家出问题了，管理员更希望能掏出手机快速看一眼状态、发条重启指令。P2 已经搭好了 API 全链路，P3 让前端适配多端，就能覆盖这两种典型场景：

| 场景 | 终端 | 做什么 |
|------|------|--------|
| 日常管理 | PC 浏览器 | 编辑配置文件、管理权限、浏览文件目录、操作日志审计 |
| 应急运维 | 手机浏览器 | 看一眼服务器状态、启动/停止/重启、控制台发指令、查看在线玩家 |

**做法**：不是把 PC 页面缩小塞进手机，而是同一套业务逻辑配两套渲染器——Desktop 的侧边栏布局和 Mobile 的底部导航卡片布局各自独立设计。技术栈延续 P2 的 Vue 3 + Naive UI + TypeScript，在上层做渐进式改造，不改动后端。

**范围**：

- 新增设备检测基础设施（`useDevice` 组合式函数）
- 拆分 Desktop / Mobile 双 Layout
- 核心页面添加 Mobile 渲染变体（控制台、表格→卡片、文件管理）
- Navigation 系统从硬编码菜单改为配置驱动
- PWA 支持作为远期目标

---

## 背景

P2 阶段已完成 Vue 3 + Spring Boot 全栈打通：登录认证、API Key 管理、实例管理的基础交互界面已就绪，前端与后端联调通过。

P3 阶段的核心目标是**多端适配重构**：同一套业务逻辑支撑 Desktop（完整管理端）与 Mobile（运维监控端）两种使用场景，同时保持现有功能不受影响。

### P2 已交付

| 模块 | 状态 |
|------|------|
| Vue 3 + Vite + TypeScript 项目骨架 | ✅ |
| Naive UI 集成 | ✅ |
| axios 客户端（JWT 拦截器 + ErrorCode 解包） | ✅ |
| 路由 + 导航守卫 | ✅ |
| LoginView / DashboardView | ✅ |
| KeyListView（表格 + 注册弹窗 + 吊销确认） | ✅ |
| InstanceListView / InstanceDetailView | ✅ |
| AppLayout（顶栏 + 侧边栏 + 内容区） | ✅ |
| 构建产物输出到 `backend/src/main/resources/static/` | ✅ |

### P3 变更范围

```
P2（当前）                        P3（目标）
─────────────                    ─────────────
单一 Desktop 布局          →     Desktop + Mobile 双布局
硬编码 Sidebar 菜单         →     NavigationProvider 动态适配
所有页面仅适配 PC           →     核心页面添加 Mobile 变体
无响应式基础设施             →     useDevice / breakpoints
Naive UI 默认主题            →     多端主题系统
```

---

## 多端设计目标

| 终端 | 定位 | 典型场景 |
|------|------|---------|
| Desktop (>768px) | 完整管理端 | 文件管理、配置编辑、权限管理、Docker 监控 |
| Mobile (≤768px) | 运维监控端 | 查看服务器状态、启动/停止、控制台发指令、查看玩家 |

**Tablet 不设独立布局**，由 Desktop 布局 + CSS 响应式覆盖。768-1200px 范围内侧边栏可折叠、表格列可缩减，但仍然是同一套 Desktop 组件。

核心原则：

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
Desktop 组件 → 提供 Desktop 渲染器
Mobile 组件  → 提供 Mobile 渲染器
```

---

## 1. 设备检测基础设施

### 1.1 断点定义

```typescript
// src/composables/breakpoints.ts

/** 断点常量 —— 与 CSS 变量保持同步 */
export const BREAKPOINTS = {
  mobile: 768,   // < 768px  → Mobile
  desktop: 768,  // ≥ 768px  → Desktop
} as const;

/** CSS 断点（配合 Naive UI 或独立使用） */
export const MEDIA_QUERIES = {
  mobile: '(max-width: 767px)',
  desktop: '(min-width: 768px)',
} as const;
```

### 1.2 useDevice 组合式函数

```typescript
// src/composables/useDevice.ts

import { ref, onMounted, onUnmounted } from 'vue';
import { BREAKPOINTS } from './breakpoints';

export type DeviceType = 'mobile' | 'desktop';

const deviceType = ref<DeviceType>('desktop');
const windowWidth = ref(window.innerWidth);
const windowHeight = ref(window.innerHeight);

function update() {
  windowWidth.value = window.innerWidth;
  windowHeight.value = window.innerHeight;
  deviceType.value = window.innerWidth < BREAKPOINTS.mobile ? 'mobile' : 'desktop';
}

let listening = false;

export function useDevice() {
  if (!listening) {
    update();
    window.addEventListener('resize', update);
    listening = true;
  }

  // cleanup 由第一个调用方的 onUnmounted 处理
  // 实际上应该确保至少有一个持久的 listener
  // 全局单例模式：第一次调用注册，模块级生命周期

  const isMobile = computed(() => deviceType.value === 'mobile');
  const isDesktop = computed(() => deviceType.value === 'desktop');

  return {
    deviceType,       // Ref<'mobile' | 'desktop'>
    isMobile,         // ComputedRef<boolean>
    isDesktop,        // ComputedRef<boolean>
    windowWidth,      // Ref<number>
    windowHeight,     // Ref<number>
  };
}
```

> **实现要点**：`useDevice` 采用模块级单例 —— `deviceType` 是模块作用域的 ref，所有组件共享同一个响应式状态和一个 resize 监听器，避免重复绑定。

### 1.3 用法

```vue
<script setup lang="ts">
import { useDevice } from '@/composables/useDevice';

const { isMobile, isDesktop, deviceType } = useDevice();
</script>

<template>
  <!-- 条件渲染不同布局 -->
  <DesktopLayout v-if="isDesktop" />
  <MobileLayout v-else />
</template>
```

组件内部：

```vue
<script setup lang="ts">
import { useDevice } from '@/composables/useDevice';
import ServerCardDesktop from './ServerCardDesktop.vue';
import ServerCardMobile from './ServerCardMobile.vue';

const { isDesktop } = useDevice();
</script>

<template>
  <ServerCardDesktop v-if="isDesktop" v-bind="$props" />
  <ServerCardMobile v-else v-bind="$props" />
</template>
```

---

## 2. Layout 系统

### 2.1 架构

```
App.vue
  │
  ├─ useDevice()  ← 模块级单例，全局共享
  │
  ├─ DesktopLayout.vue        (≥ 768px)
  │   ├─ Header.vue           (用户信息 + 退出)
  │   ├─ Sidebar.vue          (导航菜单)
  │   └─ <router-view />
  │
  └─ MobileLayout.vue         (< 768px)
      ├─ Header.vue           (简化顶栏 + 标题)
      ├─ <router-view />
      └─ BottomNavigation.vue (底部标签栏)
```

路由树不变，仍是同一套路由配置。差异只在哪个 Layout 组件包裹 `<router-view />`。

### 2.2 DesktopLayout

沿用当前 `AppLayout.vue` 的整体结构（`n-layout` + `n-layout-sider` + `n-layout-content`），但将菜单数据源从硬编码改为由 NavigationProvider 注入：

```vue
<!-- src/layouts/DesktopLayout.vue -->
<template>
  <n-layout style="height: 100vh">
    <n-layout-header bordered style="height: 56px; display: flex; align-items: center; padding: 0 24px; justify-content: space-between">
      <div style="font-size: 18px; font-weight: 600">MC Panel</div>
      <n-space align="center">
        <n-text>{{ userInfo?.username }}</n-text>
        <n-tag size="small">{{ userInfo?.role }}</n-tag>
        <n-button text @click="handleLogout">退出</n-button>
      </n-space>
    </n-layout-header>

    <n-layout has-sider style="flex: 1">
      <n-layout-sider bordered width="200" style="padding-top: 12px">
        <SidebarMenu :items="menuItems" />
      </n-layout-sider>

      <n-layout-content style="padding: 24px; background: #f5f5f5; overflow: auto">
        <router-view />
      </n-layout-content>
    </n-layout>
  </n-layout>
</template>
```

### 2.3 MobileLayout

```vue
<!-- src/layouts/MobileLayout.vue -->
<template>
  <n-layout style="height: 100vh; display: flex; flex-direction: column">
    <n-layout-header bordered style="height: 48px; display: flex; align-items: center; padding: 0 16px; flex-shrink: 0">
      <div style="font-size: 16px; font-weight: 600">MC Panel</div>
      <n-space align="center" style="margin-left: auto">
        <n-button size="small" text @click="handleLogout">退出</n-button>
      </n-space>
    </n-layout-header>

    <n-layout-content style="flex: 1; overflow-y: auto; padding: 12px; background: #f5f5f5">
      <router-view />
    </n-layout-content>

    <BottomNavigation :items="menuItems" style="flex-shrink: 0" />
  </n-layout>
</template>
```

### 2.4 App.vue 调整

```vue
<!-- src/App.vue -->
<template>
  <n-message-provider>
    <DesktopLayout v-if="isDesktop" />
    <MobileLayout v-else />
  </n-message-provider>
</template>

<script setup lang="ts">
import { NMessageProvider } from 'naive-ui';
import { useDevice } from '@/composables/useDevice';
import DesktopLayout from '@/layouts/DesktopLayout.vue';
import MobileLayout from '@/layouts/MobileLayout.vue';

const { isDesktop } = useDevice();
</script>
```

> **注意**：登录页 (`/login`) 不经过 Layout，路由设计上将 `/login` 保持为独立路由（不是 Layout 的子路由），在 Desktop 和 Mobile 下都直接全屏渲染。路由守卫逻辑不变。

---

## 3. 路由策略

### 3.1 路由树（不变）

```typescript
// src/router/index.ts —— 整体结构不变
const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/LoginView.vue'),
    meta: { requiresAuth: false },
  },
  {
    path: '/',
    // P3 改为重定向，不再直接挂 AppLayout
    redirect: '/dashboard',
  },
  {
    path: '/dashboard',
    component: () => import('@/views/DashboardView.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/keys',
    component: () => import('@/views/keys/KeyListView.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/instances',
    component: () => import('@/views/instances/InstanceListView.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/instances/:id',
    component: () => import('@/views/instances/InstanceDetailView.vue'),
    props: true,
    meta: { requiresAuth: true },
  },
  { path: '/:pathMatch(.*)*', redirect: '/dashboard' },
];
```

### 3.2 关键改动

P2 中 Layout 是路由树的一部分（`path: '/'` 的 component 是 `AppLayout`，页面是 children）。P3 改为**平铺路由 + App.vue 控制 Layout**：

| 方案 | P2 | P3 |
|------|----|----|
| Layout 挂载点 | 路由 component | App.vue 根据 `useDevice` 决定 |
| 路由结构 | Layout 为父路由 + children | 页面路由平铺 |
| 优点 | 简单 | Layout 可独立切换，路由不感知设备 |

> **迁移说明**：此改动不影响已有页面的 URL，`/dashboard`、`/keys`、`/instances/:id` 路径保持不变。仅内部路由结构调整。

---

## 4. 组件响应式模式

### 4.1 决策矩阵

```
┌──────────────────────┬────────────────────┬────────────────────────┐
│ 场景                  │ 策略               │ 实现方式                │
├──────────────────────┼────────────────────┼────────────────────────┤
│ 间距/字号/列数变化     │ CSS 响应式         │ @media / Naive UI 响应式 │
│ 布局结构变化           │ Component 响应式    │ Desktop/Mobile 两个组件  │
│ 交互模式变化           │ Component 响应式    │ 如：表格 → 卡片列表      │
│ 信息密度变化           │ Component 响应式    │ 如：完整表单 → 精简字段  │
└──────────────────────┴────────────────────┴────────────────────────┘
```

**典型判断标准**：如果你需要 `v-if` 来切换两块 DOM 结构，那就是 Component 响应式；如果只是调整 `flex-direction`、`font-size`、`gap`，那就是 CSS 响应式。

### 4.2 具体场景分类

| 组件 / 页面 | Desktop | Mobile | 策略 |
|-------------|---------|--------|------|
| Dashboard 卡片 | 横向排列 4 列 | 纵向堆叠 2 列 | CSS 响应式 |
| 数据表格（Key 列表等） | `<n-data-table>` 完整列 | Card List（每行变卡片） | Component 响应式 |
| 实例详情 | 双栏信息卡片 + 控制台 | 单栏 + 操作按钮固定底部 | CSS + Component |
| 控制台 | 日志区 + 输入框并排 | 日志全屏 + 输入框固定底部 | Component 响应式 |
| 文件管理 | 文件树 + 编辑器分栏 | 单栏逐级导航 | Component 响应式 |
| 登录页 | 居中卡片 | 居中卡片（宽度自适应） | CSS 响应式 |
| 确认弹窗 | Naive UI Modal | Naive UI Modal（宽度自适应） | CSS 响应式 |

### 4.3 目录组织

采用**按功能分组 + 设备变体 co-located**：

```
src/components/
├── common/                    # 设备无关的通用组件
│   ├── ConfirmDialog.vue      # （已有）
│   └── StatusBadge.vue        # （已有）
│
├── ServerCard/                # 按功能分目录
│   ├── ServerCard.vue         # 入口：根据 device 选择渲染器
│   ├── ServerCardDesktop.vue  # Desktop 渲染器
│   └── ServerCardMobile.vue   # Mobile 渲染器
│
├── CommandConsole/
│   ├── CommandConsole.vue     # 入口 + 共享逻辑 (sendCommand, history)
│   ├── CommandConsoleDesktop.vue
│   └── CommandConsoleMobile.vue
│
├── DataView/                  # 表格/卡片自适应组件
│   ├── DataView.vue           # 入口 + 共享数据逻辑
│   ├── DataTableDesktop.vue   # Desktop: <n-data-table>
│   └── DataCardListMobile.vue # Mobile: 卡片列表
│
└── FileManager/
    ├── FileManager.vue        # 入口 + 共享文件操作逻辑
    ├── FileManagerDesktop.vue # 双栏：文件树 + 编辑器
    └── FileManagerMobile.vue  # 单栏：列表 → 点击进入 → 内容
```

### 4.4 入口组件模板

```vue
<!-- src/components/ServerCard/ServerCard.vue -->
<script setup lang="ts">
import { useDevice } from '@/composables/useDevice';
import ServerCardDesktop from './ServerCardDesktop.vue';
import ServerCardMobile from './ServerCardMobile.vue';

// 共享的 props —— Desktop 和 Mobile 接收相同的接口
interface Props {
  serverId: string;
  name: string;
  status: 'online' | 'offline' | 'starting';
  cpu: number;
  memory: number;
  players: number;
}

const props = defineProps<Props>();
const emit = defineEmits<{
  start: [];
  stop: [];
  openConsole: [];
}>();

// 共享的业务逻辑：启动/停止/跳转
// Desktop 和 Mobile 只需要关心渲染

const { isDesktop } = useDevice();
</script>

<template>
  <ServerCardDesktop
    v-if="isDesktop"
    v-bind="props"
    @start="emit('start')"
    @stop="emit('stop')"
    @open-console="emit('openConsole')"
  />
  <ServerCardMobile
    v-else
    v-bind="props"
    @start="emit('start')"
    @stop="emit('stop')"
    @open-console="emit('openConsole')"
  />
</template>
```

> **设计决策**：业务逻辑（API 调用、状态管理、事件处理）放在入口组件或 Pinia store 中，Desktop/Mobile 变体是**纯渲染组件**，只负责 UI 展示。这样两个变体零业务耦合，可以独立测试和修改。

---

## 5. 导航系统

### 5.1 设计

当前 P2 的菜单硬编码在 `AppLayout.vue` 中。P3 抽取为 NavigationProvider，让 Desktop Sidebar 和 Mobile BottomNavigation 消费同一份菜单数据。

```typescript
// src/navigation/menuConfig.ts

export interface MenuItem {
  key: string;
  label: string;
  icon: string;          // Naive UI 图标名 或 icon 组件名
  path: string;
  permission?: string;   // 关联权限码，用于权限过滤
  mobileVisible?: boolean; // 移动端是否显示（默认 true）
  desktopVisible?: boolean; // 桌面端是否显示（默认 true）
}

export const menuConfig: MenuItem[] = [
  {
    key: 'dashboard',
    label: '仪表盘',
    icon: 'dashboard',
    path: '/dashboard',
  },
  {
    key: 'keys',
    label: 'API Keys',
    icon: 'key',
    path: '/keys',
    mobileVisible: false,       // Key 管理仅在桌面端操作
  },
  {
    key: 'instances',
    label: '实例管理',
    icon: 'server',
    path: '/instances',
  },
];
```

### 5.2 消费方式

```vue
<!-- Desktop Sidebar -->
<script setup lang="ts">
import { computed } from 'vue';
import { useDevice } from '@/composables/useDevice';
import { menuConfig } from '@/navigation/menuConfig';

const { isDesktop } = useDevice();

const visibleItems = computed(() =>
  menuConfig.filter(item => item.desktopVisible !== false)
);
</script>
```

```vue
<!-- Mobile BottomNavigation -->
<script setup lang="ts">
import { computed } from 'vue';
import { menuConfig } from '@/navigation/menuConfig';

const visibleItems = computed(() =>
  menuConfig.filter(item => item.mobileVisible !== false)
);
</script>
```

> **权限联动**：Phase 2 实现 RBAC 后，`visibleItems` 的计算逻辑中加入权限判断 —— 用户没有 `server:view` 权限则不显示对应菜单项。当前 P3 Phase 1 先保留简单的 `mobileVisible`/`desktopVisible` 字段。

---

## 6. 关键页面适配方案

### 6.1 数据表格 → Mobile Card List

PC 端用 `<n-data-table>` 展示完整列，Mobile 端将每行数据渲染为卡片：

```
Desktop:                          Mobile:
┌──────────────────────────┐      ┌─────────────────┐
│ Name │ Status │ Time     │      │ Tom             │
├──────┼────────┼──────────┤      │ Status: Online  │
│ Tom  │ Online │ 12:30    │      │ Time: 12:30     │
│ Jack │ Offline│ 11:00    │      ├─────────────────┤
└──────────────────────────┘      │ Jack            │
                                  │ Status: Offline │
                                  │ Time: 11:00     │
                                  └─────────────────┘
```

实现：`DataView` 组件封装 `<n-data-table>` 和 Card List 切换逻辑，使用时只需传入 `columns`（Desktop 用）和 `cardFields`（Mobile 用）。

### 6.2 控制台页面

```
Desktop:                          Mobile:
┌─────────────────────────┐       ┌─────────────────┐
│ Log Area                │       │ Log Area        │
│                         │       │ (全屏滚动)       │
│ > TPS 20                │       │ > TPS 20        │
│ > server started        │       │ > server started │
├─────────────────────────┤       ├─────────────────┤
│ [________________] Send │       │ [________] 发送  │
└─────────────────────────┘       └─── 固定底部 ────┘
```

Mobile 输入框使用 `position: fixed; bottom: 0` 固定在键盘上方。

### 6.3 文件管理

```
Desktop:                         Mobile:
┌──────┬──────────────┐          ┌───────────┐
│ Tree │ Editor       │          │ /world    │  ← 目录列表
│      │              │          │   region/ │
│ /    │              │     ↓    │   data/   │
│ world│ content...   │          ├───────────┤
│ data │              │     ↓    │ r.0.0.mca │  ← 点击进入文件内容
│      │              │          │ content.. │
└──────┴──────────────┘          └───────────┘
```

Mobile 端模拟手机文件管理器的逐级导航模式：目录列表 → 点击进入子目录 → 点击文件查看内容。用路由参数或嵌套 `<router-view>` 实现导航栈。

### 6.4 Dashboard

Desktop 用 CSS Grid 4 列，Tablet 3 列，Mobile 2 列 —— 纯 CSS 响应式，不需要 Component 响应式：

```css
.dashboard-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}

@media (max-width: 1200px) {
  .dashboard-grid { grid-template-columns: repeat(3, 1fr); }
}

@media (max-width: 768px) {
  .dashboard-grid { grid-template-columns: repeat(2, 1fr); }
}
```

---

## 7. 主题系统

基于 Naive UI 的 `NConfigProvider` + `themeOverrides`，按设备提供主题差异：

```
src/themes/
├── index.ts              # 主题入口，导出桌面/移动主题配置
├── shared.ts             # 共用的基础 token（品牌色、字体等）
├── desktop.ts            # 桌面主题 overrides（大插画背景等）
└── mobile.ts             # 移动主题 overrides（降低透明度、增大触控区域等）
```

```typescript
// src/themes/index.ts
import { useDevice } from '@/composables/useDevice';
import { desktopThemeOverrides } from './desktop';
import { mobileThemeOverrides } from './mobile';

export function useThemeOverrides() {
  const { isDesktop } = useDevice();
  return computed(() =>
    isDesktop.value ? desktopThemeOverrides : mobileThemeOverrides
  );
}
```

在 App.vue 中用 `NConfigProvider` 注入：

```vue
<template>
  <n-config-provider :theme-overrides="themeOverrides">
    <n-message-provider>
      <DesktopLayout v-if="isDesktop" />
      <MobileLayout v-else />
    </n-message-provider>
  </n-config-provider>
</template>
```

---

## 8. 项目最终目录结构

```
panel/web/
├── index.html
├── package.json
├── vite.config.ts
├── tsconfig.json
├── src/
│   ├── main.ts
│   ├── App.vue
│   │
│   ├── api/                        # 不变
│   │   ├── types.ts
│   │   ├── client.ts
│   │   ├── auth.ts
│   │   ├── keys.ts
│   │   ├── instances.ts
│   │   └── server.ts
│   │
│   ├── composables/                # P3 新增
│   │   ├── useDevice.ts            # 设备检测
│   │   └── breakpoints.ts          # 断点常量
│   │
│   ├── layouts/                    # P3 新增（替代原 AppLayout）
│   │   ├── DesktopLayout.vue
│   │   ├── MobileLayout.vue
│   │   ├── DesktopSidebar.vue
│   │   ├── MobileBottomNav.vue
│   │   └── Header.vue
│   │
│   ├── navigation/                 # P3 新增
│   │   └── menuConfig.ts           # 菜单配置（双端共用）
│   │
│   ├── components/
│   │   ├── common/                 # 设备无关组件
│   │   │   ├── ConfirmDialog.vue
│   │   │   └── StatusBadge.vue
│   │   │
│   │   ├── ServerCard/             # 按功能 + 设备变体
│   │   │   ├── ServerCard.vue
│   │   │   ├── ServerCardDesktop.vue
│   │   │   └── ServerCardMobile.vue
│   │   │
│   │   ├── CommandConsole/
│   │   │   ├── CommandConsole.vue
│   │   │   ├── CommandConsoleDesktop.vue
│   │   │   └── CommandConsoleMobile.vue
│   │   │
│   │   ├── DataView/
│   │   │   ├── DataView.vue
│   │   │   ├── DataTableDesktop.vue
│   │   │   └── DataCardListMobile.vue
│   │   │
│   │   └── FileManager/
│   │       ├── FileManager.vue
│   │       ├── FileManagerDesktop.vue
│   │       └── FileManagerMobile.vue
│   │
│   ├── router/
│   │   └── index.ts
│   │
│   ├── stores/                     # Pinia（需要时添加）
│   │
│   ├── themes/                     # P3 新增
│   │   ├── index.ts
│   │   ├── shared.ts
│   │   ├── desktop.ts
│   │   └── mobile.ts
│   │
│   ├── views/                      # 页面（结构不变）
│   │   ├── LoginView.vue
│   │   ├── DashboardView.vue
│   │   ├── keys/
│   │   │   └── KeyListView.vue
│   │   └── instances/
│   │       ├── InstanceListView.vue
│   │       └── InstanceDetailView.vue
│   │
│   └── utils/
│       └── format.ts
│
└── public/
    └── favicon.ico
```

---

## 9. 迁移路径

P2 → P3 采用**渐进式迁移**，每步不破坏已有功能：

### Step 1：铺设基础设施（不改变任何页面外观）

- 新增 `src/composables/useDevice.ts` + `breakpoints.ts`
- 新增 `src/navigation/menuConfig.ts`
- 新增 `src/themes/` 目录（先用与当前一致的默认值）
- 此时 `AppLayout.vue` 仍然工作，所有页面不变

### Step 2：拆分 Layout

- 创建 `src/layouts/DesktopLayout.vue`（功能等价于当前 `AppLayout.vue`）
- 创建 `src/layouts/MobileLayout.vue`
- 修改 `App.vue`：用 `useDevice` 切换两个 Layout
- 修改路由：页面路由平铺（不再作为 Layout 的 children）
- **验收点**：Desktop 行为与 P2 完全一致，Mobile 能看到基本布局框架

### Step 3：适配核心页面

- 实现组件响应式模式（ServerCard、DataView 等）
- 将 KeyListView、InstanceListView 的表格改为 DataView 组件
- InstanceDetailView 的控制台适配 Mobile
- **验收点**：Desktop 和 Mobile 各核心页面可用

### Step 4：打磨与测试

- 在真实手机 / Chrome DevTools 中测试全流程
- 主题调优
- CSS 响应式细节（间距、字号、触控区域）

---

## 10. 实施阶段

### Phase 1：多端基础架构

| 任务 | 产出 |
|------|------|
| `useDevice` + `breakpoints` | `src/composables/` |
| DesktopLayout + MobileLayout | `src/layouts/` |
| Navigation 系统（menuConfig + Sidebar + BottomNav） | `src/navigation/`、替代硬编码菜单 |
| 路由平铺化 | `src/router/` |
| 主题入口框架 | `src/themes/` |
| App.vue 切换逻辑 | `App.vue` |

### Phase 2：核心页面适配

| 任务 | 产出 |
|------|------|
| DataView 组件（表格 / 卡片自适应） | `src/components/DataView/` |
| ServerCard 多端组件 | `src/components/ServerCard/` |
| CommandConsole 多端组件 | `src/components/CommandConsole/` |
| DashboardView 响应式 | CSS Grid 响应式 |
| KeyListView + InstanceListView → DataView | 替换 `<n-data-table>` |
| InstanceDetailView 控制台 Mobile 适配 | Component 响应式 |

### Phase 3：文件管理 + 高级页面

| 任务 | 产出 |
|------|------|
| FileManager 多端组件 | `src/components/FileManager/` |
| RBAC 权限联动菜单 | NavigationProvider 集成权限 |
| 主题完善（Mobile 触控优化等） | `src/themes/` |

### Phase 4（远期）：平台能力

| 任务 | 说明 |
|------|------|
| PWA | `vite-plugin-pwa` + Service Worker + manifest.json |
| 推送通知 | Web Notification API（需后端配合 Web Push） |
| 主题市场 | 远期愿景，P4 仅做技术评估 |

---

## 11. 与原 refactor-web.md 的差异说明

| 差异点 | 原方案 | 整合后方案 |
|--------|--------|-----------|
| Tablet 布局 | 独立 TabletLayout | 并入 Desktop + CSS 响应式 |
| 组件目录 | `components/desktop/` + `components/mobile/` | 按功能 co-located（`ServerCard/ServerCardDesktop.vue`） |
| 实施方式 | 新建工程式描述 | 基于 P2 代码的渐进迁移路径 |
| Device Detector | 概念层描述 | 具体 `useDevice` 组合式函数 + 代码示例 |
| 导航 | 概念图 | 具体 `menuConfig` + 消费方式代码 |
| PWA | Phase 4 核心任务 | 远期愿景（Phase 4 标注为技术评估） |
| 技术栈 | 未提及 | 对齐现有 Naive UI / Pinia / Vue Router |
| 路由 | 未涉及 | 平铺路由 + App.vue 控制 Layout |
| 迁移策略 | 无 | 4-step 渐进迁移 |

---

## 12. 技术与部署（不变）

以下 P2 约定在 P3 中保持不变：

| 项 | 约定 |
|----|------|
| 构建 | Vite → `panel/backend/src/main/resources/static/` |
| API 前缀 | 所有后端接口 `/api/*` |
| 认证 | JWT（localStorage）+ axios 请求拦截器 |
| 响应格式 | `{ code: int, msg: string, data: T }`，HTTP 层始终 200 |
| 错误处理 | axios 响应拦截器自动解包 |
| ErrorCode | 前端维护 `ErrorCode` 枚举，与后端同步 |
| 部署 | Spring Boot 内嵌静态资源，单容器单端口 |

---

## 参考文档

- [[develop-process]]：API 开发过程文档
- [[springboot-infra-plan]]：Spring Boot 基础设施方案
- [[planning-v1]]：总体规划
