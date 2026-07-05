import { Progress, Spin, Skeleton } from 'antd';
import type { CSSProperties, ReactNode } from 'react';
import type { DashboardSettings, DashboardWidgetConfig } from '@/utils/presets';
import { resolveProp, resolveAllColors, resolveChartStretchMode } from '@/utils/dashboardResolve';
import type { PowerSnapshot } from '@/utils/powerDataSources';
import { getPowerDataSourceValue } from '@/utils/powerDataSources';
import { ChartTrendSvg, type ChartTrendPoint } from '@/components/dashboard/ChartTrendSvg';
import { formatTime, formatLargeWithDelta, formatSignificant } from '@/utils/format';
import {
  isHistoryDataSource,
  isPowerHistoryDataSource,
} from '@/utils/dataSourceChartMap';

export interface PowerWidgetContentProps {
  widget: DashboardWidgetConfig;
  settings: DashboardSettings;
  snapshot: PowerSnapshot | null;
  t: (key: string, arg?: string | number) => string;
  fmtNum: (n: number) => string;
  dataSourceLabel: (ds: string) => string;
  /** Only true on first fetch with no cached data — trend area may show spinner. */
  initialLoading?: boolean;
  /** Primary network id for network-metric trend lookup (non-power data sources). */
  networkId?: number;
  /** Trend history lookup from useNetworkMetrics; used when the widget's data
   *  source is not backed by PowerSampler. */
  getHistory?: (networkId: number, dataSource: string) => ChartTrendPoint[];
}

function buildTrendPoints(
  values: number[],
  timestamps: number[]
): Array<{ value: number; ts?: number }> {
  return values.map((value, i) => ({
    value,
    ts: timestamps[i],
  }));
}

export function PowerWidgetContent({
  widget,
  settings,
  snapshot,
  t,
  fmtNum,
  dataSourceLabel,
  initialLoading = false,
  networkId,
  getHistory,
}: PowerWidgetContentProps) {
  const value = getPowerDataSourceValue(widget.dataSource, snapshot);
  const isPercent =
    widget.dataSource.includes('Percent') || widget.dataSource === 'euPercent';
  const colors = resolveAllColors(widget, settings);
  const chartSize = resolveProp(widget, settings, 'chartSize');
  const chartColor = colors.chartColor || 'var(--accent)';
  const steamColor = colors.chartColor || 'var(--steam)';

  const labelStyle: CSSProperties = {
    fontSize: Math.max(10, resolveProp(widget, settings, 'fontSize') - 2),
  };
  if (colors.titleColor) labelStyle.color = colors.titleColor;

  const valueStyle: CSSProperties = {
    fontSize: resolveProp(widget, settings, 'fontSize') + 6,
  };
  if (colors.chartColor) valueStyle.color = colors.chartColor;

  const wrap = (children: ReactNode) => (
    <div
      className="widget-align power-widget-inner"
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
    if (isPercent) return value.toFixed(1) + '%';
    if (widget.dataSource === 'euInRate' || widget.dataSource === 'euOutRate') {
      return fmtNum(value) + ' EU/t';
    }
    if (widget.dataSource === 'steamStored' && snapshot) {
      return `${fmtNum(snapshot.steamStored)} / ${fmtNum(snapshot.steamMax)}`;
    }
    if (widget.dataSource === 'steamPercent' && snapshot) {
      return `${fmtNum(snapshot.steamStored)} / ${fmtNum(snapshot.steamMax)}`;
    }
    return fmtNum(value);
  };

  const gaugePercent = (): number => {
    if (isPercent) return Math.min(100, value);
    if (widget.dataSource === 'euStored' && snapshot && snapshot.euMax > 0) {
      return Math.min(100, (snapshot.euStored / snapshot.euMax) * 100);
    }
    if (widget.dataSource === 'steamStored' && snapshot && snapshot.steamMax > 0) {
      return Math.min(100, (snapshot.steamStored / snapshot.steamMax) * 100);
    }
    return 0;
  };

  const chartStretch = resolveChartStretchMode(widget, settings, widget.type);

  if (initialLoading && !snapshot && (widget.type === 'statCard' || widget.type === 'lineChart')) {
    return wrap(
      <Skeleton active paragraph={{ rows: widget.type === 'statCard' ? 1 : 3 }} title={false} />
    );
  }

  switch (widget.type) {
    case 'statCard': {
      const showDelta = widget.showDelta ?? false;
      const sigDigits = widget.significantDigits ?? 5;
      let mainText = formatValue();
      let deltaEl: ReactNode = null;
      if (showDelta && getHistory && networkId != null) {
        const hist = getHistory(networkId, widget.dataSource);
        const prev = hist.length >= 2 ? hist[hist.length - 2]?.value : undefined;
        const formatted = formatLargeWithDelta(value, prev, 'ae');
        mainText = widget.significantDigits
          ? formatSignificant(value, 'ae', sigDigits)
          : formatted.main;
        if (formatted.delta) {
          const color =
            formatted.deltaPositive === true
              ? 'var(--success, #52c41a)'
              : formatted.deltaPositive === false
                ? 'var(--error, #ff4d4f)'
                : 'var(--text-dim)';
          deltaEl = (
            <div
              className="stat-card-delta"
              style={{
                fontSize: Math.max(9, resolveProp(widget, settings, 'fontSize') - 3),
                color,
              }}
            >
              {formatted.delta}
            </div>
          );
        }
      }
      return wrap(
        <>
          {labelText(label)}
          <div className="stat-card-value overview-stat-value" style={valueStyle}>
            {mainText}
          </div>
          {deltaEl}
        </>
      );
    }

    case 'progressBar': {
      const pct = Math.min(100, isPercent ? value : gaugePercent());
      const fillColor = colors.progressFillColor || (widget.dataSource.startsWith('steam') ? steamColor : chartColor);
      const trackColor = colors.progressTrackColor || undefined;
      return wrap(
        <>
          {labelText(label)}
          <div
            className="widget-chart-area widget-chart-area--sized overview-progress-area"
            style={{ flex: '0 0 auto', maxHeight: `${chartSize}%`, width: '100%' }}
          >
            <Progress
              percent={Math.round(pct)}
              type={widget.style === 'circular' ? 'circle' : 'line'}
              strokeColor={trackColor ? { color: fillColor, trailColor: trackColor } : fillColor}
              size="small"
              format={() => formatValue()}
              style={{ width: '100%', margin: 0 }}
            />
          </div>
        </>
      );
    }

    case 'gauge': {
      const pct = gaugePercent();
      const strokeColor = colors.gaugeStrokeColor || (widget.dataSource.startsWith('steam') ? steamColor : chartColor);
      const trackColor = colors.gaugeTrackColor || undefined;
      return wrap(
        <>
          {labelText(label)}
          <div className="widget-chart-area widget-chart-area--sized overview-progress-area" style={{ maxHeight: `${chartSize}%` }}>
            <Progress
              type="circle"
              percent={Math.round(pct)}
              strokeColor={trackColor ? { color: strokeColor, trailColor: trackColor } : strokeColor}
              size="small"
              format={() => (isPercent || pct > 0 ? pct.toFixed(0) + '%' : formatValue())}
            />
          </div>
        </>
      );
    }

    case 'lineChart': {
      const ds = widget.dataSource;
      const lineColor = colors.chartLineColor || chartColor;
      const areaColor = colors.chartAreaColor || (colors.chartColor ? `${colors.chartColor}33` : 'var(--accent-dim)');
      const steamLine = colors.chartSecondaryLineColor || steamColor;
      const steamArea = colors.chartSecondaryAreaColor || 'rgba(100,180,255,0.15)';

      // Non-power data sources: fall back to NetworkMetricSampler history.
      if (!isPowerHistoryDataSource(ds) && isHistoryDataSource(ds) && getHistory && networkId != null) {
        const points = getHistory(networkId, ds);
        if (points.length < 2) {
          return wrap(
            <>
              {labelText(label)}
              <div className="widget-chart-area widget-chart-area--sized power-trend-chart" style={{ height: `${chartSize}%`, minHeight: 120 }}>
                <span className="chart-no-data-watermark">{t('notEnoughData')}</span>
              </div>
            </>
          );
        }
        const isPct = ds.includes('Percent') || ds === 'cpuBusyRatio';
        return wrap(
          <>
            {labelText(label)}
            <div className="widget-chart-area widget-chart-area--sized power-trend-chart" style={{ height: `${chartSize}%`, minHeight: 120 }}>
              <ChartTrendSvg
                series={[
                  { id: ds, label, points, lineColor, areaColor },
                ]}
                formatValue={(v) => (isPct ? v.toFixed(1) + '%' : fmtNum(v))}
                formatTime={(ts) => formatTime(ts)}
                showValueAxis={settings.chartShowValueAxis}
                showTimeAxis={settings.chartShowTimeAxis}
                stretchMode={chartStretch}
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

      const euHist = snapshot?.euHistory || [];
      const steamHist = snapshot?.steamHistory || [];
      const euTs = snapshot?.euHistoryTimestamps || [];
      const steamTs = snapshot?.steamHistoryTimestamps || [];
      const hasEu = euHist.length >= 2;
      const hasSteam = steamHist.length >= 2;
      const hasChart = hasEu || hasSteam;

      const series = [];
      if (hasEu) {
        series.push({
          id: 'eu',
          label: t('euStored'),
          points: buildTrendPoints(euHist, euTs),
          lineColor,
          areaColor,
        });
      }
      if (hasSteam) {
        series.push({
          id: 'steam',
          label: t('steamStored'),
          points: buildTrendPoints(steamHist, steamTs),
          lineColor: steamLine,
          areaColor: steamArea,
          dashed: true,
        });
      }

      return wrap(
        <>
          {labelText(label)}
          <div
            className="widget-chart-area power-trend-chart power-chart-stable"
            style={{ height: `${chartSize}%`, minHeight: 120 }}
            aria-live="polite"
          >
            {initialLoading && !snapshot ? (
              <Spin size="small" />
            ) : hasChart ? (
              <ChartTrendSvg
                series={series}
                formatValue={(v) => fmtNum(v)}
                formatTime={(ts) => formatTime(ts)}
                showValueAxis={settings.chartShowValueAxis}
                showTimeAxis={settings.chartShowTimeAxis}
                stretchMode={chartStretch}
                colors={{
                  gridColor: colors.chartGridColor || 'var(--border-light)',
                  pointColor: colors.chartPointColor || lineColor,
                  axisTextColor: colors.axisTextColor || undefined,
                }}
              />
            ) : (
              <span className="chart-no-data-watermark">{t('notEnoughData')}</span>
            )}
          </div>
          {hasChart && (
            <div className="power-trend-legend" style={{ fontSize: '0.7rem', color: 'var(--text-dim)' }}>
              {hasEu && <span style={{ marginRight: 12, color: lineColor }}>{t('euStored')}</span>}
              {hasSteam && <span style={{ color: steamLine }}>{t('steamStored')}</span>}
            </div>
          )}
        </>
      );
    }

    default:
      return wrap(
        <div style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
          <span className="chart-no-data-watermark">{t('noData')}</span>
        </div>
      );
  }
}
