# 备份与恢复检查表

## 备份批次

一次备份必须同时包含：

1. MySQL 导出文件 `requirements.sql`。
2. 完整附件目录 `attachments`。
3. `manifest.json`，记录同一批次号和创建时间。

预览目录属于可重建缓存，默认不要求备份。若选择备份预览目录，必须与数据库和原始附件使用同一批次，不能单独恢复旧预览缓存。

使用 [deployment.md](deployment.md) 的命令每天执行一次，至少保留最近 7 个批次。不要只备份数据库：附件实体文件不在 MySQL 中。

## 恢复步骤

1. 停止后端写入，并确认待恢复批次的 SQL、附件目录和 manifest 都存在。
2. 新建或清空目标数据库；导入 SQL：`mysql -u requirements_app -p requirements_platform < requirements.sql`。
3. 将同批次 `attachments` 复制为 `ATTACHMENTS_ROOT` 指向的目录。
4. 配置数据库连接、原始附件目录、预览目录和LibreOffice可执行路径，启动后端。
5. 检查 `/api/health` 返回 `{"status":"UP"}`，并抽查系统、需求、附件下载、图片和PDF预览。
6. 对一个Word和一个Excel附件点击“重新生成”，确认恢复环境能够重新生成PDF预览。

## 版本边界

本公开版本只支持全新部署及其后续备份恢复。不要将使用原内部迁移链的数据库备份直接导入本版本；V22/V23 的校验和不同。恢复演练应使用独立数据库和附件目录。
