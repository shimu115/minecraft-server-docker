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

        <!-- 终端模拟 -->
        <div class="terminal" ref="terminalRef">
          <div class="terminal-output">
            <div v-for="(entry, i) in terminalLines" :key="i"
              :class="['terminal-line', entry.type]">
              <span v-if="entry.type === 'input'" class="prompt">$ </span>
              <span>{{ entry.text }}</span>
            </div>
          </div>
          <div class="terminal-input-row">
            <span class="prompt">$ </span>
            <input
              v-model="command"
              class="terminal-input"
              placeholder="输入 MC 指令…"
              @keyup.enter="handleSendCommand"
              :disabled="cmdLoading"
            />
          </div>
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
import { nextTick, onMounted, onUnmounted, ref } from 'vue';
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
const terminalRef = ref<HTMLElement | null>(null);

interface TerminalLine {
  type: 'input' | 'output' | 'info';
  text: string;
}
const terminalLines = ref<TerminalLine[]>([]);

function appendTerminal(type: TerminalLine['type'], text: string) {
  terminalLines.value.push({ type, text });
  // 保留最近 500 行
  if (terminalLines.value.length > 500) {
    terminalLines.value = terminalLines.value.slice(-500);
  }
  nextTick(() => {
    if (terminalRef.value) {
      terminalRef.value.scrollTop = terminalRef.value.scrollHeight;
    }
  });
}
interface ServerStatus {
  running: boolean;
  players: number;
  uptime?: string;
  version?: string;
}

const actionLoading = ref<string | null>(null);
const statusLoading = ref(false);
const serverStatus = ref<ServerStatus | null>(null);

let logAbortController: AbortController | null = null;

async function connectLogs() {
  const token = localStorage.getItem('token');
  if (!token) return;

  logAbortController = new AbortController();
  try {
    const response = await fetch(`/api/server/${instanceId}/get-logs?tail=100`, {
      headers: { Authorization: `Bearer ${token}` },
      signal: logAbortController.signal,
    });

    const reader = response.body!.getReader();
    const decoder = new TextDecoder();
    let buffer = '';

    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true });
      const lines = buffer.split('\n');
      buffer = lines.pop() || '';
      for (const line of lines) {
        if (line.startsWith('data:')) {
          const data = line.substring(5).trim();
          if (data) appendTerminal('output', data);
        }
      }
    }
  } catch {
    // connection closed or aborted
  }
}

function disconnectLogs() {
  logAbortController?.abort();
  logAbortController = null;
}

onMounted(async () => {
  try {
    const me = await getMe();
    isRoot.value = me.role === 'ROOT';
  } catch { /* ignore */ }
  await loadInstance();
  connectLogs();
});

onUnmounted(() => {
  disconnectLogs();
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
  const cmd = command.value.trim();
  appendTerminal('input', cmd);
  command.value = '';
  cmdLoading.value = true;
  try {
    await sendCommand(instanceId, cmd);
  } catch {
    // 错误已由拦截器处理
  } finally {
    cmdLoading.value = false;
  }
}

async function handleServerAction(action: string) {
  actionLoading.value = action;
  try {
    if (action === 'start') await startServer(instanceId);
    else if (action === 'stop') await stopServer(instanceId);
    else if (action === 'restart') await restartServer(instanceId);
    appendTerminal('info', `${action} 指令已发送`);
    message.success(`${action} 指令已发送`);
  } catch {
    // 错误已由拦截器处理
  } finally { actionLoading.value = null; }
}
</script>

<style scoped>
.terminal {
  background: #1a1a2e;
  border-radius: 6px;
  padding: 12px 16px;
  font-family: 'Cascadia Code', 'Fira Code', 'Consolas', monospace;
  font-size: 13px;
  line-height: 1.6;
  max-height: 400px;
  overflow-y: auto;
  color: #e0e0e0;
}

.terminal-output {
  min-height: 60px;
}

.terminal-line {
  white-space: pre-wrap;
  word-break: break-all;
}
.terminal-line.input {
  color: #4fc3f7;
}
.terminal-line.info {
  color: #81c784;
}
.terminal-line.output {
  color: #b0bec5;
}

.prompt {
  color: #66bb6a;
  user-select: none;
}

.terminal-input-row {
  display: flex;
  align-items: center;
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px solid #333;
}

.terminal-input {
  flex: 1;
  background: transparent;
  border: none;
  outline: none;
  color: #fff;
  font-family: inherit;
  font-size: inherit;
  caret-color: #66bb6a;
}
.terminal-input::placeholder {
  color: #555;
}
.terminal-input:disabled {
  opacity: 0.5;
}
</style>
