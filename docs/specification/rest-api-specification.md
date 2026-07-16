# REST API 规范（REST API Specification）

## 1. 设计原则

项目所有 HTTP 接口均应遵循 RESTful 风格设计。

设计目标：

- URI 表示资源（Resource）。
- HTTP Method 表示操作行为。
- 接口职责清晰。
- 接口命名统一。
- 接口文档统一维护。

---

# 2. URI 命名规范

URI 应表示资源，而不是行为。

推荐：

```text
/users
/users/{id}

/instances
/instances/{id}

/keys
/keys/{id}
```

禁止：

```text
/getUser
/queryUser
/deleteUser
/createUser
```

URI 使用：

- 小写英文
- 多个单词使用 "-"
- 使用复数名词

例如：

```text
/api-keys

/server-instances
```

---

# 3. HTTP Method

统一使用 HTTP Method 表示操作。

| Method | 说明 |
|----------|------|
| GET | 查询资源 |
| POST | 创建资源或执行业务动作 |
| PUT | 更新资源 |
| DELETE | 删除资源 |
| PATCH | 局部更新（可选） |

例如：

```text
GET     /users

GET     /users/{id}

POST    /users

PUT     /users/{id}

DELETE  /users/{id}
```

---

# 4. Path 参数

资源唯一标识统一放置于 URI。

推荐：

```text
/users/{id}

/instances/{id}
```

不推荐：

```text
/users?id=1
```

（查询单个资源）

---

# 5. Query 参数

筛选、排序、分页统一使用 Query。

例如：

```text
GET /users?page=1&pageSize=20

GET /users?keyword=admin

GET /servers?status=ONLINE
```

---

# 6. 业务动作接口

对于无法使用 CRUD 描述的业务操作，应在资源后追加动作名称。

统一格式：

```text
POST /resources/{id}/action
```

例如：

```text
POST /instances/{id}/start

POST /instances/{id}/stop

POST /instances/{id}/restart

POST /instances/{id}/bind-key

POST /keys/{id}/refresh
```

禁止：

```text
/startInstance

/start/{id}

/bindKey

/refreshKey
```

---

# 7. Controller 开发规范

Controller 负责：

- 接收请求
- 参数校验
- 调用 Service
- 返回统一响应

不得：

- 编写业务逻辑
- 操作数据库
- 调用 Mapper

接口统一返回：

```java
ApiResponse<Response>
```

例如：

```java
public ApiResponse<LoginResponse> login(
        @RequestBody LoginRequest request)
```

---

# 8. JavaDoc 规范

所有 Controller 的公开接口必须编写 JavaDoc。

推荐格式：

```java
/**
 * 用户登录。
 *
 * 验证用户名和密码，成功后返回 JWT Token。
 *
 * @param request 登录请求
 * @return 登录结果
 */
@PostMapping("/login")
public ApiResponse<LoginResponse> login(
        @RequestBody LoginRequest request) {

}
```

要求：

- 描述接口作用。
- 描述请求参数。
- 描述返回值。
- 不要求描述实现细节。

---

# 9. OpenAPI 文档规范

项目应集成 OpenAPI（如 SpringDoc），自动生成接口文档。

推荐访问地址：

```text
/swagger-ui/index.html
```

所有 Request 与 Response 应提供完整的字段说明。

推荐：

```java
@Schema(description = "用户名")
private String username;
```

字段说明统一维护于实体类。

禁止在多个位置重复维护字段说明。

---

# 10. API 文档规范

项目必须维护接口索引文档。

文档位置：

```text
src/main/resources/doc/api.md
```

API.md 用于快速查阅项目全部接口。

推荐按照模块分类：

```text
认证（Auth）

用户（User）

API Key

服务端（Instance）
```

API.md 主要记录：

- 请求方式
- 请求路径
- Request
- Response
- 权限要求
- 接口说明

字段定义统一维护于 Request 与 Response。

API.md 不记录字段详情。

---

# 11. 文档维护规范

新增接口时，必须完成：

- 编写 Controller JavaDoc。
- 为 Request 与 Response 补充 OpenAPI 字段说明。
- 更新 API.md。

修改接口时：

- 更新 JavaDoc。
- 更新 OpenAPI 注释。
- 更新 API.md。

删除接口时：

- 删除 API.md 中对应内容。

保证代码与文档保持一致。

---

# 12. 接口安全规范

接口路径不属于敏感信息。

不得通过隐藏 URI 或加密 URI 提升安全性。

接口安全应依赖：

- JWT
- RBAC 权限控制
- 参数校验
- 数据权限控制
- 限流
- 审计日志

对于 API Key、Token、密码等敏感数据，应采用：

- 加密存储
- 权限控制
- 脱敏展示

而不是隐藏接口路径。

---

# 13. 文档职责

整个项目的接口文档职责如下：

| 文档 | 职责 |
|------|------|
| Controller JavaDoc | 描述接口业务 |
| Request / Response | 描述字段含义 |
| OpenAPI（Swagger UI） | 自动生成接口说明 |
| API.md | 提供接口导航与索引 |

所有信息仅维护一处，避免重复维护导致文档与代码不一致。

---

# 14. 核心原则

REST API 应遵循资源导向设计。

- URI 表示资源。
- HTTP Method 表示行为。
- Request 描述接口输入。
- Response 描述接口输出。
- ApiResponse 描述统一响应格式。
- OpenAPI 描述字段定义。
- API.md 提供接口导航。

保证接口风格统一，提高代码可维护性与团队协作效率。