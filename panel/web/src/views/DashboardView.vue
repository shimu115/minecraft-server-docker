<template>
  <div>
    <h2 style="margin-top: 0">仪表盘</h2>

    <n-grid :cols="3" :x-gap="16" style="margin-bottom: 24px">
      <n-grid-item>
        <n-card hoverable @click="router.push('/keys')">
          <n-statistic label="已注册 API Key" :value="stats.keyCount" />
        </n-card>
      </n-grid-item>
      <n-grid-item>
        <n-card hoverable @click="router.push('/instances')">
          <n-statistic label="MC 实例" :value="stats.instanceCount" />
        </n-card>
      </n-grid-item>
      <n-grid-item>
        <n-card>
          <n-statistic label="运行中实例" :value="stats.runningCount">
            <template #suffix>/ {{ stats.instanceCount }}</template>
          </n-statistic>
        </n-card>
      </n-grid-item>
    </n-grid>

    <n-space>
      <n-button type="primary" @click="router.push('/keys')">注册 API Key</n-button>
      <n-button @click="router.push('/instances')">管理实例</n-button>
    </n-space>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive } from 'vue';
import { useRouter } from 'vue-router';
import { NCard, NGrid, NGridItem, NStatistic, NButton, NSpace } from 'naive-ui';
import { listKeys } from '@/api/keys';
import { listInstances } from '@/api/instances';

const router = useRouter();

const stats = reactive({ keyCount: 0, instanceCount: 0, runningCount: 0 });

onMounted(async () => {
  try {
    const keys = await listKeys();
    stats.keyCount = keys.length;
  } catch { /* ignore */ }
  try {
    const instances = await listInstances();
    stats.instanceCount = instances.length;
    stats.runningCount = instances.filter((i) => i.status === 'running').length;
  } catch { /* ignore */ }
});
</script>
