---
name: textech-cursor-account-migrate
description: >-
  Migrates TeXTech Cursor setup after switching Cursor accounts: verifies
  project MCP/skills/rules, re-creates Automations, and re-links dashboard MCP.
  Use when the user @-mentions this skill or says they switched Cursor accounts
  and need to restore MCP, Automations, or Agent workflows.
disable-model-invocation: true
---

# TeXTech Cursor 账号迁移

换 Cursor 账号后按本清单恢复。**仓库内配置会跟着 git 走**；**账号级配置要重做**。

## 一句话原则

| 跟着仓库（新账号打开同一项目即有） | 跟着旧账号（需在新账号重配） |
|----------------------------------|------------------------------|
| `.cursor/rules/*.mdc` | Dashboard / Settings 里的 MCP（GitHub、Slack…） |
| `.cursor/skills/**` | MCP OAuth 授权状态 |
| `.cursor/mcp.json` + `tools/mcp/**` | Cursor Automations（那三条） |
| `.github/workflows/**` | Cloud Agent 额度 / 团队设置 |
| 本机 `py` / Node / `run/` 游戏数据 | 用户级 Cursor Settings（主题等，可选） |

## 迁移前（旧账号，可选备份）

```
Migrate prep:
- [ ] 确认本仓库已 push（含 .cursor/skills、.cursor/mcp.json、CI）
- [ ] 在 Automations UI 记下三条名称与触发（或打开本 Skill 第三节）
- [ ] 记下 Dashboard 已接的 MCP 名称（GitHub 等）
- [ ] 不要指望导出本地 MCP token；换号后重登即可
```

**不必备份**：项目 MCP 服务器代码、Skill、规则——都在 git 里。

## 迁移后（新账号）— Agent 执行清单

用户说「换好账号了 / 迁移」时：

```
Migrate restore:
- [ ] 1. 用新账号打开本仓库同一路径
- [ ] 2. Reload MCP；确认四个 textech-* 出现（见下）
- [ ] 3. 冒烟：doc-check 的 check_manual_chapters 或 webae_config
- [ ] 4. 按第三节重开三条 Automation（一次一条）
- [ ] 5. 若需要 PR 评论 / gh：新账号接 GitHub（Dashboard 或 gh auth）
- [ ] 6. 提醒：WEBAE_TOKEN 等环境变量在本机用户环境，与 Cursor 账号无关
```

### 项目 MCP（应自动出现）

来自 `.cursor/mcp.json`：

- `textech-doc-check`
- `textech-webae-api`
- `textech-admweb`
- `textech-build`

若缺失：检查 `mcp.json` 是否在磁盘上 → Cursor Settings → MCP → 启用项目服务器 → Reload。

本机依赖：`py -3`、（build 用）JDK + Node。Windows 上命令是 `py` 不是 `python`。

### 项目 Skill（应自动可 @）

`textech-webae-full-build`、`textech-doc-sync-pr`（自动 invoke）及其余手动 Skill，均在 `.cursor/skills/`。换号后无需复制。

## 三条 Automation 重开（账号级）

旧账号里的 Automation **不会**自动出现在新账号。用 `@textech-cursor-automations` 或按下面名称重开：

1. **TeXTech PR 文档门禁** — PR 打开/推送 → doc-check → PR 评论  
2. **TeXTech WebAE 前端检查** — PR → `webae-frontend` npm ci/build/test  
3. **TeXTech Release 提醒** — push `master` → README/发版清单  

仓库：`ImgoodWK/AdvanceDataMonitor429`，默认分支 `master`。

Agent：一次只 `open_automation` 一条；用户保存后再开下一条。

> 不要把 `textech-*` 项目 MCP 填进 Automation 的 MCP 动作（Dashboard 看不到本地服务器）。

## 刻意不迁移

- admweb **RCON**（项目未实现）  
- 旧账号 Cloud 对话历史（一般不可搬）  
- 本机 `run/TeXTech/WebAE/web-tokens.json`（跟游戏实例，不跟 Cursor 账号）

## 完成后对用户说

- 项目 MCP / Skill / 规则：已随仓库恢复（或列出仍缺的）  
- Automation：已重开几条 / 还差几条  
- Dashboard MCP：还需用户在设置里点的项  
