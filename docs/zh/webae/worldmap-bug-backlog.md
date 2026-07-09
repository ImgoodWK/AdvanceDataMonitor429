# 世界地图 Bug 遗留项

> 最后更新：2026-07  
> **Bug A / Bug B 核心路径已于 2026-07 修复**（terrain/AE 入队解耦、tier 查找对齐、像素级 AE 透明度、设备级 marker 着色）。以下保留验证清单与后续可选增强。

---

## Bug A：含 AE 方块的 chunk 不显示地形，仅显示 AE 图层 — **已修复**

### 现象（修复前）

- 有 AE 设备的 chunk：AE 着色/overlay 正常
- **地形层空白**（或仅条纹/低清占位），周围无 AE 的 chunk 地形可能正常

### 已实施修复

| 改动 | 文件 |
|------|------|
| terrain cache miss **仅** `enqueue(TERRAIN)`，AE 独立入队 | `WorldMapHandler.java` |
| 含 AE chunk 的 terrain 查找同时尝试 requested tier 与 boost tier | `WorldMapHandler.findCachedTile` |
| `upgrading` 重试 `cache: 'no-store'` + `?_t=` 绕过 HTTP 缓存 | `useWorldMapTileLoader.ts` |

### 验收

含 AE chunk 地形 + AE 两层均可见；调 AE 透明度时地形保持不透明。

---

## Bug B：无 AE 的 chunk 仍持续闪烁「AE 更新中」角标 — **已修复**

### 现象（修复前）

- 无 AE 设备的 chunk：渲染完成后 AE 角标仍 loading/pending 闪烁

### 已实施修复

| 改动 | 文件 |
|------|------|
| `showAe={aeVisible}`，关闭 overlay 不显示 AE 角标 | `TopologyWorldMapView.tsx` |
| AE prefetch 仅在 `aeVisible` 时启用 | `TopologyWorldMapView.tsx` · `WorldMapAeOverlayStack.tsx` |
| 空 AE 瓦片 `tileStatus=empty` 不渲染 overlay `<img>` | `WorldMapAeOverlayLayer.tsx` |

---

## AE 透明度误作用于整块区域 — **已修复**

- **修复前**：CSS 容器 `opacity` 作用于整个 AE 层，含 AE chunk 在 Bug A 下观感像「地形变透明」
- **修复后**：`worldMapAeOverlayOpacity`（**0–1**）仅写入 AE 着色像素 alpha；地形/AE 层 `isolation: isolate`；AE 图块 `mix-blend-mode: normal`；re-tint 逐瓦片更新
- air-block AE 设备由整块填色改为 **dot marker**（与线缆一致），有实体 block 仍走顶面纹理 rasterize

---

## 瓦片 Z 轴拼接错位（规律乱序）— **已修复**

### 现象（修复前）

- 各 chunk 地形/AE 瓦片相对 marker 与相邻 chunk **南北颠倒或边界断裂**
- 调节 AE 透明度时，因层间错位看起来像「非 AE 区域地形变色」

### 已实施修复

| 改动 | 文件 |
|------|------|
| 布局框西北角锚定 + `scaleY(-1)`（`WORLD_MAP_TILE_FLIP_Y`，仅翻转南北，不镜像东西） | `worldMapTerrain.ts` · `useWorldMapTileLoader` |
| 坐标单元测试 | `worldMapTerrain.test.ts` |

Dynmap/JourneyMap 裁剪 PNG 同为北缘 row 0，无需在 `WorldMapDynmapChunkCropper` 额外翻转。

---

## 快照仅保留 current + previous — **已实施**

- `finalizeSnapshot` 写入 `previousVersion` 并删除更旧版本目录
- 瓦片 HTTP：`getTileWithFallback`；meta 暴露 `previousSnapshotVersion`
- 浏览器 IndexedDB / MC `map-cache` 同步清理；刷新快照期间上一版继续显示

---

## client_only AE 设备纹理 — **已增强**

- **修复前**：`WorldMapAeVectorOverlayRenderer` 对设备仅画圆点，不按 `pxPerBlock` 栅格化 block 顶面
- **修复后**：客户端读取方块并调用 `WorldMapFaceRasterizer.rasterizeTopFaceCategoryId`（与服务端 `WorldMapAeOverlayRenderer` 一致）；线缆/part 仍为矢量；仅 AE 像素非透明

---

## 手动测试清单

1. 采集 logical 快照 → self 模式 medium 打开世界地图  
2. **有 AE** chunk：terrain 与 AE 均可见；调 AE 透明度仅着色像素变化  
3. **无 AE** chunk：无 AE 着色与角标  
4. 关闭 AE overlay：不 prefetch AE  
5. 在线 + `when_online`：10s 内 terrain 从 upgrading → cached  

---

## 相关文件

- `webae/worldmap/WorldMapHandler.java`
- `webae/worldmap/WorldMapAeOverlayRenderer.java`
- `webae-frontend/src/hooks/useWorldMapTileLoader.ts`
- `webae-frontend/src/utils/worldMapAeTint.ts`
- `webae-frontend/src/components/topology/WorldMapAeOverlayLayer.tsx`
- `webae-frontend/src/utils/worldMapTerrain.ts`
- `webae-frontend/src/utils/worldMapTerrain.test.ts`
- `webae-frontend/src/components/topology/TopologyWorldMapView.tsx`
- `src/main/java/com/imgood/textech/client/worldmap/WorldMapAeVectorOverlayRenderer.java`
