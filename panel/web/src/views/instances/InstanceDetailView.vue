<template>
  <div>
    <n-button text @click="router.push('/instances')" style="margin-bottom: 16px">← 返回实例列表</n-button>

    <n-spin :show="loading">
      <n-grid :cols="2" :x-gap="16" style="margin-bottom: 16px">
        <!-- 基本信息 -->
        <n-grid-item>
          <n-card title="基本信息">
            <n-descriptions :column="1" label-placement="left">
              <n-descriptions-item label="名称">{{ instance?.name }}</n-descriptions-item>
              <n-descriptions-item label="地址">{{ instance?.host }}:{{ instance?.port }}</n-descriptions-item>
              <n-descriptions-item label="类型">{{ instance?.server_type }}</n-descriptions-item>
              <n-descriptions-item label="MC 版本">{{ instance?.mc_version }}</n-descriptions-item>
              <n-descriptions-item label="状态">
                <StatusBadge v-if="instance" :status="instance.status" />
              </n-descriptions-item>
              <n-descriptions-item label="创建时间">{{ formatDate(instance?.created_at || '') }}</n-descriptions-item>
            </n-descriptions>
          </n-card>
        </n-grid-item>

        <!-- Key 信息 + 操作 -->
        <n-grid-item>
          <n-card title="绑定的 API Key">
            <n-descriptions v-if="instance?.api_key" :column="1" label-placement="left">
              <n-descriptions-item label="Key 名称">{{ instance?.api_key?.name }}</n-descriptions-item>
              <n-descriptions-item label="Key 值">{{ instance?.api_key?.key_preview }}</n-descriptions-item>
              <n-descriptions-item label="状态">
                <StatusBadge :status="instance?.api_key?.status || 'unknown'" />
              </n-descriptions-item>
            </n-descriptions>

            <n-divider />

            <n-space>
              <n-button type="primary" @click="handleHealthCheck" :loading="healthLoading">健康检查</n-button>
              <n-button @click="showBindKey = true; loadKeysForBind()">更换绑定 Key</n-button>
              <n-button type="warning" @click="handleRefreshKey" :loading="refreshLoading"
                v-if="isRoot">刷新 API Key</n-button>
            </n-space>

            <div v-if="healthResult" style="margin-top: 12px">
              <n-alert :type="healthResult.success ? 'success' : 'error'">
                {{ healthResult.message }}
              </n-alert>
            </div>
          </n-card>
        </n-grid-item>
      </n-grid>

      <!-- 控制台 -->
      <n-card title="控制台" style="margin-bottom: 16px">
        <n-space style="margin-bottom: 12px">
          <n-button type="success" @click="handleServerAction('start')" :loading="actionLoading === 'start'">
            启动
          </n-button>
          <n-button type="warning" @click="handleServerAction('stop')" :loading="actionLoading === 'stop'">
            停止
          </n-button>
          <n-button @click="handleServerAction('restart')" :loading="actionLoading === 'restart'">
            重启
          </n-button>
          <n-button @click="handleStatus" :loading="statusLoading">查询状态</n-button>
        </n-space>

        <div v-if="serverStatus" style="margin-bottom: 12px">
          <n-descriptions :column="3" label-placement="left" bordered size="small">
            <n-descriptions-item label="状态">
              <n-tag :type="serverStatus.running ? 'success' : 'error'" size="small">
                {{ serverStatus.running ? '运行中' : '已停止' }}
              </n-tag>
            </n-descriptions-item>
            <n-descriptions-item label="玩家">{{ serverStatus.players ?? 0 }}</n-descriptions-item>
            <n-descriptions-item v-if="serverStatus.uptime" label="运行时长">{{ serverStatus.uptime }}</n-descriptions-item>
            <n-descriptions-item v-if="serverStatus.version" label="MC 版本">{{ serverStatus.version }}</n-descriptions-item>
          </n-descriptions>
        </div>

        <n-divider>发送指令</n-divider>
        <n-space>
          <n-input v-model:value="command" placeholder="输入 MC 指令（如 list）" style="width: 300px"
            @keyup.enter="handleSendCommand" />
          <n-button type="primary" @click="handleSendCommand" :loading="cmdLoading">发送</n-button>
        </n-space>
        <div v-if="cmdHistory.length" style="margin-top: 12px">
          <n-tag v-for="(cmd, i) in cmdHistory" :key="i" style="margin: 4px">$ {{ cmd }}</n-tag>
        </div>
      </n-card>
    </n-spin>

    <!-- 更换 Key 弹窗 -->
    <n-modal v-model:show="showBindKey" preset="card" title="更换绑定 Key" style="width: 400px">
      <n-select v-model:value="selectedKeyId" :options="keyOptions" placeholder="选择活跃的 API Key"
        :loading="keysLoading" />
      <template #footer>
        <n-space justify="end">
          <n-button @click="showBindKey = false">取消</n-button>
          <n-button type="primary" @click="handleBindKey" :loading="bindLoading">确认</n-button>
        </n-space>
      </template>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import {
  NCard, NButton, NDescriptions, NDescriptionsItem, NDivider,
  NSpace, NGrid, NGridItem, NInput, NTag, NAlert,
  NModal, NSelect, NSpin, createDiscreteApi,
} from 'naive-ui';
import { getInstance, updateInstance, bindKey, refreshKey, healthCheck } from '@/api/instances';
import { listKeys } from '@/api/keys';
import { getMe } from '@/api/auth';
import { startServer, stopServer, restartServer, getStatus, sendCommand } from '@/api/server';
import { formatDate } from '@/utils/format';
import StatusBadge from '@/components/StatusBadge.vue';
import type { InstanceItem, KeyItem } from '@/api/types';

const { message, dialog } = createDiscreteApi(['message', 'dialog']);
const router = useRouter();

const props = defineProps<{ id: string }>();
const instanceId = parseInt(props.id);

const instance = ref<InstanceItem | null>(null);
const loading = ref(false);
const isRoot = ref(false);

// Health
const healthLoading = ref(false);
const healthResult = ref<{ success: boolean; message: string } | null>(null);

// Refresh key
const refreshLoading = ref(false);

// Bind key
const showBindKey = ref(false);
const selectedKeyId = ref<number | null>(null);
const keyOptions = ref<{ label: string; value: number }[]>([]);
const keysLoading = ref(false);
const bindLoading = ref(false);

// Console
const command = ref('');
const cmdLoading = ref(false);
const cmdHistory = ref<string[]>([]);
interface ServerStatus {
  running: boolean;
  players: number;
  uptime?: string;
  version?: string;
}

const actionLoading = ref<string | null>(null);
const statusLoading = ref(false);
const serverStatus = ref<ServerStatus | null>(null);

onMounted(async () => {
  try {
    const me = await getMe();
    isRoot.value = me.role === 'ROOT';
  } catch { /* ignore */ }
  await loadInstance();
});

async function loadInstance() {
  loading.value = true;
  try {
    instance.value = await getInstance(instanceId);
    console.log(instance.value)
  } finally { loading.value = false; }
}

async function handleHealthCheck() {
  healthLoading.value = true;
  healthResult.value = null;
  try {
    const result = await healthCheck(instanceId);
    healthResult.value = { success: true, message: `Go API 状态: ${result.go_api_health}` };
    message.success('健康检查通过');
  } catch {
    healthResult.value = { success: false, message: '健康检查失败' };
  } finally { healthLoading.value = false; }
}

function handleRefreshKey() {
  dialog.warning({
    title: '确认刷新 API Key',
    content: '此操作将使旧 Key 立即失效，Go API 会生成新 Key 并重新绑定。确认刷新？',
    positiveText: '确认刷新',
    negativeText: '取消',
    onPositiveClick: async () => {
      refreshLoading.value = true;
      try {
        const result = await refreshKey(instanceId);
        message.success('Key 已刷新');
        dialog.success({
          title: '刷新成功',
          content: `旧 Key: ${result.previous_key.key_preview} → 新 Key: ${result.new_key.key_preview}`,
          positiveText: '知道了',
        });
        loadInstance();
      } finally { refreshLoading.value = false; }
    },
  });
}

async function loadKeysForBind() {
  keysLoading.value = true;
  try {
    const allKeys = await listKeys();
    keyOptions.value = allKeys
      .filter((k) => k.status === 'active' && k.id !== instance.value?.api_key?.id)
      .map((k) => ({ label: `${k.name} (${k.key_preview})`, value: k.id }));
  } finally { keysLoading.value = false; }
}

async function handleBindKey() {
  if (!selectedKeyId.value) {
    message.warning('请选择 Key');
    return;
  }
  bindLoading.value = true;
  try {
    await bindKey(instanceId, selectedKeyId.value);
    message.success('Key 已更换');
    showBindKey.value = false;
    loadInstance();
  } finally { bindLoading.value = false; }
}

async function handleServerAction(action: string) {
  actionLoading.value = action;
  try {
    if (action === 'start') await startServer(instanceId);
    else if (action === 'stop') await stopServer(instanceId);
    else if (action === 'restart') await restartServer(instanceId);
    message.success(`${action} 指令已发送`);
  } finally { actionLoading.value = null; }
}

async function handleStatus() {
  statusLoading.value = true;
  try {
    const goRaw = await getStatus(instanceId);
    // Go API 返回 JSON 字符串，解析后提取 data 字段
    const parsed = typeof goRaw === 'string' ? JSON.parse(goRaw) : goRaw;
    serverStatus.value = parsed.data || parsed;
  } finally { statusLoading.value = false; }
}

async function handleSendCommand() {
  if (!command.value.trim()) return;
  cmdLoading.value = true;
  try {
    await sendCommand(instanceId, command.value);
    cmdHistory.value.unshift(command.value);
    command.value = '';
  } finally { cmdLoading.value = false; }
}
</script>
