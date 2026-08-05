# TeXTech Gradle 工作流

> 受众：开发者 · 构建 / 迁移 / 移植 · 最后同步：2026-08

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
.\gradlew.bat spotlessCheck     # 检查 Java / Gradle 格式
.\gradlew.bat test               # 运行 Java 测试
.\gradlew.bat build              # 编译、测试并生成全部发行资产
.\gradlew.bat runClient          # 启动开发客户端
.\gradlew.bat runServer          # 启动开发服务端
.\gradlew.bat voiceJar           # 仅打可选语音资源包
.\gradlew.bat webaeZip           # 仅打已提交的 WebAE 静态资源包
.\gradlew.bat -Ptextech.useChinaMirrors=true build # 国内网络显式启用镜像
```

`build/libs/` 产物：

| 产物 | 说明 |
|------|------|
| `textech-<tag>.jar` | 发布用本体；不含 WebAE 页面与 `assets/textech/voice/vosk/**` |
| `textech-<tag>-voice.jar` | 可选；modid `textechvoice`，含 Vosk 模型；客户端放入 `mods/` |
| `textech-<tag>-webae.zip` | 可选；解压到服务端实例根目录，使 `TeXTech/WebAE/ui/index.html` 存在 |
| `textech-<tag>-sources.jar` | 开发者源码参考，不放入玩家 `mods/` |

Tag `v3.0.0-rc.1` 对应的四个文件名分别为
`textech-v3.0.0-rc.1.jar`、`textech-v3.0.0-rc.1-voice.jar`、
`textech-v3.0.0-rc.1-webae.zip` 与 `textech-v3.0.0-rc.1-sources.jar`。
开发环境的 `runClient` 仍从源码树加载 Vosk，无需先安装 voice JAR。

可选 MCEF：官网仍提供 **1.7.10 / 0.6**（https://montoyo.net/wd3/?modid=mcef ）。将 `mcef-1.7.10-0.6.jar` 放入 `libs/`（开发）或游戏 `mods/`（玩法）后，`dependencies.gradle` 以 `devOnlyNonPublishable` 引入，`addon.late.gradle` 添加 ShutdownPatcher。GitHub Releases 仅有 1.12.2 API，不能替代 1.7.10 整包。运行时还要能访问 https://montoyo.net/jcef 下载原生库；官方警告 1.10.2 之前可能不兼容新版启动器。无 MCEF 时监视器主路径为浏览器推帧（`browser-jpeg`），主机 Chrome/Edge 截 embed（`spa-jpeg`）作兜底。
Unix-like shell 下对应使用 `./gradlew`。

### 1.2 关键文件

| 文件 | 作用 |
|------|------|
| `settings.gradle.kts` | 插件仓库、官方源默认值和国内镜像显式开关 |
| `build.gradle.kts` | 应用 GTNH convention 插件；**勿随意修改**，升级时替换模板版本 |
| `gradle.properties` | modId、版本、MC/Forge/MCP 版本、Jabel、Mixin、Access Transformer 等 |
| `dependencies.gradle` | 模组依赖声明（GT5、AE2FC、Vosk/JNA/PinIn shadow 等） |
| `repositories.gradle` | 额外 Maven 仓库 |
| `libs/` | 本地 dev jar（Chisel、Galacticraft、IC2NuclearControl 等） |
| `jitpack.yml` | Jitpack CI 配置 |
| `.github/workflows/` | 仓库自有 CI、CodeQL、Tag Release、Wiki 同步与来源监测 |

### 1.3 ExampleMod 模板特性

- 可升级：替换 `build.gradle` 为新模板版本
- 可选 API artifact、版本替换、依赖 shadow
- Mixin 与 Access Transformer 支持
- Scala 支持（`src/main/scala/`）
- Git Tag 集成版本号；TeXTech 使用仓库自有 CI 与 Tag Release 工作流

**从零创建新模组**（仅为上游 ExampleMod 参考流程）：

1. 解压 [project starter](https://github.com/GTNewHorizons/ExampleMod1.7.10/releases/download/master-packages/starter.zip)
2. 处理 LICENSE，初始化 Git
3. 修改 `gradle.properties`、包名与类名
4. 运行 `./gradlew build`

TeXTech 不再从本仓库发布 ExampleMod 的 `starter.zip`、`migration.zip`，也不创建
`latest-packages` 或 `v*-packages` Tag；这些不是玩家发行资产。

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

### Could not find CodeChickenLib / 插件或依赖解析失败

默认构建与 CI 只使用 Gradle Plugin Portal、Maven Central、GTNH Nexus 和项目明确声明的官方仓库；国内镜像不是隐式唯一来源。`mavenLocal()` 仅在非 CI 环境作为最后来源，Tag 构建不会依赖本机发布物。

国内网络需要镜像时必须显式传入：

```powershell
.\gradlew.bat -Ptextech.useChinaMirrors=true build
```

该属性默认 `false`，CI 与 Release 工作流也保持默认值。启用后阿里云/腾讯云镜像会加入仓库列表，官方源仍保留作为回退；若镜像本身返回 5xx，去掉该属性即可恢复官方源优先的可复现路径。

依赖解析问题也可能来自**过期的模块元数据缓存**：

**一次性修复**（在项目根目录执行）：

```powershell
.\gradlew.bat --refresh-dependencies compileJava
```

若仍失败，可删除本地缓存后重试：

```powershell
Remove-Item -Recurse -Force "$env:USERPROFILE\.gradle\caches\modules-2\files-2.1\com.github.GTNewHorizons\CodeChickenLib" -ErrorAction SilentlyContinue
.\gradlew.bat compileJava
```

不要为修复单个依赖而把镜像设为 CI 唯一来源；如官方 GTNH 坐标确实缺失，应先核对 `dependencies.gradle` 中的版本与 GTNH Nexus。

### GregTech / ModularUI2 / AE2（GTNH 2.9.0-beta-2）

本仓库 **已对齐 GTNH 整合包 2.9.0-beta-2**（不再兼容 5.09.51 / ModularUI2 2.2 / GTNHLib 0.10 旧栈）：

| 组件 | 版本 |
|------|------|
| GT5-Unofficial | `5.09.54.20` |
| Applied-Energistics-2 | `rv3-beta-1000-GTNH` |
| AE2FluidCraft-Rework | `1.5.95-gtnh` |
| ModularUI2 | `2.3.79-1.7.10` |
| GTNHLib | `0.11.24` |
| BetterQuesting | `3.8.72-GTNH` |
| NewHorizonsCoreMod | `2.9.5` |
| StructureLib | `1.4.42` |
| NotEnoughItems | `2.8.111-GTNH` |

`addon.gradle` 对上述坐标做 `resolutionStrategy.force`。

**AE2 可选 API stub**：发布的 AE2 jar 中部分 Tile（如 `TileChest`）字节码 implements 了 Mekanism / RotaryCraft 接口；TeXTech 用 `tools/ae2-optional-stubs/` 生成 `build/ae2-optional-api-stubs.jar`（`compileOnly`）。**CoFH RF** 由 AE2 传递的 Curse CoFH Core 在运行时提供（GT++ cryotheum 依赖 `Mods.COFHCore`）；`runClient*` 前会跑 `syncDevCofhMappingsConfig`，把 `run/config/CodeChickenLib.cfg` 的 `mappingDir` 指到 `GRADLE_USER_HOME` 下的 MCP conf，避免 CoFH coremod 在 RFG 反混淆环境崩溃。

### BetterQuesting / WebAE 任务书调试

- **模组依赖**：`BetterQuesting 3.8.72-GTNH` + `GTNHLib 0.11.24`（任务完成 title 通知依赖 `TitleAPI.setEffectTier`）。
- **常见崩溃**：`NoSuchMethodError: TitleAPI.setEffectTier` → 运行时 gtnhlib 过旧；清理 `run/mods` 旧 jar 后 `--refresh-dependencies`。
- **任务数据**：整合包任务快照在 `dev-fixtures/betterquesting/`（见 `SOURCE.json`）。`runClient` / `runServer` 前自动 `syncDevBetterQuesting`。
- **手动同步**：`.\gradlew.bat syncDevBetterQuesting`
- **进游戏加载**：OP 执行 `/bq_admin default load`；WebAE `?page=quests` 或游戏内任务书验证。
- **更新快照**：`powershell -ExecutionPolicy Bypass -File tools/dev/sync-betterquesting-from-gtnh.ps1`，然后提交 `dev-fixtures/betterquesting/`。

### NBTEdit `NoSuchFieldError: field_71412_D`

`run/mods/ForgeNBTEdit-universal-1.0.0.test.jar` 为旧版 In-game NBTEdit，与 Java 17 / lwjgl3ify 运行时不兼容（访问 `Minecraft.mcDataDir` 的 SRG 字段名失效）。该 jar **不是** Gradle 依赖，多为历史手动放入。

删除后重跑即可：

```powershell
Remove-Item -Force "run\mods\ForgeNBTEdit-universal-1.0.0.test.jar" -ErrorAction SilentlyContinue
.\gradlew.bat runClient17
```

查看 NBT 请用本模组自带的映录器 GUI（`GuiNbtViewer`）或 NEI。

### CoFH `Failed to select mappings directory` / GT++ null cryotheum

AE2 / WAILA 会传递引入 Curse `cofh-core`。其 coremod 默认在 `~/.gradle/caches/minecraft/.../unpacked/conf` 找 MCP 映射；本仓库常用 `GRADLE_USER_HOME=D:\dev-agent-cache\gradle`。若 mappings 找不到会立刻崩溃；若排除 CoFH，则 GT++ 无法注册 cryotheum，随后在 `MTEIndustrialVacuumFreezerLegacy` 以 `Cannot create a fluidstack from a null fluid` 失败。

`addon.gradle` 的 `syncDevCofhMappingsConfig` 在 `runClient*` / `runServer*` 前写入 `run/config/CodeChickenLib.cfg` 的 `mappingDir`。勿全局 exclude CoFH Core。

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
