# Stage 1: 编译 Go API
FROM golang:1.23 AS builder
WORKDIR /build
COPY api/go.mod api/go.sum* ./
RUN go mod download 2>/dev/null || true
COPY api/ ./
RUN CGO_ENABLED=0 GOOS=linux go build -ldflags="-s -w" -o /mc-api .

# Stage 2: 最终运行镜像
FROM debian:bookworm

# 创建目录
RUN mkdir -p /minecraft

# 安装运行依赖
RUN apt update \
 && apt install -y bash wget screen tar vsftpd \
 && rm -rf /var/lib/apt/lists/*

# 拷贝启动脚本
COPY ./start.sh /

# 设置启动脚本可执行
RUN chmod +x /start.sh

# 拷贝 Go API 二进制
COPY --from=builder /mc-api /usr/local/bin/mc-api

# 工作目录
WORKDIR /minecraft

# Minecraft 默认端口 + API 端口 + FTP 端口 + FTP 被动模式端口范围
EXPOSE 25565 25560 21 30000-30009

# 启动命令
CMD ["/start.sh"]
