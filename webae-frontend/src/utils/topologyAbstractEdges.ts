import type { TopologyEdgeDto, TopologyNodeDto } from '@/types/dto';
import { isCableNode, isCellNode } from '@/utils/topologyDevices';

const CABLE_TIER_RANK: Record<string, number> = {
  smart: 1,
  covered: 2,
  dense: 3,
};

/** Nodes that should never appear as clickable device circles in the abstract tree. */
export function visibleAbstractNodes(nodes: TopologyNodeDto[]): TopologyNodeDto[] {
  return nodes.filter(
    (n) =>
      !!n.id &&
      !isCableNode(n) &&
      !isCellNode(n) &&
      n.simKind !== 'junction' &&
      !n.simKind?.startsWith('cable') &&
      n.role !== 'empty_branch'
  );
}

function isCableLikeNode(node: TopologyNodeDto | undefined): boolean {
  if (!node) return false;
  return isCableNode(node) || node.simKind === 'junction' || !!node.simKind?.startsWith('cable');
}

function pickHigherTier(a: string | undefined, b: string | undefined): string {
  const rankA = CABLE_TIER_RANK[a ?? ''] ?? 0;
  const rankB = CABLE_TIER_RANK[b ?? ''] ?? 0;
  return rankA >= rankB ? (a ?? b ?? 'covered') : (b ?? a ?? 'covered');
}

/**
 * Collapse edges that pass through synthetic cable nodes so the abstract tree
 * draws direct lines between real devices while preserving cable tier coloring.
 */
export function collapseCableEdges(nodes: TopologyNodeDto[], edges: TopologyEdgeDto[]): TopologyEdgeDto[] {
  const byId = new Map(nodes.map((n) => [n.id, n]));
  const children = new Map<string, { to: string; cableType?: string }[]>();

  for (const edge of edges) {
    const list = children.get(edge.from) ?? [];
    list.push({ to: edge.to, cableType: edge.cableType });
    children.set(edge.from, list);
  }

  const visibleIds = new Set(visibleAbstractNodes(nodes).map((n) => n.id));
  const collapsed = new Map<string, TopologyEdgeDto>();

  function walk(fromId: string, startId: string, tier: string | undefined, visited: Set<string>) {
    const outs = children.get(fromId);
    if (!outs) return;
    for (const { to, cableType } of outs) {
      if (visited.has(to)) continue;
      const nextTier = pickHigherTier(tier, cableType);
      const target = byId.get(to);
      if (!target) continue;
      if (visibleIds.has(to)) {
        const key = `${startId}->${to}`;
        const existing = collapsed.get(key);
        if (existing) {
          existing.cableType = pickHigherTier(existing.cableType, nextTier);
        } else {
          const orig = edges.find((e) => e.from === fromId && e.to === to);
          collapsed.set(key, {
            from: startId,
            to,
            cableType: nextTier,
            channelsSimulated: orig?.channelsSimulated,
            channelsReal: orig?.channelsReal,
            pathPoints: orig?.pathPoints,
          });
        }
      } else if (isCableLikeNode(target)) {
        const nextVisited = new Set(visited);
        nextVisited.add(to);
        walk(to, startId, nextTier, nextVisited);
      }
    }
  }

  for (const id of visibleIds) {
    walk(id, id, undefined, new Set([id]));
  }

  // Also keep direct edges between two visible nodes (e.g. controller → drive).
  for (const edge of edges) {
    if (!visibleIds.has(edge.from) || !visibleIds.has(edge.to)) continue;
    const key = `${edge.from}->${edge.to}`;
    if (!collapsed.has(key)) {
      collapsed.set(key, { ...edge });
    }
  }

  // Preserve empty smart-branch placeholder edges (dashed lines in abstract view).
  for (const edge of edges) {
    if (!edge.emptyBranch) continue;
    const key = `empty:${edge.from}->${edge.to}`;
    if (!collapsed.has(key)) {
      collapsed.set(key, { ...edge });
    }
  }

  return Array.from(collapsed.values());
}
