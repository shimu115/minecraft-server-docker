# MC Server API 测试面板

基于 Vue 3 + Vite 的 API 测试页面，用于调试 Minecraft Server Docker 的 Go API。

## 说明

- 本测试页面**不会**打包进 Minecraft Server Docker 镜像
- 仅在开发/调试时手动启动，通过浏览器访问
- 需要 Go API 服务已启动（默认端口 `25560`）

## 本地启动

```bash
cd test
npm install
npm run dev
```

浏览器打开 `http://localhost:25561`，在 API 连接面板填入 API Key 点击连接即可。

> 留空 API 地址时，Vite 自动将 `/api/*` 代理到 `http://localhost:25560`，无需处理跨域。

## Docker 部署

如需将测试页面也部署为 Docker 容器，可自行编写 Dockerfile：

### Dockerfile

```dockerfile
FROM node:22-alpine AS builder
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:alpine
COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
```

### nginx.conf

```nginx
server {
    listen 80;
    server_name localhost;

    location / {
        root /usr/share/nginx/html;
        index index.html;
        try_files $uri $uri/ /index.html;
    }

    location /api/ {
        proxy_pass http://host.docker.internal:25560;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_read_timeout 86400s;
        proxy_buffering off;
    }
}
```

### docker-compose.yaml

```yaml
services:
  mc-api-test:
    build: .
    container_name: mc-api-test
    ports:
      - "8080:80"
    extra_hosts:
      - "host.docker.internal:host-gateway"
```

### 构建与启动

```bash
# 构建
docker build -t mc-api-test .

# 启动
docker run -d -p 8080:80 --add-host host.docker.internal:host-gateway mc-api-test

# 或使用 docker compose
docker compose up -d
```

浏览器打开 `http://localhost:8080`，API 地址填写 `http://host.docker.internal:25560`（或 Go API 服务的实际地址）即可。

## 功能

| 模块 | 功能 |
|------|------|
| API 连接 | 输入 API Key 和地址，保存到 localStorage |
| 服务端状态 | 查看运行状态、玩家数、版本、运行时长 |
| 服务端控制 | 启动 / 停止 / 重启 MC 服务端 |
| 发送指令 | 向 MC 控制台发送任意指令 |
| 实时日志 | SSE 实时推送，支持 tail 参数和自动滚动 |
| 文件管理 | 多选、上传、下载、删除、在线编辑、导出 |
