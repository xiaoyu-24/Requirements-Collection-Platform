# 公开版本说明 / Public distribution

本仓库是面向全新部署的独立源码版本，不含组织内部人员初始化数据、历史部署包或内部仓库的 Git 历史。

This is an independent source distribution for fresh deployments. It contains no organization-specific account imports, historical deployment packages, or Git history from the internal repository.

## 数据库与首次登录 / Database and first login

- V1–V21 保留表结构与必要的数据迁移。V22/V23 仅执行 `SELECT 1`，不导入普通用户或固定密码管理员。
- 空用户表首次启动时，`UserBootstrap` 创建 `admin`。可以设置 `ADMIN_INITIAL_PASSWORD`，或从本次启动日志获取随机初始密码；首次登录需要修改密码。
- 已有用户表不会再次初始化。启动配置不会重置已存在的账号。
- 管理员通过人员管理页面创建其他账号。新建/重置账号仍使用 `888888` 作为初始密码，首次登录需要改密。
- 部门和需求类型是可由管理员维护的通用初始字典。

V1–V21 retain the schema and required data migrations. V22/V23 are no-ops. On first startup with an empty users table, `UserBootstrap` creates `admin`, using `ADMIN_INITIAL_PASSWORD` if set or a random password printed once in the startup log. Existing accounts are never reset. Administrators create other accounts through the UI; newly created or reset accounts use `888888` until their first-login password change.

## 兼容边界 / Compatibility boundary

**本版本只用于全新数据库及其后续升级。** V22/V23 与内部版本的校验和不同；不能直接连接内部现有数据库，也不能通过关闭 Flyway 校验来绕过差异。内部数据库转用本版本需要单独制定迁移方案。

**Use a fresh database.** V22/V23 have different checksums from the internal distribution. Do not point this build at an existing internal database or disable Flyway validation to bypass the difference. Converting an internal deployment requires a separate migration plan.

## 发布与后续维护 / Publishing and maintenance

发布步骤见 [publishing.md](publishing.md)。后续同步功能时，应逐项移植代码并复核改动；不要合并内部仓库的旧历史，也不要复制旧 JAR、备份、真实人员导入或本地环境文件。

See [publishing.md](publishing.md) for GitHub publishing steps. Port future changes selectively and review them before publication. Do not merge internal Git history or copy old JARs, backups, account imports, or local environment files into this repository.

公开源码和公开在线服务是两件事。部署时应配置 HTTPS、数据库权限和备份；AI Key 当前以明文保存在数据库中，启用 AI 时输入内容及相关业务选项会发送到配置的服务。仓库尚未指定开源许可证。

Publishing source code is separate from exposing a running service. Configure HTTPS, database access, and backups for deployment. AI keys are currently stored as plaintext in the database; AI analysis sends input and relevant business options to the configured service. No open-source license has been selected yet.
