# TeXTech 整合包安装说明（GTNH 2.9.0）

> 面向：**整合包作者 / 服管** · 目标包：**GTNH 2.9.0（含 2.9.0-beta-2 线）**  
> Mod ID：`textech` · 显示名：TeXTech / 铽丝科技 · MC 1.7.10 / Forge 10.13.4.1614  
> 最后同步：2026-07

本文可直接复制到你的 2.9.0 包说明、Curse/Modrinth 附加说明或服管手册。更细的玩法见 [用户手册](用户手册.md)。

---

## 1. 该装哪个 jar？

从本模组 `build/libs/` 或 GitHub Release 取文件时，按文件名筛选：

| 文件名特征 | 是否放入整合包 `mods/` | 说明 |
|------------|------------------------|------|
| `textech-*.jar`（**无**下列后缀） | **必装** | 主模组 |
| `*-voice.jar` | **可选** | 离线中文语音模型包（独立 modid `textechvoice`） |
| `*-webae.zip` | **可选，不放 `mods/`** | WebAE 网页资源；解压到服务端实例根目录 |
| `*-dev.jar` | 否 | 开发反混淆产物 |
| `*-dev-preshadow.jar` | 否 | Shadow 前中间产物 |
| `*-sources.jar` | 否 | 源码包 |
| 文件名含 `dirty` | 自测可、正式包不建议 | 表示构建时工作区有未提交改动 |

**Gradle / IDE「Build and Test」**：产物可用，但正式入包请用**无 `-dev` / `-preshadow` / `-sources`** 的主 jar；Release 尽量在干净提交上构建，避免 `-dirty`。

客户端与服务端的 `mods/` **都要**放主 jar（版本一致）。

---

## 2. 推荐放入整合包的文件布局

```
mods/
  textech-<version>.jar              # 必装：主模组
  textech-<version>-voice.jar        # 可选：离线语音（见 §4）
  mcef-1.7.10-0.6.jar                # 可选：游戏内 Chromium（外置下载，见 §4）
TeXTech/WebAE/ui/                    # 可选：textech-<version>-webae.zip 解压结果
```

不要把 Vosk 模型、MCEF、AstrBot 插件打进主 jar；本仓库发布时已刻意拆开。

---

## 3. 硬依赖（GTNH 2.9.0 包内通常已有）

Forge `@Mod` 声明：

- `required-after:gregtech`
- `required-after:structurelib`

实际玩法还依赖整合包内的 AE2 栈（GTNH 自带），例如：

- Applied Energistics 2（GTNH 分支）
- AE2FluidCraft-Rework（流体相关）
- NewHorizonsCoreMod / ModularUI2 / GTNHLib 等包内组件

**不要**把本模组装进裸 Forge 或明显旧于 2.9.0 的 GTNH 线；当前开发目标为 **GTNH 2.9.0-beta-2 栈**。

---

## 4. 软附属 / 可选资源（分开提供）

这些**不是**主 jar 的硬依赖；缺了游戏可启动，对应功能降级或关闭。

### 4.1 本仓库可随 Release 分发

| 产物 | Mod ID | 体积量级 | 作用 | 缺了会怎样 |
|------|--------|----------|------|------------|
| `*-voice.jar` | `textechvoice` | ~40–65 MB | 离线 Vosk 中文 STT；首次用会解包到数据目录 | 默认 `embedded-vosk` 不可用；可改 `voice.sttMode=http` 或本地模型路径；其它功能不受影响 |
| `*-webae.zip` | 无（网页资源） | ~1–3 MB | WebAE React 控制台 | API 可启动，但网页入口只显示资源未安装提示 |

语音 JAR 与主 jar **同目录**放入 `mods/`；WebAE ZIP 解压到服务端实例根目录，并确认 `TeXTech/WebAE/ui/index.html` 存在。

### 4.2 外置下载（不要当本模组构建产物上传）

| 名称 | 获取 | 作用 | 缺了会怎样 |
|------|------|------|------------|
| **MCEF** 1.7.10 → **0.6** | [montoyo.net/wd3/?modid=mcef](https://montoyo.net/wd3/?modid=mcef) | 监视器网页表面走本机 Chromium（`mcef`） | 仍可用 WebAE 页推帧 `browser-jpeg`，或主机 Chrome/Edge 截 embed `spa-jpeg` |
| JCEF 原生库 | [montoyo.net/jcef](https://montoyo.net/jcef) | MCEF 运行所需 | 官方说明：MCEF **&lt; 1.10.2** 可能与新版启动器不兼容；GTNH/Prism 上常「装了也起不来」——包说明里建议标为**实验性可选** |

**注意**：GitHub 上的 MCEF 1.12.2 API **不能**替代 1.7.10 整包。

### 4.3 GTNH 包内常见软增强（一般无需你额外塞）

| 模组 / 能力 | 典型 Mod ID / 来源 | TeXTech 用途 | 缺了会怎样 |
|-------------|-------------------|--------------|------------|
| BetterQuesting | `betterquesting` | WebAE 任务书相关 | 任务页/提交等不可用 |
| Spark | `spark` | WebAE Spark 分析页 | 该页与相关 API 不暴露 |
| JourneyMap | JourneyMap | 世界地图客户端地形源之一 | 降级到其它源 / 自建截图 |
| GTNH-Web-Map（Dynmap 系） | 包内 GWM | 世界地图 Dynmap 瓦片 | 地形改走自建等路径 |
| NotEnoughCharacters | NEC | 手册搜索拼音与 NEI 对齐 | 仍用内置 PinIn 拼音 |

### 4.4 非 Minecraft 模组（可选运维）

| 组件 | 位置 | 说明 |
|------|------|------|
| AstrBot 意图插件 | 本仓库 `integrations/astrbot/` | QQ 群机器人意图交接；**单独部署进程**，不放入 `mods/` |

---

## 5. 配置与数据目录（服管速查）

首次启动后生成：

| 路径 | 用途 |
|------|------|
| `config/textech/textech.cfg` | 主配置（含 AI / 语音 / WebAE 等） |
| `config/textech/ai-client-local.cfg` | 客户端本地 AI 密钥（勿提交到公共仓库） |
| `<实例>/TeXTech/` | 运行时数据（WebAE / Assistant / Grapple 等） |

启用 AI / HTTP 语音时：在配置或游戏内 AI 设置中填入 API Key，并确认隐私开关（`ai.privacyConfirmed` / `voice.privacyConfirmed`）。

离线语音：装好 `*-voice.jar` 后保持 `voice.sttMode=embedded-vosk`（Windows 64-bit 推荐）；非 Windows 请改用 `http`。

WebAE：先安装同版本 `*-webae.zip`，再在 cfg 中启用 Web 控制台；Token / 安全项见 [WebAE 用户手册](../webae/用户手册.md)。

---

## 6. 整合包作者检查清单

- [ ] 主 jar 已放入客户端与服务端 `mods/`，版本一致  
- [ ] **未**放入 `-dev` / `-preshadow` / `-sources`  
- [ ] 目标为 GTNH **2.9.0** 线；包内已有 GregTech、StructureLib、AE2  
- [ ] 是否随包提供 `*-voice.jar`（体积大，建议标「可选语音」分开放下载）  
- [ ] 是否随服务端提供匹配版本 `*-webae.zip`，且未把 ZIP 放入 `mods/`  
- [ ] 是否提及可选 MCEF + 启动器兼容风险；无 MCEF 时说明 browser-jpeg / spa-jpeg 仍可用  
- [ ] 服管文档提醒：AI Key、隐私确认、WebAE Token 勿写进公开预设  
- [ ] （可选）AstrBot / QQ 能力单独说明，勿与 MC mods 清单混淆  

---

## 7. Release 分发建议（本模组侧）

若你维护 TeXTech 的 GitHub Release，建议资产拆成：

1. **必传**：主 jar  
2. **另传**：`*-voice.jar`  
3. **另传**：`*-webae.zip`  
4. **仅说明链接**：MCEF、AstrBot 插件（不打进 TeXTech Release 也行）  

主 jar **不含** `assets/textech/voice/vosk/**` 或 `assets/textech/webae/**`，离线语音和 WebAE 网页资源均独立发布。

---

## 8. 相关文档

| 文档 | 内容 |
|------|------|
| [用户手册 §2](用户手册.md#2-环境与安装) | 玩家安装步骤 |
| [用户手册 §9 / §11.4](用户手册.md#9-语音助手) | 语音与 `voice` 配置 |
| [WebAE 用户手册](../webae/用户手册.md) | 浏览器控制台 |
| [Gradle 工作流](../developer/Gradle工作流.md) | 构建产物说明 |
| [GTNH 版本兼容说明](../developer/GTNH版本兼容说明.md) | 2.8 / 2.9 支持范围 |
| 仓库根 [README.md](../../../README.md) | 英文 Quick Start 产物表 |

---

## 9. 一句话给玩家

> 把 **TeXTech 主 jar** 放进 GTNH 2.9.0 的 `mods/`；要离线语音再加 **`*-voice.jar`**；要游戏内真浏览器再可选装 **MCEF 1.7.10-0.6**（可能与新启动器不兼容）。AI / WebAE 需自行配置密钥与 Token。
