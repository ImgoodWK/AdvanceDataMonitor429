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
| Dashboard | 141 color schemes × 36 layouts × 138 page styles, plus 28 searchable/favoritable one-click packs and a dedicated Top Picks entry; draggable widget grid |
| Storage / Fluids / Essentia | Dedicated sidebar pages; Storage also has item/fluid/essentia sub-tables |
| Crafting CPUs | Standalone menu for AE2 crafting CPU status and details |
| Power Monitor | Configurable EU/steam gauges, in/out rates, dual-series trend chart |
| GT Machines | Online GT machine status, progress, and recipes |
| Recipe Search | After OP upload, click **Fetch recipes** to sync into browser IndexedDB; local fuzzy search with merged/compact/detailed layouts |
| Pattern Manager | View, create, edit, and inject AE2 patterns into ME Interfaces |
| AE Orders | Pattern/item/**craft tree** single orders with optional CPU selection and real AE2 progress |
| Network Topology | Logical / spatial / **P2P channel** / **world map** views; channel-budget lanes (dense 32→4×smart 8) + role pods; CSV export |
| Quest Book | BetterQuesting lines/graph/submit assist (requires BQ) |
| Link Scanner | Browse in-game scanner results, aliases, and coords |
| Monitor Bindings | Read-only chart slots; per-slot **line preview** Drawer |
| Planner | Sync Advance Planner entries in the browser |
| AI Assistant | Web chat entry (same capabilities as in-game assistant) |
| Alerts History | Browse triggered automation alerts; rules via Settings/alerts editor |
| Spark Profiler | Requires the Spark mod; directly shows method hotspots, influence groups, smart suggestions, and two-run comparisons in the admin console; Viewer is optional |
| Server Console | Admins run server commands on demand, save shared presets, filter online/offline/all players, and insert names or UUIDs; includes confirmation and bounded auditing |
| QQ Group Bot | Configure an official QQ Open Platform bot for player count/TPS/online list/memory/uptime queries, AI chat, scheduled reports, announcements, and audit |
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
aiServerKeyEnabled=true
aiBrowserKeyEnabled=false
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
sparkEnabled=true
sparkMaxHistory=50
sparkDefaultDurationSeconds=30
sparkMaxDurationSeconds=300
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
- `aiServerKeyEnabled`: allow admin-managed shared AI profiles on the server (default true; false when migrating from legacy `aiKeyMode=browser`).
- `aiBrowserKeyEnabled`: allow per-browser personal AI profiles in localStorage (default false; true when migrating from legacy `aiKeyMode=browser`). Both may be enabled; when both are on, Settings → AI & API chooses the preferred source for this browser.
- `aiKeyMode`: deprecated mutual mode kept only as a migration seed.
- `sparkEnabled` defaults to true but only takes effect when Spark (Forge 1.7.10) is installed. Without Spark, the Spark tab in the admin console is hidden and the API reports unavailable.
- `sparkMaxHistory` bounds retained records in `TeXTech/WebAE/spark-history.json`; each record includes status, initiator, bounded local hotspot/category/thread summaries, context output, and an optional Spark Viewer URL.
- `sparkDefaultDurationSeconds` and `sparkMaxDurationSeconds` control the admin-console capture duration. WebAE explicitly stops Spark at the deadline; Viewer upload can take longer but does not extend sampling. Starting, stopping, recovering Viewer links, and deleting history require WebAE admin privileges.
- **Admin Console → Spark Profiler** has three on-demand modes: **server game thread** (default, low overhead), **slow-tick focus** (keeps ticks over a threshold), and **all threads** (widest view, higher overhead, minimum 10ms interval). You can tune the interval and slow-tick threshold; prefer the default and keep all-thread captures short.
- When sampling stops, WebAE derives method self-time hotspots, influence groups, and thread shares from Spark's stopped local call tree. **Smart performance diagnosis** defaults to deterministic local suggestions backed by a named class/method and exclusive share, without needing Viewer. An admin can explicitly click **Analyze with AI** or **Compare with AI**; only bounded aggregates are sent, never the API key, Viewer link, or raw output, and comparisons always use `B − A`. Start/stop Spark TPS/MSPT/CPU context remains available while the full Viewer stays in a collapsed advanced section.
- Selecting two records compares category and method shares as `B − A`: positive means the influence grew, negative means it improved. Prefer runs made with the same mode, interval, and reproduction scenario. Legacy records without local analysis can only compare metadata.
- While idle, the tab adds no tick collection, world scan, or Spark-log polling. The browser refreshes in-memory state every 3 seconds only during a run, followed by a bounded upload wait. The full interactive flame graph remains optionally available in Spark Viewer.

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
| `/admweb icons upload [pack]` / `upload snapshot [pack]` | **OP** triggers client render/upload to the server |
| `/admweb icons local [pack]` / `local snapshot [pack]` | Any online player: render to this PC `TeXTech/WebAE/icons-local/` (no upload) |
| `/admweb icons pull [pack]` | Any online player: download server PNGs into `icons-local/` |
| `/admweb icons y` / `n` | Lazy-capture consent (only when `iconLazyCaptureEnabled=true`) |
| `/admweb icons render <itemId> [pack]` | **OP** render and upload a single item icon |
| `/admweb icons verify <itemId> [pack]` | Open icon verify GUI |
| `/admweb icons import <folder> [pack]` | **OP** import PNGs from a local folder |
| `/admweb icons import-nesql [pack] [subpath]` | **OP** imports pre-rendered PNGs from `nesqlRepositoryPath` (default `TeXTech/WebAE/`; incremental) |
| `/admweb icons modes` | List icon render mode (nei only) |
| `/admweb icons status` | List installed icon packs and config state |
| `/admweb icons clear` | Delete all icon packs (OP only; async, does not freeze the game; chat notifies when done) |
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

The page separates a **Storage capacity overview** from the **Inventory details** workspace with explicit headings, descriptions, and spacing. The overview remains a configurable GridStack; normal mode shows Settings and Edit, while edit mode expands undo/redo, add, arrange, and reset tools. Browsing mode hides all of those editing entry points. Details retain item/fluid/essentia tabs, sorting, search, and multi-network Split/Merged aggregation. The standalone Fluids page similarly separates totals, pinned trends, and the inventory table.

### Crafting CPUs

Standalone sidebar menu with separate **CPU health overview** and **Processors and crafting queue** workspaces. Click a row for the detail drawer. Multi-network Split mode uses tabs.

### Power Monitor

The **Power operations overview** contains the full-page GridStack (EU gauge, in/out rates, steam bar, dual-series trend chart, etc.). **Edit layout** expands the complete editor toolbar; Browsing mode hides Edit and Settings. Multi-network mode uses a prominent network switcher, and both snapshot values and trend history follow the active network; auto-refresh keeps charts mounted without flicker.

### GT Machines

Lists online machines with name, progress, recipe, and input/output slots. Filter, search, and sort supported.

### Recipe Search

Recipes are not kept in server heap for the browser to query continuously. Flow: **OP uploads in-game → server writes disk → player clicks Fetch recipes on the Recipes page → chunks land in this browser’s IndexedDB → local browse/search**.

- On the Recipes page, click toolbar **Fetch recipes** when a new server revision is available (progress bar; cancellable). Then use fuzzy search, category multi-select, Full/Merged and Compact/Detailed layouts against the local store; no automatic re-download while revision is unchanged.
- OP must run `/admweb recipes upload snapshot` (recommended) or `upload` (full) first; collection also writes `.minecraft/TeXTech/WebAE/web-recipes.json` on the client (plain JSON backup).
- Changing browsers or clearing site data requires Fetch again. Server `/admweb recipes clear` does not wipe browser IndexedDB.

### Item Icons & Texture Packs

Real game icons in tables and recipes; abbreviation fallback on failure. Resolution order: **local folder → IndexedDB → server disk → abbreviation** (lazy capture off by default).

- **Local folder**: Settings → pick `TeXTech/WebAE/icons-local/` (Chrome/Edge; https or localhost; on LAN http://IP use ZIP import). Any player: `/admweb icons local` or `/admweb icons pull`.
- **Local first**: Directory/IndexedDB hits skip the server; OP `/admweb icons upload` or import fills server disk.
- **Auto-sync (off by default)**: Settings bulk-download into IndexedDB when the pack revision changes.
- **Manual fetch**: Sync full pack; Fill visible missing only requests existing PNGs (does not imply in-game render).
- **Server cache**: OP upload / import-nesql; render mode fixed to **`nei`** (64×64 NESQL FBO). If PNGs on disk show square/odd-shaped holes, re-run upload with a fixed mod build to overwrite; for intermittent wrong icons in the browser, **Ctrl+F5** or clear IndexedDB / re-pick the local icon folder.
- **GT++ (miscutils) missing ingot/plate/rod icons**: Older full-pack uploads skipped stacks with `getIconIndex==null` (dusts kept, metal forms dropped). On-disk `itemDustMix*` is the special "Mix" dust, **not** a misnamed ingot. Re-run `/admweb icons upload snapshot` (preferred) or a full `upload` on a current build, then Ctrl+F5.
- **Whole GT meta series showing abbreviations**: the frontend used to mark bare `gregtech:gt.metaitem.01` failed when one meta id failed, blocking sibling metas. Current builds only mark `:0` equivalents; hard-refresh to clear poisoned state.
- **Async fill (opt-in)**: `iconLazyCaptureEnabled` default **false**. When on, miss enqueues after chat consent (resource-pack notice). Direct render still default **false**.
- **Multiplayer tip**: OP `/admweb icons upload snapshot` once; players use local/folder/ZIP.

### Local data folder `TeXTech/WebAE/`

| Path | Purpose |
|------|---------|
| Client `.minecraft/TeXTech/WebAE/web-recipes.json` | NEI recipe JSON written after `/admweb recipes upload*` |
| Server `TeXTech/WebAE/web-recipes.json` + `.meta.json` + `recipe-chunks/` | Server authoritative cache; browsers pull chunks via **Fetch recipes** |
| Server `<instance>/TeXTech/WebAE/` (or configured `nesqlRepositoryPath`) | NESQL pre-rendered PNGs for `/admweb icons import-nesql` (often under `images/`) |
| Server `TeXTech/WebAE/qq-bot.json` + `qq-bot-master.key` | Non-secret QQ bot settings plus AES-GCM master key; ClientSecret is stored only as ciphertext and never returned by the API |

The folder is created automatically on first use.

### Pattern Manager

Left: pattern list with search and batch delete. Right: 9×3 input grid and outputs with multiplier controls. Encoding consumes a blank pattern from the AE network; inject into ME Interfaces.

### Crafting Orders

Optional CPU selector at top. **By pattern** tab: paginated Grid + Interface browse with virtual scroll and single orders. **By item** tab: storage search and single orders. **Craft tree** tab: enter registry name and quantity; recursively expands recipe chain and shows storage gaps (view only; no batch order). Active/history orders poll every 3s; progress comes from AE2 `ICraftingLink` and CPU craft-tree step counters (same as in-game CPU GUI, **not** final-output count). In-game cancelled jobs appear in history as cancelled. History rows offer **Reorder** with a confirmation dialog before resubmitting.

### Network Topology

Sidebar **Network Topology** offers logical grouping, spatial bins, **P2P channels**, and **world map** views. The logical view is a **channel budget planning map** (`ae_budget_v2`): controller → dense trunk 32 → four smart lanes of 8 → role pods (terminals/buses/interfaces/…) → devices; zero-channel storage/CPU hang on the hub orbit. This is not real AE cabling. A double-ring summary mode remains available. The former cable-simulation view is deprecated (`topologySimulatedEnabled`, default off). P2P view lists tunnel endpoints grouped by frequency. CSV export supported.

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
- **Quest detail**: clicking a node opens a **fixed right sidebar** (no extra drawer on desktop). Sections appear in order: **related quests** (prerequisites / unlocks), requirements (from BetterQuesting `tasks`), and rewards (from `rewards`; `bq_standard:choice` shows a pick-one group; pure item rewards can be claimed on Web). Clicking a prerequisite or follow-up quest switches quest lines when needed and centers the node; implicit/hidden prerequisites show tags. Panel width is configurable in Settings (default 380px).
- **Refresh**: load once on enter / chapter switch into local cache; **no background polling**. Manual refresh has a **30s cooldown**; progress is force-refreshed before submit/claim actions.
- **Reward claim**: when a quest is `UNCLAIMED` and every reward is a resolvable pure item (`bq_standard:item`) or choice (`bq_standard:choice`), select choice options in the sidebar, then confirm **Claim to AE network**. Items go through the official BQ claim path and are moved into the selected network. If AE cannot accept everything, the claim is refused and the quest stays unclaimed. Quests with command/XP/other non-item rewards still prompt in-game claim. Guest tokens cannot claim.
- **Read-only for guests**: browse lines and party progress; guests cannot submit or claim rewards.
- **Web-assisted steps**: item/fluid submit and Retrieval / fluid hold-detect (completed from AE stock; **does not** put items/fluids into the player inventory). **Fluid-cell tasks**: DETECT sums free fluid + filled cells (GT/IC2 by default); SUBMIT prefers filled cells, else empty cell + fluid fill (free fluid alone is not enough). True fluid tasks may drain needed mB from cells (remainder returned). Submit and craft-then-submit use **AE virtual escrow** (pre-lock available materials before craft; append-lock when craft products arrive). Click a single step to submit; **Chain submit** walks prerequisites in topological order (configurable). The submit panel shows AE stock vs requirement; **Craftable** appears only when a matching AE pattern exists and the material chain can be satisfied. When `questFluidAllContainersOption=true`, the panel can opt into counting buckets/cans.
- **Offline**: Token + FakePlayer still works when the owner is offline (progress writes to the **Token owner's** questing UUID); the server must be running and the AE network resolvable (Link / chunks loaded), same as other WebAE AE ops.
- **Party**: Web submit does **not** sync party members; only the Token owner is updated. BQ forces single-player for FakePlayer; this mod does not wire PartyManager / SyncPartyQuests.
- **Config**: `[webConsole] questEnabled`, `questSubmitEnabled`, `questClaimEnabled` (default true), `questChainSubmitEnabled` (default true), `questSubmitMaxStacks`, `questCraftWaitTimeoutMs`, `questEscrowEnabled` (default true), `questEscrowTimeoutMs` (default 120000), `questFluidAllContainersOption` (default false), `questCacheTtlSec` in `textech.cfg`.

### Monitor Bindings & Preview

Sidebar **Monitor Bindings** shows read-only chart slots and GT binding coords. Click **Preview** on a slot to open a line chart Drawer mirroring in-game monitor data (edit remains in-game).

### Link Scanner / Planner / AI Assistant

- **Link Scanner**: browse results from the in-game Advance Link Scanner (owner/name filters and coordinates; teleport remains in-game).
- **Planner**: sync Advance Planner entries for browsing and progress checks in the browser.
- **AI Assistant**: the Web page shares intent types with the in-game assistant. Shared mode uses ordered admin AI profiles with provider-side failover; personal mode calls the provider from the browser and posts only the plan/reply. When both are enabled, the browser preference wins (with fallback to the other side if unconfigured). Shared multi-engine web search can augment chat, intent, and Spark AI (personal LLM mode uses server-side search proxy). Missing/failed AI falls back to the local lexicon. Crafting, withdrawal, and teleport require a second confirmation; guests remain read-only.

### Automation Alerts & History

`TeXTech/WebAE/web-alerts.json` drives inventory, stuck CPU, GT error, order completion, channel overload, low-TPS, and automatic-restock events. `[webConsole] alertsEnabled=true` in `config/textech/textech.cfg` is the server-wide switch (on by default); JSON and individual routes can still be disabled.

Settings → Automation Alerts can combine these routes with event, severity, and optional owner-UUID filters:

- existing WebAE toast/browser notifications (polling plus real-time SSE);
- the owning online player's chat and client HUD (position, duration, visible count, and sound);
- legacy Discord/generic JSON webhooks;
- official QQ Open Platform bots (group `group_openid`, user `openid`, or channel ID). Settings includes **Capture target ID**: the server opens an outbound QQ WebSocket gateway listen (no public callback URL), then you can apply IDs after private-messaging or @-mentioning the bot; the Open Platform bot must allow WebSocket and matching event intents;
- WeChat Official Account customer-service text or template messages;
- SMTP email (none / STARTTLS / SSL, To/Cc);
- WeCom group-bot webhooks or WeCom custom-application messages.

For first-time setup, select the small `?` button next to the alert description to see where each platform is configured, what to copy, where to paste it, and links to official setup pages. An admin can select **Enable built-in routes** to save WebAE/browser, owner-chat, and HUD routes in one action and request browser system-notification permission from that click. If permission is denied, in-page Toast notifications still work. Use **Configure now** for an external platform, fill the required fields shown on the card, and open that target's **Advanced** section only for event/severity/owner filters, API/token overrides, SMTP Cc/subject, or other special cases.

Browser and player-chat routes default on; HUD defaults to warning/error only. External targets default empty, so an upgrade never starts third-party delivery by itself. Secrets and full webhook URLs stay in the server file and APIs expose masks only. QQ proactive delivery remains subject to bot permissions/rate limits. Official Account customer-service messages normally require a platform interaction window; template mode requires template permission and a template with `first`, `keyword1`–`keyword4`, and `remark` fields. Offline owners are not found by a server-wide scan; their occurrences remain in Web history.

DNS, TLS, OAuth, SMTP, retries, and backoff all run through a fixed-capacity background queue. Save an external target or Discord/generic webhook before selecting **Send test**. A success notice means the test was queued, not that the third party accepted it. Use the queue/delivery/failure/drop/circuit counters together with the real destination; an unavailable third party never blocks the server tick.

### Server Console

WebAE admins can run Minecraft server commands under **Admin Console → Server Console**. The leading `/` is optional. Commands execute with full server-console permission on the main thread, so shutdown, bans, OP/whitelist changes, and similar high-risk commands require explicit confirmation. The page submits one command at a time, while the server also enforces single-flight execution, a short cooldown, and a task-queue depth gate. If waiting for a result times out, the command may still be queued; check audit history before retrying.

- The player panel reads existing player metadata when first opened or manually refreshed. Online/offline/all filtering and search are local to the browser, so changing filters does not create more server requests. **Insert Name** and `UUID` place the selected identity at the command cursor.
- Presets are shared by all WebAE admins and persist in `TeXTech/WebAE/admin-console.json`, with a maximum of 64. Loading a preset only fills the editor and never runs it immediately.
- Audit retains at most 40 entries with actor, command, state, duration, and affected count. The table transfers only output previews; full output is fetched on demand and bounded to the last 24 lines of 256 characters each.
- There is no automatic player polling, world scan, or idle tick task. Player snapshots are briefly reused for three seconds, and preset/audit writes are coalesced on background threads.

### QQ Group Bot Administration

WebAE admins configure the official QQ Open Platform bot under **Admin Console → QQ Group Bot**. It is disabled by default. After AppID and ClientSecret are saved and the feature is enabled, the server opens an outbound Gateway WebSocket connection; no public HTTPS callback or OneBot/NapCat process is required.

- **Read-only commands**: `/status`, `/players`, `/list`, `/tps`, `/memory`, `/uptime`, `/about`, and `/ping`, with several Chinese aliases. The prefix is configurable, and group/direct/channel inputs can be independently disabled.
- **AI chat**: `/ai <question>` uses the server-side shared AI profiles managed by WebAE. Natural-language auto-reply and shared web search are optional. Conversation history is isolated by target plus QQ user, bounded by turns and TTL, and protected by a separate AI cooldown. `/reset` clears only that user's current session.
- **Security scope**: allowlist group openids, user openids, and bot-admin user openids. Bot admins receive bot-only management hints; they are never granted Minecraft OP and QQ cannot execute arbitrary server commands. AI receives only a bounded read-only snapshot such as TPS, players, online names, uptime, and JVM memory.
- **Reports and announcements**: schedule status reports to `group:<openid>`, `c2c:<openid>`, or `channel:<id>` with a minimum five-minute interval, and send manual announcements/tests from the admin console. QQ HTTP, AI, and send work use a bounded background queue and never block server ticks.
- **In-game manual messages**: OPs can enqueue text with `/admweb qq send [group|c2c|channel] <openid> <message>`, inspect connection/queue state with `/admweb qq status`, and start an asynchronous reconnect with `restart`. Queue acceptance is not final QQ delivery.
- **Operations**: the runtime tab shows connection phase, reconnect time, receive/reply/AI/failure/drop/rate-limit counters, queue depth, and a short in-memory audit ring. ClientSecret is encrypted server-side and shown only as a mask; deleting it also disables the bot.

Group/user openids and channel IDs can be captured with **Settings → Automation Alerts → QQ official bot → Capture target ID**. The bot still needs matching event permissions in QQ Open Platform; group messages normally require an @ mention and proactive reports remain subject to platform permissions and rate limits.

### Game Window Screenshots and Sharing

- Bind **Capture Current Game Window** under Controls (F10 by default). It reads Minecraft's framebuffer, so the world and any open GUI are included; it never invokes desktop capture or reads other windows.
- Files stay under client `<instance>/TeXTech/Screenshots/` and never upload automatically. `/admscreenshot list [page]` lists local history; click an entry or use `preview [index]` for an in-game preview. Index 1 is newest.
- `/admscreenshot send web [index] [caption]` publishes one image to WebAE Chat. The browser fetches it with the current authenticated token and offers click-to-preview.
- `/admscreenshot send qq <group openid> [index] [caption]` sends through the configured official QQ bot and requires Minecraft OP. Group/C2C image delivery is supported; channel images are not.
- Defaults are 1920×1080, JPEG 88%, 2048 KiB per image, one 24 KiB chunk per tick, and a 15-second per-player cooldown. `[webConsole] screenshot*` settings control clarity, bandwidth, concurrency, and client/server retention. Server completion uses one bounded worker and adds no idle tick scan.

### Mobile & PWA

Includes `manifest.webmanifest` and responsive CSS for narrow screens. You can add the console to your phone home screen (still prefer SSH tunnel access; do not expose raw to the public internet).

### Dashboard, Chat & Settings

- **AI & API**: Settings use separate Server / This browser tabs. Admins manage ordered shared LLM profiles plus shared multi-engine web search (AES-GCM under `TeXTech/WebAE/`). Any logged-in user can keep ordered personal profiles in `localStorage` and call providers directly. When both sources are enabled, choose a preferred source in the UI. Failover follows list order for provider-side errors only. Provider base URLs must be HTTPS except loopback; key entry is disabled outside HTTPS/loopback pages.

- **Dashboard**: GridStack drag layout with **141** color schemes + **36** layouts + **138** page styles. Settings → Appearance now has 28 complete design packs and opens on a dedicated **Top Picks** filter; packs remain searchable/favoritable and all four appearance axes can still be mixed freely. Eight new media-technology flagships interpret League Hextech, StarCraft Terran/Protoss, Death Stranding BRIDGES, Evangelion NERV/MAGI, Ghost in the Shell Section 9, NieR YoRHa, and the TRON Grid through original CSS composition, material, information hierarchy, and motion—without bundling franchise images or external fonts. Existing Rhodes, cyber, aerospace, Printstream, Auraeco, and GTNH/GregTech families remain, and decorative layers never clip business content.
  - **Effects performance tiers**: Low-end Host keeps static composition while disabling continuous motion/blur; Modern Office PC enables micro-interactions and moderate glass; Gaming PC Full FX enables scans, orbits, particles, glow, and continuous chart motion. Browser reduced-motion preference always disables animation.
  - **Group containers (nested grids)**: add a **Group** widget to nest children in one cell and move them together; use **+** on the group header to add children while editing.
  - **Layout / feed widgets**: text note, spacer, alerts summary, crafting queue; use the edit-mode palette for quick add and the widget delete button to remove. React remains the sole owner of DOM removal instead of letting the grid engine delete page nodes.
  - **Composite operations widgets**: network health core (storage/power/crafting/GT/server/alerts), power flow, storage matrix, GT machine fleet, player presence, combined alert/crafting activity stream, and server vitals (TPS/MSPT/uptime). They reuse existing WebAE snapshots and polling results and add no server-tick work.
  - **Edit recovery**: undo/redo in edit mode (toolbar or Ctrl+Z / Ctrl+Y); clearing all widgets stays empty after refresh; Storage/CPU Overview widget height is fixed to 2 rows.
  - **Lock & size-to-content**: per-widget lock / no-move / no-resize and optional size-to-content; soft alert threshold tint on stats/gauges.
  - **Data-table columns & pins**: the widget editor independently controls icon, name, amount, registry name, and source-specific columns. The **name** column uses the item's display name, while **registry name** is shown separately. An empty selection is preserved; changing the data source selects that source's defaults. Focus the pin search to see current-inventory candidates, or search by display name, registry name, or item ID; remove an existing pin before adding another at the server-provided limit.
  - **Export for in-game display**: publishes the dashboard and copies a live binding. Successful frames must be real web content: host Chrome/Edge capture of `/embed/dashboard` (`spa-jpeg`), or local MCEF when installed (`mcef`). Export warms `frame.jpg`; on failure the GUI shows pending/error codes — it does **not** silently switch to AWT snapshot fake UI or `render.html`. Static snapshot is only produced when publish fails (offline/unauthenticated) and is explicitly not a web page. `webSurfaceUseMcef` defaults to true. Publish fills `webaeOrigin` from the browser Origin when possible. `GET /api/display/{id}/frame-status` reports capture diagnostics.
- **Chat**: 💬 icon in sidebar; web messages broadcast in-game as `[Web] <name>: content`. Explicit client screenshots appear as image messages with caption, dimensions, and size; retention-expired files show an unavailable attachment state.
- **Sidebar**: edge button cycles Expanded → Collapsed → Hidden.
- **Top bar**: fixed-width refresh countdown/status next to connection dot.
- **Browsing mode** (Settings → **Browsing mode**): hides layout Settings, Edit, and capture controls on read-only Dashboard, Storage/CPU overview, Power, and Topology surfaces while retaining viewing, search, filters, refresh, and export. Every page except Admin and Settings uses a viewport-bound outer container; genuinely long content remains wheel/touch scrollable inside the page without a browser-edge scrollbar. The preference is stored in `localStorage.webae_browsing_mode` and participates in UI backup/restore.
- **Backup & Restore** (Settings → **Backup & Restore** tab):
  - **Export JSON**: one-shot backup of theme, Browsing mode, per-page layouts (main dashboard, Storage/CPU/Power overviews, topology, quest book, recipes, chat, etc.), refresh and debug preferences; optionally presets and server data (favorites, order templates; alert rules require OP).
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
