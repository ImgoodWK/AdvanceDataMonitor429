# TeXTech UI 框架（Flex 组件树）

TeXTech 自研 GUI 框架位于 `com.imgood.textech.gui.framework`。框架以批准的 Meowa 像素科幻素材为视觉来源，在运行时 **稀疏主题图集**之上提供类 HTML 的 **组件树 + Flexbox 布局 + `UiStyle` 样式**。ADM 主路径只使用四个独立角、双层半透明 cover 背景、完整比例按钮和线式输入框；四段旧边饰已移除，次元口袋全链路始终不受此契约影响。

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
  GuiBlitUtil, AtlasRegion, SparseFrameRegion, FixedAspectButtonFamily, UnderlineFieldRegion, UiFeedbackArea
  NineSliceRegion, UiTheme, AdmUiTheme, PocketUiTheme, UiThemes                 — 旧主题兼容接口仍保留
  UiPanel, UiText, UiIcon, UiButton, UiToggleButton, UiSlot, UiTextField, UiTooltip — 命令式绘制（兼容旧 GUI）
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
    .style(new UiStyle().padding(8).gap(4))
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
| `background` | `UiBackground`：NONE / SOLID / NINE_SLICE / FULL_TEXTURE；`NINE_SLICE` 仅保留给旧主题兼容，ADM 主框由宿主先用 `UiPanel.draw` 绘制 |
| `textColor` / `textHoverColor` | 覆盖主题色；默认走 `UiTheme` |
| `visible` | 是否参与布局与绘制 |

链式：`new UiStyle().padding(8).gap(4).margin(2, 0, 2, 0)`。

## 坐标系

沿用 `UiLayoutContext`：

| 层 | 坐标 | 用途 |
|----|------|------|
| 背景层 | 屏幕绝对 = `guiLeft + local` | 稀疏主/内框、槽位格 |
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

- `UiPanel.draw` 绘制稀疏主外壳，`drawSection` 绘制稀疏内区，`drawTitleOrnament` / `drawFooterOrnament` 各居中绘制一次长饰条；
- `UiButton` / `UiButtonWidget` 使用 normal / hover / pressed / disabled 四态，并把请求尺寸归一到 `20/50/60/80/100/200/240 : 20` 的完整按钮壳；按钮壳只能在调用者请求矩形内等比拟合，绘制、文字、hover 与命中使用同一边界，页面提供的按钮请求矩形也不得互相重叠；
- `UiSlot.drawTheme`、`UiScrollPanel` 和手工列表滚动条分别使用主题槽位、轨道和滑块；
- `ADM_GuiScreen`、`ADM_GuiButton`、`ADM_GuiTextField` 会把 `AdmGuiTextures` 的旧背景、按钮和输入框常量自动桥接到当前主题；ADM 稀疏框只绘制四个独立角，旧边饰 UV 区域保持全透明，背景按 cover 裁切；普通整图等比适配，均不允许非等比拉伸；
- `ADM_GuiTextField` / `UiTextField` 按 normal / focused / invalid / disabled 选择左右竖线和居中裁切的底线；字段内容变化会清除旧 invalid 标记。表单应在正文与底部按钮之间保留 `UiFeedbackArea`，把换行后的校验错误限制在固定区域内；
- `ADM_GuiScreen` 通过 `UiViewportTransform.fitCenteredBounds` 围绕屏幕中心统一缩小逻辑视口，绘制、按钮、输入框、tooltip 与鼠标命中共享同一变换；外部透明区域不再调用原版 `drawDefaultBackground()` 黑幕。`GuiResponsiveLayout.fitCentered` 仅保留给自行计算运行时 bounds 的旧布局；
- AE2 `GuiImgButton` 模式按钮和升级卡列保留原生图标与状态语义，不套 ADM 装饰外壳。

## 图集与主题

路径：`assets/textech/textures/gui/adm_ui_atlas.png`（512×512 RGBA）

| 区域 | UV 起点 / 尺寸 | 用途 |
|------|----------------|------|
| 主稀疏框 | 角：`(0,0)/(24,0)/(0,24)/(24,24)`，各 22×22 | 四角各只绘制一次；旧边饰区域 `(48..261,0..63)` 保持透明；深色 cover 背景 `(264,0)` 64×64，alpha `56/255` |
| 内区稀疏框 | 角：`(0,66)/(16,66)/(0,82)/(16,82)`，各 14×14 | 四角各只绘制一次；旧边饰区域 `(32..153,66..99)` 保持透明；亮色 cover 背景 `(156,66)` 64×64，alpha `72/255` |
| 标题 / 底部饰条 | `(222,66)` 160×12 / `(222,80)` 160×8 | 每个位置居中绘制一次；空间不足时整体等比缩小 |
| 完整按钮壳 | 四行：`v=140/184/228/272`；规格 `20/50/60/80/100/200/240 × 20` | normal / hover / pressed / disabled；不在运行时横向拼接或平铺 |
| 线式输入框 | 底线：`u=0,w=480,h=3,v=320/325/330/335`；左右线：`u=482/487,w=3,h=20,v=320/342/364/386` | normal / focused / invalid / disabled；底线仅从最长源居中裁切 |
| 图标 | normal `v=350`、hover `v=398`，8 列 16×16 | 保存、取消、返回、翻页、增删改、搜索、刷新、设置、导入/导出、发送、绑定、复制、菜单 |
| 既有精确控件 | 槽位 `(300,452)`；滚动 `(320,452)/(330,452)`；开关/复选 `(342..374,452/468)` | 槽位、滚动和布尔控件保留各自语义 |

`AtlasRegion` 表示精确源像素。`SparseFrameRegion` 以同一缩放比例布置四个角，`GuiBlitUtil` 只对背景做等比 cover 裁切。`FixedAspectButtonFamily` 只选择完整比例壳并向请求矩形内拟合；`UnderlineFieldRegion` 只裁切最长底线。`NineSliceRegion`、`TiledFrameRegion` 和 `TiledBarRegion` 仍是旧主题的兼容 API，ADM 主路径不得以它们平铺边框、按钮或输入框。普通 PNG 通过 `GuiBlitUtil.drawFullTexture` 等比缩放并居中留白。

资产溯源：

- 视觉来源固定为批准的框架母表 SHA-256 `a3889faf589b9d1832e4ba8707169b171638eed7a2e1b6dcc2a178195d006ee0`，旧控件源为 `ce3b712acec6d3a377dc924656c61a484d0d508f3fdb8931edfd5ac81435242d`；运行时只交付透明图集；
- 运行时图集：`src/main/resources/assets/textech/textures/gui/adm_ui_atlas.png`（SHA-256 `203244455b02bff5a996bcd3df4f89788545af87e148848b1c3f7667569c4a91`）；
- 可重复构建脚本与布局账本：`tools/gui/build_adm_sparse_atlas.py`、`tools/gui/adm_ui_atlas.layout.json`；按钮/输入图表和 `final_outputs.json` 仅在 `.workspace/adm-gui-batch1-assets/` 保存，不进入发布包。

| 主题 | 类 | 状态 |
|------|-----|------|
| ADM | `AdmUiTheme` / `UiThemes.ADM` | 默认 |
| Pocket | `PocketUiTheme` / `UiThemes.POCKET` | stub；**永久不接入本 ADM 契约** |

## 组件状态

| 组件 | 命令式 | Widget 树 | 游戏内调试 |
|------|--------|-----------|------------|
| `GuiBlitUtil` / `SparseFrameRegion` | 是 | — | 是 |
| `UiPanel` | 是（主面板 / 次级面板 / 分隔线） | 背景经 `UiStyle` | 是 |
| `UiText` / `UiLabel` | 是 / 树 | 是 | 是 |
| `UiIcon` | 是 | — | 是 |
| `UiButton` / `UiButtonWidget` | 是（四态） | 是 | 是 |
| `UiToggleButton` | 是 | — | 调试 GUI |
| `UiSlot` | 是 | — | 解压器、储存链接器、调试 GUI |
| `UiTextField` | 是 | 可挂树包装 | 调试 GUI |
| `FixedAspectButtonFamily` / `UnderlineFieldRegion` / `UiFeedbackArea` | 是 | 可复用 | 调试 GUI / 表单校验 |
| `UiFlex` / `UiFlexLayoutEngine` | — | 是 | 调试 Flex 区 |
| `UiScrollPanel` | — | 是 | 调试 Flex 区；计划器/扫描器复用同主题滚动区域 |
| `AdmUiScreen` | — | 是 | — |
| `ADM_UiContainer` | 是 | 可选挂树 | 是 |
| `UiTooltip` | stub | — | 否 |

## 排除清单（次元口袋）

永久不迁入本 ADM 框架契约：

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

所有非口袋 GUI 均经统一 ADM 宿主覆盖：

- `ADM_GuiScreen`、`AdmItemConfigScreen`、`AbstractMonitorSubGui`、`ADM_UiContainer` 提供统一主外壳与旧纹理桥接；
- 28 个非口袋游戏 GUI 统一使用四角-only 稀疏原语、完整比例按钮与线式字段；不再按页面族分批验收，真实客户端中文截图和按钮矩形审计在全量完成后一次性交付；
- 储存链接器与物质球解压器的容器槽位使用 `UiSlot.drawTheme`；
- 保留的程序色块仅表示选中、hover、交替行、HUD 拖动等瞬时状态，不作为 GUI 装饰外壳；
- 输入字段使用 placeholder 与悬浮说明交代用途、范围、格式、示例和保存失败原因；消息框正文按面板宽度换行并随内容增高；NBT 截断行/折叠节点提供完整值或展开操作提示；
- 次元口袋全链路永久排除本 ADM 契约。

### 步骤检查清单

1. 改继承：配置 → `AdmUiScreen`；容器 → `ADM_UiContainer`（若尚未）
2. `buildUi()` / `initGui` 建树，删除手工 `x/y` 堆叠（绝对定位仅逃生）
3. `ADM_GuiScreen` 子类覆写 `drawAdmScreen`（不可覆写 final `drawScreen`）；先画正文，再调用基类绘制控件，最后画 tooltip
4. 输入：`mouseClicked` / `keyTyped` / 滚轮转发根节点
5. **不改** lang key、Config、网络包语义
6. 行为回归：保存/取消、校验、同步与旧版一致

### 双轨共存

- 旧 `ADM_GuiButton` / `ADM_GuiTextField` / 绝对坐标样板短期保留
- **新界面禁止**再扩纯绝对坐标布局；触达旧界面时再迁

## 调试方块

`[debug] uiFrameworkBlock=true` 并重启 → 创造栏 **UI 框架调试方块** → `GuiUiFrameworkDebug`：

- 左侧：命令式组件样例（含 Meowa 主题槽位）
- 右侧：稀疏框、完整按钮壳、四态输入线和图标网格 UV 对照
- 右下：紧凑 Flex 表单 + 滚动演示

展示页固定在 408×228 的安全尺寸内。`GuiLowResolutionLayoutTest` 锁定 427×240 scaled viewport 下的大面板边界，`UiViewportTransformTest` 覆盖 320×180、427×240、854×480 与超宽视口的等比缩放/坐标逆变换；真实客户端 QA 使用原版 framebuffer 截图检查透明外部、黑屏与裁切。

配置：`Config.debugUiFrameworkBlock`。

## 参考实现

- 解压器（命令式）：`MatterBallDecompressorGuiLayout`、`MatterBallDecompressorGuiRenderer`、`GuiMatterBallDecompressor`
- Flex 调试：`GuiUiFrameworkDebug`、`UiFrameworkDebugLayout`
- 布局单测：`src/test/java/com/imgood/textech/gui/framework/layout/UiFlexLayoutEngineTest.java`
- GUI 覆盖守卫：`src/test/java/com/imgood/textech/gui/GuiThemeCoverageTest.java` 动态扫描 `gui/guiscreen/`，要求全部非口袋 GUI 继承 ADM 宿主，并禁止原生 `GuiScreen`、原版暗幕调用、原版按钮、独立旧 GUI PNG，以及直接调用九宫格、三段或平铺 ADM chrome；`GuiDimensionalPocketConfig`、`GuiPocketStorage` 是固定排除项。
- 图集合同：`src/test/java/com/imgood/textech/gui/framework/AdmUiAtlasContractTest.java` 锁定批准的 512×512 RGBA 图集、两层背景 alpha、八块旧边饰区域全透明、两组各四个独立角、七种按钮比例、四态输入线和 17 个语义图标；`SparseRegionContractTest` 锁定四角单次位置、cover 等比裁切、按钮向请求矩形内拟合且不制造重叠、输入底线裁切和独立反馈区。
