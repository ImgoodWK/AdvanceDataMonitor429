# TeXTech UI 框架（Flex 组件树）

TeXTech 自研 GUI 框架位于 `com.imgood.textech.gui.framework`。在既有 **9-slice 主题 / 图集** 之上，提供类 HTML 的 **组件树 + Flexbox 布局 + `UiStyle` 样式**，供除次元口袋外的配置屏与容器屏统一使用。

权威规范路由：`.cursor/rules/gui-guidelines.mdc` §2b / §1d。  
参考实现（只借鉴、不硬依赖）：[Qz-UILib](https://github.com/QuanhuZeYu/Qz-UILib) Flex、[ModularUI2](https://wiki.gtnewhorizons.com/wiki/ModularUI) Widget/Flow。

## 依赖边界（已定）

| 依赖 | 策略 |
|------|------|
| Qz-UILib | **不硬依赖、不 shade**；布局语义对齐其 FlexLayouter |
| ModularUI2 | 保持现有 `devOnlyNonPublishable`；GUI **不迁** `IGuiHolder`；文档提供对照表；运行时与 MUI/NEI **共存** |
| 同步 | 首期客户端布局/绘制；C↔S 仍走现有网络包 |

## 包结构

```
gui/framework/
  GuiBlitUtil, NineSliceRegion, UiTheme, AdmUiTheme, PocketUiTheme, UiThemes
  UiPanel, UiText, UiIcon, UiButton, UiToggleButton, UiSlot, UiTextField, UiTooltip  — 命令式绘制（兼容旧 GUI）
  UiLayoutContext
  layout/     — 纯 Java Flex 引擎（可单测，无 @SideOnly）
  style/      — UiStyle / UiBackground
  widget/     — UiWidget 树、UiFlex、UiLabel、UiScrollPanel、挂树按钮等
  host/       — AdmUiScreen（配置屏宿主）
gui/custom/
  ADM_UiContainer — 容器宿主（可挂同一棵 Widget 树）
```

底层 blit 仍委托 `GuiBlitUtil`（自口袋渲染抽取）；**口袋 GUI / Overlay / Mixin 行为不纳入本框架迁移**。

## 架构

```
AdmUiScreen / ADM_UiContainer
        │
        ▼
   UiWidget 根节点
        │
   UiFlex (row/column)
        ├── UiLabel / UiButtonWidget / UiTextFieldWidget …
        └── UiScrollPanel
                └── …
        │
   UiFlexLayoutEngine.layout(root, constraints)
        │
   GuiBlitUtil + UiTheme 图集绘制
```

## API 草案（目标 DSL）

```java
root.child(UiFlex.column()
    .style(new UiStyle().padding(8).gap(4).backgroundNineSlice(theme.mainPanel()))
    .child(UiLabel.title(I18n.format("adm.title.xxx")))
    .child(UiFlex.row()
        .child(UiLabel.of(I18n.format("adm.label.name")).grow(0f).preferredWidth(80))
        .child(fieldWidget.grow(1f)))
    .child(UiFlex.row().mainAlign(UiMainAlign.END).gap(4)
        .child(UiButtonWidget.save(this::onSave))
        .child(UiButtonWidget.cancel(this::onCancel))));
```

宿主在 `initGui` 建树 → `UiFlexLayoutEngine.layout` → `drawScreen` / 背景层只调用 `root.render` / 输入走 `root.hitTest`。

## Flex 语义

| 能力 | TeXTech | Qz-UILib | MUI2 | CSS |
|------|---------|----------|------|-----|
| 方向 | `UiFlexDirection.ROW/COLUMN` | `FlexDirection` | `Flow.row()` / column | `flex-direction` |
| 主轴对齐 | `UiMainAlign` START/CENTER/END | `MainAxisAlign` | 锚点 + Flow | `justify-content` |
| 交叉轴 | `UiCrossAlign` + STRETCH | `CrossAxisAlign` | — | `align-items` |
| 自身覆盖 | `UiAlignSelf` | `AlignSelf` | — | `align-self` |
| 间距 | `UiStyle.gap` / padding / margin | gap / padding / margin | margin 锚点 | gap / padding / margin |
| 伸缩 | `grow` / `shrink` | grow 分配 | `coverChildren` 等 | `flex-grow/shrink` |
| 首选尺寸 | `preferredWidth/Height`（>0 时 STRETCH 豁免） | preferred + 豁免 | size 声明 | width/height |
| 绝对逃生 | `setAbsolute(x,y)` | AnchorRect 等 | left/right/top/bottom | `position:absolute` |

布局步骤（引擎）：测量子 → 主轴 grow 分配 → 按 gap/对齐定位 → 交叉轴 STRETCH（有 preferred 则豁免）。

## 样式模型 `UiStyle`

| 字段 | 含义 |
|------|------|
| `padding` / `margin` | `UiInsets`（上右下左） |
| `gap` | Flex 子项间距（仅容器） |
| `background` | `UiBackground`：NONE / SOLID / NINE_SLICE / FULL_TEXTURE |
| `textColor` / `textHoverColor` | 覆盖主题色；默认走 `UiTheme` |
| `visible` | 是否参与布局与绘制 |

链式：`new UiStyle().padding(8).gap(4).margin(2, 0, 2, 0)`。

## 坐标系

沿用 `UiLayoutContext`：

| 层 | 坐标 | 用途 |
|----|------|------|
| 背景层 | 屏幕绝对 = `guiLeft + local` | 9-slice 面板、槽位格 |
| 前景 / Widget 树 | 相对父节点；根节点原点 = `(guiLeft, guiTop)` | 标签、按钮、输入框 |

容器 GUI：`drawGuiContainerBackgroundLayer` 可先 `drawMainPanel`，再渲染挂在根上的树；或整树自绘背景。

## 宿主

### 配置屏 — `AdmUiScreen`

- 继承 `GuiScreen`（可与旧 `ADM_GuiScreen` 并存；新屏优先本类）
- 抽象 `buildUi()`；管理 `guiLeft/Top/Width/Height`、主题、根节点 layout/render/input
- 小型配置仍可暂用 `AdmItemConfigScreen`，触达时再迁

### 容器屏 — `ADM_UiContainer`

- 已有 `theme()` / `drawMainPanel` / `layoutContext()`
- 增加可选 Widget 树：`setUiRoot` / `layoutUi` / `renderUi` / 鼠标键盘转发
- 新容器 GUI 必须继承本类 + `UiThemes.ADM`

## 命令式 API（兼容层）

物质球解压器等已用的 `UiPanel.draw` / `UiButton(x,y,w,h)` **保留**，新代码优先 Widget 树。调试状态见下表。

## 图集与主题

路径：`assets/textech/textures/gui/adm_ui_atlas.png`（256×256）

| 区域 | UV 起点 | 尺寸 | border | 用途 |
|------|---------|------|--------|------|
| mainPanel | (0, 0) | 64×64 | 16 | 主面板 9-slice |
| buttonNormal | (0, 64) | 48×20 | 8 | 按钮 3-slice |
| buttonHover | (0, 84) | 48×20 | 8 | hover |
| buttonDisabled | (0, 104) | 48×20 | 8 | disabled |
| textFieldNormal | (64, 64) | 80×20 | 6 | 输入框 |
| textFieldFocused | (64, 84) | 80×20 | 6 | 聚焦 |
| icons | (64, 0) | 16×16 网格 | — | 主题小图标 |

| 主题 | 类 | 状态 |
|------|-----|------|
| ADM | `AdmUiTheme` / `UiThemes.ADM` | 默认 |
| Pocket | `PocketUiTheme` / `UiThemes.POCKET` | stub；**不接入本迁移批次** |

## 组件状态

| 组件 | 命令式 | Widget 树 | 游戏内调试 |
|------|--------|-----------|------------|
| `GuiBlitUtil` / `NineSliceRegion` | 是 | — | 是 |
| `UiPanel` | 是 | 背景经 `UiStyle` | 是 |
| `UiText` / `UiLabel` | 是 / 树 | 是 | 是 |
| `UiIcon` | 是 | — | 是 |
| `UiButton` / `UiButtonWidget` | 是 | 是 | 是 |
| `UiToggleButton` | 是 | — | 调试 GUI |
| `UiSlot` | 是 | — | 调试 GUI |
| `UiTextField` | 是 | 可挂树包装 | 调试 GUI |
| `UiFlex` / `UiFlexLayoutEngine` | — | 是 | 调试 Flex 区 |
| `UiScrollPanel` | — | 是 | 调试 Flex 区 |
| `AdmUiScreen` | — | 是 | — |
| `ADM_UiContainer` | 是 | 可选挂树 | 是 |
| `UiTooltip` | stub | — | 否 |

## 排除清单（次元口袋）

勿迁入本框架批次：

- `GuiDimensionalPocketConfig`、`GuiPocketStorage`
- `ContainerDimensionalPocket`、`ContainerPocketStorage`
- `client/GuiPocketOverlay`、`PocketOverlayHandler`、`PocketPortalGuiRenderer` 行为
- 口袋相关 Mixin / NEI 排除区逻辑
- `UiThemes.POCKET` 接入（可继续 stub）

## 非目标（首期）

- JSON 主题热加载 / 资源包主题
- 完整 C↔S Sync Handler（MUI 式）
- Skija / Qz 高清字体
- 完整 CSS Grid
- 动画引擎
- `MuiBridge` 嵌入（远期可选）

## 移植指南

### 优先级

| 优先级 | 界面 | 说明 |
|--------|------|------|
| P0 | `GuiUiFrameworkDebug` Flex 演示区 | 验证引擎 |
| P1 | `AdmItemConfigScreen` 系 | 小窗、保存/取消 |
| P2 | `AbstractMonitorSubGui` 系 | 双列 → Row/Column 模板 |
| P3 | 监视器主页 / AI 聊天 / 计划器 | 最复杂 |
| 排除 | 口袋全链路 | 永久排除本批次 |

### 步骤检查清单

1. 改继承：配置 → `AdmUiScreen`；容器 → `ADM_UiContainer`（若尚未）
2. `buildUi()` / `initGui` 建树，删除手工 `x/y` 堆叠（绝对定位仅逃生）
3. `drawScreen` 只调根 `render`；背景可用 `UiStyle` 或暂留 `AdmGuiTextures` 整图
4. 输入：`mouseClicked` / `keyTyped` / 滚轮转发根节点
5. **不改** lang key、Config、网络包语义
6. 行为回归：保存/取消、校验、同步与旧版一致

### 双轨共存

- 旧 `ADM_GuiButton` / `ADM_GuiTextField` / 绝对坐标样板短期保留
- **新界面禁止**再扩纯绝对坐标布局；触达旧界面时再迁

## 调试方块

`[debug] uiFrameworkBlock=true` 并重启 → 创造栏 **UI 框架调试方块** → `GuiUiFrameworkDebug`：

- 左侧：命令式组件样例
- 中/下：Flex 表单 + 滚动演示
- 右侧：图集 UV 对照

配置：`Config.debugUiFrameworkBlock`。

## 参考实现

- 解压器（命令式）：`MatterBallDecompressorGuiLayout`、`GuiMatterBallDecompressor`
- Flex 调试：`GuiUiFrameworkDebug`、`UiFrameworkDebugLayout`
- 布局单测：`src/test/java/com/imgood/textech/gui/framework/layout/UiFlexLayoutEngineTest.java`

生成图集脚本（开发用）：`.workspace/generate_adm_ui_atlas.py`
