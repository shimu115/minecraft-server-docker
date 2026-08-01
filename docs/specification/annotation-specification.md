# 注解使用规范

本规范定义项目中各类注解的统一使用方式，确保代码风格一致、问题可快速定位。

---

# 1. Lombok 注解

## 1.1 日志：@Slf4j

> ⚠ **强制要求：所有类统一使用 `@Slf4j`，禁止手动创建 Logger。**

### ✅ 正确

```java
@Slf4j
@Service
public class InstanceServiceImpl implements InstanceService {

    public void doSomething() {
        log.info("实例创建成功 | instanceId={}", id);
    }
}
```

### ❌ 错误

```java
// 禁止手动创建 Logger
@Service
public class InstanceServiceImpl implements InstanceService {

    private static final Logger log = LoggerFactory.getLogger(InstanceServiceImpl.class);
    // ...
}
```

### 理由

- `@Slf4j` 编译期生成 `log` 字段，零性能开销
- 当前项目中 6/7 的类已使用 `@Slf4j`，1 个不一致（`InstanceServiceImpl`）
- 使用注解后，无需手动维护类名，重构更安全

### 清理要求

使用 `@Slf4j` 的类必须删除残留的 `import org.slf4j.Logger` 和 `import org.slf4j.LoggerFactory`。

当前需清理的文件：
- `AgentClient.java`
- `InstanceAccessAspect.java`
- `ServerController.java`
- `DataInitializer.java`
- `TryCatchGlobalException.java`

---

## 1.2 实体/DTO：@Data vs @Getter

| 类类型 | 推荐注解 | 说明 |
|--------|---------|------|
| DTO（Request/Response 等） | `@Data` | 需要 getter/setter/equals/hashCode/toString |
| 实体（PO/JPA Entity） | `@Data` | 同上 |
| 异常类 | `@Getter` | 仅需 getter，不需要 setter |
| 枚举 | `@Getter` | 仅需 getter |
| 工具类响应（如 ApiResponse） | `@Getter` + `@Builder` | 不可变对象，仅需 getter |

### 理由

- `@Data` 生成全部样板方法，适合数据载体
- `@Getter` 用于不可变或仅需读取的场景
- 禁止无差别使用 `@Data`（如异常类用 `@Data` 会暴露 setter，语义不对）

### ✅ 正确

```java
// DTO：@Data 覆盖全部需求
@Data
public class CreateInstanceRequest {
    @NotBlank
    private String name;
}

// 异常：仅 @Getter
@Getter
public class McPanelException extends RuntimeException {
    private final ErrorCode errorCode;
}

// 不可变响应：@Getter + @Builder
@Getter
@Builder
public class ApiResponse<T> {
    private final int code;
    private final String msg;
    private final T data;
}
```

---

## 1.3 构造器：@AllArgsConstructor vs @RequiredArgsConstructor

| 注解 | 适用场景 |
|------|---------|
| `@AllArgsConstructor` | DTO/Response，所有字段都是可选的 |
| `@RequiredArgsConstructor` | Service/Component，仅 `final` 字段参与构造注入 |
| 显式构造器 | 构造逻辑复杂（如 `AgentClient` 手动创建 `RestClient`） |

### @RequiredArgsConstructor 与构造器注入的关系

`@RequiredArgsConstructor` 为所有 `final` 字段和 `@NonNull` 字段生成构造器，**天然适配 Spring 构造器注入**（见 [2.1 节](#21-依赖注入构造器注入)）。

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class InstanceServiceImpl implements InstanceService {

    private final ServerInstanceRepository instanceRepository;
    private final ApiKeyRepository apiKeyRepository;
    private final AgentClient agentClient;
    // Lombok 生成：public InstanceServiceImpl(repository1, repository2, agentClient) { ... }
}
```

---

# 2. Spring 注解

## 2.1 依赖注入：构造器注入

> ⚠ **强制要求：统一使用构造器注入（`@RequiredArgsConstructor` + `final`），禁止字段注入（`@Autowired` 在字段上）。**

### ✅ 正确

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class InstanceServiceImpl implements InstanceService {

    private final ServerInstanceRepository instanceRepository;
    private final ApiKeyRepository apiKeyRepository;
    private final AgentClient agentClient;
}
```

### ❌ 错误

```java
@Service
public class InstanceServiceImpl implements InstanceService {

    @Autowired
    private ServerInstanceRepository instanceRepository;
    @Autowired
    private ApiKeyRepository apiKeyRepository;
    @Autowired
    private AgentClient agentClient;
}
```

### 理由

| 维度 | 构造器注入 | 字段注入 |
|------|-----------|---------|
| 不可变性 | ✅ `final` 字段，编译器保证不篡改 | ❌ 可变字段 |
| 单元测试 | ✅ 无需 Spring 容器，直接 new + mock 传参 | ❌ 必须启动 Spring 或反射注入 |
| 依赖可见性 | ✅ 构造器参数一目了然 | ❌ 字段分散，依赖隐式 |
| 循环依赖 | ✅ 启动时报错 | ⚠ 可能运行时才发现 |
| 代码量 | 与 `@RequiredArgsConstructor` 一致 | 一致 |

### @Value 的处理

`@Value` 同样应通过构造器注入：

```java
@Component
@Slf4j
public class DataInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String rootUsername;
    private final String rootPassword;

    public DataInitializer(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.root-user.username}") String rootUsername,
            @Value("${app.root-user.password}") String rootPassword) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.rootUsername = rootUsername;
        this.rootPassword = rootPassword;
    }
}
```

或使用 `@RequiredArgsConstructor` + `@Value` 无法在字段上用的替代方案（推荐使用 `@ConfigurationProperties` 绑定配置类）。

---

## 2.2 声明型注解

| 注解 | 使用位置 | 说明 |
|------|---------|------|
| `@RestController` | Controller 层 | 组合 `@Controller` + `@ResponseBody` |
| `@Service` | Service 实现类 | 标记业务逻辑层 |
| `@Repository` | MyBatis Mapper | 标记数据访问层 |
| `@Component` | 工具类/AOP/客户端 | 通用 Spring Bean |
| `@Configuration` | 配置类 | 标记 `@Bean` 工厂类 |
| `@RestControllerAdvice` | 全局异常处理 | 组合 `@ControllerAdvice` + `@ResponseBody` |

以上项目中已统一，继续沿用。

### @Transactional

- 仅标注在 **Service 层方法**上
- 读操作无需事务（`@Transactional(readOnly = true)` 按需使用）
- 禁止标注在 Controller 上

---

## 2.3 校验注解

校验注解规范已合入 [实体对象规范](model-specification.md#11-参数校验规范)，此处不再重复。

---

# 3. Jackson 注解

与前端交互的序列化相关注解：

| 注解 | 用途 |
|------|------|
| `@JsonProperty("field_name")` | 指定 JSON 字段名（Java camelCase → JSON snake_case） |
| `@JsonIgnore` | 序列化/反序列化时忽略字段 |
| `@JsonInclude(Include.NON_NULL)` | null 字段不序列化 |

### 使用原则

- 项目默认驼峰命名，通常无需 `@JsonProperty`
- 密码、密钥等敏感字段必须使用 `@JsonIgnore` 防止泄露
- Response 中可为 null 的字段建议类级别加 `@JsonInclude(Include.NON_NULL)`

---

# 4. 注解顺序

类级别注解按以下顺序排列（自上而下）：

1. **Spring 声明型**：`@RestController` / `@Service` / `@Component` / `@Configuration`
2. **Lombok**：`@Slf4j`、`@RequiredArgsConstructor`、`@Data` 等
3. **其他**：`@Transactional`、`@RequestMapping` 等

```java
@RestController
@RequestMapping("/api/admin/instances")
@RequiredArgsConstructor
@Slf4j
public class InstanceController {
```

> 当前项目 `@Component` 在上、`@Slf4j` 在下的模式予以保留。

---

# 5. 迁移计划

| 优先级 | 事项 | 说明 |
|--------|------|------|
| P1 | `InstanceServiceImpl` 改用 `@Slf4j` | 唯一差异点，立即修复 |
| P1 | 清理 5 个文件的 `LoggerFactory` 残留导入 | 编译无害但误导，配合 P3 第一阶段 |
| P2 | `@Autowired` 字段注入 → 构造器注入 | 涉及 9 个文件，配合 P3 第一阶段整体重构 |
| P2 | `@Value` 字段注入 → 构造器参数 | 配合构造器注入一起改 |

> 以上迁移在 P3 第一阶段「Backend 后端重构」中统一执行。详见 [planning-v1.md](../../requirements/planning-v1.md)。
