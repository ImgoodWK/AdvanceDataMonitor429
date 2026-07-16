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
| Dashboard | 111 color schemes × 30 layout presets × 109 page styles (bold + batch2 + batch3, including 22 structural layouts), thumbnail preview tiles in Settings; draggable widget grid |
| Storage / Fluids / Essentia | Dedicated sidebar pages; Storage also has item/fluid/essentia sub-tables |
| Crafting CPUs | Standalone menu for AE2 crafting CPU status and details |
| Power Monitor | Configurable EU/steam gauges, in/out rates, dual-series trend chart |
| GT Machines | Online GT machine status, progress, and recipes |
| Recipe Search | After OP upload, click **Fetch recipes** to sync into browser IndexedDB; local fuzzy search with merged/compact/detailed layouts |
| Pattern Manager | View, create, edit, and inject AE2 patterns into ME Interfaces |
| AE Orders | Pattern/item/**craft tree** single orders with optional CPU selection and real AE2 progress |
| Network Topology | Logical / spatial / **P2P channel** / **world map** views; abstract tree/star layouts; CSV export |
| Quest Book | BetterQuesting lines/graph/submit assist (requires BQ) |
| Link Scanner | Browse in-game scanner results, aliases, and coords |
| Monitor Bindings | Read-only chart slots; per-slot **line preview** Drawer |
| Planner | Sync Advance Planner entries in the browser |
| AI Assistant | Web chat entry (same capabilities as in-game assistant) |
| Alerts History | Browse triggered automation alerts; rules via Settings/alerts editor |
| Chat | Web-to-in-game chat bridge with online player list |
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
recipeSearchMinIntervalMs=1000
recipeKeepMemoryAfterUpload=false
recipeSyncChunkSize=400
nesqlRepositoryPath=
neiDeepScanItemsPerTick=0
iconMissingDispatchPerTick=8
iconDirectRenderEnabled=false
iconDirectRenderTimeoutMs=3000
iconDirectRenderPerTick=4
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
- `recipeCacheMode`: `full` (GTNH default, no LRU eviction) or `lru` (evict when `maxRecipeCacheMB` exceeded; only while server memory is loaded).
- `recipeKeepMemoryAfterUpload`: keep full recipes in server heap after upload/save; default `false` (clear heap; browsers sync via **Fetch recipes**).
- `recipeSyncChunkSize`: recipes per browser-sync chunk (default 400).
- `nesqlRepositoryPath`: NESQL repo root for `/admweb icons import-nesql`. **When empty**, defaults to `<instance>/TeXTech/WebAE/` (`.minecraft/TeXTech/WebAE/` on client; same folder name under server root on dedicated servers; same as client recipe export).
- `bindAddress=127.0.0.1` is localhost only; set `0.0.0.0` for LAN (use a firewall).

Full config reference: [Developer Guide §4](developer-guide.md#4-configuration).

---

## 3. Get an Access Token

**Command index**: run `/textech help` in-game (aliases `/adm help`, `/txt help`) for all TeXTech commands; use `/xxx help` on each command for full usage (follows game language: en/zh).

The Web Console requires token authentication. Use commands in-game (or from server console):

| Command | Description |
|---------|-------------|
| `/admweb issue` | Issue an **owner** token (requires at least one Advance Data Monitor you own) |
| `/admweb login` | Generate a **6-digit browser login code** (5 min TTL, single use; no OP token required) |
| `/admweb guest <player>` | Monitor owner sends a **guest** token privately to an **online** player |
| `/admweb copy` | Copy your active token to clipboard |
| `/admweb list` | List tokens with type, owner, actor (OP only) |
| `/admweb revoke [guestName]` | Revoke your owner token; owners revoke guest tokens; OP can revoke others |
| `/admweb reload` | Reload TeXTech config; `enabled`/`port`/`bindAddress` still need restart (OP only) |
| `/admweb refresh [network]` | Admin force re-collect snapshots (OP only) |
| `/admweb server status` | Show WebAE HTTP server state |
| `/admweb server restart` | Restart HTTP server (OP only) |
| `/admweb recipes upload [snapshot\|deep]` / `export` | **OP** triggers client NEI collection and upload to server disk; also writes `<instance>/TeXTech/WebAE/web-recipes.json` on the client; web UI still needs **Fetch recipes**; `snapshot` = storage-related items only (recommended daily); `deep` = full NEI item scan (slow) |
| `/admweb recipes status` | Show recipe cache status (incl. disk size) |
| `/admweb recipes clear` | Clear recipe memory + disk cache (OP only) |
| `/admweb icons upload [pack]` / `upload snapshot [pack]` | **OP** triggers client icon render/upload (always nei) |
| `/admweb icons render <itemId> [pack]` | **OP** render and upload a single item icon |
| `/admweb icons verify <itemId> [pack]` | Open icon verify GUI |
| `/admweb icons import <folder> [pack]` | **OP** import PNGs from a local folder |
| `/admweb icons import-nesql [pack] [subpath]` | **OP** imports pre-rendered PNGs from `nesqlRepositoryPath` (default `TeXTech/WebAE/`; incremental) |
| `/admweb icons modes` | List icon render mode (nei only) |
| `/admweb icons status` | List installed icon packs and config state |
| `/admweb icons clear` | Delete all icon packs (OP only) |
| `/admweb worldmap upload [networkId]` | Upload world map snapshot (must be near AE network; client capture) |
| `/admweb worldmap accept <requestId>` | Accept a guest/web map upload request (legacy) |
| `/admweb wm y [id]` | **Recommended** accept upload; id optional; same as clicking Accept in chat |
| `/admweb wm n [id]` | Decline upload request |
| `/admweb wm up [networkId]` | Same as `worldmap upload` |
| `/admweb wm st [networkId]` | Same as `worldmap status` |
| `/admweb worldmap status [networkId]` | Map snapshot capture status |
| `/admweb worldmap status [networkId]` | Show map snapshot capture status |
| `/admweb help` | Show usage (incl. recipes/icons/worldmap/server grouped help) |

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

Recipes are not kept in server heap for the browser to query continuously. Flow: **OP uploads in-game → server writes disk → player clicks Fetch recipes on the Recipes page → chunks land in this browser’s IndexedDB → local browse/search**.

- On the Recipes page, click toolbar **Fetch recipes** when a new server revision is available (progress bar; cancellable). Then use fuzzy search, category multi-select, Full/Merged and Compact/Detailed layouts against the local store; no automatic re-download while revision is unchanged.
- OP must run `/admweb recipes upload snapshot` (recommended) or `upload` (full) first; collection also writes `.minecraft/TeXTech/WebAE/web-recipes.json` on the client (plain JSON backup).
- Changing browsers or clearing site data requires Fetch again. Server `/admweb recipes clear` does not wipe browser IndexedDB.

### Item Icons & Texture Packs

Real game icons in tables and recipes; abbreviation fallback on failure. Loading uses three tiers: **browser IndexedDB → server disk cache → async client fill on miss (SSE refresh)**.

- **Local first**: IndexedDB / local pack hits never need the server; in-game `/admweb icons upload` or import fills server disk for on-demand requests.
- **Auto-sync (off by default)**: Settings → “Auto-sync server icon pack” bulk-downloads into IndexedDB when the pack revision changes (for admin custom packs; not needed for normal browsing).
- **Manual fetch**: Settings → **Sync full pack** downloads the server pack; **Fill visible missing** requests only icons currently on screen that are missing locally. Local ZIP import (browser-only) remains available.
- **Server cache**: OP runs `/admweb icons upload [packName]` or `/admweb icons import-nesql` (defaults to `TeXTech/WebAE/`; override with `nesqlRepositoryPath`); Settings switches packs; admins can upload zip. Render mode is fixed to **`nei`** (NESQL-style `GuiContainerManager.drawItem` single-icon FBO; fluids use mod specials); other modes remain in code as `@Deprecated` archival only — commands no longer take a mode argument.
- **Async fill (default)**: Disk miss returns immediately and enqueues the lazy queue (`iconUploadEnabled`); client render/upload then SSE `icon-ready` so abbreviations upgrade to icons. Synchronous direct render (`iconDirectRenderEnabled`, default **false**) is for debugging only.
- **Multiplayer tip**: Run `/admweb icons upload snapshot` once after startup; players rely on local/on-demand loads and may manually sync a full pack in Settings when needed.

### Local data folder `TeXTech/WebAE/`

| Path | Purpose |
|------|---------|
| Client `.minecraft/TeXTech/WebAE/web-recipes.json` | NEI recipe JSON written after `/admweb recipes upload*` |
| Server `TeXTech/WebAE/web-recipes.json` + `.meta.json` + `recipe-chunks/` | Server authoritative cache; browsers pull chunks via **Fetch recipes** |
| Server `<instance>/TeXTech/WebAE/` (or configured `nesqlRepositoryPath`) | NESQL pre-rendered PNGs for `/admweb icons import-nesql` (often under `images/`) |

The folder is created automatically on first use.

### Pattern Manager

Left: pattern list with search and batch delete. Right: 9×3 input grid and outputs with multiplier controls. Encoding consumes a blank pattern from the AE network; inject into ME Interfaces.

### Crafting Orders

Optional CPU selector at top. **By pattern** tab: paginated Grid + Interface browse with virtual scroll and single orders. **By item** tab: storage search and single orders. **Craft tree** tab: enter registry name and quantity; recursively expands recipe chain and shows storage gaps (view only; no batch order). Active/history orders poll every 3s; progress comes from AE2 `ICraftingLink` and CPU craft-tree step counters (same as in-game CPU GUI, **not** final-output count). In-game cancelled jobs appear in history as cancelled. History rows offer **Reorder** with a confirmation dialog before resubmitting.

### Network Topology

Sidebar **Network Topology** offers logical grouping, spatial bins, **P2P channels**, and **world map** views. Logical/spatial use abstract tree or double-ring layouts (not real AE routing). The former cable-simulation view is deprecated (`topologySimulatedEnabled`, default off). P2P view lists tunnel endpoints grouped by frequency. CSV export supported.

#### World Map View

1. **Prerequisite**: Capture a logical topology snapshot first (POST `/api/network/topology/snapshot` or the in-page **Capture snapshot** button).
2. **Terrain source dual mode**:
   - **Self-rendered** (self): Server-side UV/ray-traced tiles; flat (top-down) and oblique views; four quality tiers via Segmented control (low 64px / medium 128px / high 256px / ultra 512px HD). **First open or cold cache is slow**; without GWM/Dynmap (`auto` falls back to self) expect slow renders and mediocre GT textures. Keep `worldMapTerrainSource=auto` on GTNH packs for instant GWM tiles.
   - **Dynmap terrain** (dynmap): External Dynmap/GWM pre-rendered tiles via Leaflet, plus an **ultra-quality AE chunk tint overlay** (`WorldMapAeOverlayStack`) and optional dot markers; quality controls mainly affect the AE layer in dynmap mode.
   - **Client GL priority** (`worldMapClientCaptureMode=when_online`, default): When the player is online in the target dimension, all quality tiers prefer client `RenderBlocks` FBO capture; nearby chunks are pre-warmed while exploring (`worldMapClientCaptureRadius`).
   - **Progressive placeholder** (`worldMapProgressiveFallback=true`, default): While the target tier renders, serve lower cached tiers or a Dynmap 128-block crop preview (`X-WorldMap-Tile-Status: upgrading`) instead of stripe placeholders.
   - Server auto-detect (`auto`) or force-select via `worldMapTerrainSource`; settings drawer shows terrain source, client capture mode, and online status.
3. **Loading progress**: Both modes show toolbar progress (`completed/total layer jobs`, scoped to current network·view·quality); each chunk displays loading / ready / error badges (`WorldMapChunkStatusOverlay`); subtle hint text appears in the toolbar and bottom-left of the map while loading.
4. **Device list FAB**: Top-right **Device list** opens a modal without resizing the map.
5. **Markers & popup**: Click a device icon or cluster count to open a **device thumbnail list** (detail-page types only: interface, drive, CPU, buses, chest, security terminal, level maintainer, controller, IO port, quantum bridge, energy, P2P, etc.; terminals/monitors/emitters/pattern providers are excluded). Click a row to open the **detail drawer**. Cluster open zooms the map 1.5× temporarily; closing the popup restores scale. Pan/zoom does not dismiss the popup; wheel inside the list scrolls the list only. **Hover a cluster count** to see each device type icon with quantity (xN). **Crafting CPUs and ME controllers** are multiblocks: each structure counts as **one device** in cluster totals (the detail drawer still lists every block coordinate).
6. **Left AE legend rail**: A narrow color strip on the left edge; hover to expand category names, visibility checkboxes, and color pickers (tints both AE overlay and marker icon borders). **Unchecking a category keeps the legend row** (shown dimmed) and hides only the matching device icons on the map. Lock makes controls read-only (hover-out still collapses); category swatches use the same icon IDs as map device markers (first device in that category), while **Other** shows a configured color swatch only. The toolbar **palette** button was removed.
7. **Local cache**: Browser IndexedDB tile cache; client also syncs snapshots to `TeXTech/WebAE/map-cache/` when logging in from another device.
8. **AE overlay**: Toggle in topology settings; **opacity** slider (0.5–1.0) affects AE tint pixels only, not terrain.
9. **Wheel isolation**: Wheel over world map or tree graph zooms the view instead of scrolling the page.
10. **Refresh & invalidation**: Tiles auto-invalidate when switching networks or capturing a new snapshot; OP can POST `/api/worldmap/invalidate` to force rebuild.
11. **Config**: `[webConsole] worldMapEnabled`, `worldMapTerrainSource` (`auto`/`dynmap`/`self`), `dynmapTileRoot`, `worldMapClientCaptureMode` (`off`/`ultra_only`/`when_online`), `worldMapClientCaptureRadius`, `worldMapProgressiveFallback`, `worldMapMaxQualityTier` (default ultra), `worldMapDefaultQualityTier` (default medium), `worldMapBoundsPaddingChunks` (default 1). See [Developer Guide §4](developer-guide.md#4-configuration) and [§11.26](developer-guide.md#1126-world-map-view-phase-ab--ae-overlay).

### Quest Book (BetterQuesting)

Requires the **BetterQuesting** mod. Sidebar **Quest Book** (`?page=quests`) uses a **left quest-line | center graph | right fixed detail panel** layout (on screens ≤768px wide, details still open in a drawer). The toolbar **Task list** button opens a drawer with search and filters.

- **Graph nodes**: quest item icons; main quests (`isMain`) use diamond frames, normal quests rounded rectangles; hover highlights related nodes/edges and shows a quest-name tooltip (independent of the persistent “Show quest names” label toggle); optional **Hide completed**; cross-chapter prerequisites appear as dashed **ghost** nodes. The toolbar provides **zoom in / zoom out / fit view** and **Settings** (node spacing, node size, labels, sidebar, edges, etc.; stored in browser localStorage); **wheel** zooms at the pointer inside the graph (does not scroll the page); drag to pan. Node names on the graph can be toggled separately; labels show stripped text colored by the first `§` color code.
- **§ format codes**: quest titles, descriptions, and step names support Minecraft `§` colors and styles (e.g. `§a§l`); the detail panel and lists render full styling; search matches ignore format codes.
- **Quest detail**: clicking a node opens a **fixed right sidebar** (no extra drawer on desktop). Sections appear in order: **related quests** (prerequisites / unlocks), requirements (from BetterQuesting `tasks`), and reward preview (from `rewards`, one row per item when a reward grants multiple stacks). Clicking a prerequisite or follow-up quest switches quest lines when needed and centers the node; implicit/hidden prerequisites show tags. Rewards must still be claimed in-game. Panel width is configurable in Settings (default 380px).
- **Refresh**: load once on enter / chapter switch into local cache; **no background polling**. Manual refresh has a **30s cooldown**; progress is force-refreshed before submit actions.
- **Read-only for guests**: browse lines and party progress; **rewards cannot be claimed on Web** (UNCLAIMED prompts in-game claim).
- **Web-assisted steps**: item/fluid submit and Retrieval / fluid hold-detect (completed from AE stock; **does not** put items/fluids into the player inventory). Submit and craft-then-submit use an **AE virtual escrow** to lock required stacks before submit/detect; failure or timeout returns them to the network. Click a single step to submit; **Chain submit** walks prerequisites in topological order (configurable). The submit panel shows AE stock vs requirement; **Craftable** appears only when a matching AE pattern exists and the material chain can be satisfied.
- **Offline**: works when the owner is offline via Token + FakePlayer (progress is written to the **Token owner** questing UUID). Requires a running server and a resolvable AE network (Link / loaded chunks), same as other WebAE AE actions.
- **Parties**: Web submit **does not** complete teammates' quests—only the Token owner is updated. BQ forces FakePlayer to solo; TeXTech does not wire PartyManager / SyncPartyQuests.
- **Config**: `[webConsole] questEnabled`, `questSubmitEnabled`, `questChainSubmitEnabled` (default true), `questSubmitMaxStacks`, `questCraftWaitTimeoutMs`, `questEscrowEnabled` (default true), `questEscrowTimeoutMs` (default 120000), `questCacheTtlSec` in `textech.cfg`.

### Monitor Bindings & Preview

Sidebar **Monitor Bindings** shows read-only chart slots and GT binding coords. Click **Preview** on a slot to open a line chart Drawer mirroring in-game monitor data (edit remains in-game).

### Link Scanner / Planner / AI Assistant

- **Link Scanner**: browse results from the in-game Advance Link Scanner (owner/name filters and coordinates; teleport remains in-game).
- **Planner**: sync Advance Planner entries for browsing and progress checks in the browser.
- **AI Assistant**: web chat entry with the same capabilities as the in-game assistant (requires AI config; AE actions still need a nearby Advanced Network Linker).

### Automation Alerts & History

Driven by `TeXTech/WebAE/web-alerts.json` (inventory, stuck CPU, GT errors, order complete, channel overload). Besides 10s polling, the browser connects to SSE (`/api/events/stream`) for real-time alerts; connection pauses when the tab is hidden. Sidebar **Alerts History** lists past triggers.

### Mobile & PWA

Includes `manifest.webmanifest` and responsive CSS for narrow screens. You can add the console to your phone home screen (still prefer SSH tunnel access; do not expose raw to the public internet).

### Dashboard, Chat & Settings

- **Dashboard**: GridStack drag layout, **111** color schemes + **30** layouts (incl. bottom nav / floating sider / split chrome, plus batch3 dual-rail / dock / theater / HUD / corner-hub structural variants) + **109** page styles (restrained hexcell / arc-reactor without content clipping, plus batch2 / batch3 packs; Settings uses near-real thumbnail tiles for color / layout / style / presets); chart style overrides unchanged.
  - **Group containers (nested grids)**: add a **Group** widget to nest children in one cell and move them together; use **+** on the group header to add children while editing.
  - **Layout / feed widgets**: text note, spacer, alerts summary, crafting queue; use the edit-mode palette for quick add, or drag to the trash zone to delete.
  - **Lock & size-to-content**: per-widget lock / no-move / no-resize and optional size-to-content; soft alert threshold tint on stats/gauges.
- **Chat**: 💬 icon in sidebar; web messages broadcast in-game as `[Web] <name>: content`.
- **Sidebar**: edge button cycles Expanded → Collapsed → Hidden.
- **Top bar**: fixed-width refresh countdown/status next to connection dot.
- **Backup & Restore** (Settings → **Backup & Restore** tab):
  - **Export JSON**: one-shot backup of theme, per-page layouts (main dashboard, Storage/CPU/Power overviews, topology, quest book, recipes, chat, etc.), refresh and debug preferences; optionally presets and server data (favorites, order templates; alert rules require OP).
  - **Import JSON**: preview affected sections, optional merge mode; reload the page afterward for GridStack layouts to fully apply.
  - **Restore pack defaults**: re-applies server `ui-defaults.json` (instance `TeXTech/WebAE/ui-defaults.json` first, else mod jar bundled file).
- **Pack authors**: export JSON from WebAE Settings, place at `TeXTech/WebAE/ui-defaults.json` or have an Agent write `assets/textech/webae/ui-defaults.json`; first-time visitors with no existing browser prefs apply it automatically. OP can also run `/admweb defaults install <path>`.

---

## 6. Important Notes

- **Localhost only by default**: `bindAddress=127.0.0.1` restricts access to the local machine
- **LAN access risk**: `0.0.0.0` exposes the console to the LAN — use a firewall or SSH tunnel
- **Mandatory auth**: all `/api/` endpoints require a token; force-refresh needs OP/admin grant
- **Layered access**: admins can ban a player account (kick to login), suspend one AE network for everyone including the owner (in-game AE unaffected), or limit guest tokens to selected networks
- **Token security**: tokens grant storage view and crafting submit — store securely
- **Recipes need upload + Fetch**: after OP `/admweb recipes upload`, each player clicks **Fetch recipes** on the Recipes page to sync into browser IndexedDB
- **Icon upload**: OP runs `/admweb icons upload [packName]`; frontend auto-selects the server's most recent pack on first load
- **reload limits**: `/admweb reload` does not rebind the web server; tokens and runtime data files are unaffected

---

> This guide reflects the TeXTech source tree at time of writing.
