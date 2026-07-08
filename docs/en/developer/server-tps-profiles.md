# TeXTech Server TPS Recommended Profiles (Three Tiers)

Applies to `[webConsole]` and `[dataLoomCell]` sections in `config/textech/textech.cfg`. Config-only; no logic changes.

## Small (≤5 players, Web map off or rare)

```properties
webWorldMapEnabled=false
webTopologyEnabled=true
webRefreshIntervalMs=5000
webWorldMapTileBudgetPerTick=1
webWorldMapRayBudgetPerTick=0
webWorldMapZoomBudgetPerTick=1
webWorldMapMaxQualityTier=medium
webWorldMapObliqueEngine=legacy
webWorldMapMaxChunks=128
dataLoomCellSyncIntervalSeconds=15
linkSearchRadius=16
```

## Medium (5–15 players, dashboard + topology active)

```properties
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
dataLoomCellSyncIntervalSeconds=10
linkSearchRadius=24
```

## Large (15+ players, multi-user Web + large AE)

```properties
webWorldMapEnabled=true
webTopologyEnabled=true
webRefreshIntervalMs=3000
webWorldMapTileBudgetPerTick=1
webWorldMapRayBudgetPerTick=1
webWorldMapZoomBudgetPerTick=1
webWorldMapMaxQualityTier=high
webWorldMapDefaultQualityTier=medium
webWorldMapObliqueEngine=legacy
webWorldMapMaxChunks=192
dataLoomCellSyncIntervalSeconds=20
linkSearchRadius=20
```

See `docs/zh/developer/tps-test-checklist.md` (Chinese) for the full regression checklist with **[OPT]** items.
