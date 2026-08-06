# TeXTech WebAE Console User Guide

> Audience: Players & server admins · Last synced: 2026-07<br>
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
| Monitor Bindings | Read-only monitor slots; type-aware scalar/series/category/table preview Drawer |
| Planner | Sync Advance Planner entries in the browser |
| AI Assistant | Web chat entry (same capabilities as in-game assistant) |
| Alerts History | Browse triggered automation alerts; rules via Settings/alerts editor |
| Spark Profiler | Requires the Spark mod; directly shows method hotspots, influence groups, smart suggestions, and two-run comparisons in the admin console; Viewer is optional |
| Server Console | Admins run server commands on demand, save shared presets, filter online/offline/all players, and insert names or UUIDs; includes confirmation and bounded auditing |
| QQ Group Bot | Configure an official QQ Open Platform bot for player count/TPS/online list/memory/uptime queries, AI chat, scheduled reports, announcements, and audit |
| Chat | Web-to-in-game chat bridge with online player list |
| Command Upload | OPs run `/textech web recipes upload` and `/textech web icons upload` (no in-game keybind) |

Default URL: `http://127.0.0.1:8090` (port is configurable).

---

## 2. Enable & Configure

WebAE is an optional TeXTech 2.0 download and is not bundled in the core mod JAR. Download the matching `textech-*-webae.zip` from the same GitHub Release and extract it directly at the **server instance root**. A correct installation contains:

```text
TeXTech/WebAE/ui/index.html
```

Servers that do not use WebAE do not need this ZIP. Without the UI bundle the configured backend APIs can still start, but `/` returns an installation notice instead of the console. On upgrade, replace the complete `TeXTech/WebAE/ui/` directory with the matching ZIP version.

The Web Console is **disabled by default**. After installing the UI bundle, edit the `config/textech/textech.cfg` `[webConsole]` section:

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
- `nesqlRepositoryPath`: NESQL repo root for `/textech web icons import-nesql`. **When empty**, defaults to `<instance>/TeXTech/WebAE/` (`.minecraft/TeXTech/WebAE/` on client; same folder name under server root on dedicated servers; same as client recipe export).
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

**Command index**: run `/textech help` in-game (aliases `/adm`, `/txt`) to list every command domain. The branded Web entry point is `/textech web …`; the legacy roots `/admweb`, `/adm-web`, and `/webconsole` remain available for compatibility.

The Web Console requires token authentication. Use commands in-game (or from server console):

| Command | Description |
|---------|-------------|
| `/textech web issue` | Issue an **owner** token (requires at least one Advance Data Monitor you own) |
| `/textech web login` | Generate a **6-digit browser login code** (5 min TTL, single use; no OP token required) |
| `/textech web guest <player>` | Monitor owner sends a **read-only guest** token privately to an **online** player (only the owner's allowed AE networks) |
| `/textech web copy` | Copy your active token to clipboard |
| `/textech web list` | List tokens with type, owner, actor (OP only) |
| `/textech web revoke [guestName]` | Revoke your owner token; owners revoke guest tokens; OP can revoke others |
| `/textech web reload` | Reload TeXTech config; `enabled`/`port`/`bindAddress` still need restart (OP only) |
| `/textech web refresh [network]` | Admin force re-collect snapshots (OP only) |
| `/textech web server status` | Show WebAE HTTP server state |
| `/textech web server restart` | Restart HTTP server (OP only) |
| `/textech web recipes upload [snapshot\|deep]` / `export` | **OP** triggers client NEI collection and upload to server disk; also writes `<instance>/TeXTech/WebAE/web-recipes.json` on the client; web UI still needs **Fetch recipes**; `snapshot` = storage-related items only (recommended daily); `deep` = full NEI item scan (slow) |
| `/textech web recipes status` | Show recipe cache status (incl. disk size) |
| `/textech web recipes clear` | Clear recipe memory + disk cache (OP only) |
| `/textech web icons upload [pack]` / `upload snapshot [pack]` | **OP** triggers client render/upload to the server |
| `/textech web icons local [pack]` / `local snapshot [pack]` | Any online player: render to this PC `TeXTech/WebAE/icons-local/` (no upload) |
| `/textech web icons pull [pack]` | Any online player: download server PNGs into `icons-local/` |
| `/textech web icons y` / `n` | Lazy-capture consent (only when `iconLazyCaptureEnabled=true`) |
| `/textech web icons render <itemId> [pack]` | **OP** render and upload a single item icon |
| `/textech web icons verify <itemId> [pack]` | Open icon verify GUI |
| `/textech web icons import <folder> [pack]` | **OP** import PNGs from a local folder |
| `/textech web icons import-nesql [pack] [subpath]` | **OP** imports pre-rendered PNGs from `nesqlRepositoryPath` (default `TeXTech/WebAE/`; incremental) |
| `/textech web icons modes` | List icon render mode (nei only) |
| `/textech web icons status` | List installed icon packs and config state |
| `/textech web icons clear` | Delete all icon packs (OP only; async, does not freeze the game; chat notifies when done) |
| `/textech web worldmap upload [networkId]` | Upload world map snapshot (must be near AE network; client capture) |
| `/textech web worldmap accept <requestId>` | Accept a guest/web map upload request (legacy) |
| `/textech web wm y [id]` | **Recommended** accept upload; id optional; same as clicking Accept in chat |
| `/textech web wm n [id]` | Decline upload request |
| `/textech web wm up [networkId]` | Same as `worldmap upload` |
| `/textech web wm st [networkId]` | Same as `worldmap status` |
| `/textech web worldmap status [networkId]` | Map snapshot capture status |
| `/textech web worldmap status [networkId]` | Show map snapshot capture status |
| `/textech web help` | Show usage (incl. recipes/icons/worldmap/server grouped help) |

**Token types**

- **Owner token**: bound to the monitor owner UUID; read/write all AE networks linked to their monitors; **owner need not be online**.
- **Guest token**: issued via `/textech web guest`; read-only and limited to the owner's network allowlist. Guests cannot refresh, upload, submit orders, write patterns, or perform other mutations; chat still shows the guest name.
- Tokens persist in `TeXTech/WebAE/web-tokens.json`; legacy entries migrate to `type: owner` on load.

**Offline access & chunks**

- Only monitors in **loaded chunks** are discovered; `monitorDim` reflects the monitor’s dimension.
- Keep bases chunk-loaded (loader or players nearby) or the web network list may be empty.

---

## 4. Browser Access

1. Confirm the server is running with `enabled=true`
2. Open `http://127.0.0.1:8090` (or your configured address/port)
3. Enter the token from `/textech web issue` on the login page
4. The token is stored in browser `localStorage`; **auto-login** (default on) reconnects on next visit
5. If the token expires or is revoked, the login page clears it — re-issue with `/textech web issue`
6. To switch tokens, enter a new one in Settings for immediate reconnect
7. Disable auto-login in Settings if preferred

---

## 5. Feature Pages

### Storage Monitor

The page separates a **Storage capacity overview** from the **Inventory details** workspace with explicit headings, descriptions, and spacing. The overview remains a configurable GridStack; normal mode shows Settings and Edit, while edit mode expands undo/redo, add, arrange, and reset tools. Browsing mode hides all of those editing entry points. Details retain item/fluid/essentia tabs, sorting, search, and multi-network Split/Merged aggregation. The standalone Fluids page similarly separates totals, pinned trends, and the inventory table.

### Crafting CPUs

Standalone sidebar menu with separate **CPU health overview** and **Processors and crafting queue** workspaces. Click a row for the detail drawer. Multi-network Split mode uses tabs. The drawer shows a network-wide busy-rate trend, bounded job history, and a capacity summary; filter queued/running/completed/failed/cancelled/stuck/unknown jobs. Capacity windows are 1 hour, 6 hours, 24 hours, 7 days, or 14 days, with peak concurrency, P50/P95 duration, queue time, storage pressure, and a read-only CPU estimate.

The main Dashboard also includes a **CPU history and capacity** region for the first selected network, with a network-wide busy-rate trend, recent jobs, lifecycle filter, capacity windows, bottlenecks, and recommendations. It refreshes at most every 30 seconds while the Dashboard is active and follows network selection. Both the Dashboard and CPU detail drawer make unknown and truncated data explicit rather than using a success state.

History is retained for up to 14 days. One response contains at most 500 jobs and 1,000 CPU snapshots; **Results truncated** means a request or response cap was reached. **Unknown** is rendered as a warning and means the lifecycle could not be verified—it is never treated as idle or completed. The first request for a network that has not yet been activated only enables later normal server-tick sampling, so a brief empty view does not prove that the network is idle; in-flight jobs also recover conservatively as unknown after a server restart. History and capacity are planning data only: WebAE never creates, splits, resizes, or edits CPUs and never changes or resubmits orders automatically.

### Power Monitor

The **Power operations overview** contains the full-page GridStack (EU gauge, in/out rates, steam bar, dual-series trend chart, etc.). **Edit layout** expands the complete editor toolbar; Browsing mode hides Edit and Settings. SNL 0.2.5 exposes stored steam but no real capacity, so the page never fabricates a stored/max ratio or 0%; a progress bar or gauge is shown only after an explicit target is configured. Multi-network mode uses a prominent network switcher, and both snapshot values and trend history follow the active network; auto-refresh keeps charts mounted without flicker.

### GT Machines

Lists online machines with name, progress, recipe, and input/output slots. Filter, search, and sort supported.

### Recipe Search

Recipes are not kept in server heap for the browser to query continuously. Flow: **OP uploads in-game → server writes disk → player clicks Fetch recipes on the Recipes page → chunks land in this browser’s IndexedDB → local browse/search**.

- On the Recipes page, click toolbar **Fetch recipes** when a new server revision is available (progress bar; cancellable). Then use fuzzy search, category multi-select, Full/Merged and Compact/Detailed layouts against the local store; no automatic re-download while revision is unchanged.
- OP must run `/textech web recipes upload snapshot` (recommended) or `upload` (full) first; collection also writes `.minecraft/TeXTech/WebAE/web-recipes.json` on the client (plain JSON backup).
- Changing browsers or clearing site data requires Fetch again. Server `/textech web recipes clear` does not wipe browser IndexedDB.

### Item Icons & Texture Packs

Real game icons in tables and recipes; abbreviation fallback on failure. Resolution order: **local folder → IndexedDB → server disk → abbreviation** (lazy capture off by default).

- **Local folder**: Settings → pick `TeXTech/WebAE/icons-local/` (Chrome/Edge; https or localhost; on LAN http://IP use ZIP import). Any player: `/textech web icons local` or `/textech web icons pull`.
- **Local first**: Directory/IndexedDB hits skip the server; OP `/textech web icons upload` or import fills server disk.
- **Auto-sync (off by default)**: Settings bulk-download into IndexedDB when the pack revision changes.
- **Manual fetch**: Sync full pack; Fill visible missing only requests existing PNGs (does not imply in-game render).
- **Server cache**: OP upload / import-nesql; render mode fixed to **`nei`** (64×64 NESQL FBO). If PNGs on disk show square/odd-shaped holes, re-run upload with a fixed mod build to overwrite; for intermittent wrong icons in the browser, **Ctrl+F5** or clear IndexedDB / re-pick the local icon folder.
- **GT++ (miscutils) missing ingot/plate/rod icons**: Older full-pack uploads skipped stacks with `getIconIndex==null` (dusts kept, metal forms dropped). On-disk `itemDustMix*` is the special "Mix" dust, **not** a misnamed ingot. Re-run `/textech web icons upload snapshot` (preferred) or a full `upload` on a current build, then Ctrl+F5.
- **Whole GT meta series showing abbreviations**: the frontend used to mark bare `gregtech:gt.metaitem.01` failed when one meta id failed, blocking sibling metas. Current builds only mark `:0` equivalents; hard-refresh to clear poisoned state.
- **Async fill (opt-in)**: `iconLazyCaptureEnabled` default **false**. When on, miss enqueues after chat consent (resource-pack notice). Direct render still default **false**.
- **Multiplayer tip**: OP `/textech web icons upload snapshot` once; players use local/folder/ZIP.

### Local data folder `TeXTech/WebAE/`

| Path | Purpose |
|------|---------|
| Client `.minecraft/TeXTech/WebAE/web-recipes.json` | NEI recipe JSON written after `/textech web recipes upload*` |
| Server `TeXTech/WebAE/web-recipes.json` + `.meta.json` + `recipe-chunks/` | Server authoritative cache; browsers pull chunks via **Fetch recipes** |
| Server `<instance>/TeXTech/WebAE/` (or configured `nesqlRepositoryPath`) | NESQL pre-rendered PNGs for `/textech web icons import-nesql` (often under `images/`) |
| Server `TeXTech/WebAE/qq-bot.json` + `qq-bot-master.key` | Non-secret QQ bot settings plus AES-GCM master key; ClientSecret is stored only as ciphertext and never returned by the API |

The folder is created automatically on first use.

### Pattern Manager

The Patterns page is now a three-column workbench modeled after the in-game Pattern Terminal: recipe/pattern sources on the left, the current editor in the center, and ME Interfaces plus a Web-only buffer on the right.

- **Create from a recipe**: Search products, ingredients, and recipe types, filter by the exact handler, then apply any concrete recipe in one click. Crafting patterns use a shaped 3×3 grid; processing patterns use 9×3. Empty shaped slots, item metadata/NBT, and fluid inputs are preserved.
- **Edit existing patterns**: Click a network pattern or occupied interface slot, edit it, then save it back to the same slot without consuming another blank. A blank pattern is deducted only when a new pattern is written into an empty interface slot.
- **Interface workspace**: Full-block interfaces and cable interface parts are both listed with machine recipe type, active slots, and installed patterns. Drag to an empty slot to move; dropping onto an occupied slot offers an atomic swap.
- **Cross-interface moves**: Move a physical pattern into the Web-only buffer, select another empty interface slot, then place it. The buffer is isolated by owner and AE network, holds at most 54 entries, and persists at `TeXTech/WebAE/web-pattern-buffer.json`. Taking an entry clears its source slot; this is not a copied pattern.
- **Programmable Hatches compatibility**: When the community Programmable Hatches mod is installed, enable its editor mode to wrap molds, lenses, catalysts, and other `stackSize=0` inputs as `programmablehatches:prog_circuit`. Such inputs show `∞`; a selected input can also be toggled manually. The switch is disabled when the mod is absent.

Writing a new pattern, moving, swapping, buffering, deleting, and write-back require WebAE admin permission. The page refreshes interface and pattern state after mutations; use **Refresh** if the background pattern snapshot is still warming up.

### Crafting Orders

Optional CPU selector at top. **By pattern** tab: paginated Grid + Interface browse with virtual scroll and single orders. **By item** tab: storage search and single orders. **Craft tree** tab: enter registry name and quantity; recursively expands recipe chain and shows storage gaps (view only; no batch order). Active/history orders poll every 3s; progress comes from AE2 `ICraftingLink` and CPU craft-tree step counters (same as in-game CPU GUI, **not** final-output count). In-game cancelled jobs appear in history as cancelled. History rows offer **Reorder** with a confirmation dialog before resubmitting.

### Network Topology

Sidebar **Network Topology** offers logical grouping, spatial bins, **P2P channels**, and **world map** views. The logical view is a **channel budget planning map** (`ae_budget_v2`): controller → dense trunk 32 → four smart lanes of 8 → role pods (terminals/buses/interfaces/…) → devices; zero-channel storage/CPU hang on the hub orbit. This is not real AE cabling. A double-ring summary mode remains available. The former cable-simulation view is deprecated (`topologySimulatedEnabled`, default off). P2P view lists tunnel endpoints grouped by frequency. CSV export supported.

#### World Map View

The default **client snapshot** mode (`worldMapSnapshotMode=client_only`) captures and uploads map data from an online player client; the server does not render terrain.

1. **First use**: capture a logical topology snapshot, then click **Update map snapshot** in WebAE, or have the network owner run `/textech web wm up` (or `/textech web worldmap upload`) near the AE network.
2. **JourneyMap**: when JourneyMap is installed, TeXTech first reads the current world's highest-resolution local day tiles (`worldMapJourneyMapEnabled`, including JM 5.x `{x},{z}.png` names). Missing tiles fall back to client GL. Web quality is controlled by the quality selector or `webWorldMapDefaultQualityTier` / `webWorldMapMaxQualityTier` (up to ultra, 512 px/chunk).
3. **Guest-requested updates**: clicking **Update map snapshot** sends nearby players an Accept/Decline chat prompt. `/textech web wm y` accepts the latest request without requiring its ID. Duplicate pending requests do not spam offers.
4. **Device list**: the top-right **Device list** button opens a modal without resizing the map.
5. **Markers and details**: click a device icon or cluster count for a thumbnail list, then open a detail drawer. Crafting CPUs and ME controllers count once per multiblock in cluster totals while details retain all coordinates.
6. **Left AE legend rail**: hover to expand category visibility and colors. Hidden categories remain listed but dimmed; colors affect the AE overlay and marker borders.
7. **Local cache**: the browser caches tiles in IndexedDB; the MC client also synchronizes snapshots into `TeXTech/WebAE/map-cache/` after login on another device.
8. **AE overlay**: captured with terrain; its opacity setting affects AE pixels only.
9. **Wheel isolation**: the wheel zooms the world map/tree view instead of scrolling the whole page.
10. **Version comparison**: the version panel defaults to `previous → current`; choose either side separately or click **Compare previous**. Terrain always remains current, with green added, red removed, blue moved, and orange changed overlays. Marker and tile changes can be toggled independently. Counts use the complete server summary; truncation is explicit, and unknown/partial is never presented as “no changes.”
11. **Server annotations**: right-click empty map space or a device marker to create one; empty-map Y defaults to 64. The editor accepts label, note, color, dimension/XYZ, and an inclusive version range, where `0` means unbounded. Click a pin to view, edit, or confirm deletion. Formal annotations persist on the server across browsers and do not use localStorage.
12. **Read-only access**: guest tokens and Browsing mode may still read versions, diffs, and annotations, but create/edit/delete controls are hidden. Owner mutations continue to use the selected network's existing ACL.
13. **Configuration**: `worldMapSnapshotMode`, `worldMapJourneyMapEnabled`, `worldMapConsentRadiusChunks`, `worldMapOwnerSkipConsent`, and related settings are documented in [Developer Guide §11.26](developer-guide.md#1126-world-map-view-three-source-capture--client-snapshot--sp-direct).

Reliability and cleanup: large jobs are paged and fully reassembled before capture starts; a truncated chunk list is never used. A server keeps at most 32 active jobs, expiring them after 90 minutes idle or 2 hours absolute. Player disconnect, WebAE stop/restart, or a job-send failure removes the unpublished snapshot; consent-skipping owner capture still observes the request cooldown. JourneyMap reads only the exact current-world directory, and symlinked/out-of-root paths or oversized/corrupt PNGs are rejected.

World-map authorization and file boundaries: HD/client uploads require the resource owner, an OP, or a real player authorized by the active capture job for the current AE network; owner UUIDs are canonical lowercase. Manifest/current/tile files validate size, structure, coordinates, layer, and SHA-256, with layers limited to `terrain`/`ae` and quality limited to exact lowercase `low|medium|high|ultra`. Integrated-server direct capture is available only in integrated single-player and binds the pending provider/request; Dynmap proxy world names, zoom, coordinates, perspective, and file paths are constrained to allowed values. Dynmap's separate public proxy is not equivalent to WebAE owner-network access.

### Network Health Diagnostics

The **Diagnostics** page includes a Network Health section for each network you are allowed to view. It uses the same owner/network scope as the network selector and shows the runtime network id plus the stable monitor key (`dim:x:y:z`), the last server-side check, sample age, and evidence for Link registration/reachability, monitor binding, AE Grid storage/crafting/connector availability, and channel usage. Issue rows include a short explanation and a suggested next check.

Statuses are intentionally conservative: **healthy** means the required evidence is present, **degraded** means a warning was observed, **failed** means a known required component is unavailable, and **unknown** means the sample is stale or evidence could not be verified. Unknown is not rendered as healthy. Sampling runs on the server tick (about every 5 seconds); opening or refreshing the page only reads the cached result and never scans the world from the browser request. The feature reports problems only—it does not repair bindings, rebuild a Grid, or change channel configuration. The read-only API form is `GET /api/network/health?network=<id>`; it requires the normal WebAE login and network ACL.

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

Sidebar **Monitor Bindings** shows read-only slots, sources, and GT binding coords. Click **Preview** to render the matching scalar/progress, time series, bar/pie categories, or table rows within a 240-point visual budget (edit remains in-game).

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
- **In-game manual messages**: OPs can enqueue text with `/textech web qq send [group|c2c|channel] <openid> <message>`, inspect connection/queue state with `/textech web qq status`, and start an asynchronous reconnect with `restart`. Queue acceptance is not final QQ delivery.
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
  - **Data-table columns & pins**: the widget editor independently controls icon, name, amount, registry name, and source-specific columns. The **name** column uses the item's display name, while **registry name** is shown separately. Missing or `null` `columns` means source defaults; `columns=[]` explicitly hides every column and remains empty through render/import/export. Changing the data source clears old source columns and returns to the new source defaults. Focus the pin search to see current-inventory candidates, or search by display name, registry name, or item ID; remove an existing pin before adding another at the server-provided limit.
  - **Whole-page in-game display export**: publishes the dashboard and copies a `textech-webae-display-binding` live binding, with `textech-webae-display-snapshot` as the static fallback. It also pushes a JPEG of the **current browser viewport** (`browser-jpeg`, matches what you see). Keep the WebAE tab open for near-live refresh. Fallback: host Chrome/Edge capture of `/embed/dashboard` (`spa-jpeg`; optional `webDisplayChromePath` / `WEBAE_CHROME_PATH`). Optional MCEF 1.7.10-0.6 from https://montoyo.net/wd3/?modid=mcef (`mcef`; may not work on modern launchers before 1.10.2). GUI shows frame source/errors; cyan frame means no JPEG yet. It does **not** silently switch to an AWT snapshot. `GET /api/display/{id}/frame-status` reports `hasFrame`/`source`/`error`; `POST /api/display/{id}/frame` accepts browser JPEG.
  - **Shared semantic widget export**: the widget import/export section copies a `textech-monitor-widget-bundle` v1 in current Dashboard order. It includes at most 36 `statCard`, `progressBar`, `gauge`, `lineChart`, `barChart`, `pieChart`, and `dataTable` widgets, and the same **Import from JSON** entry can import the bundle again. WebAE-only `radarChart` and composites such as network health or activity streams stay out of the semantic bundle; use the whole-page live/snapshot surface to show them in-game.
- **Chat**: 💬 icon in sidebar; web messages broadcast in-game as `[Web] <name>: content`. Explicit client screenshots appear as image messages with caption, dimensions, and size; retention-expired files show an unavailable attachment state.
- **Sidebar**: edge button cycles Expanded → Collapsed → Hidden.
- **Top bar**: fixed-width refresh countdown/status next to connection dot.
- **Browsing mode** (Settings → **Browsing mode**): hides layout Settings, Edit, and capture controls on read-only Dashboard, Storage/CPU overview, Power, and Topology surfaces while retaining viewing, search, filters, refresh, and export. Every page except Admin and Settings uses a viewport-bound outer container; genuinely long content remains wheel/touch scrollable inside the page without a browser-edge scrollbar. The preference is stored in `localStorage.webae_browsing_mode` and participates in UI backup/restore.
- **Backup & Restore** (Settings → **Backup & Restore** tab):
  - **Export JSON**: one-shot backup of theme, Browsing mode, per-page layouts (main dashboard, Storage/CPU/Power overviews, topology, quest book, recipes, chat, etc.), refresh and debug preferences; optionally presets and server data (favorites, order templates; alert rules require OP).
  - **Import JSON**: preview affected sections, optional merge mode; reload the page afterward for GridStack layouts to fully apply.
  - **Restore pack defaults**: re-applies server `ui-defaults.json` (instance `TeXTech/WebAE/ui-defaults.json` first, else mod jar bundled file).
- **Pack authors**: export JSON from WebAE Settings, place at `TeXTech/WebAE/ui-defaults.json` or have an Agent write `assets/textech/webae/ui-defaults.json`; first-time visitors with no existing browser prefs apply it automatically. OP can also run `/textech web defaults install <path>`.

---

## 6. Important Notes

- **Localhost only by default**: `bindAddress=127.0.0.1` restricts access to the local machine
- **LAN access risk**: `0.0.0.0` exposes the console to the LAN — use a firewall or SSH tunnel
- **Mandatory auth**: all `/api/` endpoints require a token; force-refresh needs OP/admin grant
- **Layered access**: admins can ban a player account (kick to login), suspend one AE network for everyone including the owner (in-game AE unaffected), or limit guest tokens to selected networks
- **Token security**: owner tokens can read/write within their authorized scope; guest tokens are read-only and limited by the network allowlist. Store tokens securely and revoke them immediately if compromised.
- **Recipes need upload + Fetch**: after OP `/textech web recipes upload`, each player clicks **Fetch recipes** on the Recipes page to sync into browser IndexedDB
- **Icon upload**: OP runs `/textech web icons upload [packName]`; frontend auto-selects the server's most recent pack on first load
- **reload limits**: `/textech web reload` does not rebind the web server; tokens and runtime data files are unaffected

---

> This guide reflects the TeXTech source tree at time of writing.
