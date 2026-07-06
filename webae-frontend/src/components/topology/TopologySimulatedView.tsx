import {
  useCallback,
  useEffect,
  useImperativeHandle,
  useMemo,
  useRef,
  useState,
  forwardRef,
  type WheelEvent,
} from 'react';
import type { TopologyEdgeDto, TopologyNodeDto } from '@/types/dto';
import type { TopologyDisplaySettings } from '@/types/topologyDisplay';
import { SimTextureImage } from '@/components/topology/SimTextureImage';
import { buildCableCells, simulatedViewBox } from '@/utils/topologyCablePath';
import { fitViewTransform, TOPOLOGY_MAX_SCALE, TOPOLOGY_MIN_SCALE } from '@/utils/topologyLayout';
import { isCellNode, isCableNode } from '@/utils/topologyDevices';
import { aeCableIconId, blockIconIdForNode } from '@/utils/aeCableColors';
import type { TopologyGraphHandle } from '@/components/topology/TopologyGraphSvg';

interface TopologySimulatedViewProps {
  nodes: TopologyNodeDto[];
  edges: TopologyEdgeDto[];
  displaySettings: TopologyDisplaySettings;
  selectedNodeId: string | null;
  onNodeSelect: (node: TopologyNodeDto | null) => void;
  onDriveClick?: (node: TopologyNodeDto) => void;
  height?: number;
  /** Changes when snapshot data changes — triggers fit-to-view once. */
  layoutEpoch?: string;
}

export const TopologySimulatedView = forwardRef<TopologyGraphHandle, TopologySimulatedViewProps>(
  function TopologySimulatedView(
    {
      nodes,
      edges,
      displaySettings,
      selectedNodeId,
      onNodeSelect,
      onDriveClick,
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

    const cellPx = displaySettings.cableCellPx;
    const blockPx = displaySettings.nodeBlockPx;

    const blockNodes = useMemo(
      () =>
        nodes.filter((n) => {
          if (isCellNode(n)) return false;
          if (n.simKind === 'hidden') return false;
          if (displaySettings.hideCableNodes && isCableNode(n)) return false;
          if (n.simKind?.startsWith('cable')) return false;
          if (n.simKind === 'junction') return false;
          return n.simGridX != null;
        }),
      [nodes, displaySettings.hideCableNodes]
    );

    const cableCells = useMemo(
      () => buildCableCells(nodes, edges, displaySettings.hideCableNodes),
      [nodes, edges, displaySettings.hideCableNodes]
    );

    const viewBox = useMemo(
      () => simulatedViewBox(blockNodes, cableCells, cellPx, blockPx),
      [blockNodes, cableCells, cellPx, blockPx]
    );

    const vbParts = viewBox.split(' ').map(Number);
    const layoutW = vbParts[2] ?? 400;
    const layoutH = vbParts[3] ?? 300;

    const fitView = useCallback(() => {
      const el = containerRef.current;
      if (!el) return;
      const t = fitViewTransform(layoutW, layoutH, el.clientWidth, el.clientHeight);
      setScale(t.scale);
      setPan({ x: t.panX, y: t.panY });
    }, [layoutW, layoutH]);

    useImperativeHandle(ref, () => ({
      resetView: () => {
        setScale(1);
        setPan({ x: 0, y: 0 });
      },
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
        if ((e.target as HTMLElement).closest('.topology-sim-block')) return;
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

    const cablePreset = displaySettings.cableColorPreset;

    return (
      <div
        ref={containerRef}
        className="topology-graph-host topology-simulated-host"
        style={{ height, touchAction: 'none' }}
        onWheel={onWheel}
        onPointerDown={onPointerDown}
        onPointerMove={onPointerMove}
        onPointerUp={onPointerUp}
        onPointerCancel={onPointerUp}
        role="img"
        aria-label="Simulated cable topology"
      >
        <svg
          className="topology-graph-svg topology-simulated-svg"
          viewBox={viewBox}
          preserveAspectRatio="xMidYMid meet"
          style={{
            transform: `translate(${pan.x}px, ${pan.y}px) scale(${scale})`,
            transformOrigin: 'center center',
          }}
        >
          {/* Cable cells — one MC block per grid cell with AE texture */}
          {cableCells.map((cell) => {
            const tier = cell.cableType === 'dense' ? 'dense' : cell.cableType === 'smart' ? 'smart' : 'covered';
            const colorId = cablePreset[tier];
            const iconId = aeCableIconId(tier, colorId);
            return (
              <SimTextureImage
                key={`cable-${cell.gx}-${cell.gy}`}
                iconId={iconId}
                x={cell.gx * cellPx}
                y={cell.gy * cellPx}
                size={cellPx}
                fallbackColor={displaySettings.colors[tier]}
                renderMode="block"
              />
            );
          })}

          {/* Junction / cable nodes rendered as cable textures */}
          {nodes
            .filter((n) => n.simKind === 'junction' || n.simKind?.startsWith('cable'))
            .map((node) => {
              if (node.simGridX == null || node.simGridY == null) return null;
              const tier =
                node.type === 'cable_dense' ? 'dense' : node.type === 'cable_smart' ? 'smart' : 'covered';
              const iconId = aeCableIconId(tier, cablePreset[tier]);
              return (
                <SimTextureImage
                  key={`junction-${node.id}`}
                  iconId={iconId}
                  x={node.simGridX * cellPx}
                  y={node.simGridY * cellPx}
                  size={cellPx}
                  fallbackColor={displaySettings.colors[tier]}
                  renderMode="block"
                />
              );
            })}

          {displaySettings.showEdgeChannelLabels &&
            edges.map((edge) => {
              const ch = edge.channelsSimulated;
              if (!ch?.available || !edge.pathPoints || edge.pathPoints.length < 2) return null;
              const mid = edge.pathPoints[Math.floor(edge.pathPoints.length / 2)];
              const px = mid.x * cellPx + cellPx / 2;
              const py = mid.y * cellPx + cellPx / 2;
              return (
                <g key={`el-${edge.from}-${edge.to}`} transform={`translate(${px}, ${py})`}>
                  <rect x={-16} y={-8} width={32} height={16} rx={3} fill="var(--bg-primary)" fillOpacity={0.9} />
                  <text textAnchor="middle" dominantBaseline="middle" fontSize={9} fill={displaySettings.colors.labelDim}>
                    {ch.used}/{ch.max}
                  </text>
                </g>
              );
            })}

          {/* Block devices with AE block textures */}
          {blockNodes.map((node) => {
            const px = (node.simGridX ?? 0) * cellPx;
            const py = (node.simGridY ?? 0) * cellPx;
            const isSelected = selectedNodeId === node.id;
            const isDrive = node.type === 'drive';
            const iconId = blockIconIdForNode(node.type, node.iconItemId);
            return (
              <g
                key={node.id}
                className="topology-sim-block"
                style={{ cursor: 'pointer' }}
                onClick={(e) => {
                  e.stopPropagation();
                  if (isDrive && onDriveClick) {
                    onDriveClick(node);
                  } else {
                    onNodeSelect(isSelected ? null : node);
                  }
                }}
              >
                {isSelected && (
                  <rect
                    x={px - 2}
                    y={py - 2}
                    width={blockPx + 4}
                    height={blockPx + 4}
                    fill="none"
                    stroke="var(--accent)"
                    strokeWidth={2}
                    rx={4}
                  />
                )}
                <SimTextureImage iconId={iconId} x={px} y={py} size={blockPx} renderMode="block" />
                <text
                  x={px + blockPx / 2}
                  y={py + blockPx + 12}
                  textAnchor="middle"
                  fontSize={10}
                  fill={displaySettings.colors.label}
                  className="topology-node-label"
                >
                  {node.displayName.length > 22 ? `${node.displayName.slice(0, 21)}…` : node.displayName}
                </text>
              </g>
            );
          })}
        </svg>
      </div>
    );
  }
);
