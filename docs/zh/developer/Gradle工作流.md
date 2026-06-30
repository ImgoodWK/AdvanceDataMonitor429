# TeXTech Gradle 工作流

> 受众：开发者 · 构建 / 迁移 / 移植 · 最后同步：2026-06

本文档合并 GTNH ExampleMod 模板说明、构建系统迁移、模组移植流程与构建 FAQ。

---

## 目录

- [1. 项目构建概览](#1-项目构建概览)
  - [1.1 常用命令](#11-常用命令)
  - [1.2 关键文件](#12-关键文件)
  - [1.3 ExampleMod 模板特性](#13-examplemod-模板特性)
- [2. 构建系统迁移](#2-构建系统迁移)
  - [2.1 通用迁移步骤](#21-通用迁移步骤)
  - [2.2 Mixin 配置迁移](#22-mixin-配置迁移)
- [3. 模组移植指南](#3-模组移植指南)
  - [3.1 设置仓库与构建系统](#31-设置仓库与构建系统)
  - [3.2 精简 fork 与依赖](#32-精简-fork-与依赖)
  - [3.3 移植准备](#33-移植准备)
  - [3.4 移植代码](#34-移植代码)
- [4. 构建常见问题](#4-构建常见问题)
- [5. 高级扩展](#5-高级扩展)

---

## 1. 项目构建概览

TeXTech 基于 [GTNH ExampleMod 1.7.10](https://github.com/GTNewHorizons/ExampleMod1.7.10) 构建骨架，主要行为由 `com.gtnewhorizons.gtnhconvention` 插件提供。

### 1.1 常用命令

```powershell
.\gradlew.bat build              # 编译打包
.\gradlew.bat runClient          # 启动开发客户端
.\gradlew.bat runServer          # 启动开发服务端
.\gradlew.bat test               # 运行测试
.\gradlew.bat clean setupDecompWorkspace   # 重建反编译工作区
```

Unix-like shell 下对应使用 `./gradlew`。

### 1.2 关键文件

| 文件 | 作用 |
|------|------|
| `build.gradle.kts` | 应用 GTNH convention 插件；**勿随意修改**，升级时替换模板版本 |
| `gradle.properties` | modId、版本、MC/Forge/MCP 版本、Jabel、Mixin、Access Transformer 等 |
| `dependencies.gradle` | 模组依赖声明（GT5、AE2FC、Vosk/JNA/PinIn shadow 等） |
| `repositories.gradle` | 额外 Maven 仓库 |
| `libs/` | 本地 dev jar（Chisel、Galacticraft、IC2NuclearControl 等） |
| `jitpack.yml` | Jitpack CI 配置 |
| `.github/workflows/` | GitHub CI（构建、发布） |

### 1.3 ExampleMod 模板特性

- 可升级：替换 `build.gradle` 为新模板版本
- 可选 API artifact、版本替换、依赖 shadow
- Mixin 与 Access Transformer 支持
- Scala 支持（`src/main/scala/`）
- Git Tags 集成版本号
- Jitpack / GitHub CI 自动发布

**从零创建新模组**（参考流程）：

1. 解压 [project starter](https://github.com/GTNewHorizons/ExampleMod1.7.10/releases/download/master-packages/starter.zip)
2. 处理 LICENSE，初始化 Git
3. 修改 `gradle.properties`、包名与类名
4. 运行 `./gradlew build`

---

## 2. 构建系统迁移

适用于典型 Forge 模组（无特殊 core plugin / shadow / AT / ASM）。若缺少步骤，欢迎贡献补充。

### 2.1 通用迁移步骤

1. 从 [migration.zip](https://github.com/GTNewHorizons/ExampleMod1.7.10/releases/download/master-packages/migration.zip) 复制并替换仓库文件（**除 `build.gradle` 外**）
2. 将原 `build.gradle(.kts)` 中的 `repositories` 复制到 `repositories.gradle`
3. 将原 `dependencies` 复制到 `dependencies.gradle`
4. 用模板 `build.gradle` 替换原文件；自定义 task 移入 `addon.gradle`（存在时自动集成）
5. 适配 `gradle.properties`
6. 确保 `src/main/resources/mcmod.info` 含 `${modId}`、`${modName}`、`${modVersion}`、`${minecraftVersion}`
7. IDE 重新导入（IntelliJ 建议 clean caches 重启）
8. 运行 `./gradlew clean setupDecompWorkspace`

### 2.2 Mixin 配置迁移

参考 [example-mixins 分支](https://github.com/GTNewHorizons/ExampleMod1.7.10/tree/example-mixins)：

1. 从 `mixins.yourModId.json` 提取 mixin 包与 plugin 配置到 `gradle.properties`
2. 按示例实现 MixinPlugin
3. 删除 `mixins.mymodid.json`

---

## 3. 模组移植指南

### 3.1 设置仓库与构建系统

1. 查阅原模组 README/Wiki 中的特殊构建配置
2. Fork 原仓库以保留 commit 历史
3. 在 fork 上执行 [§2 构建系统迁移](#2-构建系统迁移)

### 3.2 精简 fork 与依赖

尽量消除对 `libs/` 具体 jar 的硬依赖，改用 Maven：

1. 查原项目是否已发布到 Maven / Jitpack
2. 若无且开源许可允许：fork → 添加 `jitpack.yml` 与 CI → 打 tag → 在 [jitpack.io](https://jitpack.io) 获取依赖坐标
3. 单 jar 可参考 [Jitpack 单文件发布](https://gist.github.com/jitpack-io/f928a858aa5da08ad9d9662f982da983)

若模组依赖其他模组，需先移植依赖链。

### 3.3 移植准备

构建并分类错误：

- **缺失引用**：类/方法/字段重命名或移除 → 调整调用
- **构建错误**：缺少外部库 → 添加 `dependencies.gradle` 依赖

先修复所有构建级错误，再进入代码移植。

### 3.4 移植代码

建议顺序：

1. 修复 moved/renamed：删除错误 import，IDE 自动导入等价类
2. 对无法快速修复的代码提供 stub（TODO 追踪）
3. 构建并尝试运行
4. 优先修复导致崩溃的问题
5. 由小到大逐步修复功能
6. 不值得维护的功能可放弃并在 issue 中说明
7. 回归测试，修复移植引入的 bug

---

## 4. 构建常见问题

### Select an mcp conf dir for the deobfuscator

可能弹出 MCP 反混淆器配置对话框：

![](http://i.imgur.com/gzBMLrr.png)

**解决方案**：指向 Forge 解压 conf 目录：

- Linux/macOS: `~/.gradle/caches/minecraft/net/minecraftforge/forge/1.7.10-10.13.4.1614-1.7.10/unpacked/conf`
- Windows: `%USERPROFILE%/.gradle/caches/minecraft/net/minecraftforge/forge/1.7.10-10.13.4.1614-1.7.10/unpacked/conf`

若仍无法解决，请在 GitHub 开 issue。

---

## 5. 高级扩展

### Access Transformers

在 `gradle.properties` 中定义 AT 配置文件。参考 [example-access-transformers 分支](https://github.com/GTNewHorizons/ExampleMod1.7.10/tree/example-access-transformers)。

**警告**：AT 可能导致反编译源码不可用；IntelliJ 无法在依赖中搜索无 sources 的类。

### Mixins

运行时修改原版/模组行为，无需改源码。在 `gradle.properties` 启用后自动生成 mixin 配置。参考 [Hodgepodge](https://github.com/GTNewHorizons/Hodgepodge/) 与 [Angelica](https://github.com/GTNewHorizons/Angelica/pull/8)。

### addon.gradle

项目需自定义 Gradle 命令时，添加 `addon.gradle[.kts]`（构建早期集成）或 `addon.late.gradle[.kts]`（晚期属性可用）。本地不提交的 tweak 用 `addon.local.gradle[.kts]`。

---

*模板原作者：SinTh0r4s、TheElan、basdxz · GTNewHorizons ExampleMod1.7.10*
