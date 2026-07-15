import { Tag } from 'antd';
import type { DashboardWidgetConfig } from '@/utils/presets';
import type { ChartStyleRecipe } from '@/theme/pageStyles';
import { CHART_STYLE_RECIPES } from '@/theme/pageStyles';

export interface RadarAxisConfig {
  dataSource: string;
  label?: string;
}

/** Pre-resolved axis values (e.g. from pins); when provided, overrides dataSource lookup. */
export interface RadarAxisValue {
  label: string;
  value: number;
}

interface RadarChartWidgetProps {
  widget: DashboardWidgetConfig;
  axes: RadarAxisConfig[];
  /** When set (e.g. from dashboard pins), used instead of getValue(dataSource). */
  axisValues?: RadarAxisValue[];
  chartSize: number;
  chartColor: string;
  radarAxisColor: string;
  getValue: (dataSource: string) => number;
  getLabel: (dataSource: string) => string;
  fmtNum: (v: number) => string;
  recipe?: ChartStyleRecipe;
}

const DEFAULT_RADAR_AXES: RadarAxisConfig[] = [
  { dataSource: 'itemCount' },
  { dataSource: 'fluidCount' },
  { dataSource: 'essentiaCount' },
  { dataSource: 'cpuBusyRatio' },
  { dataSource: 'bytesPercent' },
  { dataSource: 'gtActiveCount' },
];

export function resolveRadarAxes(widget: DashboardWidgetConfig): RadarAxisConfig[] {
  const axes = widget.radarAxes?.filter((a) => a.dataSource)?.slice(0, 8);
  if (axes && axes.length >= 3) return axes;
  return DEFAULT_RADAR_AXES;
}

export function RadarChartWidget({
  widget,
  axes,
  axisValues,
  chartSize,
  chartColor,
  radarAxisColor,
  getValue,
  getLabel,
  fmtNum,
  recipe = CHART_STYLE_RECIPES.classic,
}: RadarChartWidgetProps) {
  const fromPins = axisValues && axisValues.length >= 3 ? axisValues.slice(0, 8) : null;
  const resolved = axes.length >= 3 ? axes : DEFAULT_RADAR_AXES;
  const rawValues = fromPins
    ? fromPins.map((a) => Math.max(0, a.value))
    : resolved.map((a) => Math.max(0, getValue(a.dataSource)));
  const maxVal = Math.max(...rawValues, 1);
  const normalized = rawValues.map((v) => (v / maxVal) * 100);
  const labels = fromPins
    ? fromPins.map((a) => a.label)
    : resolved.map((a) => a.label?.trim() || getLabel(a.dataSource));

  const center = 50;
  const radius = 40;
  const n = normalized.length;

  const dataPoints = normalized.map((value, i) => {
    const angle = (i / n) * Math.PI * 2 - Math.PI / 2;
    const r = (value / 100) * radius;
    return `${center + Math.cos(angle) * r},${center + Math.sin(angle) * r}`;
  });

  const axisPoints = normalized.map((_, i) => {
    const angle = (i / n) * Math.PI * 2 - Math.PI / 2;
    return `${center + Math.cos(angle) * radius},${center + Math.sin(angle) * radius}`;
  });

  const opacityHex = Math.round(Math.max(0, Math.min(1, recipe.radarFillOpacity)) * 255)
    .toString(16)
    .padStart(2, '0');
  const fillColor =
    recipe.radarFillOpacity <= 0
      ? 'transparent'
      : chartColor
        ? `${chartColor}${opacityHex}`
        : 'var(--accent-dim)';

  return (
    <>
      <div className="widget-chart-area widget-chart-area--sized" style={{ height: `${chartSize}%` }}>
        <svg viewBox="0 0 100 100" preserveAspectRatio="xMidYMid meet" className="chart-svg">
          <polygon
            points={axisPoints.join(' ')}
            fill="none"
            stroke={radarAxisColor}
            strokeWidth={recipe.radarStrokeWidth * 0.5}
          />
          {recipe.radarRingScales.map((scale) => {
            const ring = normalized.map((_, i) => {
              const angle = (i / n) * Math.PI * 2 - Math.PI / 2;
              const r = radius * scale;
              return `${center + Math.cos(angle) * r},${center + Math.sin(angle) * r}`;
            });
            return (
              <polygon
                key={scale}
                points={ring.join(' ')}
                fill="none"
                stroke={radarAxisColor}
                strokeWidth={0.3}
                opacity={0.5}
              />
            );
          })}
          <polygon
            className="chart-radar-data"
            points={dataPoints.join(' ')}
            fill={fillColor}
            stroke={chartColor}
            strokeWidth={recipe.radarStrokeWidth}
          />
        </svg>
      </div>
      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 4 }}>
        {labels.map((label, i) => (
          <Tag key={`${widget.id}-radar-${i}`} style={{ fontSize: '0.65rem' }}>
            {label}: {fmtNum(rawValues[i])} ({normalized[i].toFixed(0)}%)
          </Tag>
        ))}
      </div>
    </>
  );
}
