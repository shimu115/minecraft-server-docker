FROM eclipse-temurin:8-jdk AS jdk8
FROM eclipse-temurin:17-jdk AS jdk17
FROM eclipse-temurin:21-jdk AS jdk21

FROM debian:bookworm

# 创建目录
RUN mkdir -p /minecraft /usr/lib/jvm

# 安装运行依赖
RUN apt update \
 && apt install -y bash wget screen \
 && rm -rf /var/lib/apt/lists/*

# 从 Eclipse Temurin 官方镜像拷贝 JDK（免费可再分发，无法律风险）
COPY --from=jdk8 /opt/java/openjdk /usr/lib/jvm/8
COPY --from=jdk17 /opt/java/openjdk /usr/lib/jvm/17
COPY --from=jdk21 /opt/java/openjdk /usr/lib/jvm/21

# 拷贝 Minecraft 服务端相关文件和启动脚本
COPY ./start.sh /

# 设置启动脚本可执行
RUN chmod +x /start.sh

# 工作目录
WORKDIR /minecraft

# Minecraft 默认端口
EXPOSE 25565

# 启动命令
CMD ["/start.sh"]
