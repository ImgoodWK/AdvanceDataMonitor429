# AdvanceDataMonitor

**Minecraft 1.7.10 / GTNH (GregTech: New Horizons) 社区模组** — 高级数据监视器

> Mod ID: `advancedatamonitor` · 平台: Forge 10.13.4.1614 · MCP stable_12

---

## 功能概览

- **AE2 数据监视器** — 折线图/柱状图、合成 CPU 状态、存储物品列表
- **AE2 链接器方块** — 网络 / 合成 / 存储三类 Link
- **AI 与语音助手** — 自然语言查询 AE2、合成下单、取出物品、计划管理（V 键语音 / O 键聊天）
- **挂索系统** — 沿索道路网平滑滑行，双手自由操作
- **数据编织元件** — ME 驱动器内按时间编织标记物品/流体/源质
- **高级计划器** — 游戏内待办清单与 HUD

---

## 快速开始

```powershell
.\gradlew.bat build          # 编译打包
.\gradlew.bat runClient      # 启动开发客户端
```

主要依赖通过 GTNH Maven 自动解析；本地 dev jar 放在 `libs/`（Chisel、Galacticraft、IC2NuclearControl 等）。

---

## 文档

**完整文档请见 [docs/README.md](docs/README.md)**

| 受众 | 入口 |
|------|------|
| 玩家 | [docs/zh/player/用户手册.md](docs/zh/player/用户手册.md) |
| 开发者 | [docs/zh/developer/技术文档.md](docs/zh/developer/技术文档.md) |
| AI 助手开发 | [docs/zh/ai-assistant/开发指南.md](docs/zh/ai-assistant/开发指南.md) |
| 构建 / Gradle | [docs/zh/developer/Gradle工作流.md](docs/zh/developer/Gradle工作流.md) |
| English docs | [docs/en/README.md](docs/en/README.md) |

---

## 技术栈

- Java 8（Jabel 语法糖）· Gradle Kotlin DSL + GTNH Convention Plugin
- DeepSeek / OpenAI-compatible API · Vosk + HTTP STT
- OpenGL 1.1 · @SidedProxy 客户端/服务端分离

---

## 许可证

MIT License — 详见 [LICENSE](LICENSE)
