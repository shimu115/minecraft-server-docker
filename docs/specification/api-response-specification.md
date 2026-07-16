# ApiResponse 统一响应规范（ApiResponse Specification）

## 1. 设计原则

项目所有 HTTP 接口统一使用 `ApiResponse<T>` 作为响应对象。

统一响应格式能够：

- 保持所有接口返回格式一致。
- 降低前端解析复杂度。
- 统一业务状态码管理。
- 方便后续扩展响应字段。
- 提高项目可维护性。

所有 Controller 接口均应遵循本规范。

---

# 2. 响应格式

统一响应格式如下：

```json
{
    "code": 200,
    "msg": "success",
    "data": {}
}
```

字段说明：

| 字段 | 类型 | 说明 |
|------|------|------|
| code | Integer | 业务状态码 |
| msg | String | 状态描述信息 |
| data | Object | 接口返回数据 |

---

# 3. 返回值规范

Controller 层所有接口统一返回：

```java
ApiResponse<T>
```

例如：

```java
public ApiResponse<UserResponse> detail(...)
```

无返回数据时：

```java
public ApiResponse<Void> delete(...)
```

统一返回：

```java
return ApiResponse.success();
```

---

# 4. 泛型规范

返回业务数据时：

```java
ApiResponse<LoginResponse>
```

返回列表：

```java
ApiResponse<List<UserResponse>>
```

返回分页：

```java
ApiResponse<PageResponse<UserResponse>>
```

无返回数据：

```java
ApiResponse<Void>
```

禁止：

```java
ApiResponse<Object>

ApiResponse<Map<String, Object>>

ApiResponse<HashMap>

ApiResponse
```

所有返回对象均应使用具体实体类型。

---

# 5. 成功响应

返回业务数据：

```java
return ApiResponse.success(response);
```

返回示例：

```json
{
    "code": 200,
    "msg": "success",
    "data": {
        ...
    }
}
```

无业务数据：

```java
return ApiResponse.success();
```

返回示例：

```json
{
    "code": 200,
    "msg": "success",
    "data": null
}
```

---

# 6. 错误响应

统一使用 ErrorCode。

推荐：

```java
return ApiResponse.error(ErrorCode.USER_NOT_FOUND);
```

或者：

```java
throw new McPanelException(ErrorCode.USER_NOT_FOUND);
```

禁止：

```java
return ApiResponse.error(
    500,
    "未知错误"
);
```

除特殊情况外，不应直接使用数字状态码。

所有业务状态码应统一维护于：

```text
ErrorCode.java
```

---

# 7. HTTP 状态码规范

项目采用：

**HTTP 状态码表示 HTTP 请求处理结果。**

**ApiResponse.code 表示业务处理结果。**

正常业务请求统一返回：

```text
HTTP 200
```

业务失败：

```json
{
    "code": 4001,
    "msg": "...",
    "data": null
}
```

HTTP 状态码仅用于表示：

- 请求格式错误
- 身份认证失败
- 权限不足
- 服务异常

业务失败统一使用 `ApiResponse.code` 表示。

---

# 8. Layer 使用规范

ApiResponse 属于 HTTP 接口层对象。

仅允许：

```text
Controller
```

使用。

Controller：

```java
public ApiResponse<UserResponse>
```

Service：

```java
public UserVO
```

Mapper：

```java
public UserPO
```

禁止：

```java
public ApiResponse<UserVO>
```

禁止：

```java
public ApiResponse<UserPO>
```

禁止：

```java
Service 返回 ApiResponse
```

禁止：

```java
Mapper 返回 ApiResponse
```

---

# 9. Response 规范

ApiResponse 的泛型统一使用：

```text
Response
```

例如：

```java
ApiResponse<LoginResponse>

ApiResponse<UserResponse>

ApiResponse<InstanceResponse>
```

禁止：

```java
ApiResponse<UserPO>

ApiResponse<UserVO>

ApiResponse<UserDTO>
```

---

# 10. 使用示例

查询：

```java
@GetMapping("/{id}")
public ApiResponse<UserResponse> detail(
        @PathVariable Long id) {

    UserVO vo = userService.getById(id);

    UserResponse response = ...

    return ApiResponse.success(response);
}
```

删除：

```java
@DeleteMapping("/{id}")
public ApiResponse<Void> delete(
        @PathVariable Long id) {

    userService.delete(id);

    return ApiResponse.success();
}
```

---

# 11. 禁止事项

禁止直接返回：

```java
UserResponse
```

禁止：

```java
ResponseEntity<UserResponse>
```

禁止：

```java
Map<String, Object>
```

禁止：

```java
Object
```

禁止：

```java
String
```

所有 HTTP 接口均应统一返回：

```java
ApiResponse<T>
```

---

# 12. 核心原则

统一响应对象仅负责描述 HTTP 接口返回结果。

职责划分如下：

| 对象 | 职责 |
|------|------|
| ApiResponse | 统一响应包装 |
| Response | 接口返回数据 |
| VO | Service 业务对象 |
| PO | 数据库对象 |
| ErrorCode | 统一业务状态码 |

通过统一响应规范，保证所有接口返回格式一致，提高接口可维护性与前后端协作效率。