import type { DashboardWidgetConfig } from '@/utils/presets';

/**
 * Data source category for the dashboard widget editor. Drives both the
 * "valid chart types" matrix (so users can't pick an unsupported combo) and
 * the trend-chart history lookup (so each scalar data source knows which
 * history array to feed to {@code ChartTrendSvg}).
 */
export type DataSourceCategory =
  | 'scalarCount'
  | 'scalarAmount'
  | 'percent'
  | 'rate'
  | 'timeseries'
  | 'list'
  | 'categorical'
  | 'multiAxis'
  | 'pinned'
  | 'layout'
  | 'alertsFeed'
  | 'craftingFeed';

/**
 * Which chart types make sense for each category. Used by
 * {@link getValidChartTypes} to filter the type dropdown in EditWidgetModal
 * and the data-source dropdown when a type is already chosen.
 */
export const VALID_CHART_TYPES_FOR_CATEGORY: Record<
  DataSourceCategory,
  DashboardWidgetConfig['type'][]
> = {
  scalarCount: ['statCard', 'progressBar', 'gauge', 'lineChart'],
  scalarAmount: ['statCard', 'progressBar', 'gauge', 'lineChart'],
  percent: ['statCard', 'progressBar', 'gauge', 'lineChart'],
  rate: ['statCard', 'lineChart'],
  timeseries: ['lineChart'],
  list: ['dataTable'],
  categorical: ['barChart', 'pieChart', 'radarChart'],
  multiAxis: ['lineChart', 'barChart'],
  pinned: ['statCard', 'progressBar', 'gauge', 'lineChart', 'barChart', 'pieChart', 'dataTable', 'radarChart'],
  layout: ['group', 'textNote', 'spacer'],
  alertsFeed: ['alertsSummary'],
  craftingFeed: ['craftingQueue'],
};

const DATA_SOURCE_CATEGORY_MAP: Record<string, DataSourceCategory> = {
  // Scalar counts (integer counts of distinct entries)
  itemCount: 'scalarCount',
  fluidCount: 'scalarCount',
  essentiaCount: 'scalarCount',
  activeCpu: 'scalarCount',
  busyCpu: 'scalarCount',
  gtMachineCount: 'scalarCount',
  gtActiveCount: 'scalarCount',
  playerOnlineCount: 'scalarCount',
  serverTps: 'scalarCount',
  // Scalar amounts (sums / stored quantities)
  bytesUsed: 'scalarAmount',
  bytesMax: 'scalarAmount',
  itemTotal: 'scalarAmount',
  fluidTotal: 'scalarAmount',
  euStored: 'scalarAmount',
  euMax: 'scalarAmount',
  steamStored: 'scalarAmount',
  serverMspt: 'scalarAmount',
  // Percent / ratio (0-100 or 0-1)
  bytesPercent: 'percent',
  euPercent: 'percent',
  cpuBusyRatio: 'percent',
  // Rates
  euInRate: 'rate',
  euOutRate: 'rate',
  // Pre-built time series
  powerHistory: 'timeseries',
  playerOnlineTrend: 'timeseries',
  // List / table data
  topItems: 'list',
  cpuList: 'list',
  gtMachineList: 'list',
  // Categorical breakdowns
  storageByCategory: 'categorical',
  machineByStatus: 'categorical',
  // Multi-axis comparison
  networkCompare: 'multiAxis',
  networkBalance: 'list',
  // Custom pin bundle (items/fluids/CPUs/…)
  customPins: 'pinned',
  // Layout / feed widgets (no AE scalar)
  none: 'layout',
  alertsActive: 'alertsFeed',
  craftingBusy: 'craftingFeed',
  networkHealth: 'layout',
  powerFlow: 'layout',
  storageMatrix: 'layout',
  machineFleet: 'layout',
  playerPresence: 'layout',
  activityStream: 'layout',
  serverVitals: 'layout',
};

/**
 * @returns the category for a data source, defaulting to {@code scalarCount}
 *          for unknown sources so they still render as a stat card.
 */
export function getCategory(dataSource: string): DataSourceCategory {
  return DATA_SOURCE_CATEGORY_MAP[dataSource] || 'scalarCount';
}

/**
 * @returns the list of chart types valid for the given data source. Used to
 *          filter the type dropdown in EditWidgetModal.
 */
export function getValidChartTypes(
  dataSource: string
): DashboardWidgetConfig['type'][] {
  return VALID_CHART_TYPES_FOR_CATEGORY[getCategory(dataSource)];
}

/**
 * @returns {@code true} if the data source has a server-side (or hook-side)
 *          history sequence that can feed a line/sparkline trend chart.
 *
 * History sources:
 * - {@code powerHistory} and the {@code eu*}/{@code steamStored} scalars →
 *   PowerSampler (power.euHistory)
 * - {@code playerOnlineTrend} / {@code playerOnlineCount} → usePlayers history
 * - All other scalar/percent/rate sources → useNetworkMetrics
 *   (NetworkMetricSampler)
 */
export function isHistoryDataSource(dataSource: string): boolean {
  const cat = getCategory(dataSource);
  return cat === 'scalarCount'
    || cat === 'scalarAmount'
    || cat === 'percent'
    || cat === 'rate'
    || cat === 'timeseries';
}

/**
 * @returns {@code true} if the data source's history comes from the power
 *          sampler (PowerDto.euHistory / steamHistory) rather than
 *          NetworkMetricSampler. Used by Dashboard to pick the right branch.
 */
export function isPowerHistoryDataSource(dataSource: string): boolean {
  return dataSource === 'powerHistory'
    || dataSource === 'euStored'
    || dataSource === 'euMax'
    || dataSource === 'euPercent'
    || dataSource === 'euInRate'
    || dataSource === 'euOutRate'
    || dataSource === 'steamStored';
}

/**
 * @returns {@code true} if the data source's history comes from the player
 *          online sampler (usePlayers). Used by Dashboard to pick the right
 *          branch.
 */
export function isPlayerHistoryDataSource(dataSource: string): boolean {
  return dataSource === 'playerOnlineTrend' || dataSource === 'playerOnlineCount';
}

/**
 * @returns {@code true} if history comes from GET /api/server/health.
 */
export function isServerHealthHistoryDataSource(dataSource: string): boolean {
  return dataSource === 'serverTps' || dataSource === 'serverMspt';
}
