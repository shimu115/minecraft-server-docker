# 八、Minecraft Runtime 配置简化与自动版本解析

## 背景

当前配置方式：

```yaml
environment:
  JAVA_VERSION: "8"

  SERVER_TYPE: forge

  DOWNLOAD_URL: "https://maven.minecraftforge.net/net/minecraftforge/forge/1.12.2-14.23.5.2864/forge-1.12.2-14.23.5.2864-installer.jar"
```

存在以下问题：

### 用户体验差

用户需要自行查找：

* Forge 下载链接
* Fabric 下载链接
* NeoForge 下载链接

并手动填写：

```yaml
DOWNLOAD_URL
```

---

### JDK 版本需要用户维护

用户需要了解：

```text
Minecraft 1.12.2 -> Java 8

Minecraft 1.17 -> Java 16

Minecraft 1.18+ -> Java 17

Minecraft 1.20.5+ -> Java 21
```

否则容易出现：

```text
Java Version Error
Unsupported Class Version
```

问题。

---

### 不利于后续面板开发

未来面板创建服务器时：

```text
选择：
Forge

版本：
1.12.2
```

即可完成创建。

面板不应该要求用户填写：

```text
下载链接
JDK版本
```

等实现细节。

---

# 目标

配置简化为：

```yaml
environment:
  SERVER_TYPE: forge

  MC_VERSION: 1.12.2
```

由 Runtime 自动完成：

```text
解析服务端版本

解析下载地址

解析JDK版本

下载服务端

启动服务端
```

---

# 自动JDK选择（P0）

## 配置方式

允许：

```yaml
JAVA_VERSION: auto
```

或者直接省略：

```yaml
MC_VERSION: 1.12.2
```

---

## 自动映射规则

```text
1.7.x ~ 1.16.x
↓
Java 8

1.17.x
↓
Java 16

1.18.x ~ 1.20.4
↓
Java 17

1.20.5+
↓
Java 21
```

---

## 用户强制指定

高级用户允许覆盖：

```yaml
JAVA_VERSION: 17
```

优先使用用户配置。

---

## 镜像要求

镜像内预装：

```text
Java 8

Java 17

Java 21
```

启动时动态切换：

```bash
JAVA_HOME=/usr/lib/jvm/java-17
```

无需用户参与。

---

## 优先级

P0（最高）

原因：

* 实现成本低
* 用户收益极高
* 为后续自动版本解析提供基础能力

---

# 服务端版本自动解析（P1）

## 新配置格式

### Vanilla

```yaml
SERVER_TYPE: vanilla

MC_VERSION: 1.21.1
```

---

### Forge

```yaml
SERVER_TYPE: forge

MC_VERSION: 1.12.2
```

---

### Fabric

```yaml
SERVER_TYPE: fabric

MC_VERSION: 1.20.1
```

---

### NeoForge

```yaml
SERVER_TYPE: neoforge

MC_VERSION: 1.21.1
```

---

# 自动获取服务端版本

## Forge

查询：

```text
https://maven.minecraftforge.net/net/minecraftforge/forge/maven-metadata.xml
```

根据：

```text
MC_VERSION
```

获取对应最新 Forge 版本。

例如：

```text
1.12.2
```

自动解析：

```text
14.23.5.2864
```

并生成：

```text
https://maven.minecraftforge.net/net/minecraftforge/forge/1.12.2-14.23.5.2864/forge-1.12.2-14.23.5.2864-installer.jar
```

---

## Fabric

查询：

```text
Fabric Meta API
```

获取：

```text
Latest Loader
Latest Installer
```

自动生成安装参数。

---

## NeoForge

查询：

```text
NeoForge Maven Metadata
```

获取：

```text
Latest Recommended Version
```

自动生成下载地址。

---

## Vanilla

查询：

```text
Mojang Version Manifest
```

获取：

```text
Server Download URL
```

自动下载服务端。

---

# 实现方式

## 第一阶段

使用：

```text
bash

curl

grep

awk

sed
```

实现。

无需引入额外运行时。

---

## 第二阶段

新增：

```text
agent/bootstrap
```

模块。

职责：

```text
版本解析

下载地址生成

JDK选择

服务端安装
```

由 Go 实现。

---

## 目标架构

```text
start.sh
    ↓

bootstrap
    ↓

获取版本信息
    ↓

选择JDK
    ↓

下载服务端
    ↓

启动Minecraft
```

---

# 长期目标

最终用户配置：

```yaml
environment:

  SERVER_TYPE: forge

  MC_VERSION: 1.12.2
```

即可启动服务端。

无需配置：

```yaml
JAVA_VERSION

DOWNLOAD_URL
```

Runtime 自动完成全部解析逻辑。

---

# 优先级调整

P0

* 自动JDK选择
* 项目目录重构

P1

* Go API模块化
* SpringBoot初始化
* 服务端版本自动解析

P2

* Dashboard
* Console
* File Manager

P3

* 权限系统
* 用户管理

P4

* 实例管理
* Docker SDK

P5

* 多节点管理
* 集群管理

```
```
