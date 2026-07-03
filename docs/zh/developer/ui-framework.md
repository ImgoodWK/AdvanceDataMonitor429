# UI 框架（容器 GUI）

TeXTech 通用容器 GUI 框架位于 `com.imgood.textech.gui.framework`，通过 **9-slice / 3-slice** 从单一图集拼装不同尺寸的界面，并支持多主题。

完整 API 与调试状态见下文；配置类界面仍使用 [`ADM_GuiScreen`](../.cursor/rules/gui-guidelines.mdc) 整图拉伸方案。

## 架构

```
gui/framework/
  GuiBlitUtil          — 底层 blit、9-slice、横向 3-slice
  NineSliceRegion      — 图集内区域描述（UV、border）
  UiTheme / AdmUiTheme / PocketUiTheme / UiThemes
  UiPanel, UiText, UiIcon, UiButton, UiLayoutContext
  UiSlot, UiTextField, UiToggleButton, UiTooltip（占位）
gui/custom/
  ADM_UiContainer      — 新容器 GUI 基类（`GuiMatterBallDecompressor` 已采用）
```

底层 blit 自 [`PocketPortalGuiRenderer`](../../src/main/java/com/imgood/textech/client/PocketPortalGuiRenderer.java) 抽取；口袋 GUI 行为未改，仅委托 `GuiBlitUtil`。

## 图集：`adm_ui_atlas.png`

路径：`assets/textech/textures/gui/adm_ui_atlas.png`（256×256）

| 区域 | UV 起点 | 尺寸 | border | 用途 |
|------|---------|------|--------|------|
| mainPanel | (0, 0) | 64×64 | 16 | 主面板 9-slice |
| buttonNormal | (0, 64) | 48×20 | 8 | 按钮 3-slice |
| buttonHover | (0, 84) | 48×20 | 8 | hover |
| buttonDisabled | (0, 104) | 48×20 | 8 | disabled |
| textFieldNormal | (64, 64) | 80×20 | 6 | 输入框（未调试） |
| textFieldFocused | (64, 84) | 80×20 | 6 | 聚焦（未调试） |
| icons | (64, 0) | 16×16 网格 | — | 主题小图标 |

图集缺失时 `UiPanel` 回退纯色面板（与解压器旧版 `drawSolidPanel` 一致）。

## 主题

| 主题 | 类 | 状态 |
|------|-----|------|
| ADM | `AdmUiTheme` / `UiThemes.ADM` | 默认；物质球解压器使用 |
| Pocket | `PocketUiTheme` / `UiThemes.POCKET` | stub，映射 `pocket_portal_panel.png`，**未接入 GUI** |

## 组件用法（简要）

### 背景 — `UiPanel`

```java
UiPanel.draw(UiThemes.ADM, panelLeft, guiTop, width, height);
UiPanel.drawDivider(panelLeft + 8, splitY, width - 16);
```

### 文字 — `UiText`（容器前景层，GUI 本地坐标）

```java
UiText.drawCenteredTitle(UiThemes.ADM, fontRendererObj, title, centerX, 7);
UiText.drawLabel(UiThemes.ADM, fontRendererObj, I18n.format("container.inventory"), x, y);
```

### 图标 — `UiIcon`

```java
UiIcon.drawThemeIcon(UiThemes.ADM, iconIndex, x, y, destSize);
UiIcon.drawAnchored(theme, index, parentX, parentY, parentW, parentH, Anchor.CENTER, 0, 0);
```

### 按钮 — `UiButton`

横向 3-slice + 可选图标/文字；`hitTest` / `click` 用于非 `GuiButton` 列表场景。

```java
UiButton btn = new UiButton(x, y, w, h).setLabel("...").setOnClick(...);
btn.draw(UiThemes.ADM, fontRendererObj, mouseX, mouseY);
```

### 布局 — `UiLayoutContext`

封装 `guiLeft` / `guiTop` / `theme` / `fontRenderer`，区分背景层绝对坐标与前景层本地坐标。

### 容器基类 — `ADM_UiContainer`（可选）

```java
public class MyGui extends ADM_UiContainer {
    public MyGui(Container c) { super(c, UiThemes.ADM); }
    protected void drawGuiContainerBackgroundLayer(...) {
        drawMainPanel(localX, localY, panelW, panelH);
    }
}
```

## 调试状态表

| 组件 | 已实现 | 游戏内调试 | 验证界面 / 说明 |
|------|--------|------------|-----------------|
| `GuiBlitUtil` / `NineSliceRegion` | 是 | 是（间接） | 口袋 GUI 回归 + 解压器 |
| `UiPanel` / 9-slice 主面板 | 是 | **是** | 物质球解压器，buffer 1×1 / 3×3 / 9×9 动态尺寸 |
| `UiText` | 是 | **是** | 解压器标题、`container.inventory` 标签 |
| `UiIcon` | 是 | **是** | 解压器侧栏 AE 按钮旁状态图标 |
| `UiButton` | 是 | **是** | 解压器标题栏装饰 chip（3-slice，disabled 展示） |
| `UiToggleButton` | 是 | **否** | — |
| `UiSlot` | 是 | **否** | 解压器槽位仍用 `MatterBallDecompressorGuiRenderer.drawSlotCell` |
| `UiTextField` | 是 | **否** | — |
| `UiTooltip` | stub | **否** | 需在 `GuiScreen` 子类内调用 `drawHoveringText` |
| `PocketUiTheme` | stub | **否** | — |
| `ADM_UiContainer` | 是 | **是** | `GuiMatterBallDecompressor` 已继承；新容器 GUI 应优先使用 |
| AE `GuiImgButton` / 升级列 | 沿用 | 否 | 非本框架范围；解压器侧栏与右侧升级列 |

## 参考实现

- **布局**：[`MatterBallDecompressorGuiLayout.java`](../../src/main/java/com/imgood/textech/gui/MatterBallDecompressorGuiLayout.java)
- **背景渲染**：[`MatterBallDecompressorGuiRenderer.java`](../../src/main/java/com/imgood/textech/renders/MatterBallDecompressorGuiRenderer.java)
- **前景 / 按钮**：[`GuiMatterBallDecompressor.java`](../../src/main/java/com/imgood/textech/gui/guiscreen/GuiMatterBallDecompressor.java)

## 与旧组件的关系

| 场景 | 推荐 |
|------|------|
| 配置/设置界面（无 Container） | `AdmItemConfigScreen`（小型）或 `ADM_GuiScreen`（复杂）+ `AdmGuiTextures` |
| 监视器绑定子页 | `AbstractMonitorSubGui` |
| 容器界面（动态尺寸、统一图集） | `ADM_UiContainer` + `UiThemes.ADM` + `UiPanel` / `UiText` / … |
| 口袋传送门风格 | 暂用 `PocketPortalGuiRenderer`；后续可迁 `UiThemes.POCKET` |

生成图集脚本（开发用）：`.workspace/generate_adm_ui_atlas.py`

## 调试方块与展示 GUI

启用配置 `[debug] uiFrameworkBlock=true` 并**重启游戏**后，创造模式物品栏会出现 **UI 框架调试方块**（`BlockUiFrameworkDebug`）。右键打开 `GuiUiFrameworkDebug`，在同一界面内展示：

| 区域 | 内容 |
|------|------|
| 整页背景 | `UiPanel` 9-slice（`mainPanel` 区域） |
| 左侧 | 各组件 live 样例 + 类名 + 简短说明 |
| 右侧 | `adm_ui_atlas.png` 各区域 **UV / 尺寸 / border** 对照表 |
| 底部 | 修改材质提示（PNG 路径 + `AdmUiTheme` 常量需同步） |

展示组件：`UiText`、`UiIcon`（0–3）、`UiButton`、`UiButton(disabled)`、`UiToggleButton`、`UiSlot`（vanilla + procedural）、`UiTextField`。

配置项：`Config.debugUiFrameworkBlock` / `[debug] uiFrameworkBlock`。
