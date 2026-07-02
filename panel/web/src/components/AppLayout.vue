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
        <n-menu :value="activeKey" :options="menuOptions" @update:value="handleMenu" />
      </n-layout-sider>

      <n-layout-content style="padding: 24px; background: #f5f5f5; overflow: auto">
        <router-view />
      </n-layout-content>
    </n-layout>
  </n-layout>
</template>

<script setup lang="ts">
import { computed, h, onMounted, ref } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import {
  NLayout, NLayoutHeader, NLayoutSider, NLayoutContent,
  NMenu, NButton, NSpace, NTag, NText,
} from 'naive-ui';
import type { MenuOption } from 'naive-ui';
import { RouterLink } from 'vue-router';
import { getMe } from '@/api/auth';

const router = useRouter();
const route = useRoute();

const userInfo = ref<{ userId: number; username: string; role: string } | null>(null);

onMounted(async () => {
  try {
    userInfo.value = await getMe();
  } catch {
    // 获取失败跳登录
    localStorage.removeItem('token');
    router.push('/login');
  }
});

const activeKey = computed(() => {
  if (route.path.startsWith('/keys')) return 'keys';
  if (route.path.startsWith('/instances')) return 'instances';
  return 'dashboard';
});

function renderLabel(label: string, path: string) {
  return () => h(RouterLink, { to: path }, { default: () => label });
}

const menuOptions: MenuOption[] = [
  { key: 'dashboard', label: renderLabel('仪表盘', '/') },
  { key: 'keys', label: renderLabel('API Keys', '/keys') },
  { key: 'instances', label: renderLabel('实例管理', '/instances') },
];

function handleMenu(key: string) {
  // handled by RouterLink
}

function handleLogout() {
  localStorage.removeItem('token');
  router.push('/login');
}
</script>
