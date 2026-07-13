---
name: textech-gui-add
description: >-
  Adds a TeXTech GUI with the correct base class (AdmItemConfigScreen,
  AbstractMonitorSubGui, or ADM_UiContainer), AdmGuiTextures, LoaderGui
  registration, and lang keys. Use when the user @-mentions this skill or asks
  to add a GuiScreen, container GUI, or monitor sub-page.
disable-model-invocation: true
---

# TeXTech GUI Add

按需 `@textech-gui-add`。细则：`.cursor/rules/gui-guidelines.mdc`、`docs/zh/developer/ui-framework.md`。

## 基类决策

| 场景 | 基类 |
|------|------|
| 手持物品 / 节点小型配置 | `AdmItemConfigScreen` |
| 监视器 per-binding 子页 | `AbstractMonitorSubGui` |
| 新有槽位容器 | **必须** `ADM_UiContainer` + `UiThemes.ADM` / `POCKET` |
| 无槽位全屏 | `ADM_GuiScreen`（少用；优先上面三类） |

## 清单

```
GUI Add:
- [ ] 1. 选定基类，避免复制 GuiSub* 样板
- [ ] 2. 纹理用 AdmGuiTextures，勿散落 ResourceLocation
- [ ] 3. LoaderGui + GuiHandler ID（若新屏）
- [ ] 4. 容器类放 gui/container/；打开约定：服务端 openGui
- [ ] 5. 双语 lang；必要时更新 project-structure*.mdc
```

## 注意

- 口袋类 GUI 必须由**服务端** `player.openGui` 触发（见 `project-structure.mdc` 次元口袋约定）
- 新容器走 `gui/framework/`（`UiPanel` / `UiButton` / `UiSlot`…）
- 需要网络同步时接 `@textech-network-packet`

## 完成后

`@textech-doc-sync-pr` 或至少更新 structure mdc + lang。
