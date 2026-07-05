# TeXTech WebAE Frontend

React + TypeScript + Ant Design frontend for the TeXTech WebAE Console.

## Build

```bash
cd webae-frontend
npm install
npm run build
```

The build output is configured to go directly into
`../src/main/resources/assets/textech/webae/` so the embedded NanoHTTPD server
serves the compiled bundle without any copy step.

## Development

```bash
cd webae-frontend
npm install
npm run dev
```

The dev server proxies `/api` and `/icons` to `http://127.0.0.1:8090` by default
(match your `[webConsole] port` in `config/textech/textech.cfg`). Start the Minecraft
server with the mod loaded to test against the live backend.

## Tech Stack

- **React 18** — UI framework
- **TypeScript 5** — type safety
- **Ant Design 5** — component library (buttons, tables, forms, modals, etc.)
- **@ant-design/icons** — icon set
- **Vite 5** — build tool / dev server
- **Inline SVG charts** — Dashboard/Power trend (`ChartTrendSvg`); bar/pie/radar widgets use CSS/SVG (no chart.js)
- **GridStack** — dashboard / storage / CPU / power drag-and-drop grids（三处 Grid 共用 `WidgetShell` + `widgetGridActions` 复制/JSON 导入导出）
- **@tanstack/react-virtual** — AE 下单页按产物/按样板虚拟滚动（`VirtualProductGrid` / `VirtualPatternGrid`）

## Features

- 24 theme color schemes (19 classic + 5 Phase 8 sci-fi: hologram, plasma, neon-pulse, quantum, crystal)
- 5 layout presets (standard, compact, wide, sidebar-right, topnav)
- Advanced / Minimal UI mode toggle (glassmorphism + effects vs clean)
- 6 number formats (full, thousands, scientific, AE-style, engineering, short)
- Preset system (save/apply/rename/delete/import/export all settings)
- WCAG 2.1 AA accessibility (keyboard nav, focus indicators, aria-live, skip link, reduced-motion)
- zh / en bilingual i18n
- Offline packaging (no CDN dependencies)
- **Phase 6–12 extensions**: craft tree tab, SSE alerts (`useEventStream`) + icon-ready lazy-load refresh, topology P2P view, monitor preview Drawer, PWA manifest + mobile responsive CSS

## Recipe & Icon APIs (NEI performance Phase 0–4)

- **Multi-handler search**: `GET /api/recipes/search?q=&handler=h1,h2` accepts comma-separated handler IDs; the Recipes page passes selected category Tags as `handler`.
- **Upload modes** (in-game, OP): `/admweb recipes upload snapshot` (storage items only), `upload` (default full NEI+Game), `upload deep` (slow item-driven NEI scan). Client also writes `<instance>/TeXTechWebAE/web-recipes.json.gz` (same gzip schema as the server cache).
- **Icon lazy-load SSE**: when `IconMissingQueue` resolves a missing icon, the server emits SSE event `icon-ready`; `useEventStream` dispatches `webae-icon-ready` so `Icon.tsx` reloads without a full page refresh.
- **NESQL import**: `[webConsole] nesqlRepositoryPath` optional; when empty, defaults to `<instance>/TeXTechWebAE`. OP runs `/admweb icons import-nesql [pack] [subpath]` (server-side, no client render).

## Project Structure

```
webae-frontend/
├── package.json          # Dependencies & build scripts
├── public/manifest.webmanifest  # PWA manifest (copied to build output)
├── vite.config.ts        # Vite config (outDir → resources/webae, base './')
├── tsconfig.json         # TypeScript config
├── index.html            # SPA entry HTML
└── src/
    ├── main.tsx          # React root
    ├── App.tsx           # ConfigProvider + Login/AppLayout switch
    ├── api/client.ts     # Fetch wrapper with Bearer auth + token refresh
    ├── types/dto.ts      # TypeScript interfaces mirroring Java DTOs
    ├── i18n/             # zh + en dictionaries + I18nProvider hook
    ├── context/AppContext.tsx  # Global state (auth, theme, settings, presets)
    ├── theme/            # Color schemes, layout presets, antd theme builder
    ├── hooks/            # useLocalStorage, useNumberFormat, useInterval, useSnapshotData
    ├── utils/            # formatNumber, icon URL, presets, dashboardResolve, widgetGridActions,
    │                     # overviewDataSources, powerDataSources, cpuColumns, recipe (groupByPrimaryOutput)
    ├── components/       # Login, Icon, Layout (Sidebar/TopBar/AppLayout),
    │                     # dashboard (AlignmentGrid/ColorField/WidgetColorSection/DashboardSettingsDrawer/
    │                     # EditWidgetModal/WidgetShell/OverviewWidgetGrid/WidgetContent/
    │                     # PowerWidgetGrid/PowerWidgetContent/ChartTrendSvg),
    │                     # recipes (...),
    │                     # patterns (PatternOrderCard/PatternDetailModal/VirtualPatternGrid/
    │                     # VirtualProductGrid/PatternProductCard — Phase 7 AE ordering)
    ├── pages/            # Dashboard, Storage, Cpu, Power, GtMachines, Recipes,
    │                     # PatternEditor (Phase 6: merged recipe search, I/O icons/qty,
    │                     # output multipliers, interface pattern info),
    │                     # AeOrdering (Phase 7: browse dual-source, virtual scroll by product/pattern,
    │                     # CPU select, hybrid progress, history; Phase 6 craft tree tab),
    │                     # NetworkTopology (logical/spatial/p2p), MonitorBindings (preview Drawer),
    │                     # Chat, Settings
    └── styles/global.css # Base styles + advanced mode effect layers + mobile responsive + GridStack overrides
```

## Icon Authentication (Bug Fix)

Browser `<img>` requests cannot attach an `Authorization` header, so the
`WebAuthMiddleware` was updated to fall back to reading the token from the
`?token=` query parameter. The `Icon` component appends the current token to
every icon URL: `/api/icon?item=...&token=...`.

## Number Format

Use the `useNumberFormat()` hook or the `fmtNum()` function from
`AppContext` to format numbers according to the user's selected format:

```tsx
const fmtNum = useNumberFormat();
fmtNum(1234567); // → "1,234,567" or "1.23M" or "1.235E+6" etc.
```
