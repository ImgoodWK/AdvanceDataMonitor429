---
name: textech-cursor-automations
description: >-
  Drafts TeXTech Cursor Automations for PR doc-check, WebAE frontend CI-style
  checks, and release reminders. Use when the user @-mentions this skill or asks
  to create Cursor Automations for this repo.
disable-model-invocation: true
---

# TeXTech Cursor Automations

按需 `@textech-cursor-automations`。用 **Automate** / `open_automation` 打开编辑器落稿。

> 本地项目 MCP（`textech-doc-check` / `textech-build` 等）**不能**写入 Automation 的 MCP action。Agent 在云端用 Shell 跑命令；评论用 `prComment`。硬门禁已有 CI：`.github/workflows/ci.yml`。

## 三条 Automation（预填草稿）

### 1. TeXTech PR 文档门禁

| 项 | 内容 |
|----|------|
| 名称 | TeXTech PR 文档门禁 |
| 触发 | PR 打开 / PR 推送代码 → `ImgoodWK/TeXTech-GTNH` |
| 工具 | 评论 PR |
| 指令 | 跑 `python tools/doc-check/doc-consistency-check.py`，摘要评论到 PR；errors 标明 FAILED |
| 编辑器待完成 | 确认 GitHub 连接；可选忽略草稿 PR（已预填） |

### 2. TeXTech WebAE 前端检查

| 项 | 内容 |
|----|------|
| 名称 | TeXTech WebAE 前端检查 |
| 触发 | PR 打开 / PR 推送 → 同仓库（路径过滤若 UI 支持：`webae-frontend/**`） |
| 工具 | 评论 PR（可选） |
| 指令 | `cd webae-frontend && npm ci && npm run build && npm test`；失败贴日志尾部 |
| 编辑器待完成 | 路径过滤（若有）；确认 Node 云环境 |

### 3. TeXTech Release 提醒

| 项 | 内容 |
|----|------|
| 名称 | TeXTech Release 提醒 |
| 触发 | 推送到 `master`（tag 过滤在编辑器补；或改为手动） |
| 工具 | 按 UI |
| 指令 | 检查 README / docs/README；对照 `git-push-release` 清单；提醒 `gh release create` |
| 编辑器待完成 | 若只要 tag：改触发为 tag 推送或 webhook |

## Agent 操作

1. 展示上表 → 用户批准
2. 问是否打开编辑器 → 用户确认后 `open_automation`（一次一条）
3. 用户保存后再开下一条

## 不要做

- 不要把项目级 `textech-*` MCP 填进 Automation MCP 行
- 不要未批准就连开多条覆盖表单
