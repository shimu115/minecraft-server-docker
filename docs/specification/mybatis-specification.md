# 多数据库兼容重构设计文档

## 1. 概述

### 1.1 背景

随着项目交付场景的多样化，单一的数据库（如 MySQL）已无法满足所有部署需求。为了实现降低边缘计算/私有化部署成本或提升离线开发测试的便利性，系统需要同时兼容 **MySQL**、**SQLite** 和 **H2** 三种数据库。

### 1.2 设计原则

- **对修改关闭，对扩展开放（OCP）**：后续新增数据库类型时，严禁修改现有核心业务逻辑。
- **业务零感知**：Service 层（业务层）代码应 100% 聚焦于业务，不包含任何特定数据库的判断、方言或逻辑。
- **物理隔离**：因数据库特性导致的 SQL 差异或 Java 代码调优，必须实现物理层面的代码和配置隔离。
- **精简配置**：`application.yml` 仅保留当前生效数据库的信息，拒绝冗余。

------

## 2. 总体架构设计

系统采用 **Service（业务层） ➔ Repository（数据适配层/优化包） ➔ Mapper（MyBatis 持久层）** 的三层解耦架构。

```
+-------------------------------------------------------------+

|                     Service 层 (通用业务逻辑)                 |
+-------------------------------------------------------------+
                               |
                               v
+-------------------------------------------------------------+

|                Repository 层 (数据访问适配接口)                |
+-------------------------------------------------------------+
                               |
            +------------------+------------------+

            | (根据 database.type 动态注入)         |
            v                                     v
+-----------------------+             +-----------------------+

|  MysqlRepositoryImpl  |             |  SqliteRepositoryImpl | (等)
+-----------------------+             +-----------------------+

            |                                     |
            v                                     v
+-----------------------+             +-----------------------+

|     MysqlUserMapper   |             |    SqliteUserMapper   |
+-----------------------+             +-----------------------+
```

### 2.1 分层职责规范

1. **Service 层**：负责核心业务流程、参数校验、缓存控制及事务边界。**严禁**引入任何特定数据库的关键字或 Java 条件判断。
2. **Repository 层（方言优化包）**：核心隔离层。用于解决因数据库不同而导致的 **Java 逻辑差异**（如 SQLite 的并发文件锁处理、不同数据库的批量提交批次限制等）。
3. **Mapper 层**：纯粹的 SQL 执行器。通过特定目录或 `databaseId` 实现差异化 SQL 的硬隔离。

------

## 3. 核心配置与实现方案

### 3.1 极简 `application.yml` 配置规范

配置文件只体现当前环境所使用的数据库参数。

```yaml
database:
  type: mysql           # 可选值: mysql, sqlite, h2
  host: 127.0.0.1       # 关系型数据库必填
  port: 3306            # 关系型数据库必填
  database-name: my_db  # 数据库名 或 SQLite 的本地文件路径 (如 ./data/mydb.db)
  username: root
  password: root
```

请谨慎使用此类代码。



### 3.2 策略工厂（Provider）与动态数据源装配

系统通过定义 `DataSourceProvider` 插件化接口，由 Spring 动态扫描并装配对应的数据源。

- **数据源提供者接口**：

```java
public interface DataSourceProvider {
    boolean supports(String type);
    DataSource buildDataSource(DatabaseProperties props);
}
```

请谨慎使用此类代码。



- **动态装配核心**：

```java
@Configuration
public class DynamicDataSourceConfig {
    @Autowired
    private DatabaseProperties properties;
    @Autowired
    private List<DataSourceProvider> providers;

    @Bean
    public DataSource dataSource() {
        String targetType = properties.getType();
        return providers.stream()
                .filter(provider -> provider.supports(targetType))
                .findFirst()
                .map(provider -> provider.buildDataSource(properties))
                .orElseThrow(() -> new IllegalArgumentException("Unsupported database type: " + targetType));
    }
}
```

请谨慎使用此类代码。



### 3.3 MyBatis 多路径 XML 动态加载

为了彻底分离不同数据库的 SQL 差异，严禁在 `application.yml` 中配置原生的 `mybatis.mapper-locations`。系统通过重写 `SqlSessionFactory` 动态拼接并合并加载路径：

- 通用 XML 路径：`classpath:mapper/common/**/*.xml`
- 专属 XML 路径：`classpath:mapper/${database.type}/**/*.xml`

------

## 4. 嵌入式空库自动初始化方案（针对 SQLite / H2）

由于 SQLite 和 H2 经常用于轻量化部署或内存测试，本地默认没有表结构。系统通过 `DatabaseInitializer` 监听组件，在项目启动时依据 `database.type` 自动寻找并执行初始化脚本。

- **脚本存放路径**：
  - `resources/schema/schema-sqlite.sql`
  - `resources/schema/schema-h2.sql`
- **触发机制**：仅当 `database.type` 为 `sqlite` 或 `h2` 时，启动 `ResourceDatabasePopulator` 自动建表。MySQL 生产环境脚本交由 CI/CD 或 Flyway 管理，程序启动时不自动干预。

------

## 5. SQL 编写强约束规范

重构过程中，所有研发人员必须严格遵守以下通用 SQL 规范，避免引发跨库不兼容错误：

1. **主键生成**：**禁用**各数据库的自增机制（如 MySQL 的 `AUTO_INCREMENT`）。统一在 Java 业务层通过**雪花算法 (Snowflake ID)** 生成全局唯一长整型 ID。
2. **空值代换**：**禁用** MySQL 的 `IFNULL`，统一改用标准 SQL 规范的 **`COALESCE(val, 0)`**（三库完美通用）。
3. **模糊查询**：统一使用标准拼接格式：`LIKE CONCAT('%', #{keyword}, '%')`。
4. **时间处理**：**禁用** `NOW()`、`SYSDATE` 等数据库内置时间函数。所有创建/更新时间统一在 Java 层通过 `LocalDateTime.now()` 生成并作为参数传入。

------

## 6. 各数据库底层致命差异与填坑指南（重点注意）

在重构 Repository（适配层）时，必须针对不同数据库的底层特性编写特定的防御性 Java 代码：

### 6.1 SQLite 文件级锁导致并发写挂起

- **问题**：MySQL 支持行级锁，而 SQLite 在写操作时会对整个文件上锁。多线程并发写入时会直接抛出 `database is locked` 异常。
- **重构对策**：在 `SqliteUserRepository` 等涉及写操作的嵌入式适配层中，必须引入 Java 层的并发控制锁（如 `ReentrantLock`、`synchronized` 块或信号量），在 Java 进程内将写操作排队化。

### 6.2 SQLite 参数变量绑定限制

- **问题**：SQLite 在进行批量插入（Batch Insert）时，绑定的最大变量参数默认不能超过 999 个。如果批量插入 500 条数据，每条数据有 3 个字段（共 1500 个参数），直接报错。
- **重构对策**：在 Repository 层的批量插入实现中，必须显式编写**分批提交逻辑**（如固定每 200 条数据执行一次 `SqlSession` 的 `flushStatements()`）。

### 6.3 布尔类型映射差异

- **问题**：MySQL 会将 `Boolean` 映射为 `TINYINT(1)`（存储为 `0`/`1`）；H2 支持真正的 `BOOLEAN` 类型；SQLite 通常用 `INTEGER` 代替。
- **重构对策**：MyBatis 返回的 Java 实体类字段统一声明为 `Boolean`（或封装类 `Integer` 并通过枚举转换），禁止直接在 SQL 中使用 `true` 或 `false` 关键字硬编码，应使用 `1` 和 `0` 进行逻辑对齐。