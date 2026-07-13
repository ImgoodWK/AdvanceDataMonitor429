---
name: textech-gtnh-deps-troubleshoot
description: >-
  Troubleshoots TeXTech GTNH Gradle/Maven dependency and libs/ jar issues,
  including decompile-to-.workspace workflow. Use when the user @-mentions this
  skill or reports missing jars, unresolved dependencies, or AE compat classpath
  problems.
disable-model-invocation: true
---

# TeXTech GTNH Deps Troubleshoot

按需 `@textech-gtnh-deps-troubleshoot`。权威：`external-deps-jars.mdc`、`agent-execution-permissions.mdc`、`workspace-artifacts.mdc`。

## 决策树

```
Deps:
1. 先查 libs/ 是否已有目标 jar
   - 有 → 直接列表/反编译到 .workspace/（勿再问）
   - 无 → 是否应走 Maven（dependencies.gradle）？
2. Maven 依赖 → ./gradlew 解析；网络权限按 agent-execution-permissions
3. 仍缺本地 jar → 询问用户是否手动放入 libs/ 或跳过
4. 用户添加后 → 更新 external-deps-jars.mdc 清单
```

## 常用命令

```powershell
# 依赖解析 / 编译
.\gradlew dependencies
.\gradlew build

# 反编译输出必须在 .workspace/
# 例：.workspace/decompiled/<mod>/
```

从 GitHub 拉参考仓库：目标目录 `.workspace/external-snippets/<repo>`，Shell **首次** `required_permissions: ["all"]`。

## 禁止

- 把反编译 / 临时 jar 解压放进 `src/` 或仓库根目录
- `libs/` 已有 jar 仍反复问用户
- 未询问就假定用户会补缺失的非 Maven jar

## AE 兼容

改 AE 路径只经 `compat/ae/AeCompat`；profile 见 `docs/zh/developer/ae-compat-290.md`。
