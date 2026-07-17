import type { TopologyEdgeDto, TopologyNodeDto } from '@/types/dto';
import { isCellNode } from '@/utils/topologyDevices';

/** Structural spine layers that must stay visible in channel-lane topology. */
const SPINE_LAYERS = new Set(['hub', 'trunk', 'lane', 'pod']);

function isSpineStructural(node: TopologyNodeDto): boolean {
  const layer = node.layer ?? '';
  return (
    layer === 'trunk' ||
    layer === 'lane' ||
    node.role === 'trunk' ||
    node.role === 'lane' ||
    node.type === 'cable_dense' ||
    node.type === 'cable_smart'
  );
}

/**
 * Nodes shown in the ae_budget_v2 abstract graph.
 * Trunk / lane / pod stay visible by default; cells stay hidden.
 */
export function visibleAbstractNodes(
  nodes: TopologyNodeDto[],
  options?: { showEmptyLanes?: boolean; collapsedPodIds?: Set<string>; hideSpine?: boolean }
): TopologyNodeDto[] {
  const collapsedPods = options?.collapsedPodIds;
  const hideSpine = options?.hideSpine === true;

  return nodes.filter((n) => {
    if (!n.id || isCellNode(n)) return false;
    if (n.role === 'empty_branch') return false;
    if (hideSpine && isSpineStructural(n)) return false;

    const layer = n.layer ?? '';
    if (layer === 'lane' || n.role === 'lane' || n.type === 'cable_smart') {
      return true;
    }
    if (layer === 'trunk' || n.role === 'trunk' || n.type === 'cable_dense') {
      return true;
    }
    if (layer === 'pod' || n.type === 'pod' || n.role === 'pod') {
      return true;
    }
    if (collapsedPods && n.parentId && collapsedPods.has(n.parentId)) {
      return false;
    }
    if (SPINE_LAYERS.has(layer)) return true;
    return true;
  });
}

/**
 * Keep capacity-spine edges intact when spine nodes are visible.
 * When spine is hidden, walk through trunk/lane so pods/devices stay connected to the hub.
 */
export function capacitySpineEdges(nodes: TopologyNodeDto[], edges: TopologyEdgeDto[]): TopologyEdgeDto[] {
  const visibleIds = new Set(nodes.map((n) => n.id));
  const children = new Map<string, TopologyEdgeDto[]>();
  for (const edge of edges) {
    const list = children.get(edge.from) ?? [];
    list.push(edge);
    children.set(edge.from, list);
  }

  const out = new Map<string, TopologyEdgeDto>();

  function walk(fromId: string, startId: string, inherited: TopologyEdgeDto | null, visited: Set<string>) {
    const outs = children.get(fromId);
    if (!outs) return;
    for (const edge of outs) {
      if (visited.has(edge.to)) continue;
      const nextVisited = new Set(visited);
      nextVisited.add(edge.to);
      if (visibleIds.has(edge.to)) {
        const key = `${startId}->${edge.to}`;
        if (!out.has(key)) {
          out.set(key, {
            ...edge,
            from: startId,
            to: edge.to,
            kind: edge.kind || inherited?.kind,
            branchIndex: edge.branchIndex ?? inherited?.branchIndex,
            overflow: !!(edge.overflow || inherited?.overflow),
            channelsSimulated: edge.channelsSimulated ?? inherited?.channelsSimulated,
          });
        }
      } else {
        // Continue through hidden spine nodes (trunk/lane when hideSpine).
        walk(edge.to, startId, edge, nextVisited);
      }
    }
  }

  for (const id of visibleIds) {
    walk(id, id, null, new Set([id]));
  }

  for (const edge of edges) {
    if (!visibleIds.has(edge.from) || !visibleIds.has(edge.to)) continue;
    const key = `${edge.from}->${edge.to}`;
    if (!out.has(key)) out.set(key, { ...edge });
  }

  return Array.from(out.values());
}

/** @deprecated Alias kept for TopologyGraphSvg. */
export function collapseCableEdges(nodes: TopologyNodeDto[], edges: TopologyEdgeDto[]): TopologyEdgeDto[] {
  return capacitySpineEdges(visibleAbstractNodes(nodes), edges);
}
