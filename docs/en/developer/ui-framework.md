# TeXTech UI Framework (Flex Widget Tree)

The TeXTech GUI framework lives in `com.imgood.textech.gui.framework`. It combines a **Flex widget tree**, `UiStyle`, and a runtime **9-slice theme atlas** derived from a Meowa-generated pixel sci-fi component sheet. All configuration and container screens except the Dimensional Pocket family use this ADM visual system.

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
  GuiBlitUtil          — blit, 9-slice, horizontal 3-slice
  NineSliceRegion      — atlas region (UV, border)
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

`UiStyle` owns padding, margin, gap, visibility, text color overrides, and a `UiBackground` (`NONE`, `SOLID`, `NINE_SLICE`, or `FULL_TEXTURE`). A typical tree is built in `buildUi()`, laid out during `initGui`, rendered from the host, and receives input through root hit testing.

## Coordinate model and hosts

| Layer | Coordinates | Typical content |
|-------|-------------|-----------------|
| Background | Screen-absolute (`guiLeft + local`) | 9-slice panels and inventory slots |
| Foreground/widget tree | Relative to the parent; root origin at `(guiLeft, guiTop)` | Labels, buttons, and fields |

- `AdmUiScreen` hosts new non-container configuration screens and owns theme, bounds, layout, rendering, and input dispatch.
- `ADM_UiContainer` hosts standard `InventoryPlayer` + `Container` screens and may attach the same widget tree.
- `AdmItemConfigScreen` and `AbstractMonitorSubGui` remain the shared bases for small item dialogs and monitor binding sub-pages.
- Existing absolute-coordinate screens may remain on `ADM_GuiScreen`, but new screens must not extend that pattern.
- `GuiResponsiveLayout.fitCentered` constrains a legacy preferred panel size to the scaled viewport. Background, controls, scroll/content bounds, and wrapping width must all use the returned runtime bounds. `ADM_GuiButton` trims labels in very narrow controls so localized text cannot escape the button.

## Atlas: `adm_ui_atlas.png`

Path: `assets/textech/textures/gui/adm_ui_atlas.png` (256×256)

| Region | UV origin | Size | border | Use |
|--------|-----------|------|--------|-----|
| mainPanel | (0, 0) | 96×108 | 10 | Main panel 9-slice |
| sectionPanel | (100, 0) | 88×56 | 8 | Inset/list/preview panel |
| buttonNormal | (100, 60) | 48×20 | 8 | Normal button |
| buttonHover | (100, 82) | 48×20 | 8 | Hover |
| buttonPressed | (100, 104) | 48×20 | 8 | Mouse-down |
| buttonDisabled | (100, 126) | 48×20 | 8 | Disabled |
| textFieldNormal | (152, 60) | 48×20 | 6 | Text field |
| textFieldFocused | (204, 60) | 48×20 | 6 | Focused text field |
| slot | (152, 84) | 18×18 | 3 | Inventory slot |
| scrollTrack | (174, 84) | 10×42 | 3 | Scroll track |
| scrollThumb | (188, 84) | 10×20 | 3 | Scroll thumb |
| divider | (204, 84) | 48×4 | 1 | Divider |
| toggleOff / On / Disabled | (152 / 184 / 216, 108) | 28×14 | 4 | Three-state toggle |
| checkOff / On / Disabled | (152 / 170 / 188, 126) | 14×14 | 3 | Three-state checkbox |
| icons | (0, 160) | 16×16 grid | — | Theme icons |

Asset provenance:

- Meowa source sheet: `.workspace/meowa/in-game-gui-unified-r1/A_cohesive_pixel-art_game_UI_component_sheet_for_TeXTech_Advance_Data_Monitor_in_Minecraft_GTNH_black_transluc_222abf39/ui_output.png` (1024×1024, SHA-256 `f4a3c419484d2ad955bd53774d9d5856fd2948d2a73d6917778735ef775405cb`).
- Runtime atlas: `src/main/resources/assets/textech/textures/gui/adm_ui_atlas.png` (SHA-256 `0038210d8ba910b9e93a67b3ed628ecac9d7057962148d8aa510e90886e4f029`).
- Authoritative UV data: `.workspace/adm_ui_atlas_meowa.layout.json`; builder: `.workspace/build_adm_ui_atlas.py`.

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

Horizontal 3-slice with normal / hover / pressed / disabled states and an optional icon/label; `hitTest` / `click` support non-`GuiButton` flows.

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
| `GuiBlitUtil` / `NineSliceRegion` | Yes | Yes (indirect) | Shared themed blitting |
| `UiPanel` | Yes | **Yes** | Main, section, preview, tooltip, and divider chrome |
| `UiText` | Yes | **Yes** | Container labels and debug samples |
| `UiIcon` | Yes | **Yes** | Theme icon grid and status icons |
| `UiButton` / `UiButtonWidget` | Yes | **Yes** | Four visual states |
| `UiToggleButton` | Yes | **Yes** | Debug GUI |
| `UiSlot` | Yes | **Yes** | Decompressor, Storage Link, and debug GUI |
| `UiTextField` | Yes | **Yes** | Debug GUI and legacy bridge |
| `UiScrollPanel` | Yes | **Yes** | Flex debug; Planner and Link Scanner use the same themed track/thumb |
| `UiTooltip` | stub | **No** | Call `drawHoveringText` from a `GuiScreen` subclass |
| `PocketUiTheme` | stub | **No** | — |
| `ADM_UiContainer` | Yes | **Yes** | `GuiMatterBallDecompressor` inherits it; required for new container GUIs |
| AE `GuiImgButton` / upgrade column | Native | No | Preserved for AE2 icon/state semantics |

## Reference implementation

- **Layout**: [`MatterBallDecompressorGuiLayout.java`](../../src/main/java/com/imgood/textech/gui/MatterBallDecompressorGuiLayout.java)
- **Background**: [`MatterBallDecompressorGuiRenderer.java`](../../src/main/java/com/imgood/textech/renders/MatterBallDecompressorGuiRenderer.java)
- **Foreground / buttons**: [`GuiMatterBallDecompressor.java`](../../src/main/java/com/imgood/textech/gui/guiscreen/GuiMatterBallDecompressor.java)
- **GUI coverage guard**: `src/test/java/com/imgood/textech/gui/GuiThemeCoverageTest.java` dynamically scans `gui/guiscreen/`. Every non-pocket GUI must use an ADM host or explicit ADM panels, and may not introduce a vanilla button or standalone legacy GUI PNG. `GuiDimensionalPocketConfig` and `GuiPocketStorage` are the fixed exclusions.
- **Atlas contract**: `src/test/java/com/imgood/textech/gui/framework/AdmUiAtlasContractTest.java` locks the approved Meowa atlas SHA-256, 256x256 RGBA contract, every theme UV and bound, all 14 icon cells, and distinct button, text-field, scroll, toggle, and check states.

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

The shared hosts or explicit themed components cover the monitor main/sub screens, AI Chat, Planner, Link Scanner, manual, NBT Viewer, Screenshot Gallery, Storage Link, Matter Ball Decompressor, and the framework debug screen. Standard Minecraft/AE2 tooltips keep their native behavior; the custom monitor tooltip and model preview receive themed section frames.

The following remain excluded: `GuiDimensionalPocketConfig`, `GuiPocketStorage`, both pocket containers, the pocket overlay/renderer/mixins, and wiring `UiThemes.POCKET` into those paths. This is a permanent boundary for this migration, not unfinished ADM work.

## Debug block and showcase GUI

Set `[debug] uiFrameworkBlock=true` and **restart the game**. The **UI Framework Debug Block** (`BlockUiFrameworkDebug`) appears in the creative inventory. Right-click to open `GuiUiFrameworkDebug`:

| Area | Content |
|------|---------|
| Full background | `UiPanel` 9-slice (`mainPanel` region) |
| Left column | Live widget samples + class names + short descriptions |
| Right column | **UV / size / border** reference including section, pressed, slot, scroll, and divider regions |
| Right-bottom | Compact Flex + scroll sample |

Widgets shown: `UiText`, `UiIcon` (0–3), `UiButton`, `UiButton(disabled)`, `UiToggleButton`, `UiSlot` (vanilla + Meowa theme), `UiTextField`, and the Flex scroll sample.

The showcase is bounded to a 408x228 safe size. `GuiLowResolutionLayoutTest` locks large-panel bounds, column separation, and atlas/Flex vertical limits at a 427x240 scaled viewport; real-client QA uses vanilla framebuffer screenshots to detect blank or clipped output.

Config: `Config.debugUiFrameworkBlock` / `[debug] uiFrameworkBlock`.
