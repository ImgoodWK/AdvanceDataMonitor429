# TeXTech Brand & Visual Design Guide

> Audience: artists, pack authors, marketing · Last updated: 2026-07  
> 中文: [品牌视觉设计指南.md](../../zh/design/品牌视觉设计指南.md)

This document defines **TeXTech** brand identity, world-building, color system, and promotional asset specs, derived from current code, manual text, GUI themes, texture tooling, and the [Future Development Vision](future-development-vision.md). It includes ready-to-use AI image prompts.

---

## 1. Mod Identity

| Field | Value | Notes |
|-------|-------|-------|
| **English brand** | **TeXTech** | Code, GitHub, CurseForge |
| **Chinese display name** | **铽丝科技** | In-game key category (`key.categories.textech`) |
| **Mod ID** | `textech` | Assets under `assets/textech/` |
| **Legacy Mod ID** | `advancedatamonitor` | Migrated; do not use in new assets |
| **Tagline (ZH)** | *从数据洪流中编织现实，用二进制之线缝合物与质。* | Core brand line |
| **Tagline (EN)** | *Weaving reality from the torrent of data, stitching matter with threads of binary.* | International marketing |
| **Positioning** | GTNH community utility mod | AE2 visualization + inverse data weaving + AI assistant |
| **Platform** | Minecraft 1.7.10 / Forge / GTNH | Pixel art + GregTech industrial sci-fi |

### Naming semantics

- **Terbium (Tb)**: rare-earth metaphor for endgame precision tech.  
- **丝 (thread)**: data as weaveable threads—item types, NBT, fluids, essentia.  
- **TeXTech**: dev-facing ID; `X` suggests cross-tech (AE2 × GT × visualization).

### Brand voice

Calm, engineering-focused, cosmic scale; avoid childish fantasy or memes that break GTNH immersion.

---

## 2. World-Building

### Universe

Same universe as **GregTech: New Horizons**, after massive AE2 deployment. When storage bytes and item types reach astronomical scale:

> **What else can we do with all this data?**

AE2 **digitizes** matter into network records. TeXTech researchers invert the process—**weaving digital records back into real matter**.

### Core concept: Data Weaving

| Path | Description |
|------|-------------|
| Forward | AE2: matter → scan → stored data |
| Inverse | Loom cells: stored type records → slowly woven dust, items, fluids, essentia |
| Monitor | Advance Data Monitor: NBT/stats rendered as in-world charts |
| Imprint | Data Imprint Tool: captures linker NBT as monitor data source |
| Pocket | Dimensional Pocket: per-player data fold persisted in world save |
| Grapple | Travel as **journey** across built bases, not one-frame teleport |

### Four narrative pillars

1. **See data** — monitors, AE2 linkers, matter ball decompressor  
2. **Weave matter** — dust/form/flow/tide/source cells + weave amplifiers  
3. **Talk to automation** — AI/voice assistant, advance planner  
4. **Legend & experience** — Super Orange, Empyrean Holy Judgment, grapple, pocket  

### Lore (implemented + vision hooks)

- **Dust loom cell**: immature tech, dust prefixes only.  
- **Form loom cell**: full item reconstruction.  
- **Flow / tide / source**: fluid and Thaumcraft essentia channels.  
- **Super Orange**: ultra-rare legendary utility drop.  
- **Empyrean Holy Judgment**: endgame destructive sword with cosmic shader.  
- **Vision**: first Super Oranges were woven in a lost **data temple**; larger origin story planned.

---

## 3. Visual Style

**GTNH industrial sci-fi × AE2 storage aesthetic × deep-space data visualization**

- Items: 16×16 pixel; loom cells use AE2 cell shells + animated center glyphs.  
- Blocks: monitor uses 3D TESR (`AdvanceDataMonitor.obj`).  
- GUI: deep blue + cyan 9-slice (`adm_ui_atlas.png`).  
- Manual GUI: dashboard HUD palette (§4.2).  
- Pocket: portal rift, blue-purple grid (`pocket_portal_panel.png`).  
- Legendary: orange/gold vs cosmic sword nebula.

Animated loom textures: **16×320 strip, 20 frames**, AE2 Universe layout.

---

## 4. Color System

### Primary palette

| Name | Hex | Usage |
|------|-----|-------|
| Deep Void | `#0E1A30` | Manual BG, promo base |
| Panel Navy | `#122040` | Sidebar, cards |
| Frame Black | `#0A1220` | Borders |
| Select Teal | `#1A6080` | Selected state |
| Hover Slate | `#1A3A5C` | Hover |
| Divider Cyan | `#1E5080` | Separators |
| Text Primary | `#404040` | ADM container text on light panels |
| Text Disabled | `#A0A0A0` | Disabled controls |

### Accents

| Name | Hex | Usage |
|------|-----|-------|
| Core Cyan | `#00FFFF` | ADM accent, loom glow |
| Manual Cyan | `#00E5FF` | Manual selection |
| Chapter Aqua | `#20B8E0` | Chapter titles |
| Sidebar Ice | `#C8E0FF` | Sidebar body |
| Pocket Blue | `#88AAFF` | Pocket theme |

### Subsystem colors

| System | Colors |
|--------|--------|
| Dust loom | Brown `#78644A` + cyan |
| Form loom | `#00C8FF` + white core |
| Flow loom | Blue `#1E78FF` |
| Source loom | Purple `#B428C8` |
| Super Orange | Orange `#FF8800` + gold |
| Holy sword | White / star blue / purple-black cosmos |

### Weave glow ramp (from tooling)

`(0,95,120) → (0,155,190) → (40,215,245) → (150,255,255)`

### Rules

70% deep blue-black / 20% cyan glow / 10% functional accents (orange, purple, gold). Avoid pure green as primary or flat Material-style UI.

---

## 5. Typography & Logo

In-game: Minecraft bitmap font. Promo: geometric sans (Eurostile, Orbitron, Source Han Heavy).  
Logo wordmark: `TeX` in `#00FFFF`, `Tech` in `#C8E0FF`, faint hex or binary background at 5–8% opacity.

---

## 6. Promotional Asset Specs

### Mod icon (256 / 512)

- **Use**: CurseForge, Modrinth, GitHub, `mcmod.info` `logoFile`  
- **Composition**: simplified Advance Data Monitor face, cyan line chart + weave ring, deep void BG  
- **Suggested path**: `assets/textech/textures/promo/mod_icon_512.png`

### Banner (1920×480 or 1920×1080)

- Left third: title safe zone  
- Right: GT factory + AE cables + floating monitor charts + cyan particle streams  
- Optional: grapple nodes, pocket rift accent  

### Poster (1080×1920, optional)

Logo top; form loom cell + sword + orange triangle; four-system icon strip bottom.

### Screenshot priorities

1. In-world monitor charts  
2. Loom cells in ME drive + amplifiers  
3. AI chat crafting query  
4. Grapple POV  
5. Pocket portal GUI  
6. Holy sword rain VFX  

---

## 7. Subsystem Visual Quick Reference

See Chinese doc §7 for asset file mapping (`AdvanceDataMonitor.obj`, `adm_ui_atlas.png`, loom cells, etc.).

---

## 8. AI Image Prompts

**Append to every EN prompt:**

```
GregTech New Horizons mod aesthetic, industrial sci-fi, deep navy blue and cyan glow color palette, volumetric lighting, dark background, Minecraft 1.7.10 game asset style, high detail, 4K
```

### Mod icon (512×512)

```
Square mod icon logo for "TeXTech" Minecraft GTNH mod, centered futuristic data monitor screen showing cyan line chart and weave ring symbol, deep navy blue to black gradient background, subtle hex grid pattern, cyan glow accents, clean readable silhouette at small size, game mod thumbnail, no text clutter
```

### Banner (1920×480)

```
Wide cinematic banner 1920x480, left third empty dark space for title overlay, right side GregTech factory silhouette with Applied Energistics cable glow, floating holographic monitor charts with cyan graphs, streams of cyan data particles flowing into screens, tiny grapple zipline nodes in far background, deep space industrial atmosphere, TeXTech mod promotional header
```

### Key visual (1920×1080)

```
Epic key visual poster, TeXTech "weaving data into matter" theme, massive AE2 storage matrix in background, foreground split: left Advanced Data Monitor with multi-face charts, center data loom cell with cyan spinning weave ring, right dimensional pocket portal rift in blue-purple, bottom tagline area, orange super fruit and cosmic sword as small legendary easter eggs in corners, dramatic teal lighting, GTNH endgame aesthetic
```

### Advance Data Monitor block

```
Minecraft-style Advanced Data Monitor block, tall sci-fi display panel on metal stand, multiple faces showing different chart types line graph bar chart, cyan UI on dark screen, GregTech metal casing deep blue, subtle LED strips, in-world placement render, dark workshop background
```

### Loom cells (concept upscales)

- **Dust**: `AE2 storage cell item icon, brown dust particles swirling in center, cyan energy frame, data weaving theme`  
- **Form**: `AE2 storage cell with rotating cyan wireframe cube in center, item crafting reconstruction theme`  
- **Flow/tide**: `AE2 fluid storage cell, blue liquid level animation feel, tide variant with dual wave crest`  
- **Source**: `AE2 cell with purple essentia vortex center, Thaumcraft meets AE2`  
- **Amplifier card**: `Minecraft AE2 upgrade card shape, golden connector fingers on left, cyan chase light ring on card face, super tier with orange accent streak`

### Dimensional pocket

```
Dimensional pocket storage GUI concept, nine-slice portal frame, transparent center with blue-purple rift energy, faint grid lines, items floating in pocket dimension, sci-fi personal inventory, soft glow #88AAFF accent
```

### Grapple system

```
Minecraft grapple travel system concept art, thin metal anchor plates on factory ceiling corridor, cyan path line between nodes, player silhouette ziplining hands-free, GregTech base interior, journey not teleportation mood, warm industrial lights below
```

### Legendary items

- **Super Orange**: `Legendary golden-orange fruit item with tiny orbiting drone robots, warm orange glow, Minecraft item render, GTNH loot rarity feel`  
- **Empyrean Holy Judgment**: `Cosmic holy sword Minecraft item, blade filled with starfield shader nebula, white and cyan edge glow, destructive divine judgment theme, dramatic dark background with falling mini sword rain particles`

### Manual UI mockup

```
In-game manual UI mockup, dark blue sidebar #122040, cyan selected chapter #00E5FF, right page technical documentation with highlighted search terms, TeXTech Advance Data Monitor manual, clean HUD dashboard aesthetic
```

### Negative prompt (SD)

```
cartoon, childish, bright pastel, low contrast, blurry, watermark, text garbled, wrong spelling, modern flat UI, rounded mobile app, photorealistic human face, copyright logo, purple-only AE portal clone
```

---

## 9. Asset Checklist

| Asset | Status | Target path |
|-------|--------|-------------|
| 512 mod icon | **TODO** | `assets/textech/textures/promo/mod_icon_512.png` |
| mcmod.info logoFile | **TODO** | Link when icon exists |
| CurseForge banner | **TODO** | Repo or CF upload |
| adm_ui_atlas v1 | **Done** | `textures/gui/adm_ui_atlas.png` |
| Temp block textures | **In progress** | See [temporary-textures.md](../developer/temporary-textures.md) |

---

## 10. Related Docs

- [Future Development Vision](future-development-vision.md)  
- [UI Framework](../developer/ui-framework.md)  
- [Temporary Textures](../developer/temporary-textures.md)  
- [Player Guide](../player/player-guide.md)  

---

*TeXTech — Weaving reality from the torrent of data.*
