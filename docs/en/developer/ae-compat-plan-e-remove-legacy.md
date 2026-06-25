# Plan E — Remove Legacy AE Compatibility (GTNH 2.9.0+ only)

> **Purpose**: After GTNH 2.9.0 becomes the **minimum supported pack**, run this plan in a dedicated Agent session to remove the Legacy / GlodBlock dual-path code.  
> **Irreversible** cleanup — do not run until 2.8.x support is officially dropped.  
> **Session opener**: "Continue GTNH 2.9 AE compat — execute Plan E, remove Legacy support"

**Related**: [ae-compat-290.md](ae-compat-290.md) (current dual-path docs)

---

## Prerequisites

- [ ] Mod README states minimum GTNH **2.9.0 beta-1+**
- [ ] Native path validated on a real **2.9.0-beta** instance (NetworkLink stats, AI QUERY_BYTES/patterns, fluid loom cells)
- [ ] Dev jars available per `.workspace/ae2_290_api_diff.md` (AE2 rv3-beta-977, ae2fc 1.5.88)

---

## Steps (summary)

1. **Upgrade deps** — `dependencies.gradle`: ae2fc 1.5.88-gtnh + AE2 rv3-beta-977-GTNH; `compileJava`
2. **Delete** — entire `compat/ae/legacy/`; `GtnhEnvironmentProbe`, `GtnhVersion`, `AeCompatProfile`, `AeCompatDetectionSource`
3. **Simplify `AeCompat`** — single static binding to native implementations; remove profile probing
4. **Merge `native_/`** — rename/move to `compat/ae/*Impl`; inline any remaining Legacy fallbacks
5. **Data loom** — migrate `IDataLoomFluidCell` / fluid config off `com.glodblock.*`
6. **Config & docs** — remove `[compat] aeProfileOverride`, `ConfigCompatLoader`, update guides and `project-structure.mdc`
7. **(Optional)** — inline `AeCompat` facade into call sites

---

## Do not remove (unrelated "legacy")

- `TileEntityAdvanceStorageLink.LEGACY_MARKED_ITEMS_TAG` — NBT migration
- `AiClientPreferences` legacy config key migration

---

## Acceptance

- No `com.glodblock` imports under `src/`
- No `compat/ae/legacy/` or version probe classes
- `compileJava` OK on 2.9.0 AE stack
- In-game regression on 2.9.0-beta

Full step-by-step checklist (Chinese, authoritative): [docs/zh/developer/ae-compat-plan-e-remove-legacy.md](../../zh/developer/ae-compat-plan-e-remove-legacy.md)

---

## Todos

1. **pe-deps** — Upgrade dependencies and fix compile  
2. **pe-delete-legacy** — Delete legacy package and probe layer  
3. **pe-merge-native** — Promote native_ implementations  
4. **pe-dataloom** — Remove GlodBlock from loom cells  
5. **pe-docs** — Config, docs, Cursor rules  
