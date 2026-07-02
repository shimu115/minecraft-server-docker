<template>
  <div>
    <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px">
      <h2 style="margin: 0">实例管理</h2>
      <n-space>
        <n-button @click="checkAllHealth" :loading="healthChecking">健康检查全部</n-button>
        <n-button type="primary" @click="showCreate = true">注册新实例</n-button>
      </n-space>
    </div>

    <n-data-table :columns="columns" :data="instances" :loading="loading" :bordered="false" />

    <!-- 注册弹窗 -->
    <n-modal v-model:show="showCreate" preset="card" title="注册 MC 实例" style="width: 520px">
      <n-form :model="createForm" :rules="createRules">
        <n-form-item path="name" label="名称">
          <n-input v-model:value="createForm.name" placeholder="如：1.12.2 Forge Survival" />
        </n-form-item>
        <n-form-item path="host" label="Host">
          <n-input v-model:value="createForm.host" placeholder="容器名或 IP（如 mc-forge-1.12.2）" />
        </n-form-item>
        <n-form-item path="port" label="Port">
          <n-input-number v-model:value="createForm.port" :min="1" :max="65535" style="width: 100%" />
        </n-form-item>
        <n-form-item path="serverType" label="服务端类型">
          <n-select v-model:value="createForm.serverType" :options="serverTypeOptions" placeholder="选择类型" />
        </n-form-item>
        <n-form-item path="mcVersion" label="MC 版本">
          <n-input v-model:value="createForm.mcVersion" placeholder="如 1.12.2" />
        </n-form-item>
        <n-form-item path="apiKeyId" label="API Key">
          <n-select v-model:value="createForm.apiKeyId" :options="keyOptions" placeholder="选择已注册的活跃 Key"
            :loading="keysLoading" />
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="showCreate = false">取消</n-button>
          <n-button type="primary" :loading="submitting" @click="handleCreate">注册</n-button>
        </n-space>
      </template>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import { h, onMounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import {
  NDataTable, NButton, NModal, NForm, NFormItem, NInput, NInputNumber,
  NSelect, NSpace, createDiscreteApi,
} from 'naive-ui';
import type { DataTableColumn } from 'naive-ui';
import { listInstances, createInstance, deleteInstance, healthCheck } from '@/api/instances';
import { listKeys } from '@/api/keys';
import { formatDate } from '@/utils/format';
import StatusBadge from '@/components/StatusBadge.vue';
import type { InstanceItem, KeyItem } from '@/api/types';

const { message, dialog } = createDiscreteApi(['message', 'dialog']);
const router = useRouter();

const instances = ref<InstanceItem[]>([]);
const loading = ref(false);
const showCreate = ref(false);
const submitting = ref(false);
const healthChecking = ref(false);
const keysLoading = ref(false);
const availableKeys = ref<KeyItem[]>([]);

const createForm = ref({
  name: '', host: '', port: 25560, serverType: 'vanilla', mcVersion: '', apiKeyId: null as number | null,
});
const createRules = {
  name: [{ required: true, message: '请输入名称' }],
  host: [{ required: true, message: '请输入 Host' }],
  serverType: [{ required: true, message: '请选择类型' }],
  mcVersion: [{ required: true, message: '请输入 MC 版本' }],
  apiKeyId: [{ required: true, message: '请选择 API Key' }],
};

const serverTypeOptions = [
  { label: 'Vanilla', value: 'vanilla' },
  { label: 'Forge', value: 'forge' },
  { label: 'Fabric', value: 'fabric' },
  { label: 'NeoForge', value: 'neoforge' },
  { label: 'Paper', value: 'paper' },
  { label: 'Purpur', value: 'purpur' },
];

const keyOptions = ref<{ label: string; value: number }[]>([]);

const columns: DataTableColumn<InstanceItem>[] = [
  { title: 'ID', key: 'id', width: 60 },
  { title: '名称', key: 'name' },
  { title: '地址', key: 'host', render: (row) => `${row.host}:${row.port}` },
  { title: '类型', key: 'server_type' },
  { title: 'MC 版本', key: 'mc_version' },
  { title: '状态', key: 'status', render: (row) => h(StatusBadge, { status: row.status }) },
  { title: '创建时间', key: 'created_at', render: (row) => formatDate(row.created_at) },
  {
    title: '操作', key: 'actions',
    render: (row) => h(NSpace, {}, {
      default: () => [
        h(NButton, { size: 'small', onClick: () => router.push(`/instances/${row.id}`) }, { default: () => '详情' }),
        h(NButton, { size: 'small', type: 'error', onClick: () => handleDelete(row) }, { default: () => '删除' }),
      ],
    }),
  },
];

watch(showCreate, (v) => { if (v) loadKeys(); });
onMounted(() => refreshList());

async function refreshList() {
  loading.value = true;
  try {
    instances.value = await listInstances();
  } finally {
    loading.value = false;
  }
}

async function loadKeys() {
  keysLoading.value = true;
  try {
    availableKeys.value = await listKeys();
    keyOptions.value = availableKeys.value
      .filter((k) => k.status === 'active')
      .map((k) => ({ label: `${k.name} (${k.key_preview})`, value: k.id }));
  } finally {
    keysLoading.value = false;
  }
}

async function handleCreate() {
  submitting.value = true;
  try {
    await createInstance({
      name: createForm.value.name,
      host: createForm.value.host,
      port: createForm.value.port,
      serverType: createForm.value.serverType,
      mcVersion: createForm.value.mcVersion,
      apiKeyId: createForm.value.apiKeyId!,
    });
    message.success('实例注册成功');
    showCreate.value = false;
    createForm.value = { name: '', host: '', port: 25560, serverType: 'vanilla', mcVersion: '', apiKeyId: null };
    refreshList();
  } finally { submitting.value = false; }
}

function handleDelete(row: InstanceItem) {
  dialog.warning({
    title: '确认删除',
    content: `确认删除实例 "${row.name}"？对应的 API Key 将自动解绑。`,
    positiveText: '确认删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      await deleteInstance(row.id);
      message.success('实例已删除');
      refreshList();
    },
  });
}

async function checkAllHealth() {
  healthChecking.value = true;
  let ok = 0;
  let fail = 0;
  for (const inst of instances.value) {
    try {
      await healthCheck(inst.id);
      ok++;
    } catch { fail++; }
  }
  message.info(`健康检查完成：${ok} 成功，${fail} 失败`);
  healthChecking.value = false;
}
</script>
