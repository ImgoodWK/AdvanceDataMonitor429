# AdvanceDataMonitor 文档中心（中文）

> Mod ID: `advancedatamonitor` · Minecraft 1.7.10 / GTNH · 最后同步：2026-06

English docs: [docs/en/README.md](../en/README.md)

---

## 按受众选择

| 你是谁 | 从这里开始 |
|--------|-----------|
| **新玩家** | [用户手册 §0 快速了解](player/用户手册.md#0-快速了解) |
| **计划器用户** | [用户手册 §19 高级计划器](player/用户手册.md#19-高级计划器) |
| **服管 / 整合包作者** | [用户手册 §2 环境与安装](player/用户手册.md#2-环境与安装) · [§11 配置文件](player/用户手册.md#11-配置文件详解) |
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

### 开发者向

| 文档 | 说明 |
|------|------|
| [developer/技术文档.md](developer/技术文档.md) | 项目结构、Forge 注册、核心模块、数据流（高级计划器 API：§5.11） |
| [developer/Gradle工作流.md](developer/Gradle工作流.md) | ExampleMod 模板、构建迁移、FAQ |
| [developer/临时材质清单.md](developer/临时材质清单.md) | 缺失/占位方块与物品材质审计；**临时**程序化贴图说明 |
| [developer/GTNH版本兼容说明.md](developer/GTNH版本兼容说明.md) | v1.0.0 对 2.8.x / 2.9.0-beta+ 的支持范围与下一版移除 Legacy 计划 |
| [developer/ae-compat-290.md](developer/ae-compat-290.md) | AE 双路径兼容技术细节（开发者） |

### AI 助手专项

| 文档 | 说明 |
|------|------|
| [ai-assistant/开发指南.md](ai-assistant/开发指南.md) | Part A 架构 · Part B 必改文件 · Part C 本地 STT |

### 子系统

| 文档 | 说明 |
|------|------|
| [subsystems/挂索节点系统设计.md](subsystems/挂索节点系统设计.md) | 挂索状态机、磁吸、网络包、Config |

### 归档

| 文档 | 说明 |
|------|------|
| [archive/GoldenThrone_GT_Multiblock_移植指南.md](archive/GoldenThrone_GT_Multiblock_移植指南.md) | 黄金王座 GT 多方块移植手册（已从本模组剥离，仅供历史参考） |

---

## 命名规范

文档中的方块/物品显示名以 `lang/zh_CN.lang` 为准，例如：

- **高级数据监视器**、**数据映录器**
- **网络链接器**、**合成链接器**、**高级存储链接器**、**高级存储链接元件**
- **挂索节点** / **挂索器**、**高级计划器**、**超能砂糖桔**、**至高天圣裁**
- **高级数据监视器手册**（物品 `manual`）
