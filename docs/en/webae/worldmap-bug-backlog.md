# World Map Bug Backlog

> Last updated: 2026-07  
> **Bug A / Bug B core paths were fixed in 2026-07** (terrain/AE enqueue decoupling, tier lookup alignment, per-pixel AE alpha, device-level marker tint). Keep the verification checklist and optional follow-ups below.

See also the [Chinese version](../../zh/webae/worldmap-bug-backlog.md).

---

## Bug A: AE chunks show overlay only, no terrain — **Fixed**

### Symptom (before fix)

- Chunks with AE devices: AE tint/overlay OK
- **Terrain layer blank** (or stripes / low-res placeholders); non-AE neighbors may look fine

### Fixes shipped

| Change | File |
|--------|------|
| On terrain cache miss **only** `enqueue(TERRAIN)`; AE enqueued separately | `WorldMapHandler.java` |
| Terrain lookup for AE chunks tries requested tier and boost tier | `WorldMapHandler.findCachedTile` |
| `upgrading` retries use `cache: 'no-store'` + `?_t=` to bust HTTP cache | `useWorldMapTileLoader.ts` |

### Acceptance

AE chunks show both terrain and AE; adjusting AE opacity leaves terrain fully opaque.

---

## Bug B: AE badge keeps flashing on non-AE chunks — **Fixed**

### Symptom (before fix)

- Chunks without AE devices still flash AE corner badge (loading/pending) after render completes

### Fixes shipped

| Change | File |
|--------|------|
| `showAe={aeVisible}` so hiding overlay also hides AE badges | `TopologyWorldMapView.tsx` |
| AE prefetch only when `aeVisible` | `TopologyWorldMapView.tsx` · `WorldMapAeOverlayStack.tsx` |
| Empty AE tiles (`tileStatus=empty`) skip overlay `<img>` | `WorldMapAeOverlayLayer.tsx` |

---

## AE opacity wrongly applied to whole region — **Fixed**

- **Before**: CSS container `opacity` hit the whole AE layer; with Bug A this looked like “terrain went transparent”
- **After**: `worldMapAeOverlayOpacity` (**0–1**) writes only AE tint pixel alpha; terrain/AE layers use `isolation: isolate`; AE tiles `mix-blend-mode: normal`; re-tint updates per tile
- Air-block AE devices use **dot markers** (same as cables); solid blocks still rasterize top faces

---

## Tile Z-axis seam / shuffled rows — **Fixed**

### Symptom (before fix)

- Terrain/AE tiles flipped N/S vs markers or neighbors, or seams at chunk borders
- Changing AE opacity looked like non-AE terrain recolored because layers were misaligned

### Fixes shipped

| Change | File |
|--------|------|
| NW corner layout + `scaleY(-1)` (`WORLD_MAP_TILE_FLIP_Y`; flip N/S only, not E/W) | `worldMapTerrain.ts` · `useWorldMapTileLoader` |
| Coordinate unit tests | `worldMapTerrain.test.ts` |

Dynmap/JourneyMap crops already use north-edge row 0; no extra flip in `WorldMapDynmapChunkCropper`.

---

## Snapshots keep only current + previous — **Implemented**

- `finalizeSnapshot` writes `previousVersion` and deletes older version dirs
- Tile HTTP: `getTileWithFallback`; meta exposes `previousSnapshotVersion`
- Browser IndexedDB / MC `map-cache` pruned; previous version stays visible while a new snapshot refreshes

---

## client_only AE device textures — **Enhanced**

- **Before**: `WorldMapAeVectorOverlayRenderer` drew device dots only (no `pxPerBlock` top-face raster)
- **After**: Client reads blocks and calls `WorldMapFaceRasterizer.rasterizeTopFaceCategoryId` (same as server `WorldMapAeOverlayRenderer`); cables/parts stay vector; only non-transparent AE pixels

---

## Manual test checklist

1. Capture a logical snapshot → open world map in self mode at medium quality  
2. **AE** chunks: terrain + AE both visible; AE opacity changes tint pixels only  
3. **Non-AE** chunks: no AE tint or badge  
4. Hide AE overlay: no AE prefetch  
5. Online + `when_online`: within ~10s terrain goes upgrading → cached  

---

## Related files

- `webae/worldmap/WorldMapHandler.java`
- `webae/worldmap/WorldMapAeOverlayRenderer.java`
- `webae-frontend/src/hooks/useWorldMapTileLoader.ts`
- `webae-frontend/src/utils/worldMapAeTint.ts`
- `webae-frontend/src/components/topology/WorldMapAeOverlayLayer.tsx`
- `webae-frontend/src/utils/worldMapTerrain.ts`
- `webae-frontend/src/utils/worldMapTerrain.test.ts`
- `webae-frontend/src/components/topology/TopologyWorldMapView.tsx`
- `src/main/java/com/imgood/textech/client/worldmap/WorldMapAeVectorOverlayRenderer.java`
