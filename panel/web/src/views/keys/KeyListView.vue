<template>
  <div>
    <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px">
      <h2 style="margin: 0">API Key 管理</h2>
      <n-button type="primary" @click="showCreate = true">注册新 Key</n-button>
    </div>

    <n-data-table :columns="columns" :data="keys" :loading="loading" :bordered="false"/>

    <!-- 注册弹窗 -->
    <n-modal v-model:show="showCreate" preset="card" title="注册 API Key" style="width: 480px">
      <n-form :model="createForm" :rules="createRules">
        <n-form-item path="name" label="名称">
          <n-input v-model:value="createForm.name" placeholder="如：1.12.2 Forge 生存服"/>
        </n-form-item>
        <n-form-item path="keyValue" label="API Key">
          <n-input v-model:value="createForm.keyValue" placeholder="粘贴从 Docker 日志获取的 UUID"/>
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="showCreate = false">取消</n-button>
          <n-button type="primary" :loading="submitting" @click="handleRegister">注册</n-button>
        </n-space>
      </template>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import {h, onMounted, ref} from 'vue';
import {NDataTable, NButton, NModal, NForm, NFormItem, NInput, NSpace, createDiscreteApi} from 'naive-ui';
import type {DataTableColumn} from 'naive-ui';
import {listKeys, registerKey, deleteKey, revokeKey} from '@/api/keys';
import {formatDate} from '@/utils/format';
import StatusBadge from '@/components/StatusBadge.vue';
import type {KeyItem} from '@/api/types';

const {message, dialog} = createDiscreteApi(['message', 'dialog']);

const keys = ref<KeyItem[]>([]);
const loading = ref(false);
const showCreate = ref(false);
const submitting = ref(false);

const createForm = ref({name: '', keyValue: ''});
const createRules = {
  name: [{required: true, message: '请输入名称'}],
  keyValue: [{required: true, message: '请输入 Key 值'}],
};

const columns: DataTableColumn<KeyItem>[] = [
  {title: 'ID', key: 'id', width: 60},
  {title: '名称', key: 'name'},
  {title: 'Key', key: 'key_preview', render: (row) => row.key_preview},
  {title: '状态', key: 'status', render: (row) => h(StatusBadge, {status: row.status})},
  {
    title: '绑定实例', key: 'bound_instance',
    render: (row) => row.bound_instance ? row.bound_instance.name : '-',
  },
  {title: '创建时间', key: 'created_at', render: (row) => formatDate(row.created_at)},
  {
    title: '操作', key: 'actions',
    render: (row) => h(NSpace, {}, {
      default: () => [
        h(NButton, {size: 'small', onClick: () => handleRevoke(row)}, {default: () => '吊销'}),
        h(NButton, {size: 'small', type: 'error', onClick: () => handleDelete(row)}, {default: () => '删除'}),
      ],
    }),
  },
];

onMounted(() => refreshList());

async function refreshList() {
  loading.value = true;
  try {
    keys.value = await listKeys();
  } finally {
    loading.value = false;
  }
}

async function handleRegister() {
  submitting.value = true;
  try {
    console.log('registerKey', createForm.value.name, createForm.value.keyValue)
    await registerKey(createForm.value.name, createForm.value.keyValue);
    message.success('Key 注册成功');
    showCreate.value = false;
    createForm.value = {name: '', keyValue: ''};
    refreshList();
  } catch { /* error handled by interceptor */
  } finally {
    submitting.value = false;
  }
}

function handleRevoke(row: KeyItem) {
  dialog.warning({
    title: '确认吊销',
    content: '吊销后该 Key 将无法使用，已绑定的实例将失去连接。确认吊销？',
    positiveText: '确认吊销',
    negativeText: '取消',
    onPositiveClick: async () => {
      await revokeKey(row.id);
      message.success('Key 已吊销');
      refreshList();
    },
  });
}

function handleDelete(row: KeyItem) {
  dialog.warning({
    title: '确认删除',
    content: row.bound_instance
        ? `Key "${row.name}" 已绑定实例 "${row.bound_instance.name}"，请先解绑再删除。`
        : `确认删除 Key "${row.name}"？`,
    positiveText: '确认删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      if (row.bound_instance) return;
      await deleteKey(row.id);
      message.success('Key 已删除');
      refreshList();
    },
  });
}
</script>
