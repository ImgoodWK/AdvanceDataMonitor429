# 世界地图 Bug 遗留项

> 最后更新：2026-07  
> 多源渲染主方案已合入；以下 2 项用户反馈 Bug **尚未修复**，作为后续迭代计划。

---

## Bug A：含 AE 方块的 chunk 不显示地形，仅显示 AE 图层

### 现象

- 有 AE 设备的 chunk：AE 着色/overlay 正常
- **地形层空白**（或仅条纹/低清占位），周围无 AE 的 chunk 地形可能正常

### 已尝试但未验证生效

| 改动 | 文件 | 可能仍不足 |
|------|------|-----------|
| `upgrading` 允许重试 | `useWorldMapTileLoader.ts` | HTTP 60s 缓存、retry 与 pump 竞态 |
| Dynmap/低清 fallback | `WorldMapTerrainFallback.java` | client GL 等待期间 terrain job 未完成 |
| 客户端 GL 全档位 | `WorldMapTileQueue.java` | 跨维度/GL 失败后回退不及时 |

### 待验证假设

1. `handleTile` terrain miss 调用 `enqueueChunkPair`，terrain 在 AE chunk 上被 HD 路径长期阻塞
2. `worldMapAeQualityBoost` 导致 terrain 缓存 tier 与浏览器请求的 medium 不一致 → 永久 miss
3. `upgrading` 占位掩盖失败，用户只见 AE 层

### 修复计划

| 步骤 | 内容 |
|------|------|
| A1 | 复现 + `WorldMapHandler` debug 日志 + Network/磁盘缓存对照 |
| A2 | terrain miss **仅** `enqueue(TERRAIN)`，不解耦 ae（`handleTile`） |
| A3 | AE chunk 地形 tier 与请求 quality / boost 对齐，或 lookup 尝试相邻 tier |
| A4 | upgrading 重试 `cache: 'no-store'` 或 `?_t=` 绕过缓存 |
| A5 | client GL 失败快速回退；dynmap crop 写入 medium 临时缓存 |

**验收**：含 AE chunk 地形 + AE 两层均可见；离线仍有 dynmap/UV 地形。

---

## Bug B：无 AE 的 chunk 仍持续闪烁「AE 更新中」角标

### 现象

- 无 AE 设备的 chunk：渲染完成后 AE 角标仍 loading/pending 闪烁

### 已尝试但未验证生效

| 改动 | 文件 | 可能仍不足 |
|------|------|-----------|
| 空 AE 返回 `empty` | `WorldMapHandler.java` | 仅直接 ae 请求路径；enqueueChunkPair 仍 markQueued |
| 合并 ultra AE 进度 | `WorldMapTileProgressTracker.java` | prefetch 写入不同 session；merge 未覆盖全部 chunk |
| 前端 `empty` → loaded | `useWorldMapTileLoader.ts` | prefetch 全 scope；服务端仍返回 pending |

### 待验证假设

1. `TopologyWorldMapView` **`showAe={true}` 硬编码**，与 overlay 开关无关
2. 空 AE chunk 经 `enqueueChunkPair` 重复 ae 入队
3. `WorldMapChunkStatusOverlay` 优先 `serverProgress` queued，忽略前端 ae tile 已 loaded

### 修复计划

| 步骤 | 内容 |
|------|------|
| B1 | `showAe={aeVisible}`；ae tile loaded（含 empty）时隐藏角标 |
| B2 | 空 AE chunk 禁止 ae 入队，仅 `markEmpty` + 可选 `writeEmpty` |
| B3 | progress API 双层状态与前端对齐（集成测试 merge） |
| B4 | AE prefetch 仅限含 `aePlacements` 的 chunk，或 `aeVisible` 时才 prefetch |
| B5 | 空 AE 瓦片 `WorldMapTileCache.writeEmpty` 持久化 |

**验收**：无 AE chunk 无 AE 角标；progress ae=`empty`；关闭 overlay 不 prefetch ae。

---

## 建议实施顺序

**优先 A2 + B1 + B2**（小改动、高概率同时缓解两 Bug）。

1. A1 复现与日志  
2. A2 + B2 解耦入队 / 禁止空 AE 入队  
3. B1 角标显示条件  
4. A3 tier 对齐  
5. B3–B5 progress / prefetch / empty 缓存  
6. A4–A5 upgrading 与 GL 回退  

---

## 手动测试清单

1. 采集 logical 快照 → self 模式 medium 打开世界地图  
2. **有 AE** chunk：Network 中 terrain URL 响应非永久 pending；磁盘 `map-tiles/flat/q*/terrain` 有 PNG  
3. **无 AE** chunk：ae URL 为 `empty`；角标消失  
4. 在线 + `when_online`：10s 内 terrain 从 upgrading → cached  
5. 切换 ultra：AE chunk 地形仍可见  

---

## 相关文件

- `webae/worldmap/WorldMapHandler.java`
- `webae/worldmap/WorldMapTileQueue.java`
- `webae/worldmap/WorldMapTileProgressTracker.java`
- `webae-frontend/src/hooks/useWorldMapTileLoader.ts`
- `webae-frontend/src/components/topology/WorldMapChunkStatusOverlay.tsx`
- `webae-frontend/src/components/topology/TopologyWorldMapView.tsx`
