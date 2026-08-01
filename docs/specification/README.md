# Java Backend Development Specification

本目录包含项目开发过程中所使用的所有开发规范。

所有开发人员及 AI（Claude Code、ChatGPT、Copilot、DeepSeek 等）在新增功能、修改代码或重构项目时，应优先遵循本目录下的所有规范。

---

## 阅读顺序

建议首次阅读按照以下顺序：

1. 项目结构规范
2. 实体对象规范（含参数校验规范）
3. REST API 规范
4. ApiResponse 统一响应规范
5. 异常处理规范（含日志规范）
6. 错误码规范

其他规范可根据实际开发需求进行查阅。

> 💡 编码时参考 [注解使用规范](annotation-specification.md)，确保 Lombok、Spring DI 等注解用法统一。

---

# 规范目录

| 文档 | 说明 |
|------|------|
| [项目结构规范](project-structure-specification.md) | 定义包结构及目录组织方式 |
| [实体对象规范](model-specification.md) | 定义 Request、Response、VO、PO、DTO 的职责及参数校验规范 |
| [REST API 规范](rest-api-specification.md) | 定义 URI、HTTP Method、接口文档等设计规范 |
| [ApiResponse 规范](api-response-specification.md) | 定义统一响应格式 |
| [异常处理规范](exception-specification.md) | 定义异常抛出、捕获、全局处理及日志记录规范 |
| [错误码规范](error-code-specification.md) | 定义业务状态码号段分配及使用规范 |
| [MyBatis 规范](mybatis-specification.md) | MyBatis 代码规范与多数据库兼容方案 |
| [注解使用规范](annotation-specification.md) | Lombok、Spring DI、Jackson 等注解的统一使用规范 |
| [api.md](../../panel/backend/src/main/resources/docs/api.md) | 项目接口索引及导航 |

---

# 文档维护原则

所有规范均应长期维护，并随着项目迭代持续更新。

新增规范时，应同步更新本 README。

---

# AI 开发要求

AI 在生成代码时应遵循以下原则：

- 优先遵循本目录中的开发规范。
- 不得违反实体对象规范。
- 不得违反分层开发规范。
- 不得违反 REST API 规范。
- 不得违反注解使用规范（日志、依赖注入等）。
- 保持与现有项目一致的代码风格。