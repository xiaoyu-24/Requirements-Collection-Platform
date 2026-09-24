# 将公开版本发布到 GitHub

本说明适用于已整理好的独立公开版本。原内部项目应继续保持私有；不要把原仓库直接改为 Public。

## 1. 确认使用正确目录

在生成的 `xqfx-public` 目录打开 PowerShell。该目录已初始化独立 Git 仓库，只有新的公开源码历史。

```powershell
git status --short
git log --oneline
git remote -v
```

首次交付时，工作区应干净，历史应只有一个初始提交，远端应为空。使用通用项目作者身份创建初始提交，避免带入个人邮箱；可在 GitHub 账号设置中选用自己的 noreply 邮箱作为后续提交身份。

## 2. 在 GitHub 创建空仓库

打开 [GitHub 新建仓库](https://github.com/new)：

1. 选择自己的账号或有权限的组织。
2. Repository name 填 `xqfx-public`，或其他尚未使用的名字。
3. Visibility 选择 **Public**。
4. **不要**勾选初始化 README，不要添加 `.gitignore` 或 License；本地已经有 README 和忽略规则。
5. 点击 **Create repository**，复制新仓库的 HTTPS 地址。

这一步创建的是全新仓库，不是 fork，也不是把内部仓库转为公开。

## 3. 推送公开源码

仍在 `xqfx-public` 目录执行：

```powershell
$publicRepoUrl = Read-Host '粘贴刚创建的全新 GitHub 仓库 HTTPS 地址'
git remote add origin $publicRepoUrl
git remote -v
```

确认输出是刚创建的新仓库后，再执行：

```powershell
git push -u origin main
```

按 Git 凭据管理器提示登录 GitHub。不要把访问令牌写进命令、URL、README 或项目配置。若提示仓库不为空，先核实仓库是否选对；不要使用强制推送覆盖其他仓库。

## 4. 检查 GitHub 页面

- 首页显示中文 README，English 链接可切换英文。
- Commits 中只有公开版本自己的历史。
- V22/V23 中只有说明和空操作，没有真实账号或密码哈希。
- 文件列表不含 `release/`、`backend/target/`、`frontend/node_modules/`、上传数据、数据库备份或本地环境配置。
- About 可填写：`Self-hosted requirements management with Vue 3, Spring Boot, and optional AI-assisted entry.`

## 5. 后续修改与发布包

后续在此公开仓库中开发并按正常流程提交。需要同步内部功能时只移植审核过的源码改动，不要把内部仓库作为父历史合并进来。

```powershell
git status --short
git diff
# 审查后，只添加这次准备公开的文件
git add README.md README.en.md
git commit -m "docs: update project documentation"
git push
```

部署包从本仓库重新构建。可把检查后的 JAR 和前端 ZIP 上传到新仓库的 GitHub Releases，不要上传内部版本的旧包。本地交付的源码 ZIP 仅包含初始公开提交的文件，不包含 `.git`，适合备份或分享源码；如需按上述命令推送，应使用已经初始化好的 `xqfx-public` 目录。

## 许可证

当前未擅自添加许可证。公开仓库可以先展示源码；若希望他人按明确开源条款使用、修改和分发，再选择合适许可证并加入 `LICENSE`，同时更新双语 README。许可证选择不会影响本次人员数据脱敏。
