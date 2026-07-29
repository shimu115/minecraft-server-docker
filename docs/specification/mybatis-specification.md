# MyBatis 代码规范与多数据库兼容方案

## 1. 概述

### 1.1 背景

随着项目交付场景的多样化，单一的数据库（如 MySQL）已无法满足所有部署需求。为了实现降低边缘计算/私有化部署成本或提升离线开发测试的便利性，系统需要同时兼容 **MySQL**、**SQLite** 和 **H2** 三种数据库。

### 1.2 设计原则

- **对修改关闭，对扩展开放（OCP）**：后续新增数据库类型时，严禁修改现有核心业务逻辑。
- **业务零感知**：Service 层（业务层）代码应 100% 聚焦于业务，不包含任何特定数据库的判断、方言或逻辑。
- **物理隔离**：因数据库特性导致的 SQL 差异或 Java 代码调优，必须实现物理层面的代码和配置隔离。
- **精简配置**：`application.yml` 仅保留当前生效数据库的连接参数，所有 JDBC URL、驱动类、连接池参数等"重量配置"均由各 `DataSourceProvider` 实现类内部管理，`application.yml` 不暴露任何数据库特定的连接细节。

---

## 2. 总体架构设计

系统采用 **Service（业务层） ➔ Dialect（数据适配层） ➔ Mapper（MyBatis 持久层）** 的三层解耦架构。

> **注意**：项目结构规范中 `repository` 包为 JPA 专用。在 MyBatis 多数据库架构下，中间适配层统一命名为 `dialect` 包，避免与 JPA 的 `repository` 产生歧义。

```
+-------------------------------------------------------------+
|                     Service 层 (通用业务逻辑)                 |
+-------------------------------------------------------------+
                               |
                               v
+-------------------------------------------------------------+
|                  Dialect 层 (数据访问适配接口)                 |
+-------------------------------------------------------------+
                               |
            +------------------+------------------+
            | (根据 database.type 动态注入)         |
            v                                     v
+-----------------------+             +-----------------------+
|   MysqlUserDialect    |             |  SqliteUserDialect    |  (等)
+-----------------------+             +-----------------------+
            |                                     |
            v                                     v
+-----------------------+             +-----------------------+
|     UserMapper        |             |     UserMapper        |
| (XML: mapper/mysql/)  |             | (XML: mapper/sqlite/) |
+-----------------------+             +-----------------------+
```

### 2.1 分层职责规范

| 层级 | 包路径 | 职责 | 禁止 |
|------|--------|------|------|
| **Service** | `service` | 核心业务流程、参数校验、缓存控制、事务边界 | 引入任何数据库特定关键字或条件判断 |
| **Dialect** | `dialect` | 数据库差异的 Java 逻辑适配（并发控制、批量策略等） | 包含业务逻辑 |
| **Mapper** | `mapper` | 纯 SQL 执行器，通过专属目录实现 SQL 物理隔离 | 包含业务逻辑或 Java 适配代码 |

### 2.2 为什么选择物理隔离而非 databaseId

MyBatis 原生支持 `databaseId` 属性，可在同一个 XML 文件中编写多套 SQL：

```xml
<!-- databaseId 方案：所有 DB 的 SQL 混在同一个文件中 -->
<select id="findById" databaseId="mysql">...</select>
<select id="findById" databaseId="sqlite">...</select>
<select id="findById" databaseId="h2">...</select>
```

| 方案 | 优点 | 缺点 |
|------|------|------|
| **databaseId** | SQL 集中在同一文件，方便对比 | 文件随 DB 数量线性膨胀；不同 DB 的 SQL 差异大时维护困难；新增 DB 需修改已有文件（违反 OCP） |
| **物理隔离（本项目采用）** | 文件彻底分离，新增 DB 只需加目录不改已有代码；各 DB 的 XML 可独立演进 | 相同逻辑的 SQL 需在各 DB 目录下各写一份（少量重复） |

**选择物理隔离的核心原因**：对修改关闭。新增 PostgreSQL 时，只需新建 `mapper/postgresql/` 目录并编写 SQL，已有 MySQL/SQLite/H2 的代码和 XML **一行不改**。

### 2.3 XML 加载冲突约束

系统加载 `mapper/common/`（通用 SQL）+ `mapper/${database.type}/`（专属 SQL）。**同一个 Mapper 接口的同一个方法 ID 不得同时出现在 common 和 DB 专属目录中**，否则 MyBatis 启动时抛出 `Mapped Statements collection already contains value` 异常。

- **放入 `common/`**：所有数据库通用的标准 SQL（如简单的主键查询）
- **放入 `mapper/${database.type}/`**：需要适配的 SQL（如分页、批量插入、方言函数等）

---

## 3. 核心配置与实现方案

### 3.1 application.yml 极简配置

以下配置是用户唯一需要关心的。所有 JDBC URL、驱动类、连接池参数均由各 `DataSourceProvider` 实现类内部管理，`application.yml` 不暴露任何数据库特定的连接细节。

```yaml
database:
  type: mysql           # 可选值: mysql, sqlite, h2 —— 切换数据库只需改这一个值
  host: 127.0.0.1       # 关系型数据库必填（SQLite/H2 忽略）
  port: 3306            # 关系型数据库必填（SQLite/H2 忽略）
  database-name: my_db  # MySQL 库名 / SQLite 文件路径 / H2 数据库名
  username: root
  password: root
```

**设计意图**：类似 Nacos 的配置风格——`application.yml` 只管"连哪里"，各 Provider 实现类管"怎么连"。

### 3.2 DataSourceProvider 策略工厂

定义插件化接口，由 Spring 动态扫描并装配对应的数据源：

```java
// 数据源提供者接口
public interface DataSourceProvider {

    /** 判断是否支持指定的数据库类型 */
    boolean supports(String type);

    /** 根据配置属性构建数据源（URL、驱动、连接池参数全部在实现类内部硬编码） */
    DataSource buildDataSource(DatabaseProperties props);
}
```

**MySQL 提供者实现示例**：

```java
@Component
public class MySqlDataSourceProvider implements DataSourceProvider {

    @Override
    public boolean supports(String type) {
        return "mysql".equalsIgnoreCase(type);
    }

    @Override
    public DataSource buildDataSource(DatabaseProperties props) {
        HikariDataSource ds = new HikariDataSource();
        ds.setDriverClassName("com.mysql.cj.jdbc.Driver");
        ds.setJdbcUrl(String.format(
                "jdbc:mysql://%s:%d/%s?useUnicode=true&characterEncoding=utf-8&serverTimezone=UTC&useSSL=false&allowPublicKeyRetrieval=true",
                props.getHost(), props.getPort(), props.getDatabaseName()
        ));
        ds.setUsername(props.getUsername());
        ds.setPassword(props.getPassword());
        // 连接池参数在代码内硬编码，不暴露到 yml
        ds.setMaximumPoolSize(20);
        ds.setMinimumIdle(5);
        ds.setConnectionTimeout(30000);
        return ds;
    }
}
```

**SQLite 提供者实现示例**：

```java
@Component
public class SqliteDataSourceProvider implements DataSourceProvider {

    @Override
    public boolean supports(String type) {
        return "sqlite".equalsIgnoreCase(type);
    }

    @Override
    public DataSource buildDataSource(DatabaseProperties props) {
        // SQLite 使用文件路径，database-name 在此被解释为文件路径
        String url = "jdbc:sqlite:" + props.getDatabaseName();
        // SQLite 文件级锁特性下，连接池意义不大
        HikariDataSource ds = new HikariDataSource();
        ds.setDriverClassName("org.sqlite.JDBC");
        ds.setJdbcUrl(url);
        ds.setMaximumPoolSize(1); // SQLite 写串行化，连接池 >1 无意义
        // 启用 WAL 模式 + 写锁超时
        ds.setConnectionInitSql(
                "PRAGMA journal_mode=WAL; PRAGMA busy_timeout=5000; PRAGMA synchronous=NORMAL;");
        return ds;
    }
}
```

**H2 提供者实现示例**：

```java
@Component
public class H2DataSourceProvider implements DataSourceProvider {

    @Override
    public boolean supports(String type) {
        return "h2".equalsIgnoreCase(type);
    }

    @Override
    public DataSource buildDataSource(DatabaseProperties props) {
        HikariDataSource ds = new HikariDataSource();
        ds.setDriverClassName("org.h2.Driver");
        ds.setJdbcUrl(String.format(
                "jdbc:h2:file:%s;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
                props.getDatabaseName()
        ));
        ds.setUsername(props.getUsername());
        ds.setPassword(props.getPassword());
        ds.setMaximumPoolSize(10);
        return ds;
    }
}
```

**动态装配核心**：

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
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unsupported database type: " + targetType));
    }
}
```

### 3.3 SqlSessionFactory 动态加载 XML

严禁在 `application.yml` 中配置原生的 `mybatis.mapper-locations`。通过重写 `SqlSessionFactory` 动态合并加载路径：

```java
@Configuration
public class MyBatisConfig {

    @Bean
    public SqlSessionFactory sqlSessionFactory(
            DataSource dataSource,
            @Value("${database.type}") String dbType) throws Exception {

        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(dataSource);

        // 动态合并: 通用 XML + 当前数据库专属 XML
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        List<Resource> resources = new ArrayList<>();

        // 通用 SQL（所有数据库都相同的标准 SQL）
        resources.addAll(Arrays.asList(
                resolver.getResources("classpath:mapper/common/**/*.xml")));
        // 当前数据库的专属 SQL
        resources.addAll(Arrays.asList(
                resolver.getResources("classpath:mapper/" + dbType + "/**/*.xml")));

        factory.setMapperLocations(resources.toArray(new Resource[0]));

        // 下划线转驼峰（所有数据库统一）
        org.apache.ibatis.session.Configuration config = new org.apache.ibatis.session.Configuration();
        config.setMapUnderscoreToCamelCase(true);
        factory.setConfiguration(config);

        return factory.getObject();
    }

    @Bean
    public SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory sqlSessionFactory) {
        return new SqlSessionTemplate(sqlSessionFactory);
    }
}
```

**XML 目录结构**：

```text
resources/mapper/
├── common/                    # 所有数据库通用的 SQL
│   └── UserMapper.xml         # 简单的主键查询等标准 SQL
├── mysql/                     # MySQL 专属 SQL
│   ├── UserMapper.xml         # MySQL 方言版本
│   └── ServerMapper.xml
├── sqlite/                    # SQLite 专属 SQL
│   ├── UserMapper.xml         # SQLite 方言版本
│   └── ServerMapper.xml
└── h2/                        # H2 专属 SQL
    ├── UserMapper.xml         # H2 方言版本
    └── ServerMapper.xml
```

---

## 4. 嵌入式数据库自动初始化

由于 SQLite 和 H2 经常用于轻量化部署或内存测试，本地默认没有表结构。系统通过 `DatabaseInitializer` 监听组件，在项目启动时依据 `database.type` 自动执行初始化脚本。

### 4.1 脚本路径与触发机制

| 数据库 | 初始化脚本路径 | 触发条件 |
|--------|---------------|----------|
| SQLite | `resources/schema/schema-sqlite.sql` | `database.type = sqlite` |
| H2 | `resources/schema/schema-h2.sql` | `database.type = h2` |

```java
@Component
public class DatabaseInitializer implements ApplicationListener<ApplicationReadyEvent> {

    @Value("${database.type}")
    private String dbType;

    @Autowired
    private DataSource dataSource;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        // 仅嵌入式数据库自动建表，MySQL 由 Flyway 管理
        if ("mysql".equalsIgnoreCase(dbType)) {
            return;
        }
        String scriptPath = "schema/schema-" + dbType.toLowerCase() + ".sql";
        Resource resource = new ClassPathResource(scriptPath);
        if (resource.exists()) {
            ResourceDatabasePopulator populator = new ResourceDatabasePopulator(resource);
            populator.setContinueOnError(false);
            DatabasePopulatorUtils.execute(populator, dataSource);
        }
    }
}
```

### 4.2 MySQL 生产环境的 Schema 管理

MySQL 生产环境的 DDL 不在启动时自动执行，统一交由 **Flyway** 管理：

```text
resources/db/migration/mysql/
├── V1.0.0__init_schema.sql
├── V1.1.0__add_instance_table.sql
└── ...
```

SQLite/H2 的初始化脚本应与 Flyway 的 V1 脚本保持字段级一致，仅语法适配各自方言（详见第 7 节 DDL 编写规范）。

### 4.3 本地文件数据库启动完整性检查

SQLite 和 H2 是文件型数据库，数据库文件可能因以下原因损坏或结构异常：

- 宿主机突然断电 / 强制杀进程 → 写入未完成
- 磁盘空间满 → 写入截断
- 文件系统错误 / 磁盘坏道
- 人工误操作直接修改或替换了数据库文件
- 程序 bug 导致写入畸形数据

如果在启动时不检测，这些问题会在业务运行中随机爆发，排查困难。系统必须在启动阶段对本地文件数据库执行完整性校验。

#### 4.3.1 检测维度

| 检测项 | 说明 | 失败影响 |
|--------|------|----------|
| **文件存在性** | 数据库文件是否存在 | 不存在 → 走全新初始化流程（4.1 节） |
| **文件可读性** | 文件是否为合法数据库格式（非空、非损坏文件头） | 无法打开 → 告警并终止启动 |
| **数据完整性** | 数据库内部结构是否一致（B-Tree、索引、页链） | 不一致 → 尝试从备份恢复 |
| **结构匹配性** | 表名、列名、列类型是否与代码预期一致 | 不匹配 → 触发迁移（第 5 节）或告警 |

#### 4.3.2 完整性检查实现

```java
@Component
@Order(0) // 最先执行——在 DatabaseInitializer 和 MigrationRunner 之前
public class DatabaseIntegrityChecker implements ApplicationListener<ApplicationReadyEvent> {

    @Value("${database.type}")
    private String dbType;

    @Autowired
    private DataSource dataSource;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        // MySQL 是远程服务，不由本机做文件级检查
        if ("mysql".equalsIgnoreCase(dbType)) {
            return;
        }

        // 阶段 1: 文件存在性检查
        String dbPath = resolveDatabaseFilePath();
        if (dbPath == null || !Files.exists(Path.of(dbPath))) {
            log.info("Database file not found — will be created by DatabaseInitializer");
            return; // 交给 DatabaseInitializer 处理
        }

        // 阶段 2: 可读性检查
        if (!isReadableDatabase(dbPath)) {
            log.error("Database file exists but is not a valid database: {}", dbPath);
            handleUnrecoverable(dbPath, "Database file is corrupted or not a valid database file");
            return;
        }

        // 阶段 3: 数据完整性检查
        String integrityResult = checkIntegrity();
        if (!"ok".equalsIgnoreCase(integrityResult)) {
            log.error("Database integrity check FAILED: {}", integrityResult);
            attemptRecovery(dbPath);
            return;
        }

        // 阶段 4: 结构匹配性检查
        List<String> mismatches = checkSchemaCompatibility();
        if (!mismatches.isEmpty()) {
            log.warn("Database schema mismatch detected: {}", mismatches);
            // 不阻止启动，交给 MigrationRunner（第 5 节）处理
        }

        log.info("Database integrity check PASSED");
    }

    /**
     * SQLite: 执行 PRAGMA integrity_check
     * H2: 执行 CHECK TABLE（需遍历所有表）
     */
    private String checkIntegrity() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        if ("sqlite".equalsIgnoreCase(dbType)) {
            return jdbc.queryForObject("PRAGMA integrity_check", String.class);
        } else if ("h2".equalsIgnoreCase(dbType)) {
            // H2 需逐表检查，汇总结果
            List<String> tables = jdbc.queryForList(
                "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'PUBLIC'",
                String.class);
            List<String> errors = new ArrayList<>();
            for (String table : tables) {
                try {
                    jdbc.execute("CHECK TABLE \"" + table + "\"");
                } catch (Exception e) {
                    errors.add(table + ": " + e.getMessage());
                }
            }
            return errors.isEmpty() ? "ok" : String.join("; ", errors);
        }
        return "ok";
    }

    /**
     * 检查核心表是否存在且结构符合预期
     */
    private List<String> checkSchemaCompatibility() {
        // 从配置或代码中读取预期的表结构定义
        // 与实际数据库中的 INFORMATION_SCHEMA 进行比对
        // 返回不匹配的差异列表
        // 注意：只检测核心表是否存在，不要求列完全匹配（升级场景交给迁移）
        List<String> issues = new ArrayList<>();
        List<String> expectedTables = getExpectedCoreTables();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        for (String table : expectedTables) {
            try {
                jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
            } catch (Exception e) {
                issues.add("Table " + table + " not accessible: " + e.getMessage());
            }
        }
        return issues;
    }

    /**
     * 尝试从备份恢复
     */
    private void attemptRecovery(String dbPath) {
        Path backupDir = Path.of(dbPath + ".backups/");
        if (!Files.exists(backupDir)) {
            handleUnrecoverable(dbPath, "No backup directory found, cannot recover");
            return;
        }

        // 查找最近的备份文件
        try {
            Optional<Path> latestBackup = Files.list(backupDir)
                .filter(f -> f.toString().endsWith(".db"))
                .sorted(Comparator.reverseOrder())
                .findFirst();

            if (latestBackup.isPresent()) {
                log.warn("Attempting recovery from backup: {}", latestBackup.get());
                Files.copy(latestBackup.get(), Path.of(dbPath),
                    StandardCopyOption.REPLACE_EXISTING);
                // 恢复后再次校验
                String retryResult = checkIntegrity();
                if ("ok".equalsIgnoreCase(retryResult)) {
                    log.info("Recovery SUCCESS — database restored from backup");
                    return;
                }
                log.error("Recovery FAILED — restored file also fails integrity check");
            }
        } catch (IOException e) {
            log.error("Recovery attempt failed: {}", e.getMessage());
        }

        handleUnrecoverable(dbPath, "All recovery attempts failed");
    }

    /**
     * 不可恢复错误处理
     */
    private void handleUnrecoverable(String dbPath, String reason) {
        log.error("=" .repeat(60));
        log.error("DATABASE UNRECOVERABLE: {}", reason);
        log.error("File: {}", dbPath);
        log.error("Manual action required:");
        log.error("  1. Check disk space and file permissions");
        log.error("  2. Delete the corrupted file if a valid backup exists");
        log.error("  3. Restore from external backup if available");
        log.error("  4. Restart the application");
        log.error("=" .repeat(60));

        // 终止启动——继续运行只会产生更多难以排查的错误
        // 遵循项目异常规范：业务异常统一使用 McPanelException
        throw new McPanelException(ErrorCode.DB_INTEGRITY_FAILED, reason);
    }

    private boolean isReadableDatabase(String dbPath) {
        try {
            // 尝试读取数据库文件头
            byte[] header = new byte[16];
            try (FileInputStream fis = new FileInputStream(dbPath)) {
                if (fis.read(header) < 16) return false;
            }
            // SQLite 文件头: "SQLite format 3\0"
            // H2 文件头: "-- H2 " 或二进制标识
            String headerStr = new String(header, 0, 15, StandardCharsets.US_ASCII);
            return headerStr.startsWith("SQLite format 3") || headerStr.startsWith("-- H2");
        } catch (IOException e) {
            return false;
        }
    }

    private String resolveDatabaseFilePath() {
        // 从 DataSource 的 JDBC URL 中解析出文件路径
        // jdbc:sqlite:/path/to/db → /path/to/db
        // jdbc:h2:file:/path/to/db → /path/to/db.mv.db
    }

    private List<String> getExpectedCoreTables() {
        // 返回所有 PO 实体对应的表名列表
        // 可通过扫描 @TableName 注解或维护静态配置获得
    }
}
```

#### 4.3.3 启动时的检测流程总览

将 4.1、4.3 和第 5 节串联起来，本地文件数据库的完整启动流程如下：

```
应用启动 → DataSource 就绪
  │
  ├── [4.3] DatabaseIntegrityChecker（@Order(0)）
  │    ├── 文件不存在 → 跳至 DatabaseInitializer
  │    ├── 文件损坏 → 尝试从备份恢复 → 不可恢复则终止启动
  │    └── 文件正常 → 校验通过，继续
  │
  ├── [4.1] DatabaseInitializer（@Order(1)）
  │    └── 全新安装 → 执行完整 schema 脚本
  │
  └── [5.x] MigrationRunner（@Order(2)）
       └── 结构不匹配 → 按版本号执行迁移脚本
```

#### 4.3.4 运行时的定期健康检查（可选增强）

对于长期运行的边缘节点，建议通过定时任务定期检查数据库文件健康状态：

```java
@Component
public class DatabaseHealthMonitor {

    @Value("${database.type}")
    private String dbType;

    @Autowired
    private DataSource dataSource;

    @Scheduled(cron = "0 0 3 * * ?") // 每天凌晨 3 点
    public void scheduledIntegrityCheck() {
        if (!"sqlite".equalsIgnoreCase(dbType) && !"h2".equalsIgnoreCase(dbType)) {
            return;
        }
        String result = checkIntegrity();
        if (!"ok".equalsIgnoreCase(result)) {
            log.error("Scheduled integrity check FAILED: {}", result);
            // 发送告警通知
        }
    }
}
```

#### 4.3.5 数据库相关错误码

以下错误码使用 `error-code-specification.md` 中预留的 **80000 ~ 89999（备份与恢复）** 号段，需在 `ErrorCode.java` 中新增：

| 枚举值 | code | msg | 触发场景 |
|--------|------|-----|----------|
| `DB_INTEGRITY_FAILED` | `80000` | `数据库文件完整性校验失败` | 启动时 `PRAGMA integrity_check` 未通过且无法自动恢复 |
| `DB_MIGRATION_FAILED` | `80001` | `数据库迁移失败` | MigrationRunner 执行迁移脚本出错，已回滚至备份 |
| `DB_BACKUP_FAILED` | `80002` | `数据库备份失败` | 迁移前备份创建失败 |
| `DB_RECOVERY_FAILED` | `80003` | `数据库备份恢复失败` | 用备份文件恢复后再次校验仍未通过 |
| `DB_STRUCTURE_MISMATCH` | `80004` | `数据库表结构与预期不匹配` | 核心表缺失或无法访问 |

使用方式遵循 `exception-specification.md` 的约定：

```java
// 启动阶段数据库不可恢复（硬失败，终止启动）
throw new McPanelException(ErrorCode.DB_INTEGRITY_FAILED, reason);

// 迁移失败但已回滚（软失败，记录后继续运行）
log.error("Migration failed, rolled back | {}", e.getMessage());
// 应用继续以旧版本数据库状态运行
```

> 注意：数据库相关异常统一使用 `McPanelException`，遵循项目异常规范（`exception-specification.md` 第 1 节——"业务异常统一使用 McPanelException，不直接使用 RuntimeException"）。

---

## 5. 数据库版本升级与数据迁移方案

### 5.1 核心问题

项目迭代过程中，数据库表结构不可避免地会发生变化（新增字段、删除列、修改类型、拆分表等）。如果启动时直接使用新版本代码访问旧版本数据库，会出现：

- **启动报错**：Unknown column、Table doesn't exist 等
- **数据丢失**：手动删表重建或覆盖安装
- **人工操作不可追溯**：谁在什么时间改了什么？无法审计

因此需要一套**自动化、可追溯、可回滚**的数据库版本迁移机制。

### 5.2 设计理念

核心流程遵循"**备份 → 改表 → 按映射恢复数据 → 更新版本号**"四步法：

1. **先备份，再动刀**：备份不成功绝不执行任何 DDL
2. **显式列映射**：数据从旧表恢复到新表时，必须显式写出列对应关系，不依赖 `SELECT *` 或列顺序
3. **版本驱动**：用一个表记录数据库当前对应的项目版本，脚本根据版本差决定执行哪些迁移
4. **失败可回滚**：任何一步出错，都能靠备份文件恢复至迁移前的完整状态

### 5.3 版本追踪表设计

使用 `db_version` 表记录当前数据库对应的项目版本号。启动时对比当前代码要求的版本与数据库实际版本，决定是否执行迁移。

```sql
-- 所有数据库统一的版本追踪表
CREATE TABLE IF NOT EXISTS db_version (
    id            INTEGER      NOT NULL PRIMARY KEY DEFAULT 1,
    version       VARCHAR(32)  NOT NULL,           -- 当前版本号，如 "2.1.0"
    target_version VARCHAR(32),                    -- 正在迁移到的目标版本（执行中时非空）
    applied_at    DATETIME     NOT NULL,           -- 最后迁移时间
    backup_path   VARCHAR(512),                    -- 最近备份文件路径
    checksum      VARCHAR(64),                     -- 最后一个迁移脚本的校验和
    status        TINYINT      NOT NULL DEFAULT 1  -- 0=迁移中 1=正常 2=失败
);
```

**核心约定**：

| 场景 | db_version 状态 | 系统行为 |
|------|----------------|----------|
| 表不存在 | — | 全新安装，执行完整初始化脚本（第 4 节），写入当前版本号 |
| `version = 目标版本` | status = 1 | 无需操作，正常启动 |
| `version < 目标版本` | status = 1 或 2 | 触发迁移流程 |
| `status = 0`（迁移中） | — | 上次迁移未正常完成 → 触发恢复流程 |

### 5.4 迁移脚本规范

迁移脚本存放路径，MySQL 和嵌入式数据库分别维护，语法适配各自方言，但**语义必须一致**：

```text
resources/db/migration/
├── mysql/                              # MySQL（Flyway 管理）
│   ├── V1.0.0__init_schema.sql
│   ├── V1.1.0__add_motd_column.sql
│   └── V1.2.0__split_config_table.sql
└── embedded/                           # SQLite / H2（自建 MigrationRunner 管理）
    ├── V1.0.0__init_schema.sql
    ├── V1.1.0__add_motd_column.sql
    └── V1.2.0__split_config_table.sql
```

脚本命名规范：`V{目标版本}__{描述}.sql`，双下划线分隔版本号和描述。

**脚本标准结构**：

```sql
-- ============================================
-- V1.1.0: 为 player 表新增 motd 字段
-- 前置版本: <= 1.0.x
-- 依赖: 无
-- ============================================

-- [阶段 1] DDL 变更
ALTER TABLE player ADD COLUMN motd VARCHAR(255);

-- [阶段 2] 数据迁移（如有）
UPDATE player SET motd = 'Welcome to Minecraft!' WHERE motd IS NULL;
```

> 备份和版本更新由 `MigrationRunner` 自动处理，迁移脚本本身只需关注 DDL 和数据转换。

### 5.5 迁移流程（四阶段）

```
应用启动
  │
  ├── [前置检查] 读取 db_version 表
  │    ├── 表不存在 → 全新安装，执行完整初始化（第 4 节），写入 version = 目标版本
  │    ├── version = 目标版本 → 正常启动
  │    └── version < 目标版本 → 进入迁移流程 ↓
  │
  ├── 阶段 1【备份】
  │    ├── MySQL: mysqldump 或逐表导出 INSERT 语句
  │    ├── SQLite: 直接复制 .db 文件到 backup/ 目录
  │    └── H2: 执行 SCRIPT TO 'backup.sql' 导出
  │    备份失败 → 终止启动，记录 ERROR 日志
  │
  ├── 阶段 2【改表】按版本号升序依次执行迁移脚本
  │    ├── 设置 db_version.status = 0, target_version = 当前脚本版本
  │    ├── 执行 V{x}__{desc}.sql
  │    │    ├── 成功 → 继续下一个
  │    │    └── 失败 → 跳至阶段 3（回滚）
  │    └── 全部成功 → status = 1, version = 目标版本, target_version = NULL
  │
  ├── 阶段 3【失败恢复】（任一脚本失败时触发）
  │    ├── 根据备份文件恢复数据库至迁移前状态
  │    ├── 设置 db_version.status = 2（标记失败）
  │    └── 记录 ERROR 日志（含失败脚本路径 + 错误信息），应用继续启动
  │
  └── 阶段 4【清理】
       └── 根据保留策略清理过期备份文件
```

**关键约束**：

- 备份没有 100% 成功之前，**绝不执行任何 DDL**。
- 迁移脚本必须**幂等**——重复执行不造成数据错误（使用 `IF NOT EXISTS`、判断列是否存在等）。
- MySQL 环境每个迁移脚本在单独**事务**中执行，失败自动回滚该脚本的 DDL。SQLite/H2 靠备份恢复。
- 所有迁移操作写入日志文件 `logs/db-migration.log`，便于审计追溯。

### 5.6 MigrationRunner 实现骨架

```java
@Component
@Order(1) // 在 DatabaseInitializer 之后执行
public class MigrationRunner implements ApplicationListener<ApplicationReadyEvent> {

    private static final String TARGET_VERSION = "2.1.0"; // 项目当前所需数据库版本

    @Value("${database.type}")
    private String dbType;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private DatabaseBackupService backupService;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        String currentVersion = getCurrentVersion();

        // 全新安装，由 DatabaseInitializer 处理
        if (currentVersion == null) {
            log.info("Fresh installation — schema initialized, version set to {}", TARGET_VERSION);
            insertVersionRecord(TARGET_VERSION);
            return;
        }

        if (TARGET_VERSION.equals(currentVersion)) {
            log.info("Database schema is up to date (v{})", currentVersion);
            return;
        }

        // 检查是否有其他实例正在迁移
        if (!tryAcquireMigrationLock()) {
            log.info("Another instance is running migration, proceeding with existing schema");
            return;
        }

        log.warn("Schema upgrade needed: v{} → v{}. Starting migration...",
                currentVersion, TARGET_VERSION);

        // 阶段 1: 备份
        String backupPath = backupService.createBackup(dbType, dataSource);
        log.info("Backup created: {}", backupPath);

        // 阶段 2: 执行迁移
        try {
            List<MigrationScript> scripts = loadPendingScripts(currentVersion);
            for (MigrationScript script : scripts) {
                log.info("Applying: {}", script.getFileName());
                setMigrationStatus(0, script.getVersion()); // status=0 执行中
                executeScript(script);
            }
            updateVersion(TARGET_VERSION, backupPath, 1); // status=1 成功
            log.info("Migration to v{} completed successfully", TARGET_VERSION);
        } catch (Exception e) {
            // 阶段 3: 失败恢复
            log.error("Migration failed at script, restoring from backup...", e);
            backupService.restoreFromBackup(dbType, dataSource, backupPath);
            updateVersion(currentVersion, null, 2); // status=2 失败，回退版本号
        } finally {
            // 阶段 4: 清理过期备份
            backupService.cleanupOldBackups();
        }
    }

    private List<MigrationScript> loadPendingScripts(String fromVersion) {
        String scriptDir = "mysql".equalsIgnoreCase(dbType)
                ? "db/migration/mysql/" : "db/migration/embedded/";
        // 扫描 classpath 下的迁移脚本
        // 按版本号排序，筛选 version > fromVersion 且 <= TARGET_VERSION
    }

    private String getCurrentVersion() {
        // SELECT version FROM db_version WHERE id = 1
    }

    private void insertVersionRecord(String version) { /* INSERT INTO db_version ... */ }
    private void updateVersion(String version, String backupPath, int status) { /* UPDATE */ }
    private void setMigrationStatus(int status, String targetVersion) { /* UPDATE status, target_version */ }
    private void executeScript(MigrationScript script) { /* 执行 SQL 脚本 */ }
}
```

### 5.7 SQLite 专用迁移策略

SQLite 的 `ALTER TABLE` 仅支持 `RENAME TABLE` 和 `ADD COLUMN`（新增列必须放在末尾，不能指定位置，不能附加除 `DEFAULT` 外的约束）。任何涉及**删除列、修改列类型、添加约束、调整列顺序**的操作，都必须走"重建表"模式：

```sql
-- ============================================
-- V1.2.0: player 表结构重构
--   删除: old_column_to_remove
--   新增: motd, last_login
--   修改: score 类型不变但重设默认值
-- ============================================

-- 1. 创建新表（包含目标结构）
CREATE TABLE player_new (
    id          INTEGER NOT NULL PRIMARY KEY,
    name        VARCHAR(64) NOT NULL,
    score       INT NOT NULL DEFAULT 0,
    motd        VARCHAR(255),
    last_login  TEXT,
    created_at  TEXT NOT NULL
);

-- 2. 从旧表迁移数据（显式列映射，不依赖 SELECT * 或列顺序）
INSERT INTO player_new (id, name, score, motd, last_login, created_at)
SELECT id, name, score, NULL, NULL, created_at
FROM player;

-- 3. 删除旧表
DROP TABLE player;

-- 4. 重命名
ALTER TABLE player_new RENAME TO player;

-- 5. 重建索引
CREATE INDEX IF NOT EXISTS idx_player_name ON player (name);
CREATE INDEX IF NOT EXISTS idx_player_last_login ON player (last_login);
```

**SQLite 备份实现**：单文件数据库，备份即文件复制：

```java
public class SqliteBackupService implements DatabaseBackupService {

    @Override
    public String createBackup(String dbType, DataSource dataSource) {
        // 1. 执行 WAL checkpoint 确保数据写入主文件
        jdbcTemplate.execute("PRAGMA wal_checkpoint(TRUNCATE)");

        // 2. 从 JDBC URL 解析出文件路径（jdbc:sqlite:/path/to/db）
        String dbPath = extractDbPath(dataSource);
        Path source = Path.of(dbPath);
        String backupDir = dbPath + ".backups/";
        Files.createDirectories(Path.of(backupDir));

        String timestamp = LocalDateTime.now(ZoneOffset.UTC)
                .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        Path backup = Path.of(backupDir, "backup_" + timestamp + ".db");
        Files.copy(source, backup, StandardCopyOption.REPLACE_EXISTING);

        return backup.toString();
    }

    @Override
    public void restoreFromBackup(String dbType, DataSource dataSource, String backupPath) {
        // 关闭连接池 → 用备份文件覆盖 → 重建连接池
        String dbPath = extractDbPath(dataSource);
        Files.copy(Path.of(backupPath), Path.of(dbPath),
                StandardCopyOption.REPLACE_EXISTING);
    }
}
```

SQLite 重建表时应特别注意：**外键约束**和**触发器**也需要在新表中重建，且必须在迁移脚本中显式处理。

### 5.8 备份保留策略

```text
{数据库文件路径}.backups/
├── backup_20260729_103000_v1.0.0.db    # 大版本锚点（永久保留）
├── backup_20260730_142500_v1.1.0.db
├── backup_20260731_090000_v2.0.0.db    # 大版本锚点（永久保留）
├── backup_20260731_153000_v2.1.0.db    # 最近 10 次保留
└── ...
```

| 策略项 | 规则 |
|--------|------|
| **保留数量** | 最近 **10 次**迁移的备份 |
| **大版本锚点** | v1.0.0、v2.0.0 等 X.0.0 版本备份**永久保留** |
| **清理时机** | 每次迁移成功后，删除超出数量的旧备份 |
| **手动触发** | 支持通过管理 API 或 CLI 工具手动触发全量备份 |
| **备份校验** | 备份完成后计算文件 SHA-256 写入 `db_version.checksum`，恢复前校验 |

### 5.9 多实例启动保护

当多个应用实例同时启动时（如 K8s 多副本、Docker Compose `scale`），必须确保**只有一个实例执行迁移**。

**方案**：利用 `db_version.status` 作为分布式锁标记：

```java
private boolean tryAcquireMigrationLock() {
    // 原子操作：只有 status 为 1（正常）或 2（失败）时才允许获取锁
    int rows = jdbcTemplate.update(
        "UPDATE db_version SET status = 0, target_version = ? " +
        "WHERE id = 1 AND status IN (1, 2)",
        TARGET_VERSION);

    if (rows > 0) return true; // 获取锁成功

    // 未获取到锁 → 检查原因
    Integer status = jdbcTemplate.queryForObject(
        "SELECT status FROM db_version WHERE id = 1", Integer.class);
    if (status != null && status == 0) {
        log.info("Another instance is running migration (status=0), " +
                 "proceeding with existing schema");
        // 不等待，直接以当前数据库状态启动
        // 等迁移实例完成后，外部负载均衡器将流量切换到新版本实例
    }
    return false;
}
```

### 5.10 与 Flyway 的职责划分

| 功能 | MySQL（Flyway） | SQLite / H2（自建 MigrationRunner） |
|------|----------------|--------------------------------------|
| 全新安装 | Flyway 执行 V1~Vn | DatabaseInitializer 执行完整 schema |
| 版本升级 | Flyway 自动检测并执行 | MigrationRunner 检测并执行 |
| 版本追踪 | `flyway_schema_history` 表 | 自定义 `db_version` 表 |
| 备份 | 外部脚本（mysqldump） | 文件复制 / SQL SCRIPT 导出 |
| 失败回滚 | Flyway 不支持自动回滚，需外部脚本处理 | 备份文件覆盖恢复 |
| 多实例保护 | Flyway 内置 `flyway_schema_history` 行锁 | db_version.status 乐观锁 |
| 脚本目录 | `db/migration/mysql/` | `db/migration/embedded/` |

> 两套目录下的脚本**语义一致**（同样的表变更、同样的数据迁移逻辑），仅 DDL 语法适配各自方言。

### 5.11 开发工作流

当开发者修改了数据库结构，必须同步完成以下步骤，**不允许"手动改一下数据库就行"**：

```text
1. 修改 model.po 实体类（新增/删除/修改字段）
       │
2. 更新 resources/schema/schema-{sqlite,h2}.sql（完整 DDL，供全新安装用）
       │
3. 编写增量迁移脚本:
   ├── db/migration/mysql/V{x}__{desc}.sql
   └── db/migration/embedded/V{x}__{desc}.sql
       │
4. 更新 MigrationRunner.TARGET_VERSION 常量
       │
5. 本地启动验证: 迁移脚本是否正确执行
       │
6. 同一 commit 提交: 实体变更 + 完整 DDL + 迁移脚本 + 版本号
```

> **铁律**：修改了任何 PO 实体字段，必须同时提供对应的迁移脚本。所有数据库变更必须**脚本化、可复现、可审计**。

---

## 6. SQL 编写强约束规范

重构过程中，所有研发人员必须严格遵守以下通用 SQL 规范，避免引发跨库不兼容错误：

### 6.1 主键生成

**禁用**各数据库的自增机制（如 MySQL 的 `AUTO_INCREMENT`、SQLite 的 `AUTOINCREMENT`）。统一在 Java 业务层通过**雪花算法 (Snowflake ID)** 生成全局唯一长整型 ID。

建表时主键统一声明为 `BIGINT NOT NULL`，不附加任何自增语义：

```sql
-- 所有数据库统一写法
id BIGINT NOT NULL PRIMARY KEY
```

> 特别注意：SQLite 中 `INTEGER PRIMARY KEY`（未显式声明 NOT NULL）会自动成为 rowid 别名并自增，**严禁使用此特性**。必须写 `INTEGER NOT NULL PRIMARY KEY`。

### 6.2 空值代换

**禁用** MySQL 的 `IFNULL`，统一改用标准 SQL 规范的 **`COALESCE(val, 0)`**（三库通用）。

```sql
-- 错误
SELECT IFNULL(score, 0) FROM player;
-- 正确
SELECT COALESCE(score, 0) FROM player;
```

### 6.3 模糊查询

统一使用标准拼接格式，禁止在 XML 中硬编码 `%`：

```sql
-- 正确
WHERE name LIKE CONCAT('%', #{keyword}, '%')
```

### 6.4 时间处理

**禁用** `NOW()`、`SYSDATE`、`CURRENT_TIMESTAMP` 等数据库内置时间函数。所有创建/更新时间统一在 Java 层生成并作为参数传入。

**时间生成规范**：统一使用 UTC 时区，避免服务器时区差异导致数据不一致：

```java
// 正确：统一 UTC
LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

// 错误：不带时区，不同地区服务器产生不同结果
LocalDateTime now = LocalDateTime.now();
```

不推荐使用 `new Date()` 或 `System.currentTimeMillis()` 传递时间——类型不安全且时区语义模糊。

### 6.5 布尔类型映射

数据库层面统一使用 `TINYINT`（存储 `0`/`1`），Java 实体类字段声明为 `Boolean`。通过自定义 **TypeHandler** 统一转换，避免在每个 SQL 中手动写 `0`/`1`：

```java
// MyBatis TypeHandler: 自动完成 Boolean ↔ Integer 映射
@MappedTypes(Boolean.class)
@MappedJdbcTypes(JdbcType.TINYINT)
public class BooleanIntegerTypeHandler extends BaseTypeHandler<Boolean> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Boolean param, JdbcType jdbcType)
            throws SQLException {
        ps.setInt(i, param ? 1 : 0);
    }

    @Override
    public Boolean getNullableResult(ResultSet rs, String columnName) throws SQLException {
        int value = rs.getInt(columnName);
        return !rs.wasNull() && value == 1;
    }

    @Override
    public Boolean getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        int value = rs.getInt(columnIndex);
        return !rs.wasNull() && value == 1;
    }

    @Override
    public Boolean getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        int value = cs.getInt(columnIndex);
        return !rs.wasNull() && value == 1;
    }
}
```

在 MyBatis 配置中全局注册：

```java
config.getTypeHandlerRegistry().register(Boolean.class, new BooleanIntegerTypeHandler());
```

或在 XML 中按字段使用：

```xml
<result column="is_active" property="isActive"
        typeHandler="com.mcpanel.panel.common.typehandler.BooleanIntegerTypeHandler"/>
```

---

## 7. DDL 编写规范

由于不同数据库的 DDL 语法差异比 DML 更大，建表脚本必须遵守以下约定。

### 7.1 字段类型统一映射

| 业务含义 | Java 类型 | MySQL | SQLite | H2 | 说明 |
|----------|-----------|-------|--------|-----|------|
| 主键 ID | `Long` | `BIGINT` | `INTEGER` | `BIGINT` | SQLite 的 INTEGER 即 64 位 |
| 布尔标记 | `Boolean` | `TINYINT(1)` | `INTEGER` | `TINYINT` | 配合 TypeHandler 使用 |
| 日期时间 | `LocalDateTime` | `DATETIME` | `TEXT` | `TIMESTAMP` | 统一存 UTC 时间 |
| 长文本 | `String` | `TEXT` | `TEXT` | `TEXT` | 三库统一用 TEXT |
| 整型 | `Integer` | `INT` | `INTEGER` | `INT` | |
| 长整型 | `Long` | `BIGINT` | `INTEGER` | `BIGINT` | |
| 浮点 | `Double` | `DOUBLE` | `REAL` | `DOUBLE` | |

### 7.2 约束与默认值

```sql
-- 所有数据库统一约束写法（避免使用数据库特有语法）

-- NOT NULL 约束 + 默认值（三库通用）
score INT NOT NULL DEFAULT 0

-- 唯一约束（三库通用）
CONSTRAINT uk_username UNIQUE (username)

-- 时间字段：不在 SQL 中设 DEFAULT，由 Java 层统一传入
created_at DATETIME NOT NULL
```

**特别注意**：

- SQLite 的 `ALTER TABLE` 能力极弱，不支持 `ADD CONSTRAINT`、`DROP COLUMN` 等操作。涉及 SQLite 的迁移建议"重建表"而非"修改表"（详见第 5.7 节）。
- MySQL 的 `TEXT` 类型不能设默认值（严格模式下报错），确需默认值时用 `VARCHAR` 代替。
- SQLite 建表时使用 `INTEGER PRIMARY KEY` 会自动成为 rowid 的别名（自增），**严禁使用此特性**——统一用 `INTEGER NOT NULL PRIMARY KEY`。

### 7.3 索引操作

```sql
-- 建表时创建索引（三库通用）
CREATE TABLE player (
    id BIGINT NOT NULL PRIMARY KEY,
    name VARCHAR(64) NOT NULL,
    INDEX idx_name (name)
);

-- 建表后追加索引（MySQL / H2 支持，SQLite 需重建表）
CREATE INDEX idx_name ON player (name);
```

**建议**：DDL 变更优先走迁移脚本（第 5 节），并为 SQLite/H2 单独维护等价的初始化脚本（完整 DDL，不做增量）。

---

## 8. 各数据库底层差异与填坑指南

在编写 Dialect 层（适配层）时，必须针对不同数据库的底层特性编写特定的防御性 Java 代码。

### 8.1 SQLite 文件级锁导致并发写阻塞

- **问题**：MySQL InnoDB 支持行级锁 + MVCC，读写互不阻塞。SQLite 在写操作时会对整个数据库文件加排他锁，多线程并发写入时直接抛出 `SQLITE_BUSY`（database is locked）。
- **影响范围**：不仅并发写互相阻塞，**读操作也会被写操作阻塞**（除非启用 WAL 模式）。

**对策一：启用 WAL 模式（推荐，优先采用）**

在 SQLite 连接初始化时设置（已在 3.2 节 `SqliteDataSourceProvider` 中配置）：

```sql
PRAGMA journal_mode=WAL;
PRAGMA busy_timeout=5000;
PRAGMA synchronous=NORMAL;
```

WAL（Write-Ahead Logging）模式下，读操作不再被写操作阻塞，写操作之间仍串行但性能显著提升。`busy_timeout` 设置写锁等待超时（毫秒），避免立即抛异常。

**对策二：Dialect 层写操作排队（兜底方案）**

对于部署环境无法启用 WAL 的场景，在对应的 Dialect 实现中使用单线程写队列：

```java
@Service
public class SqliteUserDialect implements UserDialect {

    // SQLite 写操作专用单线程执行器
    private final ExecutorService writeExecutor = Executors.newSingleThreadExecutor();

    @Autowired
    private UserMapper userMapper;

    @Override
    public void batchInsertUsers(List<UserPO> users) {
        CompletableFuture.runAsync(() -> {
            doBatchInsert(users);
        }, writeExecutor).join();
    }

    private void doBatchInsert(List<UserPO> users) {
        for (int i = 0; i < users.size(); i += 200) {
            List<UserPO> batch = users.subList(i, Math.min(i + 200, users.size()));
            userMapper.batchInsert(batch);
        }
    }
}
```

> **不推荐**直接使用 `synchronized` 或 `ReentrantLock` 处理此问题——会阻塞调用线程并占用连接池资源。使用队列化执行器是更优的选择。

### 8.2 SQLite 参数变量绑定限制

- **问题**：SQLite 默认最大绑定变量数为 999（`SQLITE_MAX_VARIABLE_NUMBER`）。批量插入 500 条记录，每条 3 个字段 = 1500 个参数 → 直接报错。
- **触发场景**：MyBatis 的 `<foreach>` 批量插入、大量 `IN` 条件。

**对策：分批提交**

```java
// Dialect 层统一控制分批大小，不在 XML 中硬编码
private static final int BATCH_SIZE = 200; // 保守值，确保 BATCH_SIZE × 字段数 < 999

public void saveAll(List<UserPO> users) {
    for (int i = 0; i < users.size(); i += BATCH_SIZE) {
        List<UserPO> batch = users.subList(i, Math.min(i + BATCH_SIZE, users.size()));
        userMapper.batchInsert(batch);
    }
}
```

> 此限制在 MySQL 和 H2 中不适用，但为保持代码一致性，建议所有 Dialect 实现统一遵循此分批策略。

### 8.3 分页方言差异

- **MySQL**：支持 `LIMIT offset, count` 和 `LIMIT count OFFSET offset` 两种语法。
- **SQLite**：仅支持 `LIMIT count OFFSET offset`（必须用 OFFSET 关键字）。
- **H2**：两种语法都支持。

**统一方案**：所有数据库统一使用 `LIMIT #{limit} OFFSET #{offset}` 语法，三库完美兼容：

```xml
<!-- mapper/${database.type}/UserMapper.xml -->
<select id="findByPage" resultType="UserPO">
    SELECT id, name FROM player
    ORDER BY id
    LIMIT #{limit} OFFSET #{offset}
</select>
```

> **不使用 MyBatis PageHelper**：PageHelper 需要为每种数据库配置不同的 `helperDialect`，且依赖 ThreadLocal 实现，在多数据库动态切换场景下可能产生混乱。本项目通过 Dialect 层 + 统一 SQL 语法自行处理分页。

### 8.4 布尔类型映射差异

- **问题**：MySQL 将 `Boolean` 映射为 `TINYINT(1)`（存储 `0`/`1`）；H2 支持真正的 `BOOLEAN` 类型；SQLite 没有布尔类型，通常用 `INTEGER` 代替。
- **对策**：参见第 6.5 节，使用 `BooleanIntegerTypeHandler` 统一转换，SQL 中始终用 `0`/`1` 表示布尔值。禁止在 SQL 中硬编码 `true`/`false` 关键字。

---

## 9. 事务管理

### 9.1 隔离级别差异

| 特性 | MySQL (InnoDB) | SQLite | H2 |
|------|---------------|--------|-----|
| 默认隔离级别 | REPEATABLE READ | SERIALIZABLE | READ COMMITTED |
| 行级锁 | 支持 | 不支持（文件级锁） | 支持 |
| MVCC | 支持 | WAL 模式下近似支持 | 支持 |
| 并发读写 | 完全支持 | 仅 WAL 模式支持 | 支持 |

### 9.2 统一事务策略

Service 层声明式事务，不感知底层数据库：

```java
@Service
public class PlayerService {

    @Transactional(rollbackFor = Exception.class)
    public void createPlayer(CreatePlayerRequest request) {
        playerDialect.insert(playerPO);
        logDialect.recordCreation(playerPO.getId());
    }
}
```

### 9.3 SQLite 事务特别注意事项

- SQLite 默认使用 **DEFERRED** 事务模式（写操作才开始获取锁），这会导致"读-判断-写"流程中出现 TOCTOU 竞态。需要原子性操作时，显式使用 `BEGIN IMMEDIATE`。
- SQLite 在 `SERIALIZABLE` 隔离级别下，所有写操作串行化，性能极低。生产部署**必须启用 WAL 模式**（参见 8.1 节）。

---

## 10. 测试策略

多数据库兼容最大的痛点不在编码阶段，而在验证阶段。必须建立覆盖全部目标数据库的测试体系。

### 10.1 测试分层

| 测试层级 | 数据库 | 目的 | 工具 |
|----------|--------|------|------|
| **单元测试** | H2 内存模式 | 快速验证 Mapper SQL 正确性 | `@MyBatisTest` + H2 |
| **集成测试** | MySQL + SQLite + H2 | 验证 Dialect 层适配逻辑 | `@SpringBootTest` + TestContainers (MySQL) / 嵌入式 (SQLite/H2) |
| **Dialect 专项** | 全量 | 逐一验证并发控制、批量策略、类型转换 | JUnit 5 `@ParameterizedTest` |
| **迁移测试** | 全量 | 验证迁移脚本从旧版本升级的完整流程 | 预置旧版本数据库文件 + MigrationRunner |

### 10.2 CI 矩阵构建

```yaml
# .github/workflows/test.yml（示意）
jobs:
  test:
    strategy:
      matrix:
        database: [h2, sqlite, mysql]
    steps:
      - name: Run tests with ${{ matrix.database }}
        run: ./gradlew test -Ddatabase.type=${{ matrix.database }}

  migration-test:
    strategy:
      matrix:
        from-version: [1.0.0, 1.1.0, 2.0.0]
        database: [sqlite, h2]
    steps:
      - name: Test migration from v${{ matrix.from-version }}
        run: ./gradlew migrationTest -DfromVersion=${{ matrix.from-version }} -Ddatabase.type=${{ matrix.database }}
```

### 10.3 测试基类设计

```java
// 所有数据库共用同一套测试用例
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public abstract class BaseDialectTest {

    @Autowired
    protected UserDialect userDialect;

    @Test
    void shouldInsertAndQueryUser() {
        UserPO user = createTestUser();
        userDialect.insert(user);
        UserPO found = userDialect.findById(user.getId());
        assertThat(found).isNotNull();
        assertThat(found.getName()).isEqualTo(user.getName());
    }

    @Test
    void shouldHandleBatchInsert() {
        List<UserPO> users = IntStream.range(0, 500)
                .mapToObj(i -> createTestUser((long) i))
                .collect(Collectors.toList());
        userDialect.saveAll(users);
        assertThat(userDialect.countAll()).isEqualTo(500);
    }
}
```

各数据库只需提供 `@Profile` 注解的子类，继承上述测试即可。

---

## 11. 新增数据库检查清单

当需要新增支持一种数据库（如 PostgreSQL）时，按以下清单逐项完成：

- [ ] 1. 实现 `DataSourceProvider`（驱动类、JDBC URL、连接池参数全部在代码内）
- [ ] 2. 在 `resources/mapper/{新数据库}/` 下编写全部 SQL XML
- [ ] 3. 编写 Dialect 实现类，处理该数据库特有的 Java 层逻辑
- [ ] 4. 编写 `schema-{新数据库}.sql` 初始化脚本
- [ ] 5. 在 `db/migration/` 下编写该数据库的迁移脚本
- [ ] 6. 实现该数据库的 `DatabaseBackupService`（备份与恢复逻辑）
- [ ] 7. 确保所有 SQL 不使用方言函数（复查第 6 节约束清单）
- [ ] 8. 确保 DDL 字段类型符合第 7 节映射表
- [ ] 9. 在集成测试矩阵中加入新数据库
- [ ] 10. 在迁移测试中加入该数据库
- [ ] 11. 更新 `application.yml` 中 `database.type` 的枚举注释
- [ ] 12. 更新本文档第 2 节架构图中加入新数据库

> **核心纪律**：以上步骤中，步骤 1-7 **不得修改任何已有代码**，只能新增。如需修改已有类，说明架构设计有缺陷，应回溯到步骤 3 寻找不污染现有代码的扩展方式。

---

## 附录 A: 依赖清单

```xml
<!-- pom.xml 中需要的核心依赖 -->

<!-- MySQL -->
<dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
    <scope>runtime</scope>
</dependency>

<!-- SQLite -->
<dependency>
    <groupId>org.xerial</groupId>
    <artifactId>sqlite-jdbc</artifactId>
    <scope>runtime</scope>
</dependency>

<!-- H2（测试 + 轻量部署） -->
<dependency>
    <groupId>com.h2database</groupId>
    <artifactId>h2</artifactId>
    <scope>runtime</scope>
</dependency>

<!-- MyBatis Spring Boot Starter -->
<dependency>
    <groupId>org.mybatis.spring.boot</groupId>
    <artifactId>mybatis-spring-boot-starter</artifactId>
</dependency>

<!-- Flyway（MySQL 生产迁移） -->
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
</dependency>
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-mysql</artifactId>
</dependency>
```

---

## 附录 B: 与现有项目结构规范的协调

本文档引入的 `dialect` 包是对 `project-structure-specification.md` 的补充，而非违反。在现有目录结构中新增：

```text
com.mcpanel.panel
├── dialect                  # 数据库适配层（新增）
│   ├── UserDialect.java     # 通用接口
│   ├── MysqlUserDialect.java
│   ├── SqliteUserDialect.java
│   └── H2UserDialect.java
├── mapper                   # MyBatis Mapper 接口（已有）
├── config
│   ├── datasource           # DataSourceProvider 实现（新增）
│   │   ├── DataSourceProvider.java
│   │   ├── MySqlDataSourceProvider.java
│   │   ├── SqliteDataSourceProvider.java
│   │   ├── H2DataSourceProvider.java
│   │   └── DynamicDataSourceConfig.java
│   └── migration            # 迁移相关配置（新增）
│       ├── MigrationRunner.java
│       ├── DatabaseBackupService.java
│       └── impl/
│           ├── MySqlBackupService.java
│           ├── SqliteBackupService.java
│           └── H2BackupService.java
└── ...
```

> 说明：`repository` 包按项目结构规范保留为 JPA 专用，本文档不使用该包名。
