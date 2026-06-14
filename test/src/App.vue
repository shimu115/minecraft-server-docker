<script setup>
import { ref, onMounted, onUnmounted, nextTick } from 'vue'

// ===== State =====
const apiKey = ref(localStorage.getItem('mc_api_key') || '')
const baseURL = ref(localStorage.getItem('mc_api_url') || '')
const connected = ref(false)
const status = ref({ running: false, players: 0, uptime: '', version: '' })
const command = ref('')
const commandResult = ref('')
const logs = ref([])
const logContainer = ref(null)
const autoScroll = ref(true)
const logTail = ref(200)
const selectedFiles = ref(new Set())
const exportFormat = ref('zip')
const uploading = ref(false)
let sseAbort = null

// ===== File Browser =====
const filePath = ref('')
const files = ref([])
const selectedFile = ref(null)
const fileContent = ref('')
const fileEditing = ref(false)
const fileError = ref('')

// ===== Helpers =====
function headers() {
  const h = { 'Content-Type': 'application/json' }
  if (apiKey.value) h['Authorization'] = `Bearer ${apiKey.value}`
  return h
}

async function api(method, path, body) {
  const url = (baseURL.value || '') + path
  const opts = { method, headers: headers() }
  if (body) opts.body = JSON.stringify(body)
  const res = await fetch(url, opts)
  return res.json()
}

async function refreshKey() {
  try {
    const data = await api('POST', '/api/auth/refresh')
    if (data.code === 200 && data.data?.api_key) {
      apiKey.value = data.data.api_key
      localStorage.setItem('mc_api_key', apiKey.value)
      stopLogStream()
      startLogStream()
    }
    commandResult.value = JSON.stringify(data, null, 2)
  } catch {}
}

// ===== Connection =====
function saveApiKey() {
  localStorage.setItem('mc_api_key', apiKey.value)
  localStorage.setItem('mc_api_url', baseURL.value)
  checkConnection()
}

async function checkConnection() {
  try {
    const data = await api('GET', '/api/health')
    connected.value = data.code === 200
    if (connected.value) { fetchStatus(); fetchFiles(); startLogStream() }
  } catch { connected.value = false }
}

// ===== Server Controls =====
async function fetchStatus() {
  try { const data = await api('GET', '/api/server/status'); if (data.code === 200) status.value = data.data } catch {}
}
async function serverStart() {
  const data = await api('POST', '/api/server/start')
  commandResult.value = JSON.stringify(data, null, 2)
  setTimeout(fetchStatus, 2000)
}
async function serverStop() {
  const data = await api('POST', '/api/server/stop')
  commandResult.value = JSON.stringify(data, null, 2)
  setTimeout(fetchStatus, 3000)
}
async function serverRestart() {
  const data = await api('POST', '/api/server/restart')
  commandResult.value = JSON.stringify(data, null, 2)
  setTimeout(fetchStatus, 5000)
}

// ===== Command =====
async function sendCommand() {
  if (!command.value.trim()) return
  const data = await api('POST', '/api/command', { command: command.value })
  commandResult.value = JSON.stringify(data, null, 2)
  command.value = ''
}

// ===== Logs (SSE) =====
async function startLogStream() {
  stopLogStream()
  const url = (baseURL.value || '') + `/api/logs?tail=${logTail.value}`
  const controller = new AbortController()
  sseAbort = controller

  try {
    const res = await fetch(url, {
      signal: controller.signal,
      headers: headers(),
    })
    if (!res.ok) return

    const reader = res.body.getReader()
    const decoder = new TextDecoder()
    let buf = ''

    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buf += decoder.decode(value, { stream: true })
      const lines = buf.split('\n')
      buf = lines.pop() || ''
      for (const line of lines) {
        if (line.startsWith('data: ')) {
          logs.value.push(line.slice(6))
          if (logs.value.length > 1000) logs.value.shift()
        }
      }
      if (autoScroll.value) scrollToBottom()
    }
  } catch (e) {
    if (e.name !== 'AbortError') console.error('SSE error:', e)
  }
}

function stopLogStream() { if (sseAbort) { sseAbort.abort(); sseAbort = null } }

function scrollToBottom() {
  nextTick(() => { if (logContainer.value) logContainer.value.scrollTop = logContainer.value.scrollHeight })
}

function restartLogStream() { stopLogStream(); startLogStream() }
function clearLogs() { logs.value = [] }

// ===== File Browser (multi-select) =====
function toggleSelect(f) { const s = selectedFiles.value; s.has(f.path) ? s.delete(f.path) : s.add(f.path); selectedFiles.value = new Set(s) }
function toggleSelectAll() {
  if (selectedFiles.value.size === files.value.length) { selectedFiles.value = new Set() }
  else { selectedFiles.value = new Set(files.value.map(f => f.path)) }
}
async function downloadSelected() {
  for (const path of selectedFiles.value) {
    const url = (baseURL.value || '') + `/api/files/download?path=${encodeURIComponent(path)}`
    const a = document.createElement('a'); a.href = url; a.download = path.split('/').pop(); a.click()
  }
}
async function deleteSelected() {
  if (!confirm(`确定删除 ${selectedFiles.value.size} 个文件?`)) return
  for (const path of selectedFiles.value) { await api('DELETE', `/api/files/delete?path=${encodeURIComponent(path)}`) }
  selectedFiles.value = new Set(); fetchFiles()
}
async function uploadFile(e) {
  const file = e.target.files[0]; if (!file) return
  uploading.value = true
  const form = new FormData(); form.append('file', file)
  if (filePath.value) form.append('dir', filePath.value)
  try {
    const url = (baseURL.value || '') + '/api/files/upload'
    const res = await fetch(url, { method: 'POST', headers: { Authorization: headers().Authorization }, body: form })
    commandResult.value = JSON.stringify(await res.json(), null, 2)
    fetchFiles()
  } catch {}
  uploading.value = false
}
function exportFiles() {
  const form = document.createElement('form')
  form.method = 'POST'; form.action = (baseURL.value || '') + '/api/files/export'
  form.target = '_blank'
  const f1 = document.createElement('input'); f1.name = 'format'; f1.value = exportFormat.value; form.appendChild(f1)
  const f2 = document.createElement('input'); f2.name = 'key'; f2.value = apiKey.value; form.appendChild(f2)
  document.body.appendChild(form); form.submit(); document.body.removeChild(form)
}

// ===== File Browser =====
async function fetchFiles() {
  try {
    const data = await api('GET', `/api/files/list?path=${encodeURIComponent(filePath.value || '')}`)
    if (data.code === 200) { files.value = data.data; fileError.value = '' }
    else { fileError.value = data.message || '请求失败' }
  } catch (e) { fileError.value = '连接失败: ' + e.message }
}
function enterDir(dir) { filePath.value = dir; selectedFile.value = null; fileContent.value = ''; fileEditing.value = false; fetchFiles() }
function goUp() { const p = filePath.value.split('/').filter(Boolean); p.pop(); filePath.value = p.join('/'); selectedFile.value = null; fileContent.value = ''; fileEditing.value = false; fetchFiles() }
async function openFile(f) {
  if (f.isDir) { enterDir(f.path); return }
  selectedFile.value = f; fileEditing.value = false
  try { const data = await api('GET', `/api/files/read?path=${encodeURIComponent(f.path)}`); if (data.code === 200) fileContent.value = data.data.content } catch {}
}
function editFile() { fileEditing.value = true }
async function saveFile() {
  if (!selectedFile.value) return
  try { await api('POST', `/api/files/write?path=${encodeURIComponent(selectedFile.value.path)}`, { content: fileContent.value }); fileEditing.value = false } catch {}
}
async function deleteFile(f) {
  if (!confirm(`确定删除 ${f.name}?`)) return
  try {
    await api('DELETE', `/api/files/delete?path=${encodeURIComponent(f.path)}`)
    if (selectedFile.value?.path === f.path) { selectedFile.value = null; fileContent.value = '' }
    fetchFiles()
  } catch {}
}
function formatSize(bytes) {
  if (!bytes) return '0 B'
  const u = ['B', 'KB', 'MB', 'GB']; let i = 0, s = bytes
  while (s >= 1024 && i < u.length - 1) { s /= 1024; i++ }
  return s.toFixed(i ? 1 : 0) + ' ' + u[i]
}

// ===== Lifecycle =====
onMounted(() => { if (apiKey.value) checkConnection() })
onUnmounted(() => { stopLogStream() })
</script>

<template>
  <div class="app">
    <header class="header">
      <h1>&#x1F9B1; MC Server API 测试面板</h1>
      <span class="badge" :class="connected ? 'ok' : 'err'">
        {{ connected ? '&#x25CF; 已连接' : '&#x25CB; 未连接' }}
      </span>
    </header>

    <!-- 主布局：左面板（控制）+ 右面板（日志） -->
    <div class="main-layout">
      <div class="left-panel">
        <!-- API 连接 -->
        <section class="card">
          <h2>&#x1F511; API 连接</h2>
          <div class="row">
            <input v-model="baseURL" placeholder="API 地址 (留空走代理)" class="input" />
            <input v-model="apiKey" type="password" placeholder="API Key" class="input" />
            <button @click="saveApiKey" class="btn primary">连接</button>
            <button @click="refreshKey" class="btn warn sm">刷新 Key</button>
          </div>
        </section>

        <!-- 服务端状态 -->
        <section class="card">
          <h2>&#x1F4CA; 服务端状态</h2>
          <div class="status-grid">
            <div class="stat"><label>运行状态</label><span :class="status.running ? 'green' : 'red'">{{ status.running ? '运行中' : '已停止' }}</span></div>
            <div class="stat"><label>在线玩家</label><span>{{ status.players }}</span></div>
            <div class="stat"><label>运行时间</label><span>{{ status.uptime || '-' }}</span></div>
            <div class="stat"><label>版本</label><span>{{ status.version || '-' }}</span></div>
          </div>
          <div class="btn-group">
            <button @click="serverStart" :disabled="status.running" class="btn success">&#x25B6; 启动</button>
            <button @click="serverStop" :disabled="!status.running" class="btn danger">&#x25A0; 停止</button>
            <button @click="serverRestart" :disabled="!status.running" class="btn warn">&#x21BA; 重启</button>
            <button @click="fetchStatus" class="btn">&#x21BB; 刷新</button>
          </div>
        </section>

        <!-- 发送指令 -->
        <section class="card">
          <h2>&#x2328; 发送指令</h2>
          <div class="row">
            <input v-model="command" @keyup.enter="sendCommand" placeholder="例如: say Hello" class="input" />
            <button @click="sendCommand" class="btn primary">发送</button>
          </div>
          <pre v-if="commandResult" class="result">{{ commandResult }}</pre>
        </section>

        <!-- 文件管理 -->
        <section class="card">
          <h2>&#x1F4C4; 文件管理</h2>
          <div class="row" style="margin-bottom:10px">
            <span v-if="fileError" class="red" style="font-size:12px">{{ fileError }}</span>
            <button @click="fetchFiles" class="btn primary sm">&#x21BB;</button>
            <button v-if="filePath" @click="goUp" class="btn sm">&#x2190;</button>
            <span class="file-breadcrumb">./{{ filePath || '' }}</span>
            <label class="btn sm" style="cursor:pointer;margin-left:auto" :class="{ primary: uploading }">
              {{ uploading ? '...' : '&#x2B06;' }}
              <input type="file" @change="uploadFile" style="display:none" />
            </label>
            <button v-if="selectedFiles.size" @click="downloadSelected" class="btn sm success">&#x2B07;</button>
            <button v-if="selectedFiles.size" @click="deleteSelected" class="btn sm danger">&#x2716;</button>
            <select v-model="exportFormat" class="input" style="flex:0 0 auto;width:auto;min-width:60px">
              <option value="zip">zip</option>
              <option value="tar.gz">tar.gz</option>
            </select>
            <button @click="exportFiles" class="btn sm warn">导出</button>
          </div>
          <div class="file-layout">
            <div class="file-list">
              <div class="file-row" @click="toggleSelectAll" style="color:#8b949e">
                <input type="checkbox" :checked="selectedFiles.size === files.length && files.length > 0" style="pointer-events:none" />
                <span class="file-name">全选</span>
              </div>
              <div v-for="f in files" :key="f.path" class="file-row" :class="{ active: selectedFile?.path === f.path }">
                <input type="checkbox" :checked="selectedFiles.has(f.path)" @click.stop="toggleSelect(f)" />
                <span @click="openFile(f)" style="display:flex;align-items:center;gap:6px;flex:1;cursor:pointer">
                  <span>{{ f.isDir ? '&#x1F4C1;' : '&#x1F4C4;' }}</span>
                  <span class="file-name">{{ f.name }}</span>
                  <span class="file-size" v-if="!f.isDir">{{ formatSize(f.size) }}</span>
                </span>
                <button @click.stop="deleteFile(f)" class="btn sm danger">&#x2716;</button>
              </div>
              <div v-if="files.length === 0" class="log-empty">目录为空</div>
            </div>
            <div class="file-preview" v-if="selectedFile">
              <div class="row" style="margin-bottom:6px">
                <strong>{{ selectedFile.name }}</strong>
                <span class="hint">{{ formatSize(selectedFile.size) }}</span>
                <button v-if="!fileEditing" @click="editFile" class="btn sm">编辑</button>
                <button v-if="fileEditing" @click="saveFile" class="btn sm success">保存</button>
              </div>
              <textarea v-if="fileEditing" v-model="fileContent" class="file-editor"></textarea>
              <pre v-else class="file-content">{{ fileContent }}</pre>
            </div>
            <div class="file-preview" v-else>
              <div class="log-empty">点击文件查看内容</div>
            </div>
          </div>
        </section>
      </div>

      <!-- 日志 -->
      <div class="right-panel">
        <section class="card log-card">
          <h2>&#x1F4DC; 实时日志</h2>
          <div class="log-toolbar">
            <label>Tail <input v-model.number="logTail" type="number" class="input short" style="width:60px" /></label>
            <label><input type="checkbox" v-model="autoScroll" /> 自动滚动</label>
            <button @click="clearLogs" class="btn sm">清空</button>
            <button @click="restartLogStream" class="btn sm">刷新</button>
            <span v-if="logs.length" class="hint">{{ logs.length }} 行</span>
          </div>
          <div ref="logContainer" class="log-viewer log-viewer-tall">
            <div v-for="(line, i) in logs" :key="i" class="log-line">{{ line }}</div>
            <div v-if="logs.length === 0" class="log-empty">等待日志...</div>
          </div>
        </section>
      </div>
    </div>
  </div>
</template>

<style>
* { box-sizing: border-box; margin: 0; padding: 0; }
body { font-family: system-ui, sans-serif; background: #0d1117; color: #c9d1d9; }
.app {
  max-width: 1400px;
  margin: 0 auto;
  padding: 20px;
  height: 100vh;           /* 锁定视口高度 */
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
}
.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  flex-shrink: 0;          /* 防止页头被压缩 */
}
.header h1 { font-size: 22px; color: #58a6ff; }
.card { background: #161b22; border: 1px solid #30363d; border-radius: 8px; padding: 16px; margin-bottom: 16px; }
.card h2 { font-size: 15px; color: #8b949e; margin-bottom: 12px; }
.row { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.main-layout {
  display: grid;
  grid-template-columns: 420px minmax(500px, 1fr);
  gap: 20px;
  flex: 1;                 /* 弹性充满 header 剩下的所有高度 */
  min-height: 0;           /* 🔥 极其关键：允许子元素收缩，否则会被内容撑开 */
  height: calc(100vh - 120px); /* 备用双保险固定高度 */
}
/* 左侧控制面板：内部滚动 */
.left-panel {
  min-width: 0;
  max-width: 420px;
  overflow-y: auto;        /* 如果左侧卡片太多，左侧自己滚动 */
  height: 100%;
}
/* 3. 右侧日志面板容器（假设你用了 .right-panel 或类似命名） */
.right-panel {
  display: flex;
  flex-direction: column;
  height: 100%;            /* 撑满 main-layout 的高度 */
  min-height: 0;           /* 🔥 极其关键：防止被子类无限撑高 */
}
.log-card { height: 100%; display: flex; flex-direction: column; overflow: hidden; }
.log-card h2 { flex-shrink: 0; }
.log-card .log-toolbar { flex-shrink: 0; }
.log-card .log-viewer-tall { flex: 1; min-height: 0; overflow-y: auto; }
.badge { font-size: 13px; padding: 2px 10px; border-radius: 12px; }
.badge.ok { background: #1a3d1a; color: #3fb950; }
.badge.err { background: #3d1a1a; color: #f85149; }
.input { background: #0d1117; border: 1px solid #30363d; color: #c9d1d9; padding: 6px 10px; border-radius: 6px; font-size: 13px; flex: 1; min-width: 150px; }
.input.short { flex: 0 0 90px; min-width: 60px; }
.input:focus { outline: none; border-color: #58a6ff; }
.btn { padding: 6px 14px; border: 1px solid #30363d; border-radius: 6px; background: #21262d; color: #c9d1d9; cursor: pointer; font-size: 13px; white-space: nowrap; }
.btn:hover { background: #30363d; }
.btn:disabled { opacity: 0.4; cursor: not-allowed; }
.btn.primary { background: #1f6feb; border-color: #1f6feb; color: #fff; }
.btn.success { background: #238636; border-color: #238636; color: #fff; }
.btn.danger { background: #da3633; border-color: #da3633; color: #fff; }
.btn.warn { background: #9e6a03; border-color: #9e6a03; color: #fff; }
.btn.sm { padding: 3px 10px; font-size: 12px; }
.btn-group { display: flex; gap: 6px; margin-top: 10px; }
.status-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; margin-bottom: 6px; }
.stat { display: flex; flex-direction: column; }
.stat label { font-size: 11px; color: #8b949e; text-transform: uppercase; }
.stat span { font-size: 15px; font-weight: 600; }
.green { color: #3fb950; }
.red { color: #f85149; }
.result { background: #0d1117; border: 1px solid #30363d; border-radius: 6px; padding: 10px; margin-top: 8px; font-size: 12px; font-family: monospace; white-space: pre-wrap; max-height: 150px; overflow-y: auto; }
.log-toolbar { display: flex; align-items: center; gap: 12px; margin-bottom: 8px; font-size: 12px; }
.log-toolbar .hint { color: #8b949e; }
.log-viewer { background: #0d1117; border: 1px solid #30363d; border-radius: 6px; height: 400px; overflow-y: auto; padding: 8px; font-family: 'Consolas', monospace; font-size: 12px; line-height: 1.6; }
.log-line { color: #8b949e; word-break: break-all; }
.log-line:hover { background: #161b22; }
.log-empty { color: #484f58; text-align: center; padding: 40px; }
.file-breadcrumb { color: #58a6ff; font-size: 13px; }
.file-layout { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.file-list { background: #0d1117; border: 1px solid #30363d; border-radius: 6px; max-height: 300px; overflow-y: auto; }
.file-row { display: flex; align-items: center; gap: 6px; padding: 5px 8px; cursor: pointer; font-size: 13px; border-bottom: 1px solid #21262d; }
.file-row:hover { background: #161b22; }
.file-row.active { background: #1f2a3a; }
.file-name { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.file-size { color: #8b949e; font-size: 11px; }
.file-preview { background: #0d1117; border: 1px solid #30363d; border-radius: 6px; padding: 8px; }
.file-content { max-height: 250px; overflow: auto; font-size: 12px; font-family: monospace; white-space: pre-wrap; word-break: break-all; margin: 0; }
.file-editor { width: 100%; height: 250px; background: #0d1117; color: #c9d1d9; border: 1px solid #30363d; border-radius: 4px; padding: 8px; font-size: 12px; font-family: monospace; resize: vertical; }
@media (max-width: 900px) { .main-layout { grid-template-columns: 1fr; } .log-card { height: 500px; } }
</style>
