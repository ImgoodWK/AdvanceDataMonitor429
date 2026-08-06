import { Progress, Skeleton } from 'antd';
import type { ReactNode } from 'react';
import type { DashboardSettings, DashboardWidgetConfig } from '@/utils/presets';
import { resolveAllColors, resolveProp } from '@/utils/dashboardResolve';

export interface ScalarWidgetRendererProps {
  widget: DashboardWidgetConfig;
  settings: DashboardSettings;
  label: string;
  valueText: string;
  /** Omit when an absolute metric has neither a real maximum nor an explicit target. */
  progressPercent?: number;
  icon?: ReactNode;
  delta?: { text: string; color: string };
  containerClassName?: string;
  overThreshold?: boolean;
  loading?: boolean;
}

export function clampProgressPercent(value: number | undefined): number | undefined {
  if (value === undefined || !Number.isFinite(value)) return undefined;
  return Math.max(0, Math.min(100, value));
}

export interface ProgressSemanticInput {
  value: number;
  /** True when value already has 0..100 semantics. */
  percentMetric?: boolean;
  /** Real provider/source maximum. Preferred over a user target when known. */
  realMaximum?: number;
  /** Explicit user target for otherwise unbounded absolute metrics. */
  targetValue?: number;
  /** Absolute numerator when value itself is a derived/placeholder percentage. */
  absoluteValue?: number;
  /** False suppresses both percentage and provider-maximum semantics. */
  capacityKnown?: boolean;
}

export function resolveProgressPercent({
  value,
  percentMetric = false,
  realMaximum,
  targetValue,
  absoluteValue,
  capacityKnown,
}: ProgressSemanticInput): number | undefined {
  const numerator = Number.isFinite(absoluteValue) ? absoluteValue as number : value;
  if (capacityKnown !== false && realMaximum !== undefined && Number.isFinite(realMaximum) && realMaximum > 0) {
    return clampProgressPercent((numerator / realMaximum) * 100);
  }
  if (capacityKnown !== false && percentMetric) {
    return clampProgressPercent(value);
  }
  if (targetValue !== undefined && Number.isFinite(targetValue) && targetValue > 0) {
    return clampProgressPercent((numerator / targetValue) * 100);
  }
  return undefined;
}

/** Shared stat/progress/gauge renderer used by dashboard, overview, and power grids. */
export function ScalarWidgetRenderer({
  widget,
  settings,
  label,
  valueText,
  progressPercent,
  icon,
  delta,
  containerClassName = 'overview-widget-inner',
  overThreshold = false,
  loading = false,
}: ScalarWidgetRendererProps) {
  const colors = resolveAllColors(widget, settings);
  const fontSize = resolveProp(widget, settings, 'fontSize');
  const chartSize = resolveProp(widget, settings, 'chartSize');
  const percent = clampProgressPercent(progressPercent);
  const scalarFallback = widget.type !== 'statCard' && percent === undefined;
  const className = `widget-align ${containerClassName}${overThreshold ? ' widget-alert-threshold' : ''}`;

  const labelNode = (
    <div
      className="stat-card-label"
      style={{ fontSize: Math.max(10, fontSize - 2), color: colors.titleColor || undefined }}
    >
      {label}
    </div>
  );
  const valueNode = (
    <div
      className="stat-card-value overview-stat-value"
      style={{ fontSize: fontSize + 6, color: colors.chartColor || undefined }}
    >
      {icon ? <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>{icon}{valueText}</span> : valueText}
    </div>
  );

  let content: ReactNode;
  if (loading) {
    content = <Skeleton active paragraph={{ rows: widget.type === 'statCard' ? 1 : 2 }} title={false} />;
  } else if (widget.type === 'statCard' || scalarFallback) {
    content = (
      <>
        {labelNode}
        {valueNode}
        {delta && (
          <div className="stat-card-delta" style={{ fontSize: Math.max(9, fontSize - 3), color: delta.color }}>
            {delta.text}
          </div>
        )}
      </>
    );
  } else if (widget.type === 'progressBar') {
    const fillColor = colors.progressFillColor || colors.chartColor || 'var(--accent)';
    const trackColor = colors.progressTrackColor || undefined;
    content = (
      <>
        {labelNode}
        <div
          className="widget-chart-area widget-chart-area--sized overview-progress-area"
          style={{ flex: '0 0 auto', height: `${chartSize}%`, maxHeight: `${chartSize}%`, width: '100%' }}
        >
          <Progress
            percent={percent}
            type={widget.style === 'circular' ? 'circle' : 'line'}
            strokeColor={trackColor ? { color: fillColor, trailColor: trackColor } : fillColor}
            size="small"
            format={() => valueText}
            style={{ width: '100%', margin: 0 }}
          />
        </div>
      </>
    );
  } else {
    const strokeColor = colors.gaugeStrokeColor || colors.chartColor || 'var(--accent)';
    const trackColor = colors.gaugeTrackColor || undefined;
    content = (
      <>
        {labelNode}
        <div
          className="widget-chart-area widget-chart-area--sized overview-progress-area"
          style={{ height: `${chartSize}%`, maxHeight: `${chartSize}%` }}
        >
          <Progress
            type="circle"
            percent={percent}
            strokeColor={trackColor ? { color: strokeColor, trailColor: trackColor } : strokeColor}
            size="small"
            format={() => valueText}
          />
        </div>
      </>
    );
  }

  return (
    <div
      className={className}
      data-align={resolveProp(widget, settings, 'alignment')}
      style={{ fontSize, height: '100%', overflow: 'hidden' }}
    >
      {content}
    </div>
  );
}
