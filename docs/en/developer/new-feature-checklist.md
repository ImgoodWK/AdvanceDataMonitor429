# New Feature Development Checklist

Before adding a new feature or subsystem to TeXTech, review this checklist to avoid class-count bloat and duplicated boilerplate.

See also the Chinese version: [`new-feature-checklist.md`](new-feature-checklist.md).

## Architecture and reuse

- [ ] Can this extend existing Item / Block / TE patterns instead of a new parallel package?
- [ ] AE logic goes through `compat/ae/AeCompat` only (no direct `legacy/` / `native_/` imports)
- [ ] New loom cells extend `AbstractDataLoomItemCell` / `AbstractDataLoomFluidCell`
- [ ] Pocket upgrade cards extend `AbstractPocketUpgradeCard`

## GUI

- [ ] Correct base class?
  - Small config dialog → `AdmItemConfigScreen`
  - Monitor binding sub-page → `AbstractMonitorSubGui`
  - New container GUI → `ADM_UiContainer` + `gui/framework/`
- [ ] Textures from `AdmGuiTextures`, not duplicated `ResourceLocation` fields
- [ ] Avoid copying `GuiSub*` boilerplate

## Network and server

- [ ] Server handlers use `PacketHandlers.runOnServer` / `runOnServerThread`
- [ ] Block/TE packets call `NetworkValidationUtil` (range + `IOwnableTile` permission)
- [ ] Register in `loader/LoaderNetwork` with a fixed packet ID

## Registration and handlers

- [ ] Register only in the appropriate `Loader*` classes
- [ ] Thin event handler + domain class in `handler/`; register in `LoaderHandler`

## Docs and localization

- [ ] Update both `assets/textech/lang/en_US.lang` and `assets/textech/lang/zh_CN.lang`
- [ ] Update `manual/` JSON and `docs/` for player-facing features
- [ ] Update `project-structure.mdc` / `project-structure-details.mdc` for new Java files

## Expected class count

| Feature type | Typical new classes |
|--------------|---------------------|
| Simple item | 1 Item + lang |
| Loom cell | 1 Item + 1 Config + lang + manual |
| Handheld config | extend `AdmItemConfigScreen` + 0–1 Packet |
| Monitor binding page | extend `AbstractMonitorSubGui` |
| New container GUI | 1 Container + 1 Gui (`ADM_UiContainer`) + 0–1 Packet |

If you expect five new 700+ line GUI classes, extract a base class first.
