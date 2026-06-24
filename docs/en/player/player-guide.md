# AdvanceDataMonitor Player Guide

> Audience: players / pack authors / server admins · Last synced: 2026-06

For developer details see [Technical Documentation](../developer/technical-documentation.md). Subsystem deep-dive: [Grapple System Design](../subsystems/grapple-system-design.md). Advance Planner API: [Technical Documentation §5.11](../developer/technical-documentation.md#511-advance-planner)

---

## Table of Contents

- [0. Quick Overview](#0-quick-overview)
- [1. Mod Overview](#1-mod-overview)
- [2. Environment & Installation](#2-environment--installation)
- [3. Items & Blocks Overview](#3-items--blocks-overview)
  - [3.1 Advance Data Monitor](#31-advance-data-monitor)
  - [3.2 Data Imprint Tool](#32-data-imprint-tool)
  - [3.3 Network Linker](#33-network-linker)
  - [3.4 Crafting Linker](#34-crafting-linker)
  - [3.5 Advanced Storage Linker](#35-advanced-storage-linker)
  - [3.6 Advanced Storage Link Cell](#36-advanced-storage-link-cell)
  - [3.7 Grapple Anchor](#37-grapple-anchor)
  - [3.8 Grapple Hook](#38-grapple-hook)
  - [3.9 Data Loom Cells](#39-data-loom-cells)
  - [3.10 Super Orange](#310-super-orange)
  - [3.11 Empyrean Holy Judgment](#311-empyrean-holy-judgment)
  - [3.12 AdvanceDataMonitor Manual](#312-advancedatamonitor-manual)
- [4. Advance Data Monitor Tutorial](#4-advance-data-monitor-tutorial)
- [5. AE2 Network Monitoring](#5-ae2-network-monitoring)
- [6. AE2 Crafting Monitoring](#6-ae2-crafting-monitoring)
- [7. AE2 Advanced Storage Linker Tutorial](#7-ae2-advanced-storage-linker-tutorial)
- [8. AI Chat & Assistant](#8-ai-chat--assistant)
- [9. Voice Assistant](#9-voice-assistant)
- [10. Commands](#10-commands)
- [11. Configuration Reference](#11-configuration-reference)
- [12. Assistant Lexicon](#12-assistant-lexicon)
- [13. AI Providers & Search Modes](#13-ai-providers--search-modes)
- [14. Common Use Cases](#14-common-use-cases)
- [15. Troubleshooting](#15-troubleshooting)
- [16. Server Admin Notes](#16-server-admin-notes)
- [17. Known Limitations](#17-known-limitations)
- [18. Quick Reference](#18-quick-reference)
- [19. Advance Planner](#19-advance-planner)

---

## 0. Quick Overview

**AdvanceDataMonitor** is a Minecraft 1.7.10 utility mod for GTNH-style packs. It adds in-world data visualization for AE2 networks plus AI-assisted automation.

| Capability | Description |
|------------|-------------|
| Advance Data Monitor | Line charts, bar charts, 3D bars, waterfall, difference plots, and text overlays bound to live data |
| AE2 Linkers | **Network Linker**, **Crafting Linker**, and **Advanced Storage Linker** blocks connect to AE2 |
| Grapple travel | Smooth gliding along anchor networks with both hands free |
| AI Assistant | Natural-language AE2 queries, crafting orders, withdrawals, plans, and **Advanced Dislocator** teleport |
| Voice Assistant | **V** key recording; embedded Vosk (offline) or HTTP STT |

**Key blocks & items:** Advance Data Monitor, Data Imprint Tool, the three AE2 linker blocks, Grapple Anchor / Grapple Hook, Advanced Storage Link Cell, Data Loom cells, Advance Planner, AdvanceDataMonitor Manual, and more.

**Environment:** GTNH or any 1.7.10 pack with AE2/GTNH dependencies. After install, edit `config/advancedatamonitor/advancedatamonitor.cfg` and set an AI API key to enable the assistant.

---

## 1. Mod Overview

AdvanceDataMonitor centers on **in-world data panels + AE2 network monitoring + an AI assistant**. It reads machine TileEntity NBT, AE2 network stats, crafting CPU state, and filtered storage counts, then renders charts or item grids in the world. A chat window lets you query AE2 storage, inspect patterns, submit craft jobs, withdraw items, manage lightweight plans, and teleport via Draconic Evolution's **Advanced Dislocator**.

Core features:

- **Advance Data Monitor** — charts, titles, text, AE2 storage grids, and crafting status on any face.
- **Data Imprint Tool** — snapshot target TileEntity NBT to discover bindable field names.
- **Network Linker** — AE2 network item/fluid byte and type usage.
- **Crafting Linker** — AE2 crafting CPU counts, busy state, storage, co-processors, and active jobs.
- **Advanced Storage Linker** — displays counts for items configured on **Advanced Storage Link Cell** partitions.
- **AI chat & assistant** — OpenAI-compatible Chat API; structured AE2 actions when a key is configured.
- **Voice assistant** — WAV upload to OpenAI-compatible STT or local Vosk transcription.
- **Grapple Anchor / Grapple Hook** — scenic base travel without tying up movement keys (see [§3.7](#37-grapple-anchor)).
- **Commands & config** — `/admai`, `/admassistant`, and Forge config categories.

---

## 2. Environment & Installation

### 2.1 Runtime

| Setting | Value |
|---------|-------|
| Minecraft | 1.7.10 |
| Forge | 10.13.4.1614 |
| Mod ID | `advancedatamonitor` |
| Mod name | AdvanceDataMonitor |
| Java | JVM 8 target |

### 2.2 Dependencies

- Forge 1.7.10
- Applied Energistics 2 (AE2) — all linker blocks use AE2 network APIs
- GTNH development/runtime stack
- AE Fluid / ExtraCells APIs for fluid statistics
- Optional GregTech TileEntity name helpers for NBT binding

Use inside GTNH or a pack that already ships AE2 and related GTNH mods.

### 2.3 Install Steps

1. Install Forge 1.7.10 on client and server.
2. Place the compiled AdvanceDataMonitor jar in `mods/` on both sides.
3. Ensure AE2 and pack dependencies are present.
4. Launch once to generate config files.
5. For AI/voice: set API keys in config or in-game AI Settings.

### 2.4 Recipes

The mod does **not** register shaped/shapeless recipes in code. Obtain blocks and items via creative tab, NEI, Minetweaker/CraftTweaker scripts, or admin give commands. Pack authors should add recipes for at least:

| Registry name | Display name |
|---------------|--------------|
| `advDataMonitor` | Advance Data Monitor |
| `advNetworkLinkBlock` | Network Linker |
| `advStorageLink` | Advanced Storage Linker |
| `advCraftingLink` | Crafting Linker |
| `data_imprint` | Data Imprint Tool |
| `advance_storage_link_cell` | Advanced Storage Link Cell |
| `grappleAnchor` | Grapple Anchor |
| `grapple_hook` | Grapple Hook |

---

## 3. Items & Blocks Overview

### 3.1 Advance Data Monitor

**Registry:** `advancedatamonitor:advDataMonitor`

The main display block. Right-click to open the configuration GUI: add data bindings, change facing, toggle body/screen visibility, and open AI chat. All settings persist in TileEntity NBT and sync to clients for rendering.

**Features:**

- Records its own coordinates as the default bind target on placement.
- Ships with a demo binding (`testRandomData`) enabled by default.
- Supports multiple display entries per face, each with its own coordinates, field name, chart type, sample interval, and transform.
- Chart types: line, bar, 3D bar, waterfall, difference, crafting text, storage item grid.
- **AI** button on the main GUI opens the assistant chat window.

**In-game title (lang):** `§lAdvance Data Monitor` (`adm.title.main`)

---

### 3.2 Data Imprint Tool

**Registry:** `advancedatamonitor:data_imprint`  
**Lang name:** Data Imprint Tool (`item.dataImprint.name`)

**Tooltips (from lang):**

> *Imprints a block's data state as a snapshot for Data Monitor binding.*  
> *Imprinting observes and records; Data Loom cells weave matter from network data.*

**Usage:**

| Action | Effect |
|--------|--------|
| Sneak + right-click block | Bind target; save coordinates, block ID, meta, and TileEntity NBT snapshot |
| Right-click air | Open NBT viewer (when a TileEntity is bound) |
| Sneak + right-click air | Clear all imprint data on the tool |

**Notes:**

- Cannot bind another **Advance Data Monitor**.
- Stores a **snapshot** — not a live feed unless rebound.
- Blocks without TileEntity/NBT show an empty-viewer message.

---

### 3.3 Network Linker

**Registry:** `advancedatamonitor:advNetworkLinkBlock`

Connects to AE2 and reports network-wide storage capacity for items and fluids.

**Features:**

- AE network TileEntity; requires a channel.
- Smart Cable connection type.
- Refreshes every 20 ticks and on AE storage events.
- Right-click prints current network stats to chat.

**Bindable metrics:**

| Field | Meaning |
|-------|---------|
| `ItemTotalBytes` / `ItemUsedBytes` | Item storage capacity / used |
| `ItemTotalTypes` / `ItemUsedTypes` | Item type slots total / used |
| `FluidTotalBytes` / `FluidUsedBytes` | Fluid storage capacity / used |
| `FluidTotalTypes` / `FluidUsedTypes` | Fluid type slots total / used |

Bind the block on an **Advance Data Monitor** and choose raw values or percentage display.

---

### 3.4 Crafting Linker

**Registry:** `advancedatamonitor:advCraftingLink`

Connects to AE2 and tracks crafting CPU state. Also powers AI craft queries and job submission within **32 blocks** of the player.

**Features:**

- AE network TileEntity; requires a channel.
- Refreshes every 20 ticks and on crafting CPU events.
- Right-click prints CPU summary to chat.

**Network-wide fields:** `totalCpus`, `busyCpus`, `cpuTotalBytes`, `cpuUsedBytes`, `totalCoProcessors`

**Per-CPU fields** (by name or `CPU#N`):

| Field | Description |
|-------|-------------|
| `busyCpus:CPU#1` | 1 if busy, else 0 |
| `usedStorage:CPU#1` | Used storage bytes |
| `availableStorage:CPU#1` | Free storage bytes |
| `coProcessors:CPU#1` | Co-processor count |
| `finalOutputName:CPU#1` | Current job output name |
| `finalOutputAmount:CPU#1` | Output stack size |
| `remainingItems:CPU#1` / `startItems:CPU#1` | Job progress |
| `elapsedTime:CPU#1` | Elapsed ms |

---

### 3.5 Advanced Storage Linker

**Registry:** `advancedatamonitor:advStorageLink`  
**Lang name:** Advanced Storage Linker (`tile.advanceStorageLink.name`)

Shows AE2 counts for items/fluids configured on **Advanced Storage Link Cell** partitions placed inside the block.

**Features:**

- AE network TileEntity with **36 slots** — only accepts Advanced Storage Link Cells.
- Right-click opens the Advanced Storage Linker GUI.
- Partition slots on each cell (edited in AE2 Cell Workbench) define tracked items.
- Supports AE2 Fuzzy Card, Inverter Card, Ore Filter Card, and fluid marker NBT.
- **Data Loom cell internals are excluded** — only network stock matching cell partitions is counted.

**Typical workflow:**

1. Configure one or more **Advanced Storage Link Cells** in a Cell Workbench.
2. Optionally add upgrade cards (fuzzy / inverter / ore dictionary / fluid markers).
3. Insert cells into the **Advanced Storage Linker** GUI.
4. Bind the linker block on an **Advance Data Monitor** with display type `storage`.

> Without a Fuzzy Card, matching is exact (`isItemEqual`) so AE2FC fluid droplets are not merged incorrectly.

---

### 3.6 Advanced Storage Link Cell

**Registry:** `advancedatamonitor:advance_storage_link_cell`  
**Lang name:** Advanced Storage Link Cell (`item.advanceStorageLinkCell.name`)

A **filter/config cartridge**, not a storage drive. Implements AE2 `ICellWorkbenchItem`.

**Features:**

- Max stack size 1; 2 upgrade slots.
- Fuzzy / Inverter / Ore Filter cards supported.
- Partition slots define which items the parent **Advanced Storage Linker** displays.
- Fluid marker NBT (`fluidMarkers`) tracks AE2 fluid amounts in mB.
- Internal **Data Loom cell** accumulators (`dataLoomItemAccum` / `dataLoomFluidAccum`) are **not** counted.

**Shared tooltip (Data Loom cells):**

> *Items, fluids, or essentia stored inside this cell are excluded from Advanced Storage Linker statistics.*

---

### 3.7 Grapple Anchor

**Registry:** `advancedatamonitor:grappleAnchor`  
**Lang name:** Grapple Anchor (`tile.grappleAnchor.name`)

**Design intent:** Bases and production lines are often built to be seen, yet daily travel defaults to flight, sprinting, or teleport — all of which either tie up your hands or skip the scenery. Grapple anchors plus the **Grapple Hook** balance **speed** and **seeing the journey**.

**Tooltips (from lang):**

> *Face-mountable grapple anchor. Travel through your beautifully built base with a sense of journey — enjoy the scenery instead of skipping it with teleport.*

> *Mounts on 6 faces. Magnetic-select a node with the hook and right-click to attach; Shift to detach, right-click again to glide to another node. Shift+Right-click for node settings.*

**Usage:**

- Place on any solid face (wall, ceiling, floor) like a thin panel.
- With **Grapple Hook** in hand: HUD hint within 12 blocks; navigation icon within 6 blocks.
- Magnetic select (icon turns green), then **right-click** to attach — you are rooted but can look around freely.
- While attached, reachable nodes show through walls; select target and **right-click** to glide in a straight line.
- **Shift** — detach.
- **Shift + right-click** anchor — open settings (display name, icon highlight color).

**Good placement:** factory tour routes, scenic bridges, vertical shafts, main paths from gate to core.

**Config:** Forge category `[grapple]` (hint range, interact distance, scan radius, travel speed, magnetic angle). See [Grapple System Design](../subsystems/grapple-system-design.md).

---

### 3.8 Grapple Hook

**Registry:** `advancedatamonitor:grapple_hook`  
**Lang name:** Grapple Hook (`item.grappleHook.name`)

Core interaction tool for grapple anchors. Stack size 1.

**Tooltips (from lang):**

> *Interact with grapple anchors. Flying and sprinting tie up your hands; teleport skips the view. Glide between nodes with both hands free.*

> *HUD hint when a node is nearby. Magnetic-select a node, then right-click to attach or travel. Shift+Right-click for travel speed and HUD display settings.*

**HUD hints (lang):**

- `Grapple anchor nearby`
- `Right-click to attach to selected node`
- `Press Shift to detach grapple`

Grapple travel complements — not replaces — AE2 teleport or flight mods for paths worth experiencing slowly.

---

### 3.9 Data Loom Cells

AE2 storage cells that **weave** items, fluids, or essentia from network data (unlike the **Data Imprint Tool**, which only observes). Place in ME Drive or ME Chest; mark targets in Cell Workbench.

| Cell | Registry prefix | Channel | Marking rules |
|------|-----------------|---------|---------------|
| Data Dust Loom Cell | `data_dust_loom_cell` | Items | GT dusts only; cannot mark AdvanceDataMonitor items |
| Data Form Loom Cell | `data_form_loom_cell` | Items | Any item; cannot mark AdvanceDataMonitor items |
| Data Flow Cell | `data_flow_cell` | Fluids | Any fluid; 5 types |
| Data Tide Loom Cell | `data_tide_loom_cell` | Fluids | Same as Flow; 63 types; enchanted glint |
| Data Source Loom Cell | `data_source_loom_cell` | Fluids | Thaumcraft essentia aspect fluids only |

**Lang names:** `item.dataDustLoomCell.name`, `item.dataFormLoomCell.name`, `item.dataFlowCell.name`, `item.dataTideLoomCell.name`, `item.dataSourceLoomCell.name`

#### Story tooltips (lang)

**Data Dust Loom Cell**

> *Weaves AE network data into matter. Early tech can only reconstruct the simplest form — dust.*  
> *Mark dust items in Cell Workbench partition slots to generate slowly; AdvanceDataMonitor items cannot be marked.*

**Data Form Loom Cell**

> *Fully mastered data weaving — reconstructs any complex item from network data.*  
> *Mark any item in Cell Workbench partition slots to generate slowly; AdvanceDataMonitor items cannot be marked.*

**Data Flow Cell**

> *Weaves AE network data into fluids for the storage network.*  
> *Mark any fluid using fluid-cell style markers (containers, fluid packets, NEI drag); AdvanceDataMonitor items cannot be used as markers.*

**Data Tide Loom Cell**

> *Fully mastered fluid weaving — tides of data become up to 63 fluid types at once.*  
> *Same as Data Flow Cell: mark any fluid in Cell Workbench partition slots; AdvanceDataMonitor items cannot be used as markers.*

**Data Source Loom Cell**

> *Weaves AE network data into Thaumcraft essentia for the storage network.*  
> *Mark essentia fluids (phials, jars, etc.) in Cell Workbench; output batches every %d seconds.*

#### Shared loom tooltips (lang)

> *Drains AE network energy (default 999999 AE/t, configurable via [dataLoomCell] energyDrainPerTick).*  
> *Only Weave Amplifier cards accepted — no AE acceleration cards.*  
> *Output settles every %d seconds ([dataLoomCell] syncIntervalSeconds); see cached rate lines above.*  
> *Items, fluids, or essentia stored inside this cell are excluded from Advanced Storage Linker statistics.*

#### Weave Amplifier Card / Super Weave Amplifier Card

**Lang names:** Weave Amplifier Card · Super Weave Amplifier Card

| Card | Default multiplier | Lang highlights |
|------|-------------------|-----------------|
| Weave Amplifier Card | 4× per card (configurable) | Up to 8 cards; same-type cards multiply (1→4×, 2→16×, …) |
| Super Weave Amplifier Card | 16× per card (configurable) | **⚠ Warning:** exponential surge may instantly flood the AE network |

Both card types stack in the upgrade slot (max 8 total). Internal loom accumulators never appear on **Advanced Storage Linker** displays.

Config sections: `[dataDustLoomCell]`, `[dataFormLoomCell]`, `[dataFlowCell]`, `[dataSourceLoomCell]`, `[dataLoomCell]`. Developer detail: [Technical Documentation §5.7](../developer/technical-documentation.md).

---

### 3.10 Super Orange

**Registry:** `advancedatamonitor:super_orange`  
**Lang name:** Super Orange (`item.orange.name`)

Legendary item (`adm.super_orange.tooltip.legendary` — *Dungeon/Mob Drop (Legendary Item)*)

**Abilities (lang tooltips):**

| Feature | Control | Description |
|---------|---------|-------------|
| Instant mining | Hold item | *Instantly mine any block when held* (including GT ores) |
| Matter ball & drop multiplier | Sneak + right-click | Toggle; mined drops become Avaritia matter clusters at configured multiplier |
| Drone & projectile immunity | Right-click | Body orbits player, intercepts projectiles; clones attack nearby hostiles |
| Head nameplate & halo | Anvil rename | Custom nameplate text; hidden in first-person, visible in third person / to others |

Config section: `[superOrange]`. **Not** auto-gifted on first join (unlike the **AdvanceDataMonitor Manual**).

---

### 3.11 Empyrean Holy Judgment

**Registry:** `advancedatamonitor:starry_cosmos_sword`  
**Lang name:** Empyrean Holy Judgment (`item.starryCosmosSword.name`)

**Tooltip (`adm.tooltip.starry_cosmos_sword`):**

> *Left-click: instant kill + crescent slash wave. Right-click: throw straight along crosshair; sword rain for 5s on impact. Shift+right-click: mini judgment swords on mobs in 3×3 chunks.*

Creative tab: Combat. Max stack 1.

---

### 3.12 AdvanceDataMonitor Manual

**Registry:** `advancedatamonitor:manual`  
**Lang name:** AdvanceDataMonitor Manual (`item.manual.name`)

Right-click opens the in-game manual GUI (chapter sidebar + page content) covering blocks, items, AI assistant, planner, teleport, and config reference. Content is driven by JSON under `assets/advancedatamonitor/manual/`.

**First join:** one manual is automatically added to new players (`HandlerPlayerJoin`).

Page types: `text`, `item_showcase`, `config_ref`.

---

## 4. Advance Data Monitor Tutorial

**Setup:** Place block → right-click GUI → **Add binding** → enter `x,y,z` → configure per target type (generic TileEntity, Network Linker, Crafting Linker, or Advanced Storage Linker) → set title, chart type, offsets, rotation, scale, interval → save.

**Main GUI buttons:** facing cycle; add binding; body/screen/single-vs-dual toggles; **AI** chat; per-entry config buttons; color/chart sub-pages.

**Coordinates:** comma-separated integers, e.g. `123,64,-123` — English comma only.

**Chart fields:** `name` (NBT path or AE metric), `displayName`, `dataType`, `dataLimit`, `interval` (ticks), `xRange`, `yRange`, `yMin`/`yMax`, `isValue` (percentage mode for AE bytes).

**Chart types:** `line`, `bar`, `bar3d`, `waterfall`, `diffrence`, `crafting`, `storage`.

**Transforms:** `xOffset`/`yOffset`/`zOffset`, `rotationX`/`Y`/`Z`, `scale`. If invisible, increase `scale` (try `0.5`–`1.0`) and adjust `zOffset`.

**Colors:** 6-digit hex without `#` (e.g. `00FFFF`). Alpha fields `0.0`–`1.0`.

**Performance:** default `interval=20` for fast data; `40`–`100` for machines; never mass-deploy `interval=1` on servers.

More rendering detail: [Technical Documentation](../developer/technical-documentation.md).

---

## 5. AE2 Network Monitoring

1. Cable-connect **Network Linker** to AE2 (channel required).
2. Right-click — chat should show byte/type stats.
3. Bind link coordinates on **Advance Data Monitor**.
4. Pick metric (e.g. `ItemUsedBytes` as percentage line chart).
5. Save — watch live trend on the monitor face.

**Examples:** item usage % → `ItemUsedBytes` + percentage + line chart; fluid type pressure → `FluidUsedTypes`.

---

## 6. AE2 Crafting Monitoring

1. Connect **Crafting Linker** to the AE2 network with crafting CPUs online.
2. Right-click for CPU summary; bind on **Advance Data Monitor**.
3. Open crafting CPU config — choose **whole network** or per-CPU template mode.
4. Tune text scale, alpha, alignment, and template string.

**Template syntax:** `{br}` newline; `{variable}` value; `{var:CPUName}` per CPU; `{expr ? "yes" : "no"}` conditional.

Example:

```text
{br}Crafting{br}Total: {totalCpus} Busy: {busyCpus}{br}{busyCpus:CPU#1 == 1 ? "§cCPU#1 working" : "§aCPU#1 idle"} Used: {usedStorage:CPU#1}
```

CPU names must match AE2 exactly or values show as `??`.

---

## 7. AE2 Advanced Storage Linker Tutorial

**Configure Advanced Storage Link Cell:** Cell Workbench → partition slots → optional Fuzzy / Inverter / Ore Filter / fluid markers → remove cell.

**Configure Advanced Storage Linker block:** cable to AE2 → open GUI → insert configured cells (up to 36).

**Monitor display:** bind Advanced Storage Linker coordinates → storage display config → set `storageColumns`, `storageSpacing`, `storageIconScale`, count/delta/name toggles.

---

## 8. AI Chat & Assistant

Open via **AI** on the monitor GUI or **O** key (default). Supports free chat and structured AE2 actions when `ai.networkEnabled=true` and an API key is set (`ai.apiKey` or `DEEPSEEK_API_KEY`). Without AI, a local keyword parser handles limited English/Chinese commands.

**Privacy:** first use requires confirming `ai.privacyConfirmed=true`; voice has separate `voice.privacyConfirmed`.

| Action | Requires within 32 blocks | Example prompt |
|--------|---------------------------|----------------|
| Query storage | Advanced Storage Linker | "How much tin do I have in AE?" |
| Query recipes | Crafting Linker | "How do I craft iridium plates?" |
| Craft order | Crafting Linker | "Order 64 tin gears" |
| Batch craft | Crafting Linker | "Order 64 tin gears, 32 copper wire" |
| Withdraw | Advanced Storage Linker | "Withdraw 64 tin ingots to inventory" |
| Teleport | **Advanced Dislocator** in inventory | "List my teleport points" / "Teleport to base" |
| Plans | — | "Add plan: check iridium line tomorrow" |

**Limits:** max `4096` per line (`assistant.maxOrderAmount` / `maxWithdrawAmount`); max 8 tasks per AI parse; batch craft needs enough free AE2 calculation slots.

**Teleport:** scans Draconic Evolution **Advanced Dislocator** (`TeleporterMKII`) destinations. If none: *No Advanced Dislocator found in inventory, or no saved destinations.* (`adm.ai.assistant.no_teleport_destinations`)

**Cancel:** type `cancel` — clears pending candidates and sends server craft-cancel; chat Cancel button only aborts HTTP, not AE2 jobs.

Full intent list: [AI Assistant Development Guide](../ai-assistant/development-guide.md).

---

## 9. Voice Assistant

Records PCM → WAV → STT → same assistant pipeline as text chat.

Enable `voice.enabled=true`. STT key priority: `voice.sttApiKey` → `VOICE_STT_API_KEY` → `ai.apiKey` → `DEEPSEEK_API_KEY`.

| `voice.sttMode` | Behavior |
|-----------------|----------|
| `embedded-vosk` (default) | Offline Chinese model on 64-bit Windows Java; no extra services |
| `http` | OpenAI-compatible `/v1/audio/transcriptions` (e.g. Whisper) |

**V** key toggles recording. Transcript opens AI chat if closed. Do not read secrets aloud — audio may leave your machine in HTTP mode.

---

## 10. Commands

### `/admai` (aliases: `/adm-ai`, `/aicfg`)

```text
/admai status
/admai key sk-xxxx
/admai clearKey
/admai model deepseek-chat
/admai base https://api.deepseek.com
/admai provider openrouter
/admai network on|off|toggle
/admai search on|off|auto|openrouter|...
```

Providers include `deepseek`, `openai`, `openrouter`, `dashscope`, `zhipu`, `kimi`, `volcengine`, `siliconflow`, `minimax`, `groq`, `mistral`, `gemini`, `anthropic`.

### `/admassistant` (aliases: `/adm-assistant`, `/admast`)

```text
/admassistant lexicon
/admassistant reloadLexicon   # op level 2
```

---

## 11. Configuration Reference

**Path:** `.minecraft/config/advancedatamonitor/advancedatamonitor.cfg` (client) or `config/advancedatamonitor.cfg` (server).

| Section | Key fields |
|---------|------------|
| `general` | `greeting` — log-only |
| `ai` | `apiBaseUrl`, `apiKey`, `model`, `networkEnabled`, `webSearchEnabled`, `webSearchMode`, `streamingEnabled`, `privacyConfirmed`, `timeoutSeconds`, `maxTokens`, `temperature` |
| `voice` | `enabled`, `privacyConfirmed`, `sttMode`, `sttBaseUrl`, `sttApiKey`, `sttModel`, `sttTimeoutSeconds` |
| `assistant` | `maxOrderAmount`, `maxWithdrawAmount`, `craftJobTimeoutSeconds`, `maxConcurrentCraftJobs` |
| `[grapple]` | hint range, interact distance, travel speed — see grapple subsystem doc |
| `[dataLoomCell]` / per-cell sections | energy drain, sync interval, base rates |

In-game **AI Settings** saves some fields; server admins editing `craftJobTimeoutSeconds` or `maxConcurrentCraftJobs` should edit cfg directly. Config key descriptions: [Technical Documentation](../developer/technical-documentation.md).

---

## 12. Assistant Lexicon

Runtime file: `assets/advancedatamonitor/config/assistant-lexicon.json` (path shown by `/admassistant lexicon`). Used for keyword fallback when AI is offline, batch merge/confirm phrasing, and time/quantity parsing. Reload with `/admassistant reloadLexicon`.

---

## 13. AI Providers & Search Modes

Built-in provider profiles cover major OpenAI-compatible hosts. `webSearchMode`: `auto`, `off`, `openai`, `openrouter`, `dashscope`, `zhipu`, `generic-tools`. Actual search support depends on the provider API.

---

## 14. Common Use Cases

| Goal | Steps |
|------|-------|
| AE storage trend chart | Network Linker → bind `ItemUsedBytes` % → line chart, `interval=100`, `dataLimit=200` |
| Crafting overview wall | Crafting Linker → whole-network template → tune text scale/offset |
| Material stock wall | Configure cells → fill **Advanced Storage Linker** → bind with `storageColumns=4` |
| Base tour | Place **Grapple Anchor** nodes → **Grapple Hook** attach/glide (see [§3.7](#37-grapple-anchor)) |
| AI craft batch | Stand near **Crafting Linker** → natural language order → confirm by number or `confirm` |
| AI withdraw | Near **Advanced Storage Linker** → "Withdraw 64 tin ingots" → confirm |

---

## 15. Troubleshooting

| Symptom | Checks |
|---------|--------|
| Blank monitor | Entry enabled? Screen visible? `scale` too small? Wrong coordinates? Chunk loaded? |
| Chart stuck at 0 | Wrong `name`; use **Data Imprint Tool** NBT viewer; AE metric spelling; chunk loaded |
| Network Linker empty | Cabled to AE2? Channel? Drives present? Right-click chat output? |
| No CPUs on Crafting Linker | Same network as CPUs? Valid multiblock? Name matches template? |
| Advanced Storage Linker empty | AE2 online? Cell partitions set? Cell inserted in linker GUI? Inverter card inverted filter? |
| No API key | `/admai key` or cfg `ai.apiKey`; `networkEnabled=true` |
| AI chats but no AE2 actions | **Advanced Storage Linker** / **Crafting Linker** within 32 blocks, channeled |
| Batch craft failed | Missing candidate line; over `maxOrderAmount`; insufficient calc slots |
| Voice dead | `voice.enabled`; privacy confirmed; STT mode/key; mic permissions |
| Teleport failed | **Advanced Dislocator** in inventory with saved destinations |
| Withdraw 0 / error | Stock insufficient; backpack full; `maxWithdrawAmount`; link online |

---

## 16. Server Admin Notes

- Avoid many monitors at `interval=1`.
- Cap `assistant.maxOrderAmount` on public servers.
- Tune `maxConcurrentCraftJobs` and `craftJobTimeoutSeconds` for TPS.
- Disclose that AI/voice may send player text/audio to external APIs.
- Never ship real API keys in public pack configs.
- Disable `ai.debugLogging` in production when possible.
- Balance craft recipes for linker blocks against their automation value.

---

## 17. Known Limitations

- No built-in survival recipes in mod code.
- AE2 link search radius for assistant: **32 blocks** (hard-coded).
- AI structured parse: max **8** tasks; one bad task fails entire AI parse (falls back to rules).
- Withdraw needs backpack space; partial withdraw pauses batch operations.
- Chat history is client-only (not saved across sessions).
- Storage/craft query replies are formatted text, not GUI widgets.
- Batch merge keys on natural-language target strings, not normalized item IDs.
- Network Linker scans standard ME Drive/Chest cells — exotic storage may be incomplete.
- Crafting templates may omit non-item pattern I/O on some AE2 API paths.
- HTTP voice mode requires network; `embedded-vosk` is offline but Chinese-focused by default.

---

## 18. Quick Reference

### Core blocks & items

Advance Data Monitor · Data Imprint Tool · Network Linker · Crafting Linker · Advanced Storage Linker · Advanced Storage Link Cell · Grapple Anchor · Grapple Hook · Data Dust/Form/Flow/Tide/Source Loom Cell · Weave Amplifier Card · Super Weave Amplifier Card · Advance Planner · AdvanceDataMonitor Manual · Super Orange · Empyrean Holy Judgment

### Commands

```text
/admai status
/admai key <apiKey>
/admai provider <provider>
/admai network on|off|toggle
/admai search on|off|auto|openrouter|openai|dashscope|zhipu|generic-tools
/admassistant lexicon
/admassistant reloadLexicon
```

### Config keys

```text
ai.apiBaseUrl
ai.apiKey
ai.model
ai.networkEnabled
ai.webSearchEnabled
ai.webSearchMode
voice.enabled
voice.sttBaseUrl
voice.sttApiKey
voice.sttModel
assistant.maxOrderAmount
assistant.maxWithdrawAmount
assistant.craftJobTimeoutSeconds
assistant.maxConcurrentCraftJobs
```

### Example AI prompts

```text
How much tin is in AE?
How do I craft iridium plates?
Order 64 tin gears
Order 64 tin gears, 32 copper wire, 4 quantum processors
Withdraw 64 tin ingots to inventory
Get 32 copper plates from AE
List my teleport points
Teleport to my base
confirm
cancel
List plans
```

### Default keybinds

| Key | Action |
|-----|--------|
| O | Open AI chat |
| P | Open Advance Planner (when held) |
| H | Toggle planner HUD (when applicable) |
| V | Voice assistant record |

---

## 19. Advance Planner

> Developer API: [Technical Documentation §5.11](../developer/technical-documentation.md#511-advance-planner)

### 19.1 Overview

**Advance Planner** (`item.advancePlanner.name`) is a lightweight in-game todo list stored in item NBT. Record tasks, check them off, scroll long lists, merge multiple planner items, and optionally show a HUD overlay.

**Core features:**

- Multiple entries per item
- Text + completed flag per entry
- Scrollable list (up to 50 visible rows)
- Merge all planners in inventory into one
- On-screen HUD toggle

---

### 19.2 Obtaining

Creative tab **Tools** — search "Planner" or "Advance Planner".

| Display name | Registry ID |
|--------------|-------------|
| Advance Planner | `advancedatamonitor:advance_planner` |

---

### 19.3 Basic Operations

#### Open GUI

**Right-click** (without sneaking) while holding the planner.

#### Add entry

1. List shows up to 8 rows per page (scroll for more).
2. Click an empty row (*Click to add...* — `adm.planner.empty_slot`).
3. Type text → green **Add** button or **Enter**.
4. Red **X** or **Esc** cancels without creating a row.

> Empty confirm on a new row does nothing. Clearing an existing row's text and saving keeps the row with blank text.

#### Edit entry

Click existing text → edit → **Enter** or **Add**.

#### Toggle complete

Click the checkbox on the left. Completed rows turn **green** with strikethrough. Tooltip: *Mark as completed* / *Mark as incomplete*.

#### Scroll

Mouse wheel when more than 8 entries; scrollbar on the right.

---

### 19.4 GUI Layout

```
┌─────────────────────────────────────────┐
│           §lAdvance Planner              │  ← title (adm.planner.title)
│  Total N | Done X | Pending Y            │  ← adm.planner.stats
├─────────────────────────────────────────┤
│ ☐ #1  Gather 64 iron ingots      14:30  │
│ ☑ #2  Build warehouse            14:31  │  ← completed (green + strike)
│ ☐ #3  Craft diamond pickaxe      14:32  │
│ ☐ #4  Click to add...                   │
│ ...                                      │
├─────────────────────────────────────────┤
│     [ Merge ]              [ Exit ]      │
└─────────────────────────────────────────┘
```

| Element | Lang key / meaning |
|---------|-------------------|
| Title | `adm.planner.title` — **§lAdvance Planner** |
| Stats | `adm.planner.stats` — Total \| Done \| Pending |
| Empty slot | `adm.planner.empty_slot` — Click to add... |
| Merge | `adm.planner.merge` |
| Exit | `adm.planner.exit` |
| Add / Cancel | `adm.planner.add` / `adm.planner.cancel` |

---

### 19.5 Merge (Combine Multiple Planners)

When you carry **two or more** Advance Planners:

1. Open any planner.
2. Click **Merge** (`adm.planner.merge`).
3. Confirmation screen shows inventory planner count and total entries (`adm.planner.merge_prompt`).
4. Choose mode:
   - **Merge by Time** (`adm.planner.merge_by_time`) — sort all entries by timestamp
   - **Merge by Index** (`adm.planner.merge_by_index`) — append planners in inventory order
5. Click **Confirm Merge** (`adm.planner.confirm_merge`).

**Rules:**

- Entries renumber from 1; text, completion state, and timestamps preserved.
- Other planner items are **consumed** from inventory; only the opened one remains.
- **Cancel** returns without changes.

---

### 19.6 HUD Toggle

**Sneak + right-click** the planner toggles HUD overlay.

- On: chat shows **HUD Enabled** (`adm.planner.hud_enabled`, cyan).
- Off: **HUD Disabled** (`adm.planner.hud_disabled`).

Advanced HUD position/scale/width settings are available in the HUD Config screen (`adm.planner.hud_config`). Limits enforced by `[plannerHudLimits]` in config — see developer doc.

---

### 19.7 Lang Reference (EN)

| Key | English string |
|-----|----------------|
| `item.advancePlanner.name` | Advance Planner |
| `adm.planner.title` | §lAdvance Planner |
| `adm.planner.add` | Add |
| `adm.planner.delete` | Delete |
| `adm.planner.merge` | §lMerge |
| `adm.planner.confirm_merge` | §lConfirm Merge |
| `adm.planner.cancel` | §lCancel |
| `adm.planner.exit` | §lExit |
| `adm.planner.merge_confirm_title` | §lMerge Confirmation |
| `adm.planner.merge_by_time` | §lMerge by Time |
| `adm.planner.merge_by_index` | §lMerge by Index |
| `adm.planner.empty_slot` | Click to add... |
| `adm.planner.completed` | Completed |
| `adm.planner.pending` | Pending |
| `adm.planner.mark_complete` | Mark as completed |
| `adm.planner.mark_incomplete` | Mark as incomplete |
| `adm.planner.stats` | Total %s \| Done %s \| Pending %s |
| `adm.planner.merge_prompt` | Found %s planner(s) in inventory with %s total entries to merge |

---

### 19.8 Tips

1. **Separate planners** for build vs materials vs exploration — merge when consolidating.
2. **Strikethrough rows** give at-a-glance progress without deleting history.
3. **Review merge screen** entry counts before confirming — other items are consumed.
4. **HUD during big builds** — sneak+right-click once, keep tasks visible while placing blocks.
5. **Stack size 1** — each planner uses one hotbar slot.

---

### 19.9 FAQ

**Q: Merge button greyed out?**  
A: Need at least **2** Advance Planners in inventory.

**Q: Where did merged planners go?**  
A: Consumed except the one you had open. Merge is destructive to extra items.

**Q: Entry limit?**  
A: GUI scrolls up to **50** visible rows; storage limited by NBT size in practice.

**Q: Esc while editing?**  
A: Discards unsaved edit text for that session.

**Q: Close GUI mid-edit?**  
A: Pending edit auto-commits on close.

---

> This guide reflects the AdvanceDataMonitor source tree at time of writing. Report doc drift against [Technical Documentation](../developer/technical-documentation.md).
