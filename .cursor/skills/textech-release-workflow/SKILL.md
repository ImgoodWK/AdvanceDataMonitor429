---
name: textech-release-workflow
description: >-
  Runs TeXTech Git push and GitHub Release workflow: README checks, commit
  messaging, gh release notes, and permission rules. Use when the user
  @-mentions this skill or asks to push, tag, or publish a GitHub Release.
disable-model-invocation: true
---

# TeXTech Release Workflow

按需 `@textech-release-workflow`。权威：`.cursor/rules/git-push-release.mdc`；拉取同步见 `git-pull-sync.mdc`。

## 清单

```
Release / Push:
- [ ] 1. 确认用户明确要求 push / release（否则不推送）
- [ ] 2. 检查 README.md + docs/README.md 是否反映现状
- [ ] 3. git status / diff / log；提交信息概括「为什么」
- [ ] 4. 排除 .workspace/、.cursor/plans/、密钥文件
- [ ] 5. 所有 git/gh 命令 required_permissions: ["all"]
- [ ] 6. Release：gh release create + notes（新功能/修复/工具链）
```

## 推送要点

- 用户说「以我本地为准覆盖远程」才考虑 `--force`；**禁止**对 main/master 擅自 force（规则有要求时再执行并警告）
- 必须包含 `.cursor/rules/`；不要提交 `.workspace/`
- Windows LF/CRLF 警告可忽略；不要改 `git config`

## Release 示例

```bash
gh release create vX.Y.Z --title "vX.Y.Z - …" --notes "…"
```

Notes 建议：新增功能、修复、依赖/工具链说明。

## 相关

- PR：按用户 creating-pull-requests 规则用 `gh pr create`
- 文档门禁：先 `@textech-doc-sync-pr` / CI `ci.yml` 的 `Documentation` Job
