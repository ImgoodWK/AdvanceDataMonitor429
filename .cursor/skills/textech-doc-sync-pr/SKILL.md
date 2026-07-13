---
name: textech-doc-sync-pr
description: >-
  Syncs TeXTech docs, lang, manual JSON, and project-structure rules after code
  changes; runs tools/doc-check/doc-consistency-check.py and reports gaps.
  Use when editing src/main/java, assets/textech/lang, docs/, manual/,
  .cursor/rules/project-structure*.mdc, or when the user mentions PR prep,
  documentation sync, doc-check, or bilingual lang updates.
---

# TeXTech Doc Sync / PR Prep

文档与规则同步工作流。规则兜底见 `.cursor/rules/docs-sync.mdc`；地图见 `docs/zh/developer/documentation-map.md`。

## 何时自动走本 Skill

- 新增 / 删除 / 重命名 Java 源文件或 lang 键
- 修改 `docs/`、`manual/`、`Config` 对外行为
- 用户说：提 PR、检查文档、文档是否漏改
- **任务主工作完成时**自动跑 doc-check（不必等用户提醒）

复杂 PR 仍建议用户 `@textech-doc-sync-pr` 点名一次更稳。

## 清单

```
Doc Sync:
- [ ] 1. 按 documentation-map 更新受影响文档 / lang / manual / rules
- [ ] 2. 运行 doc-check
- [ ] 3. 总结列出已同步与未同步路径（陈述句，不追问）
```

### 1. 按域同步

对照 `docs/zh/developer/documentation-map.md` 与 `docs-sync.mdc` 触发表，至少检查：

| 变更 | 必更新 |
|------|--------|
| 新增/删/重命名 Java | `project-structure.mdc` + `project-structure-details.mdc` |
| Config 项 | `ConfigDescriptions.java`、手册 `config_reference`、相关 docs |
| 网络包 | `network-packets.mdc`（ID 表） |
| 玩家可见文案 | `en_US.lang` + `zh_CN.lang`（成对） |
| 玩家功能 | `manual/chapters/` + 对应 docs |
| WebAE API/行为 | `docs/zh/webae/` + `docs/en/webae/` |
| AI 助手 | 另遵循 `ai-assistant-docs-sync.mdc` |

### 2. 运行 doc-check

优先（PowerShell）：

```powershell
.cursor/skills/textech-doc-sync-pr/scripts/run-doc-check.ps1
```

或：

```bash
python tools/doc-check/doc-consistency-check.py
```

若已配置 `textech-doc-check` MCP，也可调用 `run_doc_consistency_check`。

脚本为 warn-only 风格：修复 **errors**；warnings 在总结中列出。

### 3. 总结格式（陈述句，不追问）

- 已同步：列出路径
- 未同步 / 缺口：列出建议路径（不要问「要不要现在改」）

用户明确说「不用动文档」时跳过更新，但仍可跑 check 并报告现状。

## 完成定义

- 受影响文档 / lang / manual / structure 规则已按地图更新（或已声明跳过原因）
- doc-check 已运行，errors 已处理或已说明阻塞
- 总结含已同步与未同步路径
