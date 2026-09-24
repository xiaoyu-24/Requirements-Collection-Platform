# XQSJ · 需求收集与管理平台

**简体中文** | [English](README.en.md)

面向团队的需求协作平台，将需求提交、评估处理、系统与版本管理、附件和进度通知集中在一个工作台中。支持私有化部署，也可按需接入兼容 OpenAI 接口的 AI 服务，从文本或截图中提取需求信息。

## 功能

- **需求管理**：暂存草稿、正式提交、组合筛选、紧急程度、处理进度和需求详情。
- **处理工作台与概览**：查看负责或协助处理的需求，按维度统计并下钻到需求列表。
- **系统与版本**：维护系统负责人、协助人和版本，批量绑定、迁移或解除版本需求，记录版本变更历史。
- **附件管理**：选择、拖拽或粘贴上传；支持图片、PDF、Word、Excel，图片和 PDF 直接预览，Office 文件通过 LibreOffice 转换预览。
- **AI 辅助录入**：从文本或需求截图中提取字段；只补充空白字段，结果由用户确认后提交；支持维护多个 AI 配置并激活其中一个。
- **角色与账号**：普通用户、需求处理员、管理员；支持账号启停、密码重置和首次登录改密。
- **站内通知与字典**：需求动态、待处理提醒，以及部门和需求类型维护。

需求状态包括待评估、已确认、开发中、暂停、已完成、已拒绝和已关闭。

| 角色 | 主要能力 |
| --- | --- |
| 普通用户 `USER` | 提交与查看自己的需求，在允许的状态下修改或删除，查看系统和版本信息 |
| 需求处理员 `HANDLER` | 查看和处理团队需求，使用工作台与概览，维护系统和版本 |
| 管理员 `ADMIN` | 需求处理员能力，以及人员、字典和 AI 配置管理 |

## 技术栈

| 层次 | 技术 |
| --- | --- |
| 前端 | Vue 3.5、TypeScript 5.7、Vite 6、Ant Design Vue 4、Vue Router |
| 后端 | Java 17、Spring Boot 3.3、Spring Data JPA |
| 数据库 | MySQL；现有部署文档以 MySQL 5.7.32 为兼容目标 |
| 数据库迁移 | Flyway |
| 附件预览 | 浏览器原生图片/PDF 预览、LibreOffice |
| 可选 AI 服务 | OpenAI 兼容 Chat Completions 接口；截图识别需支持图像输入的模型 |

界面目前为中文；英文 README 不代表已提供英文界面。

## 本地运行

### 1. 准备环境

- JDK 17、Maven 3.6.3 或更高版本。
- Node.js 22 LTS、npm。
- 可连接的 MySQL 数据库。升级到其他 MySQL 版本时，请先在独立空库验证迁移。
- LibreOffice 为可选依赖，仅 Word/Excel 在线预览需要。

克隆仓库后，在项目根目录执行后续命令。

### 2. 创建数据库

使用有建库权限的 MySQL 账号执行，替换示例密码：

```sql
CREATE DATABASE requirements_platform
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'requirements_app'@'localhost' IDENTIFIED BY 'CHANGE_ME';
GRANT ALL PRIVILEGES ON requirements_platform.* TO 'requirements_app'@'localhost';
```

上述权限供本地开发和自动迁移使用；生产环境应区分迁移权限和日常运行权限。远程数据库需要按实际连接来源调整 MySQL 账号的 Host。

### 3. 启动后端

PowerShell：

```powershell
$env:DB_URL='jdbc:mysql://localhost:3306/requirements_platform?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai'
$env:DB_USERNAME='requirements_app'
$env:DB_PASSWORD='CHANGE_ME'
cd backend
mvn spring-boot:run
```

Bash：

```bash
export DB_URL='jdbc:mysql://localhost:3306/requirements_platform?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai'
export DB_USERNAME='requirements_app'
export DB_PASSWORD='CHANGE_ME'
cd backend
mvn spring-boot:run
```

后端默认监听 `8080`，启动时自动运行 Flyway 迁移。访问 [健康检查](http://localhost:8080/api/health)，正常响应为 `{"status":"UP"}`。

**首次登录：**空数据库首次启动后，程序自动创建 `admin`，随机初始密码仅在本次启动日志中显示。也可在启动前设置环境变量 `ADMIN_INITIAL_PASSWORD` 指定初始密码。首次登录按提示修改密码；已有账号不会被启动配置重置。

公开版不预置组织内部账号。V22/V23 保留为无数据写入的占位迁移；本版本面向全新数据库，不可直接覆盖使用旧版迁移的现有数据库。详见[公开版本说明](docs/public-release.md)。人员管理中新建或重置账号仍使用初始密码 `888888`，用户需在首次登录时修改。

### 4. 启动前端

新开一个终端，从项目根目录执行：

```bash
cd frontend
npm ci
npm run dev
```

打开终端输出的地址，通常为 [http://localhost:5173](http://localhost:5173)。Vite 自动把 `/api` 代理到 `http://localhost:8080`，无需另外配置前端 API 地址。

登录后，管理员可依次维护部门与需求类型、创建账号，再由管理员或需求处理员创建系统和版本，开始提交和处理需求。

## 配置

后端通过环境变量读取配置；单独创建 `.env` 文件不会被 Spring Boot 自动加载。部署模板通过 systemd 的 `EnvironmentFile` 注入变量。

| 环境变量 | 默认值 | 用途 |
| --- | --- | --- |
| `DB_URL` | 本机 `requirements_platform` 数据库 | JDBC 连接地址，完整示例见上文 |
| `DB_USERNAME` | `requirements_app` | 数据库账号 |
| `DB_PASSWORD` | 空 | 数据库密码 |
| `ADMIN_INITIAL_PASSWORD` | 空（随机生成） | 仅在空用户表首次启动时设置管理员初始密码 |
| `ATTACHMENTS_ROOT` | `./uploads` | 原始附件目录 |
| `ATTACHMENTS_PREVIEW_ROOT` | `./previews` | 预览缓存目录 |
| `ATTACHMENTS_MINIMUM_FREE_SPACE_BYTES` | `0` | 上传时预留的磁盘空间 |
| `LIBREOFFICE_EXECUTABLE` | `libreoffice` | LibreOffice 可执行文件路径 |
| `ATTACHMENTS_PREVIEW_TIMEOUT_SECONDS` | `60` | 单次 Office 转换超时 |
| `ATTACHMENTS_PREVIEW_CONCURRENCY` | `2` | Office 转换并发数 |
| `AI_REQUEST_TIMEOUT_SECONDS` | `30` | AI 文本请求超时 |
| `AI_IMAGE_REQUEST_TIMEOUT_SECONDS` | `60` | AI 图片请求超时 |
| `NOTIFICATIONS_REMINDER_CRON` | `0 0 9 * * *` | 待处理提醒计划 |
| `NOTIFICATIONS_STALE_AFTER_DAYS` | `7` | 长期未更新提醒阈值（天） |

相对目录以启动后端时的工作目录为基准。生产部署建议使用绝对路径。

AI 服务地址、模型和 API Key 由管理员在「AI 配置」页面维护。AI 未启用时仍可手工录入需求；启用后，待分析文本或图片及相关字典、系统与版本信息会发送至配置的 AI 服务。当前 API Key 以明文存储在数据库中，接口仅返回掩码。

## 构建与验证

在项目根目录执行：

```bash
# 后端测试与打包
mvn -f backend/pom.xml test
mvn -f backend/pom.xml package

# 前端依赖、类型检查与构建
npm --prefix frontend ci
npm --prefix frontend run build
```

产物为 `backend/target/requirements-platform-0.1.0-SNAPSHOT.jar` 和 `frontend/dist/`。当前数据库相关自动化测试使用 H2 并关闭 Flyway，不能替代 MySQL 空库迁移验证。

## 部署

运行后端 JAR，使用 Nginx 托管前端构建产物，将同一站点的 `/api` 转发到后端，并配置 SPA 路由回退到 `index.html`。生产环境配置 HTTPS；Office 预览节点需安装 LibreOffice。数据库与原始附件应成套备份，预览缓存可重新生成。

- [部署与环境配置](docs/deployment.md)
- [Nginx、systemd、备份与健康检查模板](deploy/centos7/)
- [备份与恢复](docs/backup-and-recovery.md)
- [GitHub 发布步骤](docs/publishing.md)

`deploy/centos7/` 是已有环境的部署模板，迁移到其他发行版时需调整安装方式与路径。

## 项目结构

```text
backend/                  Spring Boot 后端
  src/main/java/          业务接口与服务
  src/main/resources/     应用配置与 Flyway 迁移
  src/test/               后端自动化测试
frontend/                 Vue 前端
  src/components/         页面与组件
  src/composables/        共享交互与状态逻辑
deploy/centos7/            部署模板和运维脚本
docs/                     部署、备份与公开发布说明
```

## 参与与许可

欢迎通过 Issue 提交问题和建议。提交改动前请运行相关后端测试与前端构建，并使用虚构数据编写示例。

当前仓库尚未提供 `LICENSE` 文件；公开源码不等于授予开源许可，复制、修改和分发的授权范围需由项目维护者明确。
