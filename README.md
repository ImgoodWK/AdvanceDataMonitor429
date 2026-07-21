# TeXTech

> Mod ID: `textech` · Minecraft 1.7.10 / GregTech: New Horizons

TeXTech is a GTNH community mod that extends AE2 monitoring, data weaving, AI assistance, grapple traversal, and an embedded WebAE browser console.

---

## Quick Start

```bash
./gradlew build
```

Output under `build/libs/`:

| Artifact | Required? | Notes |
|----------|-----------|--------|
| `TeXTech-*.jar` (main) | **Yes** | Core mod. Does **not** ship the ~65 MB Vosk offline speech model. |
| `TeXTech-*-voice.jar` | Optional | Offline Chinese STT (`modid` `textechvoice`). Put next to the main jar in `mods/`. |
| MCEF (external) | Optional | In-game Chromium for monitor web surfaces. Download [montoyo MCEF 1.7.10 → 0.6](https://montoyo.net/wd3/?modid=mcef) into `libs/` for dev / `mods/` for play. Official note: MCEF before 1.10.2 may not work with modern launchers; natives come from [montoyo.net/jcef](https://montoyo.net/jcef). |

Without the voice jar you can still use `voice.sttMode=http` or set `voice.sttModel` to a local Vosk model directory. Without MCEF, WebAE monitor frames prefer **browser viewport push** (`browser-jpeg` from the WebAE tab) and fall back to host Chrome/Edge embed capture (`spa-jpeg`).

**Development:** Vosk models stay under `src/main/resources` for `runClient` — no voice companion jar needed. For MCEF in-dev, place `mcef-1.7.10-0.6.jar` in `libs/` (this repo may already include it; see `.cursor/rules/external-deps-jars.mdc`).

Run a dev client with `./gradlew runClient` (GTNH dev setup: [Gradle workflow](docs/zh/developer/Gradle工作流.md); BetterQuesting quest fixtures sync automatically from `dev-fixtures/betterquesting/`).

---

## Documentation

| Language | Index |
|----------|--------|
| **中文** | [docs/zh/README.md](docs/zh/README.md) |
| **English** | [docs/en/README.md](docs/en/README.md) |
| **Overview** | [docs/README.md](docs/README.md) |

Key docs:

- Player: [zh](docs/zh/player/用户手册.md) · [en](docs/en/player/player-guide.md)
- WebAE: [zh user](docs/zh/webae/用户手册.md) · [zh dev](docs/zh/webae/开发者手册.md) · [en user](docs/en/webae/user-guide.md) · [en dev](docs/en/webae/developer-guide.md)
- Developer map: [zh](docs/zh/developer/documentation-map.md) · [en](docs/en/developer/documentation-map.md)

---

## License

See repository license file. GTNH mod development conventions apply.
