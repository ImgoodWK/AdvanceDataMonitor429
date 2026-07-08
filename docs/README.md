# TeXTech Documentation

> Mod ID: `textech` · Minecraft 1.7.10 / GTNH · Last synced: 2026-07

Documentation is split by language under `docs/zh/` (简体中文) and `docs/en/` (English).  
The project root [`README.md`](../README.md) keeps only a short intro and build commands.

---

## Documentation layers

| Layer | Role | Paths |
|-------|------|-------|
| L0 Machine-readable | Agent / CI source of truth | `ConfigDescriptions.java`, `LoaderNetwork.java`, `.cursor/rules/*.mdc` |
| L1 Developer specs | Contributor design | `docs/*/developer/`, `docs/*/webae/developer-guide.md`, `docs/*/ai-assistant/` |
| L2 Player / admin | Tutorials | `docs/*/player/`, `docs/*/webae/user-guide.md`, `assets/textech/manual/` |
| L3 Vision / design | **Not implementation spec** | `docs/*/design/` |

Maintenance map: [zh](zh/developer/documentation-map.md) · [en](en/developer/documentation-map.md)

---

## Choose your language

| Language | Index |
|----------|--------|
| **中文** | [docs/zh/README.md](zh/README.md) |
| **English** | [docs/en/README.md](en/README.md) |

---

## Document map

| Topic | 中文 | English |
|-------|------|---------|
| Player guide | [zh/player/用户手册.md](zh/player/用户手册.md) | [en/player/player-guide.md](en/player/player-guide.md) |
| Developer tech doc | [zh/developer/技术文档.md](zh/developer/技术文档.md) | [en/developer/technical-documentation.md](en/developer/technical-documentation.md) |
| Documentation map | [zh/developer/documentation-map.md](zh/developer/documentation-map.md) | [en/developer/documentation-map.md](en/developer/documentation-map.md) |
| WebAE user guide | [zh/webae/用户手册.md](zh/webae/用户手册.md) | [en/webae/user-guide.md](en/webae/user-guide.md) |
| WebAE developer guide | [zh/webae/开发者手册.md](zh/webae/开发者手册.md) | [en/webae/developer-guide.md](en/webae/developer-guide.md) |
| WebAE OC integration | [zh/webae/oc-integration.md](zh/webae/oc-integration.md) | [en/webae/oc-integration.md](en/webae/oc-integration.md) |
| UI framework | [zh/developer/ui-framework.md](zh/developer/ui-framework.md) | [en/developer/ui-framework.md](en/developer/ui-framework.md) |
| New feature checklist | [zh/developer/new-feature-checklist.md](zh/developer/new-feature-checklist.md) | [en/developer/new-feature-checklist.md](en/developer/new-feature-checklist.md) |
| Gradle workflow | [zh/developer/Gradle工作流.md](zh/developer/Gradle工作流.md) | [en/developer/gradle-workflow.md](en/developer/gradle-workflow.md) |
| Temporary textures | [zh/developer/临时材质清单.md](zh/developer/临时材质清单.md) | [en/developer/temporary-textures.md](en/developer/temporary-textures.md) |
| GTNH version compatibility | [zh/developer/GTNH版本兼容说明.md](zh/developer/GTNH版本兼容说明.md) | [en/developer/gtnh-version-compatibility.md](en/developer/gtnh-version-compatibility.md) |
| AE2 2.9 compat (dev) | [zh/developer/ae-compat-290.md](zh/developer/ae-compat-290.md) | [en/developer/ae-compat-290.md](en/developer/ae-compat-290.md) |
| Plan E (remove Legacy) | [zh/developer/ae-compat-plan-e-remove-legacy.md](zh/developer/ae-compat-plan-e-remove-legacy.md) | [en/developer/ae-compat-plan-e-remove-legacy.md](en/developer/ae-compat-plan-e-remove-legacy.md) |
| AI assistant dev | [zh/ai-assistant/开发指南.md](zh/ai-assistant/开发指南.md) | [en/ai-assistant/development-guide.md](en/ai-assistant/development-guide.md) |
| Grapple subsystem | [zh/subsystems/挂索节点系统设计.md](zh/subsystems/挂索节点系统设计.md) | [en/subsystems/grapple-system-design.md](en/subsystems/grapple-system-design.md) |
| Design vision (draft) | [zh/design/未来开发愿景.md](zh/design/未来开发愿景.md) | [en/design/future-development-vision.md](en/design/future-development-vision.md) |
| Brand & visual design | [zh/design/品牌视觉设计指南.md](zh/design/品牌视觉设计指南.md) | [en/design/brand-visual-design-guide.md](en/design/brand-visual-design-guide.md) |
| Web console redirect | [zh/developer/web-console.md](zh/developer/web-console.md) | [en/developer/web-console.md](en/developer/web-console.md) |
| Archive | [zh/archive/GoldenThrone_GT_Multiblock_移植指南.md](zh/archive/GoldenThrone_GT_Multiblock_移植指南.md) | — |

---

## Maintenance

When code or in-game names change, update **both** language trees where applicable, and align names with:

- `src/main/resources/assets/textech/lang/zh_CN.lang`
- `src/main/resources/assets/textech/lang/en_US.lang`

Before PRs that touch docs, config, or packets, run:

```bash
python tools/doc-check/doc-consistency-check.py
```

Cursor Agent rules under `.cursor/rules/` complement these docs for quick file lookup.
