# 本地部署与迁移说明

> 本文覆盖公开版本的部署、配置、备份恢复和运维操作。项目介绍与本地启动见 [README](../README.md)。本版本仅面向全新数据库；不要直接连接内部版本的现有数据库。

## 前置条件

- Java 17。
- MySQL 5.7.32，创建数据库 `requirements_platform` 和最小权限账号。
- Node.js 22 LTS（仅构建前端时需要）。
- Maven 3.6.3+（仅构建后端时需要）。
- LibreOffice（用于 DOC、DOCX、XLS、XLSX 转 PDF 预览；只需服务器端无界面组件）。

## 后端配置

通过环境变量配置，不在仓库保存密码：

```powershell
$env:DB_URL='jdbc:mysql://127.0.0.1:3306/requirements_platform?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai'
$env:DB_USERNAME='requirements_app'
$env:DB_PASSWORD='请设置实际密码'
$env:ATTACHMENTS_ROOT='D:\requirements-platform\uploads'
$env:ATTACHMENTS_MINIMUM_FREE_SPACE_BYTES='1073741824' # 可选：至少保留 1 GiB 空间
$env:ATTACHMENTS_PREVIEW_ROOT='D:\requirements-platform\previews'
$env:LIBREOFFICE_EXECUTABLE='C:\Program Files\LibreOffice\program\soffice.exe'
$env:ATTACHMENTS_PREVIEW_TIMEOUT_SECONDS='60'
$env:ATTACHMENTS_PREVIEW_CONCURRENCY='2'
$env:AI_REQUEST_TIMEOUT_SECONDS='30' # 可选：AI 服务请求超时秒数
Set-Location backend
mvn clean package
java -jar target\requirements-platform-0.1.0-SNAPSHOT.jar
```

CentOS 7/宝塔部署模板见 [`deploy/centos7`](../deploy/centos7)。生产环境应复制 `.env.example` 后通过宝塔进程管理或 systemd 注入环境变量，不要把实际密码提交到仓库。

前后端分域名部署时采用同源代理方式：前端继续使用 `/api`，前端域名的 Nginx 将 `/api` 代理到本机 8080；后端 API 域名可作为独立运维访问入口。这样浏览器不发生跨域请求，不需要配置 CORS 或 `VITE_API_BASE_URL`。

Flyway 会自动执行所有待执行迁移，包括需求处理信息、附件预览字段和 AI 配置表。应用账号需要数据库、表和索引的创建/变更权限；日常运行时建议改用仅数据读写权限的账号。

首次启动时，空用户表会由程序初始化一个 `admin` 管理员。初始密码可通过 `ADMIN_INITIAL_PASSWORD` 指定；未指定时随机生成并仅在本次启动日志打印。首次登录需要改密。V22/V23 不导入任何账号，重启不会重置现有密码。

AI 配置在页面中维护 OpenAI 兼容服务地址、模型和 API Key。API Key 以明文保存在数据库中，因此必须严格限制数据库账号、数据库备份和服务器文件的访问权限。读取 AI 配置时接口只会显示 API Key 的前后各 4 位，完整密钥不会返回给浏览器。

新部署完成后，管理员可在 AI 配置页填写服务地址、模型与 Key，再执行连接测试和智能导入。

启动完成后访问 `http://127.0.0.1:8080/api/health`，应返回 `{"status":"UP"}`。附件目录应由运行账号拥有读写权限；上传时会保留临时文件所需空间，并按 `ATTACHMENTS_MINIMUM_FREE_SPACE_BYTES` 预留剩余容量。

## 附件预览运行环境

Linux服务器可通过系统软件源或经过审核的离线RPM安装LibreOffice。安装后先执行：

```bash
/usr/bin/libreoffice --headless --version
```

创建并授权原始附件和预览目录：

```bash
mkdir -p /www/wwwroot/requirements-platform/data/uploads
mkdir -p /www/wwwroot/requirements-platform/data/previews
chown -R requirements:requirements /www/wwwroot/requirements-platform/data
chmod -R u=rwX,g=rX,o= /www/wwwroot/requirements-platform/data
```

图片和PDF使用原文件预览；Word和Excel在后台线程中调用LibreOffice转换为PDF。默认最多同时转换2个文件，单文件超时60秒。服务器资源较小时可将并发数改为1。转换失败不会影响原文件下载，用户可在附件列表点击“重新生成”。

升级时Flyway会执行V8迁移，为附件增加预览状态、缓存路径、生成时间和失败原因字段。历史图片和PDF立即可预览；历史Word和Excel在打开详情或重新生成时按需转换，不执行全量集中转换。

## 前端构建

```powershell
Set-Location frontend
npm ci
npm run build
```

将 `frontend/dist` 交给内网 Web 服务器托管，并将 `/api` 反向代理到后端服务。

## 备份

每天对数据库和原始附件目录使用同一批次标识备份。预览目录属于可重建缓存，默认不纳入备份：

```powershell
$stamp=Get-Date -Format 'yyyyMMdd-HHmmss'
$backupRoot='D:\backup'
New-Item -ItemType Directory -Force "$backupRoot\$stamp" | Out-Null
mysqldump -u requirements_app -p --single-transaction --routines --events --no-tablespaces --result-file="$backupRoot\$stamp\requirements.sql" requirements_platform
Copy-Item -Recurse $env:ATTACHMENTS_ROOT "$backupRoot\$stamp\attachments"
@{ batch=$stamp; createdAt=(Get-Date).ToString('o'); database='requirements_platform'; attachments='attachments' } | ConvertTo-Json | Set-Content "$backupRoot\$stamp\manifest.json" -Encoding utf8
```

至少保留最近 7 个批次。

## 恢复与迁移

1. 停止后端，避免写入。
2. 恢复同一批次的 MySQL 导出与附件目录：`mysql -u requirements_app -p requirements_platform < D:\backup\批次号\requirements.sql`，再将 `attachments` 目录复制到配置的附件根目录。
3. 在新机器设置 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD`、`ATTACHMENTS_ROOT`。
4. 启动后端，确认 Flyway 校验成功及 `/api/health` 返回 UP。
5. 访问前端，抽查系统、需求、附件下载和图片预览。
6. 抽查PDF直接预览，并对一个Word和一个Excel附件执行重新生成，确认LibreOffice转换正常。

常见故障处理：

- 页面长期显示“正在生成预览”：检查后端日志、LibreOffice可执行路径和转换线程池队列。
- 提示转换超时：确认文件可在桌面版Office/LibreOffice打开，必要时提高超时，但不建议取消超时限制。
- 提示预览目录空间不足：清理无关联缓存或扩容，不能删除原始附件目录中的文件。
- 预览版式与原文件略有差异：属于Office转PDF兼容差异，以下载的原文件为准。

详细备份和恢复检查表见 [backup-and-recovery.md](backup-and-recovery.md)。
