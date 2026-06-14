# Minecraft Server API 文档

## 基础信息

| 项目 | 说明 |
|------|------|
| 默认端口 | `25560`（可通过 `API_PORT` 环境变量修改） |
| 认证方式 | `Authorization: Bearer <api-key>`（除 `/api/health` 外所有接口必须） |
| Content-Type | `application/json`（除 `/api/logs` 外） |
| API Key 存储位置 | `./auth/api_key.txt`（容器内 `/minecraft/auth/api_key.txt`） |

## 认证

### 获取 API Key

容器首次启动时自动生成并打印到控制台：

```
[mc-api] New API Key generated: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
```

### 请求头

所有需要认证的接口必须携带：

```
Authorization: Bearer <api-key>
```

---

## 统一响应格式

### 成功

```json
{
  "code": 200,
  "status": "ok",
  "message": "...",
  "data": {}
}
```

### 失败

```json
{
  "code": 403,
  "status": "error",
  "message": "missing or invalid api key"
}
```

### 常见状态码

| code | 说明 |
|------|------|
| 200 | 成功 |
| 400 | 请求参数错误 |
| 403 | 未认证（缺少或无效的 API Key） |
| 409 | 冲突（如服务端已在运行） |
| 500 | 服务端内部错误 |
| 503 | 服务不可用（如 MC 服务端未运行） |

---

## API 列表

### 1. 健康检查

```
GET /api/health
```

**认证**：不需要

**响应**：

```json
{
  "code": 200,
  "status": "ok",
  "data": {
    "mc_server_running": true
  }
}
```

---

### 2. 服务端状态

```
GET /api/server/status
```

**认证**：需要

**响应**：

```json
{
  "code": 200,
  "status": "ok",
  "data": {
    "running": true,
    "players": 0,
    "uptime": "23:39:09",
    "version": "1.12.2"
  }
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| running | bool | 是否运行中 |
| players | int | 在线玩家数 |
| uptime | string | 运行时长 |
| version | string | Minecraft 版本 |

`players` 通过发送 `list` 指令后解析日志获取，未运行时返回 `0`。

---

### 3. 启动服务端

```
POST /api/server/start
```

**认证**：需要

**响应**：

```json
{
  "code": 200,
  "status": "ok",
  "message": "Server starting"
}
```

**错误**：

| code | 说明 |
|------|------|
| 409 | 服务端已在运行 |

---

### 4. 停止服务端

```
POST /api/server/stop
```

**认证**：需要

**响应**：

```json
{
  "code": 200,
  "status": "ok",
  "message": "Stop command sent"
}
```

**错误**：

| code | 说明 |
|------|------|
| 503 | 服务端未运行 |

---

### 5. 重启服务端

```
POST /api/server/restart
```

**认证**：需要

**说明**：发送 `stop` 指令，等待最多 30 秒后自动重新启动。

**响应**：

```json
{
  "code": 200,
  "status": "ok",
  "message": "Server starting"
}
```

**错误**：

| code | 说明 |
|------|------|
| 503 | 服务端未运行 |

---

### 6. 发送指令

```
POST /api/command
```

**认证**：需要

**请求体**：

```json
{
  "command": "say Hello World"
}
```

**响应**：

```json
{
  "code": 200,
  "status": "ok",
  "message": "Command sent"
}
```

**错误**：

| code | 说明 |
|------|------|
| 400 | 指令为空 |
| 503 | 服务端未运行 |

---

### 7. 实时日志（SSE）

```
GET /api/logs?tail=200
```

**认证**：需要

**说明**：Server-Sent Events 流，实时推送 Minecraft 服务端日志。

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| tail | int | — | 连接后先回放最近 N 行日志，不填则只推新日志 |

**SSE 事件格式**：

```
data: [14:58:28] [Server thread/INFO]: Starting minecraft server version 1.12.2
```

每行日志为一个 SSE 事件。日志轮转时发送：

```
event: rotation
data: Log rotated
```

**心跳**：每 15 秒发送 SSE 注释 `: heartbeat` 保持连接。

**示例**：

```bash
curl -H "Authorization: Bearer <key>" "http://localhost:25560/api/logs?tail=100"
```

---

### 8. 刷新 API Key

```
POST /api/auth/refresh
```

**认证**：需要（使用当前有效的 Key）

**说明**：重新生成 API Key，旧 Key 立即失效，`api_key.txt` 文件同步更新。

**响应**：

```json
{
  "code": 200,
  "status": "ok",
  "message": "API Key refreshed",
  "data": {
    "api_key": "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
  }
}
```

---

### 9. FTP 管理

#### 9.1 启动 FTP

```
POST /api/ftp/start
```

**认证**：需要

**请求体**（可选，不传使用默认值）：

```json
{
  "port": 21,
  "username": "root",
  "password": "minecraft"
}
```

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| port | int | 21 | FTP 监听端口 |
| username | string | root | FTP 登录用户 |
| password | string | minecraft | FTP 登录密码 |

**响应**：

```json
{
  "code": 200,
  "status": "ok",
  "message": "FTP started",
  "data": {
    "running": true,
    "port": 21
  }
}
```

**错误**：

| code | 说明 |
|------|------|
| 409 | FTP 已在运行 |

#### 9.2 停止 FTP

```
POST /api/ftp/stop
```

**认证**：需要

**响应**：

```json
{
  "code": 200,
  "status": "ok",
  "message": "FTP stopped"
}
```

**错误**：

| code | 说明 |
|------|------|
| 503 | FTP 未运行 |

#### 9.3 FTP 状态

```
GET /api/ftp/status
```

**认证**：需要

**响应**：

```json
{
  "code": 200,
  "status": "ok",
  "data": {
    "running": true,
    "port": 21
  }
}
```

---

### 10. 文件管理

文件操作限定在工作目录范围内，无法访问上级目录。

#### 10.1 列出目录

```
GET /api/files/list?path=<相对路径>
```

**认证**：需要

| 参数 | 类型 | 说明 |
|------|------|------|
| path | string | 相对于工作目录的路径，为空表示根目录 |

**响应**：

```json
{
  "code": 200,
  "status": "ok",
  "data": [
    {
      "name": "server.jar",
      "path": "server.jar",
      "isDir": false,
      "size": 45217654,
      "modTime": "2026-06-14 15:30:00"
    },
    {
      "name": "logs",
      "path": "logs",
      "isDir": true,
      "size": 4096,
      "modTime": "2026-06-14 16:00:00"
    }
  ]
}
```

目录排在文件前面，按名称排序。

#### 10.2 读取文件

```
GET /api/files/read?path=<相对路径>
```

**认证**：需要

| 参数 | 类型 | 说明 |
|------|------|------|
| path | string | 文件相对路径（必填） |

**响应**：

```json
{
  "code": 200,
  "status": "ok",
  "data": {
    "path": "server.properties",
    "content": "enable-command-block=true\n..."
  }
}
```

#### 10.3 写入文件

```
POST /api/files/write?path=<相对路径>
```

**认证**：需要

| 参数 | 类型 | 说明 |
|------|------|------|
| path | string | 文件相对路径（必填） |

**请求体**：

```json
{
  "content": "新的文件内容"
}
```

**响应**：

```json
{
  "code": 200,
  "status": "ok",
  "message": "File saved"
}
```

#### 10.4 删除文件

```
DELETE /api/files/delete?path=<相对路径>
```

**认证**：需要

| 参数 | 类型 | 说明 |
|------|------|------|
| path | string | 文件/目录相对路径（必填） |

**响应**：

```json
{
  "code": 200,
  "status": "ok",
  "message": "Deleted"
}
```

---

## 环境变量参考

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `API_PORT` | `25560` | API 监听端口 |
| `MC_DIR` | `.` | Minecraft 数据目录 |
| `AUTO_START` | `true` | 启动 API 后自动启动 MC 服务端 |

---

## curl 示例

```bash
# 设置变量
HOST="http://localhost:25560"
KEY="your-api-key-here"
AUTH="Authorization: Bearer $KEY"

# 健康检查（无需认证）
curl $HOST/api/health

# 查看状态
curl -H "$AUTH" $HOST/api/server/status

# 启动服务端
curl -X POST -H "$AUTH" $HOST/api/server/start

# 停止服务端
curl -X POST -H "$AUTH" $HOST/api/server/stop

# 发送指令
curl -X POST -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"command":"say Hello"}' $HOST/api/command

# 查看日志（SSE 流）
curl -H "$AUTH" "$HOST/api/logs?tail=100"

# 刷新 API Key
curl -X POST -H "$AUTH" $HOST/api/auth/refresh

# FTP 管理
curl -X POST -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"port":21}' $HOST/api/ftp/start
curl -H "$AUTH" $HOST/api/ftp/status
curl -X POST -H "$AUTH" $HOST/api/ftp/stop

# 文件管理
curl -H "$AUTH" "$HOST/api/files/list?path="
curl -H "$AUTH" "$HOST/api/files/read?path=server.properties"
curl -X POST -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"content":"enable-command-block=true"}' \
  "$HOST/api/files/write?path=server.properties"
curl -X DELETE -H "$AUTH" "$HOST/api/files/delete?path=old-file.txt"
```
