# GTNH Version Compatibility

> **Applies to**: TeXTech **v1.0.0** (first public release)  
> **Last updated**: 2026-06-25  
> 中文: [GTNH版本兼容说明.md](../../zh/developer/GTNH版本兼容说明.md)

This document is for **pack authors, server admins, and players**. It explains which GTNH pack versions are supported in v1.0.0, how dual-path AE integration works, and **when support for pre-2.9.0-beta-1 packs will be dropped**.

For implementation details see [ae-compat-290.md](ae-compat-290.md). For the developer removal checklist see [ae-compat-plan-e-remove-legacy.md](ae-compat-plan-e-remove-legacy.md).

---

## 1. v1.0.0 support matrix

| GTNH pack | Typical AE2 / AE2FC | Status | Notes |
|-----------|---------------------|--------|-------|
| **2.8.x** (incl. 2.8.4) | AE2 pre rv3-beta-900 · AE2FC **1.3.7-gtnh** | ✅ Supported | **Legacy** path (GlodBlock / AE2FC reflection) |
| **2.9.0-beta-1+** | AE2 rv3-beta-977-GTNH · AE2FC **1.5.88-gtnh** | ✅ Supported | **NativeFluid** path (AE2 native fluid API) |
| Below **2.9.0-beta-1** | — | ⚠️ **Unsupported from next release** | See §3 |

The mod picks the path automatically at startup via `AeCompat.init()`. Example log lines:

```
[ADM] AE compat profile=LEGACY (source=DEFAULT_LEGACY, detail=pre-2.9.0-default)
[ADM] AE compat profile=NATIVE (source=GTNH_VERSION_FILE, detail=2.9.0-beta-1)
```

### Manual override (advanced)

In `config/textech/textech.cfg`, section `[compat]`:

| Setting | Values | Description |
|---------|--------|-------------|
| `aeProfileOverride` | `auto` (default) | Auto-detect |
| | `legacy` | Force Legacy (debug 2.8.x behaviour) |
| | `native` | Force Native (requires 2.9.0+ AE2) |

---

## 2. What dual-path compatibility covers

These features work on **both 2.8.x and 2.9.0-beta+**, with different adapters underneath:

| Feature | Legacy (2.8.x) | Native (2.9.0+) |
|---------|----------------|-----------------|
| Network Link — byte/type stats | GlodBlock fluid handler reflection | `StorageChannel.FLUIDS` + `ICellInventory` |
| AI — `QUERY_BYTES`, infinite cell detection | Legacy cell stats adapter | Native cell stats adapter |
| AI — pattern fluid details | AE2FC reflection | AE2 official API when available |
| Data loom fluid cells — markers & Workbench | `DataLoomFluidCellConfig` | `NativeDataLoomFluidCellConfig` |
| Monitor / Storage Link — fluid stack stats | Same as above | Same as above |

**Not affected by AE compat** (work the same on all supported packs): grapple system, advance planner, voice assistant, Super Orange, Starry Cosmos sword, monitor charts/UI, etc.

---

## 3. Planned change: drop pre-2.9.0-beta-1 support

### Statement

- **v1.0.0**: still supports **GTNH 2.8.x** and **2.9.0-beta-1+** (dual path).
- **v1.1.0** (or the next minor release — see Release Notes): **minimum GTNH 2.9.0-beta-1**; Legacy / GlodBlock code and `[compat]` config will be removed.

### Rationale

1. GTNH 2.9.0 is the active line; native AE2 fluid APIs are the standard going forward.
2. Dual-path maintenance doubles test surface (reflection, GlodBlock coupling).
3. Build dependencies will move to AE2FC 1.5.88-gtnh / AE2 rv3-beta-977-GTNH and cannot share a classpath with the 2.8 dev line.

### Player impact

| Your pack | v1.0.0 | Next release (planned) |
|-----------|--------|-------------------------|
| GTNH 2.8.x | ✅ Works | ❌ Upgrade to 2.9.0-beta-1+ |
| GTNH 2.9.0-beta-1+ | ✅ Works | ✅ Still supported |

### Migration tips

- **Still on 2.8.x**: stay on **v1.0.0** until you upgrade the pack; no extra steps required beforehand.
- **Pack authors**: if your pack remains on 2.8.x, note in your changelog that TeXTech **≥1.1.0** requires GTNH 2.9.0-beta-1+, or pin the mod to `1.0.x`.
- **Developers**: follow [ae-compat-plan-e-remove-legacy.md](ae-compat-plan-e-remove-legacy.md); opening prompt: *「继续 GTNH 2.9 AE 兼容，执行 Plan E — 移除 Legacy 支持」*.

---

## 4. v1.0.0 known limits (not version-specific)

First release is intentionally incomplete:

- **Recipes**: many items/blocks have no GTNH crafting recipes yet; use NEI / creative.
- **Textures**: some items use procedural placeholder art — see [temporary-textures.md](temporary-textures.md).
- **Rendering**: Crafting Link / Storage Link TESRs may still use placeholder cubes (`USE_PLACEHOLDER_CUBE`) until OBJ models are wired in.

---

## 5. Related docs

| Doc | Purpose |
|-----|---------|
| [ae-compat-290.md](ae-compat-290.md) | Dual-path technical reference |
| [ae-compat-plan-e-remove-legacy.md](ae-compat-plan-e-remove-legacy.md) | Plan E removal steps |
| [temporary-textures.md](temporary-textures.md) | Placeholder texture audit |
| [technical-documentation.md](technical-documentation.md) | Overall mod architecture |
