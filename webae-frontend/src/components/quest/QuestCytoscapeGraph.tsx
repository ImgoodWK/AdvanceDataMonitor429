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

import { useAppContext } from '@/context/AppContext';
import { useNonPassiveWheelZoom } from '@/hooks/useNonPassiveWheelZoom';
import { TOPOLOGY_ZOOM_FACTOR, type TopologyGraphHandle } from '@/components/topology/topologyGraphHandle';
import type { QuestLineEdgeDto, QuestLineNodeDto } from '@/types/dto';
import type { QuestDisplaySettings } from '@/types/questDisplay';
import { buildIconUrl, iconLookupIds } from '@/utils/icon';
import { getMcPrimaryColor, stripMcFormatting } from '@/utils/mcFormatting';
import { questStateColor } from '@/components/quest/questUtils';

const QUEST_MIN_ZOOM = 0.12;
const QUEST_MAX_ZOOM = 6;
const QUEST_BG_SUFFIX = '::bg';
const QUEST_ICON_SUFFIX = '::icon';
const QUEST_ICON_Z_INDEX = 5;

type QuestHoverTip = {
  text: string;
  x: number;
  y: number;
};

function questBgId(questId: string): string {
  return `${questId}${QUEST_BG_SUFFIX}`;
}

function questIconId(questId: string): string {
  return `${questId}${QUEST_ICON_SUFFIX}`;
}

function resolveQuestId(nodeId: string, dataQuestId?: string): string {
  if (dataQuestId) return dataQuestId;
  if (nodeId.endsWith(QUEST_BG_SUFFIX)) return nodeId.slice(0, -QUEST_BG_SUFFIX.length);
  if (nodeId.endsWith(QUEST_ICON_SUFFIX)) return nodeId.slice(0, -QUEST_ICON_SUFFIX.length);
  return nodeId;
}

export type QuestGraphHandle = TopologyGraphHandle & {
  centerOnQuest: (questId: string) => void;
};

interface QuestCytoscapeGraphProps {
  nodes: QuestLineNodeDto[];
  edges: QuestLineEdgeDto[];
  selectedQuestId: string | null;
  layoutKey?: string | null;
  displaySettings: QuestDisplaySettings;
  onNodeSelect: (questId: string | null) => void;
  onGhostLineJump?: (lineId: string) => void;
  height?: number | string;
}

function buildQuestStyle(settings: QuestDisplaySettings): cytoscape.StylesheetStyle[] {
  const iconFill = `${settings.iconFillPercent}%`;
  return [
    {
      selector: 'node.quest-bg',
      style: {
        label: settings.showLabels ? 'data(label)' : '',
        'text-valign': 'bottom',
        'text-halign': 'center',
        'font-size': settings.labelFontSize,
        color: 'data(labelColor)',
        'text-outline-color': '#0f172a',
        'text-outline-width': 2,
        'text-margin-y': 8,
        'text-max-width': `${settings.labelMaxWidth}px`,
        'text-wrap': 'ellipsis',
        width: 'data(width)',
        height: 'data(height)',
        'background-color': 'data(bgColor)',
        'background-opacity': 0.92,
        'border-width': 3,
        'border-color': 'data(borderColor)',
        opacity: (ele: NodeSingular) => {
          const v = ele.data('opacity');
          return typeof v === 'number' ? v : 1;
        },
        shape: 'round-rectangle',
        'z-index': 0,
      },
    },
    {
      selector: 'node.quest-icon',
      style: {
        label: '',
        width: 'data(width)',
        height: 'data(height)',
        'background-color': '#000000',
        'background-opacity': 0,
        'background-image': (ele: NodeSingular) => {
          const url = ele.data('iconUrl');
          return url && url !== 'none' ? url : 'none';
        },
        'background-fit': 'contain',
        'background-clip': 'none',
        'background-width': iconFill,
        'background-height': iconFill,
        'background-image-containment': 'over',
        'border-width': 0,
        opacity: (ele: NodeSingular) => {
          const v = ele.data('opacity');
          return typeof v === 'number' ? v : 1;
        },
        shape: 'round-rectangle',
        'z-index': QUEST_ICON_Z_INDEX,
      },
    },
    {
      selector: 'node.quest-bg.main-quest',
      style: {
        shape: 'diamond',
        'border-width': 4,
      },
    },
    {
      selector: 'node.quest-bg.ghost-quest',
      style: {
        'border-style': 'dashed',
        'background-opacity': 0.35,
        opacity: 0.7,
      },
    },
    {
      selector: 'node.quest-bg.can-submit',
      style: {
        'border-width': 4,
        'border-color': '#fbbf24',
      },
    },
    {
      selector: 'node.quest-bg:selected',
      style: {
        'border-color': '#ffffff',
        'border-width': 5,
      },
    },
    {
      selector: 'node.quest-bg.related',
      style: {
        'border-color': '#38bdf8',
        'border-width': 4,
        opacity: 1,
      },
    },
    {
      selector: 'node.quest-icon.related',
      style: {
        opacity: 1,
      },
    },
    {
      selector: 'node.quest-icon.ghost-quest',
      style: {
        opacity: 0.7,
      },
    },
    {
      selector: 'node.dimmed',
      style: {
        opacity: 0.22,
      },
    },
    {
      selector: 'edge',
      style: {
        width: settings.edgeWidth,
        'line-color': '#94a3b8',
        'target-arrow-color': '#94a3b8',
        'target-arrow-shape': 'triangle',
        'curve-style': 'bezier',
        opacity: 0.85,
      },
    },
    {
      selector: 'edge.hidden-req',
      style: {
        'line-style': 'dotted',
        opacity: 0.5,
      },
    },
    {
      selector: 'edge.related',
      style: {
        width: Math.max(settings.edgeWidth + 1.5, 4),
        'line-color': '#38bdf8',
        'target-arrow-color': '#38bdf8',
        opacity: 1,
      },
    },
    {
      selector: 'edge.dimmed',
      style: {
        opacity: 0.12,
      },
    },
  ];
}

export const QuestCytoscapeGraph = forwardRef<QuestGraphHandle, QuestCytoscapeGraphProps>(
  function QuestCytoscapeGraph(
    {
      nodes,
      edges,
      selectedQuestId,
      layoutKey,
      displaySettings,
      onNodeSelect,
      onGhostLineJump,
      height = '100%',
    },
    ref
  ) {
    const containerRef = useRef<HTMLDivElement>(null);
    const cyRef = useRef<Core | null>(null);
    const displaySettingsRef = useRef(displaySettings);
    const onNodeSelectRef = useRef(onNodeSelect);
    const onGhostLineJumpRef = useRef(onGhostLineJump);
    const setHoverTipRef = useRef<(tip: QuestHoverTip | null) => void>(() => undefined);
    const { token, iconPack, iconCacheEnabled, iconRenderMode } = useAppContext();
    const [hoverTip, setHoverTip] = useState<QuestHoverTip | null>(null);

    displaySettingsRef.current = displaySettings;
    onNodeSelectRef.current = onNodeSelect;
    onGhostLineJumpRef.current = onGhostLineJump;
    setHoverTipRef.current = setHoverTip;

    const elements = useMemo(() => {
      const { coordScale, minNodeSize, nodeSizeScale } = displaySettings;
      const els: ElementDefinition[] = [];
      const nodeIds = new Set<string>();
      for (const node of nodes) {
        if (!node.questId || nodeIds.has(node.questId)) continue;
        nodeIds.add(node.questId);
        const color = questStateColor(node.state);
        const ids = iconLookupIds({
          itemId: node.iconItemId,
          registryName: node.iconItemId,
          meta: node.iconMeta,
        });
        const iconId = ids[0];
        // #region agent log
        if (
          node.iconItemId?.startsWith('fluid:') ||
          (node.iconMeta != null && node.iconMeta >= 30000) ||
          (node.iconItemId && /cell|fluid|metaitem\.01/i.test(node.iconItemId))
        ) {
          fetch('http://127.0.0.1:7665/ingest/a3d1b8cf-d88c-4478-ad0e-1e3322a7890c', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'X-Debug-Session-Id': 'fe62d6' },
            body: JSON.stringify({
              sessionId: 'fe62d6',
              hypothesisId: 'H1',
              location: 'QuestCytoscapeGraph.tsx:elements',
              message: 'cytoscapeIconPick',
              data: {
                questId: node.questId,
                iconItemId: node.iconItemId,
                iconMeta: node.iconMeta,
                ids0: iconId,
                ids,
              },
              timestamp: Date.now(),
            }),
          }).catch(() => {});
        }
        // #endregion
        const iconUrl =
          iconId && token
            ? buildIconUrl(iconId, iconPack, token, iconCacheEnabled, iconRenderMode)
            : '';
        const opacity =
          node.state === 'LOCKED' ? 0.55 : node.state === 'COMPLETED' ? 0.72 : 1;
        const width = Math.max(minNodeSize, (node.sizeX || 24) * nodeSizeScale);
        const height = Math.max(minNodeSize, (node.sizeY || 24) * nodeSizeScale);
        const position = { x: (node.x || 0) * coordScale, y: (node.y || 0) * coordScale };
        const nodeClasses = [
          node.mainQuest ? 'main-quest' : 'norm-quest',
          node.ghost ? 'ghost-quest' : '',
          node.canSubmit ? 'can-submit' : '',
        ]
          .filter(Boolean)
          .join(' ');
        const sharedData = {
          questId: node.questId,
          label: stripMcFormatting(node.name || node.questId),
          labelColor: getMcPrimaryColor(node.name || '') || '#e2e8f0',
          width,
          height,
          borderColor: node.canSubmit ? '#fbbf24' : color,
          bgColor: color,
          iconUrl: iconUrl || 'none',
          opacity,
          isMain: !!node.mainQuest,
          isGhost: !!node.ghost,
          sourceLineId: node.sourceLineId || '',
          state: node.state || 'LOCKED',
        };
        els.push({
          group: 'nodes',
          data: {
            id: questBgId(node.questId),
            ...sharedData,
          },
          classes: `quest-bg ${nodeClasses}`.trim(),
          position,
        });
        els.push({
          group: 'nodes',
          data: {
            id: questIconId(node.questId),
            ...sharedData,
          },
          classes: `quest-icon${node.ghost ? ' ghost-quest' : ''}`.trim(),
          position,
        });
      }
      const edgeIds = new Set<string>();
      for (const edge of edges) {
        if (
          !edge.fromQuestId ||
          !edge.toQuestId ||
          !nodeIds.has(edge.fromQuestId) ||
          !nodeIds.has(edge.toQuestId)
        ) {
          continue;
        }
        const eid = `${edge.fromQuestId}->${edge.toQuestId}`;
        if (edgeIds.has(eid)) continue;
        edgeIds.add(eid);
        els.push({
          group: 'edges',
          data: {
            id: eid,
            source: questBgId(edge.fromQuestId),
            target: questBgId(edge.toQuestId),
          },
          classes: edge.requirementType === 'HIDDEN' ? 'hidden-req' : '',
        });
      }
      return els;
    }, [nodes, edges, token, iconPack, iconCacheEnabled, iconRenderMode, displaySettings]);

    const fitView = useCallback(() => {
      const cy = cyRef.current;
      if (!cy) return;
      cy.fit(undefined, displaySettingsRef.current.fitPadding);
    }, []);

    const resetView = fitView;

    useEffect(() => {
      if (!containerRef.current) return;

      let cy: Core;
      try {
        cy = cytoscape({
          container: containerRef.current,
          elements: [],
          minZoom: QUEST_MIN_ZOOM,
          maxZoom: QUEST_MAX_ZOOM,
          wheelSensitivity: 0,
          // Fixed BQ layout: pan/zoom only; do not let players drag quest nodes.
          autoungrabify: true,
          style: buildQuestStyle(displaySettingsRef.current),
          layout: { name: 'preset' },
        });
      } catch {
        return;
      }

      const clearHover = () => {
        cy.nodes().removeClass('related dimmed');
        cy.edges().removeClass('related dimmed');
        setHoverTipRef.current(null);
      };

      const relateQuestPair = (questId: string) => {
        const bgNode = cy.getElementById(questBgId(questId));
        const iconNode = cy.getElementById(questIconId(questId));
        if (bgNode.nonempty()) bgNode.removeClass('dimmed').addClass('related');
        if (iconNode.nonempty()) iconNode.removeClass('dimmed').addClass('related');
      };

      const placeHoverTip = (anchor: NodeSingular, label: string) => {
        const container = containerRef.current;
        if (!container || !label) {
          setHoverTipRef.current(null);
          return;
        }
        const rendered = anchor.renderedBoundingBox({ includeLabels: false });
        const tipW = Math.min(280, Math.max(80, label.length * 8));
        let x = rendered.x1 + (rendered.x2 - rendered.x1) / 2 - tipW / 2;
        let y = rendered.y1 - 36;
        const maxX = Math.max(8, container.clientWidth - tipW - 8);
        x = Math.max(8, Math.min(x, maxX));
        if (y < 8) y = rendered.y2 + 8;
        setHoverTipRef.current({ text: label, x, y });
      };

      cy.on('mouseover', 'node', (evt) => {
        const questId = resolveQuestId(evt.target.id(), evt.target.data('questId'));
        const bgNode = cy.getElementById(questBgId(questId));
        const anchor = bgNode.nonempty() ? bgNode : evt.target;
        const neigh = anchor.closedNeighborhood();
        cy.batch(() => {
          cy.nodes().addClass('dimmed');
          cy.edges().addClass('dimmed');
          neigh.removeClass('dimmed').addClass('related');
          relateQuestPair(questId);
        });
        const label = String(evt.target.data('label') || '');
        placeHoverTip(anchor, label);
      });
      cy.on('mouseout', 'node', clearHover);
      cy.on('viewport', () => setHoverTipRef.current(null));

      cy.on('tap', 'node', (evt) => {
        const questId = resolveQuestId(evt.target.id(), evt.target.data('questId'));
        const sourceLine = evt.target.data('sourceLineId') as string;
        const isGhost = !!evt.target.data('isGhost');
        if (isGhost && sourceLine && onGhostLineJumpRef.current) {
          onGhostLineJumpRef.current(sourceLine);
        }
        onNodeSelectRef.current(questId);
      });
      cy.on('tap', (evt) => {
        if (evt.target === cy) onNodeSelectRef.current(null);
      });

      cyRef.current = cy;
      return () => {
        setHoverTipRef.current(null);
        cy.destroy();
        cyRef.current = null;
      };
    }, []);

    useEffect(() => {
      const cy = cyRef.current;
      if (!cy) return;
      cy.style(buildQuestStyle(displaySettings));
    }, [displaySettings]);

    useEffect(() => {
      const cy = cyRef.current;
      if (!cy) return;
      cy.json({ elements });
      setHoverTip(null);
    }, [elements]);

    useEffect(() => {
      if (!layoutKey) return;
      const cy = cyRef.current;
      if (!cy) return;
      requestAnimationFrame(() => fitView());
    }, [layoutKey, fitView]);

    useEffect(() => {
      const cy = cyRef.current;
      if (!cy) return;
      cy.nodes().unselect();
      if (selectedQuestId) {
        const bg = cy.getElementById(questBgId(selectedQuestId));
        const icon = cy.getElementById(questIconId(selectedQuestId));
        if (bg.nonempty()) bg.select();
        if (icon.nonempty()) icon.select();
      }
    }, [selectedQuestId]);

    const zoomIn = useCallback(() => {
      const cy = cyRef.current;
      if (!cy) return;
      cy.zoom(Math.min(QUEST_MAX_ZOOM, cy.zoom() * TOPOLOGY_ZOOM_FACTOR));
    }, []);

    const zoomOut = useCallback(() => {
      const cy = cyRef.current;
      if (!cy) return;
      cy.zoom(Math.max(QUEST_MIN_ZOOM, cy.zoom() / TOPOLOGY_ZOOM_FACTOR));
    }, []);

    const centerOnQuest = useCallback((questId: string) => {
      const cy = cyRef.current;
      if (!cy) return;
      const minZoom = displaySettingsRef.current.centerMinZoom;
      const n = cy.getElementById(questBgId(questId));
      if (n.nonempty()) {
        cy.animate(
          { center: { eles: n }, zoom: Math.max(cy.zoom(), minZoom) },
          { duration: 250 }
        );
        n.select();
        const icon = cy.getElementById(questIconId(questId));
        if (icon.nonempty()) icon.select();
      }
    }, []);

    useImperativeHandle(
      ref,
      () => ({ resetView, fitView, zoomIn, zoomOut, centerOnQuest }),
      [resetView, fitView, zoomIn, zoomOut, centerOnQuest]
    );

    const wheelHandler = useCallback((e: WheelEvent) => {
      const cy = cyRef.current;
      const container = containerRef.current;
      if (!cy || !container) return;
      const factor = e.deltaY > 0 ? 1 / TOPOLOGY_ZOOM_FACTOR : TOPOLOGY_ZOOM_FACTOR;
      const newZoom = Math.min(QUEST_MAX_ZOOM, Math.max(QUEST_MIN_ZOOM, cy.zoom() * factor));
      const rect = container.getBoundingClientRect();
      cy.zoom({
        level: newZoom,
        renderedPosition: {
          x: e.clientX - rect.left,
          y: e.clientY - rect.top,
        },
      });
    }, []);

    useNonPassiveWheelZoom(containerRef, wheelHandler);

    return (
      <div style={{ position: 'relative', width: '100%', height, minHeight: 360 }}>
        <div
          ref={containerRef}
          style={{
            width: '100%',
            height: '100%',
            minHeight: 360,
            borderRadius: 8,
            border: '1px solid var(--border-color, #334155)',
            background: 'var(--layout-canvas-bg, #0f172a)',
            touchAction: 'none',
            overscrollBehavior: 'contain',
          }}
        />
        {hoverTip ? (
          <div
            role="tooltip"
            style={{
              position: 'absolute',
              left: hoverTip.x,
              top: hoverTip.y,
              zIndex: 20,
              maxWidth: 280,
              padding: '4px 8px',
              borderRadius: 6,
              background: 'rgba(15, 23, 42, 0.92)',
              border: '1px solid rgba(148, 163, 184, 0.45)',
              color: '#e2e8f0',
              fontSize: 12,
              lineHeight: 1.35,
              pointerEvents: 'none',
              boxShadow: '0 4px 12px rgba(0,0,0,0.35)',
              whiteSpace: 'nowrap',
              overflow: 'hidden',
              textOverflow: 'ellipsis',
            }}
          >
            {hoverTip.text}
          </div>
        ) : null}
      </div>
    );
  }
);
