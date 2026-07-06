import type { TopologyNodeDto } from '@/types/dto';
import type { TopologyDisplaySettings } from '@/types/topologyDisplay';

export interface ScreenPoint {
  x: number;
  y: number;
}

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

function pad(value: number, margin: number): number {
  return value + margin * 2;
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
  const radius = settings.depthGap;
  const margin = settings.labelMargin + settings.nodeRadius;
  const centerX = radius + margin;
  const centerY = radius + margin;
  const width = (radius + margin) * 2;
  const height = (radius + margin) * 2;

  const points = new Map<string, ScreenPoint>();
  if (hub) {
    points.set(hub.id, { x: centerX, y: centerY });
  }

  const count = satellites.length;
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

/** Pixel-coordinate tree layout for logical mode (replaces 0–100 compression). */
export function computeTreePixelLayout(
  nodes: TopologyNodeDto[],
  settings: Pick<TopologyDisplaySettings, 'layoutDirection' | 'depthGap' | 'siblingGap' | 'labelMargin' | 'nodeRadius'>
): PixelLayoutResult {
  if (nodes.length === 0) {
    return { points: new Map(), viewBox: '0 0 100 100', width: 100, height: 100, maxDepth: 0, leafCount: 0 };
  }

  let maxDepth = 0;
  let maxLeaf = 0;
  for (const node of nodes) {
    maxDepth = Math.max(maxDepth, node.layoutX);
    maxLeaf = Math.max(maxLeaf, node.layoutY);
  }
  const leafCount = maxLeaf + 1;
  const depthCount = maxDepth + 1;

  const depthGap = settings.depthGap;
  const siblingGap = settings.siblingGap;
  const margin = settings.labelMargin + settings.nodeRadius;

  const rawW = depthCount * depthGap;
  const rawH = leafCount * siblingGap;
  const width = pad(rawW, margin);
  const height = pad(rawH, margin);

  const points = new Map<string, ScreenPoint>();
  const lr = settings.layoutDirection === 'LR';

  for (const node of nodes) {
    const depthPos = margin + node.layoutX * depthGap + (lr ? settings.nodeRadius : 0);
    const siblingPos = margin + node.layoutY * siblingGap + settings.nodeRadius;
    points.set(node.id, lr ? { x: depthPos, y: siblingPos } : { x: siblingPos, y: depthPos });
  }

  return {
    points,
    viewBox: `0 0 ${width} ${height}`,
    width,
    height,
    maxDepth: depthCount,
    leafCount,
  };
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

export function abbreviateLabel(name: string, maxLen = 14): string {
  if (name.length <= maxLen) return name;
  return `${name.slice(0, maxLen - 1)}…`;
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
