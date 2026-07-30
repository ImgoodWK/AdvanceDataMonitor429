# TeXTech 文档中心（中文）

> Mod ID: `textech` · Minecraft 1.7.10 / GTNH · TeXTech 2.0 · 最后同步：2026-07

English docs: [docs/en/README.md](../en/README.md) · 总索引：[docs/README.md](../README.md)

TeXTech 2.0 面向 GTNH `2.9.0-beta-2+`。Release 的主模组 JAR、可选离线语音 JAR 与可选 WebAE ZIP 分开发布；使用 WebAE 时把 ZIP 解压到服务端实例根目录，并确认 `TeXTech/WebAE/ui/index.html` 存在。不使用 WebAE 时只下载主 JAR 即可。

---

## 文档层级

| 层级 | 作用 | 路径 |
|------|------|------|
| L0 机器可读 | Agent / CI 事实源 | `ConfigDescriptions.java`、`LoaderNetwork.java`、`.cursor/rules/` |
| L1 开发者规格 | 贡献者设计文档 | `developer/`、`webae/开发者手册.md`、`ai-assistant/` |
| L2 玩家/服管 | 教程与操作 | `player/`、`webae/用户手册.md`、`assets/textech/manual/` |
| L3 愿景/设计 | **非实现规格** | `design/` |

维护地图：[documentation-map.md](developer/documentation-map.md)

---

## 按受众选择

| 你是谁 | 从这里开始 |
|--------|-----------|
| **新玩家** | [用户手册 §0 快速了解](player/用户手册.md#0-快速了解) |
| **计划器用户** | [用户手册 §19 高级计划器](player/用户手册.md#19-高级计划器) |
| **服管 / 整合包作者** | [整合包安装-GTNH-2.9.0](player/整合包安装-GTNH-2.9.0.md) · [用户手册 §2](player/用户手册.md#2-环境与安装) · [§11 配置](player/用户手册.md#11-配置文件详解) · [WebAE 用户手册](webae/用户手册.md) |
| **新贡献开发者** | [开发者技术文档](developer/技术文档.md) · [Gradle 工作流](developer/Gradle工作流.md) |
| **改 AI 助手** | [AI 助手开发指南](ai-assistant/开发指南.md) · `.cursor/rules/ai-assistant.mdc` |
| **改挂索** | [挂索节点系统设计](subsystems/挂索节点系统设计.md) |
| **改计划器代码** | [开发者技术文档 §5.11](developer/技术文档.md#511-advance-planner高级计划器) |

---

## 文档树

### 玩家向

| 文档 | 说明 |
|------|------|
| [player/用户手册.md](player/用户手册.md) | 安装、方块物品、监视器/AE2 教程、AI/语音助手、配置、FAQ、高级计划器 |
| [player/整合包安装-GTNH-2.9.0.md](player/整合包安装-GTNH-2.9.0.md) | 2.9.0 整合包作者：该装哪个 jar、软附属拆分、检查清单 |

### WebAE 控制台

| 文档 | 说明 |
|------|------|
| [webae/用户手册.md](webae/用户手册.md) | WebAE 启用、Token、各功能页面、命令与安全注意事项 |
| [webae/开发者手册.md](webae/开发者手册.md) | 架构、REST API、网络包、前端构建、子系统设计 |
| [webae/oc-integration.md](webae/oc-integration.md) | OpenComputers Internet Card 只读 OC 集成 API |

### 开发者向

| 文档 | 说明 |
|------|------|
| [developer/技术文档.md](developer/技术文档.md) | 项目结构、Forge 注册、核心模块、数据流（高级计划器 API：§5.11） |
| [developer/documentation-map.md](developer/documentation-map.md) | 改功能应更新哪些文档/规则 |
| [developer/ui-framework.md](developer/ui-framework.md) | 容器 GUI 9-slice 框架、`ADM_UiContainer`、调试状态表 |
| [developer/new-feature-checklist.md](developer/new-feature-checklist.md) | 新功能开发决策清单（基类选型、网络包、lang 同步） |
| [developer/Gradle工作流.md](developer/Gradle工作流.md) | ExampleMod 模板、构建迁移、FAQ |
| [developer/临时材质清单.md](developer/临时材质清单.md) | 缺失/占位方块与物品材质审计；**临时**程序化贴图说明 |
| [developer/GTNH版本兼容说明.md](developer/GTNH版本兼容说明.md) | v2.0.0 以 GTNH 2.9.0-beta-2 为最低目标；2.8.x 已不再支持 |
| [developer/ae-compat-290.md](developer/ae-compat-290.md) | GTNH 2.9.0-beta-2 NativeFluid 集成与遗留源码边界 |

### AI 助手专项

| 文档 | 说明 |
|------|------|
| [ai-assistant/开发指南.md](ai-assistant/开发指南.md) | Part A 架构 · Part B 必改文件 · Part C 本地 STT |

### 子系统

| 文档 | 说明 |
|------|------|
| [subsystems/挂索节点系统设计.md](subsystems/挂索节点系统设计.md) | 挂索状态机、磁吸、网络包、Config |

### 设计草案

| 文档 | 说明 |
|------|------|
| [design/品牌视觉设计指南.md](design/品牌视觉设计指南.md) | 模组名称、世界观、配色、宣传图规格与 AI 生图提示词 |
| [design/未来开发愿景.md](design/未来开发愿景.md) | 长期功能愿景与架构草案（非当前实现规格） |

### 归档

| 文档 | 说明 |
|------|------|
| [archive/GoldenThrone_GT_Multiblock_移植指南.md](archive/GoldenThrone_GT_Multiblock_移植指南.md) | 黄金王座 GT 多方块移植手册（已从本模组剥离，仅供历史参考） |

---

## 命名规范

文档中的方块/物品显示名以 `lang/zh_CN.lang` 为准，例如：

- **高级数据监视器**、**数据映录器**
- **网络链接器**、**合成链接器**、**高级存储链接器**、**高级存储链接元件**、**物质球解压器**
- **挂索节点** / **挂索器**、**高级计划器**、**超能砂糖桔**、**至高天圣裁**
- **铽丝科技手册**（物品 `manual`）
