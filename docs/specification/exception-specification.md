# 异常处理规范（Exception Handling Specification）

## 1. 设计原则

项目所有异常统一使用 `McPanelException` 进行包装，由全局异常处理器 `TryCatchGlobalException` 统一转换为 `ApiResponse.error` 返回。

遵循以下核心原则：

- 业务异常统一使用 `McPanelException`，不直接使用 `RuntimeException` 或 `Exception`。
- **"软失败"打日志继续，"硬失败"抛异常中断**——不存在"抛了但不中断"的中间态。
- 只在必要的边界层 try-catch，不要到处捕获。
- `McPanelException extends RuntimeException`，无需在方法签名中声明 throws。

---

## 2. 核心概念：软失败 vs 硬失败

异常的本质是**中断当前执行流**。一旦 `throw`，方法立即返回，调用栈一路回退到最近的 catch 块。

项目区分两种失败场景：

| 场景 | 判断标准 | 处理方式 |
|------|---------|---------|
| **软失败**（非关键） | 失败后主流程仍可正常完成 | `log.warn` + 继续执行，**不抛异常** |
| **硬失败**（关键） | 失败后后续逻辑无法继续 | `throw new McPanelException(...)`，中断执行 |

### 软失败示例

```java
// 缓存刷新失败不影响用户查询主流程
public UserVO getUserWithCache(Long userId) {
    UserPO po = mapper.selectById(userId);
    if (po == null) {
        throw new McPanelException(ErrorCode.USER_NOT_FOUND);   // 硬失败：用户不存在，必须中断
    }

    try {
        cache.set("user:" + userId, po);
    } catch (Exception e) {
        log.warn("缓存写入失败，不影响主流程 | userId={}", userId, e);  // 软失败：打日志继续
    }

    return toVO(po);
}
```

### 硬失败示例

```java
// 删除操作前必须确认资源存在，否则无法继续
public void deleteKey(Long keyId) {
    KeyPO key = mapper.selectById(keyId);
    if (key == null) {
        throw new McPanelException(ErrorCode.KEY_NOT_FOUND);     // 硬失败：key 不存在，直接中断
    }
    if (key.getInstanceId() != null) {
        throw new McPanelException(ErrorCode.KEY_BOUND_CANNOT_DELETE);  // 硬失败：已绑定，不允许删除
    }
    mapper.deleteById(keyId);
}
```

---

## 3. 各层职责

异常处理在不同层的职责划分如下：

| 层 | 职责 | 说明 |
|----|------|------|
| **Service** | 抛出 `McPanelException` | 业务校验不通过时直接 throw，不 try-catch |
| **Controller** | **不写 try-catch** | 由全局异常处理器统一接管 |
| **Agent / 工具类** | catch 底层异常 → 转译为 `McPanelException` | 在外部调用边界完成翻译 |
| **TryCatchGlobalException** | 统一兜底，转换为 `ApiResponse.error` | 所有未捕获异常最终在此处理 |

### Service 层

```java
// ✅ 直接抛，不捕获
public InstanceVO createInstance(CreateInstanceDTO dto) {
    if (mapper.existsByName(dto.getName())) {
        throw new McPanelException(ErrorCode.INSTANCE_NAME_EXISTS);
    }
    // ... 正常业务逻辑
}

// ❌ 不要在 Service 层做无意义的 catch 再抛
public InstanceVO createInstance(CreateInstanceDTO dto) {
    try {
        if (mapper.existsByName(dto.getName())) {
            throw new McPanelException(ErrorCode.INSTANCE_NAME_EXISTS);
        }
    } catch (McPanelException e) {
        throw e;   // 毫无意义的捕获-再抛出
    }
}
```

### Controller 层

```java
// ✅ Controller 不写 try-catch，全局处理器自动接管
@PostMapping
public ApiResponse<InstanceResponse> create(@RequestBody @Valid CreateInstanceRequest request) {
    InstanceVO vo = instanceService.create(toDTO(request));
    return ApiResponse.success(toResponse(vo));
}

// ❌ 不要在 Controller 手动 catch 并构造 ApiResponse.error
@PostMapping
public ApiResponse<InstanceResponse> create(@RequestBody @Valid CreateInstanceRequest request) {
    try {
        InstanceVO vo = instanceService.create(toDTO(request));
        return ApiResponse.success(toResponse(vo));
    } catch (McPanelException e) {
        return ApiResponse.error(e.getErrorCode());   // 多余，全局处理器已经在做这件事
    }
}
```

### Agent / 工具类（外部调用边界）

```java
// ✅ 在外部调用边界 catch 底层异常，转译为 McPanelException
public AgentResponse refreshKey(String instanceId) {
    try {
        HttpResponse<String> resp = httpClient.send(request, BodyHandlers.ofString());
        return parseResponse(resp);
    } catch (IOException e) {
        throw new McPanelException(ErrorCode.AGENT_UNREACHABLE, e.getMessage());
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new McPanelException(ErrorCode.AGENT_TIMEOUT, e.getMessage());
    }
}
```

---

## 4. 异常类型与使用场景

项目已有以下异常基础设施，不应重复创建：

| 类 | 位置 | 用途 |
|----|------|------|
| `McPanelException` | `exception/` | 自定义业务异常，携带 ErrorCode |
| `ErrorCode` | `common/` | 业务状态码枚举，code 与 msg 强绑定 |
| `TryCatchGlobalException` | `config/` | 全局异常处理器 |

### McPanelException 构造方式

```java
// 方式一：仅携带 ErrorCode，消息使用枚举默认值
throw new McPanelException(ErrorCode.USER_NOT_FOUND);
// → ApiResponse.error(30000, "用户不存在")

// 方式二：携带自定义消息，优先于枚举默认值（适用于需要传递动态信息的场景）
throw new McPanelException(ErrorCode.AGENT_UNREACHABLE, e.getMessage());
// → ApiResponse.error(60000, "Connection refused: connect")
```

### ErrorCode 选择原则

ErrorCode 按业务模块分段管理，选择与异常发生位置最匹配的编码：

| 号段 | 模块 | 枚举示例 |
|------|------|---------|
| `200` | 成功 | `SUCCESS` |
| `10000` ~ `19999` | 通用错误 | `BAD_REQUEST`、`INTERNAL_ERROR` |
| `20000` ~ `29999` | 认证授权 | `UNAUTHORIZED`、`FORBIDDEN`、`INVALID_CREDENTIALS` |
| `30000` ~ `39999` | 用户 | `USER_NOT_FOUND`、`USERNAME_EXISTS` … |
| `40000` ~ `49999` | API Key | `KEY_NOT_FOUND`、`KEY_ALREADY_EXISTS` … |
| `50000` ~ `59999` | 实例 | `INSTANCE_NOT_FOUND`、`INSTANCE_NAME_EXISTS` … |
| `60000` ~ `69999` | Agent 通信 | `AGENT_UNREACHABLE`、`AGENT_TIMEOUT` … |

完整错误码定义参见 [error-code-specification.md](error-code-specification.md)。

新增业务场景时，应在对应模块号段内递增追加，不得硬编码数字。

---

## 5. try-catch 使用准则

**默认不写 try-catch。** 只在以下两种场景使用：

### 场景一：外部系统调用边界

调用外部系统（Go Agent、第三方 API）时，底层抛出的是 `IOException`、`TimeoutException` 等对上层无意义的异常类型，需要在边界层翻译为 `McPanelException`：

```java
// ✅ 边界翻译
try {
    HttpResponse<String> resp = httpClient.send(request, BodyHandlers.ofString());
} catch (IOException e) {
    throw new McPanelException(ErrorCode.AGENT_UNREACHABLE, e.getMessage());
}

// ❌ 让 IOException 直接冒泡到 Controller
public AgentResponse refreshKey(String instanceId) throws IOException {
    // 不要在方法签名上声明 checked exception
}
```

### 场景二：软失败——非关键操作

辅助功能（缓存、日志、统计）失败时，不应对主流程产生影响：

```java
// ✅ 软失败：catch 后打日志继续
public void recordLoginLog(Long userId, String ip) {
    try {
        loginLogMapper.insert(new LoginLogPO(userId, ip, Instant.now()));
    } catch (Exception e) {
        log.warn("登录日志记录失败 | userId={} ip={}", userId, ip, e);
        // 日志记录是辅助功能，失败不中断登录主流程
    }
}
```

### 不应使用 try-catch 的场景

```java
// ❌ 无意义的捕获-再抛出
try {
    return mapper.selectById(id);
} catch (Exception e) {
    throw new McPanelException(ErrorCode.INTERNAL_ERROR, e.getMessage());
}

// ❌ 在 Controller 层捕获 McPanelException（全局处理器已经在做）
try {
    return ApiResponse.success(service.getUser(id));
} catch (McPanelException e) {
    return ApiResponse.error(e.getErrorCode());
}

// ❌ 吞掉异常不处理也不记录
try {
    doSomething();
} catch (Exception e) {
    // 什么都不做，异常静默消失
}
```

---

## 6. 全局异常处理器

项目全局异常处理器 `TryCatchGlobalException` 位于 `config/` 包下，使用 `@RestControllerAdvice` 注解。

当前处理链（按优先级）：

| 优先级 | 异常类型 | 处理方式 |
|--------|---------|---------|
| 1 | `McPanelException` | 提取 ErrorCode → `ApiResponse.error(ec)` |
| 2 | `MethodArgumentNotValidException` | 提取校验错误详情 → `ApiResponse.error(BAD_REQUEST, detail)` |
| 3 | `Exception`（兜底） | 记录 error 日志 → `ApiResponse.error(INTERNAL_ERROR)` |

所有异常最终统一返回 HTTP 200 + `ApiResponse` 格式：

```json
{
    "code": 50000,
    "msg": "MC 实例不存在",
    "data": null
}
```

HTTP 状态码仅用于框架级问题（请求格式错误、认证失败等），业务失败统一使用 `ApiResponse.code` 表示。

### 新增异常类型

如后续需要区分更多异常场景（如自定义的 `ForbiddenException`），应：

1. 在 `exception/` 包下新建异常类，继承 `RuntimeException`
2. 在 `TryCatchGlobalException` 中新增对应的 `@ExceptionHandler` 方法
3. **不需要**修改 Service 或 Controller 层代码

---

## 7. 日志规范

异常处理时必须记录日志，遵循以下标准：

| 异常类型 | 日志级别 | 说明 |
|---------|---------|------|
| 业务校验失败（McPanelException） | `warn` | 预期内的业务错误，无需排查 |
| 外部系统调用失败 | `error` | 可能需要人工介入 |
| 兜底未知异常（Exception） | `error` + 堆栈 | 需要排查代码缺陷 |
| 软失败（非关键操作失败） | `warn` | 不影响主流程，仅记录 |

```java
// ✅ 业务异常：warn 级别
if (user == null) {
    log.warn("用户不存在 | userId={}", userId);
    throw new McPanelException(ErrorCode.USER_NOT_FOUND);
}

// ✅ 软失败：warn 级别
try {
    cache.set(key, value);
} catch (Exception e) {
    log.warn("缓存写入失败 | key={}", key, e);
}

// ❌ 业务异常用 error 级别（告警疲劳）
if (user == null) {
    log.error("用户不存在 | userId={}", userId);   // 预期内的业务情况，不需要 error
    throw new McPanelException(ErrorCode.USER_NOT_FOUND);
}
```

---

## 8. 批量操作中的异常处理

批量操作（列表查询、循环处理）中，一个元素失败通常不应中断整批操作：

```java
// ✅ 批量操作：记录失败项，继续处理
public List<InstanceStatusVO> batchQueryStatus(List<Long> instanceIds) {
    List<InstanceStatusVO> results = new ArrayList<>();
    for (Long id : instanceIds) {
        try {
            results.add(agentClient.queryStatus(id));
        } catch (McPanelException e) {
            log.warn("批量查询单个实例状态失败，跳过 | instanceId={} | {}", id, e.getMessage());
            results.add(InstanceStatusVO.offline(id));   // 返回降级数据
        }
    }
    return results;
}

// ❌ 批量操作：一个失败全部中断
public List<InstanceStatusVO> batchQueryStatus(List<Long> instanceIds) {
    return instanceIds.stream()
        .map(id -> agentClient.queryStatus(id))   // 一个抛异常，整批全挂
        .toList();
}
```

---

## 9. 禁止事项

- ❌ 在 Controller 层手动 try-catch `McPanelException` 并构造 `ApiResponse.error`
- ❌ 在 Service 层捕获异常后不做任何处理（吞异常）
- ❌ 使用 `throws` 关键字在方法签名上声明 checked exception
- ❌ 捕获 `McPanelException` 后原样再抛出
- ❌ 直接使用数字状态码构造 `ApiResponse.error(500, "...")`
- ❌ 在业务代码中使用 `System.out.println` 或 `e.printStackTrace()` 代替日志框架
- ❌ 在循环中逐个 try-catch 替代批量异常处理时，catch 块为空

---

## 10. 核心原则

1. **统一入口**：所有异常最终由 `TryCatchGlobalException` 转换为 `ApiResponse.error`
2. **统一异常类型**：业务异常一律使用 `McPanelException`
3. **统一错误码**：业务状态码统一维护于 `ErrorCode` 枚举
4. **软硬分明**：软失败 log + continue，硬失败 throw
5. **按需捕获**：默认不写 try-catch，只在外部边界和软失败场景使用
6. **日志分级**：业务异常 warn，未知异常 error + 堆栈

通过统一异常处理规范，保证错误信息格式一致，降低排查成本，提高代码可读性。
