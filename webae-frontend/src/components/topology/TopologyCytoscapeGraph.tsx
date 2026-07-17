import {
  forwardRef,
  useCallback,
  useEffect,
  useImperativeHandle,
  useMemo,
  useRef,
  useState,
} from 'react';
import cytoscape, { type Core, type ElementDefinition, type NodeSingular } from 'cytoscape';
import coseBilkent from 'cytoscape-cose-bilkent';

import { useAppContext } from '@/context/AppContext';
import type { TopologyEdgeDto, TopologyNodeDto } from '@/types/dto';
import type { TopologyDisplaySettings } from '@/types/topologyDisplay';
import { blockIconIdForNode } from '@/utils/aeCableColors';
import { buildIconUrl, iconIsMarkedFailed, iconLookupIds, iconReadyMatchesId, type IconReadyDetail } from '@/utils/icon';
import { collectIconIdsFromTopology, resolveLocalIconUrls } from '@/utils/iconPrefetch';
import { SERVER_SYNC_PACK_NAME } from '@/utils/localIconPack';
import { trackVisibleIcons } from '@/utils/visibleIconRegistry';
import { branchColorForIndex, computeSpatialPixelLayout } from '@/utils/topologyLayout';
import { capacitySpineEdges, visibleAbstractNodes } from '@/utils/topologyAbstractEdges';
import { computeChannelLanePresetLayout } from '@/utils/topologyLaneLayout';
import { isSpineNode, topologyNodeLabel } from '@/utils/topologyDevices';
import { useNonPassiveWheelZoom } from '@/hooks/useNonPassiveWheelZoom';
import {
  TOPOLOGY_MAX_ZOOM,
  TOPOLOGY_MIN_ZOOM,
  TOPOLOGY_ZOOM_FACTOR,
  type TopologyGraphHandle,
} from '@/components/topology/topologyGraphHandle';

cytoscape.use(coseBilkent);

interface TopologyCytoscapeGraphProps {
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

function edgeColor(edge: TopologyEdgeDto, colors: TopologyDisplaySettings['colors']): string {
  if (edge.overflow) return '#f85149';
  if (edge.kind === 'orbit_link') return colors.labelDim;
  if (edge.branchIndex != null && edge.branchIndex >= 0) {
    return branchColorForIndex(edge.branchIndex);
  }
  const cableKey = edge.cableType || 'covered';
  return colors[cableKey as keyof typeof colors] || colors.covered;
}

/** Ribbon width from capacity max + fill ratio. */
function ribbonWidth(edge: TopologyEdgeDto): number {
  const ch = edge.channelsSimulated;
  const max = ch?.max ?? 0;
  if (edge.kind === 'orbit_link' || max <= 0) return 1.5;
  if (edge.kind === 'capacity_trunk' || edge.cableType === 'dense') {
    return 5 + Math.min(8, (ch?.used ?? 0) / Math.max(1, max) * 4);
  }
  if (edge.kind === 'capacity_lane' || edge.cableType === 'smart') {
    return 3 + Math.min(5, (ch?.used ?? 0) / Math.max(1, max) * 3);
  }
  if (edge.kind === 'pod_uplink') return 2.5;
  return 2;
}

function buildLabel(node: TopologyNodeDto, showCount: boolean): string {
  let text = topologyNodeLabel(node);
  if (text.length > 22) text = `${text.slice(0, 21)}…`;
  if (showCount && node.count > 1) text += ` ×${node.count}`;
  if ((node.patternCount ?? 0) > 0) text += ` [${node.patternCount}]`;
  return text;
}

function nodeBorderColor(
  node: TopologyNodeDto,
  colors: TopologyDisplaySettings['colors'],
  branchColors: Map<string, string>
): string {
  if (node.branchIndex != null && node.branchIndex >= 0) {
    return branchColorForIndex(node.branchIndex);
  }
  return branchColors.get(node.id) ?? colors.nodeStroke;
}

function isCompoundPod(node: TopologyNodeDto): boolean {
  return node.layer === 'pod' || node.type === 'pod' || node.role === 'pod';
}

function isLaneOrTrunk(node: TopologyNodeDto): boolean {
  return (
    node.layer === 'lane' ||
    node.layer === 'trunk' ||
    node.role === 'lane' ||
    node.role === 'trunk' ||
    node.type === 'cable_smart' ||
    node.type === 'cable_dense'
  );
}

export const TopologyCytoscapeGraph = forwardRef<TopologyGraphHandle, TopologyCytoscapeGraphProps>(
  function TopologyCytoscapeGraph(
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
    const cyRef = useRef<Core | null>(null);
    const lastFitEpochRef = useRef('');
    const nodeByIdRef = useRef<Map<string, TopologyNodeDto>>(new Map());
    useNonPassiveWheelZoom(containerRef, useCallback(() => {}, []));
    const { token, iconPack, iconCacheEnabled, iconRenderMode, localIconPack, failedIcons } = useAppContext();
    const [localIconUrls, setLocalIconUrls] = useState<Record<string, string>>({});
    const [iconRefreshEpoch, setIconRefreshEpoch] = useState(0);

    const collapsedPodIds = useMemo(() => {
      if (!displaySettings.collapsePods) return undefined;
      return new Set(
        nodes.filter((n) => isCompoundPod(n)).map((n) => n.id)
      );
    }, [nodes, displaySettings.collapsePods]);

    const visibleNodes = useMemo(
      () =>
        visibleAbstractNodes(nodes, {
          showEmptyLanes: displaySettings.showEmptyLanes,
          collapsedPodIds,
          hideSpine: displaySettings.hideCableNodes,
        }).filter((n) => {
          if (!displaySettings.showEmptyLanes && (n.layer === 'lane' || n.role === 'lane')) {
            const laneEdges = edges.filter(
              (e) => (e.to === n.id || e.from === n.id) && e.kind === 'capacity_lane'
            );
            const used = laneEdges[0]?.channelsSimulated?.used ?? 0;
            if (used <= 0) return false;
          }
          return true;
        }),
      [nodes, edges, displaySettings.showEmptyLanes, displaySettings.hideCableNodes, collapsedPodIds]
    );
    const visibleNodeIds = useMemo(() => new Set(visibleNodes.map((n) => n.id)), [visibleNodes]);
    const displayEdges = useMemo(
      () => capacitySpineEdges(visibleNodes, edges),
      [visibleNodes, edges]
    );

    const abstractLayout =
      mode === 'logical'
        ? displaySettings.abstractLayout ?? (layout === 'star' ? 'star' : 'tree')
        : 'tree';

    const useChannelLane =
      mode === 'logical' && abstractLayout !== 'star';

    const spatialPoints = useMemo(() => {
      if (mode !== 'spatial') return new Map<string, { x: number; y: number }>();
      return computeSpatialPixelLayout(nodes, displaySettings).points;
    }, [nodes, mode, displaySettings]);

    const lanePoints = useMemo(() => {
      if (!useChannelLane) return new Map<string, { x: number; y: number }>();
      return computeChannelLanePresetLayout(visibleNodes, displaySettings);
    }, [visibleNodes, useChannelLane, displaySettings]);

    const hubId = useMemo(
      () => visibleNodes.find((n) => n.id === 'controller' || n.type === 'controller')?.id,
      [visibleNodes]
    );

    const branchColors = useMemo(() => {
      const map = new Map<string, string>();
      for (const node of visibleNodes) {
        if (node.branchIndex != null && node.branchIndex >= 0) {
          map.set(node.id, branchColorForIndex(node.branchIndex));
        }
      }
      return map;
    }, [visibleNodes]);

    useEffect(() => {
      const handler = (ev: Event) => {
        const detail = (ev as CustomEvent<IconReadyDetail>).detail;
        if (!detail?.itemId) return;
        const affected = visibleNodes.some((node) => {
          const iconId = blockIconIdForNode(node.type, node.iconItemId);
          return iconReadyMatchesId(detail, iconId);
        });
        if (affected) setIconRefreshEpoch((epoch) => epoch + 1);
      };
      window.addEventListener('webae-icon-ready', handler);
      return () => window.removeEventListener('webae-icon-ready', handler);
    }, [visibleNodes]);

    useEffect(() => {
      const ids = collectIconIdsFromTopology(visibleNodes);
      const untrack = trackVisibleIcons(ids);
      let cancelled = false;
      const pack = localIconPack || SERVER_SYNC_PACK_NAME;
      void resolveLocalIconUrls(ids, pack).then((map) => {
        if (!cancelled) setLocalIconUrls(map);
      });
      return () => {
        cancelled = true;
        untrack();
      };
    }, [visibleNodes, localIconPack, iconRefreshEpoch]);

    const elements = useMemo((): ElementDefinition[] => {
      const out: ElementDefinition[] = [];
      const colors = displaySettings.colors;
      const r = displaySettings.nodeRadius;
      const nodeSize = (node: TopologyNodeDto) => {
        if (node.role === 'hub') return (r + 4) * 2;
        if (isCompoundPod(node)) return (r + 6) * 2;
        if (isLaneOrTrunk(node)) return (r + 2) * 2;
        return r * 2;
      };

      for (const node of visibleNodes) {
        const isHub = node.role === 'hub' || node.layer === 'hub';
        const iconId = blockIconIdForNode(node.type, node.iconItemId);
        let iconUrl = '';
        if (iconId && !iconIsMarkedFailed(failedIcons, iconId) && !isCompoundPod(node)) {
          iconUrl = localIconUrls[iconId] || '';
          if (!iconUrl) {
            const candidates = iconLookupIds(undefined, iconId);
            for (const c of candidates) {
              if (localIconUrls[c]) {
                iconUrl = localIconUrls[c];
                break;
              }
            }
          }
          if (!iconUrl) {
            iconUrl =
              buildIconUrl(iconId, iconPack, token, iconCacheEnabled, iconRenderMode) +
              (iconRefreshEpoch ? `&r=${iconRefreshEpoch}` : '');
          }
        }
        const pos =
          mode === 'spatial' ? spatialPoints.get(node.id) : useChannelLane ? lanePoints.get(node.id) : undefined;

        // Pods are visual role groups (edges + styling), not Cytoscape compounds —
        // compound parents would break absolute preset coordinates.
        out.push({
          group: 'nodes',
          data: {
            id: node.id,
            label: buildLabel(node, displaySettings.showCountLabels),
            iconUrl,
            role: node.role ?? '',
            layer: node.layer ?? '',
            channelCost: node.channelCost ?? 0,
            branchIndex: node.branchIndex ?? -1,
            borderColor: nodeBorderColor(node, colors, branchColors),
            nodeSize: nodeSize(node),
            isHub,
            isPod: isCompoundPod(node),
            isSpine: isSpineNode(node),
            isOrbit: node.layer === 'orbit' || node.role === 'orbit',
          },
          ...(pos ? { position: { x: pos.x, y: pos.y } } : {}),
        });
      }

      for (const edge of displayEdges) {
        if (!visibleNodeIds.has(edge.from) || !visibleNodeIds.has(edge.to)) {
          continue;
        }
        const ch = edge.channelsSimulated;
        const real = edge.channelsReal;
        let channelLabel = '';
        if (displaySettings.showEdgeChannelLabels && ch?.available && !edge.emptyBranch) {
          channelLabel = `${ch.used}/${ch.max}`;
          if (edge.overflow) channelLabel += '!';
          if (real?.available) channelLabel += ` ·${real.used}`;
        }
        out.push({
          group: 'edges',
          data: {
            id: `${edge.from}-${edge.to}-${edge.kind || 'link'}-${edge.emptyBranch ? 'empty' : 'c'}`,
            source: edge.from,
            target: edge.to,
            label: channelLabel,
            color: edgeColor(edge, colors),
            width: ribbonWidth(edge),
            emptyBranch: !!edge.emptyBranch,
            overflow: !!edge.overflow,
            isOrbit: edge.kind === 'orbit_link',
            isCapacity: edge.kind === 'capacity_trunk' || edge.kind === 'capacity_lane',
          },
        });
      }
      return out;
    }, [
      visibleNodes,
      visibleNodeIds,
      displayEdges,
      displaySettings,
      spatialPoints,
      lanePoints,
      branchColors,
      iconPack,
      token,
      iconCacheEnabled,
      iconRenderMode,
      failedIcons,
      iconRefreshEpoch,
      localIconUrls,
      mode,
      useChannelLane,
    ]);

    const runLayout = useCallback(
      (cy: Core) => {
        const siblingGap = Math.max(24, displaySettings.siblingGap);

        if (mode === 'spatial') {
          cy.layout({
            name: 'cose-bilkent',
            animate: false,
            randomize: false,
            nodeRepulsion: 6500,
            idealEdgeLength: Math.max(80, displaySettings.depthGap * 0.75),
            edgeElasticity: 0.45,
            nestingFactor: 0.1,
            gravity: 0.2,
            numIter: 2000,
            tile: true,
            tilingPaddingVertical: siblingGap * 0.25,
            tilingPaddingHorizontal: siblingGap * 0.25,
          } as cytoscape.LayoutOptions).run();
          return;
        }

        if (useChannelLane) {
          // Positions already set via preset; only fit.
          cy.layout({
            name: 'preset',
            animate: false,
            fit: true,
            padding: displaySettings.labelMargin + displaySettings.nodeRadius,
          } as cytoscape.LayoutOptions).run();
          return;
        }

        if (abstractLayout === 'star') {
          cy.layout({
            name: 'concentric',
            animate: false,
            fit: true,
            padding: displaySettings.labelMargin + displaySettings.nodeRadius,
            concentric: (ele) => {
              if (ele.data('isHub')) return 100;
              return (ele.data('channelCost') as number) > 0 ? 50 : 10;
            },
            levelWidth: () => 1,
            minNodeSpacing: siblingGap,
            spacingFactor: Math.max(0.5, displaySettings.depthGap / 100),
          } as cytoscape.LayoutOptions).run();
          return;
        }

        const roots = hubId ? `#${hubId.replace(/[^a-zA-Z0-9_-]/g, '\\$&')}` : undefined;
        cy.layout({
          name: 'breadthfirst',
          animate: false,
          directed: true,
          fit: true,
          padding: displaySettings.labelMargin + displaySettings.nodeRadius,
          spacingFactor: Math.max(0.5, displaySettings.depthGap / 100),
          nodeDimensionsIncludeLabels: true,
          ...(roots ? { roots } : {}),
        } as cytoscape.LayoutOptions).run();
      },
      [mode, abstractLayout, useChannelLane, displaySettings, hubId]
    );

    const fitView = useCallback(() => {
      const cy = cyRef.current;
      if (!cy) return;
      cy.fit(undefined, displaySettings.labelMargin + displaySettings.nodeRadius);
    }, [displaySettings.labelMargin, displaySettings.nodeRadius]);

    useImperativeHandle(ref, () => ({
      resetView: () => {
        const cy = cyRef.current;
        if (!cy) return;
        cy.zoom(1);
        cy.pan({ x: 0, y: 0 });
      },
      fitView,
      zoomIn: () => {
        const cy = cyRef.current;
        if (!cy) return;
        cy.zoom(Math.min(TOPOLOGY_MAX_ZOOM, cy.zoom() * TOPOLOGY_ZOOM_FACTOR));
      },
      zoomOut: () => {
        const cy = cyRef.current;
        if (!cy) return;
        cy.zoom(Math.max(TOPOLOGY_MIN_ZOOM, cy.zoom() / TOPOLOGY_ZOOM_FACTOR));
      },
    }));

    useEffect(() => {
      nodeByIdRef.current = new Map(visibleNodes.map((n) => [n.id, n]));
    }, [visibleNodes]);

    useEffect(() => {
      const host = containerRef.current;
      if (!host) return;

      const colors = displaySettings.colors;
      const cy = cytoscape({
        container: host,
        elements,
        minZoom: TOPOLOGY_MIN_ZOOM,
        maxZoom: TOPOLOGY_MAX_ZOOM,
        wheelSensitivity: 0.22,
        boxSelectionEnabled: false,
        autounselectify: false,
        style: [
          {
            selector: 'node',
            style: {
              'background-color': colors.nodeFill,
              'background-opacity': 0.95,
              'background-image': (ele: NodeSingular) => {
                const url = ele.data('iconUrl');
                return url ? url : 'none';
              },
              'background-fit': 'contain',
              'background-clip': 'none',
              'border-width': 2,
              'border-color': 'data(borderColor)',
              width: 'data(nodeSize)',
              height: 'data(nodeSize)',
              label: 'data(label)',
              'font-size': 11,
              color: colors.label,
              'text-outline-color': colors.nodeFill,
              'text-outline-width': 2,
              'text-wrap': 'ellipsis',
              'text-max-width': '120px',
              'text-valign': 'bottom',
              'text-halign': 'center',
              'text-margin-y': 6,
              'overlay-opacity': 0,
            },
          },
          {
            selector: 'node[?isHub]',
            style: {
              'border-width': 3,
              'border-color': colors.dense,
              shape: 'round-rectangle',
            },
          },
          {
            selector: 'node[?isPod]',
            style: {
              shape: 'round-rectangle',
              'background-opacity': 0.35,
              'border-width': 2,
              'border-style': 'dashed',
              'text-valign': 'top',
              'text-margin-y': -4,
              'font-size': 10,
              'text-max-width': '140px',
            },
          },
          {
            selector: 'node[?isSpine]',
            style: {
              shape: 'round-rectangle',
              'background-opacity': 0.85,
            },
          },
          {
            selector: 'node[?isOrbit]',
            style: {
              'border-style': 'dotted',
              'border-width': 1.5,
              opacity: 0.92,
            },
          },
          {
            selector: 'node:selected',
            style: {
              'border-color': '#58a6ff',
              'border-width': 3,
              'background-opacity': 1,
            },
          },
          {
            selector: 'node.hover',
            style: {
              'border-width': 2.5,
              'border-color': '#58a6ff',
            },
          },
          {
            selector: 'edge',
            style: {
              width: 'data(width)',
              'line-color': 'data(color)',
              'target-arrow-shape': 'none',
              'curve-style': 'bezier',
              opacity: 0.88,
              label: 'data(label)',
              'font-size': 10,
              color: colors.labelDim,
              'text-background-color': colors.nodeFill,
              'text-background-opacity': 0.88,
              'text-background-padding': '3px',
              'text-background-shape': 'roundrectangle',
            },
          },
          {
            selector: 'edge[?isCapacity]',
            style: {
              'curve-style': 'haystack',
              'haystack-radius': 0,
              opacity: 0.95,
            },
          },
          {
            selector: 'edge[?isOrbit]',
            style: {
              'line-style': 'dashed',
              'line-dash-pattern': [4, 4],
              opacity: 0.5,
              width: 1.5,
            },
          },
          {
            selector: 'edge[?emptyBranch]',
            style: {
              'line-style': 'dashed',
              'line-dash-pattern': [6, 4],
              opacity: 0.4,
            },
          },
          {
            selector: 'edge[?overflow]',
            style: {
              'line-color': '#f85149',
              'border-width': 1,
              opacity: 1,
            },
          },
        ] as cytoscape.StylesheetStyle[],
      });

      cyRef.current = cy;

      cy.on('tap', 'node', (evt) => {
        const id = evt.target.id();
        const node = nodeByIdRef.current.get(id);
        if (!node) return;
        const already = selectedNodeId === id;
        onNodeSelect(already ? null : node);
      });

      cy.on('tap', (evt) => {
        if (evt.target === cy) onNodeSelect(null);
      });

      cy.on('mouseover', 'node', (evt) => {
        onNodeHover?.(evt.target.id());
      });

      cy.on('mouseout', 'node', () => {
        onNodeHover?.(null);
      });

      runLayout(cy);

      return () => {
        cy.destroy();
        cyRef.current = null;
      };
      // eslint-disable-next-line react-hooks/exhaustive-deps -- full rebuild when graph data or theme colors change
    }, [elements, displaySettings.colors, displaySettings.labelStrategy, runLayout]);

    useEffect(() => {
      const cy = cyRef.current;
      if (!cy) return;
      cy.batch(() => {
        cy.nodes().removeClass('hover');
        if (hoveredNodeId) cy.getElementById(hoveredNodeId).addClass('hover');
        cy.nodes().unselect();
        if (selectedNodeId) cy.getElementById(selectedNodeId).select();
      });
    }, [selectedNodeId, hoveredNodeId]);

    useEffect(() => {
      if (!layoutEpoch || layoutEpoch === lastFitEpochRef.current) return;
      lastFitEpochRef.current = layoutEpoch;
      const cy = cyRef.current;
      if (!cy) return;
      runLayout(cy);
      requestAnimationFrame(() => fitView());
    }, [layoutEpoch, fitView, runLayout]);

    return (
      <div
        ref={containerRef}
        className="topology-graph-host topology-cytoscape-host"
        style={{ height, overscrollBehavior: 'contain', touchAction: 'none' }}
        role="img"
        aria-label="AE channel budget topology"
      />
    );
  }
);
