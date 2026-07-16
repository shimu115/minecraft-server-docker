# 项目目录结构规范（Project Structure Specification）

## 1. 设计原则

项目采用**按职责分层（Package by Responsibility）**的目录结构。

每个包仅负责一种职责，不同职责之间不得混放。

遵循以下原则：

- 一个包只负责一种职责。
- 包名称使用小写英文。
- 包名称尽量使用业务含义，而非技术实现。
- 同一类型对象统一放置，避免散落在多个目录。
- Controller、Service、Mapper、Model 保持清晰边界。

---

# 2. 推荐目录结构

```text
com.mcpanel.panel
│
├── PanelApplication.java
│
├── common              # 公共组件
│   ├── response        # ApiResponse
│   ├── constant        # 常量
│   ├── enums           # 公共枚举
│   ├── exception       # 公共异常
│   └── util            # 工具类
│
├── config              # Spring 配置
│
├── security            # 认证授权
│   ├── jwt
│   ├── filter
│   └── permission
│
├── aspect              # AOP
│
├── annotation          # 自定义注解
│
├── model               # 所有实体对象
│   ├── request
│   ├── response
│   ├── vo
│   ├── po
│   └── dto
│
├── controller
│
├── service
│   └── impl
│
├── mapper              # MyBatis
│
├── repository          # JPA（使用 JPA 时保留）
│
├── agent
│
├── crypto
│
└── exception           # 业务异常
```

---

# 3. common 包

用于存放整个项目的公共组件。

例如：

```text
common
├── response
│   └── ApiResponse.java
│
├── enums
│   └── ErrorCode.java
│
├── util
│   ├── JsonUtil.java
│   ├── DateUtil.java
│   └── IpUtil.java
│
└── constant
```

common 中禁止存放业务代码。

---

# 4. model 包

项目所有实体统一放置于 model 包。

禁止出现：

```text
dto
entity
bean
model
vo
```

多个同级目录并存。

统一采用：

```text
model
├── request
├── response
├── vo
├── po
└── dto
```

保持所有实体集中管理。

---

# 5. controller

负责 HTTP 请求处理。

仅负责：

- 参数接收
- 参数校验
- 调用 Service
- 返回统一响应

禁止：

- SQL
- 业务逻辑
- 权限计算

---

# 6. service

负责业务逻辑。

可以：

- 聚合多个 Mapper
- 调用 Agent
- 权限判断
- 数据转换

不得：

- 返回 HTTP 响应
- 操作 Servlet

---

# 7. mapper / repository

负责数据库访问。

项目使用 MyBatis 时统一使用：

```text
mapper
```

项目使用 JPA 时统一使用：

```text
repository
```

禁止：

- SQL 与业务逻辑混写。

---

# 8. config

仅存放 Spring 配置。

例如：

```text
SecurityConfig

WebMvcConfig

JacksonConfig

OpenApiConfig
```

工具类不得放入 config。

例如：

```text
JwtUtil
```

应放入：

```text
security/jwt
```

---

# 9. security

所有认证授权统一管理。

例如：

```text
security
├── jwt
│   ├── JwtUtil
│   └── JwtProvider
│
├── filter
│   └── JwtAuthFilter
│
└── permission
```

避免 security 与 config 混合。

---

# 10. aspect

统一存放 AOP。

例如：

```text
aspect
└── InstanceAccessAspect
```

---

# 11. annotation

仅存放自定义注解。

例如：

```text
annotation
└── RequireInstanceAccess
```

不得存放 Aspect。

---

# 12. exception

统一管理业务异常。

例如：

```text
exception
├── McPanelException
├── UnauthorizedException
└── ForbiddenException
```

全局异常处理建议放入：

```text
config
└── GlobalExceptionHandler
```

---

# 13. crypto

负责所有加解密实现。

例如：

```text
crypto
├── AES256GCM
├── RSAUtil
└── KeyValueEncryptor
```

禁止放入业务逻辑。

---

# 14. agent

负责与 Agent 通信。

例如：

```text
AgentClient
AgentRequest
AgentResponse
```

不得直接操作数据库。

---

# 15. 包依赖关系

项目推荐依赖关系如下：

```text
Controller
        │
        ▼
Service
        │
        ├────────────┐
        ▼            ▼
 Mapper      Agent / Crypto
        │
        ▼
 Database
```

禁止：

```text
Controller
        │
        ▼
Mapper
```

禁止：

```text
Controller
        │
        ▼
Repository
```

禁止：

```text
Mapper
        │
        ▼
Service
```

所有依赖均应保持单向。

---

# 16. 核心原则

项目按照职责划分目录，而不是按照技术或开发阶段划分目录。

通过统一目录结构，使开发者能够仅根据包路径快速判断类的职责。

例如：

```text
model.request.CreateUserRequest
```

即可判断：

- 实体类型：Request
- 所属层：Controller 输入
- 用途：创建用户接口

同理：

```text
model.response.LoginResponse
```

表示接口返回对象。

```text
model.vo.ServerVO
```

表示业务对象。

```text
model.po.UserPO
```

表示数据库实体。

统一的目录结构能够有效降低维护成本，提高项目可读性。