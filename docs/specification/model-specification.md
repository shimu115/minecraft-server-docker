### 相应与接口返回规范
项目下存在 ApiResponse 的一个类，这个是用来统一相应模板的，每一个 controller 的接口都应该使用 ApiResponse 来返回数据，类的返回类型也应该使用 ApiResponse<T> 的形式来定义
例：
~~~java
public ApiResponse<UserResponse> getUser(@PathVariable("id") String id) {
    return ApiResponse.success(userService.getUser(id));
}
~~~ 
按照上述的情况每个接口返回的数据也应是具体定义的一个实体类。


# 实体对象（Model）规范

## 1. 设计原则

项目中所有实体对象统一放置于 `model` 包下，并按照职责进行分类管理。

```text
model
├── request
├── response
├── po
├── vo
└── dto（可选）
```

不同类型的实体承担不同职责，严禁混用。

遵循以下原则：

- 一个实体仅负责一种职责（Single Responsibility）。
- 不同层之间不得直接传递不属于当前层的实体对象。
- 实体命名应能够直接体现其用途，降低代码阅读成本。
- Controller、Service、Mapper 各层之间应保持清晰的边界。

---

# 2. Request（请求对象）

## 职责

用于接收客户端（HTTP）请求参数。

Request 是接口输入模型，仅用于描述客户端提交的数据。

## 使用规范

- 只能作为 Controller 层接口参数。
- 必须放置于 `model.request` 包下。
- 可以使用 Bean Validation 参数校验注解。
- 不允许作为 Service、Mapper 层参数继续向下传递。
- 不允许包含业务逻辑。

## 示例

```java
public class CreateUserRequest {

    @NotBlank
    private String username;

    @NotBlank
    private String password;

}
```

```java
@PostMapping
public ApiResponse<UserResponse> create(
        @RequestBody @Valid CreateUserRequest request) {

}
```

---

# 3. Response（响应对象）

## 职责

用于定义接口最终返回的数据结构。

Response 属于接口响应模型，仅用于 Controller 层返回客户端的数据。

一个接口应对应一个 Response。

Response 可以组合多个业务对象（VO）。

## 使用规范

- 只能作为 Controller 层返回值。
- 必须放置于 `model.response` 包下。
- 不允许作为 Service 层返回值。
- 可以包含多个 VO。
- 可以包含分页信息、统计信息、扩展字段等接口专属内容。

## 示例

```java
public class DashboardResponse {

    private UserVO user;

    private List<MenuVO> menus;

    private List<ServerVO> servers;

}
```

Controller：

```java
@GetMapping("/dashboard")
public ApiResponse<DashboardResponse> dashboard() {

}
```

---

# 4. VO（View Object）

## 职责

用于表示一个完整的业务展示对象。

VO 是业务层模型，不依赖 HTTP，可以聚合多个数据库对象（PO），也可以经过业务计算后生成。

VO 应描述一个完整的业务对象，而不是某个接口的数据结构。

## 使用规范

- 放置于 `model.vo` 包下。
- 可以作为 Service 层返回值。
- 可以被多个接口复用。
- 可以聚合多个 PO。
- 不允许直接映射数据库。
- 不允许作为数据库实体使用。

## 示例

```java
public class UserVO {

    private Long id;

    private String username;

    private List<RoleVO> roles;

}
```

或

```java
public class MenuVO {

    private String name;

    private String path;

    private String icon;

    private List<MenuVO> children;

}
```

---

# 5. PO（Persistence Object）

## 职责

用于表示数据库持久化对象。

PO 与数据库表保持一致，仅用于数据库映射。

## 使用规范

- 放置于 `model.po` 包下。
- 一个 PO 对应一张数据库表。
- 字段应尽可能与数据库字段保持一致。
- Mapper 层返回 PO。
- 不允许直接返回给前端。
- 不允许包含业务逻辑。

## 示例

```java
@TableName("sys_user")
public class UserPO {

    private Long id;

    private String username;

    private String password;

    private Integer deleted;

}
```

---

# 6. DTO（可选）

## 职责

用于 Service 层之间的数据传输。

DTO 不属于数据库对象，也不属于接口对象。

适用于复杂业务流程中的对象传递。

## 使用规范

- 放置于 `model.dto` 包下。
- 不允许作为 Controller 入参。
- 不允许作为 Controller 返回值。
- 可以在多个 Service 之间传递。

---

# 7. 各实体之间关系

```text
                HTTP Request
                     │
                     ▼
              Request（接口请求）
                     │
                     ▼
                Controller
                     │
                     ▼
             Service（业务处理）
                     │
          ┌──────────┴──────────┐
          ▼                     ▼
        DTO（可选）          Mapper
                                  │
                                  ▼
                              PO（数据库）
                                  │
                                  ▼
                             Service 聚合
                                  │
                                  ▼
                           VO（业务对象）
                                  │
                                  ▼
                    Response（接口响应）
                                  │
                                  ▼
                           HTTP Response
```

---

# 8. 命名规范

所有实体统一采用以下命名方式：

| 类型 | 后缀 | 示例 |
|------|------|------|
| 请求对象 | Request | CreateUserRequest |
| 响应对象 | Response | DashboardResponse |
| 业务对象 | VO | UserVO |
| 持久化对象 | PO | UserPO |
| 数据传输对象 | DTO | CreateUserDTO |

禁止使用以下模糊命名：

```text
UserModel
UserEntity
UserBean
UserData
CommonVO
ResultVO
BaseModel
```

名称应能够准确体现对象职责。

---

# 9. 使用约束

| 层级 | 入参 | 返回值 |
|------|------|--------|
| Controller | Request | ApiResponse<Response> |
| Service | DTO / 基本类型 | VO / DTO / 基本类型 |
| Mapper | PO / 基本类型 | PO / List<PO> |

禁止出现以下情况：

❌ Controller 返回 PO

```java
public ApiResponse<UserPO> getUser();
```

❌ Service 返回 Response

```java
public UserResponse getUser();
```

❌ Mapper 返回 VO

```java
public UserVO selectById(Long id);
```

❌ Service 接收 Request

```java
public void create(CreateUserRequest request);
```

---

# 10. 核心原则

项目中的实体对象遵循以下职责划分：

- Request：描述接口输入。
- Response：描述接口输出。
- VO：描述业务对象。
- PO：描述数据库对象。
- DTO：描述服务之间的数据传输对象。

**VO 可以脱离 HTTP 独立存在，而 Response 必须依赖于具体接口。**

一个 VO 可以被多个 Response 复用，而一个 Response 应仅对应一个接口。

通过明确职责边界，使代码具备良好的可读性、可维护性和可扩展性。

---

# 11. 参数校验规范

## 校验策略

项目采用分层校验策略：

| 层级 | 校验方式 | 适用场景 |
|------|---------|---------|
| Controller | `@Valid` + Bean Validation 注解 | 字段非空、格式、长度等基础校验 |
| Service | 业务逻辑校验（手动编码） | 唯一性、权限、状态机等复杂业务规则 |

## Controller 层校验

- 简单参数校验使用 Bean Validation 注解（`@NotNull`、`@NotBlank`、`@Size` 等）
- 统一使用 `@Valid` 触发校验，校验失败由全局异常处理器统一处理
- 不要在 Controller 中手动检查校验结果

```java
public class CreateUserRequest {

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 32, message = "密码长度 6-32 位")
    private String password;

}
```

## Service 层校验

- 涉及数据库查询的业务规则校验（如唯一性检查）放在 Service 层
- 校验失败抛出对应业务异常，由全局异常处理器统一处理
- 禁止在 Service 中使用 Bean Validation 注解校验入参

```java
public void create(CreateUserDTO dto) {
    if (userMapper.existsByUsername(dto.getUsername())) {
        throw new BusinessException(USER_ALREADY_EXISTS);
    }
    // 业务逻辑...
}
```

## 禁止事项

- ❌ 在 Controller 中手动 if-else 校验参数格式（应使用注解）
- ❌ 在 Service 中使用 `@Valid` 校验 DTO（注解校验属于 Controller 层职责）
- ❌ 校验失败后返回 `false` 或自定义对象（必须抛异常，由全局处理器统一处理）

---

> 所有 Request 与 Response 应提供完整的接口文档注解。
> 
> 字段说明应统一维护在实体类中，由 OpenAPI 文档自动生成接口说明。
> 
> 禁止在 API.md 中重复维护字段定义。
> 
> 推荐：
> @Schema(description = "...")
> 
> 避免：
> 
> 在多个文档中重复维护字段说明。
