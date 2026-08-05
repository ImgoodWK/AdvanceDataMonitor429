# TeXTech Documentation Maintenance Map

> Audience: contributors / Codex / Cursor Agent · Last synced: 2026-08<br>
> 中文: [documentation-map.md](../../zh/developer/documentation-map.md)

When you change a feature area, use this map to update the right docs and rules so `AGENTS.md`, `docs/`, the in-game `manual/`, and `.cursor/rules/` stay aligned.

---

## Documentation layers

| Layer | Path | Role |
|-------|------|------|
| L0 Machine-readable | `AGENTS.md`, `ConfigDescriptions.java`, `LoaderNetwork.java`, `.cursor/rules/*.mdc` | Single source of truth for Codex / Cursor / CI |
| L1 Developer specs | `docs/en/developer/`, WebAE developer guide, AI assistant guide | Contributor design docs |
| L2 Player / server admin | `docs/en/player/`, WebAE user guide, `assets/textech/manual/` | Tutorials and how-tos |
| L3 Vision / design | `docs/en/design/` | **Not an implementation specification**; excluded from CI checks |

## Agent reading policy (token savings)

- **One language by default**: English tasks read `docs/en/` only; do not bulk-read `docs/zh/` unless bilingual publish sync is required.
- **Do not default-read L3 / archive**: Unless the user asks for vision, brand design, or archive notes, do not read `docs/*/design/` or `docs/*/archive/`.
- **L0 long appendices on demand**: `project-structure-details.mdc` and full AI architecture docs are not auto-injected; read them only when per-file lists or architecture details are needed.
- AI architecture authority: `docs/en/ai-assistant/development-guide.md`; `.cursor/rules/ai-assistant.mdc` contains hard constraints only.

---

## By feature: what to update

| Domain | Source code | Developer docs | Player docs | Rules / other |
|--------|-------------|----------------|-------------|---------------|
| New Java class/package | `loader/` registration | [Technical documentation](technical-documentation.md) · this map | — | `project-structure.mdc` · `project-structure-details.mdc` |
| Config keys | `Config.java` · `Config*Loader.java` | Technical documentation · WebAE [§4](../webae/developer-guide.md#4-configuration) | [Player guide §11](../player/player-guide.md#11-configuration-reference) · `manual/config_reference.json` | `ConfigDescriptions.java` · language files |
| Network packets | `LoaderNetwork.java` | [Technical documentation §7](technical-documentation.md#7-network-packets) | — | **`network-packets.mdc`** |
| AI assistant | `assistant/` | [AI development guide](../ai-assistant/development-guide.md) | [Player guide §8](../player/player-guide.md#8-ai-chat--assistant) | `ai-assistant.mdc` · `assistant-features.json` |
| Grapple | `handler/Grapple*` | [Grapple design](../subsystems/grapple-system-design.md) | Player guide §3.7 | — |
| WebAE console | `webae/` · `webae-frontend/` | [WebAE developer guide](../webae/developer-guide.md) | [WebAE user guide](../webae/user-guide.md) · `manual/web_console.json` | `webae-frontend.mdc` |
| Standalone Card Battle integration | `CommandCardBattle.java` · `ConfigCardBattleLoader.java` | [Integration overview](../cardbattle/README.md) · [reward bridge](../cardbattle/rewards-bridge.md) | `manual/config_reference.json` | The game lives in a separate repository; TeXTech-GTNH keeps only the private Bridge adapter |
| World map | `webae/worldmap/` | WebAE [§11.26](../webae/developer-guide.md#1126-world-map-view-phase-ab--ae-overlay) · §4 `worldMap*` | WebAE user guide · `topology_text` language keys | `project-structure-details.mdc` |
| Rendering / TESR | `renders/` | Technical documentation §11 | — | `project-structure-details.mdc` renders section |
| GUI / UI framework | `gui/framework/` · `gui/custom/` | [ui-framework.md](ui-framework.md) | — | `gui-guidelines.mdc` |
| Language keys | `zh_CN.lang` + `en_US.lang` | — | Match player-guide names | `manual/` JSON `titleKey` / `textKey` |
| Debug flags | `config/Config*Loader.java` | Technical documentation §16 | — | `gtnh-mod-context.mdc` |
| Build / Release | `settings.gradle.kts` · `repositories.gradle` · `addon.late.gradle` | [Gradle workflow](gradle-workflow.md) | README install matrix · `CHANGELOG.md` | `.github/workflows/ci.yml` · `release.yml` · release notes |
| Repository governance / security | — | `CONTRIBUTING.md` · `SECURITY.md` · `SUPPORT.md` | README support links | Issue/PR/Discussion templates · `CODEOWNERS` · Dependabot |
| Project history / provenance | Git commits / signed tags / release SHA | [Timeline](../project/timeline.md) · [evidence guide](../project/provenance.md) | README history summary | `NOTICE.md` · `CITATION.cff` · provenance monitor |

---

## Cross-reference strategy (deduplication)

| Content | Authoritative document | Elsewhere |
|---------|------------------------|-----------|
| Packet ID table | `network-packets.mdc` | Technical documentation §7: summary and link only |
| AI architecture | `ai-assistant/development-guide.md` | Technical documentation §8 overview; `ai-assistant.mdc` hard constraints only |
| Per-file package inventory | `project-structure-details.mdc` (on demand) | `project-structure.mdc` short index |
| WebAE REST API | WebAE developer guide §5 | User guide: operations only |
| `worldMap*` configuration | `ConfigDescriptions` + WebAE §4 | User guides: short summary |
| Implementation status | WebAE developer guide §11 subsystem index | Remove stale phase wording elsewhere |
| Release asset names / verification | `.github/workflows/release.yml` | README, release notes, and Gradle workflow contain synchronized summaries only |
| Verifiable project timeline | `git log`, signed tags, and release SHA | Bilingual timeline, NOTICE, and CITATION use the same provenance ID |

---

## Before opening a PR

```bash
python tools/doc-check/doc-consistency-check.py
python tools/release/validate_repository.py
python tools/release/scan_secrets.py
python -m unittest tools.provenance.test_monitor
```

CI also runs `scan_secrets.py --tracked` after commit. See `.cursor/rules/docs-sync.mdc` and the command output for details.

---

## Related indexes

- [docs/en/README.md](../README.md) — English documentation tree
- [docs/README.md](../../README.md) — bilingual overview
- [AGENTS.md](../../../AGENTS.md) — Codex project entry point
- [docs-sync.mdc](../../../.cursor/rules/docs-sync.mdc) — shared Codex / Cursor synchronization rules
