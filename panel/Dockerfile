# Stage 1: Build Vue frontend
FROM node:20-alpine AS web-builder
ARG CACHE_BUST=unknown
WORKDIR /src/web
COPY web/package.json web/package-lock.json* ./
RUN npm ci 2>/dev/null || npm install
COPY web/ ./
RUN echo "Cache bust: ${CACHE_BUST}"
RUN npm run build
# Output: /src/backend/src/main/resources/static/ (vite outDir = ../backend/...)

# Stage 2: Build Spring Boot backend
FROM maven:3.9-eclipse-temurin-21 AS java-builder
ARG CACHE_BUST=unknown
RUN echo "Cache bust: ${CACHE_BUST}"
WORKDIR /src
COPY backend/pom.xml .
RUN mvn dependency:go-offline -B 2>/dev/null || true
COPY backend/src ./src
COPY --from=web-builder /src/backend/src/main/resources/static/ ./src/main/resources/static/
RUN mvn package -DskipTests -B -q

# Stage 3: Runtime
FROM eclipse-temurin:21-jre
WORKDIR /app

# 创建数据目录（H2 文件存储）
RUN mkdir -p /app/data

COPY --from=java-builder /src/target/*.jar app.jar

EXPOSE 8080

ENV TZ=Asia/Shanghai \
    LANG=C.UTF-8

ENTRYPOINT ["java", "-jar", "app.jar"]
