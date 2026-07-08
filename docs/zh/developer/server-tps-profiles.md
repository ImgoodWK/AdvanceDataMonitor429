# TeXTech 服务器 TPS 推荐配置（三档）

适用于 `config/textech/textech.cfg` 中 `[webConsole]` 与 `[dataLoomCell]` 等分节。仅调整参数，不改变游戏逻辑。

## 小型服务器（≤5 人，无 Web 地图或偶发使用）

```properties
# [webConsole]
webWorldMapEnabled=false
webTopologyEnabled=true
webRefreshIntervalMs=5000
webWorldMapTileBudgetPerTick=1
webWorldMapRayBudgetPerTick=0
webWorldMapZoomBudgetPerTick=1
webWorldMapMaxQualityTier=medium
webWorldMapObliqueEngine=legacy
webWorldMapMaxChunks=128

# [dataLoomCell]
dataLoomCellSyncIntervalSeconds=15

# [assistant]
linkSearchRadius=16
```

## 中型服务器（5–15 人，Web 仪表盘 + 拓扑常用）

```properties
# [webConsole]
webWorldMapEnabled=true
webTopologyEnabled=true
webRefreshIntervalMs=2000
webWorldMapTileBudgetPerTick=2
webWorldMapRayBudgetPerTick=1
webWorldMapZoomBudgetPerTick=2
webWorldMapMaxQualityTier=high
webWorldMapObliqueEngine=ray
webWorldMapMaxChunks=256
webWorldMapClientHdEnabled=true

# [dataLoomCell]
dataLoomCellSyncIntervalSeconds=10

# [assistant]
linkSearchRadius=24
```

## 大型服务器（15+ 人，多用户 Web + 大型 AE 网络）

```properties
# [webConsole]
webWorldMapEnabled=true
webTopologyEnabled=true
webRefreshIntervalMs=3000
webWorldMapTileBudgetPerTick=1
webWorldMapRayBudgetPerTick=1
webWorldMapZoomBudgetPerTick=1
webWorldMapMaxQualityTier=high
webWorldMapDefaultQualityTier=medium
webWorldMapObliqueEngine=legacy
webWorldMapViewsEnabled=flat,oblique
webWorldMapMaxChunks=192

# [dataLoomCell]
dataLoomCellSyncIntervalSeconds=20

# [assistant]
linkSearchRadius=20
```

## 监视器与 StorageLink（游戏内配置）

- 数据监视器绑定 `interval` 建议 ≥20 tick（高频场景 ≥10）
- 无监视器绑定的 StorageLink 在代码优化后不再每 20 tick 全库存扫描；右键/GUI 仍可手动查询

## 性能验证

优化前后在同一存档、同一坐标对比 MSPT/TPS（`/forge tps` 或 Spark）。基准场景见 `tps-test-checklist.md`。
