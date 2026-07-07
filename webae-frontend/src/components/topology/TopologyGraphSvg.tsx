import { useCallback, useEffect, useImperativeHandle, useMemo, useRef, useState, forwardRef, type WheelEvent } from 'react';
import type { TopologyEdgeDto, TopologyNodeDto } from '@/types/dto';
import type { TopologyDisplaySettings } from '@/types/topologyDisplay';
import { Icon } from '@/components/Icon';
import {
  abbreviateLabel,
  avoidLabelOverlap,
  branchColorForIndex,
  computeDoubleRingPixelLayout,
  computeSpatialPixelLayout,
  computeTreePixelLayout,
  estimateLabelWidth,
  fitViewTransform,
  labelDetailLevel,
  TOPOLOGY_MAX_SCALE,
  TOPOLOGY_MIN_SCALE,
  type NodeRect,
  type ScreenPoint,
} from '@/utils/topologyLayout';
import { collapseCableEdges, visibleAbstractNodes } from '@/utils/topologyAbstractEdges';
import { topologyNodeLabel } from '@/utils/topologyDevices';
import { blockIconIdForNode } from '@/utils/aeCableColors';

import type { TopologyGraphHandle } from '@/components/topology/topologyGraphHandle';

/** @deprecated Use {@link TopologyCytoscapeGraph} — kept for rollback. */

interface TopologyGraphSvgProps {
  nodes: TopologyNodeDto[];
  edges: TopologyEdgeDto[];
  mode: 'logical' | 'spatial';
  layout?: string;
  displaySettings: TopologyDisplaySettings;
  selectedNodeId: string | null;
  hoveredNodeId?: string | null;
  onNodeSelect: (node: TopologyNodeDto | null) => void;
  onNodeHover?: (nodeId: string | null) => void;
  height?: number;
  layoutEpoch?: string;
}

function edgeMid(from: ScreenPoint, to: ScreenPoint) {
  return { x: (from.x + to.x) / 2, y: (from.y + to.y) / 2 };
}

export const TopologyGraphSvg = forwardRef<TopologyGraphHandle, TopologyGraphSvgProps>(function TopologyGraphSvg(
  {
    nodes,
    edges,
    mode,
    layout,
    displaySettings,
    selectedNodeId,
    hoveredNodeId,
    onNodeSelect,
    onNodeHover,
    height = 520,
    layoutEpoch = '',
  },
  ref
) {
  const containerRef = useRef<HTMLDivElement>(null);
  const [pan, setPan] = useState({ x: 0, y: 0 });
  const [scale, setScale] = useState(1);
  const dragRef = useRef({ active: false, startX: 0, startY: 0, panX: 0, panY: 0 });
  const lastFitEpochRef = useRef('');

  const visibleNodes = useMemo(() => visibleAbstractNodes(nodes), [nodes]);
  const visibleNodeIds = useMemo(() => new Set(visibleNodes.map((n) => n.id)), [visibleNodes]);
  const displayEdges = useMemo(() => collapseCableEdges(nodes, edges), [nodes, edges]);

  const abstractLayout =
    mode === 'logical' ? displaySettings.abstractLayout ?? (layout === 'star' ? 'star' : 'tree') : 'tree';

  const pixelLayout = useMemo(() => {
    if (mode === 'spatial') {
      return { ...computeSpatialPixelLayout(nodes, displaySettings), branchColors: new Map<string, string>() };
    }
    if (mode === 'logical' && abstractLayout === 'star') {
      return computeDoubleRingPixelLayout(nodes, displaySettings);
    }
    return { ...computeTreePixelLayout(nodes, displaySettings), branchColors: new Map<string, string>() };
  }, [nodes, mode, abstractLayout, displaySettings]);

  const { points, viewBox, width, height: layoutHeight, branchColors } = pixelLayout;
  const detail = labelDetailLevel(scale);
  const colors = displaySettings.colors;
  const r = displaySettings.nodeRadius;

  const fitView = useCallback(() => {
    const el = containerRef.current;
    if (!el) return;
    const t = fitViewTransform(width, layoutHeight, el.clientWidth, el.clientHeight);
    setScale(t.scale);
    setPan({ x: t.panX, y: t.panY });
  }, [width, layoutHeight]);

  const resetView = useCallback(() => {
    setScale(1);
    setPan({ x: 0, y: 0 });
  }, []);

  useImperativeHandle(ref, () => ({
    resetView,
    fitView,
    zoomIn: () => setScale((s) => Math.min(TOPOLOGY_MAX_SCALE, s * 1.15)),
    zoomOut: () => setScale((s) => Math.max(TOPOLOGY_MIN_SCALE, s / 1.15)),
  }));

  useEffect(() => {
    if (!layoutEpoch || layoutEpoch === lastFitEpochRef.current) return;
    lastFitEpochRef.current = layoutEpoch;
    fitView();
  }, [layoutEpoch, fitView]);

  const onWheel = useCallback((e: WheelEvent<HTMLDivElement>) => {
    e.preventDefault();
    const delta = e.deltaY > 0 ? 0.9 : 1.1;
    setScale((s) => Math.min(TOPOLOGY_MAX_SCALE, Math.max(TOPOLOGY_MIN_SCALE, s * delta)));
  }, []);

  const onPointerDown = useCallback(
    (e: React.PointerEvent<HTMLDivElement>) => {
      if ((e.target as HTMLElement).closest('.topology-node-hit')) return;
      dragRef.current = { active: true, startX: e.clientX, startY: e.clientY, panX: pan.x, panY: pan.y };
      e.currentTarget.setPointerCapture(e.pointerId);
    },
    [pan.x, pan.y]
  );

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

  const edgeColor = (edge: TopologyEdgeDto) => {
    if (edge.branchIndex != null && edge.branchIndex >= 0) {
      return branchColorForIndex(edge.branchIndex);
    }
    const cableKey = edge.cableType || 'covered';
    return colors[cableKey as keyof typeof colors] || colors.covered;
  };

  const nodeRects: NodeRect[] = useMemo(
    () =>
      visibleNodes
        .filter((n) => points.has(n.id))
        .map((n) => {
          const pt = points.get(n.id)!;
          const isHub = n.role === 'hub';
          const nr = isHub ? r + 2 : r;
          return { id: n.id, cx: pt.x, cy: pt.y, r: nr, label: topologyNodeLabel(n) } as NodeRect;
        }),
    [visibleNodes, points, r]
  );

  const renderLabel = (node: TopologyNodeDto, pt: ScreenPoint, isSelected: boolean, isHovered: boolean) => {
    const forceFull = isSelected || isHovered || displaySettings.labelStrategy === 'hover';
    if (detail === 'icon' && !forceFull && displaySettings.labelStrategy !== 'external') return null;

    let text = topologyNodeLabel(node);
    if (!forceFull && detail === 'abbrev') text = abbreviateLabel(text);
    const countSuffix = displaySettings.showCountLabels && node.count > 1 ? ` ×${node.count}` : '';
    const fullText = text + countSuffix;
    const labelW = estimateLabelWidth(fullText);

    const external = displaySettings.labelStrategy === 'external';
    const lr = displaySettings.layoutDirection === 'LR';
    const isHub = node.role === 'hub';
    const nodeR = isHub ? r + 2 : r;

    let labelX = external && lr ? pt.x + nodeR + displaySettings.labelMargin : pt.x;
    let labelY =
      external && lr
        ? pt.y + 4
        : external
          ? pt.y + nodeR + displaySettings.labelMargin
          : pt.y + nodeR + displaySettings.labelMargin;
    const anchor = external && lr ? 'start' : 'middle';

    if (detail === 'icon' && !forceFull) return null;

    // Apply label overlap avoidance (only for non-hover, non-selected external labels in LR mode)
    if (anchor === 'start' && !isSelected && !isHovered) {
      const adjusted = avoidLabelOverlap(labelX, labelY, labelW, node.id, nodeRects);
      labelX = adjusted.x;
      labelY = adjusted.y;
    }

    return (
      <g className="topology-node-label-group">
        <text
          x={labelX}
          y={labelY}
          textAnchor={anchor}
          fontSize={12}
          fill={colors.label}
          className="topology-node-label"
        >
          {text}
          {countSuffix}
        </text>
      </g>
    );
  };

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
            <feGaussianBlur stdDeviation="1.2" result="blur" />
            <feMerge>
              <feMergeNode in="blur" />
              <feMergeNode in="SourceGraphic" />
            </feMerge>
          </filter>
        </defs>

        {displayEdges.map((edge) => {
          const from = points.get(edge.from);
          const to = points.get(edge.to);
          if (!from || !to) return null;
          const isEmpty = edge.emptyBranch;
          if (!isEmpty && (!visibleNodeIds.has(edge.from) || !visibleNodeIds.has(edge.to))) return null;
          const color = edgeColor(edge);
          const ch = edge.channelsSimulated;
          const label = displaySettings.showEdgeChannelLabels && ch?.available ? `${ch.used}/${ch.max}` : '';
          const mid = edgeMid(from, to);
          return (
            <g key={`${edge.from}-${edge.to}`}>
              <line
                x1={from.x}
                y1={from.y}
                x2={to.x}
                y2={to.y}
                stroke={color}
                strokeWidth={edge.cableType === 'dense' ? 4 : edge.cableType === 'smart' ? 2.5 : 3}
                strokeOpacity={isEmpty ? 0.45 : 0.85}
                strokeDasharray={isEmpty ? '6 4' : undefined}
                className="topology-edge-line"
              />
              {label && !isEmpty && (
                <g transform={`translate(${mid.x}, ${mid.y})`}>
                  <rect x={-18} y={-9} width={36} height={18} rx={4} fill="var(--bg-primary, #0d1117)" fillOpacity={0.88} />
                  <text textAnchor="middle" dominantBaseline="middle" fontSize={10} fill={colors.labelDim} className="topology-channel-label">
                    {label}
                  </text>
                </g>
              )}
            </g>
          );
        })}

        {visibleNodes.map((node) => {
          const pt = points.get(node.id);
          if (!pt) return null;
          const isHub = node.role === 'hub';
          const isSelected = selectedNodeId === node.id;
          const isHovered = hoveredNodeId === node.id;
          const nodeR = isHub ? r + 2 : r;
          const strokeColor =
            isSelected || isHovered
              ? 'var(--accent)'
              : branchColors.get(node.id) ?? (node.branchIndex != null && node.branchIndex >= 0 ? branchColorForIndex(node.branchIndex) : colors.nodeStroke);
          return (
            <g
              key={node.id}
              className={`topology-node-hit${isHovered ? ' topology-node-hit--hover' : ''}`}
              style={{ cursor: 'pointer' }}
              onClick={(e) => {
                e.stopPropagation();
                onNodeSelect(isSelected ? null : node);
              }}
              onMouseEnter={() => onNodeHover?.(node.id)}
              onMouseLeave={() => onNodeHover?.(null)}
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
                r={nodeR + 3}
                fill={isSelected ? 'var(--accent)' : colors.nodeFill}
                fillOpacity={isSelected ? 0.35 : isHovered ? 0.98 : 0.92}
                stroke={strokeColor}
                strokeWidth={isHub ? 2 : isHovered ? 2 : 1.2}
                filter={isHub ? 'url(#topology-glow)' : undefined}
              />
              <foreignObject x={pt.x - nodeR} y={pt.y - nodeR} width={nodeR * 2} height={nodeR * 2} pointerEvents="none">
                <div style={{ width: '100%', height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center', position: 'relative' }}>
                  {node.iconItemId || node.type ? (
                    <Icon id={blockIconIdForNode(node.type, node.iconItemId)} size={Math.round(nodeR * 1.6)} linkToWiki={false} />
                  ) : (
                    <span style={{ fontSize: 10, color: colors.labelDim }}>?</span>
                  )}
                  {(node.patternCount ?? 0) > 0 && (
                    <span className="topology-pattern-badge">{node.patternCount}</span>
                  )}
                </div>
              </foreignObject>
              {renderLabel(node, pt, isSelected, isHovered)}
            </g>
          );
        })}
      </svg>
    </div>
  );
});
