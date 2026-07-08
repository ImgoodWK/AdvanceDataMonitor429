# TeXTech WebAE Console User Guide

> Audience: Players & server admins · Last synced: 2026-07  
> Developer docs: [Developer Guide](developer-guide.md) · Mod overview: [Player Guide](../player/player-guide.md)

---

## Table of Contents

- [1. Overview](#1-overview)
- [2. Enable & Configure](#2-enable--configure)
- [3. Get an Access Token](#3-get-an-access-token)
- [4. Browser Access](#4-browser-access)
- [5. Feature Pages](#5-feature-pages)
- [6. Important Notes](#6-important-notes)

---

## 1. Overview

The WebAE Console is a **browser-accessible** HTTP management panel embedded in TeXTech. Use any modern browser to view AE2 storage, power, GT machine states, search recipes, edit patterns, submit crafting orders, and chat with in-game players.

| Feature | Description |
|---------|-------------|
| Storage Monitor | Real-time AE2 item/fluid/essentia counts and byte usage |
| Crafting CPUs | Standalone menu for AE2 crafting CPU status and details |
| Power Monitor | Configurable EU/steam gauges, in/out rates, dual-series trend chart |
| GT Machines | Online GT machine status, progress, and recipes |
| Recipe Search | Fuzzy NEI search with merged/compact/detailed layouts |
| Pattern Manager | View, create, edit, and inject AE2 patterns into ME Interfaces |
| AE Orders | Pattern/item/**craft tree** orders with optional CPU selection and hybrid progress |
| Network Topology | Logical / spatial / **P2P channel** / **world map** views; simulated cables; CSV export |
| Monitor Bindings | Read-only chart slots; per-slot **line preview** Drawer |
| Automation Alerts | Storage/CPU/GT/order/channel alerts; **SSE** push + Toast |
| Chat | Web-to-in-game chat bridge with online player list |
| Dashboard | 24 color schemes × 5 layout presets, draggable widget grid |
| Command Upload | OPs run `/admweb recipes upload` and `/admweb icons upload` (no in-game keybind) |

Default URL: `http://127.0.0.1:8090` (port is configurable).

---

## 2. Enable & Configure

The Web Console is **disabled by default**. Edit `config/textech/textech.cfg` `[webConsole]` section:

```ini
[webConsole]
enabled=true
port=8090
bindAddress=127.0.0.1
refreshIntervalMs=1000
gtRefreshIntervalMs=10000
maxNetworksDisplayed=5
tokenLifetimeHours=0
maxRecipeCacheMB=256
recipeCacheMode=full
recipeUploadBatchesPerTick=3
recipeSearchMinIntervalMs=300
nesqlRepositoryPath=
neiDeepScanItemsPerTick=0
iconMissingDispatchPerTick=8
powerSampleWindowSeconds=60
gtDefaultScanRadius=16
recipeUploadEnabled=true
iconCacheEnabled=true
iconUploadEnabled=true
iconPackEnabled=true
```

**Restart the server** after changes. Key options:

- `refreshIntervalMs`: unified server collection and frontend polling interval (ms), default 1000, min 1000.
- `gtRefreshIntervalMs`: GT machine collection interval (ms), default 10000.
- `maxNetworksDisplayed`: max AE2 networks shown at once (1–20, default 5).
- `tokenLifetimeHours`: token TTL in hours; 0 = never expire.
- `recipeCacheMode`: `full` (GTNH default, no LRU eviction) or `lru` (evict when `maxRecipeCacheMB` exceeded).
- `nesqlRepositoryPath`: NESQL repo root for `/admweb icons import-nesql`. **When empty**, defaults to `<instance>/TeXTech/WebAE/` (`.minecraft/TeXTech/WebAE/` on client; same folder name under server root on dedicated servers; same as client recipe export).
- `bindAddress=127.0.0.1` is localhost only; set `0.0.0.0` for LAN (use a firewall).

Full config reference: [Developer Guide §4](developer-guide.md#4-configuration).

---

## 3. Get an Access Token

The Web Console requires token authentication. Use commands in-game (or from server console):

| Command | Description |
|---------|-------------|
| `/admweb issue` | Issue an **owner** token (requires at least one Advance Data Monitor you own) |
| `/admweb guest <player>` | Monitor owner sends a **guest** token privately to an **online** player |
| `/admweb list` | List tokens with type, owner, actor (OP only) |
| `/admweb revoke [guestName]` | Revoke your owner token; owners revoke guest tokens; OP can revoke others |
| `/admweb reload` | Reload TeXTech config; `enabled`/`port`/`bindAddress` still need restart (OP only) |
| `/admweb refresh [network]` | Admin force re-collect snapshots (OP only) |
| `/admweb recipes upload [snapshot\|deep]` / `export` | **OP** triggers client NEI collection and upload; also writes `<instance>/TeXTech/WebAE/web-recipes.json.gz` on the client; `snapshot` = storage-related items only (recommended daily); `deep` = full NEI item scan (slow) |
| `/admweb icons import-nesql [pack] [subpath]` | **OP** imports pre-rendered PNGs from `nesqlRepositoryPath` (default `TeXTech/WebAE/`; incremental) |
| `/admweb recipes status` | Show recipe cache status (incl. disk size) |
| `/admweb recipes clear` | Clear recipe memory + disk cache (OP only) |
| `/admweb icons upload [packName]` | **OP** triggers own client icon render/upload |
| `/admweb icons status` | List installed icon packs and config state |
| `/admweb help` | Show usage |

**Token types**

- **Owner token**: bound to the monitor owner UUID; read/write all AE networks linked to their monitors; **owner need not be online**.
- **Guest token**: issued via `/admweb guest`; nearly the same AE access (operations still use owner identity); chat shows the guest name.
- Tokens persist in `TeXTech/WebAE/web-tokens.json`; legacy entries migrate to `type: owner` on load.

**Offline access & chunks**

- Only monitors in **loaded chunks** are discovered; `monitorDim` reflects the monitor’s dimension.
- Keep bases chunk-loaded (loader or players nearby) or the web network list may be empty.

---

## 4. Browser Access

1. Confirm the server is running with `enabled=true`
2. Open `http://127.0.0.1:8090` (or your configured address/port)
3. Enter the token from `/admweb issue` on the login page
4. The token is stored in browser `localStorage`; **auto-login** (default on) reconnects on next visit
5. If the token expires or is revoked, the login page clears it — re-issue with `/admweb issue`
6. To switch tokens, enter a new one in Settings for immediate reconnect
7. Disable auto-login in Settings if preferred

---

## 5. Feature Pages

### Storage Monitor

Configurable overview widget row (GridStack edit mode) plus items/fluids/essentia sub-tables. Column sort, search, and multi-network Split/Merged aggregation in the top bar.

### Crafting CPUs

Standalone sidebar menu with configurable overview and CPU table. Click a row for a detail drawer. Multi-network Split mode uses tabs.

### Power Monitor

Full-page editable GridStack widget grid (EU gauge, in/out rates, steam bar, dual-series trend chart, etc.). **Edit layout** to drag and add/remove widgets. Split mode uses tabs; auto-refresh keeps the trend chart mounted without flicker.

### GT Machines

Lists online machines with name, progress, recipe, and input/output slots. Filter, search, and sort supported.

### Recipe Search

Auto-loads recipe overview on open; fuzzy search, category multi-select, Full/Merged and Compact/Detailed layouts. OP must run `/admweb recipes upload snapshot` (recommended) or `upload` (full) before search works. Collection also writes `.minecraft/TeXTech/WebAE/web-recipes.json.gz` on the client for offline backup or external tools.

### Item Icons & Texture Packs

Real game icons in tables and recipes; abbreviation fallback on failure. OP runs `/admweb icons upload [packName]` or `/admweb icons import-nesql` (defaults to `TeXTech/WebAE/`; override with `nesqlRepositoryPath`); Settings page switches packs; admins can upload zip. Missing icons lazy-load via SSE `icon-ready`.

### Local data folder `TeXTech/WebAE/`

| Path | Purpose |
|------|---------|
| Client `.minecraft/TeXTech/WebAE/web-recipes.json.gz` | NEI recipe gzip cache written after `/admweb recipes upload*` |
| Server `<instance>/TeXTech/WebAE/` (or configured `nesqlRepositoryPath`) | NESQL pre-rendered PNGs for `/admweb icons import-nesql` (often under `images/`) |

The folder is created automatically on first use.

### Pattern Manager

Left: pattern list with search and batch delete. Right: 9×3 input grid and outputs with multiplier controls. Encoding consumes a blank pattern from the AE network; inject into ME Interfaces.

### Crafting Orders

Optional CPU selector at top. **By pattern** tab: paginated Grid + Interface browse with virtual scroll and batch orders. **By item** tab: storage search and batch orders. **Craft tree** tab: enter registry name and quantity; recursively expands recipe chain and shows storage gaps. Active/history orders poll every 3s with hybrid AE2 + time-estimate progress.

### Network Topology

Sidebar **Network Topology** offers logical grouping, spatial bins, **P2P channels**, and **world map** views. Logical/spatial use simulated star fake cables (not real AE routing); P2P view lists tunnel endpoints grouped by frequency. CSV export supported.

#### World Map View

1. **Prerequisite**: Capture a logical topology snapshot first (POST `/api/network/topology/snapshot` or the in-page **Capture snapshot** button).
2. **Terrain tiles**: flat (top-down) and oblique views; four quality tiers via Segmented control (low 64px / medium 128px / high 256px / ultra 512px HD).
3. **Loading progress**: Toolbar shows overall progress (`completed/total layer jobs`, scoped to current network·view·quality); each chunk displays loading / ready / error badges (`WorldMapChunkStatusOverlay`); subtle hint text appears in the toolbar and bottom-left of the map while loading.
4. **AE overlay**: Toggle AE overlay to show device icons and cable positions; prefetched independently from terrain.
5. **Refresh & invalidation**: Tiles auto-invalidate when switching networks or capturing a new snapshot; OP can POST `/api/worldmap/invalidate` to force rebuild.
6. **Config**: `[webConsole] worldMapEnabled`, `worldMapMaxQualityTier` (default ultra), `worldMapDefaultQualityTier` (default medium), `worldMapBoundsPaddingChunks` (default 1). See [Developer Guide §4](developer-guide.md#4-configuration) and [§11.26](developer-guide.md#1126-world-map-view-phase-ab--ae-overlay).

### Monitor Bindings & Preview

Sidebar **Monitor Bindings** shows read-only chart slots and GT binding coords. Click **Preview** on a slot to open a line chart Drawer mirroring in-game monitor data (edit remains in-game).

### Automation Alerts

Driven by `TeXTech/WebAE/web-alerts.json` (inventory, stuck CPU, GT errors, order complete, channel overload). Besides 10s polling, the browser connects to SSE (`/api/events/stream`) for real-time alerts; connection pauses when the tab is hidden.

### Mobile & PWA

Includes `manifest.webmanifest` and responsive CSS for narrow screens. You can add the console to your phone home screen (still prefer SSH tunnel access; do not expose raw to the public internet).

### Dashboard, Chat & Settings

- **Dashboard**: GridStack drag layout, 24 themes + 5 layouts, settings drawer for spacing/colors/charts.
- **Chat**: 💬 icon in sidebar; web messages broadcast in-game as `[Web] <name>: content`.
- **Sidebar**: edge button cycles Expanded → Collapsed → Hidden.
- **Top bar**: fixed-width refresh countdown/status next to connection dot.

---

## 6. Important Notes

- **Localhost only by default**: `bindAddress=127.0.0.1` restricts access to the local machine
- **LAN access risk**: `0.0.0.0` exposes the console to the LAN — use a firewall or SSH tunnel
- **Mandatory auth**: all `/api/` endpoints require a token; admin force-refresh requires OP
- **Token security**: tokens grant storage view and crafting submit — store securely
- **Recipe upload required**: OP runs `/admweb recipes upload` before recipe search works
- **Icon upload**: OP runs `/admweb icons upload [packName]`; frontend auto-selects the server's most recent pack on first load
- **reload limits**: `/admweb reload` does not rebind the web server; tokens and runtime data files are unaffected

---

> This guide reflects the TeXTech source tree at time of writing.
