FROM debian:bookworm

# 创建目录
RUN mkdir -p /minecraft

# 安装运行依赖
RUN apt update \
 && apt install -y bash wget screen tar \
 && rm -rf /var/lib/apt/lists/*

# 拷贝启动脚本
COPY ./start.sh /
COPY ./run.sh /minecraft/

# 设置启动脚本可执行
RUN chmod +x /start.sh /minecraft/run.sh

# 工作目录
WORKDIR /minecraft

# Minecraft 默认端口
EXPOSE 25565

# 启动命令
CMD ["/start.sh"]
