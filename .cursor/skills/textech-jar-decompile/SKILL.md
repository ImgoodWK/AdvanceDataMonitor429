---
name: textech-jar-decompile
description: >-
  Lists and decompiles TeXTech libs/*.jar into .workspace/decompiled/ for
  reference analysis. Use when the user @-mentions this skill or needs to
  inspect mod classes from local jars without polluting src/.
disable-model-invocation: true
---

# TeXTech Jar Decompile

按需 `@textech-jar-decompile`。权威：`external-deps-jars.mdc` + `workspace-artifacts.mdc`。

## 清单

```
Jar Decompile:
- [ ] 1. 确认 jar 在 libs/（没有则走 gtnh-deps Skill / 询问用户）
- [ ] 2. 输出只写 .workspace/decompiled/<mod>/ 或 .workspace/ae2_sources/
- [ ] 3. 先 jar tf / 列类，再按需反编译单个包
- [ ] 4. 不 git add .workspace/
```

## 推荐流程

```powershell
# 列表
jar tf libs\SomeMod.jar | Select-String "com/example"

# 或脚本（输出到 .workspace/decompiled）
.cursor/skills/textech-jar-decompile/scripts/list-jar.ps1 -Jar libs\SomeMod.jar -Filter com/example
```

反编译优先级：

1. 本机已有 **CFR / Vineflower / Fernflower** → 输出到 `.workspace/decompiled/<name>/`
2. 否则对单个 `.class`：`javap -c -p -classpath libs\SomeMod.jar fully.qualified.Name`
3. 仍不够 → 询问是否安装反编译器，或用户提供源码仓库（clone 到 `.workspace/external-snippets/`，权限 `["all"]`）

## 禁止

- 反编译产物写入 `src/`、`libs/` 覆盖、或仓库根目录散落 `.class`
- `libs/` 已有 jar 仍反复问用户要不要加

## 相关

- AE 兼容只读 `compat/ae/AeCompat`；参考源码可放 `.workspace/ae2_sources/`
