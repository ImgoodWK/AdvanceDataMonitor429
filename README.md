# AdvanceDataMonitor

**Minecraft 1.7.10 / GTNH (GregTech: New Horizons) 社区模组** — 高级数据监视器

> Mod ID: `advancedatamonitor` · 平台: Forge 10.13.4.1614 · MCP stable_12

---

## 🤖 AI 驱动开发

本项目采用 **AI 辅助开发** 模式，核心开发工具链：

| 工具 | 用途 |
|------|------|
| **Cursor** | AI-native IDE，智能代码补全、重构与项目管理 |
| **Claude Code** | Anthropic Claude 驱动的代码生成与审查 Agent |
| **GPT-5.5** | OpenAI 最新大模型，负责复杂架构设计与意图解析 |
| **DeepSeek V4 Pro** | 高效推理模型，处理流式对话响应与任务分解 |

模组内置的 AI 助手功能（语音/文字对话、AE2 物品查询、合成下单、任务计划）正是基于上述模型实现。

---

## 📋 功能概览

### 核心监视器
- **AE2 网络数据监视器** — 实时显示 ME 网络的物品、流体、能量、蒸汽数据
- **折线图/柱状图** — 历史趋势可视化，支持多数据源叠加对比
- **合成 CPU 监控** — 追踪 AE2 合成任务进度与耗时
- **存储网络监控** — 全网物品总量、类型统计

### AI 助手系统
- **语音对话** — V 键语音输入（离线 Vosk + 在线 STT 双模式）
- **文字聊天** — O 键打开 AI 聊天界面，支持流式响应
- **意图识别** — 自然语言理解，自动执行 AE2 查询、合成下单、传送等操作
- **计划器** — 创建多步骤任务计划，AI 辅助跟踪与提醒

### 高级计划器
- **多任务管理** — 游戏内创建、编辑、排序任务条目
- **进度追踪** — 任务状态展示与提醒通知
- **HUD 叠加层** — 屏幕角落实时显示计划进度

### 存储链接元件
- **矿典过滤 (Ore Filter)** — 按矿物词典筛选物品
- **流体标记 (Fluid Marker)** — AE2FC 流体精确匹配
- **名称显示开关** — 独立控制名称、数量、进出显示

---

## 🚀 快速开始

### 构建

```powershell
.\gradlew.bat build          # 编译打包
.\gradlew.bat runClient      # 启动开发客户端
```

### 依赖

构建依赖通过 GTNH Maven 仓库自动解析，本地 jar 依赖放在 `libs/` 目录：

- GT5-Unofficial 5.09.51.470
- AE2FluidCraft-Rework 1.3.7-gtnh
- Thaumcraft 1.7.10-4.2.3.5 (compileOnly)
- NewHorizonsCoreMod 2.7.260
- Chisel-2.14.1-GTNH-dev
- Galacticraft-3.3.12-GTNH-dev
- IC2NuclearControl-2.7.8-dev

---

## 📚 文档索引

| 文档 | 说明 |
|------|------|
| [AdvanceDataMonitor_使用说明.md](AdvanceDataMonitor_使用说明.md) | 功能介绍、安装、配置与使用教程 |
| [AdvanceDataMonitor_开发者技术文档.md](AdvanceDataMonitor_开发者技术文档.md) | 项目结构、模块职责、数据流与扩展点 |
| [AdvanceDataMonitor_AI助手技术说明.md](AdvanceDataMonitor_AI助手技术说明.md) | AI 助手实现细节与技术交接 |
| [AdvanceDataMonitor_AI助手类引用速查.md](AdvanceDataMonitor_AI助手类引用速查.md) | AI 助手各功能对应的必改文件清单 |
| [AdvanceDataMonitor_本地语音转写.md](AdvanceDataMonitor_本地语音转写.md) | 本地 OpenAI-compatible STT 服务配置 |
| [AdvanceDataMonitor_高级计划器使用说明.md](AdvanceDataMonitor_高级计划器使用说明.md) | 高级计划器功能使用教程 |
| [AdvanceDataMonitor_高级计划器开发文档.md](AdvanceDataMonitor_高级计划器开发文档.md) | 高级计划器开发细节与数据流 |
| [AdvanceDataMonitor-Developer-Documentation.md](AdvanceDataMonitor-Developer-Documentation.md) | 项目结构、API、扩展点英文文档 |
| [AdvanceDataMonitor-Player-Guide.md](AdvanceDataMonitor-Player-Guide.md) | 功能使用与配置英文指南 |
| [AdvanceDataMonitor_项目说明.md](AdvanceDataMonitor_项目说明.md) | GTNH 构建模板与 Gradle 工程说明 |
| [AdvanceDataMonitor_FAQ.md](AdvanceDataMonitor_FAQ.md) | 构建环境常见问题 |
| [AdvanceDataMonitor_构建迁移指南.md](AdvanceDataMonitor_构建迁移指南.md) | 将旧模组迁移到 GTNH 构建系统 |
| [AdvanceDataMonitor_模组移植指南.md](AdvanceDataMonitor_模组移植指南.md) | 模组版本/代码移植流程 |
| [AdvanceDataMonitor概览.md](AdvanceDataMonitor概览.md) | 模组功能总览 |

---

## 🔧 技术栈

- **语言**: Java 8 (Jabel 提供 lambda 语法糖)
- **构建**: Gradle Kotlin DSL + GTNH Convention Plugin
- **AI 框架**: DeepSeek API / OpenAI-compatible 流式接口
- **语音**: Vosk 离线识别 + HTTP STT 在线识别
- **渲染**: OpenGL 1.1 (GL11) + Tessellator 单例模式
- **架构**: @SidedProxy 客户端/服务端分离

---

## 📄 许可证

MIT License — 详见 [LICENSE](LICENSE)
