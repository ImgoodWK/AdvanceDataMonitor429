import { useCallback, useId, useMemo, useRef, useState } from 'react';



export interface ChartTrendPoint {

  value: number;

  ts?: number;

  label?: string;

}



export interface ChartTrendSeries {

  id: string;

  label: string;

  points: ChartTrendPoint[];

  lineColor: string;

  areaColor?: string;

  dashed?: boolean;

}



export interface ChartTrendColors {

  gridColor?: string;

  pointColor?: string;

  /** 坐标轴文字色（空=继承 CSS 默认） */
  axisTextColor?: string;

}



interface ChartTrendSvgProps {

  series: ChartTrendSeries[];

  formatValue: (v: number, seriesId: string) => string;

  formatTime?: (ts: number) => string;

  colors?: ChartTrendColors;

  className?: string;

  /** 显示左侧 Y 轴数值刻度 */

  showValueAxis?: boolean;

  /** 显示底部时间戳刻度 */

  showTimeAxis?: boolean;

  /** fit=保持比例；stretchX=横向铺满；fill=横向铺满+撑满高度 */
  stretchMode?: 'fit' | 'stretchX' | 'fill';

}



interface HoverInfo {

  x: number;

  y: number;

  index: number;

  lines: Array<{ seriesId: string; label: string; valueText: string; timeText: string; color: string }>;

}



function normalizeSeries(
  points: ChartTrendPoint[],
  min: number,
  max: number
): Array<{ x: number; y: number; pt: ChartTrendPoint }> {
  if (points.length < 2) return [];
  const range = max === min ? 1 : max - min;
  return points.map((pt, i) => ({
    x: (i / (points.length - 1)) * 100,
    y: Math.max(0, Math.min(100, 100 - ((pt.value - min) / range) * 90)),
    pt,
  }));
}



function computeValueRange(series: ChartTrendSeries[]): { min: number; max: number } {

  const values = series.flatMap((s) => s.points.map((p) => p.value));

  if (values.length === 0) return { min: 0, max: 1 };

  const min = Math.min(...values);

  const max = Math.max(...values, 1);

  return { min, max: max === min ? max + 1 : max };

}



export function ChartTrendSvg({

  series,

  formatValue,

  formatTime,

  colors,

  className,

  showValueAxis = false,

  showTimeAxis = false,

  stretchMode = 'fit',

}: ChartTrendSvgProps) {

  const svgRef = useRef<SVGSVGElement>(null);

  const clipId = useId().replace(/:/g, '');

  const [hover, setHover] = useState<HoverInfo | null>(null);



  const filteredSeries = useMemo(

    () => series.filter((s) => s.points.length >= 2),

    [series]

  );

  const valueRange = useMemo(() => computeValueRange(filteredSeries), [filteredSeries]);

  const normalized = useMemo(

    () =>

      filteredSeries.map((s) => {

        const norm = normalizeSeries(s.points, valueRange.min, valueRange.max);

        return {

          ...s,

          norm,

          poly: norm.map((p) => `${p.x},${p.y}`).join(' '),

        };

      }),

    [filteredSeries, valueRange.max, valueRange.min]

  );



  const primaryLen = normalized[0]?.points.length ?? 0;

  const primarySeriesId = normalized[0]?.id ?? 'default';



  const yAxisLabels = useMemo(() => {

    if (!showValueAxis) return [];

    const { min, max } = valueRange;

    const mid = (min + max) / 2;

    return [

      { key: 'max', text: formatValue(max, primarySeriesId) },

      { key: 'mid', text: formatValue(mid, primarySeriesId) },

      { key: 'min', text: formatValue(min, primarySeriesId) },

    ];

  }, [formatValue, primarySeriesId, showValueAxis, valueRange]);



  const xAxisLabels = useMemo(() => {

    if (!showTimeAxis || !formatTime) return [];

    const points = normalized[0]?.points ?? [];

    if (points.length < 2) return [];

    const last = points.length - 1;

    const mid = Math.floor(last / 2);

    const entries: Array<{ key: string; text: string; align: 'left' | 'center' | 'right' }> = [];

    const firstTs = points[0]?.ts;

    const midTs = points[mid]?.ts;

    const lastTs = points[last]?.ts;

    if (firstTs != null) entries.push({ key: 'start', text: formatTime(firstTs), align: 'left' });

    if (midTs != null && mid !== 0 && mid !== last) {

      entries.push({ key: 'mid', text: formatTime(midTs), align: 'center' });

    }

    if (lastTs != null) entries.push({ key: 'end', text: formatTime(lastTs), align: 'right' });

    return entries;

  }, [formatTime, normalized, showTimeAxis]);



  const handleMouseMove = useCallback(

    (e: React.MouseEvent<SVGSVGElement>) => {

      if (!svgRef.current || primaryLen < 2 || normalized.length === 0) return;

      const rect = svgRef.current.getBoundingClientRect();

      const relX = ((e.clientX - rect.left) / rect.width) * 100;

      const index = Math.round((relX / 100) * (primaryLen - 1));

      const clamped = Math.max(0, Math.min(primaryLen - 1, index));



      const lines = normalized.map((s) => {

        const pt = s.points[clamped];

        const ts = pt?.ts;

        return {

          seriesId: s.id,

          label: s.label,

          valueText: pt ? formatValue(pt.value, s.id) : '—',

          timeText: ts != null && formatTime ? formatTime(ts) : '',

          color: s.lineColor,

        };

      });



      setHover({

        x: e.clientX - rect.left,

        y: e.clientY - rect.top,

        index: clamped,

        lines,

      });

    },

    [formatTime, formatValue, normalized, primaryLen]

  );



  if (normalized.length === 0) return null;



  const gridColor = colors?.gridColor || 'var(--border-light)';

  const pointColor = colors?.pointColor || normalized[0]?.lineColor || 'var(--accent)';

  const axisTextColor = colors?.axisTextColor || undefined;

  const hasAxes = showValueAxis || showTimeAxis;



  const preserveAspect = stretchMode === 'fit' ? 'xMidYMid meet' : 'none';

  const plotSvg = (

    <svg

      ref={svgRef}

      viewBox="0 0 100 100"

      preserveAspectRatio={preserveAspect}

      className={`chart-svg chart-trend-svg${stretchMode === 'fill' ? ' chart-trend-fill' : ''}`}

      onMouseMove={handleMouseMove}

      onMouseLeave={() => setHover(null)}

      role="img"

      aria-label="trend chart"

    >

      <defs>

        <clipPath id={clipId}>

          <rect x="0" y="0" width="100" height="100" />

        </clipPath>

      </defs>

      {[20, 40, 60, 80].map((y) => (

        <line

          key={`grid-${y}`}

          x1={0}

          y1={y}

          x2={100}

          y2={y}

          stroke={gridColor}

          strokeWidth={0.3}

          vectorEffect="non-scaling-stroke"

          opacity={0.5}

        />

      ))}

      <g clipPath={`url(#${clipId})`}>

        {normalized.map((s) => (

          <g key={s.id}>

            {s.areaColor && (

              <polygon

                className="chart-svg-area"

                points={`0,100 ${s.poly} 100,100`}

                fill={s.areaColor}

                opacity={0.3}

              />

            )}

            <polyline

              className="chart-svg-line chart-flow-line"

              points={s.poly}

              fill="none"

              stroke={s.lineColor}

              strokeWidth={1.5}

              vectorEffect="non-scaling-stroke"

              strokeDasharray={s.dashed ? '4 2' : undefined}

            />

          </g>

        ))}

      </g>

      {hover != null && stretchMode === 'fit' &&

        normalized.map((s) => {

          const n = s.norm[hover.index];

          if (!n) return null;

          return (

            <circle

              key={`pt-${s.id}`}

              cx={n.x}

              cy={n.y}

              r={1.8}

              fill={pointColor}

              stroke={s.lineColor}

              strokeWidth={0.6}

              vectorEffect="non-scaling-stroke"

            />

          );

        })}

      {hover != null && stretchMode === 'fit' && (

        <line

          x1={(hover.index / (primaryLen - 1)) * 100}

          y1={0}

          x2={(hover.index / (primaryLen - 1)) * 100}

          y2={100}

          stroke={gridColor}

          strokeWidth={0.4}

          vectorEffect="non-scaling-stroke"

          strokeDasharray="2 2"

          opacity={0.7}

        />

      )}

    </svg>

  );



  return (

    <div

      className={`chart-trend-wrap${hasAxes ? ' chart-trend-with-axes' : ''}${className ? ' ' + className : ''}`}

      style={{ position: 'relative', width: '100%', height: '100%' }}

    >

      <div className="chart-trend-layout">

        {showValueAxis && (

          <div className="chart-y-axis" aria-hidden>

            {yAxisLabels.map((label) => (

              <span key={label.key} className="chart-axis-label chart-y-axis-label" style={axisTextColor ? { color: axisTextColor } : undefined}>

                {label.text}

              </span>

            ))}

          </div>

        )}

        <div className="chart-plot-area" style={{ position: 'relative', minHeight: 0, minWidth: 0 }}>

          {plotSvg}

          {hover != null && stretchMode !== 'fit' && svgRef.current && (

            <div

              className="chart-trend-hover-overlay"

              aria-hidden

              style={{ position: 'absolute', inset: 0, pointerEvents: 'none', zIndex: 1 }}

            >

              <div

                className="chart-trend-hover-vline"

                style={{

                  position: 'absolute',

                  left: hover.x,

                  top: 0,

                  bottom: 0,

                  width: 1,

                  borderLeft: `1px dashed ${gridColor}`,

                  opacity: 0.7,

                }}

              />

              {normalized.map((s) => {

                const n = s.norm[hover.index];

                if (!n) return null;

                const rect = svgRef.current!.getBoundingClientRect();

                const left = (n.x / 100) * rect.width;

                const top = (n.y / 100) * rect.height;

                return (

                  <div

                    key={`hover-pt-${s.id}`}

                    style={{

                      position: 'absolute',

                      left: left - 4,

                      top: top - 4,

                      width: 8,

                      height: 8,

                      borderRadius: '50%',

                      background: pointColor,

                      border: `1.5px solid ${s.lineColor}`,

                      boxSizing: 'border-box',

                    }}

                  />

                );

              })}

            </div>

          )}

          {hover != null && (

            <div

              className="chart-trend-tooltip"

              style={{

                position: 'absolute',

                left: Math.min(hover.x + 8, (svgRef.current?.clientWidth ?? 200) - 160),

                top: Math.max(4, hover.y - 48),

                pointerEvents: 'none',

                zIndex: 2,

                background: 'var(--bg-elevated, var(--bg))',

                border: '1px solid var(--border)',

                borderRadius: 6,

                padding: '6px 10px',

                fontSize: '0.7rem',

                boxShadow: '0 2px 8px rgba(0,0,0,0.25)',

                maxWidth: 200,

              }}

            >

              {hover.lines.map((line) => (

                <div key={line.seriesId} style={{ marginBottom: 2 }}>

                  <span style={{ color: line.color, fontWeight: 600 }}>{line.label}: </span>

                  <span>{line.valueText}</span>

                  {line.timeText && (

                    <div style={{ color: 'var(--text-dim)', fontSize: '0.65rem' }}>{line.timeText}</div>

                  )}

                </div>

              ))}

            </div>

          )}

        </div>

        {showTimeAxis && xAxisLabels.length > 0 && (

          <div

            className="chart-x-axis"

            style={{ gridColumn: showValueAxis ? 2 : 1 }}

            aria-hidden

          >

            {xAxisLabels.map((label) => (

              <span

                key={label.key}

                className={`chart-axis-label chart-x-axis-label chart-x-axis-label-${label.align}`}

                style={axisTextColor ? { color: axisTextColor } : undefined}

              >

                {label.text}

              </span>

            ))}

          </div>

        )}

      </div>

    </div>

  );

}

