# TeXTech UI 框架（Flex 组件树）

TeXTech 自研 GUI 框架位于 `com.imgood.textech.gui.framework`。框架以 Meowa 生成的像素科幻组件母表为视觉来源，在运行时 **9-slice 主题图集**之上提供类 HTML 的 **组件树 + Flexbox 布局 + `UiStyle` 样式**，供除次元口袋外的配置屏与容器屏统一使用。

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

物质球解压器等已用的 `UiPanel.draw` / `UiButton(x,y,w,h)` **保留**，新代码优先 Widget 树。命令式组件也必须传入 `UiThemes.ADM`：

- `UiPanel.draw` 绘制主外壳，`drawSection` 绘制列表、预览、tooltip 等内嵌区域，`drawDivider` 绘制分隔线；
- `UiButton` / `UiButtonWidget` 使用 normal / hover / pressed / disabled 四态；
- `UiSlot.drawTheme`、`UiScrollPanel` 和手工列表滚动条分别使用主题槽位、轨道和滑块；
- `ADM_GuiScreen`、`ADM_GuiButton`、`ADM_GuiTextField` 会把 `AdmGuiTextures` 的旧背景、按钮和输入框常量自动桥接到当前主题，避免旧屏继续拉伸历史 PNG；
- `GuiResponsiveLayout.fitCentered` 把旧屏的首选面板尺寸约束进 scaled viewport；调用方必须让背景、控件、聊天区/滚动区和换行宽度共用返回的运行时 bounds。`ADM_GuiButton` 会在极窄按钮中裁剪标签，避免本地化文本溢出；
- AE2 `GuiImgButton` 模式按钮和升级卡列保留原生图标与状态语义，不套 ADM 装饰外壳。

## 图集与主题

路径：`assets/textech/textures/gui/adm_ui_atlas.png`（256×256）

| 区域 | UV 起点 | 尺寸 | border | 用途 |
|------|---------|------|--------|------|
| mainPanel | (0, 0) | 96×108 | 10 | 主面板 9-slice |
| sectionPanel | (100, 0) | 88×56 | 8 | 内嵌区域 9-slice |
| buttonNormal | (100, 60) | 48×20 | 8 | 按钮普通态 |
| buttonHover | (100, 82) | 48×20 | 8 | hover |
| buttonPressed | (100, 104) | 48×20 | 8 | 按下 |
| buttonDisabled | (100, 126) | 48×20 | 8 | disabled |
| textFieldNormal | (152, 60) | 48×20 | 6 | 输入框 |
| textFieldFocused | (204, 60) | 48×20 | 6 | 聚焦 |
| slot | (152, 84) | 18×18 | 3 | 物品槽位 |
| scrollTrack | (174, 84) | 10×42 | 3 | 滚动条轨道 |
| scrollThumb | (188, 84) | 10×20 | 3 | 滚动滑块 |
| divider | (204, 84) | 48×4 | 1 | 分隔线 |
| toggleOff / On / Disabled | (152 / 184 / 216, 108) | 28×14 | 4 | 三态开关 |
| checkOff / On / Disabled | (152 / 170 / 188, 126) | 14×14 | 3 | 三态复选框 |
| icons | (0, 160) | 16×16 网格 | — | 主题小图标 |

资产溯源：

- Meowa 母表：`.workspace/meowa/in-game-gui-unified-r1/A_cohesive_pixel-art_game_UI_component_sheet_for_TeXTech_Advance_Data_Monitor_in_Minecraft_GTNH_black_transluc_222abf39/ui_output.png`（1024×1024，SHA-256 `f4a3c419484d2ad955bd53774d9d5856fd2948d2a73d6917778735ef775405cb`）；
- 运行时图集：`src/main/resources/assets/textech/textures/gui/adm_ui_atlas.png`（SHA-256 `0038210d8ba910b9e93a67b3ed628ecac9d7057962148d8aa510e90886e4f029`）；
- UV 权威数据：`.workspace/adm_ui_atlas_meowa.layout.json`；构建脚本：`.workspace/build_adm_ui_atlas.py`。

| 主题 | 类 | 状态 |
|------|-----|------|
| ADM | `AdmUiTheme` / `UiThemes.ADM` | 默认 |
| Pocket | `PocketUiTheme` / `UiThemes.POCKET` | stub；**不接入本迁移批次** |

## 组件状态

| 组件 | 命令式 | Widget 树 | 游戏内调试 |
|------|--------|-----------|------------|
| `GuiBlitUtil` / `NineSliceRegion` | 是 | — | 是 |
| `UiPanel` | 是（主面板 / 次级面板 / 分隔线） | 背景经 `UiStyle` | 是 |
| `UiText` / `UiLabel` | 是 / 树 | 是 | 是 |
| `UiIcon` | 是 | — | 是 |
| `UiButton` / `UiButtonWidget` | 是（四态） | 是 | 是 |
| `UiToggleButton` | 是 | — | 调试 GUI |
| `UiSlot` | 是 | — | 解压器、储存链接器、调试 GUI |
| `UiTextField` | 是 | 可挂树包装 | 调试 GUI |
| `UiFlex` / `UiFlexLayoutEngine` | — | 是 | 调试 Flex 区 |
| `UiScrollPanel` | — | 是 | 调试 Flex 区；计划器/扫描器复用同主题滚动区域 |
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

### 当前覆盖

所有非口袋 GUI 均经统一宿主或显式主题组件覆盖：

- `ADM_GuiScreen`、`AdmItemConfigScreen`、`AbstractMonitorSubGui`、`ADM_UiContainer` 提供统一主外壳与旧纹理桥接；
- 监视器主页/子页、AI Chat、Planner、Link Scanner、手册、NBT Viewer、截图画廊和调试屏使用主题次级面板、控件或滚动区域；
- 储存链接器与物质球解压器的容器槽位使用 `UiSlot.drawTheme`；
- 保留的程序色块仅表示选中、hover、交替行、HUD 拖动等瞬时状态，不作为 GUI 装饰外壳；
- 次元口袋全链路永久排除本批次。

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

- 左侧：命令式组件样例（含 Meowa 主题槽位）
- 右侧：完整图集 UV 对照（含 section、pressed、slot、scroll、divider）
- 右下：紧凑 Flex 表单 + 滚动演示

展示页固定在 408×228 的安全尺寸内。`GuiLowResolutionLayoutTest` 锁定 427×240 scaled viewport 下的大面板边界、左右列分隔、atlas/Flex 垂直边界；真实客户端 QA 使用原版 framebuffer 截图检查黑屏与裁切。

配置：`Config.debugUiFrameworkBlock`。

## 参考实现

- 解压器（命令式）：`MatterBallDecompressorGuiLayout`、`MatterBallDecompressorGuiRenderer`、`GuiMatterBallDecompressor`
- Flex 调试：`GuiUiFrameworkDebug`、`UiFrameworkDebugLayout`
- 布局单测：`src/test/java/com/imgood/textech/gui/framework/layout/UiFlexLayoutEngineTest.java`
- GUI 覆盖守卫：`src/test/java/com/imgood/textech/gui/GuiThemeCoverageTest.java` 动态扫描 `gui/guiscreen/`，要求全部非口袋 GUI 使用 ADM 宿主或显式 ADM 面板，并禁止新增原版按钮与独立旧 GUI PNG；`GuiDimensionalPocketConfig`、`GuiPocketStorage` 是固定排除项。
- 图集合同：`src/test/java/com/imgood/textech/gui/framework/AdmUiAtlasContractTest.java` 锁定批准的 Meowa atlas SHA-256、256x256 RGBA、全部主题 UV 与边界、14 个图标格，以及按钮、输入框、滚动、toggle/check 的状态差异。
