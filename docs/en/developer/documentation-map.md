# TeXTech Documentation Maintenance Map

> Audience: contributors / Codex / Cursor Agent · Last synced: 2026-07  
> 中文: [documentation-map.md](../../zh/developer/documentation-map.md)

When you change a feature area, use this map to update the right docs and rules so `AGENTS.md`, `docs/`, in-game `manual/`, and `.cursor/rules/` stay aligned.

---

## Documentation layers

| Layer | Path | Role |
|-------|------|------|
| L0 Machine-readable | `AGENTS.md`, `ConfigDescriptions.java`, `LoaderNetwork.java`, `.cursor/rules/*.mdc` | Single source of truth for Codex / Cursor / CI |
| L1 Developer specs | `docs/en/developer/`, WebAE developer guide, AI assistant guide | Contributor design docs |
| L2 Player / server admin | `docs/en/player/`, WebAE user guide, `assets/textech/manual/` | Tutorials and how-tos |
| L3 Vision / design | `docs/en/design/` | **Not implementation spec**; excluded from CI checks |

## Agent reading policy (token savings)

- **One language by default**: English tasks read `docs/en/` only; do not bulk-read `docs/zh/` unless bilingual publish sync is required.
- **Do not default-read L3 / archive**: Unless the user asks for vision, brand design, or archive notes, do not Read `docs/*/design/` or `docs/*/archive/`.
- **L0 long appendices on demand**: `project-structure-details.mdc` and full AI architecture docs are not auto-injected; Read when you need per-file lists or architecture detail.
- AI architecture authority: `docs/en/ai-assistant/development-guide.md`; `.cursor/rules/ai-assistant.mdc` is hard-constraints only.

---

## By feature: what to update

| Domain | Source code | Developer docs | Player docs | Rules / other |
|--------|-------------|----------------|-------------|---------------|
| New Java class/package | `loader/` registration | [Technical doc](technical-documentation.md) · this map | — | `project-structure.mdc` · `project-structure-details.mdc` |
| Config keys | `Config.java` · `Config*Loader.java` | Technical doc · WebAE [§4](../webae/developer-guide.md#4-configuration) | [Player guide §11](../player/player-guide.md#11-configuration-reference) · `manual/config_reference.json` | `ConfigDescriptions.java` · lang |
| Network packets | `LoaderNetwork.java` | [Technical doc §7](technical-documentation.md#7-network-packets) | — | **`network-packets.mdc`** |
| AI assistant | `assistant/` | [AI development guide](../ai-assistant/development-guide.md) | [Player guide §8](../player/player-guide.md#8-ai-chat--assistant) | `ai-assistant.mdc` · `assistant-features.json` |
| Grapple | `handler/Grapple*` | [Grapple design](../subsystems/grapple-system-design.md) | Player guide §3.7 | — |
| WebAE console | `webae/` · `webae-frontend/` | [WebAE developer guide](../webae/developer-guide.md) | [WebAE user guide](../webae/user-guide.md) · `manual/web_console.json` | `webae-frontend.mdc` |
| Card Battle web game | `cardbattle-server/` · `cardbattle-frontend/` · `cardbattle/` Java package | [Card Battle](../cardbattle/README.md) · [rules](../cardbattle/rules.md) · [UI design](../cardbattle/ui-design.md) | — | `.cursor/skills/textech-card-art/` · [reward bridge](../cardbattle/rewards-bridge.md) |
| World map | `webae/worldmap/` | WebAE [§11.26](../webae/developer-guide.md#1126-world-map-view-phase-ab--ae-overlay) · §4 worldMap* | WebAE user guide · `topology_text` lang | `project-structure-details.mdc` |
| Rendering / TESR | `renders/` | Technical doc §11 | — | `project-structure-details.mdc` renders section |
| Lang keys | `zh_CN.lang` + `en_US.lang` | — | Match player guide names | `manual/` JSON titleKey/textKey |
| Debug flags | `config/Config*Loader.java` | Technical doc §16 | — | `gtnh-mod-context.mdc` |

---

## Cross-reference strategy (deduplication)

| Content | Authoritative doc | Elsewhere |
|---------|-------------------|-----------|
| Packet ID table | `network-packets.mdc` | Technical doc §7 summary + link only |
| AI architecture | `ai-assistant/development-guide.md` | Technical doc §8 overview; `ai-assistant.mdc` hard-constraints only |
| Per-file package inventory | `project-structure-details.mdc` (on demand) | `project-structure.mdc` short index |
| WebAE REST API | WebAE developer guide §5 | User guide: operations only |
| worldMap* config | `ConfigDescriptions` + WebAE §4 | User guides: short summary |
| Implementation status | WebAE dev guide §11 subsystem index | Remove stale "Phase B pending" wording |

---

## Before opening a PR

```bash
python tools/doc-check/doc-consistency-check.py
```

See `.cursor/rules/docs-sync.mdc` and script output.

---

## Related indexes

- [docs/en/README.md](../README.md) — English doc tree  
- [docs/README.md](../../README.md) — bilingual overview  
- [AGENTS.md](../../../AGENTS.md) — Codex project entry point
- [docs-sync.mdc](../../../.cursor/rules/docs-sync.mdc) — Codex / Cursor shared sync rules  
