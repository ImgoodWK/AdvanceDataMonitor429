# AdvanceDataMonitor Documentation (English)

> Mod ID: `advancedatamonitor` · Minecraft 1.7.10 / GTNH · Last synced: 2026-06

中文文档: [docs/zh/README.md](../zh/README.md)

---

## By audience

| You are | Start here |
|---------|------------|
| **New player** | [Player Guide §0 Quick Overview](player/player-guide.md#0-quick-overview) |
| **Planner user** | [Player Guide §19 Advance Planner](player/player-guide.md#19-advance-planner) |
| **Server / pack author** | [Player Guide §2 Environment](player/player-guide.md#2-environment-and-installation) · [§11 Config](player/player-guide.md#11-configuration-reference) |
| **New contributor** | [Technical Documentation](developer/technical-documentation.md) · [Gradle Workflow](developer/gradle-workflow.md) |
| **AI assistant work** | [AI Assistant Development Guide](ai-assistant/development-guide.md) · `.cursor/rules/ai-assistant.mdc` |
| **Grapple system** | [Grapple System Design](subsystems/grapple-system-design.md) |
| **Planner code** | [Technical Documentation §5.11](developer/technical-documentation.md#511-advance-planner) |

---

## Document tree

### Player

| Doc | Description |
|-----|-------------|
| [player/player-guide.md](player/player-guide.md) | Install, blocks/items, monitors, AE2, AI/voice, config, FAQ, Advance Planner |

### Developer

| Doc | Description |
|-----|-------------|
| [developer/technical-documentation.md](developer/technical-documentation.md) | Structure, Forge registration, modules, data flow (Advance Planner API: §5.11) |
| [developer/gradle-workflow.md](developer/gradle-workflow.md) | Build / migration (body in Chinese; see header note) |
| [developer/temporary-textures.md](developer/temporary-textures.md) | Missing/placeholder block & item texture audit; **temporary** procedural art |

### AI assistant

| Doc | Description |
|-----|-------------|
| [ai-assistant/development-guide.md](ai-assistant/development-guide.md) | Architecture, file index, STT (body in Chinese; see header note) |

### Subsystems

| Doc | Description |
|-----|-------------|
| [subsystems/grapple-system-design.md](subsystems/grapple-system-design.md) | Grapple Anchor / Grapple Hook design |

---

## Naming convention

In-game display names follow `lang/en_US.lang`, e.g.:

- **Advance Data Monitor**, **Data Imprint Tool**
- **Network Linker**, **Crafting Linker**, **Advanced Storage Linker**, **Advanced Storage Link Cell**
- **Grapple Anchor** / **Grapple Hook**, **Advance Planner**, **Super Orange**, **Empyrean Holy Judgment**
- **AdvanceDataMonitor Manual** (item `manual`)
