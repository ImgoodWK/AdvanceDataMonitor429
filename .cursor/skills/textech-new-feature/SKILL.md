---
name: textech-new-feature
description: >-
  Guides adding a new TeXTech Block, Item, GUI, or subsystem with correct
  package placement, Loader registration, bilingual lang, and structure docs.
  Use when the user @-mentions this skill or explicitly asks to add a new
  feature, block, item, GUI screen, or subsystem.
disable-model-invocation: true
---

# TeXTech New Feature

按需 `@textech-new-feature`。决策清单：`docs/zh/developer/new-feature-checklist.md`；包约定：`project-structure.mdc`。

## 清单

```
New Feature:
- [ ] 1. 架构复用决策（能否扩展现有模式）
- [ ] 2. 正确包位置 + Loader 注册
- [ ] 3. 双语 lang（en_US + zh_CN）
- [ ] 4. GUI/网络按规范接入
- [ ] 5. 更新 project-structure*.mdc + 相关 docs/manual
```

## 决策要点

1. **先复用**：现有 Item/Block/TE 模式；AE 只经 `compat/ae/AeCompat`；编织元件 / 口袋卡用既有抽象基类
2. **包位置**：标准分层（`items/`、`blocks/`、`gui/`…）或系统级包（`assistant/`、`webae/`…）；禁止为单物品新建根级平行包
3. **注册**：仅在对应 `Loader*` 中注册（Block/Item/TE/Handler/Gui/Render/Network）
4. **GUI**：`AdmItemConfigScreen` / `AbstractMonitorSubGui` / `ADM_UiContainer`；纹理用 `AdmGuiTextures`
5. **网络**：固定 ID；`PacketHandlers.runOnServer`；详见 `@textech-network-packet`
6. **文档**：lang 成对；玩家功能 → `manual/` + `docs/`；新 Java → `project-structure.mdc` + `project-structure-details.mdc`

## 体量预期

| 类型 | 典型新增 |
|------|----------|
| 简单物品 | 1 Item + lang |
| 编织元件 | 1 Item + Config + lang + 手册 |
| 手持配置窗 | 继承 `AdmItemConfigScreen` + 1 Packet |
| 新容器 GUI | Container + Gui + 0~1 Packet |

预估一次性 +5 个 700+ 行 GUI 类 → 先抽基类。

## 完成后

走 `@textech-doc-sync-pr` 做文档收尾与 doc-check。
