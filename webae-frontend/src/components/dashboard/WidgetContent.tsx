import { Empty, Progress, Skeleton, Tag } from 'antd';
import type { CSSProperties, ReactNode } from 'react';
import type { DashboardSettings, DashboardWidgetConfig } from '@/utils/presets';
import {
  resolveProp,
  resolveAllColors,
  resolveChartStretchMode,
  resolveColorArray,
  resolveChartStyleRecipe,
} from '@/utils/dashboardResolve';
import { formatBytes, formatTime, formatLargeWithDelta, formatSignificant } from '@/utils/format';
import type {
  GtMachineDto,
  GtMachineListDto,
  PowerDto,
  StorageDto,
} from '@/types/dto';
import type { OverviewSnapshot } from '@/utils/overviewDataSources';
import {
  buildNetworkCompareMetrics,
  buildNetworkCompareRows,
  getGtMachinesForTable,
  getGtStatusBreakdown,
  getOverviewDataSourceValue,
  getStorageCategoryBreakdown,
  gtStatusTagColor,
  gtStatusLabel,
  type ChartCategory,
} from '@/utils/overviewDataSources';
import { ChartTrendSvg, type ChartTrendPoint } from '@/components/dashboard/ChartTrendSvg';
import { NetworkBalanceTable } from '@/components/dashboard/NetworkBalanceTable';
import {
  isHistoryDataSource,
  isPowerHistoryDataSource,
} from '@/utils/dataSourceChartMap';
import { useAppContext } from '@/context/AppContext';
import type { ChartStyleRecipe } from '@/theme/pageStyles';
import { CHART_STYLE_RECIPES } from '@/theme/pageStyles';
import { resolveColumns } from '@/utils/dashboardColumns';
import {
  ScalarWidgetRenderer,
  resolveProgressPercent,
} from '@/components/dashboard/ScalarWidgetRenderer';

export interface WidgetContentProps {
  widget: DashboardWidgetConfig;
  settings: DashboardSettings;
  snapshot: OverviewSnapshot | null;
  t: (key: string, arg?: string | number) => string;
  fmtNum: (n: number) => string;
  dataSourceLabel: (ds: string) => string;
  /** Primary network id for trend history lookup (useNetworkMetrics). */
  networkId?: number;
  /** Trend history lookup from useNetworkMetrics; required for lineChart widgets. */
  getHistory?: (networkId: number, dataSource: string) => ChartTrendPoint[];
  /** Optional GT snapshot for gtMachineList / machineByStatus widgets. */
  gt?: GtMachineListDto | null;
  /** Multi-network maps for networkCompare widget. */
  storageMap?: Record<number, StorageDto>;
  powerMap?: Record<number, PowerDto>;
  gtMap?: Record<number, GtMachineListDto>;
  selectedNetworks?: number[];
}

export function WidgetContent({
  widget,
  settings,
  snapshot,
  t,
  fmtNum,
  dataSourceLabel,
  networkId,
  getHistory,
  gt,
  storageMap,
  powerMap,
  gtMap,
  selectedNetworks,
}: WidgetContentProps) {
  const { pageStyle } = useAppContext();
  const chartRecipe = resolveChartStyleRecipe(widget, settings, pageStyle);
  const value = getOverviewDataSourceValue(widget.dataSource, snapshot);
  const isPercent =
    widget.dataSource.includes('Percent') ||
    widget.dataSource === 'cpuBusyRatio' ||
    widget.dataSource === 'totalCpuStoragePercent';
  const isBytes =
    widget.dataSource === 'bytesUsed' || widget.dataSource === 'bytesMax';
  const colors = resolveAllColors(widget, settings);
  const chartSize = resolveProp(widget, settings, 'chartSize');
  const chartColor = colors.chartColor || 'var(--accent)';

  const labelStyle: CSSProperties = {
    fontSize: Math.max(10, resolveProp(widget, settings, 'fontSize') - 2),
  };
  if (colors.titleColor) labelStyle.color = colors.titleColor;

  const wrap = (children: ReactNode) => (
    <div
      className="widget-align overview-widget-inner"
      data-align={resolveProp(widget, settings, 'alignment')}
      style={{
        fontSize: resolveProp(widget, settings, 'fontSize'),
        height: '100%',
        overflow: 'hidden',
      }}
    >
      {children}
    </div>
  );

  const label = widget.title ? t(widget.title) : dataSourceLabel(widget.dataSource);
  const labelText = (text: string) => (
    <div className="stat-card-label" style={labelStyle}>
      {text}
    </div>
  );

  const formatValue = (): string => {
    if (widget.dataSource === 'cpuActiveTotal') {
      const active = Math.floor(value / 10000);
      const total = value % 10000;
      return `${fmtNum(active)} / ${fmtNum(total)}`;
    }
    if (isBytes) return formatBytes(value);
    if (isPercent) return value.toFixed(1) + '%';
    return fmtNum(value);
  };

  const chartStretch = resolveChartStretchMode(widget, settings, widget.type);

  if (!snapshot && (widget.type === 'statCard' || widget.type === 'lineChart')) {
    return wrap(
      <Skeleton active paragraph={{ rows: widget.type === 'statCard' ? 1 : 3 }} title={false} />
    );
  }

  switch (widget.type) {
    case 'statCard': {
      const showDelta = widget.showDelta ?? false;
      const sigDigits = widget.significantDigits ?? 5;
      let mainText = formatValue();
      let delta: { text: string; color: string } | undefined;
      if (showDelta && getHistory && networkId !== undefined) {
        const hist = getHistory(networkId, widget.dataSource);
        const prev = hist.length >= 2 ? hist[hist.length - 2]?.value : undefined;
        const formatted = formatLargeWithDelta(value, prev, 'ae');
        mainText = widget.significantDigits ? formatSignificant(value, 'ae', sigDigits) : formatted.main;
        if (formatted.delta) {
          const color =
            formatted.deltaPositive === true
              ? 'var(--success, #52c41a)'
              : formatted.deltaPositive === false
                ? 'var(--error, #ff4d4f)'
                : 'var(--text-dim)';
          delta = { text: formatted.delta, color };
        }
      }
      return (
        <ScalarWidgetRenderer
          widget={widget}
          settings={settings}
          label={label}
          valueText={mainText}
          delta={delta}
        />
      );
    }

    case 'progressBar': {
      const realMaximum = widget.dataSource === 'bytesUsed' && snapshot && snapshot.bytesMax > 0
        ? snapshot.bytesMax
        : undefined;
      return (
        <ScalarWidgetRenderer
          widget={widget}
          settings={settings}
          label={label}
          valueText={formatValue()}
          progressPercent={resolveProgressPercent({
            value,
            percentMetric: isPercent,
            realMaximum,
            targetValue: widget.targetValue,
          })}
        />
      );
    }

    case 'gauge': {
      const realMaximum = widget.dataSource === 'bytesUsed' && snapshot && snapshot.bytesMax > 0
        ? snapshot.bytesMax
        : undefined;
      return (
        <ScalarWidgetRenderer
          widget={widget}
          settings={settings}
          label={label}
          valueText={formatValue()}
          progressPercent={resolveProgressPercent({
            value,
            percentMetric: isPercent,
            realMaximum,
            targetValue: widget.targetValue,
          })}
        />
      );
    }

    case 'lineChart': {
      const ds = widget.dataSource;
      const lineColor = colors.chartLineColor || chartColor;
      const areaColor = colors.chartAreaColor || (colors.chartColor ? `${colors.chartColor}33` : 'var(--accent-dim)');
      // Overview page currently has no power data sources, so we only handle
      // network-metric-backed history here. Power-history widgets live on the
      // power page (PowerWidgetContent).
      if (isHistoryDataSource(ds) && !isPowerHistoryDataSource(ds) && getHistory && networkId != null) {
        const points = getHistory(networkId, ds);
        if (points.length < 2) {
          return wrap(
            <>
              {labelText(label)}
              <div className="widget-chart-area widget-chart-area--sized" style={{ height: `${chartSize}%` }}>
                <span style={{ color: 'var(--text-dim)' }}>{t('notEnoughData')}</span>
              </div>
            </>
          );
        }
        const isPct = ds.includes('Percent') || ds === 'cpuBusyRatio';
        return wrap(
          <>
            {labelText(label)}
            <div className="widget-chart-area widget-chart-area--sized" style={{ height: `${chartSize}%`, minHeight: 80 }}>
              <ChartTrendSvg
                series={[
                  {
                    id: ds,
                    label,
                    points,
                    lineColor,
                    areaColor,
                  },
                ]}
                formatValue={(v) => (isPct ? v.toFixed(1) + '%' : fmtNum(v))}
                formatTime={(ts) => formatTime(ts)}
                showValueAxis={settings.chartShowValueAxis}
                showTimeAxis={settings.chartShowTimeAxis}
                stretchMode={resolveChartStretchMode(widget, settings, widget.type)}
                recipe={chartRecipe}
                colors={{
                  gridColor: colors.chartGridColor || 'var(--border-light)',
                  pointColor: colors.chartPointColor || lineColor,
                  axisTextColor: colors.axisTextColor || undefined,
                }}
              />
            </div>
          </>
        );
      }
      return wrap(
        <>
          {labelText(label)}
          <div className="widget-chart-area widget-chart-area--sized" style={{ height: `${chartSize}%` }}>
            <span style={{ color: 'var(--text-dim)' }}>{t('trendNotSupported')}</span>
          </div>
        </>
      );
    }

    case 'dataTable': {
      const rowAltBg = colors.dataTableRowAltColor || undefined;
      const cols = resolveColumns(widget);
      if (widget.dataSource === 'gtMachineList') {
        const machines = getGtMachinesForTable(gt, widget.maxRows || 10);
        if (machines.length === 0) {
          return wrap(
            <div style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
              <Empty description={t('noGTData')} image={Empty.PRESENTED_IMAGE_SIMPLE} />
            </div>
          );
        }
        return wrap(
          <>
            {labelText(label)}
            <div style={{ overflow: 'auto', flex: 1, width: '100%' }}>
              {machines.map((m, i) => renderGtMachineRow(m, i, rowAltBg, chartColor, fmtNum, t, cols))}
            </div>
          </>
        );
      }
      if (widget.dataSource === 'networkBalance' && selectedNetworks) {
        return wrap(
          <>
            {labelText(label)}
            <div style={{ overflow: 'auto', flex: 1, width: '100%' }}>
              <NetworkBalanceTable networkIds={selectedNetworks} compact visibleColumns={cols} />
            </div>
          </>
        );
      }
      return wrap(
        <div style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
          <Empty description={t('noData')} image={Empty.PRESENTED_IMAGE_SIMPLE} />
        </div>
      );
    }

    case 'barChart': {
      if (widget.dataSource === 'machineByStatus') {
        return renderCategoricalBarChart(
          getGtStatusBreakdown(gt?.machines, t),
          widget,
          settings,
          chartSize,
          chartColor,
          fmtNum,
          labelText,
          label,
          wrap,
          chartRecipe
        );
      }
      if (widget.dataSource === 'networkCompare' && storageMap && powerMap && gtMap && selectedNetworks) {
        return renderNetworkCompareChart(
          buildNetworkCompareRows(selectedNetworks, storageMap, powerMap, gtMap),
          widget,
          settings,
          chartSize,
          chartColor,
          t,
          fmtNum,
          labelText,
          label,
          wrap
        );
      }
      if (widget.dataSource === 'storageByCategory') {
        return renderCategoricalBarChart(
          getStorageCategoryBreakdown(snapshot, t),
          widget,
          settings,
          chartSize,
          chartColor,
          fmtNum,
          labelText,
          label,
          wrap,
          chartRecipe
        );
      }
      return wrap(
        <div style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
          <Empty description={t('noData')} image={Empty.PRESENTED_IMAGE_SIMPLE} />
        </div>
      );
    }

    case 'pieChart': {
      if (widget.dataSource === 'machineByStatus') {
        return renderCategoricalPieChart(
          getGtStatusBreakdown(gt?.machines, t),
          widget,
          settings,
          chartSize,
          chartColor,
          fmtNum,
          labelText,
          label,
          wrap,
          chartRecipe
        );
      }
      if (widget.dataSource === 'storageByCategory') {
        return renderCategoricalPieChart(
          getStorageCategoryBreakdown(snapshot, t),
          widget,
          settings,
          chartSize,
          chartColor,
          fmtNum,
          labelText,
          label,
          wrap,
          chartRecipe
        );
      }
      return wrap(
        <div style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
          <Empty description={t('noData')} image={Empty.PRESENTED_IMAGE_SIMPLE} />
        </div>
      );
    }

    default:
      return wrap(
        <div style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
          <Empty description={t('noData')} image={Empty.PRESENTED_IMAGE_SIMPLE} />
        </div>
      );
  }
}

function resolveCategoryColor(
  cat: ChartCategory,
  index: number,
  widget: DashboardWidgetConfig,
  settings: DashboardSettings,
  chartColor: string,
  paletteKey: 'barSegmentColors' | 'pieSliceColors'
): string {
  const colors = resolveAllColors(widget, settings);
  const palette = resolveColorArray(widget, settings, paletteKey);
  if (cat.colorKey === 'items') return colors.categoryItemsColor || palette[0] || chartColor;
  if (cat.colorKey === 'fluids') return colors.categoryFluidsColor || palette[1] || 'var(--success)';
  if (cat.colorKey === 'essentia') return colors.categoryEssentiaColor || palette[2] || 'var(--warning)';
  if (cat.colorKey === 'active') return 'var(--success, #52c41a)';
  if (cat.colorKey === 'error') return 'var(--error, #ff4d4f)';
  if (cat.colorKey === 'idle') return 'var(--text-dim)';
  if (palette.length > 0) return palette[index % palette.length];
  return [chartColor, 'var(--success)', 'var(--warning)'][index % 3];
}

function renderGtMachineRow(
  machine: GtMachineDto,
  index: number,
  rowAltBg: string | undefined,
  chartColor: string,
  fmtNum: (n: number) => string,
  t: (key: string) => string,
  cols: string[],
): ReactNode {
  return (
    <div
      key={`${machine.x}_${machine.y}_${machine.z}_${index}`}
      style={{
        fontSize: '0.7rem',
        margin: '4px 0',
        padding: '4px 6px',
        background: index % 2 === 1 ? (rowAltBg || 'var(--bg-hover)') : 'var(--bg-hover)',
        borderRadius: 4,
        border: '1px solid var(--border-light)',
      }}
    >
      {(cols.includes('recipe') || cols.includes('status')) && <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 4 }}>
        {cols.includes('recipe') && (
        <span
          style={{
            fontWeight: 600,
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            whiteSpace: 'nowrap',
            flex: 1,
          }}
        >
          {machine.recipeMapName || machine.machineMode || '-'}
        </span>
        )}
        {cols.includes('status') && (
        <Tag color={gtStatusTagColor(machine.statusText)} style={{ margin: 0, fontSize: '0.65rem' }}>
          {gtStatusLabel(machine.statusText, t)}
        </Tag>
        )}
      </div>
      }
      <div style={{ display: 'flex', gap: 8, marginTop: 2, color: 'var(--text-secondary)', flexWrap: 'wrap' }}>
        {cols.includes('coords') && (
        <code style={{ fontSize: '0.65rem' }}>
          {machine.x},{machine.y},{machine.z}
        </code>
        )}
        {cols.includes('progress') && machine.maxProgressTime > 0 && (
          <span>
            {Math.round(machine.progressPercent)}% ({machine.progressTime}/{machine.maxProgressTime}t)
          </span>
        )}
        {cols.includes('parallel') && machine.parallelCount > 1 && <Tag color="blue">×{machine.parallelCount}</Tag>}
      </div>
      {cols.includes('output') && machine.currentOutput && (
        <div style={{ marginTop: 2, color: chartColor, fontSize: '0.68rem' }}>{machine.currentOutput}</div>
      )}
    </div>
  );
}

export function renderCategoricalBarChart(
  categories: ChartCategory[],
  widget: DashboardWidgetConfig,
  settings: DashboardSettings,
  chartSize: number,
  chartColor: string,
  fmtNum: (n: number) => string,
  labelText: (text: string) => ReactNode,
  label: string,
  wrap: (children: ReactNode) => ReactNode,
  recipe: ChartStyleRecipe = CHART_STYLE_RECIPES.classic
): ReactNode {
  const maxVal = Math.max(...categories.map((c) => c.value), 1);
  const barPalette = resolveColorArray(widget, settings, 'barSegmentColors');
  const r = recipe.barTopRadius;
  return wrap(
    <>
      {labelText(label)}
      <div
        className="widget-chart-area widget-chart-area--sized"
        style={{ height: `${chartSize}%`, alignItems: 'flex-end', gap: 8, paddingTop: 8 }}
      >
        {categories.map((cat, i) => (
          <div
            key={cat.label}
            style={{
              flex: 1,
              textAlign: 'center',
              height: '100%',
              display: 'flex',
              flexDirection: 'column',
              justifyContent: 'flex-end',
            }}
          >
            <div
              className="chart-bar-segment"
              style={{
                height: `${(cat.value / maxVal) * 100}%`,
                background: resolveCategoryColor(cat, i, widget, settings, chartColor, 'barSegmentColors'),
                borderRadius: r > 0 ? `${r}px ${r}px 0 0` : 0,
                minHeight: 2,
                transition: 'height 0.3s',
              }}
            />
            <div style={{ fontSize: '0.65rem', color: 'var(--text-dim)', marginTop: 4 }}>{cat.label}</div>
            <div style={{ fontSize: '0.7rem' }}>{fmtNum(cat.value)}</div>
          </div>
        ))}
      </div>
    </>
  );
}

export function renderCategoricalPieChart(
  categories: ChartCategory[],
  widget: DashboardWidgetConfig,
  settings: DashboardSettings,
  chartSize: number,
  chartColor: string,
  fmtNum: (n: number) => string,
  labelText: (text: string) => ReactNode,
  label: string,
  wrap: (children: ReactNode) => ReactNode,
  recipe: ChartStyleRecipe = CHART_STYLE_RECIPES.classic
): ReactNode {
  const piePalette = resolveColorArray(widget, settings, 'pieSliceColors');
  const palette = categories.map((cat, i) =>
    resolveCategoryColor(cat, i, widget, settings, chartColor, 'pieSliceColors')
  );
  let offset = 0;
  const total = categories.reduce((s, c) => s + c.value, 0) || 1;
  const pr = recipe.pieRadius;
  const psw = recipe.pieStrokeWidth;
  return wrap(
    <>
      {labelText(label)}
      <div className="widget-chart-area widget-chart-area--sized" style={{ height: `${chartSize}%` }}>
        <svg viewBox="0 0 42 42" preserveAspectRatio="xMidYMid meet" className="chart-svg">
          <circle cx="21" cy="21" r={pr} fill="transparent" stroke="var(--bg-secondary)" strokeWidth={psw} />
          <g className="chart-pie-group">
            {categories.map((cat, i) => {
              const dash = (cat.value / total) * 100;
              const el = (
                <circle
                  key={cat.label}
                  cx="21"
                  cy="21"
                  r={pr}
                  fill="transparent"
                  stroke={palette[i % palette.length]}
                  strokeWidth={psw}
                  strokeDasharray={`${dash} ${100 - dash}`}
                  strokeDashoffset={-offset}
                />
              );
              offset += dash;
              return el;
            })}
          </g>
        </svg>
      </div>
      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
        {categories.map((cat, i) => (
          <Tag key={cat.label} color={palette[i % palette.length]} style={{ fontSize: '0.7rem' }}>
            {cat.label}: {fmtNum(cat.value)}
          </Tag>
        ))}
      </div>
    </>
  );
}

export function renderNetworkCompareChart(
  rows: ReturnType<typeof buildNetworkCompareRows>,
  widget: DashboardWidgetConfig,
  settings: DashboardSettings,
  chartSize: number,
  chartColor: string,
  t: (key: string) => string,
  fmtNum: (n: number) => string,
  labelText: (text: string) => ReactNode,
  label: string,
  wrap: (children: ReactNode) => ReactNode
): ReactNode {
  if (rows.length === 0) {
    return wrap(
      <div style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
        <Empty description={t('noData')} image={Empty.PRESENTED_IMAGE_SIMPLE} />
      </div>
    );
  }
  const metrics = buildNetworkCompareMetrics(rows, t, formatBytes, fmtNum);
  return wrap(
    <>
      {labelText(label)}
      <div style={{ overflow: 'auto', flex: 1, width: '100%', fontSize: '0.72rem' }}>
        {rows.map((row) => (
          <div
            key={row.networkId}
            style={{
              marginBottom: 8,
              padding: '4px 6px',
              borderRadius: 4,
              border: '1px solid var(--border-light)',
              background: 'var(--bg-hover)',
            }}
          >
            <div style={{ fontWeight: 600, marginBottom: 4 }}>
              {t('networkId')} {row.networkId}
            </div>
            {metrics.map((metric) => {
              const value = row[metric.key];
              const pct = metric.max > 0 ? (value / metric.max) * 100 : 0;
              return (
                <div key={metric.key} style={{ marginBottom: 4 }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', color: 'var(--text-dim)' }}>
                    <span>{metric.label}</span>
                    <span style={{ color: chartColor }}>{metric.format(value)}</span>
                  </div>
                  <Progress percent={Math.round(pct)} showInfo={false} size="small" strokeColor={chartColor} />
                </div>
              );
            })}
          </div>
        ))}
      </div>
    </>
  );
}
