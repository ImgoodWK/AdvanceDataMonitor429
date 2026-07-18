# TeXTech WebAE Console Developer Documentation

> Audience: Developers · Last synced: 2026-07  
> Player guide: [User Guide](user-guide.md) · Technical overview: [Technical Documentation](../developer/technical-documentation.md)

---

## Table of Contents

- [1. Overview](#1-overview)
- [2. Architecture](#2-architecture)
- [3. Package Structure](#3-package-structure)
- [4. Configuration](#4-configuration)
- [5. REST API Endpoints](#5-rest-api-endpoints)
- [6. Frontend Architecture](#6-frontend-architecture)
- [7. Thread Model](#7-thread-model)
- [8. Security Model](#8-security-model)
- [9. Network Packets](#9-network-packets)
- [10. Command](#10-command)
- [11. Subsystem Design Summaries](#11-subsystem-design-summaries)
  - [11.1 Storage Monitoring](#111-storage-monitoring)
  - [11.2 Power Monitoring](#112-power-monitoring)
  - [11.3 GT Machine Monitoring](#113-gt-machine-monitoring)
  - [11.4 Recipe Search](#114-recipe-search)
  - [11.5 Pattern Management](#115-pattern-management)
  - [11.6 AE Crafting Orders](#116-ae-crafting-orders)
  - [11.7 Theme System & Dashboard Customization](#117-theme-system--dashboard-customization-phase-21--22)
  - [11.8 Item Icon Cache & Texture Packs](#118-item-icon-cache--texture-packs-phase-31)
  - [11.9 Chat System](#119-chat-system-phase-e)
  - [11.10 Player Info & Skin URL](#1110-player-info--skin-url-phase-e)
  - [11.11 Command-Triggered Upload](#1111-command-triggered-upload-phase-e)
  - [11.12 Dashboard Settings Panel & Per-Widget Colors](#1112-dashboard-settings-panel--per-widget-colors-phase-e)
  - [11.13 Sidebar Three-State & Top-Bar Refresh Status](#1113-sidebar-three-state--top-bar-refresh-status-phase-e)
  - [11.14 Configurable Storage Overview & Standalone CPU Page (Phase 3)](#1114-configurable-storage-overview--standalone-cpu-page-phase-3)
  - [11.15 Configurable Power Page & Anti-Flicker (Phase 4)](#1115-configurable-power-page--anti-flicker-phase-4)
  - [11.16 Sci-Fi Themes + Chart Animations + Icon Rendering (Phase 8)](#1116-sci-fi-themes--chart-animations--icon-rendering-phase-8)
  - [11.17 Network Topology](#1117-network-topology-channel-lanes-ae_budget_v2--disk-persistence)
  - [11.18 Page Visibility Polling (Phase 4b)](#1118-page-visibility-polling-phase-4b)
  - [11.26 World Map View](#1126-world-map-view-phase-ab--ae-overlay)
  - [11.27 Spark Profiler Integration](#1127-spark-profiler-integration)
- [12. Frontend Resources](#12-frontend-resources)
- [13. Debugging and Troubleshooting](#13-debugging-and-troubleshooting)

---

## 1. Overview

The WebAE Console is a **browser-accessible** HTTP management panel embedded within the TeXTech mod. Players can use any modern browser to view real-time AE2 storage status, power/steam consumption rates, GT machine states, search NEI recipes, edit AE2 patterns, and submit batch crafting orders.

Technology stack:
- **Server**: NanoHTTPD lightweight HTTP server, embedded in the Minecraft server process
- **Frontend**: React 18 + TypeScript + Ant Design 5 single-page application (SPA), built with Vite 5, inline SVG charts (`ChartTrendSvg`, etc.), calling REST endpoints via Fetch API; fully offline-packaged (no CDN dependencies)
- **Rendering**: MVC pattern; backend returns only JSON, all UI is rendered by browser-side React

## 2. Architecture

```
Browser (React + Ant Design + inline SVG charts)
    ↓ HTTP REST API (JSON, Bearer Token)
[ NanoHTTPD Server ]
    ↓ CountDownLatch main-thread collection
[ HandlerTick Task Queue ]
    ↓
[ AE2 API / GT API ]
```

Core data flow:

1. **HTTP request arrives**: NanoHTTPD worker thread parses URL/method, dispatched via `WebApiRouter` to the appropriate handler
2. **Main-thread data collection**: All operations requiring Minecraft world state access (AE2 network queries, GT machine state reads, pattern injection, etc.) use `CountDownLatch` to block the HTTP thread, submit the task to `HandlerTick` main-thread task queue, and wake the HTTP thread once done
3. **Snapshot cache**: Frequently polled storage/power data is collected on a timer by `SnapshotScheduler` on the main thread and cached; HTTP requests read directly from cache, avoiding per-request main-thread blocking

## 3. Package Structure

All source code resides under `com.imgood.textech.webae/` (231 files). See `project-structure-details.mdc` for the full per-file listing.

| Sub-package | Files | Purpose |
|-------------|-------|---------|
| `webae/` root | 3 | `WebConsoleServer` + `WebConsoleUrlHelper` + `WebAeLocalDataDir` |
| `webae/auth/` | 5 | Token/login code/middleware/OP check |
| `webae/api/` | 1 | `WebApiRouter` REST dispatcher |
| `webae/api/handler/` | 37+ | REST endpoint handlers (incl. `ServerDiagnosticsHandler`) |
| `webae/cache/` | 2 | Thread-safe TTL snapshot cache + scheduler (storage/GT/pattern/p2p/cells/networks/monitor/scanner) |
| `webae/perf/` | 1 | `WebAePerfProfiler` tick/HTTP/snapshot timing diagnostics |
| `webae/dto/` | 15 | Data transfer objects (Storage/Power/Recipe/Pattern/PatternListEntry/PatternBrowseEntry/NetworkMetricHistory/Order×4/GtMachine×2/Player/ChatMessage) |
| `webae/power/` | 1 | Power sampler (sliding window rate calculation) |
| `webae/snapshot/` | 2 | AE2 + GT machine snapshot collectors |
| `webae/gt/` | 2 | GT machine state reader + binding data structure |
| `webae/metric/` | 2 | Network metrics + downsample util |
| `webae/network/` | 13 | Recipe/icon/world-map HD packets + upload throttle |
| `webae/recipe/` | 9+ | NEI/vanilla/GT recipe collect, disk meta/chunks, lazy cache |
| `webae/pattern/` | 8+ | Pattern encode/inject/browse cache |
| `webae/topology/` | 20 | Network topology + P2P map |
| `webae/worldmap/` | 27 | World map meta/markers/tiles/AE overlay/quality tiers/prefetch progress |
| `webae/craft/` | 2 | Material craft tree (NEI recipe recursion + storage gaps + `patternId`; `GET /api/craft/tree`) |
| `webae/favorites/` | 1 | Recipe/pattern favorites (`TeXTech/WebAE/web-favorites.json`; `GET/PUT /api/favorites`) |
| `webae/planner/` | 1 | Factory Flow / gtnh-flow export interop (`POST /api/planner/export-flow`) |
| `webae/events/` | 1 | SSE subscriber hub (`GET /api/events/stream`; alert broadcast + 15s heartbeat) |
| `webae/icon/` | 31 | Icon packs + multi-fallback rendering |
| `webae/player/` | 4 | Player info store + online-count history sampler + DTO + skin URL resolver |
| `webae/chat/` | 2 | Chat message store (ring buffer) + DTO |
| `webae/debug/` | 1 | WebAE per-feature debug logging (`WebAeDebugLog`, gated by `[debug] webaeXxx`; incl. `webae-perf.log`) |

## 4. Configuration

All Web Console configuration is managed via the `[webConsole]` section, loaded by `ConfigWebaeLoader`.

| Config Key | Type | Default | Range | Description |
|------------|------|---------|-------|-------------|
| `enabled` | boolean | `false` | — | Enable the WebAE Console. Starts listening on the configured port |
| `port` | int | `8090` | 1024-65535 | HTTP server listening port |
| `bindAddress` | string | `127.0.0.1` | — | Bind address. Default localhost only; set to `0.0.0.0` for LAN access |
| `snapshotIntervalSeconds` | int | `30` | 0-3600 | Legacy fallback snapshot interval (seconds). Set 0 to disable. Largely superseded by `refreshIntervalMs` |
| `recipeUploadEnabled` | boolean | `true` | — | Allow OPs to upload NEI recipes via `/admweb recipes upload` (Phase 2 removed the keybind) |
| `recipeCacheMode` | string | `full` | `lru`/`full` | Recipe cache eviction mode. GTNH recommends `full` (no LRU eviction) |
| `maxRecipeCacheMB` | int | `256` | 1-2048 | Approximate max memory (MB) for the recipe cache; evicted in `lru` mode when exceeded, warning-only in `full` |
| `recipeUploadBatchesPerTick` | int | `3` | 1-32 | Recipe upload JSON batches sent per client tick |
| `recipeSearchMinIntervalMs` | int | `1000` | 0-5000 | Minimum interval (ms) between fuzzy recipe searches per owner via `/api/recipes/search?q=` |
| `recipeKeepMemoryAfterUpload` | boolean | `false` | — | Keep full recipe cache in server heap after upload/save; default `false` (clear heap; browsers sync chunks; craft-tree / fallback APIs use `ensureLoaded`) |
| `recipeSyncChunkSize` | int | `400` | 50-2000 | Recipes per browser-sync chunk file / `GET /api/recipes/sync/chunk` |
| `nesqlRepositoryPath` | string | `` | — | NESQL exporter repository root for `/admweb icons import-nesql`. Empty → `<instance>/TeXTech/WebAE/` (same folder as client recipe export) |
| `neiDeepScanItemsPerTick` | int | `0` | 0-512 | NEI item-driven deep scan items per client tick (`/admweb recipes upload deep`; 0 = disabled) |
| `iconMissingDispatchPerTick` | int | `8` | 1-64 | IconMissingQueue lazy-load requests dispatched per server tick |
| `iconDirectRenderEnabled` | boolean | `false` | — | HTTP 404 sync direct render (blocking); off by default — use async queue + SSE |
| `iconDirectRenderTimeoutMs` | int | `3000` | — | Max wait ms for sync direct render |
| `iconDirectRenderPerTick` | int | `4` | — | Sync direct renders per client tick |
| `powerSampleWindowSeconds` | int | `60` | 10-600 | Sliding window (seconds) for power/steam rate calculation |
| `gtDefaultScanRadius` | int | `16` | 1-256 | Default GT machine scan radius for the Data Imprint Tool |
| `refreshIntervalMs` | int | `10000` | 1000-60000 | Unified refresh interval (ms) for server collection and frontend polling |
| `gtRefreshIntervalMs` | int | `30000` | 1000-60000 | GT machine snapshot collection interval (ms) |
| `maxNetworksDisplayed` | int | `5` | 1-20 | Max networks the web console can display simultaneously |
| `tokenLifetimeHours` | int | `0` | 0-8760 | Web auth token TTL in hours. 0 = never expire; >0 rejects after issuedAt + TTL |
| `iconCacheEnabled` | boolean | `true` | — | Enable item icon cache and `/api/icon` serving |
| `iconUploadEnabled` | boolean | `true` | — | Allow explicit C→S upload (`/admweb icons upload` / import); does **not** enable HTTP 404 lazy capture |
| `iconLazyCaptureEnabled` | boolean | `false` | — | When true, GET `/api/icon` miss enqueues `IconMissingQueue` (dispatch only after chat consent) |
| `iconLazyPreferOpOnly` | boolean | `true` | — | Lazy-capture consent offered to OP players only |
| `iconPackEnabled` | boolean | `true` | — | Allow admin zip pack upload (`POST /api/icon/pack`) |
| `metricSampleIntervalMs` | int | `30000` | 1000-60000 | AE network metric history sample interval (ms) |
| `metricSampleWindowSeconds` | int | `300` | 60-3600 | Network metric history sliding window (seconds) |
| `patternBrowsePageSize` | int | `80` | 20-200 | Default pattern browse API page size |
| `patternBrowseMaxTotal` | int | `20000` | 1000-100000 | Max pattern browse entries per network |
| `patternCacheTtlMs` | int | `120000` | 5000-300000 | Pattern browse TTL cache duration (ms) |
| `topologyEnabled` | boolean | `true` | — | Enable `GET /api/network/topology` network topology API |
| `topologyCacheTtlMs` | int | `10000` | 1000-3600000 | Manual topology snapshot capture cooldown (ms; default 10 s) |
| `worldMapSnapshotCooldownMs` | int | `10000` | 1000-3600000 | Manual world map snapshot request cooldown (ms; client capture; default 10 s) |
| `topologySnapshotPersist` | boolean | `true` | — | Persist snapshots under `TeXTech/WebAE/topology/` |
| `topologySimulatedEnabled` | boolean | `false` | — | **Deprecated**: cable-simulation render mode + `GET /api/ae2/cable-texture`; default off |
| `worldMapEnabled` | boolean | `true` | — | Enable `GET /api/worldmap/*` world map API (requires `topologyEnabled`) |
| `worldMapTilePx` | int | `128` | — | **Deprecated**: legacy single tile size; non-128 migrates to medium tier |
| `worldMapMaxQualityTier` | string | `medium` | — | Server cap for quality tier (low/medium/high/ultra) |
| `worldMapDefaultQualityTier` | string | `medium` | — | Default tier when WebAE has no user preference |
| `worldMapBoundsPaddingChunks` | int | `1` | 0-16 | AE network bbox padding in chunks |
| `worldMapTileBudgetPerTick` | int | `1` | 1-32 | Max chunk tiles rendered per tick |
| `worldMapMaxChunks` | int | `512` | 16-4096 | Max chunk tiles per dimension (clamp + `boundsTooLarge`) |
| `worldMapRequireNetworkScope` | boolean | `true` | — | Render tiles only within AE network scope |
| `worldMapViewsEnabled` | string | `flat` | — | Enabled views (comma-separated) |
| `worldMapObliqueEnabled` | boolean | `false` | — | Master oblique switch; when false, flat only (AND with `worldMapViewsEnabled`) |
| `worldMapZoomLevels` | int | `1` | 1-6 | Zoom pyramid depth; default 1 (z0 only; viewport scales) |
| `worldMapClientHdEnabled` | boolean | `true` | — | Allow client GL tile capture upload |
| `worldMapClientHdBudgetPerTick` | int | `3` | 1-8 | Client GL tile render budget per tick |
| `worldMapClientCaptureMode` | string | `when_online` | — | Client capture policy: `off` / `ultra_only` / `when_online` |
| `worldMapClientCaptureRadius` | int | `2` | 0-8 | Proactive flat capture radius in chunks (0=off) |
| `worldMapClientCaptureBudgetPerTick` | int | `1` | 0-4 | Proactive captures per client tick |
| `worldMapProgressiveFallback` | boolean | `true` | — | Serve lower tier / Dynmap crop while target tier renders |
| `worldMapClientHdTimeoutMs` | int | `5000` | 1000-30000 | Client GL wait before server fallback |
| `worldMapAeOverlayEnabled` | boolean | `true` | — | Render AE overlay layer |
| `worldMapAeOverlayIncludeCables` | boolean | `true` | — | Include cables in AE overlay |
| `worldMapAeOverlayQualityTier` | string | `medium` | — | AE overlay tile quality (decoupled from terrain quality) |
| `worldMapAeQualityBoost` | boolean | `false` | — | +1 terrain tier for AE-device chunks (default off) |
| `worldMapRenderEngine` | string | `uv` | — | Flat engine: `legacy` (average color) / `uv` (texture UV + biome/lighting) |
| `worldMapObliqueEngine` | string | `ray` | — | Oblique engine: `legacy` (column painter) / `ray` (per-pixel ray trace) |
| `worldMapChunkPadding` | int | `1` | 0-4 | Chunk padding for cross-boundary snapshots (1 = 3×3) |
| `worldMapTextureCacheMax` | int | `2048` | 256-8192 | UV/ray block texture LRU cap |
| `worldMapRayBudgetPerTick` | int | `1` | 1-32 | Per-tick chunk budget for oblique ray renders |
| `worldMapRenderThreads` | int | `0` | 0-32 | Background tile render threads; `0` = auto (CPU cores / 2, min 1) |
| `worldMapMaxRayDepth` | int | `3` | 1-8 | Max transparent layers per ray pixel |
| `worldMapLowTierObliqueEngine` | string | `legacy` | — | low/medium tier oblique fallback: `legacy` / `ray` |
| `worldMapZoomBudgetPerTick` | int | `4` | 1-64 | Max parent zoom tile syntheses per server tick |
| `worldMapBlockPatchesEnabled` | boolean | `true` | — | Enable JSON/built-in block patches for oblique ray (stairs, slabs, GT) |
| `worldMapServerAtlasEnabled` | boolean | `true` | — | Bake server block-face textures into one atlas grid |
| `worldMapServerAtlasPx` | int | `2048` | 256-4096 | Server atlas edge length in px (multiple of 16) |
| `questEnabled` | boolean | `true` | — | Enable BetterQuesting quest book page and read APIs (no-op without BQ) |
| `questSubmitEnabled` | boolean | `true` | — | Allow Web item/fluid submit from AE |
| `questClaimEnabled` | boolean | `true` | — | Allow claiming pure item/choice rewards into the selected AE network; mixed/non-item rewards stay in-game only |
| `questChainSubmitEnabled` | boolean | `true` | — | Allow chain submit (walk prerequisites; optional craft-then-submit) |
| `questSubmitMaxStacks` | int | `64` | 1-512 | Max distinct item stacks per submit |
| `questCraftWaitTimeoutMs` | int | `120000` | 5000-600000 | Craft-then-submit / chain craft wait timeout (ms) |
| `questEscrowEnabled` | boolean | `true` | — | AE virtual escrow: lock before submit/detect; craft path pre-locks available and appendLocks on craft complete |
| `questEscrowTimeoutMs` | int | `120000` | 5000-600000 | Escrow session timeout (ms); auto-return locked stacks to AE |
| `questFluidAllContainersOption` | boolean | `false` | — | When true, Web UI may count all fluid containers toward equivalence; when false, only GT/IC2 cells |
| `questCacheTtlSec` | int | `300` | 30-3600 | Quest-line definition cache TTL (seconds); progress ~1/10 |
| `sparkEnabled` | boolean | `true` | — | Enable the WebAE Spark page/API when the Spark mod is installed; force unavailable when absent |
| `sparkMaxHistory` | int | `50` | 1-500 | Number of Spark run records retained in `TeXTech/WebAE/spark-history.json` |
| `sparkDefaultDurationSeconds` | int | `30` | 5-600 | Default Spark profiler duration requested by the WebAE page (seconds) |
| `sparkMaxDurationSeconds` | int | `300` | 5-600 | Hard maximum duration for a Spark profiler run started from WebAE (seconds) |

**GTNH / large-pack TPS-friendly defaults** (first-time cfg generation; values below thresholds are overridden in memory at startup with a WARN, not written back to disk):

| Key | Default | Notes |
|-----|---------|-------|
| `refreshIntervalMs` | `10000` | Snapshot / polling interval |
| `gtRefreshIntervalMs` | `30000` | GT machine snapshots |
| `patternCacheTtlMs` | `120000` | Pattern pre-collection |
| `metricSampleIntervalMs` | `30000` | Dashboard metric sampling |
| `worldMapTileBudgetPerTick` | `1` | World map chunk budget per tick |
| `worldMapMaxQualityTier` / `worldMapAeOverlayQualityTier` | `medium` | Map and AE overlay tiers |
| `worldMapAeQualityBoost` | `false` | Disable AE chunk quality bump |
| `dashboardMaxTracksGlobal` / `dashboardMaxItemTracks` | `16` / `8` | Fewer dashboard tracks |
| `recipeSearchMinIntervalMs` | `1000` | Recipe fuzzy search throttle |
| `recipeKeepMemoryAfterUpload` | `false` | Clear heap after save; browsers Fetch chunks into IndexedDB |
| `recipeSyncChunkSize` | `400` | Sync chunk size |

**Security note**: Default binds to `127.0.0.1`. All `/api/` endpoints enforce Bearer authentication (no opt-out). Changing `bindAddress` to `0.0.0.0` exposes the console to the LAN — use a firewall or SSH tunnel. Admin-only endpoints (refresh) additionally require OP level >= 2.

**Debug toggle**: `[debug] debugWebae` (default false, loaded by `ConfigDebugLoader`) gates WebAE diagnostic logging. It currently drives `NeiRecipeCollector` to print handler counts, total numRecipes, and pre/post-filter recipe counts during collection — useful for diagnosing "recipe search returns no results" issues.

## 5. REST API Endpoints

| Method | Path | Auth | Admin? | Description |
|--------|------|------|--------|-------------|
| POST | `/api/auth/exchange` | **No** | No | Exchange 6-digit login code for owner token (5 min TTL, single-use; body `{"code":"123456"}`) |
| GET | `/api/auth/login` | Yes | No | Post-auth info (playerUuid) |
| GET | `/api/config` | Yes | No | Public client-readable config (refreshIntervalMs, gtRefreshIntervalMs, maxNetworksDisplayed, tokenLifetimeHours, themePresets [legacy, mirrors themeColors], themeColors, themeLayouts, pageStyles) |
| GET | `/api/spark` | Yes | No | Spark availability, current run, and history summaries; 503 when Spark is absent or `sparkEnabled=false` |
| GET | `/api/spark/history/{id}` | Yes | No | Full output summary and Viewer URL for one Spark history record |
| POST | `/api/spark/profile` | Yes | **Admin** | Body `{durationSeconds}`; invokes Spark `profiler start --timeout`; only one active run is allowed |
| POST | `/api/spark/stop` | Yes | **Admin** | Requests profiler stop and waits for Spark's final output |
| DELETE | `/api/spark/history/{id}` | Yes | **Admin** | Deletes retained Spark metadata; does not delete the remote Spark Viewer result |
| GET | `/api/ui-defaults` | **No** | No | Pack/mod default WebAE UI settings JSON (instance `TeXTech/WebAE/ui-defaults.json` first, else jar `assets/textech/webae/ui-defaults.json`; `defaults:null` when absent) |
| GET | `/api/networks` | Yes | No | List available AE2 networks; **HTTP cache-only** (`cached`/`timestamp`); owner-scoped `SnapshotScheduler` pre-collect; `?refresh=1` async rebuild |
| GET | `/api/storage?network=<id>` | Yes | No | Cached storage snapshot (stale fallback with `cached:false`) |
| GET | `/api/storage/batch?networks=0,1,2` | Yes | No | Batched cached storage read |
| GET | `/api/storage/items?network=<id>&cursor=&limit=200&sort=amount_desc&search=` | Yes | No | Cursor-paginated items (in-memory slice; `nextCursor`/`totalEstimate`/`snapshotVersion`; stale cursor → 409) |
| GET | `/api/storage/fluids?network=<id>&cursor=&limit=&sort=&search=` | Yes | No | Cursor-paginated fluids (same) |
| GET | `/api/storage/essentia?network=<id>&cursor=&limit=&sort=&search=` | Yes | No | Cursor-paginated essentia (same) |
| POST | `/api/refresh?network=<id>` | Yes | **Yes (OP)** | Force storage re-collect (returns 403 for non-OP) |
| POST | `/api/refresh/batch?networks=0,1,2` | Yes | **Yes (OP)** | Batched force storage re-collect |
| GET | `/api/power?network=<id>` | Yes | No | Cached power/steam snapshot |
| GET | `/api/power/batch?networks=0,1,2` | Yes | No | Batched cached power read |
| POST | `/api/power/refresh?network=<id>` | Yes | **Yes (OP)** | Force power cache invalidation |
| POST | `/api/power/refresh/batch?networks=0,1,2` | Yes | **Yes (OP)** | Batched power cache invalidation |
| GET | `/api/gt/machines?network=<id>` | Yes | No | Cached GT machine list snapshot |
| GET | `/api/gt/machines/batch?networks=0,1,2` | Yes | No | Batched cached GT read |
| POST | `/api/gt/machines/refresh?network=<id>` | Yes | **Yes (OP)** | Force GT re-collect |
| POST | `/api/gt/machines/refresh/batch?networks=0,1,2` | Yes | **Yes (OP)** | Batched GT re-collect |
| GET | `/api/recipes/sync/manifest` | Yes | No | Recipe disk catalog metadata (revision/chunkCount/handlers; no full in-memory load) |
| GET | `/api/recipes/sync/chunk?index=N` | Yes | No | Read `recipe-chunks/chunk-NNNN.json` (browser IndexedDB sync) |
| GET | `/api/recipes/handlers` | Yes | No | List recipe handler types with counts (memory or meta) |
| GET | `/api/recipes/status` | Yes | No | Recipe cache status (disk/memory/lazy) |
| GET | `/api/recipes/browse?handler=<id\|all>&offset=&limit=` | Yes | No | Server paginated browse fallback (`ensureLoaded`; response includes `total`) |
| GET | `/api/recipes/search?output=<name>&handler=<id>` | Yes | No | Exact search by output registry name (server fallback) |
| GET | `/api/recipes/search?input=<name>&handler=<id>` | Yes | No | Exact search by input registry name (server fallback) |
| GET | `/api/recipes/search?q=<text>&handler=&offset=&limit=` | Yes | No | Fuzzy search (server fallback; paginated, response includes `total`) |
| GET | `/api/recipes/suggest?q=<text>&limit=` | Yes | No | Item autocomplete (server fallback; registry + display name) |
| GET | `/api/recipes/{handlerId}/{recipeIndex}` | Yes | No | Get single recipe (includes gridSlots / GT fields; server fallback) |
| POST | `/api/order` | Yes | No | Submit single crafting order; body may include optional `cpuName` and `patternId` (takes priority over itemName) |
| POST | `/api/order/batch` | Yes | No | Submit batch crafting orders (items may include `patternId` for pattern-based orders; body may include `cpuName`) |
| GET | `/api/order/list` | Yes | No | List active + history (`{success,orders,history}`; progress via `ICraftingLink` + CPU step counters by networkId; history owner-scoped) |
| GET | `/api/order/status?jobId=<id>` | Yes | No | Query a single order; unknown jobId → 404 (no forged completed) |
| POST | `/api/order/cancel` | Yes | No | Cancel all pending/active orders for the current player |
| GET | `/api/order/templates` | Yes | No | List batch order templates for the current owner (`TeXTech/WebAE/web-order-templates.json`, isolated by ownerUuid) |
| PUT | `/api/order/templates` | Yes | No | Replace the owner's template list; body `{ "templates": [{ id, name, cpuName?, networkId, items:[{ itemName, amount, patternId? }], updatedAt }] }`; validates non-empty name, items≥1, amount≥1, ≤50 items per template |
| GET | `/api/interfaces?network=<id>` | Yes | No | Enumerate ME interfaces (coords, slot states, `machineRecipeType`, `existingPatterns` summaries) |
| POST | `/api/pattern/encode` | Yes | No | Encode a pattern; body may include `networkId` + `consumeBlank` (defaults true when `networkId≥0`); deducts 1 blank pattern from AE network on main thread; returns `code: NO_BLANK_PATTERN` when insufficient |
| POST | `/api/pattern/inject` | Yes | No | Inject a pattern into an interface; body includes `consumeBlank` (default true; pass false if encode already consumed a blank) |
| GET | `/api/patterns?network=<id>` | Yes | No | List all ME interface patterns (rich NBT); **HTTP cache** `patterns_rich`; invalidated with browse on mutations |
| GET | `/api/patterns/browse?network=<id>&q=&offset=&limit=&source=both\|grid\|interface` | Yes | No | Paginated Grid + Interface browse; **HTTP cache-only read** (`cached`/`timestamp`); background pre-collect via `SnapshotScheduler` at `webPatternCacheTtlMs`; offline owner uses `WebAeOwnerContext.getOwnerPlayerOrFake`; `PatternBrowseInvalidationGridCache` listens for `MENetworkCraftingPatternChange` + Web PUT/DELETE/inject call `invalidateAll` |
| POST | `/api/patterns/browse/refresh?network=<id>` | Yes | **Yes (OP)** | Admin force-rebuild browse cache (main-thread collect; GET never blocks) |
| GET | `/api/patterns/grid/<gridKey>?network=<id>` | Yes | No | Single grid pattern detail (`gridKey`=`grid:<index>`, same ordering as browse); full inputs/outputs |
| GET | `/api/patterns/<id>` | Yes | No | Single pattern detail (`id` = `<x>:<y>:<z>:<dim>#<slot>`) |
| DELETE | `/api/patterns/<id>` | Yes | **Yes (OP)** | Delete pattern from interface slot; fires `MENetworkCraftingPatternChange` |
| PUT | `/api/patterns/<id>` | Yes | **Yes (OP)** | Write back edited pattern NBT to slot |
| GET | `/api/icon?item=<itemId>&pack=<pack>&meta=<int>&size=<16\|32\|64>` | Yes | No | Get an item/fluid icon PNG (with ETag + Cache-Control); disk miss returns 404 immediately and enqueues async fill (sync direct render off by default) |
| GET | `/api/ae2/cable-texture?type=smart\|covered\|dense` | Yes | No | **Deprecated** (requires `topologySimulatedEnabled=true`): Fluix cable PNGs for cable simulation; 503 when disabled |
| GET | `/api/icon/packs` | Yes | No | List installed texture packs; response includes a `defaultPack` field (the most recently uploaded pack, or null) |
| GET | `/api/icon/sync/manifest?pack=&mode=` | Yes | No | Pack revision metadata (full-pack sync) |
| GET | `/api/icon/sync/bulk?pack=&mode=` | Yes | No | Full-pack zip (in-memory build; Settings manual / user-enabled auto-sync only — not on login by default) |
| POST | `/api/icon/pack?pack=<packName>` | Yes | **Yes (OP)** | Upload a texture-pack zip (Zip Slip protected) |
| GET | `/api/chat/history?limit=<n>&since=<ts>` | Yes | No | Fetch chat history (default limit=200, optional since for incremental) |
| GET | `/api/chat/since=<ts>` | Yes | No | Incremental fetch of messages since a timestamp |
| POST | `/api/chat/send` | Yes | No | Send a chat from the web (body `{content}`; reads playerUuid from token, appends a `source=web` message and broadcasts `[Web] <name>: <content>` in-game; rejects banned/empty/over-long content) |
| GET | `/api/players` | Yes | No | Returns `{online:[...],offline:[...]}`; each entry has uuid/name/online/onlineMs/lastLogin/lastLogout/skinUrl |
| GET | `/api/players/since=<ts>` | Yes | No | Incremental fetch of player online-state changes |
| GET | `/api/players/online/history` | Yes | No | Online player count trend `[{"ts":,"count":}]` (~60 min, 30s samples, for dashboard widgets) |
| GET | `/api/players/locations` | Yes | No | **Phase6** Online player coordinates `{locations:[{uuid,name,x,y,z,dim,online}]}` |
| POST | `/api/auth/guest-invite` | Yes | No (owner) | Shareable guest token `{token,url}`; optional body `networkKeys:string[]` (omit = all nets); disabled owner → `401 webae_disabled` |
| GET | `/api/admin/players` | Yes | **Yes (admin grant/OP)** | Player summaries (disabled, networkCount, cache stats) |
| GET | `/api/admin/players/:uuid/access` | Yes | **Yes** | Owned nets (`networkKey`/suspended) + guest-token ACL detail |
| POST | `/api/admin/players/:uuid/disable` | Yes | **Yes** | Account ban: revoke all tokens/login codes, clear cache, stop scheduling; client gets `401 webae_disabled` |
| POST | `/api/admin/players/:uuid/enable` | Yes | **Yes** | Re-enable account |
| POST | `/api/admin/players/:uuid/clear-cache` | Yes | **Yes** | Clear snapshot/topology/map caches for player |
| POST | `/api/admin/players/:ownerUuid/networks/:networkKey/suspend` | Yes | **Yes** | Suspend one net for everyone incl. owner (stable key `dim:x:y:z`); APIs return `403 network_suspended` |
| POST | `/api/admin/players/:ownerUuid/networks/:networkKey/resume` | Yes | **Yes** | Resume network |
| POST | `/api/admin/players/:actorUuid/acl` | Yes | **Yes** | body `{ownerUuid,networkKey,effect:deny\|allow}` actor-level deny overlay |
| POST | `/api/admin/players/:actorUuid/guest-tokens/revoke` | Yes | **Yes** | body `{token}` revoke guest token |
| GET | `/api/network/metrics?network=<id>` | Yes | No | AE network metric history (item/fluid/essentia/bytes/CPU busy ratio/GT active count rolling window, `NetworkMetricSampler`) |
| GET | `/api/network/metrics/fluids?network=<id>&fluids=water,lava` | Yes | No | Pinned fluid amount trends (limits via `dashboardMaxFluidTracks` / per-request `dashboardMaxTracksPerWidget`) |
| GET | `/api/network/metrics/items?network=<id>&items=mod:item,...` | Yes | No | Pinned item amount trends (missing in AE → 0; limits via cfg) |
| GET | `/api/network/metrics/entities?network=<id>&entities=cpu:Name,gt:0:1:2:3&fields=...` | Yes | No | Pinned CPU/GT numeric trends (default `craftingProgress` / `progressPercent`) |
| GET | `/api/network/topology?network=<id>&mode=logical\|spatial` | Yes | No | AE network topology graph (logical=`ae_budget_v2` channel budget: dense 32→4×smart 8 + role pods; spatial=spatial bins; not real cabling; TTL via `topologyCacheTtlMs`; 503 when `topologyEnabled=false`) |
| GET | `/api/network/cells?network=<id>` | Yes | No | Network cell byte summary + infinite cell detection (I5); **HTTP cache-only** |
| GET | `/api/network/balance?networks=0,1&minSurplus=&minShortage=&limit=` | Yes | No | Cross-network storage balance suggestions (read-only; compares cached snapshots; Phase 8) |
| GET | `/api/network/p2p?network=<id>` | Yes | No | P2P tunnel map by frequency (Phase 10; requires `topologyEnabled`); **HTTP cache-only** |
| GET | `/api/worldmap/progress?network=<id>&view=&dim=&quality=` | Yes | No | Batch tile prefetch progress (per-chunk terrain/ae status) |
| GET | `/api/worldmap/meta?network=<id>` | Yes | No | World map meta (dimension bboxes, tilePx, qualityTiers[], max/defaultQualityTier, markerCount, `boundsTooLarge`; **Phase A**; requires logical snapshot; 503 when `worldMapEnabled=false` or `topologyEnabled=false`) |
| GET | `/api/worldmap/markers?network=<id>` | Yes | No | Flattened AE device markers (from logical snapshot; `code:no_logical_snapshot` when missing) |
| GET | `/api/worldmap/tiles/{view}/{dim}/{cx}/{cz}.png?network=&quality=` | Yes | No | Terrain tile PNG (transparent outside scope; enqueues render on miss) |
| GET | `/api/worldmap/tiles/{view}/ae/{dim}/{cx}/{cz}.png?network=&quality=` | Yes | No | AE overlay tile PNG |
| POST | `/api/worldmap/invalidate?network=&views=&layer=&quality=` | Yes | No | Invalidate cache and batch-prefetch terrain+ae for allowed chunks |
| GET | `/api/craft/tree?item=&amount=&network=&maxDepth=` | Yes | No | Material tree (`required`/`inStock`/`toCraft`/`patternId`; Phase 4.1) |
| GET/PUT | `/api/favorites` | Yes | PUT No (guest read-only) | Favorites (`web-favorites.json`; Phase 4.2) |
| GET/POST | `/api/planner/plans` | Yes | POST No (guest) | Plan list / create (`plans.json`; Phase 4.4) |
| PATCH/DELETE | `/api/planner/plans/<id>` | Yes | No (guest) | Edit title / complete / delete plan |
| POST | `/api/planner/export-flow` | Yes | No (guest) | Material tree export (`factory-flow-v1` / `gtnh-flow-v1`; Phase 4.3) |
| GET | `/api/events/stream?token=` | Yes | No | SSE alert push + 15s heartbeat (Phase 9; Bearer or query token) |
| GET | `/api/monitor/preview?dim=&x=&y=&z=&slot=` | Yes | No | Monitor slot line-chart preview (Phase 11) |
| GET | `/api/scanner/blocks?type=&q=` | Yes | No | Link Scanner mirror; full list cached, `type`/`q` filtered on HTTP thread |
| GET | `/api/monitor/bindings` | Yes | No | Data monitor Link/GT binding view; **HTTP cache-only** |
| POST | `/api/assistant/query` body `{text,locale}` | Yes | No | Web assistant rule intent parsing (2s rate limit; no AI keys; I4) |
| GET | `/api/pocket/overview` | Yes | **Yes (OP)** | Minimal read-only dimensional pocket overview (no item contents; I6) |
| GET | `/api/quests/meta` | Yes | No | BQ availability, toggles, version, line count |
| GET | `/api/quests/lines` | Yes | No | Quest line (chapter) list |
| GET | `/api/quests/lines/{lineId}` | Yes | No | Line graph nodes+edges (icons, `mainQuest`, cross-line ghosts) |
| GET | `/api/quests/progress` | Yes | No | Full progress snapshot (includes `updatedAt`) |
| GET | `/api/quests/search?q=` | Yes | No | Search quests by name (max 50) |
| GET | `/api/quests/{id}` | Yes | No | Quest detail (`tasks`/`rewards` from BQ int-keyed `DBEntry` TaskStorage/RewardStorage; `requirementQuestIds`; `prerequisites[]`/`dependents[]`; `webClaimable`/`claimBlockReason`; choice rewards expanded per option) |
| GET | `/api/quests/{id}/analysis?network=` | Yes | No | AE stock vs task step analysis; optional `includeAllFluidContainers=true` |
| GET | `/api/quests/{id}/chain-plan?network=` | Yes | No | Chain-submit topological plan; optional `includeAllFluidContainers` |
| POST | `/api/quests/{id}/detect` | Yes | No | Retrieval hold-detect; optional body `{networkId,includeAllFluidContainers}`; with AE uses escrow + offline completeRetrieval (guest 403) |
| POST | `/api/quests/{id}/submit` | Yes | No | Submit items/fluids; body `{networkId,dryRun,steps?,includeAllFluidContainers?}`; locks via escrow when enabled (guest 403) |
| POST | `/api/quests/{id}/claim` | Yes | No | Claim pure item/choice rewards into AE; body `{networkId,choices:{rewardId:choiceIndex}}`; AE capacity precheck keeps UNCLAIMED on failure; needs `questClaimEnabled` (guest 403) |
| POST | `/api/quests/{id}/submit-craft` | Yes | No | Craft missing then submit; pre-lock then appendLock; job.phase includes `crafting`/`locking`/`escrow_failed`/`done` (guest 403) |
| POST | `/api/quests/{id}/submit-chain` | Yes | No | Chain submit; body `{networkId,dryRun,skipMissing,craftMissing,includeAllFluidContainers?}`; needs `questChainSubmitEnabled` |
| GET | `/api/quests/submit-jobs/{jobId}` | Yes | No | Poll craft-then-submit job |
| GET | `/api/quests/chain-jobs/{jobId}` | Yes | No | Poll chain-submit job |

Quest submit internals: `QuestFluidEquivalence` (fluid↔cell equivalence), `QuestInventoryEscrow` (lock/lockPartial/appendLock/release/commit/timeout), `QuestSubmitService` (DETECT via `completeRetrievalTask`; cell DETECT may use synthetic stacks from free fluid), `QuestCraftOrchestrator`/`QuestChainOrchestrator` (pre-lock → appendLock → `submitFromEscrow`). Progress is written for the Token owner only; party members are not synced.

`QuestTaskDeserializer`: `bq_standard:retrieval` + `consume=true` → web action **SUBMIT** (symmetric to fluid `bq_standard:fluid` + `consume=true`); hold-detect retrieval/fluid use `consume=false` → **DETECT**. `BqApiFacade.completeRetrievalTask` refuses the `forceComplete` fallback when `consume=true`; those tasks must go through `submitItem`/`submitFluid` + escrow commit.

Fluid/cell equivalence (`QuestFluidEquivalence`; GT/IC2 by default; `questFluidAllContainersOption` + request flag expands to all FCR containers):
- **DETECT (item cell)**: filled cells + `floor(free mB / capacity)`; escrow real cells + fluid; BQ gets synthetic filled stacks.
- **SUBMIT (item cell)**: prefer filled cells; else empty + fluid fill; free fluid alone is not enough.
- **True fluid tasks**: available = free + cell mB; drain needed mB from cells first; return empty cell + remainder fluid.

Fluid-cell display (`iconItemId` + `displayName`): filled cells keep `mod:id:meta`. Only FluidDisplay and true `requiredFluids` set `iconItemId=fluid:<name>`.

Local WebAE QA: the first BetterQuesting tab from dev fixtures is **WebAE Test Lab** (`dev-fixtures/betterquesting/`, tracked in Git, **not** packed into the mod jar; see that folder’s `README-dev.md`).

| GET | `/api/alerts` | Yes | No | Active automation alerts + `web-alerts.json` rules mirror (A1–A5); includes `canEditRules` (OP) |
| GET | `/api/alerts/rules` | Yes | No | Rules only + `canEditRules` |
| PUT | `/api/alerts/rules` body `WebAlertsConfig` | Yes | **OP** | Validate and write `TeXTech/WebAE/web-alerts.json`; `WebAlertEngine` reads on next tick; **webhook URLs masked** (`***` + last 4 chars) |
| GET | `/api/server/health` | Yes | No | Server TPS / MSPT (same as `/forge tps` Overall) / online players / uptime + 300s rolling history |
| GET | `/api/server/diagnostics` | Yes | No | WebAE perf diagnostics: tick phases, `HandlerTick` queue depth, snapshot collect timings, `snapshotWorkerBusy` / `snapshotTimeouts` / `snapshotSkippedBusy` / `snapshotSkippedQueue`, slow HTTP / top routes, config summary; polled by Diagnostics page |
| GET | `/api/oc/summary` | Yes | No | OC read-only summary (item types, CPU busy, active orders, TPS; 1 req/s; see [oc-integration.md](oc-integration.md)) |
| GET | `/api/search?q=&limit=&offset=&types=&network=` | Yes | No | Aggregated read-only search (storage/recipe/gt/pattern; 500ms rate limit; pagination; Phase 4a) |

Authentication: `Authorization: Bearer <token>` header. All `/api/` endpoints require it; admin-only endpoints additionally check the player's OP level (>= 2) via `WebAuthOpCheck`.

401 responses include a `code` field: `missing_token`, `invalid_format`, `empty_token`, `token_expired` (only when `tokenLifetimeHours > 0`), `invalid_token`, or `webae_disabled` on account ban (also mirrors `error`; SPA clears token and returns to login).

Network denials: `403` + `code:network_suspended` (includes owner) or `network_access_denied` (guest allowlist/ACL). Stable network key is monitor coords `dim:x:y:z` (not runtime `networkId`). Stores: `web-network-suspends.json`, `web-network-acl.json`; checks in `webae/access/WebAeNetworkAccess`. `WebAeNetworkKeys.fromNetworkId` / `toNetworkId` and HTTP-side `findNetworkGroups` must **not** touch `World` (registry coordinate keys / stale connector cache only); world freshness is maintained on the server thread via `getNetworks` / `refreshHealth`.

## 6. Frontend Architecture

The frontend source lives at `webae-frontend/` in the project root, built with Vite 5 + React 18 + TypeScript + Ant Design 5. The build output goes to `src/main/resources/assets/textech/webae/` and is served by NanoHTTPD.

```
webae-frontend/               # Frontend source (permanent project part)
├── package.json              # Dependencies & build scripts
├── vite.config.ts            # Vite config (outDir → resources/webae, base './')
├── tsconfig.json             # TypeScript config
├── index.html                # SPA entry HTML
└── src/
    ├── main.tsx              # React root render
    ├── App.tsx               # ConfigProvider + Login/AppLayout switch
    ├── api/client.ts         # Fetch wrapper (Bearer auth + error handling)
    ├── types/dto.ts          # TypeScript interfaces mirroring Java DTOs
    ├── i18n/                 # zh + en dictionaries + I18nProvider hook
    ├── context/AppContext.tsx  # Global state (auth/theme/settings/presets/connection/refresh)
    ├── theme/                # 128 colors + 30 layouts + 126 page styles (7 chrome + 12 composition + 20 bold + 20 batch2 + 50 batch3 + 12 Printstream batch4 + 5 Auraeco batch5) + antd theme builder + preview var helper
    ├── hooks/                # useLocalStorage / useInterval / usePageVisibility / useVisibilityAwarePolling / useSnapshotData / usePlayers / useWebAlerts / useNetworkMetrics / useGlobalSearch / useWorldMapData / useWorldMapTileLoader / useWorldMapProgress
    ├── utils/                # formatNumber / icon URL / presets / dashboardResolve / overviewDataSources / powerDataSources / cpuColumns / recipe
    ├── components/           # Login / Icon / Layout(Sidebar/TopBar/AppLayout/navConfig/PageShell) /
    │                         # common(SettingRow/SelectableListRow/SelectableCard) /
    │                         # recipes(HandlerCategoryFilter/RecipeToolbar/RecipeResultList/
    │                         # RecipeThumbnailCard/RecipeDetailCard/RecipeMergedCard/RecipeDetailModal/
    │                         # RecipeGrid/ItemRecipePanel; utils/recipe.ts groupByPrimaryOutput) /
    │                         # patterns(PatternListSidebar/PatternEditorForm/PatternInjectPanel/
    │                         # PatternOrderCard/PatternDetailModal/VirtualPatternGrid/
    │                         # VirtualProductGrid — Phase 7 AE order browse + virtual scroll)
    │                         # ordering(OrderQueryTab/OrderPatternsTab/OrderHistorySection — AE ordering split)
    ├── pages/                # Dashboard / Storage / Cpu / Power / GtMachines / Recipes /
    │                         # PatternEditor / AeOrdering / Chat / Settings / Spark / QuestBook
    │                         # components/quest (list/graph/detail/step+chain submit)
    └── styles/global.css     # Base styles + advanced mode effects + GridStack overrides + widget 9-grid alignment
```

Build command: `cd webae-frontend && npm install && npm run build`

See `.cursor/rules/webae-frontend.mdc` for frontend conventions.

Technical notes:
- **Build tool**: Vite 5 + `@vitejs/plugin-react`; fully offline bundle (antd/react)
- **UI**: Ant Design 5 only; charts via inline SVG/CSS (`ChartTrendSvg`, `WidgetContent` categorical charts, GT page `GtSummaryCharts`); Dashboard drag/resize via `gridstack` (layout engine exception)
- **Pages**: all 20 business pages wrapped in `PageShell` (including AE ordering and pattern editor)
- **Shared UI**: `navConfig.ts` navigation; `common/SettingRow`, `SelectableListRow`, `SelectableCard`; list/nav/pattern-editor CSS utilities in `global.css` (`.webae-pattern-slot`, `.webae-scroll-panel`, etc.); `patternEntryIconId` for pattern icon IDs; all three Settings Drawers use `SettingRow`
- **Global state**: `AppContext` — token/network/lang/theme/numberFormat/presets/iconPack/localIconPack/sidebarMode/displayMode/autoRefresh/pauseRefreshWhenHidden/refreshPaused/connection
- **Token stability**: in-memory `WebAuthToken` cache + debounced atomic disk flush; frontend `tokenRef` + silent re-login on 401; heartbeat uses raw fetch
- **Icon dual-track**: IndexedDB local-first + server disk; miss → async client fill (SSE); `iconAutoSync` default false; Settings full-pack sync + fill-visible; see `.cursor/rules/webae-icon-performance.mdc`; resolution: local → server → abbreviation
- **Hooks**: `useIconPackAutoSync` (optional bulk), `iconPrefetch.ts` (local warm / `fillMissingIconsFromServer`), `visibleIconRegistry.ts`
- **Recipe local-first**: OP upload → server disk (`web-recipes.json` + meta + chunks) → player clicks **Fetch recipes** on Recipes page → browser IndexedDB → local browse/search; `useRecipeSync` + `recipeLocalStore`; server browse/search is fallback (`ensureLoaded`)
- **Recipe sync API**: `GET /api/recipes/sync/manifest`, `GET /api/recipes/sync/chunk?index=`; browse/search/suggest remain as fallbacks
- **Preset system**: quick-switch profiles (theme/layout/lang/number format/icon pack/sidebar/main dashboard); localStorage `webae_presets`
- **Full backup** (`utils/uiSettingsBundle.ts` + Settings **Backup & Restore** tab): `WebUiSettingsBundle` v1 (`format:textech-webae-ui-settings`) aggregates all localStorage page prefs; optional server favorites/order templates/alert rules (OP); excludes token and IndexedDB icon binaries
- **Default layout**: `WebUiDefaultsStore` + public `GET /api/ui-defaults`; `/admweb defaults status|install|clear`; `AppContext` auto-applies on first visit when no prior `webae_*` localStorage exists
- **Pack / Agent workflow**: export JSON from Settings → write `TeXTech/WebAE/ui-defaults.json` or `assets/textech/webae/ui-defaults.json`; optionally sync `presets.ts` `DEFAULT_*` as code fallback
- **Dashboard**: GridStack 12-column grid; layout persisted as x/y/w/h in `webae_dashboard_config`
- **WCAG**: skip link, aria-live (connection/countdown), focus-visible, icon button aria-labels
- **Static serving**: NanoHTTPD maps all `/` paths to static file serving from classpath `assets/textech/webae/`

## 7. Thread Model

Core challenge: Minecraft server logic **must execute on the main thread**, but NanoHTTPD handles HTTP requests in separate worker threads.

Solution: **CountDownLatch main-thread dispatch pattern**

```java
// Pseudo-code example
public SnapshotResult getSnapshot() {
    CountDownLatch latch = new CountDownLatch(1);
    SnapshotResult[] result = new SnapshotResult[1];

    HandlerTick.enqueueServerTask(() -> {
        result[0] = collectSnapshot();  // Collect on main thread
        latch.countDown();
    });

    latch.await(timeout, TimeUnit.SECONDS);
    return result[0];
}
```

Three collection strategies:

| Strategy | Use Case | Blocks HTTP Thread? |
|----------|----------|--------------------|
| **Cache Read** | Storage/power/GT, networks, P2P, cells, monitor bindings, scanner, rich patterns + browse (`SnapshotScheduler`) | No |
| **Main-thread live** | Order submit, pattern inject/writes, topology POST capture, assistant, etc. | Yes (≤5–15s timeout) |
| **Client upload** | Recipe data (`PacketWebRecipeUpload` C→S) | Writes server cache directly |

`CountDownLatch` timeout defaults to 5 seconds; HTTP 503 is returned on timeout.

## 8. Security Model

- **Token Authentication**: random UUID per player, persisted to `TeXTech/WebAE/web-tokens.json` (re-issue replaces the previous token). TTL is optional via `tokenLifetimeHours` (0 = never expire)
- **Mandatory auth**: all `/api/` endpoints require `Authorization: Bearer <token>`; there is no opt-out config. 401 responses carry a `code` field (`missing_token` / `invalid_format` / `empty_token` / `token_expired` / `invalid_token`)
- **Admin-only endpoints**: `POST /api/refresh`, `/api/refresh/batch`, `/api/power/refresh`, `/api/power/refresh/batch`, `/api/gt/machines/refresh`, `/api/gt/machines/refresh/batch` require OP level >= 2 (checked via `WebAuthOpCheck`); non-OP gets 403 with `code:admin_required`
- **Default Local Bind**: `bindAddress=127.0.0.1` restricts access to the local machine only
- **No HTTPS**: NanoHTTPD does not provide TLS. Use SSH tunneling or reverse proxy for encryption on LAN
- **Token Management**: `/admweb issue` (self), `/admweb revoke [player]` (self/OP), `/admweb list` (OP), `/admweb refresh [network]` (OP, Phase 1.2)
- **No Password Storage**: Users only need a token to access; no Minecraft account verification required

**Security Warning**:
- `bindAddress=0.0.0.0` exposes the console to all LAN devices; anyone with the token can view storage and submit crafting
- Recommend restricting port access at the server firewall level

## 9. Network Packets

WebAE uses 5 Forge network packets covering recipe/icon upload and command-triggered upload:

| ID | Packet Class | Direction | Purpose |
|----|-------------|-----------|---------|
| 26 | `PacketWebRecipeUpload` | C→S | Client NEI recipe batch upload (large payload auto-chunking) |
| 27 | `PacketWebRecipeUploadAck` | S→C | Server recipe upload confirmation (with total progress) |
| 28 | `PacketWebIconUpload` | C→S | Client item/fluid icon batch upload (itemId → base64PNG, reuses the chunked pattern) |
| 29 | `PacketWebIconUploadAck` | S→C | Server icon upload confirmation (receivedChunks/totalChunks/success/message) |
| 30 | `PacketWebUploadTrigger` | S→C | Server command triggers client to start recipes/icons upload (`/admweb recipes upload`, `/admweb icons upload`) |

Recipe upload flow:
1. Client `KeyBindings.uploadNeiRecipes(scope, snapshotItemIds)` hybrid collect: `NeiRecipeCollector` (main thread; `deep` enables item-driven scan) or `RecipeSnapshotCollector` (`snapshot` scope) + `GameRecipeCollector` (background), deduped with NEI winning into a **single session**
2. First batch `isStart`: `RecipeUploadSession.onStart()` decides whether to `clearMemoryOnly()` (only the first active session per player; overlapping uploads ignored)
3. Batches throttled client-side by `RecipeUploadThrottler` (`recipeUploadBatchesPerTick` per tick); `RecipeUploadBatcher` splits under the FML 32KB cap (JSON ≤ ~32KB−512B; oversized recipes trim grid/rawJson)
4. Server `RecipeCacheStore.ingest()` + final `rebuildHandlerCounts()`; debounced save to `web-recipes.json` + `web-recipes.meta.json` + `recipe-chunks/chunk-NNNN.json`; default `recipeKeepMemoryAfterUpload=false` clears heap after save
5. Client `RecipeLocalExporter` writes `<instance>/TeXTech/WebAE/web-recipes.json` (plain JSON only, no gzip)
6. On completion, `PacketWebRecipeUploadAck` confirms delivery; browsers must click **Fetch recipes** on the Recipes page to pull sync chunks into IndexedDB

Icon upload flow:
1. Client `IconRenderer` (@SideOnly CLIENT) renders several items per frame into 32×32 PNGs offscreen
2. Packs an IconBundle (itemId → base64PNG) and chunk-uploads via `PacketWebIconUpload`
3. Server decodes and writes to `TeXTech/WebAE/icons/<packName>/`, then calls `IconStore.recordDefaultPack` to remember the most recent pack
4. `PacketWebIconUploadAck` reports progress

Command-triggered upload flow (ID 30):
1. An OP player runs `/admweb recipes upload` or `/admweb icons upload`
2. `CommandWebConsole` checks OP (`canUseOpCommands`) and that the sender is a player, then sends `PacketWebUploadTrigger` (S→C, fields uploadType=recipes/icons, packName)
3. The client handler calls the public static entry points `KeyBindings.uploadNeiRecipes()` or `KeyBindings.triggerIconUpload(packName)`
4. The rest follows the same path as the command flow (IDs 26/28)

See `network-packets.mdc` for details.

## 10. Command

The `/admweb` command manages Web Console access tokens and admin actions. The base command `getRequiredPermissionLevel` stays 0 (so issue/list/status remain open to any player), but the `recipes upload`/`icons upload` sub-commands internally check OP via `canUseOpCommands`.

| Subcommand | Permission | Description |
|------------|------------|-------------|
| `/admweb issue` | Any player | Generate a new token for yourself (full value shown once) |
| `/admweb revoke [player]` | Self / OP | Revoke your own token, or another player's token (OP only) |
| `/admweb list` | OP | List all active tokens (prefix + issue time only) |
| `/admweb reload` | OP | Actually reloads the TeXTech configuration: calls `Config.reloadConfiguration()` to re-read the active config file and re-run every section loader (debug/compat/ai/voice/assistant/plannerHud/dataLoom/superOrange/matterBallDecompressor/grapple/webConsole); some options (e.g. `enabled`/`port`/`bindAddress` for the web server itself) still need a server restart, and the response notes this; token and runtime data files (web-tokens.json/web-players.json/web-chat.json/web-icons/) are not affected |
| `/admweb recipes upload [snapshot\|deep]` | **OP** | Client merged NEI+Game single upload; writes server disk and client `TeXTech/WebAE/web-recipes.json`; browsers must still **Fetch recipes** on the Recipes page; `snapshot` collects recipes for AE storage snapshot items only; `deep` enables full NEI item scan (slow) |
| `/admweb icons import-nesql [pack] [subpath]` | **OP** | Import pre-rendered PNGs from `nesqlRepositoryPath` (default `TeXTech/WebAE/` when empty; incremental; does not overwrite existing) |
| `/admweb recipes export` | **OP** | Alias of upload, emphasizing the export-to-cache semantics |
| `/admweb recipes status` | Any | Show server recipe cache status (including disk cache size and last-save time) |
| `/admweb recipes clear` | OP | Clear memory and disk recipe cache |
| `/admweb icons upload [packName]` | **OP** | Checks OP + player identity, then sends `PacketWebUploadTrigger` to make your own client render and upload item icons (Phase 2 removed the in-game keybind; upload is command-only) |
| `/admweb icons status` | Any | List installed texture packs and config state |
| `/admweb icons clear` | **OP** | Async wipe of all packs under `TeXTech/WebAE/icons/` (resets index immediately; disk delete on background thread; chat notifies when done) |
| `/admweb refresh [network]` | OP | Force snapshot re-collect for one network or all active networks (Phase 1.2) |
| `/admweb help` | Any | Show usage |

## 11. Subsystem Design Summaries

Index by functional domain (status: **done** / **Phase C pending**). Phase numbers in headings are kept for searchability.

| Domain | Section | Status | Package / frontend |
|--------|---------|--------|-------------------|
| Storage | §11.1 | Done | `webae/snapshot/` · Storage page |
| Power | §11.2 | Done | `webae/power/` · Power page |
| GT machines | §11.3 | Done | `webae/gt/` · GT page |
| Recipe search | §11.4 | Done | `webae/recipe/` · Recipes page |
| Patterns | §11.5 | Done | `webae/pattern/` · Patterns page |
| AE ordering | §11.6 | Done | `webae/craft/` · AeOrdering page |
| Theme/dashboard | §11.7 | Done | `webae-frontend/` GridStack |
| Icon cache | §11.8 | Done | `webae/icon/` · client upload |
| Chat | §11.9 | Done | `webae/chat/` · Chat page |
| Player info | §11.10 | Done | `webae/player/` |
| Command upload | §11.11 | Done | `webae/network/` packets 26–36 |
| Dashboard settings | §11.12 | Done | Settings drawer |
| Storage/CPU pages | §11.14 | Done | Storage/Cpu pages |
| Power page | §11.15 | Done | Power page |
| Sci-fi themes | §11.16 | Done | CSS + IconRenderer |
| **Network topology** | §11.17 | Done | `webae/topology/` |
| **World map** | §11.26 | Done (device-focus enhancements tracked in backlog; not “unimplemented”) | `webae/worldmap/` · `TopologyWorldMapView` |
| Visibility polling | §11.18 | Done | `usePageVisibility` |
| GT charts | §11.19 | Done | `GtSummaryCharts` |
| Alert editor | §11.20 | Done | `webae/alerts/` |
| Webhook/health | §11.20a | Done | `WebhookDispatcher` |
| Monitoring depth | §11.20b | Done | Fluids/P2P power |
| Craft tree | §11.21 | Done | `CraftTreeCalculator` |
| SSE alerts | §11.22 | Done | `EventStreamHub` |
| P2P map | §11.23 | Done | `P2pTunnelEnumerator` |
| Monitor preview | §11.24 | Done | `MonitorPreviewCollector` |
| PWA/mobile | §11.25 | Done | manifest + CSS |

### 11.1 Storage Monitoring

- **Handler**: `StorageHandler.java` (full snapshot), `StoragePagedHandler.java` (cursor pagination)
- **Collector**: `AeSnapshotCollector.java` reads AE2 network storage stats on the main thread
- **Cache**: `SnapshotCache` TTL = `webRefreshIntervalMs * 3` (tolerates collection jitter); `snapshotVersion()` for cursor invalidation
- **Scheduler (Phase 1.2)**: `SnapshotScheduler` collects every `refreshIntervalMs`, spread across ticks (`ceil(activeKeys / N)` per tick where `N = intervalMs / 50`), only for networks active in the last 2 minutes
- **Return data**: items / fluids / essentia / bytes used+max / crafting CPUs (`CpuEntry` extended in Phase 3 with coordinates, monitor position, remainingItems/startItems)
- **Endpoints**: `GET /api/storage` (cache-only, stale fallback), `GET /api/storage/batch`, `GET /api/storage/items|fluids|essentia` (cursor pagination, default limit=200, sort=`amount_desc|amount_asc|name_asc|name_desc`, search filter; 409 `cursor_stale`), `POST /api/refresh` (admin), `POST /api/refresh/batch` (admin)
- **Frontend**: Storage tabs use `useStoragePaged` + `VirtualStorageTable` (`@tanstack/react-virtual` row virtualization + infinite scroll); 300ms search debounce; sort change resets cursor; merge mode client-merge for ≤3 networks, warning when >3; top `OverviewWidgetGrid` uses paged `totalEstimate`/`totalAmountSum` via `OverviewSnapshot` count overrides; `useSnapshotData` stale-while-revalidate on network switch (per-network cache + `refreshing` indicator). CPUs on standalone menu (see [11.14](#1114-configurable-storage-overview--standalone-cpu-page-phase-3))

### 11.2 Power Monitoring

- **Handler**: `PowerHandler.java`
- **Sampler**: `PowerSampler.java` keeps a 5-second sampling window (sliding window for rate calculation, unit: EU/t) and writes each sample into `SnapshotCache`
- **Data source**: `WirelessPowerQuery` / `WirelessSteamQuery` (reuses AI assistant power query interface)
- **Endpoints (Phase 1.2)**: `GET /api/power` (cache-only, stale fallback), `GET /api/power/batch`, `POST /api/power/refresh` (admin, invalidates cache only — next sampler tick repopulates), `POST /api/power/refresh/batch` (admin)
- **Return data**: Current EU/steam level, in/out rates, EU/steam history arrays
- **Frontend**: Full-page configurable GridStack on the Power page (`PowerWidgetGrid` + `PowerWidgetContent`, persisted in `webae_power_config`); defaults include EU gauge, in/out statCards, steam progressBar, dual-series SVG trend lineChart, steam in/out statCards; multi-network split uses Tabs per network (see [11.15](#1115-configurable-power-page--anti-flicker-phase-4))

### 11.3 GT Machine Monitoring

- **Handler**: `GtMachineHandler.java`
- **Collector**: `GtSnapshotCollector.java` reads GT machine states via `GtMachineStateReader`
- **Scheduler (Phase 1.2)**: GT snapshots are now collected by `SnapshotScheduler` every `gtRefreshIntervalMs` (default 10000 ms) on the same throttled-spread model as storage
- **Binding**: `GtMachineBinding.java` data structure for monitoring targets
- **Return data**: Machine name, progress percentage, current recipe, power consumption, input/output slot items
- **Endpoints**: `GET /api/gt/machines` (cache-only), `GET /api/gt/machines/batch`, `POST /api/gt/machines/refresh` (admin), `POST /api/gt/machines/refresh/batch` (admin)
- **Frontend**: machine table + antd `Progress` bars; top summary with status pie + recipe-map bar chart (`components/gt/GtSummaryCharts.tsx`, data from `utils/gtChartData.ts` reusing `getGtStatusBreakdown`); error rows highlighted (`gt-row-error`); sortable by status/recipe/voltage + text search; multi-network merge for charts and table

### 11.4 Recipe Search

- **Data flow**: OP `/admweb recipes upload*` → server disk is authoritative (no startup full load) → player clicks **Fetch recipes** → IndexedDB local browse/search
- **Handler**: `RecipeHandler.java` (sync manifest/chunk + server browse/search fallback)
- **Hybrid collection (client)**: `KeyBindings.uploadNeiRecipes(scope, …)` runs `NeiRecipeCollector.collectAll(deepScan)` or `RecipeSnapshotCollector.collectForItems()` on the client main thread, then `GameRecipeCollector.collectAll()` on a background thread; dedup by `handlerId:recipeIndex` with NEI winning; merged into one upload session via `RecipeUploadThrottler`
- **Upload session**: `RecipeUploadSession` ensures only the first `isStart` batch per player clears memory; concurrent overlapping uploads are ignored
- **NEI parsing**: `NeiRecipeCollector.extractRecipe()` reflects `PositionedStack`; `handlerName` formatted via `RecipeDisplayNames` as `Localized (langKey)`; `itemId` includes meta (`registry:meta`)
- **Game collection**: `GameRecipeCollector` writes grids for `ShapedRecipes` and EU/duration for GT RecipeMap entries; handler labels same as NEI
- **Upload**: chunked C→S via `PacketWebRecipeUpload`; `rebuildHandlerCounts()` on last batch
- **Cache / disk**: `RecipeCacheStore` — no startup full load; `ensureLoaded()` lazy (blocking on HTTP workers; on the **server tick thread** only starts `WebAE-RecipeCache-Load` and returns immediately — never sync-parses the full catalog); quest analysis/submit prefetch `ensureLoaded` on the HTTP thread; craft-tree skips expand on the main thread when memory is not loaded; save writes `web-recipes.json` + `web-recipes.meta.json` (`RecipeDiskMeta`) + `recipe-chunks/chunk-NNNN.json` (`recipeSyncChunkSize`); default `recipeKeepMemoryAfterUpload=false` clears heap after save; `recipeCacheMode` (`full` default) applies only when memory is loaded; fallback fuzzy search rate-limited by `RecipeSearchRateLimiter`
- **Sync API** (primary; does not require full heap load):
  - `GET /api/recipes/sync/manifest` — revision / chunkCount / handlers
  - `GET /api/recipes/sync/chunk?index=N` — one chunk of recipes
- **Server fallback API** (craft tree etc.; `ensureLoaded`):
  - `GET /api/recipes/browse|search|suggest|…`
- **DTO**: `RecipeDto` adds `gridSlots`, `gridWidth/Height`, `euPerTick`, `durationTicks`, `voltageTier`, `recipeType`
- **Frontend** (`webae-frontend/src/pages/Recipes.tsx` + `hooks/useRecipeSync.ts` + `utils/recipeLocalStore.ts` + `components/recipes/`):
  - Toolbar **Fetch recipes**: compare manifest revision, download chunks into IndexedDB with progress / cancel; unchanged revision does not auto re-download
  - After fetch, **local** browse / fuzzy search / suggest; category filter, Full/Merged, Compact/Detailed layouts as before
  - Infinite scroll over local store; compact/merged cards + `RecipeDetailModal`; detailed craft grid / fluids / GT tags
  - Layout switch CSS show/hide + `React.memo`; persisted `localStorage.webae-recipe-layout` / `webae-recipe-display-mode`
- **Clear**: `/admweb recipes clear` wipes server memory + disk (meta/chunks included); browser IndexedDB must be cleared per-origin or overwritten by Fetch

### 11.5 Pattern Management

- **Handlers**: `PatternHandler.java` (encode/inject/interface enum), `PatternListHandler.java` (pattern overview/detail/delete/PUT write-back), `PatternBrowseHandler.java` (Grid+Interface dual-source browse pagination), `PatternGridDetailHandler.java` (single Grid entry full I/O)
- **Browse cache**: `PatternBrowseService.java` splits main-thread `buildAndStoreCache` from HTTP-thread `paginate`; `SnapshotScheduler` pre-collects active networks at `Config.webPatternCacheTtlMs`; GET returns `cached:true/false` + `timestamp` (stale data still served when expired); offline owner browse via `getOwnerPlayerOrFake`; POST `/api/patterns/browse/refresh` (OP) force-rebuild; Web PUT/DELETE/inject and `PatternBrowseInvalidationGridCache` (listens for `MENetworkCraftingPatternChange`, registered at postInit via `PatternBrowseGridCacheRegistrar`) all call `invalidateAll()`
- **Interface enumeration**: `InterfaceLocator.java` enumerates all ME interface terminals on the main thread; DTO includes `machineRecipeType` (machine name + GT RecipeMap) and `existingPatterns` (slot/patternId/outputs/crafting)
- **Encoder**: `PatternEncoder.java` encodes item/fluid lists into AE2 pattern NBT; `decode(String)` reverses NBT
- **Blank pattern**: `BlankPatternHelper.java` extracts 1× `materials().blankPattern()` from AE network `IStorageGrid`; encode (`consumeBlank`) and inject both deduct; insufficient stock returns `NO_BLANK_PATTERN`
- **Injector**: `PatternInjector.java` writes patterns to a specified interface on the main thread; `consumeBlank: false` skips duplicate deduction after encode
- **Pattern overview**: `PatternListHandler` scans all interface pattern slots on the network, decodes NBT to `PatternListEntryDto`; DELETE/PUT fires `MENetworkCraftingPatternChange`
- **Frontend** (`pages/PatternEditor.tsx`, Phase 6):
  - Left: pattern overview list (icon + primary output + source interface; fuzzy search, multi-select, batch delete/export)
  - Right editor: 9×3 input grid (icon + stack-size badge) + outputs (icon + quantity + ×2/×4/×8/×16/×32/×64 multiplier and ÷2, floored at original recipe amount)
  - Recipe search: `groupByPrimaryOutput()` merged thumbnail list; click to add default recipe or open `RecipeDetailModal` with `onApplyRecipe` to pick handler type
  - Encode preview (consumes blank pattern) + save write-back + inject (interface dropdown shows coords/pattern count/machine recipe type; selected interface lists existing pattern details)

### 11.6 AE Crafting Orders (Phase 7 enhancements)

- **Handler**: `OrderHandler.java`
- **Order interfaces**:
  - Single `/api/order`: body `{ networkId, itemName?, amount, rawText?, locale?, cpuName?, patternId? }`; `patternId` takes priority over itemName
  - Batch `/api/order/batch`: body `{ networkId, cpuName?, items:[{ itemName, amount, patternId? }] }` (API kept for internal/integrations; **Web UI no longer exposes the batch panel**)
- **CPU selection**: optional `cpuName`; `AssistantServerServices.submitCraft(..., cpuName)` passes `ICraftingCPU` to AE2 `submitJob`; when omitted AE2 auto-assigns; `cpuInfo` snapshot stored on `OrderStatus`
- **Real progress** (`WebAeOrderProgressService`, resolves by `networkId` — **not** player-proximity Link search):
  1. Register `jobId` on submit; bind `ICraftingLink` / `craftingId` after `submitJob` succeeds
  2. Calculation phase: `AssistantCraftJobManager` (keyed by `trackingKey=jobId`) → pending ~1–25%
  3. Execution: `(startItemCount - remainingItemCount) / startItemCount` (same **craft-tree step progress** as AE2 CPU GUI, not final-output count; capped at 99 until done)
  4. Complete/cancel: `ICraftingLink.isDone()` / `isCanceled()` (including in-game cancel); Web cancel calls `link.cancel()`
  5. Per-network short TTL (~50ms) CPU snapshot cache shared across orders; **never** `populatePlan` / full item lists
- **List/history**: `GET /api/order/list` returns `{ success, orders, history }`; completed/cancelled/failed move to in-memory `historyOrders` (max 200, cleared on restart, **filtered by ownerUuid**); `OrderStatus` includes `cpuName`/`cpuInfo`/`finalProgress`/`itemName`/`amount`/`patternId`/`networkId`/`craftingId`/`startItems`/`remainingItems`/`progressKind=steps`
- **Status/cancel**: `GET /api/order/status?jobId=` (unknown jobId → 404, no forged completed); `POST /api/order/cancel` cancels all orders for the current player (calc Future + CPU links)
- **Batch templates API**: `GET/PUT /api/order/templates` still available (`web-order-templates.json`); Web ordering page UI no longer shows the templates panel
- **Backend**: `WebAeCraftService` + `AssistantServerServices` + `AssistantCraftJobManager` + `WebAeOrderProgressService`
- **Frontend** (`pages/AeOrdering.tsx`):
  - Top CPU `Select` (name + capacity + parallelism + busy/idle)
  - "By pattern" tab: paginated browse + single orders (product quick-add / pattern cart / product modal)
  - "By item" tab: storage search + single orders
  - "Craft tree" tab: gap display only (no batch order)
  - Active/history tables poll `/api/order/list` (3s); progress tooltip explains step progress; history **Reorder** confirms then `POST /api/order`

### 11.7 Theme System & Dashboard Customization (Phase 2.1 / 2.2)

- **Theme system**:
  - Theme = color scheme × layout preset × **page style**, chosen independently
  - Color schemes (`[data-theme-color]`): **128** total — 19 classic + 5 Phase 8 sci-fi + 4 composition companions + 13 bold-batch + 20 batch2 + 50 batch3 + 12 Printstream batch4 + **5 Auraeco batch5** (`aura` / `aura-front` / `aura-design` / `aura-sys` / `aura-interact`: voxel cyan / front cyan / design violet / system green / interact blue)
  - **Page styles** (`[data-page-style]`, `theme/pageStyles.ts`): **126** total — 7 chrome + 12 composition + 20 bold + 20 batch2 + 50 batch3 + 12 Printstream batch4 + **5 Auraeco batch5** (`aura-voxel` / `aura-spore` / `aura-dome` / `aura-sparks` / `aura-bubble`); visuals in `styles/bold-styles.css` + `bold-styles-batch2.css` + `bold-styles-batch3.css` + `bold-styles-batch4.css` + `bold-styles-batch5.css` + `effects-motion.css`. Printstream uses B/W geometry + Pantone pearl gradients + ASCII dashed streams (CS community homage); Auraeco approximates voxel waves / tendril particles in CSS (no Three.js); neither uses content `clip-path`; gated by `effectsLevel` + `prefers-reduced-motion`
  - Layout presets (`[data-theme-layout]`): **30** total — classic 8 (standard / compact / wide / sidebar-right / topnav / bottomnav / floating / split-chrome) + **22 batch3 structural** layouts via `chromeKind` (dual-rail / rail-only / dock / island / theater / dense-ops / magazine / split-pane / top-tabs / zen / command / tri-chrome / card-stack / hud-frame / pipeline / hero-header / status-strip / drawer-peek / corner-hub / widescreen / right-drawer / frame; see `layout-batch3.css` + `AppLayout`)
  - **Settings appearance / presets**: `ThemePreviewMini` (in-DOM structural thumbnails) + `ThemeOptionGrid` thumbnail tiles for color / layout / style / preset picking; ~**121** builtin AppPresets (including batch3 + Printstream batch4 + Auraeco batch5 `builtin-style-*` and flagship `builtin-printstream` / `builtin-aura`)
  - Settings panel: color + layout + **page style** + effects intensity; dashboard widget editor can override chart style
  - Chart colors follow CSS variables (e.g. `var(--accent)`) and theme tokens; Dashboard/Power trend SVG updates with theme colors; when `effectsLevel=full`, line/area/pie/radar/bar charts get continuous CSS animations (`.chart-flow-line`, etc.); disabled under `prefers-reduced-motion`
  - The backend `/api/config` returns `themeColors` / `themeLayouts` / `pageStyles` lists for discoverability (the frontend also owns the same catalog)
- **Dashboard customization**:
  - GridStack 11.x via npm; `pages/Dashboard.tsx` 12-column grid; main dashboard `gs-min-w/h=1` (free sizing)
  - Widget model (`DashboardWidgetConfig`): chart types as before plus **`group` / `textNote` / `spacer` / `alertsSummary` / `craftingQueue`**; data sources include `customPins` / `none` / `alertsActive` / `craftingBusy`; optional **`children?`**, **`noteText?`**, **`locked?`/`noMove?`/`noResize?`/`sizeToContent?`**, **`alertThreshold?`**, plus existing `pins?` / `columns?` / `contentScale?` / `pinsOnly?` / `gaugeThreshold?`
  - **Nested groups**: `type:'group'` hosts an inner GridStack (`GroupWidget.tsx`, `column:'auto'`); tree helpers in `utils/dashboardTree.ts`; migrate/import/export recurse `children`
  - **Edit UX**: palette for one-click add; `.dashboard-trash` + GridStack `removable`; add-to-group from group header; pin metrics use `flattenWidgets`
  - **Layout/feed widgets**: `SpecialWidgets.tsx` + `useDashboardAlerts`
  - **Data-first editor**: pick data source & pins, then compatible chart type (`dataSourceChartMap.ts`; layout/feed categories for `none` / alerts / crafting)
  - **Pin history APIs**: `GET /api/network/metrics/items`, extended fluids, `GET /api/network/metrics/entities`; sampler limits in cfg (`dashboardMaxTracksPerWidget` default 10, `dashboardMaxTracksGlobal` 16, item sub-cap 8, fluid/entity 16) exposed via `/api/config`; line charts merge built-in timeseries ∪ pin histories; radar uses pin current values when ≥3 pins; network-balance rows are searchable pins
  - **Data-table column contract**: `utils/dashboardColumns.ts` owns per-source column definitions. `columns === undefined` means defaults, while `columns: []` is an explicit hide-all selection and must not be replaced through truthy/length fallbacks. For `topItems`, the `name` column consumes the storage snapshot's `displayName`, and `registryName` consumes the registry identifier; they must not substitute for one another. `Dashboard.tsx`, shared `WidgetContent.tsx`, and `NetworkBalanceTable.tsx` must consume the same selection; clear `columns` when changing `dataSource` so stale source-specific keys do not leak. Regression coverage: `utils/dashboardColumns.test.ts`
  - **Pin editor**: `WidgetPinEditor.tsx` local inventory search matches display name, registry name, and item ID; focusing an empty search offers current Top inventory candidates. Recipe suggestion IDs must preserve `meta=0`; the per-widget cap comes from `/api/config.dashboardMaxTracksPerWidget`
  - **Phase 2 data sources**: `gtMachineList` / `machineByStatus` / `networkCompare` unchanged
  - Layout persistence: `localStorage.webae_dashboard_config`; Edit Modal width/height rebuilds GridStack via `widgetLayoutSignature`
  - Data refresh: `useSnapshotData` + `useDashboardPinMetrics` (merged pin history) + `usePlayers` / `useNetworkMetrics` / `useDashboardAlerts`

### 11.8 Item Icon Cache & Texture Packs (Phase 3.1)

- **Server store**: `webae/icon/IconStore.java` singleton manages `TeXTech/WebAE/icons/<packName>/nei/<itemId>.png` (legacy flat PNGs auto-migrate on first access); `manifest.json` records `modes[]`, `counts{}`, `uploadedAt`, `clientVersion`; path-traversal protection; `listPacks`/`listIcons`/`getIconFile`/`resolveWriteTarget`/`refreshPack`/`recordModeUpload`/`migrateLegacyPackIfNeeded`; `recordDefaultPack`/`getDefaultPack`
- **REST handler**: `webae/api/handler/IconHandler.java`
  - `GET /api/icon?item=<itemId>&pack=<pack>&mode=<mode>&meta=<int>&size=<16|32|64>` returns PNG (`mode` defaults to nei; disk may serve legacy modes; **miss is immediate 404**; enqueue `IconMissingQueue` only when `iconLazyCaptureEnabled=true`; sync direct render only when `iconDirectRenderEnabled=true`)
  - `GET /api/icon/packs` lists packs as JSON (`{success:true, packs:[...], defaultPack:"<name>"|null}`); `defaultPack` comes from `IconStore.getDefaultPack()` so the frontend can pick it on first load
  - `GET /api/icon/sync/manifest` / `GET /api/icon/sync/bulk` full-pack sync (frontend does not call on login by default; Settings manual or user-enabled auto-sync)
  - `POST /api/icon/pack?pack=<packName>` admin-only (`WebAuthOpCheck.isOp` OP>=2) uploads a zip; the server extracts it with `ZipInputStream` into `web-icons/<packName>/`, applies **Zip Slip protection** (canonical-path check that entries stay inside packDir), accepts only `.png` entries, refreshes the IconStore index, and calls `recordDefaultPack` to remember the most recent pack
- **vs world map**: both use HTTP; map misses read snapshots/placeholders by default; icons never GL-render on the server. Icon concurrency is high — **do not** default to sync `IconDirectCaptureBridge` (prefer async placeholder + SSE, not map SP sync capture)
- **Static serving**: `WebConsoleServer.serveStatic()` extended to serve `/icons/<pack>/<itemId>.png` from the external `TeXTech/WebAE/icons/` directory with canonical path-traversal protection + `Cache-Control`
- **Routing**: `WebApiRouter` dispatches `/api/icon` and the `/api/icon/` prefix to `IconHandler`
- **Client renderer (NESQL primary path)**: `GuiIconExportScreen` renders `iconRenderPerTick` items per frame; active path is `IconNesqlStyleRenderer` (64×64 FBO + `GuiContainerManager.drawItem`, **output 64×64**, matching NESQL exporter) via `IconExportResolver.resolve`; **fluids / fluid-aware stacks** keep `IconFluidRenderer` + `IconGlFallback.renderFluidAwareSlotIcon` / `renderRegistryFluidIcon`; **`nei` only**; `/admweb icons local` writes `icons-local/`; `PacketWebIconPullZip` (49) for pull
- **Production stability (full GTNH pack)**:
  - `IconRenderGuard` — after each off-screen GL/FBO export: finish dangling `Tessellator` draws, reset stencil/depth/blend pollution, restore the main framebuffer (prevents `Already tesselating!` and square / AE-terminal-shaped holes in batch-exported PNGs)
  - `IconNesqlStyleRenderer` — per-icon `glPushAttrib` + `clearFboBuffers` (COLOR|DEPTH|STENCIL) to isolate custom `IItemRenderer` state
  - `IconLazyRenderQueue` — client-side lazy-load queue, max 2 icon renders per tick; `PacketWebIconRequest` no longer blocks the main thread synchronously; bulk `/admweb icons upload` clears the queue and pauses lazy work
  - `PacketWebIconUploadAck` — suppresses chat spam for single-icon lazy completions (`1 icons stored`); bulk/multi-icon batch completions still notify in chat
  - **Recommendation**: close the WebAE browser tab before full-pack export; prefer `/admweb icons upload snapshot default` or `/admweb icons import-nesql` instead of enumerating 40k+ items at once
- **Commands**: `/admweb icons upload [pack]`, `/admweb icons upload snapshot [pack]`, `/admweb icons import-nesql [pack] [subpath]`, `/admweb icons import <folder> [pack]`, `/admweb icons modes`, `/admweb icons status`, `/admweb icons clear` (async; does not block the server thread)
- **NESQL import**: `WebAeLocalDataDir` + `NesqlIconImporter` reads pre-rendered PNGs from `nesqlRepositoryPath` (empty → `TeXTech/WebAE/`); incremental, skips existing icons
- **Lazy-load SSE**: **opt-in** via `iconLazyCaptureEnabled` (default false) + chat consent (resource-pack notice) → `IconMissingQueue` dispatches → `IconLazyRenderQueue` → SSE `icon-ready`. Not the default HTTP miss path.
- **Active resolve chain**: fluid specials → NESQL `drawItem` FBO → placeholder; archived chain in `resolveLegacy`
- **itemId lookup**: exact id first (`IconItemId.lookupCandidates` / frontend `iconLookupIds`). `GET /api/icon` always uses `Cache-Control: max-age=86400`; `X-Icon-Exact` is diagnostic only. SSE `icon-ready` only bumps the icon URL version and reloads — **does not** delete IndexedDB/directory caches (NESQL-style long-lived static cache).
- **Config** (`[webConsole]`): `iconCacheEnabled` / `iconUploadEnabled` / `iconPackEnabled` / `iconDirectRenderEnabled`(false) / `iconRenderPerTick`(64) / `iconRenderPerTickAll`(32) / `iconUploadChunksPerTick`(4) / `iconProgressChatIntervalMs`(3000)
- **Frontend**: Settings fixed to `nei`; `iconModeFallbackChain` → `['nei']`; `iconAutoSync` default false; no topology/world-map server prefetch; Cytoscape prefers `resolveLocalIconUrls`; see `.cursor/rules/webae-icon-performance.mdc`
- **Icon auth fix (v3.0)**: `WebAuthMiddleware.authenticate()` now falls back to reading the token from the `?token=` or `?access_token=` query parameter when the `Authorization` header is missing; security is preserved (token validity still checked); this fixes `<img src="/api/icon?...">` returning 401 (missing Authorization header) → icon error → text fallback
- **itemId fix (Phase E)**: `webae/snapshot/AeSnapshotCollector` now builds `itemId` from the registry name (`GameRegistry.findUniqueIdentifierFor(item)`); meta is appended only when `!= 0` (and not wildcard). The old `unlocalizedName:meta` format did not match the registry-name keys used by the icon store, causing icon 404s
- **Default pack fix (Phase E)**: on first load (no localStorage memory) the frontend uses `/api/icon/packs` response `defaultPack` instead of hardcoded `'default'`; both `PacketWebIconUpload` and `IconHandler` zip upload call `IconStore.recordDefaultPack` to persist the most recent pack

### 11.9 Chat System (Phase E)

- **DTO**: `webae/chat/ChatMessage.java` (id/senderUuid/senderName/content/timestamp/source=web|game|system)
- **Store**: `webae/chat/ChatMessageStore.java` singleton, `ArrayDeque<ChatMessage>` ring buffer (default 200); `append`/`getSince(ts)`/`getRecent(limit)`/`latestTimestamp`; debounced save to `TeXTech/WebAE/web-chat.json` so history survives restarts
- **In-game collection**: `handler/HandlerWebChatCollector.java` `@SubscribeEvent` listens to `ServerChatEvent` (registered to `MinecraftForge.EVENT_BUS` by `LoaderHandler`) and **does not cancel the event** — it mirrors the message into `ChatMessageStore` as `source=game`
- **Web broadcast**: `ChatHandler.handleSend` reads `playerUuid` from the token, calls `ChatMessageStore.append(source=web)`, then broadcasts to in-game chat via `FMLCommonHandler.instance().getMinecraftServerInstance().getConfigurationManager().sendChatMsg(new ChatComponentText("[Web] <name>: content"))`; banned players, empty content, and over-long content are rejected
- **REST handler**: `webae/api/handler/ChatHandler.java`
  - `GET /api/chat/history?limit=200&since=<ts>` returns recent messages
  - `GET /api/chat/since=<ts>` incremental fetch
  - `POST /api/chat/send` body `{content}` sends a message
- **Response DTO**: `webae/dto/ChatMessageDto.java`
- **Routing**: `WebApiRouter` registers `/api/chat/*` → `ChatHandler`
- **Frontend**: see "Chat page" in section 6

### 11.10 Player Info & Skin URL (Phase E)

- **DTO**: `webae/player/PlayerInfo.java` (uuid/name/firstLogin/lastLogin/lastLogout/totalOnlineMs/online)
- **Store**: `webae/player/PlayerInfoStore.java` singleton, in-memory `Map<UUID,PlayerInfo>`; `touchLogin`/`touchLogout`/`reconcileOnline`/`getOnlinePlayers`/`getAllPlayers`/`effectiveOnlineMs`; debounced save to `TeXTech/WebAE/web-players.json`
- **Tracker**: `handler/HandlerWebPlayerTracker.java` `@SubscribeEvent` listens to `PlayerEvent.PlayerLoggedInEvent`/`PlayerLoggedOutEvent` (registered to the FML bus by `LoaderHandler`) and writes to `PlayerInfoStore`; `HandlerTick` calls `reconcileOnline` + `tickSave` every tick
- **Skin URL**: `webae/player/SkinUrlResolver.java` reads `EntityPlayerMP.getGameProfile().getProperties().get("textures")`, base64-decodes it with `javax.xml.bind.DatatypeConverter` (avoids Java 8+ APIs), parses the JSON, and returns `textures.SKIN.url`; offline players return null
- **REST handler**: `webae/api/handler/PlayerHandler.java`
  - `GET /api/players` returns `{online:[...],offline:[...]}`; each entry has uuid/name/online/onlineMs/lastLogin/lastLogout/skinUrl
  - `GET /api/players/since=<ts>` incremental fetch
- **Response DTO**: `webae/dto/PlayerDto.java`
- **Routing**: `WebApiRouter` registers `/api/players` → `PlayerHandler`
- **Frontend**: avatars load `https://textures.minecraft.net/<hash>` at 32×32 from `skinUrl`; on failure or no skin, a circular first-letter placeholder is shown

### 11.11 Command-Triggered Upload (Phase E)

- **Network packet**: `webae/network/PacketWebUploadTrigger.java` (S→C, ID 30, fields `uploadType`=recipes/icons and `packName`), registered in `LoaderNetwork`
- **Command side**: `CommandWebConsole`'s `recipes upload` / `icons upload` sub-commands now check OP (`canUseOpCommands`) + that the sender is a player, then send the trigger packet; `getRequiredPermissionLevel` stays 0 so other sub-commands are unaffected
- **Client entry points**: `KeyBindings.uploadNeiRecipes()` and `KeyBindings.triggerIconUpload(packName)` (moved into `KeyBindings` in Phase 2 from the deleted `HandlerWebIconUpload`) are public static entry points the packet handler calls; `uploadNeiRecipes()` now collects on the client main thread (`Minecraft.addScheduledTask`) and uploads asynchronously
- **Downstream**: follows the same path as the command flow (recipes → IDs 26/27, icons → IDs 28/29)

### 11.12 Dashboard Settings Panel & Widget Property Inheritance (Phase E + WebAE Roadmap Phase 1)

- **Global config**: the `DashboardSettings` type (`utils/presets.ts`) includes `margin`, `widgetGap`, `contentInset` (visual padding inside grid cells, does not affect GridStack snap), `borderWidth`, `chartStretchMode` (`fit`/`stretchX`/`fill`), `fontSize`, `chartSize`, `chartShowValueAxis/TimeAxis`, `showLastUpdated` (optional widget footer “updated Ns ago”), `defaultAlignment`, `defaultColors`, `colorPresets`, `widgets`; persisted to `localStorage.webae_dashboard_config`; merged with `DEFAULT_DASHBOARD_SETTINGS` on load
- **Property inheritance**: `utils/dashboardResolve.ts` provides `resolveProp`, `resolveAllColors`, `resolveContentInset`, `resolveBorderWidth`, `resolveChartStretchMode`, `applyWidgetShellStyle`; widget overrides win; colors honour `colors.inheritDefault`; per-widget `colors.borderWidth` can override shell border thickness
- **Unified shell**: `WidgetShell.tsx` applies resolved bg/border/inset on `.grid-stack-item-content.widget-shell` (CSS vars `--widget-bg`/`--widget-border`/`--widget-border-width`); used by Overview, Power, and Dashboard grids
- **Settings panel**: `DashboardSettingsDrawer.tsx` — Cancel/Apply draft mode; effective `contentInset` = 10 + user value; `RadarChartWidget` multi-axis editor; `iconWikiEnabled` in Settings; storage `amount=0` → craftable link → AE ordering prefill via `orderNavigation`
- **Per-widget editor**: `EditWidgetModal.tsx` (antd `Tabs`: basic / layout / colors / advanced); `WidgetColorSection.tsx` shared color fields; advanced tab includes statCard `showDelta`/`significantDigits`, per-widget `contentInset`/`chartStretchMode` inherit, `borderWidth` inherit
- **Numbers & charts**: `formatLargeWithDelta` + statCard `showDelta`; `ChartTrendSvg` accepts `stretchMode`; antd `Skeleton` while loading statCard/chart
- **Widget ops**: all three grids support “copy widget” in edit mode; `utils/widgetGridActions.ts` provides `copyWidgetConfig` / `exportWidgetsJson` / `parseWidgetsImport`
- **Rendering**: `renderWidget` wraps content in a `widget-align` container, applying `resolveProp` font size, `resolveAllColors` colors, and `data-align` alignment; SVG charts use `preserveAspectRatio="xMidYMid meet"` to avoid stretching; the chart area height follows `chartSize%`
- **Chart palette**: pieChart derives its palette from `[chartColor, iconColor||var(--success), var(--warning)]`; lineChart/barChart/radarChart use chartColor for stroke/fill
- **GridStack rebuild**: the `useEffect` depends on `layoutSignature` (`widgets.map(w=>id_w_h).join(',')`) so changing width/height in the Edit Modal rebuilds the grid with the new sizes

### 11.13 Sidebar Three-State & Top-Bar Refresh Status (Phase E)

- **Sidebar three-state**: the bottom `.sidebar-collapse-btn` is removed; a `.sidebar-mode-toggle` tab button sits on the sidebar edge; `aside.sidebar` gets `:data-mode` (expanded/collapsed/hidden); CSS drives three states: expanded (default), collapsed (60px, icons only), hidden (translateX -100%, only the toggle remains); in the sidebar-right layout the toggle moves to the left edge and the hidden direction reverses; `sidebarCollapsed` boolean is replaced by a `sidebarMode` string, with a new `cycleSidebarMode` action; persists to `localStorage.webae_sidebar_mode`; the mobile `sidebarMobileOpen` stays a separate control
- **Top-bar refresh status**: fixed-width status text next to connection dot; Settings page shows last data update time and freshness indicator (Phase 2)

### 11.14 Configurable Storage Overview & Standalone CPU Page (Phase 3)

- **Inheritance reuse**: Storage and CPU overview rows reuse Phase 1 `DashboardWidgetConfig` + `resolveProp`/`resolveAllColors` (`utils/dashboardResolve.ts`)
- **Types & defaults** (`utils/presets.ts`): `StorageOverviewSettings` → `localStorage.webae_storage_overview_config`; `CpuOverviewSettings` → `localStorage.webae_cpu_overview_config`; helpers `mergeOverviewSettings()` / `overviewAsDashboardSettings()`
- **Shared components**: `OverviewWidgetGrid.tsx`, `WidgetContent.tsx`, `overviewDataSources.ts`, `cpuColumns.tsx`
- **Storage page** (`Storage.tsx`): CPU tab removed; configurable overview + items/fluids/essentia tables
- **Essentia page** (`Essentia.tsx`, Phase 5): sidebar `PageId: essentia` (`ExperimentOutlined`); aspect count/total summary + `essentiaCountHistory` trend via `useNetworkMetrics`/`ChartTrendSvg`; searchable/sortable aspect table; multi-network merge; Ctrl+K essentia hits navigate to `?page=essentia` with search prefill
- **CPU page** (`Cpu.tsx`): sidebar `PageId: cpu` (`HddOutlined`); overview + clickable table + detail Drawer; multi-network Tabs (split) or merged; craft history placeholder
- **Backend** (`StorageDto.CpuEntry`): crafting-link x/y/z/dim, monitorX/Y/Z/Dim, remainingItems/startItems via `AeSnapshotCollector.collectCpus`
- **CSS**: overview cards fixed 112px height

### 11.15 Configurable Power Page & Anti-Flicker (Phase 4)

- **Inheritance reuse**: Power page reuses Phase 1 `DashboardWidgetConfig` + `resolveProp`/`resolveAllColors`
- **Types & defaults** (`utils/presets.ts`): `PowerSettings` + `DEFAULT_POWER_WIDGETS` → `localStorage.webae_power_config`; default widgets: EU gauge, EU in/out statCards, steam progressBar (`steamPercent`), power trend lineChart (`powerHistory`), steam in/out statCards
- **Shared components**: `PowerWidgetGrid.tsx` (GridStack 12-col, `cellHeight:64`, variable height, edit mode); `PowerWidgetContent.tsx` (statCard/progressBar/gauge/lineChart with inline SVG dual EU+steam series — avoids Plots poll flicker); `utils/powerDataSources.ts` (`PowerSnapshot`, `getPowerDataSourceValue()`, 11 power data sources)
- **Power page** (`Power.tsx`): multi-network Tabs; "Edit layout" toolbar; trend chart stays mounted on poll (no Card loading overlay)
- **Anti-flicker**: `useSnapshotData` splits `initialLoading` vs `refreshing`; `loading` aliases `initialLoading` for backward compatibility; trend widget shows Spin only when `initialLoading && !snapshot`; empty history keeps container + `.chart-no-data-watermark`; CSS `.power-chart-stable` / `.power-trend-chart`

### 11.16 Sci-Fi Themes + Chart Animations + Icon Rendering (Phase 8 — roadmap final stage)

- **New themes** (`theme/colors.ts` + `styles/global.css`):
  - `hologram` — holographic cyan glass + scan-line overlay
  - `plasma` — purple/pink radial particle pulse background
  - `neon-pulse` — fluorescent accent + breathing card borders
  - `quantum` — deep blue + drifting grid background
  - `crystal` — prismatic gradient text shimmer
  - Backend `/api/config` `themeColors` / `themeLayouts` / `pageStyles` match the frontend catalog (128 / 30 / 126); Settings i18n `themeColor_*` / `themeLayout_*` / `pageStyle_*`; thumbnail previews via `ThemePreviewMini`
- **Continuous chart animations** (gated by `data-effects-level=full`):
  - Dashboard SVG: `.chart-flow-line` (stroke-dash flow), `.chart-svg-area` (area pulse), `.chart-pie-group` (slow spin), `.chart-radar-data` (radar pulse), `.chart-bar-segment` (bar brightness pulse)
  - Power trend: inline SVG in `PowerWidgetContent.tsx` reuses the same CSS classes
  - `@media (prefers-reduced-motion: reduce)` disables all Phase 8 animations
- **Icon rendering optimizations** (`IconRenderer.java` + `IconItemEnumerator.java` + `IconExportResolver.java` + `IconRenderGuard.java`):
  - A: 128×128 FBO + 4× scale, read 64×64 downsampled to 32×32
  - B: flat icon quad UV inset (1/16 atlas margin)
  - C: ItemBlock fallback via `RenderBlocks.renderBlockAsItem`
  - D: blank detection → multi-path retry → placeholder PNG with abbreviated itemId
  - E: bind items/blocks atlases before each batch
  - F: when NEI unavailable, enumerate meta variants via `Item.getSubItems`
  - G: `IconRenderGuard` resets Tessellator after each slot/page/GL fallback; `IconLazyRenderQueue` throttles lazy load to 2/tick

### 11.17 Network Topology (channel lanes ae_budget_v2 + disk persistence)

- **Package**: `webae/topology/` — `NetworkStatusEnumerator` (network-tool parity + subtype classification + drive pattern counts), `TopologyRules` (fine subtypes + `podKind`/`layer`), `TopologyFacilityGrouper` (`subtype|itemId` groups), `ChannelBranchAllocator` (4×8 smart lanes; subtype/podKind preference; overflow marked), `LogicalTopologyBuilder` (**ae_budget_v2** channel-budget tree: dense trunk 32 → 4× smart lane 8 → role pods → devices; zero-channel hub orbit; `buildStar()` double-ring summary still available), `TopologySnapshotStore` (`TeXTech/WebAE/topology/<owner>-<network>.json`), `CraftingCpuTopologyCollector`, `SimulatedLayoutBuilder` (deprecated Manhattan `pathPoints`), `TopologySnapshot`, `TopologyCache`
- **Intent**: **Channel capacity planning diagram**, not real AE cable/path simulation. Mental model: one controller capacity ≈ dense trunk 32 → up to four smart lanes of 8
- **Logical layout (ae_budget_v2)**:
  - Controller hub; Energy Cell/Acceptor on left/right orbit (0 channels)
  - Dense trunk (32ch) → always-visible 4× smart lanes (8ch each, including empty lanes)
  - Channel-consuming devices (terminals/buses/interfaces/monitors/P2P/…) allocated by `ChannelBranchAllocator`, then grouped into role pods by `podKind` (access/io/craft/sense/tunnel/…)
  - Drive/Chest/IO/CPU/Quantum on controller **zero-channel orbit** (not on the four lanes)
  - Edge `kind`: `capacity_trunk` / `capacity_lane` / `pod_uplink` / `device_link` / `orbit_link`; capacity edges show ribbon `used/max` and `overflow`
  - `meta.channelModel=ae_budget_v2`; `meta.lanes[]`; `meta.orbitCounts`
- **Node DTO**: `subtype`, `layer`, `podKind`, `parentId`, `patternCount`, `patternSlots[]`, `branchIndex`, `layoutSector`
- **Frontend**: `TopologyCytoscapeGraph` (**preset channel-lane layout**; trunk/lanes no longer collapsed); `topologyLaneLayout.ts`; capacity-spine edges; device list grouped by pod; settings for empty lanes / collapse pods / hide spine
- **REST**: `GET /api/network/topology` (read disk/memory snapshot, no auto-rebuild; logical = channel budget map); `POST /api/network/topology/snapshot?force=1` (OP forced capture)
- **Config**: `[webConsole] topologyEnabled` (default true), `topologyCacheTtlMs` (default **10000**), `worldMapSnapshotCooldownMs` (default **10000**, world map client snapshot request cooldown), `topologySnapshotPersist` (default true), `topologySimulatedEnabled` (default **false**, cable simulation deprecated); world map — see §11.26

### 11.18 Page Visibility Polling (Phase 4b)

- **Hooks**:
  - `hooks/usePageVisibility.ts` — listens to `visibilitychange` + `focus`/`blur`, returns whether the tab is visible
  - `hooks/useVisibilityAwarePolling.ts` — built on `useInterval`; when `pauseWhenHidden=true` and the tab is hidden, sets `delay=null` to pause; fires one immediate poll when visibility returns
- **Setting**: `localStorage.webae_pause_refresh_when_hidden` (default `true`); Settings → Data Freshness → “Pause refresh when tab hidden” toggle
- **Global refresh** (`AppContext`): `autoRefresh` timer and TopBar countdown freeze while `refreshPaused`; on tab visible again, `refreshTick++` and countdown resets
- **Polling coverage**:
  - `AppContext` connection heartbeat (30s)
  - `useWebAlerts` (10s)
  - `useSnapshotData` (via `refreshTick`, pauses with global refresh)
  - `useNetworkMetrics` dashboard metrics
  - `usePlayers` player list / online trend
  - `pages/Chat.tsx` chat 2.5s + players 10s
  - `pages/AeOrdering.tsx` orders 3s (`autoRefreshOrders`)
- **TopBar UX**: shows “Paused” instead of countdown while paused (`aria-live`)

### 11.19 GT Page Charts & Status Visualization (Phase 4c)

- **Utils**: `utils/gtChartData.ts` — `getGtStatusBreakdown` (reuses `overviewDataSources`), `getGtRecipeMapBreakdown` (top 8 + Other), `isGtMachineErrorRow`
- **Component**: `components/gt/GtSummaryCharts.tsx` — two-column Cards: status pie (active/error/idle) + recipe-map horizontal bar chart; pure SVG/CSS, no external chart library
- **Table**: `GtMachines.tsx` progress column uses antd `Progress` (`exception` on errors); `rowClassName` highlights Error/Problem rows
- **Multi-network**: merges `selectedNetworks` via `useSnapshotData` before feeding summary charts and table

### 11.20 Alert Rules Web Editor (Phase 4d)

- **Validation**: `webae/alerts/WebAlertsConfigValidator.java` — poll 1–300s, CPU stuck 1–120min, channel thresholds, inventory rules require itemId or fluidName
- **Persistence**: `ConfigWebAlertsLoader.save()` writes `web-alerts.json` and refreshes the 30s memory cache; `WebAlertEngine` reads via `get()` each tick
- **REST**: `PUT /api/alerts/rules` requires `WebAuthOpCheck.isOp(actorUuid)`; `GET /api/alerts` includes `canEditRules`
- **Frontend**: `components/settings/AlertsRulesEditor.tsx` — editable Settings alerts tab (toggles/thresholds/inventory CRUD); non-OP sees read-only Descriptions

### 11.20a Outbound Integration: Webhooks + Server Health (reference plan Phase 2)

- **Webhooks** (AE2 Web Integration style): extend `web-alerts.json` with `webhooks[]` (`id`/`url`/`enabled`/`events`/`mention`); `WebhookDispatcher.java` single-thread `BlockingQueue` async POST (Discord embed or generic JSON); queue cap 1000; 5s HTTP timeout; enqueued on `WebAlertStore.recordNew`; URLs server-only, API responses masked via `sanitizeForClient`
- **Event types**: `inventory_threshold` / `cpu_stuck` / `gt_error` / `order_complete` / `channel_overload` / `server_tps_below` / `automation_craft`
- **TPS alert**: `serverTpsBelowEnabled` + `serverTpsThreshold` (default 15) + `serverTpsDurationSeconds` (default 60); evaluated by `WebAlertEngine` via `ServerHealthSampler`
- **Health sampler**: `webae/health/ServerHealthSampler.java` — mean of `MinecraftServer.tickTimeArray` (last 100 ticks; same formula as `/forge tps` Overall: `mspt = mean(ns)×1e-6`, `tps = min(20, 1000/mspt)`); refreshed every tick via `HandlerTick`, ~1s samples into a 300s rolling window; `GET /api/server/health` (`ServerHealthHandler`); Diagnostics page TPS/MSPT use the same source
- **Perf diagnostics**: `webae/perf/WebAePerfProfiler.java` + `SnapshotWorkerPool` — tick phases / HTTP routes / snapshot collect timings; single-flight + reject when `queueDepth≥48`; 500ms soft timeout warns only and keeps `inFlight` until the server task finishes (hard wait cap 30s); `GET /api/server/diagnostics` (includes `snapshotWorkerBusy` / timeouts / skips); `[debug] webaePerf` → `logs/textech/webae-perf.log` (slow tick ≥5ms / slow HTTP ≥200ms always logged); frontend `pages/Diagnostics.tsx` + `useServerDiagnostics`. **New `/api/*` routes must be wired into diagnostics** — see `.cursor/rules/webae-perf-diagnostics.mdc`.

### 11.20b Monitoring Deepening (reference plan Phase 3)

- **Fluids page** (3.1): standalone `pages/Fluids.tsx` sidebar entry; type/total summary + `fluidCountHistory`; pin up to 10 fluids; `GET /api/network/metrics/fluids` + `useFluidMetrics` 5-minute window polling
- **Power downsampling** (3.2): `PowerSampler.toDto` applies `MetricDownsampleUtil` when history exceeds 120 points; `GET /api/network/p2p` adds `powerChannels[]` (frequency / avgEuPerTick / endpointCount); topology P2P mode shows power summary in `P2pMapPanel`
- **Auto craft** (3.3): extend `web-alerts.json` with `automationRules[]` (`craft_when_below` + itemId/threshold/patternId/cpuName/cooldown/requireCpuIdle/maxTriggersPerHour); `OrderSubmitService` + `AutomationCooldownTracker`; `WebAlertEngine` submits on server thread; history `type=automation_craft`; editable in Settings `AlertsRulesEditor`
- **Frontend**: `AlertsRulesEditor` webhook table + TPS toggles; `hooks/useServerHealth.ts`; Dashboard data sources `serverTps` / `serverMspt` line charts

### 11.21 Material Craft Tree (Phase 6)

- **Package**: `webae/craft/` — `CraftTreeCalculator` (`RecipeCacheStore.searchByOutput` recursion, default depth 8, cap 16, cycle detection)
- **Storage gaps**: reads current network storage snapshot for `available`/`missing`
- **REST**: `GET /api/craft/tree?item=&amount=&network=&maxDepth=` → `{success,networkId,amount,tree:{...}}`; runs on HTTP thread (recipe cache is thread-safe)
- **Frontend**: `components/patterns/CraftTreePanel.tsx`; `AeOrdering.tsx` adds `craftTree` tab

### 11.22 SSE Alert Stream (Phase 9)

- **Package**: `webae/events/EventStreamHub.java` — per-owner `PipedOutputStream` subscribers; `WebAlertStore.upsert` broadcasts `event: alert`
- **Heartbeat**: `HandlerTick` calls `tickHeartbeats()` each tick (15s `: heartbeat`)
- **REST**: `GET /api/events/stream` — chunked `text/event-stream`; unregisters on stream close
- **Frontend**: `hooks/useEventStream.ts` (`EventSource` + `?token=`); complements `useWebAlerts` 10s polling

### 11.23 P2P Channel Map (Phase 10)

- **Package**: `webae/topology/P2pTunnelEnumerator` — filters P2P classes from `IGrid.getMachinesClasses()`; reflects `getFrequency`/`isOutput`
- **DTO**: `P2pTunnelDto` + `P2pMapSnapshot.fromTunnels` groups by frequency
- **REST**: `GET /api/network/p2p?network=<id>` — **cache read** (scheduler pre-collect); requires `topologyEnabled`; `?refresh=1` async rebuild
- **Frontend**: `NetworkTopology.tsx` view mode `p2p` + `P2pMapPanel.tsx` (card grid layout, per-frequency cards with IN/OUT direction tags + coordinates, search & sort)

### 11.24 Monitor Line Preview (Phase 11)

- **Package**: `webae/monitor/MonitorPreviewCollector` — reads `TileEntityAdvanceDataMonitor.getDoubleValues(slot)`
- **REST**: `GET /api/monitor/preview?dim=&x=&y=&z=&slot=` — main thread 10s timeout; owner check
- **Frontend**: `MonitorBindings.tsx` Preview button per slot + Drawer + `ChartTrendSvg`

### 11.25 PWA & Mobile (Phase 12)

- **Manifest**: `webae-frontend/public/manifest.webmanifest` → Vite build to `assets/textech/webae/`
- **HTML**: `index.html` adds `theme-color`, Apple mobile meta, `link rel=manifest`
- **CSS**: `styles/global.css` `@media (max-width: 768px/480px)` — fixed sider, content padding, table font size
- **Layout**: root `Layout` uses `webae-layout` class in `AppLayout`

### 11.26 World Map View (three-source capture / client snapshot / SP direct)

- **Default** `worldMapSnapshotMode=client_only`: no server terrain/AE render; MP capture runs on consenting **player clients** per-chunk with `worldMapSnapshotSourcePriority` (default `dynmap,journeymap,client_gl`). Manual update cooldown: `worldMapSnapshotCooldownMs` (default 10 s).
- **SP direct** (`worldMapSpDirectServe`): integrated server serves missing tiles via FS Dynmap/JM or client GL bridge (`PacketWorldMapDirectCaptureRequest/Response` IDs 45/46); headers `X-WorldMap-Tile-Status=direct`, `X-WorldMap-Tile-Source=...`.
- **AE overlay**: `WorldMapAeVectorOverlayRenderer` on client for snapshot upload and SP direct — **device blocks rasterized** via `WorldMapFaceRasterizer.rasterizeTopFaceCategoryId` (category-ID PNG); cables/parts remain vector; cable width `worldMapAeCableWidthBlocks` (default 0.25 blocks).
- **Tile compositing (frontend)**: `chunkTileScreenRect` anchors chunk **north-west** corner; `WORLD_MAP_TILE_FLIP_Y` (`scaleY(-1)`) aligns PNG row 0 (north) with `worldToScreen`; tests in `worldMapTerrain.test.ts`.
- **Frontend**: unified `TopologyWorldMapView` snapshot chunks; Leaflet Dynmap terrain view removed (external Dynmap link kept).
- **Dev deps**: `dependencies.gradle` includes `devOnlyNonPublishable` JourneyMap 5.2.18 and **GTNH-Web-Map (GWM) 0.4-beta-1** for local `runClient`/`runServer` only; not bundled in release jars.

### 11.27 Spark Profiler Integration

- **Optional dependency**: `dependencies.gradle` uses the Curse Maven Spark Forge 1.7.10 file (`curse.maven:spark-361579:3577247`) as `compileOnly` plus dev-only runtime. TeXTech never bundles Spark; the pack must install `spark-forge1710.jar` separately.
- **Runtime gate**: `SparkService.isEnabled()` requires both `[webConsole] sparkEnabled=true` and Forge modid `spark` loaded. When either is false, `/api/config` exposes `sparkEnabled=false`, navigation hides the page, and direct Spark API calls return 503.
- **Command bridge**: Admin requests run `spark profiler start --timeout N` / `spark profiler stop` on the server thread and capture asynchronous Spark output through a reflective `ICommandSender` proxy. This avoids linking Spark private implementation classes and tolerates 1.7.10 patch builds.
- **Persistence**: `SparkProfileStore` keeps bounded JSON metadata in `TeXTech/WebAE/spark-history.json`, including status, start time, duration, initiator, captured output, and Spark Viewer URL. Active records are marked `interrupted` after a server restart.
- **REST/frontend**: `SparkHandler` provides status, detail, start, stop, and delete endpoints. `pages/Spark.tsx` provides duration input, current status, history table, Viewer links, output details, and two-run metadata comparison. The call tree/flame view remains in the official Spark Viewer rather than being copied into the mod.
- **Package**: `webae/worldmap/` + `webae/worldmap/engine/` (Dynmap-grade UV/ray core) — see `project-structure-details.mdc`; frontend overlay is `WorldMapAeOverlayLayer.tsx` (not a Java class)
- **Render engines**: `WorldMapRenderSupport.renderForView` dispatches flat+`uv` → `WorldMapFlatUvRenderer`; oblique+`ray` → `WorldMapIsoRayRenderer` (`WorldMapObliqueProjection` ortho rays + voxel DDA + `WorldMapFaceRasterizer` UV/biome/lighting); `legacy` painters kept as fallback; meta exposes `flatRenderEngine` / `obliqueRenderEngine`
- **REST**: `GET /api/worldmap/meta?network=<id>&quality=` (includes `zoomLevels[]`, `dynmapMaxZoom`); terrain/AE tile URLs fixed at z0; AE tiles are category ID maps (R=categoryId); cache under `{view}/ae-id/`
- **Snapshot**: `TopologySnapshot.aePlacements[]` (written on logical capture; block/cable/part coords + iconItemId)
- **Quality tiers**: `WorldMapQualityTier` — low/medium/high/ultra; cache `map-tiles/{view}/q{tier}/z0/` and `{view}/ae-id/q{tier}/z0/`
- **Single-resolution zoom**: default `webWorldMapZoomLevels=1`; built-in mode always z0 + viewport scale; Dynmap mode Leaflet `maxNativeZoom` + coordinate remap for sharpest tiles
- **AE overlay tinting**: **`worldMapAeOverlayOpacity`** (**0–1**) → tinted pixel alpha only; layer `isolation` + AE `mix-blend-mode: normal`; per-tile re-tint. Tile compositing: `WORLD_MAP_TILE_FLIP_Y`. Snapshot: keep current + previous versions only (`previousSnapshotVersion` in meta).
- **AE oblique**: `WorldMapAeObliqueRayRenderer` shares terrain oblique projection/ray pipeline
- **Optional zoom pyramid**: when `webWorldMapZoomLevels>1`, `WorldMapZoomPyramid` parent synthesis enabled
- **Block patches (Phase 4)**: `WorldMapBlockPatchRegistry` + `WorldMapGtPatchResolver` — built-in stair/slab; `assets/textech/worldmap/patches/` (`gregtech_machines` / `casings` / `structural` / `pipes`); GT pipes/cables use MTE `getConnections()` dynamic AABB; `webWorldMapBlockPatchesEnabled`
- **Server texture atlas (Phase 4)**: `WorldMapServerAtlas` — bakes hot block face PNGs into a grid atlas; registered on `WorldMapTextureRegistry` load; `webWorldMapServerAtlasEnabled` (default true), `webWorldMapServerAtlasPx` (2048)
- **AE overlay quality**: `worldMapAeOverlayQualityTier` (default ultra, decoupled from terrain quality); meta exposes `aeOverlayQualityTier`; dynmap mode stacks chunk tint via `DynmapAeOverlayBridge`
- **AE terrain boost (optional)**: `worldMapAeQualityBoost` (default false) — when enabled, AE-device chunks get +1 terrain tier; meta exposes `aeQualityBoost`
- **Config**: `[webConsole] worldMapEnabled`, …, `worldMapBlockPatchesEnabled`, `worldMapServerAtlasEnabled`, `worldMapServerAtlasPx`, `worldMapAeQualityBoost`
- **HD**: `PacketWebMapTileJob` (34) / `PacketWebMapTileUpload` (35) include `layer` + `quality`; `worldMapClientCaptureMode=when_online` prefers client GL for all tiers; `WorldMapChunkCaptureHandler` proactive pre-warm; `WorldMapTerrainFallback` + `WorldMapDynmapChunkCropper` progressive placeholders; `WorldMapTilePrefetcher` + `WorldMapTileProgressTracker` for batch prefetch and progress API
- **Frontend**: `useWorldMapTileLoader` (fixed `zoom=0`)/`useWorldMapProgress`; `WorldMapAeOverlayLayer` Canvas category tint; `WorldMapAeColorModal` (toolbar palette button on world map); AE prefetch only when overlay visible (`aeVisible`)
- **Phase C/D extensions**:
  - **Terrain dual mode**: `worldMapTerrainSource` (`auto`/`dynmap`/`self`)
  - **Dynmap bridge**: `dynmapMaxZoom` + `buildDynmapTileUrlAtDisplayZoom` single-resolution fetch
  - **Oblique switch**: `worldMapObliqueEnabled` (AND with `worldMapViewsEnabled`)
  - **Dual-mode UX**: `TopologyWorldMapView` (snapshot/direct); `TopologyDynmapView` deprecated for terrain (external Dynmap link only)

## 12. Frontend Resources

The frontend source lives at `webae-frontend/` in the project root (React + TypeScript + Ant Design + Vite). The build output goes to `src/main/resources/assets/textech/webae/`, served directly by NanoHTTPD. This directory is not required client-side (no `@SideOnly` requirement) — used only for server-side HTTP serving.

Build output (Vite bundle, contenthash filenames):
- `index.html` — SPA entry, references hashed JS/CSS + PWA meta
- `manifest.webmanifest` — PWA manifest (Phase 12)
- `assets/index-[hash].css` — global styles + advanced/minimal mode effect layers
- `js/index-[hash].js` — main bundle (page components + i18n + context + utils)
- `js/antd-[hash].js` — Ant Design + icons (~1.1MB, gzip ~353KB)
- `js/react-[hash].js` — React + ReactDOM (chunked on demand)
- `js/gridstack-[hash].js` — GridStack (tree-shaken)

Total gzip size ~450KB, fully offline-packaged (no CDN dependencies). Build command: `cd webae-frontend && npm install && npm run build`.

## 13. Debugging and Troubleshooting

| Issue | Possible Cause | Check |
|-------|---------------|-------|
| `localhost:8090` unresponsive after startup | `enabled=false` | Check `config/textech/textech.cfg` `[webConsole] enabled` |
| Port in use | Another service occupies 8090 | Change `port` config |
| Authentication failure (401) | Token expired or revoked | Run `/admweb issue` to regenerate; check `code` field (`token_expired` vs `invalid_token`) |
| Refresh returns 403 | Non-OP trying admin refresh | Use `/admweb refresh` in-game instead |
| Storage/power shows empty briefly | Cache cold-starting | Wait one `refreshIntervalMs` cycle; the scheduler only collects active networks |
| Storage/power persistently empty | AE2 network not connected or no active network | Open the web console / select a network so `SnapshotScheduler.markActive` runs |
| Recipe search returns no results | Not uploaded / did not Fetch recipes / empty IndexedDB / exact search on displayName | OP `/admweb recipes upload snapshot` (or `upload`), then **Fetch recipes** on the Recipes page; enable `[debug] debugWebae=true` for NEI collection logs |
| Icons show as text abbreviations | Auth failure / pack-name mismatch / itemId format mismatch | v3.0 fixed: `WebAuthMiddleware` supports `?token=` query parameter fallback, frontend Icon component auto-appends token; confirm `AeSnapshotCollector` uses registry names; check `TeXTech/WebAE/icons/default-pack.txt` |
| Chat messages don't appear | Not uploaded or token has no playerUuid | Confirm `ChatMessageStore` persisted `web-chat.json`; check `/api/chat/since` response; in-game messages require `sendChatMsg` broadcast |
| Player avatars fall back to initials | Offline mode or GameProfile has no textures | `SkinUrlResolver` returning null for offline players is expected; for online players check the GameProfile textures property |
| CountDownLatch timeout (503) | Main thread overloaded | Increase timeout or check `HandlerTick` task backlog |
| GT machine list empty | `GtCompat` not enabled | Verify GTNH GT mod is installed |
