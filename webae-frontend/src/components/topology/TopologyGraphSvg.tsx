import { useCallback, useMemo, useRef, useState, type WheelEvent } from 'react';
import type { TopologyEdgeDto, TopologyNodeDto } from '@/types/dto';
import { Icon } from '@/components/Icon';

const CABLE_COLORS: Record<string, string> = {
  smart: '#5ec8f2',
  covered: '#3a6fd8',
  dense: '#6b3fa0',
};

const MIN_SCALE = 0.35;
const MAX_SCALE = 4;

interface ScreenPoint {
  x: number;
  y: number;
}

interface TopologyGraphSvgProps {
  nodes: TopologyNodeDto[];
  edges: TopologyEdgeDto[];
  mode: 'logical' | 'spatial';
  layout?: string;
  selectedNodeId: string | null;
  onNodeSelect: (node: TopologyNodeDto | null) => void;
  height?: number;
}

function normalizeLayout(nodes: TopologyNodeDto[], mode: 'logical' | 'spatial', layout?: string) {
  if (nodes.length === 0) {
    return { points: new Map<string, ScreenPoint>(), viewBox: '0 0 100 100' };
  }

  if (mode === 'logical' && layout === 'tree') {
    const points = new Map<string, ScreenPoint>();
    let maxX = 0;
    let maxY = 0;
    for (const node of nodes) {
      maxX = Math.max(maxX, node.layoutX);
      maxY = Math.max(maxY, node.layoutY);
    }
    const spanX = Math.max(1, maxX);
    const spanY = Math.max(1, maxY);
    for (const node of nodes) {
      points.set(node.id, {
        x: 6 + (node.layoutX / spanX) * 88,
        y: 6 + (node.layoutY / spanY) * 88,
      });
    }
    return { points, viewBox: '0 0 100 100' };
  }

  if (mode === 'logical') {
    const points = new Map<string, ScreenPoint>();
    const cx = 50;
    const cy = 50;
    const radius = 38;
    for (const node of nodes) {
      points.set(node.id, {
        x: cx + node.layoutX * radius,
        y: cy + node.layoutY * radius,
      });
    }
    return { points, viewBox: '0 0 100 100' };
  }

  let minX = Infinity;
  let maxX = -Infinity;
  let minY = Infinity;
  let maxY = -Infinity;
  for (const node of nodes) {
    minX = Math.min(minX, node.layoutX);
    maxX = Math.max(maxX, node.layoutX);
    minY = Math.min(minY, node.layoutY);
    maxY = Math.max(maxY, node.layoutY);
  }
  const pad = 2;
  const spanX = Math.max(1, maxX - minX);
  const spanY = Math.max(1, maxY - minY);
  const scale = 90 / Math.max(spanX, spanY);
  const points = new Map<string, ScreenPoint>();
  for (const node of nodes) {
    points.set(node.id, {
      x: 5 + (node.layoutX - minX + pad) * scale,
      y: 5 + (node.layoutY - minY + pad) * scale,
    });
  }
  return { points, viewBox: '0 0 100 100' };
}

export function TopologyGraphSvg({
  nodes,
  edges,
  mode,
  layout,
  selectedNodeId,
  onNodeSelect,
  height = 520,
}: TopologyGraphSvgProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const [pan, setPan] = useState({ x: 0, y: 0 });
  const [scale, setScale] = useState(1);
  const dragRef = useRef<{ active: boolean; startX: number; startY: number; panX: number; panY: number }>({
    active: false,
    startX: 0,
    startY: 0,
    panX: 0,
    panY: 0,
  });

  const { points, viewBox } = useMemo(
    () => normalizeLayout(nodes, mode, layout),
    [nodes, mode, layout]
  );

  const onWheel = useCallback((e: WheelEvent<HTMLDivElement>) => {
    e.preventDefault();
    const delta = e.deltaY > 0 ? 0.9 : 1.1;
    setScale((s) => Math.min(MAX_SCALE, Math.max(MIN_SCALE, s * delta)));
  }, []);

  const onPointerDown = useCallback((e: React.PointerEvent<HTMLDivElement>) => {
    if ((e.target as HTMLElement).closest('.topology-node-hit')) return;
    dragRef.current = {
      active: true,
      startX: e.clientX,
      startY: e.clientY,
      panX: pan.x,
      panY: pan.y,
    };
    e.currentTarget.setPointerCapture(e.pointerId);
  }, [pan.x, pan.y]);

  const onPointerMove = useCallback((e: React.PointerEvent<HTMLDivElement>) => {
    if (!dragRef.current.active) return;
    setPan({
      x: dragRef.current.panX + (e.clientX - dragRef.current.startX),
      y: dragRef.current.panY + (e.clientY - dragRef.current.startY),
    });
  }, []);

  const onPointerUp = useCallback((e: React.PointerEvent<HTMLDivElement>) => {
    dragRef.current.active = false;
    try {
      e.currentTarget.releasePointerCapture(e.pointerId);
    } catch {
      /* ignore */
    }
  }, []);

  const edgeMid = (from: ScreenPoint, to: ScreenPoint) => ({
    x: (from.x + to.x) / 2,
    y: (from.y + to.y) / 2,
  });

  return (
    <div
      ref={containerRef}
      className="topology-graph-host"
      style={{ height, touchAction: 'none' }}
      onWheel={onWheel}
      onPointerDown={onPointerDown}
      onPointerMove={onPointerMove}
      onPointerUp={onPointerUp}
      onPointerCancel={onPointerUp}
      role="img"
      aria-label="Network topology graph"
    >
      <svg
        className="topology-graph-svg"
        viewBox={viewBox}
        preserveAspectRatio="xMidYMid meet"
        style={{
          transform: `translate(${pan.x}px, ${pan.y}px) scale(${scale})`,
          transformOrigin: 'center center',
        }}
      >
        <defs>
          <filter id="topology-glow" x="-50%" y="-50%" width="200%" height="200%">
            <feGaussianBlur stdDeviation="0.6" result="blur" />
            <feMerge>
              <feMergeNode in="blur" />
              <feMergeNode in="SourceGraphic" />
            </feMerge>
          </filter>
        </defs>

        {edges.map((edge) => {
          const from = points.get(edge.from);
          const to = points.get(edge.to);
          if (!from || !to) return null;
          const color = CABLE_COLORS[edge.cableType || 'covered'] || CABLE_COLORS.covered;
          const ch = edge.channelsSimulated;
          const label =
            ch && ch.available ? `${ch.used}/${ch.max}` : '';
          const mid = edgeMid(from, to);
          return (
            <g key={`${edge.from}-${edge.to}`}>
              <line
                x1={from.x}
                y1={from.y}
                x2={to.x}
                y2={to.y}
                stroke={color}
                strokeWidth={edge.cableType === 'dense' ? 1.4 : edge.cableType === 'smart' ? 0.7 : 1}
                strokeOpacity={0.85}
                className="topology-edge-line"
              />
              {label && (
                <g transform={`translate(${mid.x}, ${mid.y})`}>
                  <rect
                    x={-6}
                    y={-3.2}
                    width={12}
                    height={6.4}
                    rx={1.2}
                    fill="var(--bg-primary, #0d1117)"
                    fillOpacity={0.82}
                  />
                  <text
                    textAnchor="middle"
                    dominantBaseline="middle"
                    fontSize={2.6}
                    fill="var(--text-secondary, #aaa)"
                    className="topology-channel-label"
                  >
                    {label}
                  </text>
                </g>
              )}
            </g>
          );
        })}

        {nodes.map((node) => {
          const pt = points.get(node.id);
          if (!pt) return null;
          const isHub = node.role === 'hub';
          const isSelected = selectedNodeId === node.id;
          const r = isHub ? 5.5 : 4.5;
          return (
            <g
              key={node.id}
              className="topology-node-hit"
              style={{ cursor: 'pointer' }}
              onClick={(e) => {
                e.stopPropagation();
                onNodeSelect(isSelected ? null : node);
              }}
              role="button"
              tabIndex={0}
              onKeyDown={(e) => {
                if (e.key === 'Enter' || e.key === ' ') {
                  e.preventDefault();
                  onNodeSelect(isSelected ? null : node);
                }
              }}
            >
              <circle
                cx={pt.x}
                cy={pt.y}
                r={r + 1.2}
                fill={isSelected ? 'var(--accent)' : 'var(--bg-secondary, #1a2332)'}
                fillOpacity={isSelected ? 0.35 : 0.9}
                stroke={isSelected ? 'var(--accent)' : 'var(--border, #334)'}
                strokeWidth={isHub ? 0.6 : 0.4}
                filter={isHub ? 'url(#topology-glow)' : undefined}
              />
              <foreignObject
                x={pt.x - r}
                y={pt.y - r}
                width={r * 2}
                height={r * 2}
                pointerEvents="none"
              >
                <div
                  style={{
                    width: '100%',
                    height: '100%',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                  }}
                >
                  {node.iconItemId ? (
                    <Icon id={node.iconItemId} size={Math.round(r * 3.2)} linkToWiki={false} />
                  ) : (
                    <span style={{ fontSize: 8, color: 'var(--text-dim)' }}>?</span>
                  )}
                </div>
              </foreignObject>
              <text
                x={pt.x}
                y={pt.y + r + 3.5}
                textAnchor="middle"
                fontSize={2.8}
                fill="var(--text-primary, #e6edf3)"
                className="topology-node-label"
              >
                {node.displayName}
              </text>
              <text
                x={pt.x}
                y={pt.y + r + 6.2}
                textAnchor="middle"
                fontSize={2.4}
                fill="var(--text-dim, #8b949e)"
              >
                ×{node.count}
              </text>
            </g>
          );
        })}
      </svg>
    </div>
  );
}
