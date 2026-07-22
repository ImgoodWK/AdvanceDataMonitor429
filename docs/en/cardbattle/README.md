# TeXTech Card Battle

Card Battle has two independent runtime entries. The recommended production authority is the standalone Node/TypeScript service in `cardbattle-server/`; it requires no Minecraft, Forge server, or MC tick loop. The Java engine remains an optional embedded mirror for TeXTech single-player and server installations.

Standalone runs, authoritative matches, and pending rewards persist under `CARDBATTLE_DATA_DIR`. The browser stores only the active `runId` and `matchId`, so a browser refresh or service restart can resume the session.

## Standalone deployment

```powershell
cd cardbattle-frontend
npm.cmd ci
npm.cmd run build:standalone
cd ../cardbattle-server
npm.cmd ci
npm.cmd run build
npm.cmd start
```

For a self-contained image, run `docker build -f cardbattle-server/Dockerfile -t textech-cardbattle .` from the repository root, or use `cardbattle-server/docker-compose.example.yml`.

## Embedded mod use

1. Install TeXTech with `[cardBattle] enabled=true` (the default).
2. Enter a single-player or server world.
3. Open `http://127.0.0.1:8787/` in a browser.
4. Local installations use the `local` development token by default; remote access should use a WebAE token.

The in-game status command is `/textech card status`.

## Current gameplay

- Four-card opening mulligan: replace any number, draw replacements first, then shuffle the returned cards back so they cannot be immediately redrawn.
- Both players take the normal round-one draw after locking the mulligan, entering the first main window with five cards.
- A ten-card hand limit burns excess draws or generated cards to discard; attempting to draw from an empty deck loses the match.
- Alternating action priority: units, structures, slow spells, and fast spells pass priority; burst spells resolve immediately and keep priority. Two consecutive main-action passes end the round.
- Three spell speeds: slow spells start only from an empty main window, fast spells may join main or combat response stacks, and burst spells never enter the stack.
- A public LIFO spell stack and separate response-pass counter. Combat opens a spell response window after blocks and before damage.
- Alternating attack token: its owner may attack once in that round, then it normally changes sides.
- Up to 3 unused mana becomes spell-only reserve; GT capacitor storage remains a separate theme mechanic.
- Regular mana pays first, followed by spell reserve for spells and then GT storage. Surviving units heal to maximum health at round start, and a removed declared blocker leaves a ghost block.
- Six board slots, ordered attackers, explicit blocker assignment, stealth restrictions, and Thaumcraft Ordo + Aer repositioning.
- A branching four-column PvE route with normal battles, elites, and a final boss.
- Post-battle deck additions, run powers, versioned voltage reward placeholders, and board-skin unlocks.
- Drag-to-play with a click fallback, contextual action controls, and a three-column battle workspace.
- Hover, focus, or select any card to inspect authoritative exact rules, targeting, speed, keywords, aspects, and live board stats.
- Attackers animate one card at a time toward their blocker or Nexus with strike flashes, damage values, recoil, and death feedback.

## Standalone environment

| Variable | Default | Meaning |
|---|---:|---|
| `CARDBATTLE_HOST` | `127.0.0.1` | HTTP bind address; the image uses `0.0.0.0`. |
| `CARDBATTLE_PORT` | `8787` | HTTP port. |
| `CARDBATTLE_DATA_DIR` | `./data/runtime` | Persistent runs, matches, and reward ledger. |
| `CARDBATTLE_DEV_TOKEN` | unset | Standalone account token; use a strong secret in production. |
| `CARDBATTLE_CORS_ORIGINS` | empty | Comma-separated allowed browser origins. |
| `CARDBATTLE_BRIDGE_TOKEN` | empty | Private MC delivery bridge token; rewards only accumulate when absent. |

## Embedded configuration

| Key | Default | Meaning |
|---|---:|---|
| `enabled` | `true` | Start the embedded server with the world. |
| `port` | `8787` | HTTP listen port. |
| `bindAddress` | `127.0.0.1` | Local-only by default; expose deliberately and configure a firewall. |
| `devToken` | `local` | Local bypass token; clear it to require WebAE authentication. |

The system uses familiar alternating-priority, attack-token, response-stack, and route-adventure structures. Characters, card writing, art, and brand assets are original TeXTech material. See [rules.md](./rules.md), [ui-design.md](./ui-design.md), and [rewards-bridge.md](./rewards-bridge.md).

## Development

- Standalone authority: `cardbattle-server/`
- Optional Java mirror: `src/main/java/com/imgood/textech/cardbattle/`
- Frontend: `build:standalone` creates the standalone SPA; `build` writes jar resources
- Authoritative card data: `cardbattle-server/src/data/catalog.ts`; `catalog:export` synchronizes jar `cards.json`
- Art: `cardbattle-server/public/card-art/` and the jar card-art directory

Build the frontend with `npm.cmd run build` from `cardbattle-frontend/`. The build writes hashed assets into the jar resource directory without requiring players to install the frontend toolchain.

Meowa credentials must be supplied only through the local `MEOWART_API_KEY` environment variable and must never be committed, passed as a command argument, or logged.
