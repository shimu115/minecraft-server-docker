# Java Backend Development Specification

本目录包含项目开发过程中所使用的所有开发规范。

所有开发人员及 AI（Claude Code、ChatGPT、Copilot、DeepSeek 等）在新增功能、修改代码或重构项目时，应优先遵循本目录下的所有规范。

---

# 阅读顺序

建议首次阅读按照以下顺序：

1. 项目结构规范
2. 实体对象规范
3. 分层开发规范
4. REST API 规范
5. ApiResponse 统一响应规范
6. 异常处理规范
7. 错误码规范

其他规范可根据实际开发需求进行查阅。

---

# 规范目录

| 文档 | 说明 |
|------|------|
| [project-structure-specification.md](project-structure-specification.md) | 项目目录结构规范 |
| [model-specification.md](model-specification.md) | Request、Response、VO、PO、DTO 等实体规范 |
| [rest-api-specification.md](rest-api-specification.md) | REST API 设计规范 |
| [api-response-specification.md](api-response-specification.md) | ApiResponse 统一返回规范 |
| naming-specification.md | 命名规范（待完善） |
| [exception-specification.md](exception-specification.md) | 异常处理规范 |
| [error-code-specification.md](error-code-specification.md) | 错误码规范 |
| validation-specification.md | 参数校验规范（待完善） |
| [mybatis-specification.md](mybatis-specification.md) | MyBatis 代码规范与多数据库兼容方案 |
| logging-specification.md | 日志规范（待完善） |

---

# 文档职责

各文档职责如下：

| 文档                                                     | 职责 |
|--------------------------------------------------------|------|
| [项目结构规范](project-structure-specification.md)           | 定义包结构及目录组织方式 |
| [实体对象规范](model-specification.md)                       | 定义 Request、Response、VO、PO、DTO 的职责 |
| [REST API 规范](rest-api-specification.md)               | 定义 URI、HTTP Method、接口文档等设计规范 |
| [ApiResponse 规范](api-response-specification.md)        | 定义统一响应格式 |
| [异常处理规范](exception-specification.md)                     | 定义异常抛出、捕获及全局处理规范 |
| [错误码规范](error-code-specification.md)                    | 定义业务状态码号段分配及使用规范 |
| [api.md](../../panel\backend\src\main\resources\docs/api.md) | 项目接口索引及导航 |

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
- 保持与现有项目一致的代码风格。