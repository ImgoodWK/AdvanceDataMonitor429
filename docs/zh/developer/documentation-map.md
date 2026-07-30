# TeXTech 文档维护地图

> 受众：贡献者 / Codex / Cursor Agent · 最后同步：2026-07  
> English: [documentation-map.md](../../en/developer/documentation-map.md)

当你修改某类功能时，按本表更新对应文档与规则，避免四套体系（`AGENTS.md`、`docs/`、游戏内 `manual/`、`.cursor/rules/`）脱节。

---

## 文档层级

| 层级 | 路径 | 角色 |
|------|------|------|
| L0 机器可读 | `AGENTS.md`、`ConfigDescriptions.java`、`LoaderNetwork.java`、`.cursor/rules/*.mdc` | Codex / Cursor / CI 单一事实源 |
| L1 开发者规格 | `docs/zh/developer/`、`docs/zh/webae/开发者手册.md`、`docs/zh/ai-assistant/` | 贡献者详细设计 |
| L2 玩家/服管 | `docs/zh/player/`、`docs/zh/webae/用户手册.md`、`assets/textech/manual/` | 教程与操作说明 |
| L3 愿景/设计 | `docs/zh/design/` | **非实现规格**，不参与 CI 校验 |

## Agent 读文档策略（减 token）

- **默认单语**：中文任务只读 `docs/zh/`；不要同时全量读 `docs/en/`（成对发版同步时再改英文）。
- **禁止默认读 L3 / 归档**：除非用户点名愿景、品牌设计或归档移植，禁止 Read `docs/*/design/`、`docs/*/archive/`。
- **L0 长附录按需**：`project-structure-details.mdc`、完整 AI 架构（`docs/*/ai-assistant/`）不自动灌入；需要逐文件或架构细节时再 Read。
- AI 架构权威：`docs/zh/ai-assistant/开发指南.md`；`.cursor/rules/ai-assistant.mdc` 仅硬约束摘要。

---

## 按功能域：改代码应更新哪些文件

| 功能域 | 必查源码 | 开发者文档 | 玩家文档 | 规则 / 其他 |
|--------|----------|------------|----------|-------------|
| 新增 Java 类/包 | `loader/` 注册 | [技术文档](技术文档.md) · [documentation-map](documentation-map.md) | — | `project-structure.mdc` · `project-structure-details.mdc` |
| Config 配置项 | `Config.java` · `Config*Loader.java` | [技术文档 §11](../player/用户手册.md#11-配置文件详解) · WebAE [§4](../webae/开发者手册.md#4-配置) | [用户手册 §11](../player/用户手册.md#11-配置文件详解) · `manual/config_reference.json` | `ConfigDescriptions.java` · lang |
| 网络包 | `LoaderNetwork.java` | [技术文档 §7](技术文档.md#7-网络包) | — | **`network-packets.mdc`** |
| AI 助手 | `assistant/` | [AI 开发指南](../ai-assistant/开发指南.md) | [用户手册 §8](../player/用户手册.md#8-ai-对话与助手) | `ai-assistant.mdc` · `assistant-features.json` |
| 挂索 | `handler/Grapple*` | [挂索设计](../subsystems/挂索节点系统设计.md) | [用户手册 §3.7](../player/用户手册.md#37-挂索节点grapple-anchor) | — |
| WebAE 控制台 | `webae/` · `webae-frontend/` | [WebAE 开发者手册](../webae/开发者手册.md) | [WebAE 用户手册](../webae/用户手册.md) · `manual/web_console.json` | `webae-frontend.mdc` |
| 独立卡牌服务联动 | `CommandCardBattle.java` · `ConfigCardBattleLoader.java` | [联动概览](../cardbattle/README.md) · [奖励桥](../cardbattle/rewards-bridge.md) | `manual/config_reference.json` | 卡牌游戏本体位于独立仓库；ADM 只保留私密 Bridge 适配器 |
| 世界地图 | `webae/worldmap/` | WebAE [§11.26](../webae/开发者手册.md#1126-世界地图视图phase-ab--ae-透视层) · §4 worldMap* | WebAE 用户手册 · `topology_text` lang | `project-structure-details.mdc` |
| 渲染/TESR | `renders/` | [技术文档 §11](技术文档.md#11-渲染系统) | — | `project-structure-details.mdc` renders 章 |
| GUI / UI 框架 | `gui/framework/` · `gui/custom/` | [ui-framework.md](ui-framework.md) | — | `gui-guidelines.mdc` |
| lang 键 | `zh_CN.lang` + `en_US.lang` | — | 用户手册名称一致 | `manual/` JSON titleKey/textKey |
| 调试开关 | `config/Config*Loader.java` | [技术文档 §16](技术文档.md#161-调试开关) | — | `gtnh-mod-context.mdc` |

---

## 交叉引用策略（去重）

| 内容 | 权威文档 | 其他位置 |
|------|----------|----------|
| 网络包 ID 表 | `network-packets.mdc` | 技术文档 §7 仅摘要 + 链接 |
| AI 架构细节 | `ai-assistant/开发指南.md` | 技术文档 §8 概览；`ai-assistant.mdc` 仅硬约束 |
| 包逐文件清单 | `project-structure-details.mdc`（按需） | `project-structure.mdc` 短索引 |
| WebAE REST API | `webae/开发者手册.md` §5 | 用户手册仅操作步骤 |
| worldMap* 配置 | `ConfigDescriptions` + WebAE §4 | 用户手册/WebAE 用户手册摘要 |
| Implementation status | WebAE dev guide §11 subsystem index | Remove stale outdated phase wording |

---

## PR 前建议

```bash
python tools/doc-check/doc-consistency-check.py
```

见 `.cursor/rules/docs-sync.mdc` 与脚本输出说明。

---

## 相关索引

- [docs/zh/README.md](../README.md) — 中文文档树  
- [docs/README.md](../../README.md) — 双语总索引  
- [AGENTS.md](../../../AGENTS.md) — Codex 项目入口
- [docs-sync.mdc](../../../.cursor/rules/docs-sync.mdc) — Codex / Cursor 共享同步规则  
