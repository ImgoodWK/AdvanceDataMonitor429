# TeXTech UI Framework (Flex Widget Tree)

The TeXTech GUI framework lives in `com.imgood.textech.gui.framework`. It combines a **Flex widget tree**, `UiStyle`, and a runtime **sparse theme atlas** derived from approved Meowa pixel sci-fi artwork. The ADM primary path uses only four independent corners, two translucent cover backgrounds, complete fixed-aspect buttons, and underline fields. The four former edge ornaments are removed; the Dimensional Pocket family remains outside this contract.

New complex screens should use `AdmUiScreen`; existing `ADM_GuiScreen`, `AdmItemConfigScreen`, and `AbstractMonitorSubGui` screens remain supported through automatic theme bridging. The authoritative coding rules are in [gui-guidelines.mdc](../../../.cursor/rules/gui-guidelines.mdc).

## Dependency boundaries

| Dependency | Policy |
|------------|--------|
| Qz-UILib | No hard dependency and no shading; only the Flex layout semantics are used as a reference |
| ModularUI2 | Keep the existing development-only dependency; do not migrate these GUIs to `IGuiHolder` |
| Synchronization | Layout and drawing remain client-side; C↔S behavior continues to use existing packets |

## Architecture

```
gui/framework/
  GuiBlitUtil, AtlasRegion, SparseFrameRegion, FixedAspectButtonFamily, UnderlineFieldRegion, UiFeedbackArea
  NineSliceRegion      — legacy-theme compatibility descriptor
  UiTheme / AdmUiTheme / PocketUiTheme / UiThemes
  UiPanel, UiText, UiIcon, UiButton, UiLayoutContext
  UiSlot, UiTextField, UiToggleButton, UiTooltip (placeholder)
  layout/             — pure-Java Flex engine
  style/              — UiStyle / UiBackground
  widget/             — UiWidget, UiFlex, UiLabel, UiScrollPanel, button/field widgets
  host/               — AdmUiScreen
gui/custom/
  ADM_UiContainer      — themed container host with an optional widget tree
```

Low-level blitting is delegated to `GuiBlitUtil`. The Dimensional Pocket GUIs, overlay, renderer, mixins, and `UiThemes.POCKET` integration are intentionally excluded from this migration.

## Flex and style model

`UiFlex.row()` / `column()` use `UiFlexLayoutEngine` with `UiMainAlign`, `UiCrossAlign`, `UiAlignSelf`, grow/shrink, preferred sizes, gaps, padding, and margins. `setAbsolute(x, y)` is an escape hatch, not the default layout strategy.

`UiStyle` owns padding, margin, gap, visibility, text color overrides, and a `UiBackground` (`NONE`, `SOLID`, `NINE_SLICE`, or `FULL_TEXTURE`). `NINE_SLICE` remains for legacy-theme compatibility only; the ADM primary frame is drawn by the host through `UiPanel.draw`. A typical tree is built in `buildUi()`, laid out during `initGui`, rendered from the host, and receives input through root hit testing.

## Coordinate model and hosts

| Layer | Coordinates | Typical content |
|-------|-------------|-----------------|
| Background | Screen-absolute (`guiLeft + local`) | Sparse main/inner frames and inventory slots |
| Foreground/widget tree | Relative to the parent; root origin at `(guiLeft, guiTop)` | Labels, buttons, and fields |

- `AdmUiScreen` hosts new non-container configuration screens and owns theme, bounds, layout, rendering, and input dispatch.
- `ADM_UiContainer` hosts standard `InventoryPlayer` + `Container` screens and may attach the same widget tree.
- `AdmItemConfigScreen` and `AbstractMonitorSubGui` remain the shared bases for small item dialogs and monitor binding sub-pages.
- Existing absolute-coordinate screens may remain on `ADM_GuiScreen`, but new screens must not extend that pattern. Its final `drawScreen` applies one `UiViewportTransform` to chrome, content, tooltips, controls, and mouse hit-testing; subclasses implement `drawAdmScreen`.
- Every ADM sparse-frame corner is drawn once with one uniform scale; former edge UV regions stay transparent and the glass background is cover-cropped. Complete button shells fit inside their requested rectangles and underline fields are not tiled or stretched at runtime. Button request rectangles must not overlap, and visual, label, hover, and hit bounds are identical. Arbitrary images are aspect-fitted and centered. `drawDefaultBackground()` is intentionally empty so the world remains visible outside the transparent panel.

## Atlas: `adm_ui_atlas.png`

Path: `assets/textech/textures/gui/adm_ui_atlas.png` (512×512 RGBA)

| Region | UV origin / size | Use |
|--------|------------------|-----|
| Main sparse frame | corners `(0,0)/(24,0)/(0,24)/(24,24)`, 22×22 | Each corner is drawn once; former edge regions `(48..261,0..63)` stay transparent; dark cover background `(264,0)`, 64×64, alpha `56/255` |
| Inner sparse frame | corners `(0,66)/(16,66)/(0,82)/(16,82)`, 14×14 | Each corner is drawn once; former edge regions `(32..153,66..99)` stay transparent; bright cover background `(156,66)`, 64×64, alpha `72/255` |
| Title / footer ornament | `(222,66)` 160×12 / `(222,80)` 160×8 | Centered once per placement; uniformly shrunk only when needed |
| Complete buttons | four rows at `v=140/184/228/272`; `20/50/60/80/100/200/240 × 20` | Normal, hover, pressed, disabled; no runtime horizontal assembly or tiling |
| Underline fields | bottoms `u=0,w=480,h=3,v=320/325/330/335`; sides `u=482/487,w=3,h=20,v=320/342/364/386` | Normal, focused, invalid, disabled; bottom is center-cropped from the longest source |
| Icons | normal `v=350`, hover `v=398`, 8-column 16×16 grid | Save, cancel, back, paging, CRUD, search, refresh, settings, import/export, send, bind, copy, menu |
| Exact retained controls | slot `(300,452)`; scroll `(320,452)/(330,452)`; toggles/checks `(342..374,452/468)` | Slot, scroll, and Boolean controls retain their own semantics |

`AtlasRegion` identifies exact source pixels. `SparseFrameRegion` places four corners with one uniform scale, while `GuiBlitUtil` only cover-crops the background. `FixedAspectButtonFamily` selects complete shells and contain-fits them inside requested bounds; `UnderlineFieldRegion` only crops the longest bottom stroke. `NineSliceRegion`, `TiledFrameRegion`, and `TiledBarRegion` remain compatibility APIs for legacy themes; the ADM primary path must not tile frames, buttons, or fields. `GuiBlitUtil.drawFullTexture` remains the generic aspect-preserving image path.

Asset provenance:

- Visual source is fixed to approved chrome SHA-256 `a3889faf589b9d1832e4ba8707169b171638eed7a2e1b6dcc2a178195d006ee0` and legacy control source SHA-256 `ce3b712acec6d3a377dc924656c61a484d0d508f3fdb8931edfd5ac81435242d`; only the transparent runtime atlas is shipped.
- Runtime atlas: `src/main/resources/assets/textech/textures/gui/adm_ui_atlas.png` (SHA-256 `203244455b02bff5a996bcd3df4f89788545af87e148848b1c3f7667569c4a91`).
- Reproducible builder and layout ledger: `tools/gui/build_adm_sparse_atlas.py` and `tools/gui/adm_ui_atlas.layout.json`; generated sheets and `final_outputs.json` stay in `.workspace/adm-gui-batch1-assets/`.

## Themes

| Theme | Class | Status |
|-------|-------|--------|
| ADM | `AdmUiTheme` / `UiThemes.ADM` | Default for all non-pocket TeXTech GUIs |
| Pocket | `PocketUiTheme` / `UiThemes.POCKET` | Stub mapping `pocket_portal_panel.png`; **not wired to GUIs** |

## Component usage (summary)

### Background — `UiPanel`

```java
UiPanel.draw(UiThemes.ADM, panelLeft, guiTop, width, height);
UiPanel.drawSection(UiThemes.ADM, listX, listY, listW, listH);
UiPanel.drawDivider(UiThemes.ADM, panelLeft + 8, splitY, width - 16);
```

### Text — `UiText` (container foreground, GUI-local coords)

```java
UiText.drawCenteredTitle(UiThemes.ADM, fontRendererObj, title, centerX, 7);
UiText.drawLabel(UiThemes.ADM, fontRendererObj, I18n.format("container.inventory"), x, y);
```

### Icon — `UiIcon`

```java
UiIcon.drawThemeIcon(UiThemes.ADM, iconIndex, x, y, destSize);
UiIcon.drawAnchored(theme, index, parentX, parentY, parentW, parentH, Anchor.CENTER, 0, 0);
```

### Button — `UiButton`

Complete fixed-aspect shell with normal / hover / pressed / disabled states and an optional icon/label. Requested sizes normalize to `20/50/60/80/100/200/240 : 20` using contain-fit, never expanding outside the caller's rectangle. `hitTest`, hover, label, and drawing use the exact fitted bounds, and callers must provide non-overlapping request rectangles.

```java
UiButton btn = new UiButton(x, y, w, h).setLabel("...").setOnClick(...);
btn.draw(UiThemes.ADM, fontRendererObj, mouseX, mouseY);
```

### Layout — `UiLayoutContext`

Holds `guiLeft` / `guiTop` / `theme` / `fontRenderer`; separates absolute background coords from local foreground coords.

### Container base — `ADM_UiContainer` (optional)

```java
public class MyGui extends ADM_UiContainer {
    public MyGui(Container c) { super(c, UiThemes.ADM); }
    protected void drawGuiContainerBackgroundLayer(...) {
        drawMainPanel(localX, localY, panelW, panelH);
    }
}
```

## Debug status

| Component | Implemented | In-game debug | Verification / notes |
|-----------|-------------|---------------|----------------------|
| `GuiBlitUtil` / `SparseFrameRegion` | Yes | Yes (indirect) | Shared sparse chrome and cover cropping |
| `UiPanel` | Yes | **Yes** | Main, section, preview, tooltip, and divider chrome |
| `UiText` | Yes | **Yes** | Container labels and debug samples |
| `UiIcon` | Yes | **Yes** | Theme icon grid and status icons |
| `UiButton` / `UiButtonWidget` | Yes | **Yes** | Four visual states |
| `UiToggleButton` | Yes | **Yes** | Debug GUI |
| `UiSlot` | Yes | **Yes** | Decompressor, Storage Link, and debug GUI |
| `UiTextField` | Yes | **Yes** | Debug GUI and legacy bridge |
| `FixedAspectButtonFamily` / `UnderlineFieldRegion` / `UiFeedbackArea` | Yes | **Yes** | Button families, four field states, and fixed validation band |
| `UiScrollPanel` | Yes | **Yes** | Flex debug; Planner and Link Scanner use the same themed track/thumb |
| `UiTooltip` | stub | **No** | Call `drawHoveringText` from a `GuiScreen` subclass |
| `PocketUiTheme` | stub | **No** | — |
| `ADM_UiContainer` | Yes | **Yes** | `GuiMatterBallDecompressor` inherits it; required for new container GUIs |
| AE `GuiImgButton` / upgrade column | Native | No | Preserved for AE2 icon/state semantics |

## Reference implementation

- **Layout**: [`MatterBallDecompressorGuiLayout.java`](../../../src/main/java/com/imgood/textech/gui/MatterBallDecompressorGuiLayout.java)
- **Background**: [`MatterBallDecompressorGuiRenderer.java`](../../../src/main/java/com/imgood/textech/renders/MatterBallDecompressorGuiRenderer.java)
- **Foreground / buttons**: [`GuiMatterBallDecompressor.java`](../../../src/main/java/com/imgood/textech/gui/guiscreen/GuiMatterBallDecompressor.java)
- **GUI coverage guard**: `src/test/java/com/imgood/textech/gui/GuiThemeCoverageTest.java` dynamically scans `gui/guiscreen/`. Every non-pocket GUI must inherit an ADM host and may not use raw `GuiScreen`, the vanilla dim background, vanilla buttons, standalone legacy GUI PNGs, or direct nine-slice, three-slice, or tiled ADM chrome calls. `GuiDimensionalPocketConfig` and `GuiPocketStorage` are the fixed exclusions.
- **Atlas and scaling contracts**: `AdmUiAtlasContractTest` locks the approved 512×512 RGBA atlas SHA-256, dual background alpha, eight transparent former-edge regions, two sets of four independent corners, seven button ratios, four field states, and 17 semantic icons. `SparseRegionContractTest` locks four-corner placement, cover crops, contain-fit button bounds that cannot create overlap, underline cropping, and independent feedback bands; `UiViewportTransformTest` locks uniform rendering/input transforms across low-resolution and ultrawide viewports.

## Relation to legacy components

| Scenario | Recommendation |
|----------|----------------|
| New config/settings screen | `AdmUiScreen` + a Flex widget tree |
| Existing small/legacy screen | `AdmItemConfigScreen` or `ADM_GuiScreen`; `AdmGuiTextures` constants auto-bridge to the ADM theme |
| Monitor binding sub-page | `AbstractMonitorSubGui` |
| Container GUI (dynamic size, atlas) | `ADM_UiContainer` + `UiThemes.ADM` + `UiPanel` / `UiText` / … |
| Pocket portal look | Existing pocket renderer only; excluded from this migration |

Programmatic fills are reserved for transient semantics such as selection, hover, alternating rows, and HUD dragging. They must not replace panel chrome.

## Migration coverage and exclusions

All 28 non-pocket game GUIs use the four-corner-only sparse primitives, complete fixed-aspect buttons, and underline fields. Acceptance is no longer split into page-family batches: real-client Chinese screenshots and the button-rectangle audit are delivered once after full coverage. Standard Minecraft/AE2 tooltips keep their native behavior; the custom monitor tooltip and model preview receive themed inner frames.

The following remain excluded: `GuiDimensionalPocketConfig`, `GuiPocketStorage`, both pocket containers, the pocket overlay/renderer/mixins, and wiring `UiThemes.POCKET` into those paths. This is a permanent boundary for this migration, not unfinished ADM work.

## Debug block and showcase GUI

Set `[debug] uiFrameworkBlock=true` and **restart the game**. The **UI Framework Debug Block** (`BlockUiFrameworkDebug`) appears in the creative inventory. Right-click to open `GuiUiFrameworkDebug`:

| Area | Content |
|------|---------|
| Full background | `UiPanel` sparse main frame plus centered title ornament |
| Left column | Live widget samples + class names + short descriptions |
| Right column | **UV / size** reference for sparse frames, complete buttons, four field states, icons, and exact controls |
| Right-bottom | Compact Flex + scroll sample |

Widgets shown: `UiText`, `UiIcon` (0–3), `UiButton`, `UiButton(disabled)`, `UiToggleButton`, `UiSlot` (vanilla + Meowa theme), `UiTextField`, and the Flex scroll sample.

The showcase is bounded to a 408x228 safe size. `GuiLowResolutionLayoutTest` locks large-panel bounds, column separation, and atlas/Flex vertical limits at a 427x240 scaled viewport; real-client QA uses vanilla framebuffer screenshots to detect blank or clipped output.

Config: `Config.debugUiFrameworkBlock` / `[debug] uiFrameworkBlock`.
