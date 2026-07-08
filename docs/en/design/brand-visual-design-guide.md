# TeXTech Brand & Visual Design Guide

> Audience: artists, pack authors, marketing · Last updated: 2026-07  
> 中文: [品牌视觉设计指南.md](../../zh/design/品牌视觉设计指南.md)

This document defines **TeXTech** brand identity, world-building, color system, and promotional asset specs, derived from current code, manual text, GUI themes, texture tooling, and the [Future Development Vision](future-development-vision.md). It includes ready-to-use AI image prompts.

---

## 1. Mod Identity

| Field | Value | Notes |
|-------|-------|-------|
| **English brand** | **TeXTech** | Code, GitHub, CurseForge, etc.; `Te` + `XTech`, echoing Terbium and technology |
| **Chinese display name** | **铽丝科技** | In-game key category, player-facing name (`key.categories.textech`) |
| **Mod ID** | `textech` | Assets under `assets/textech/` |
| **Legacy Mod ID** | `advancedatamonitor` | Migrated; do not use in new assets |
| **Tagline (ZH)** | *从数据洪流中编织现实，用二进制之线缝合物与质。* | Core brand line from vision doc |
| **Tagline (EN)** | *Weaving reality from the torrent of data, stitching matter with threads of binary.* | International marketing |
| **Positioning** | GTNH community utility mod | AE2 data visualization + inverse weaving + AI assistant + base experience |
| **Platform** | Minecraft 1.7.10 / Forge / GTNH | Pixel art compatible with GregTech industrial aesthetic |

### 1.1 Naming Semantics

- **Terbium (Tb)**: rare-earth element symbolizing high-tier tech refined from endgame GTNH materials; echoes the precision of **data weaving** and **monitors**.
- **丝 (thread)**: data-as-thread metaphor—item types, NBT fields, fluids, and essentia in AE2 networks can all be "woven in" or "woven out."
- **TeXTech**: dev-facing identifier; `X` can read as cross-tech (AE2 × GT × visualization) or an unknown technology variable.

### 1.2 Brand Voice

| Dimension | Preferred | Avoid |
|-----------|-----------|-------|
| Narrative | Calm, engineering-focused, cosmic scale | Childish, pure fantasy, disconnected from GT hardness |
| Copy | Short sentences + technical terms (weave, imprint, link, channel) | Excessive memes, immersion-breaking slang |
| Visual | Deep-space industry + AE2 cyan glow + GT metal | High-saturation cartoon, plastic look that clashes with AE2/GT |

---

## 2. World-Building

### 2.1 Universe

TeXTech's story takes place in the **same universe as GregTech: New Horizons**, on a timeline after players have deployed large-scale AE2 networks, digital miners, and automated production lines. When storage bytes and item types approach astronomical numbers, one question emerges:

> **What else can we do with all this data?**

Applied Energistics **digitizes** matter into network types and quantities. TeXTech researchers take the opposite path—**weaving digital records back into real matter**.

### 2.2 Core Concept: Data Weaving

| Concept | Lore |
|---------|------|
| **Forward path** | AE2: matter → scan → stored as data |
| **Inverse path** | Loom cells: stored type records in the network → slowly "woven" into dust, items, fluids, essentia |
| **Monitor** | Advance Data Monitor: **imprints** TileEntity / AE2 NBT and statistics as in-world charts and text |
| **Imprint** | Data Imprint Tool: captures linker NBT as the monitor's "data source fingerprint" |
| **Dimensional Pocket** | Per-player **private data fold**—storage does not stack with items but persists in the world save |
| **Grapple** | In vast bases, movement itself should be a **journey**, not a one-frame teleport skip |

### 2.3 Four Narrative Pillars (Player-Facing)

1. **See data** — Advance Data Monitor, three AE2 linkers, matter ball decompressor  
2. **Weave matter** — dust / form / flow / tide / source cells and weave amplifier cards  
3. **Talk to automation** — AI assistant, voice assistant, advance planner  
4. **Legend & experience** — Super Orange, Empyrean Holy Judgment, grapple, dimensional pocket  

### 2.4 Storyline (Implemented + Vision Hooks)

#### Implemented Lore

- **Dust loom cell**: immature tech; can only weave the simplest forms (GT dust prefixes).  
- **Form loom cell**: once fully mastered, can reconstruct any complex item.  
- **Flow / tide / source**: weaving channels for fluids and Thaumcraft essentia.  
- **Super Orange**: ultra-rare **legendary utility** drop from dungeons and kills; accompanied by drones for instant mining and matter-ball conversion.  
- **Empyrean Holy Judgment**: endgame **destructive** holy sword with cosmic shader, instant kill, sword rain, and area judgment.  
- **Grapple**: base transit aesthetics designed by engineers who want to **admire the production lines they built**.

#### Vision Hooks (see Future Development Vision; not current gameplay)

- The first Super Oranges were not born on a crafting table—they were woven in a **lost data temple**.  
- Matter reconstruction altars, dimensional data looms, and more will reveal that **space itself is downloadable data**.  
- Behind Super Orange and Empyrean Holy Judgment lies a larger **data-weaving origin** storyline.

### 2.5 Key Perspective (No NPCs—"Researcher Notes" Tone)

> *"AE taught us: everything can be encoded. In the monitor's line charts we saw the network breathe—byte surges, type proliferation, the CPU's pulse. Then someone asked: if what we store is iron, can we weave iron from the **type itself**? The dust loom cell was the first answer—rough, slow, knowing only dust. The form loom cell was the second. And the pocket… that was the attempt to stitch storage space into the folds of the soul."*  
> — Fictional foreword style from the *TeXTech Manual*

---

## 3. Visual Style

### 3.1 Art Direction

**GTNH industrial sci-fi × AE2 storage aesthetic × deep-space data visualization**

- **Items**: primarily 16×16 pixel; loom cells use AE2 storage cell shells + animated center glyphs (ring, cube, liquid surface, vortex).  
- **Blocks**: monitor uses **3D TESR model** (`AdvanceDataMonitor.obj`); linkers are metal blocks with screens/ports.  
- **GUI**: deep blue base + cyan borders + 9-slice sci-fi panels (`adm_ui_atlas.png`); monitor sub-GUIs use traditional `background_ADM_*` full-image stretch.  
- **Manual GUI**: independent palette, **HUD dashboard** feel (see §4.2).  
- **Dimensional pocket**: portal rift, translucent center, blue-purple grid lines (`pocket_portal_panel.png`).  
- **Legendary combat**: Starry Cosmos sword cosmic shader; orange-gold warm accents (Super Orange).

### 3.2 Relationship to Reference Mods

| Reference | Borrow | Differentiate |
|-----------|--------|---------------|
| AE2 | Storage cell silhouette, cyan glow, accelerator card outline | Center glyph is a **weave ring / weave net**, not stock AE icons |
| GregTech | Deep blue metal, industrial panels, energy ports | More **data-flow / chart** elements |
| Applied Energistics monitoring | Linker "wiring" logic | **Large in-world chart screens**, not terminal UI only |

### 3.3 Animated Texture Spec

Loom items use a **16×320 vertical strip, 20 frames** (same as AE2 Universe cells), `frametime: 2`, some with `interpolate`.

- Dust: center dust particles  
- Form: wireframe cube rotation  
- Flow: rising/falling liquid level  
- Source: essentia vortex pulse  
- Weave amplifier card: chase-light ring + trail  

---

## 4. Color System

### 4.1 Primary Palette (Brand-Level)

| Name | Hex | RGB | Usage |
|------|-----|-----|-------|
| **Deep Void** | `#0E1A30` | 14, 26, 48 | Manual background, promo base |
| **Panel Navy** | `#122040` | 18, 32, 64 | GUI sidebar, card backgrounds |
| **Frame Black** | `#0A1220` | 10, 18, 32 | Borders, shadows |
| **Select Teal** | `#1A6080` | 26, 96, 128 | Selected state, emphasis blocks |
| **Hover Slate** | `#1A3A5C` | 26, 58, 92 | Hover, secondary panels |
| **Divider Cyan** | `#1E5080` | 30, 80, 128 | Separators, weak emphasis |
| **Text Primary** | `#404040` | 64, 64, 64 | ADM container GUI main text (on light panels) |
| **Text Disabled** | `#A0A0A0` | 160, 160, 160 | Disabled controls |

### 4.2 Accent Colors

| Name | Hex | Usage |
|------|-----|-------|
| **Core Cyan** | `#00FFFF` | ADM theme accent, loom cell primary glow |
| **Manual Cyan** | `#00E5FF` | Manual selected text |
| **Chapter Aqua** | `#20B8E0` | Manual chapter titles |
| **Sidebar Ice** | `#C8E0FF` | Manual sidebar body text |
| **Muted Blue** | `#5080B0` | Page numbers, secondary info |
| **Pocket Blue** | `#88AAFF` | Dimensional pocket theme accent |
| **Rift Edge** | `#4088AA` | Pocket slot borders (with alpha) |

### 4.3 Subsystem Functional Colors

| Subsystem | Primary | Secondary | Notes |
|-----------|---------|-----------|-------|
| Data weaving · dust | Brown `#78644A` | Cyan accents | Dust, incomplete forms |
| Data weaving · form | `#00C8FF` | White core | Full item reconstruction |
| Data weaving · flow | `#1E78FF` | Light blue highlights | Fluid channel |
| Data weaving · source | `#B428C8` | Deep purple `#6414A0` | Thaumcraft essentia |
| Weave amplifier card | Cyan ring + gold fingers | Orange (super tier) | Based on AE2 accelerator card |
| Super Orange | Orange `#FF8800` | Gold, green leaf | Legendary warm contrast |
| Empyrean Holy Judgment | White / star blue | Purple-black cosmos | Starfield shader |
| Grapple node | Configurable nav color | Metal gray base | Player-custom pin colors |
| Monitor charts | User-configurable | Default cyan/green/yellow | In-game Color Config |

### 4.4 Weave Glow Ramp (Code Constants)

From `tools/generate_loom_textures.py`:

```
ambient  → (0, 95, 120)
base     → (0, 155, 190)
bright   → (40, 215, 245)
hot core → (150, 255, 255)
```

### 4.5 Color Usage Principles

1. **7:2:1**: 70% deep-space blue-black base, 20% cyan glow, 10% functional/legendary accents (orange, purple, gold).  
2. **In GUI**: container frames use ADM atlas; manual/AI screens may use brighter cyan text.  
3. **In promo art**: higher contrast and volumetric light than in-game is allowed, but **hue must stay within** the primary palette above.  
4. **Avoid**: pure green as primary (confuses with GT circuits), high-saturation pink, flat Material Design rounded corners.

---

## 5. Typography & Layout

| Context | Spec |
|---------|------|
| In-game | Minecraft default bitmap font; Chinese depends on pack font pack |
| Promo / manual cover | Geometric sans (e.g. Eurostile, Orbitron, Source Han Sans Heavy); English TeXTech may be all-caps or `TeX` with lowercase x for emphasis |
| Heading hierarchy | H1 brand name + cyan underline; H2 chapter aqua `#20B8E0`; body `#C8E0FF` on dark base |
| Data / code feel | Monospace for NBT, Mod ID, config keys |

**Logo wordmark suggestion**: `TeX` in Core Cyan `#00FFFF`, `Tech` in Sidebar Ice `#C8E0FF`, background texture of faint hex grid or binary 0101 pattern at 5–8% opacity.

---

## 6. Logo & Promotional Asset Specs

### 6.1 Mod Thumbnail (Mod Icon / Logo)

| Attribute | Spec |
|-----------|------|
| **Use** | CurseForge, Modrinth, GitHub Social Preview, `mcmod.info` `logoFile` |
| **Size** | **256×256** (in-game/icon); **512×512** (store HD) |
| **Safe zone** | Main graphic within center 80% circle; 8px dark margin at corners |
| **Composition** | Center: **Advance Data Monitor** 3D screen or simplified front face; screen shows **line chart + weave ring**; deep void background; optional AE2-style cable hints |
| **Suggested path** | `src/main/resources/assets/textech/textures/promo/mod_icon_512.png` (fill `mcmod.info` after art delivery) |

### 6.2 Horizontal Banner / Header

| Attribute | Spec |
|-----------|------|
| **Use** | CurseForge page header, GitHub README top image, pack intro |
| **Size** | **1920×480** (CurseForge common) or **1920×1080** (full-screen background) |
| **Composition** | Left third: **TeXTech wordmark + tagline**; center-right: **GTNH factory silhouette + AE2 cables + floating monitor charts**; foreground **cyan data particle streams** flowing right-to-left into monitors; distant **grapple node chain** or **pocket rift** as accent |
| **Suggested path** | `docs/assets/promo/banner_1920x480.png` or repo `/.github/banner.png` |

### 6.3 Vertical Poster (Optional)

| Attribute | Spec |
|-----------|------|
| **Size** | 1080×1920 |
| **Composition** | Wordmark top; center **form loom cell + holy sword + Super Orange** triangle layout; bottom strip of four-system icons |

### 6.4 Representative In-Game Screenshots

Prioritize these for marketing:

1. Multiple in-world monitors showing AE2 line charts  
2. Loom cells in ME drive + amplifier cards  
3. AI chat window querying crafting  
4. Grapple zipline POV  
5. Dimensional pocket rift GUI  
6. Empyrean Holy Judgment sword-rain VFX  

---

## 7. Subsystem Visual Quick Reference

| Subsystem | Key Visual Symbol | Representative Assets |
|-----------|-------------------|----------------------|
| Monitor | Large screen, line/bar charts, multi-face binding | `AdvanceDataMonitor.obj`, `background_AdvanceDataMonitor_Main.png` |
| AE2 link | Metal block + port + thin cable | `adv_*_link.png` |
| Data weaving | AE2 cell shell + animated center glyph | `data_*_loom_cell.png`, `weave_amplifier.png` |
| AI assistant | Chat dialog, gear settings | `GuiAIChat` |
| Planner | Clipboard shape | `advance_planner.png` |
| Grapple | Thin anchor plate, hook, path line | `grapple_anchor.png`, `grapple_hook.png` |
| Pocket | Portal rift, blue grid | `pocket_portal_panel.png` |
| Legendary | Orange / starfield sword | `orange.png`, `starry_cosmos_sword.png` |

---

## 8. AI Image Prompt Library

The prompts below work with Midjourney, Stable Diffusion, DALL·E, etc. After generation, **downsample + hand-pixelate** for in-game 16×16 textures; promo art may stay high resolution.

**Universal suffix (English—append to every EN prompt):**

```
GregTech New Horizons mod aesthetic, industrial sci-fi, deep navy blue and cyan glow color palette, volumetric lighting, dark background, Minecraft 1.7.10 game asset style, high detail, 4K
```

**Universal suffix (Chinese):**

```
GregTech New Horizons 模组美学，工业科幻，深蓝与青色辉光配色，体积光，暗色背景，Minecraft 1.7.10 游戏资产风格，高细节，4K
```

---

### 8.1 Mod Thumbnail / Logo (512×512)

**Visual brief**: Within circular safe zone, simplified Advance Data Monitor front face; screen shows cyan line chart; weave ring symbol at screen corners; deep void blue-black gradient background; faint "TeXTech" wordmark at bottom.

**English:**
```
Square mod icon logo for "TeXTech" Minecraft GTNH mod, centered futuristic data monitor screen showing cyan line chart and weave ring symbol, deep navy blue to black gradient background, subtle hex grid pattern, cyan glow accents, clean readable silhouette at small size, game mod thumbnail, no text clutter
```

**Chinese:**
```
TeXTech Minecraft GTNH 模组方形图标，居中未来风数据监视器屏幕显示青色折线图与编织环符号，深蓝黑渐变背景，淡淡六边形网格，青色辉光点缀，小尺寸清晰可辨，模组缩略图，避免文字杂乱
```

---

### 8.2 Horizontal Banner (1920×480)

**English:**
```
Wide cinematic banner 1920x480, left third empty dark space for title overlay, right side GregTech factory silhouette with Applied Energistics cable glow, floating holographic monitor charts with cyan graphs, streams of cyan data particles flowing into screens, tiny grapple zipline nodes in far background, deep space industrial atmosphere, TeXTech mod promotional header
```

**Chinese:**
```
1920x480 宽 cinematic 横幅，左侧三分之一深色留白供标题叠加，右侧 GregTech 工厂剪影与 AE 线缆辉光，悬浮全息监视器图表青色曲线，青色数据粒子流汇入屏幕，远处微小挂索节点，深空工业氛围，TeXTech 模组宣传页眉
```

---

### 8.3 Brand Key Visual (1920×1080)

**English:**
```
Epic key visual poster, TeXTech "weaving data into matter" theme, massive AE2 storage matrix in background, foreground split: left Advanced Data Monitor with multi-face charts, center data loom cell with cyan spinning weave ring, right dimensional pocket portal rift in blue-purple, bottom tagline area, orange super fruit and cosmic sword as small legendary easter eggs in corners, dramatic teal lighting, GTNH endgame aesthetic
```

**Chinese:**
```
TeXTech「从数据编织物质」主题史诗主视觉，背景巨大 AE2 存储矩阵，前景左为高级数据监视器多面图表，中为带青色旋转编织环的数据编织元件，右为蓝紫色次元口袋裂隙，底部留 tagline 区域，角落小彩蛋超能砂糖桔与宇宙圣剑，戏剧性 Teal 光照，GTNH 终局美学
```

---

### 8.4 Advance Data Monitor (Block / 3D Promo)

**English:**
```
Minecraft-style Advanced Data Monitor block, tall sci-fi display panel on metal stand, multiple faces showing different chart types line graph bar chart, cyan UI on dark screen, GregTech metal casing deep blue, subtle LED strips, in-world placement render, dark workshop background
```

**Chinese:**
```
Minecraft 风格高级数据监视器方块，金属支架上的科幻显示屏，多面显示折线图柱状图等不同图表，深屏青色 UI，GregTech 深蓝金属外壳，微 LED 灯条，世界内放置渲染，暗色工坊背景
```

---

### 8.5 Data Loom Cell Series (16×16 Reference / HD Concept)

**Dust:**
```
AE2 storage cell item icon, brown dust particles swirling in center, cyan energy frame, 16-bit pixel art style upscaled to 4K concept art, data weaving theme
```

**Form:**
```
AE2 storage cell with rotating cyan wireframe cube in center, item crafting reconstruction theme, pixel game icon concept
```

**Flow / tide:**
```
AE2 fluid storage cell, blue liquid level animation feel, cyan highlights, water tide variant with dual wave crest for advanced tier
```

**Source:**
```
AE2 cell with purple essentia vortex center, Thaumcraft meets AE2, mystical purple and cyan contrast
```

**Weave amplifier card:**
```
Minecraft AE2 upgrade card shape, golden connector fingers on left, cyan chase light ring animation on card face, super tier with orange accent streak
```

---

### 8.6 Dimensional Pocket

**English:**
```
Dimensional pocket storage GUI concept, nine-slice portal frame, transparent center with blue-purple rift energy, faint grid lines, items floating in pocket dimension, sci-fi personal inventory, soft glow #88AAFF accent
```

**Chinese:**
```
次元口袋存储 GUI 概念图，九切片门户边框，透明中心蓝紫裂隙能量，淡淡网格线，物品悬浮于口袋维度，科幻个人库存，#88AAFF 柔光强调
```

---

### 8.7 Grapple System

**English:**
```
Minecraft grapple travel system concept art, thin metal anchor plates on factory ceiling corridor, cyan path line between nodes, player silhouette ziplining hands-free, GregTech base interior, journey not teleportation mood, warm industrial lights below
```

**Chinese:**
```
Minecraft 挂索旅行系统概念图，工厂天花板走廊上/薄金属锚点板，节点间青色路径线，玩家双手空闲滑行动影，GregTech 基地内部，强调旅程而非传送，下方暖色工业灯
```

---

### 8.8 Legendary Items

**Super Orange:**
```
Legendary golden-orange fruit item with tiny orbiting drone robots, warm orange glow, Minecraft item render, humorous yet powerful endgame artifact, GTNH loot rarity feel
```

**Empyrean Holy Judgment:**
```
Cosmic holy sword Minecraft item, blade filled with starfield shader nebula, white and cyan edge glow, destructive divine judgment theme, dramatic dark background with falling mini sword rain particles
```

---

### 8.9 AI Assistant / Manual UI

**English:**
```
In-game manual UI mockup, dark blue sidebar #122040, cyan selected chapter #00E5FF, right page technical documentation with highlighted search terms, TeXTech Advance Data Monitor manual, clean HUD dashboard aesthetic
```

---

### 8.10 Negative Prompt (Stable Diffusion)

```
cartoon, childish, bright pastel, low contrast, blurry, watermark, text garbled, wrong spelling, modern flat UI, rounded mobile app, photorealistic human face, copyright logo, purple-only AE portal clone
```

---

## 9. Deliverables & Maintenance

| Asset | Status | Owner | Target Path |
|-------|--------|-------|-------------|
| 512 mod icon | **TODO** | Art | `assets/textech/textures/promo/mod_icon_512.png` |
| mcmod.info logoFile | **TODO** | Dev | Point to PNG above |
| CurseForge banner | **TODO** | Art | Repo or CurseForge backend |
| adm_ui_atlas v1 | **Done** | Art iteration | `textures/gui/adm_ui_atlas.png` |
| Temp block textures | **In progress** | See [temporary-textures.md](../developer/temporary-textures.md) | `textures/blocks/` |

**Maintenance rule**: When adding a subsystem, add a row in §7 and a prompt in §8; major color changes must sync `AdmUiTheme.java`, `GuiManual.java`, and this file.

---

## 10. Related Docs

- [Future Development Vision](future-development-vision.md) — long-term narrative and feature drafts  
- [UI Framework](../developer/ui-framework.md) — GUI 9-slice and theme API  
- [Temporary Textures](../developer/temporary-textures.md) — placeholder texture audit  
- [Player Guide](../player/player-guide.md) — player-facing feature overview  

---

*TeXTech · 铽丝科技 — Weaving reality from the torrent of data.*
