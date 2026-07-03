# UI Framework (Container GUI)

The TeXTech reusable container GUI framework lives in `com.imgood.textech.gui.framework`. It assembles variable-size panels from a single atlas via **9-slice / horizontal 3-slice**, with multi-theme support.

Config screens still use [`ADM_GuiScreen`](../.cursor/rules/gui-guidelines.mdc) full-texture stretching.

## Architecture

```
gui/framework/
  GuiBlitUtil          — blit, 9-slice, horizontal 3-slice
  NineSliceRegion      — atlas region (UV, border)
  UiTheme / AdmUiTheme / PocketUiTheme / UiThemes
  UiPanel, UiText, UiIcon, UiButton, UiLayoutContext
  UiSlot, UiTextField, UiToggleButton, UiTooltip (placeholder)
gui/custom/
  ADM_UiContainer      — container GUI base (`GuiMatterBallDecompressor` uses it)
```

Blit logic was extracted from [`PocketPortalGuiRenderer`](../../src/main/java/com/imgood/textech/client/PocketPortalGuiRenderer.java); pocket GUI visuals are unchanged (delegates to `GuiBlitUtil`).

## Atlas: `adm_ui_atlas.png`

Path: `assets/textech/textures/gui/adm_ui_atlas.png` (256×256)

| Region | UV origin | Size | border | Use |
|--------|-----------|------|--------|-----|
| mainPanel | (0, 0) | 64×64 | 16 | Main panel 9-slice |
| buttonNormal | (0, 64) | 48×20 | 8 | Button 3-slice |
| buttonHover | (0, 84) | 48×20 | 8 | Hover |
| buttonDisabled | (0, 104) | 48×20 | 8 | Disabled |
| textFieldNormal | (64, 64) | 80×20 | 6 | Text field (not debugged) |
| textFieldFocused | (64, 84) | 80×20 | 6 | Focused (not debugged) |
| icons | (64, 0) | 16×16 grid | — | Theme icons |

If the atlas is missing, `UiPanel` falls back to solid color (same as the legacy decompressor `drawSolidPanel`).

## Themes

| Theme | Class | Status |
|-------|-------|--------|
| ADM | `AdmUiTheme` / `UiThemes.ADM` | Default; used by Matter Ball Decompressor |
| Pocket | `PocketUiTheme` / `UiThemes.POCKET` | Stub mapping `pocket_portal_panel.png`; **not wired to GUIs** |

## Component usage (summary)

### Background — `UiPanel`

```java
UiPanel.draw(UiThemes.ADM, panelLeft, guiTop, width, height);
UiPanel.drawDivider(panelLeft + 8, splitY, width - 16);
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

Horizontal 3-slice + optional icon/label; `hitTest` / `click` for non-`GuiButton` flows.

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
| `GuiBlitUtil` / `NineSliceRegion` | Yes | Yes (indirect) | Pocket GUI regression + decompressor |
| `UiPanel` / 9-slice main panel | Yes | **Yes** | Matter Ball Decompressor, buffer 1×1 / 3×3 / 9×9 |
| `UiText` | Yes | **Yes** | Decompressor title + `container.inventory` label |
| `UiIcon` | Yes | **Yes** | Status icons beside AE side buttons |
| `UiButton` | Yes | **Yes** | Title-bar accent chip (3-slice, disabled display) |
| `UiToggleButton` | Yes | **No** | — |
| `UiSlot` | Yes | **No** | Decompressor still uses `MatterBallDecompressorGuiRenderer.drawSlotCell` |
| `UiTextField` | Yes | **No** | — |
| `UiTooltip` | stub | **No** | Call `drawHoveringText` from a `GuiScreen` subclass |
| `PocketUiTheme` | stub | **No** | — |
| `ADM_UiContainer` | Yes | **Yes** | `GuiMatterBallDecompressor` inherits it; required for new container GUIs |
| AE `GuiImgButton` / upgrade column | Legacy | No | Out of framework scope |

## Reference implementation

- **Layout**: [`MatterBallDecompressorGuiLayout.java`](../../src/main/java/com/imgood/textech/gui/MatterBallDecompressorGuiLayout.java)
- **Background**: [`MatterBallDecompressorGuiRenderer.java`](../../src/main/java/com/imgood/textech/renders/MatterBallDecompressorGuiRenderer.java)
- **Foreground / buttons**: [`GuiMatterBallDecompressor.java`](../../src/main/java/com/imgood/textech/gui/guiscreen/GuiMatterBallDecompressor.java)

## Relation to legacy components

| Scenario | Recommendation |
|----------|----------------|
| Config/settings (no Container) | `AdmItemConfigScreen` (small) or `ADM_GuiScreen` (complex) + `AdmGuiTextures` |
| Monitor binding sub-page | `AbstractMonitorSubGui` |
| Container GUI (dynamic size, atlas) | `ADM_UiContainer` + `UiThemes.ADM` + `UiPanel` / `UiText` / … |
| Pocket portal look | Still `PocketPortalGuiRenderer`; future `UiThemes.POCKET` |

Atlas generator (dev): `.workspace/generate_adm_ui_atlas.py`

## Debug block and showcase GUI

Set `[debug] uiFrameworkBlock=true` and **restart the game**. The **UI Framework Debug Block** (`BlockUiFrameworkDebug`) appears in the creative inventory. Right-click to open `GuiUiFrameworkDebug`:

| Area | Content |
|------|---------|
| Full background | `UiPanel` 9-slice (`mainPanel` region) |
| Left column | Live widget samples + class names + short descriptions |
| Right column | **UV / size / border** reference for each `adm_ui_atlas.png` region |
| Footer | Hint: edit PNG regions and keep `AdmUiTheme` constants in sync |

Widgets shown: `UiText`, `UiIcon` (0–3), `UiButton`, `UiButton(disabled)`, `UiToggleButton`, `UiSlot` (vanilla + procedural), `UiTextField`.

Config: `Config.debugUiFrameworkBlock` / `[debug] uiFrameworkBlock`.
