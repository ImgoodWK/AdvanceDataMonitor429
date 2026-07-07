import type { TopologyEdgeDto, TopologyNodeDto } from '@/types/dto';
import type { TopologyDisplaySettings } from '@/types/topologyDisplay';

export interface GridPoint {
  x: number;
  y: number;
}

export interface CableCell {
  gx: number;
  gy: number;
  cableType: string;
}

export interface CableSegment {
  x1: number;
  y1: number;
  x2: number;
  y2: number;
  orientation: 'h' | 'v';
  cableType: string;
  edgeKey: string;
}

export interface CornerCell {
  x: number;
  y: number;
  kind: 'tl' | 'tr' | 'bl' | 'br' | 'cross';
  cableType: string;
}

/** Higher rank wins when multiple cables share a grid cell (dense > covered > smart). */
const CABLE_TYPE_RANK: Record<string, number> = {
  smart: 1,
  covered: 2,
  dense: 3,
};

function pickHigherCableType(a: string, b: string): string {
  const rankA = CABLE_TYPE_RANK[a] ?? 0;
  const rankB = CABLE_TYPE_RANK[b] ?? 0;
  return rankA >= rankB ? a : b;
}

function manhattanPath(from: GridPoint, to: GridPoint, horizontalFirst: boolean): GridPoint[] {
  const points: GridPoint[] = [{ ...from }];
  if (horizontalFirst) {
    if (from.x !== to.x) points.push({ x: to.x, y: from.y });
  } else {
    if (from.y !== to.y) points.push({ x: from.x, y: to.y });
  }
  if (points[points.length - 1].x !== to.x || points[points.length - 1].y !== to.y) {
    points.push({ ...to });
  }
  return points;
}

/** Pick the Manhattan path variant that avoids existing occupied cells when possible. */
function pickPath(
  from: GridPoint,
  to: GridPoint,
  occupied: Set<string>
): GridPoint[] {
  const hFirst = manhattanPath(from, to, true);
  const vFirst = manhattanPath(from, to, false);

  // Score each path by how many intermediate cells are occupied
  const score = (path: GridPoint[]) => {
    let s = 0;
    for (let i = 1; i < path.length - 1; i++) {
      const k = `${path[i].x},${path[i].y}`;
      if (occupied.has(k)) s += 1;
    }
    return s;
  };

  const hScore = score(hFirst);
  const vScore = score(vFirst);
  return hScore <= vScore ? hFirst : vFirst;
}

function segmentKey(x1: number, y1: number, x2: number, y2: number): string {
  const ax = Math.min(x1, x2);
  const ay = Math.min(y1, y2);
  const bx = Math.max(x1, x2);
  const by = Math.max(y1, y2);
  return `${ax},${ay}-${bx},${by}`;
}

/** Enumerate grid cells along a manhattan path (inclusive). */
function cellsAlongPath(path: GridPoint[]): GridPoint[] {
  const cells: GridPoint[] = [];
  const seen = new Set<string>();
  for (let i = 0; i < path.length - 1; i++) {
    const a = path[i];
    const b = path[i + 1];
    if (a.x === b.x) {
      const step = a.y < b.y ? 1 : -1;
      for (let y = a.y; step > 0 ? y <= b.y : y >= b.y; y += step) {
        const k = `${a.x},${y}`;
        if (!seen.has(k)) {
          seen.add(k);
          cells.push({ x: a.x, y });
        }
      }
    } else {
      const step = a.x < b.x ? 1 : -1;
      for (let x = a.x; step > 0 ? x <= b.x : x >= b.x; x += step) {
        const k = `${x},${a.y}`;
        if (!seen.has(k)) {
          seen.add(k);
          cells.push({ x, y: a.y });
        }
      }
    }
  }
  return cells;
}

/** Build MC-style cable cells (one block per grid cell) from node sim grid coords. */
export function buildCableCells(
  nodes: TopologyNodeDto[],
  edges: TopologyEdgeDto[],
  hideCableNodes: boolean
): CableCell[] {
  const byId = new Map(nodes.map((n) => [n.id, n]));
  const cellMap = new Map<string, CableCell>();
  const blockCells = new Set<string>();
  const occupied = new Set<string>();

  for (const node of nodes) {
    if (node.simGridX == null || node.simGridY == null) continue;
    if (node.simKind === 'block' || node.simKind === 'junction' || node.simKind?.startsWith('cable')) {
      const key = `${node.simGridX},${node.simGridY}`;
      blockCells.add(key);
      occupied.add(key);
    }
  }

  for (const edge of edges) {
    const fromNode = byId.get(edge.from);
    const toNode = byId.get(edge.to);
    if (!fromNode || !toNode) continue;
    if (hideCableNodes && (fromNode.simKind?.startsWith('cable') || toNode.simKind?.startsWith('cable'))) {
      continue;
    }
    if (fromNode.simGridX == null || fromNode.simGridY == null || toNode.simGridX == null || toNode.simGridY == null) {
      continue;
    }

    const from: GridPoint = { x: fromNode.simGridX, y: fromNode.simGridY };
    const to: GridPoint = { x: toNode.simGridX, y: toNode.simGridY };
    const path = pickPath(from, to, occupied);
    const cableType = edge.cableType || 'covered';

    for (const cell of cellsAlongPath(path)) {
      const key = `${cell.x},${cell.y}`;
      if (blockCells.has(key)) continue;
      const existing = cellMap.get(key);
      if (existing) {
        existing.cableType = pickHigherCableType(existing.cableType, cableType);
      } else {
        cellMap.set(key, { gx: cell.x, gy: cell.y, cableType });
        occupied.add(key);
      }
    }
  }

  for (const node of nodes) {
    if (node.simGridX == null || node.simGridY == null) continue;
    if (node.simKind?.startsWith('cable') || node.simKind === 'junction') {
      const key = `${node.simGridX},${node.simGridY}`;
      if (!cellMap.has(key)) {
        const cableType =
          node.type === 'cable_dense' ? 'dense' : node.type === 'cable_smart' ? 'smart' : 'covered';
        cellMap.set(key, { gx: node.simGridX, gy: node.simGridY, cableType });
      }
    }
  }

  return Array.from(cellMap.values());
}

/** Legacy segment builder for abstract overlays. */
export function buildCableSegments(
  nodes: TopologyNodeDto[],
  edges: TopologyEdgeDto[],
  cellPx: number,
  hideCableNodes: boolean
): { segments: CableSegment[]; corners: CornerCell[] } {
  const byId = new Map(nodes.map((n) => [n.id, n]));
  const segmentMap = new Map<string, CableSegment>();
  const cornerMap = new Map<string, CornerCell>();
  const occupied = new Set<string>();

  for (const node of nodes) {
    if (node.simGridX != null && node.simGridY != null) {
      occupied.add(`${node.simGridX},${node.simGridY}`);
    }
  }

  for (const edge of edges) {
    const fromNode = byId.get(edge.from);
    const toNode = byId.get(edge.to);
    if (!fromNode || !toNode) continue;
    if (hideCableNodes && (fromNode.simKind?.startsWith('cable') || toNode.simKind?.startsWith('cable'))) {
      continue;
    }
    if (fromNode.simGridX == null || fromNode.simGridY == null || toNode.simGridX == null || toNode.simGridY == null) {
      continue;
    }

    const from: GridPoint = { x: fromNode.simGridX, y: fromNode.simGridY };
    const to: GridPoint = { x: toNode.simGridX, y: toNode.simGridY };
    const path = pickPath(from, to, occupied);
    const cableType = edge.cableType || 'covered';

    for (let i = 0; i < path.length - 1; i++) {
      const a = path[i];
      const b = path[i + 1];
      // Mark intermediate cells as occupied
      if (i > 0) {
        occupied.add(`${a.x},${a.y}`);
      }
      const px1 = a.x * cellPx + cellPx / 2;
      const py1 = a.y * cellPx + cellPx / 2;
      const px2 = b.x * cellPx + cellPx / 2;
      const py2 = b.y * cellPx + cellPx / 2;
      const key = segmentKey(px1, py1, px2, py2);
      if (!segmentMap.has(key)) {
        segmentMap.set(key, {
          x1: px1,
          y1: py1,
          x2: px2,
          y2: py2,
          orientation: a.y === b.y ? 'h' : 'v',
          cableType,
          edgeKey: `${edge.from}-${edge.to}`,
        });
      }

      if (i > 0) {
        const prev = path[i - 1];
        const cornerKind =
          prev.x < a.x && a.y < b.y
            ? 'br'
            : prev.x < a.x && a.y > b.y
              ? 'tr'
              : prev.x > a.x && a.y < b.y
                ? 'bl'
                : 'tl';
        const ck = `${a.x},${a.y}`;
        if (!cornerMap.has(ck)) {
          cornerMap.set(ck, { x: a.x * cellPx, y: a.y * cellPx, kind: cornerKind, cableType });
        }
      }
    }
  }

  return {
    segments: Array.from(segmentMap.values()),
    corners: Array.from(cornerMap.values()),
  };
}

export function simulatedViewBox(
  nodes: TopologyNodeDto[],
  cableCells: CableCell[],
  cellPx: number,
  blockPx: number,
  margin = 40
): string {
  margin = Math.max(margin, cellPx + blockPx);
  let minX = Infinity;
  let maxX = -Infinity;
  let minY = Infinity;
  let maxY = -Infinity;

  const consider = (px: number, py: number, w: number, h: number) => {
    minX = Math.min(minX, px);
    maxX = Math.max(maxX, px + w);
    minY = Math.min(minY, py);
    maxY = Math.max(maxY, py + h);
  };

  for (const n of nodes) {
    if (n.simGridX == null || n.simGridY == null || n.simKind === 'hidden') continue;
    if (n.simKind === 'cable_h' || n.simKind === 'cable_v') continue;
    consider(n.simGridX * cellPx, n.simGridY * cellPx, blockPx, blockPx + 16);
  }
  for (const c of cableCells) {
    consider(c.gx * cellPx, c.gy * cellPx, cellPx, cellPx);
  }

  if (!Number.isFinite(minX)) return '0 0 400 300';
  const w = maxX - minX + margin * 2;
  const h = maxY - minY + margin * 2;
  return `${minX - margin} ${minY - margin} ${w} ${h}`;
}
