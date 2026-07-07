import type { TopologyNodeDto } from '@/types/dto';
import type { TopologyDisplaySettings } from '@/types/topologyDisplay';
import { topologyNodeLabel } from '@/utils/topologyDevices';

export interface ScreenPoint {
  x: number;
  y: number;
}

export interface NodeRect {
  id: string;
  cx: number;
  cy: number;
  r: number;
  label?: string;
  labelX?: number;
  labelY?: number;
}

const SIM_ORIGIN_X = 12;
const SIM_ORIGIN_Y = 12;
const SIM_RING_INNER = 5;
const SIM_RING_OUTER = 8;

export interface PixelLayoutResult {
  points: Map<string, ScreenPoint>;
  viewBox: string;
  width: number;
  height: number;
  maxDepth: number;
  leafCount: number;
}

const MIN_SCALE = 0.35;
const MAX_SCALE = 4;

export { MIN_SCALE as TOPOLOGY_MIN_SCALE, MAX_SCALE as TOPOLOGY_MAX_SCALE };

/** Approx pixel width per character for topology SVG labels (font-size ~12px, sans-serif). */
const CHAR_WIDTH = 7.5;
/** Min gap (px) between adjacent node circle edges to avoid overlap. */
const MIN_NODE_GAP = 8;
/** Min gap between a label bounding box and an adjacent node circle. */
const MIN_LABEL_NODE_GAP = 10;

function pad(value: number, margin: number): number {
  return value + margin * 2;
}

/** Estimate rendered width of a label string in px. */
export function estimateLabelWidth(text: string): number {
  if (!text) return 0;
  // CJK characters are roughly 2× char width in monospace-ish render
  let w = 0;
  for (const ch of text) {
    w += /[\u4e00-\u9fff\u3000-\u303f\uff00-\uffef]/.test(ch) ? CHAR_WIDTH * 2 : CHAR_WIDTH;
  }
  return w;
}

/**
 * Check if two nodes' circles overlap and return true if they do.
 */
export function nodesOverlap(a: NodeRect, b: NodeRect): boolean {
  const dx = a.cx - b.cx;
  const dy = a.cy - b.cy;
  const dist = Math.sqrt(dx * dx + dy * dy);
  return dist < a.r + b.r + MIN_NODE_GAP;
}

/**
 * Check if a label at (lx,ly) with given width overlaps a node circle.
 * Assumes label starts at the given position going rightward and is approx 16px tall.
 */
function labelOverlapsNode(
  lx: number, ly: number, labelWidth: number,
  nodeCx: number, nodeCy: number, nodeR: number
): boolean {
  const labelTop = ly - 10;
  const labelBottom = ly + 6;
  const labelRight = lx + labelWidth;
  // Simple AABB vs circle overlap: if closest point of AABB to circle center is within radius
  const closestX = Math.max(lx, Math.min(nodeCx, labelRight));
  const closestY = Math.max(labelTop, Math.min(nodeCy, labelBottom));
  const dx = nodeCx - closestX;
  const dy = nodeCy - closestY;
  return (dx * dx + dy * dy) < (nodeR + MIN_LABEL_NODE_GAP) ** 2;
}

/**
 * Compute required ring radius to avoid node overlap, given device count.
 * Returns scaled depthGap factor.
 */
function safeRingRadius(
  count: number,
  baseR: number,
  nodeRadius: number,
  maxR: number
): number {
  if (count <= 1) return baseR;
  // Chord length between adjacent devices on ring: 2*r*sin(π/count)
  // Need chord >= 2*nodeRadius + MIN_NODE_GAP
  const minChord = 2 * nodeRadius + MIN_NODE_GAP;
  const neededR = minChord / (2 * Math.sin(Math.PI / count));
  return Math.min(maxR, Math.max(baseR, neededR));
}

/** Pixel-coordinate star layout for logical mode (controller hub + device groups on a ring). */
export function computeStarPixelLayout(
  nodes: TopologyNodeDto[],
  settings: Pick<TopologyDisplaySettings, 'depthGap' | 'labelMargin' | 'nodeRadius'>
): PixelLayoutResult {
  if (nodes.length === 0) {
    return { points: new Map(), viewBox: '0 0 100 100', width: 100, height: 100, maxDepth: 0, leafCount: 0 };
  }

  const hub = nodes.find((n) => n.id === 'controller' || n.type === 'controller');
  const satellites = nodes.filter((n) => n.type !== 'cell' && n.id !== hub?.id);
  const count = satellites.length;
  const baseR = settings.depthGap;
  const nodeR = settings.nodeRadius;
  const maxR = baseR * 2;
  const radius = safeRingRadius(count, baseR, nodeR, maxR);
  const margin = settings.labelMargin + nodeR;
  const centerX = radius + margin;
  const centerY = radius + margin;
  const width = (radius + margin) * 2;
  const height = (radius + margin) * 2;

  const points = new Map<string, ScreenPoint>();
  if (hub) {
    points.set(hub.id, { x: centerX, y: centerY });
  }

  for (let i = 0; i < count; i++) {
    const node = satellites[i];
    const angle = count > 0 ? (2 * Math.PI * i) / count - Math.PI / 2 : 0;
    points.set(node.id, {
      x: centerX + Math.cos(angle) * radius,
      y: centerY + Math.sin(angle) * radius,
    });
  }

  return {
    points,
    viewBox: `0 0 ${width} ${height}`,
    width,
    height,
    maxDepth: 1,
    leafCount: count,
  };
}

/** Pixel-coordinate tree layout for logical mode (backend layoutX/layoutY with negative depth for north tier). */
export function computeTreePixelLayout(
  nodes: TopologyNodeDto[],
  settings: Pick<TopologyDisplaySettings, 'layoutDirection' | 'depthGap' | 'siblingGap' | 'labelMargin' | 'nodeRadius'>
): PixelLayoutResult {
  if (nodes.length === 0) {
    return { points: new Map(), viewBox: '0 0 100 100', width: 100, height: 100, maxDepth: 0, leafCount: 0 };
  }

  let minDepth = 0;
  let maxDepth = 0;
  let maxLeaf = 0;
  for (const node of nodes) {
    minDepth = Math.min(minDepth, node.layoutX);
    maxDepth = Math.max(maxDepth, node.layoutX);
    maxLeaf = Math.max(maxLeaf, Math.abs(node.layoutY));
  }
  const depthSpan = maxDepth - minDepth + 1;
  const leafCount = maxLeaf + 1;
  const baseDepthGap = settings.depthGap;
  const baseSiblingGap = settings.siblingGap;
  const nodeR = settings.nodeRadius;
  const lr = settings.layoutDirection === 'LR';

  // Auto-expand sibling gap if labels would overlap vertically
  const maxLabelW = Math.max(
    ...nodes.map((n) => estimateLabelWidth(topologyNodeLabel(n))),
    0
  );
  const minSiblingGap = Math.max(baseSiblingGap, 2 * nodeR + MIN_NODE_GAP);
  const labelAdjustedSiblingGap = lr
    ? Math.max(minSiblingGap, maxLabelW + 2 * nodeR + MIN_LABEL_NODE_GAP)
    : minSiblingGap;

  const depthGap = baseDepthGap;
  const siblingGap = labelAdjustedSiblingGap;

  const margin = settings.labelMargin + nodeR;
  const rawW = depthSpan * depthGap;
  const rawH = Math.max(leafCount, 1) * siblingGap;
  const width = pad(rawW, margin);
  const height = pad(rawH, margin);
  const points = new Map<string, ScreenPoint>();

  for (const node of nodes) {
    const depthOffset = node.layoutX - minDepth;
    const depthPos = margin + depthOffset * depthGap + (lr ? nodeR : 0);
    const siblingPos = margin + (node.layoutY + maxLeaf) * siblingGap + nodeR;
    points.set(node.id, lr ? { x: depthPos, y: siblingPos } : { x: siblingPos, y: depthPos });
  }

  return {
    points,
    viewBox: `0 0 ${width} ${height}`,
    width,
    height,
    maxDepth: depthSpan,
    leafCount,
  };
}

const BRANCH_COLORS = ['#3b82f6', '#22c55e', '#f59e0b', '#ef4444'];

/** Double-ring star layout: inner ring = channel devices, outer ring = zero-channel. */
export function computeDoubleRingPixelLayout(
  nodes: TopologyNodeDto[],
  settings: Pick<TopologyDisplaySettings, 'depthGap' | 'labelMargin' | 'nodeRadius'>
): PixelLayoutResult & { branchColors: Map<string, string> } {
  if (nodes.length === 0) {
    return {
      points: new Map(),
      viewBox: '0 0 100 100',
      width: 100,
      height: 100,
      maxDepth: 0,
      leafCount: 0,
      branchColors: new Map(),
    };
  }

  const hub = nodes.find((n) => n.id === 'controller' || n.type === 'controller');
  const visible = nodes.filter(
    (n) =>
      n.type !== 'cell' &&
      n.id !== hub?.id &&
      !n.id.startsWith('virtual:') &&
      n.type !== 'cable_smart' &&
      n.type !== 'cable_dense'
  );

  const inner = visible.filter((n) => (n.channelCost ?? 0) > 0);
  const outer = visible.filter((n) => (n.channelCost ?? 0) <= 0);
  const nodeR = settings.nodeRadius;

  // Auto-scale ring radii so inner/outer devices don't overlap
  const innerR = safeRingRadius(inner.length, settings.depthGap * 0.65, nodeR, settings.depthGap * 2);
  const outerR = safeRingRadius(outer.length, settings.depthGap, nodeR, settings.depthGap * 2.5);
  // Ensure outer is at least inner + gap
  const minOuterFromInner = innerR + 2 * nodeR + MIN_NODE_GAP;
  const finalOuterR = Math.max(outerR, minOuterFromInner);

  const margin = settings.labelMargin + nodeR;
  const radius = finalOuterR + margin;
  const centerX = radius;
  const centerY = radius;
  const width = radius * 2;
  const height = radius * 2;

  const points = new Map<string, ScreenPoint>();
  const branchColors = new Map<string, string>();

  if (hub) {
    points.set(hub.id, { x: centerX, y: centerY });
  }

  const placeRing = (ringNodes: TopologyNodeDto[], r: number, preferTop: boolean) => {
    const count = ringNodes.length;
    for (let i = 0; i < count; i++) {
      const node = ringNodes[i];
      let angle: number;
      if (preferTop && node.layoutSector === 'north') {
        const topCount = ringNodes.filter((n) => n.layoutSector === 'north').length;
        const topIndex = ringNodes.filter((n) => n.layoutSector === 'north').indexOf(node);
        angle = topCount > 0 ? (-Math.PI / 3 + (topIndex * (2 * Math.PI / 3)) / Math.max(1, topCount - 1)) : -Math.PI / 2;
      } else if (node.layoutSector === 'south' || node.subtype?.startsWith('bus_')) {
        const southNodes = ringNodes.filter((n) => n.layoutSector === 'south' || n.subtype?.startsWith('bus_'));
        const southIndex = southNodes.indexOf(node);
        angle = (2 * Math.PI) / 3 + (southIndex * Math.PI) / Math.max(3, southNodes.length);
      } else {
        angle = count > 0 ? (2 * Math.PI * i) / count - Math.PI / 2 : 0;
      }
      points.set(node.id, {
        x: centerX + Math.cos(angle) * r,
        y: centerY + Math.sin(angle) * r,
      });
      if (node.branchIndex != null && node.branchIndex >= 0) {
        branchColors.set(node.id, BRANCH_COLORS[node.branchIndex % BRANCH_COLORS.length]);
      }
    }
  };

  placeRing(inner, innerR, true);
  placeRing(outer, finalOuterR, false);

  return {
    points,
    viewBox: `0 0 ${width} ${height}`,
    width,
    height,
    maxDepth: 2,
    leafCount: visible.length,
    branchColors,
  };
}

export function branchColorForIndex(index: number): string {
  return BRANCH_COLORS[index >= 0 ? index % BRANCH_COLORS.length : 0];
}

/** Spatial mode: world coords scaled to pixel space with padding. */
export function computeSpatialPixelLayout(
  nodes: TopologyNodeDto[],
  settings: Pick<TopologyDisplaySettings, 'labelMargin' | 'nodeRadius'>
): PixelLayoutResult {
  if (nodes.length === 0) {
    return { points: new Map(), viewBox: '0 0 100 100', width: 100, height: 100, maxDepth: 0, leafCount: 0 };
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

  const margin = settings.labelMargin + settings.nodeRadius;
  const spanX = Math.max(1, maxX - minX);
  const spanY = Math.max(1, maxY - minY);
  const scale = Math.min(800 / spanX, 600 / spanY);
  const width = pad(spanX * scale, margin);
  const height = pad(spanY * scale, margin);

  const points = new Map<string, ScreenPoint>();
  for (const node of nodes) {
    points.set(node.id, {
      x: margin + (node.layoutX - minX) * scale,
      y: margin + (node.layoutY - minY) * scale,
    });
  }

  return {
    points,
    viewBox: `0 0 ${width} ${height}`,
    width,
    height,
    maxDepth: 0,
    leafCount: nodes.length,
  };
}

export type LabelDetailLevel = 'icon' | 'abbrev' | 'full';

export function labelDetailLevel(scale: number): LabelDetailLevel {
  if (scale < 0.6) return 'icon';
  if (scale < 1.0) return 'abbrev';
  return 'full';
}

export function abbreviateLabel(name: string | null | undefined, maxLen = 14): string {
  const text = name?.trim() || '';
  if (!text || text.length <= maxLen) return text || '?';
  return `${text.slice(0, maxLen - 1)}…`;
}

/**
 * Nudge a label position to avoid overlapping with all other node circles.
 * Uses a simple repulsion: move along the radial direction from the node center.
 */
export function avoidLabelOverlap(
  labelX: number, labelY: number, labelWidth: number,
  ownNodeId: string,
  allNodes: NodeRect[]
): ScreenPoint {
  let bestX = labelX;
  let bestY = labelY;
  let bestPenalty = 0;

  // Try increasing offsets from the node position
  const offsets = [0, 4, 8, 12, 16, 20, 26, 32];
  for (const offset of offsets) {
    const testX = labelX + offset;
    const testY = labelY;
    let penalty = 0;
    for (const n of allNodes) {
      if (n.id === ownNodeId) continue;
      if (labelOverlapsNode(testX, testY, labelWidth, n.cx, n.cy, n.r)) {
        penalty += 1;
      }
    }
    if (penalty === 0) return { x: testX, y: testY };
    if (penalty <= bestPenalty || bestPenalty === 0) {
      bestPenalty = penalty;
      bestX = testX;
    }
  }

  return { x: bestX, y: bestY };
}

export function fitViewTransform(
  layoutWidth: number,
  layoutHeight: number,
  containerWidth: number,
  containerHeight: number
): { scale: number; panX: number; panY: number } {
  if (layoutWidth <= 0 || layoutHeight <= 0 || containerWidth <= 0 || containerHeight <= 0) {
    return { scale: 1, panX: 0, panY: 0 };
  }
  const scale = Math.min(
    MAX_SCALE,
    Math.max(MIN_SCALE, Math.min(containerWidth / layoutWidth, containerHeight / layoutHeight) * 0.92)
  );
  return { scale, panX: 0, panY: 0 };
}

function isSimBlockNode(node: TopologyNodeDto): boolean {
  if (node.type === 'cell' || node.simKind === 'hidden') return false;
  if (node.id.startsWith('virtual:') || node.id.startsWith('cable:')) return false;
  if (node.role === 'empty_branch') return false;
  return node.simKind === 'block' || node.simGridX != null;
}

/** Re-map simulated grid coords to double-ring star semantics (client-side layout toggle). */
export function remapSimNodesForStarLayout(nodes: TopologyNodeDto[]): TopologyNodeDto[] {
  const cloned = nodes.map((n) => ({ ...n }));
  const hub = cloned.find((n) => n.id === 'controller' || n.type === 'controller');
  const blocks = cloned.filter((n) => isSimBlockNode(n) && n.id !== hub?.id);

  const inner = blocks.filter((n) => (n.channelCost ?? 0) > 0);
  const outer = blocks.filter((n) => (n.channelCost ?? 0) <= 0);

  if (hub) {
    hub.simGridX = SIM_ORIGIN_X;
    hub.simGridY = SIM_ORIGIN_Y;
    hub.simKind = 'block';
  }

  const placeRing = (ringNodes: TopologyNodeDto[], radius: number) => {
    const count = ringNodes.length;
    for (let i = 0; i < count; i++) {
      const node = ringNodes[i];
      const angle = count > 0 ? (2 * Math.PI * i) / count - Math.PI / 2 : 0;
      node.simGridX = SIM_ORIGIN_X + Math.round(Math.cos(angle) * radius);
      node.simGridY = SIM_ORIGIN_Y + Math.round(Math.sin(angle) * radius);
      node.simKind = 'block';
    }
  };

  placeRing(inner, SIM_RING_INNER);
  placeRing(outer, SIM_RING_OUTER);
  return cloned;
}
