---
name: textech-git-pull-sync
description: >-
  Syncs this TeXTech repo from GitHub when fetch/pull fails, including ghproxy
  mirror fetch and optional hard reset to remote. Use when the user @-mentions
  this skill or asks to pull from GitHub, fix fetch failures, or match remote.
disable-model-invocation: true
---

# TeXTech Git Pull Sync

按需 `@textech-git-pull-sync`。权威：`.cursor/rules/git-pull-sync.mdc`。所有 git 命令 `required_permissions: ["all"]`。

## 清单

```
Pull Sync:
- [ ] 1. git status / remote -v / 确认分支
- [ ] 2. 尝试 git fetch origin --prune
- [ ] 3. 若 443 失败 → 镜像 URL fetch，完成后恢复 origin
- [ ] 4. 按用户意图：仅 fetch / merge / reset --hard
- [ ] 5. 禁止擅自 git clean -fd；有 stash 勿自动 pop
```

## 标准 fetch

```powershell
git fetch origin --prune
git log HEAD..origin/master --oneline
```

## 镜像恢复（直连失败）

```powershell
$origin = "https://github.com/ImgoodWK/TeXTech-GTNH.git"
git remote set-url origin "https://ghproxy.net/https://github.com/ImgoodWK/TeXTech-GTNH.git"
git fetch origin --prune
git remote set-url origin $origin
# 仅当用户明确「以 GitHub 为准」：
# git reset --hard origin/master
```

备用镜像：`https://gh-proxy.com/https://github.com/...`

## 意图对照

| 用户说 | 动作 |
|--------|------|
| 只更新远程引用 | `fetch` only |
| 合入远程保留本地提交 | `merge` / `pull` |
| 以 GitHub 为准丢弃本地 | `reset --hard`（须明确授权） |

## 辅助

无法 curl 时可用 WebFetch：`https://api.github.com/repos/ImgoodWK/TeXTech-GTNH/commits/master` 对比本地 `git rev-parse HEAD`。
