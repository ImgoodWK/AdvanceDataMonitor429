import type { TopologyNodeDto, TopologySnapshotDto, WorldMapMarkerDto } from '@/types/dto';

/** Build a nodeId → node lookup map from a logical topology snapshot. */
export function buildNodeIndex(nodes: TopologyNodeDto[]): Map<string, TopologyNodeDto> {
  const map = new Map<string, TopologyNodeDto>();
  for (const node of nodes) {
    if (node?.id) map.set(node.id, node);
  }
  return map;
}

export function nodeById(index: Map<string, TopologyNodeDto>, nodeId: string): TopologyNodeDto | null {
  return index.get(nodeId) ?? null;
}

/** Client-side fallback flatten when markers API is unavailable. */
export function flattenMarkersFromSnapshot(snapshot: TopologySnapshotDto): WorldMapMarkerDto[] {
  const out: WorldMapMarkerDto[] = [];
  for (const node of snapshot.nodes ?? []) {
    for (const device of node.devices ?? []) {
      out.push({
        id: `${device.dim}:${device.x}:${device.y}:${device.z}`,
        nodeId: node.id,
        type: node.type,
        subtype: node.subtype,
        displayName: device.displayName || node.displayName || node.type,
        iconItemId: device.iconItemId || node.iconItemId || '',
        x: device.x,
        y: device.y,
        z: device.z,
        dim: device.dim,
        channelCost: device.channelCost ?? node.channelCost ?? 0,
      });
    }
  }
  return out;
}

export function filterMarkersByDim(markers: WorldMapMarkerDto[], dim: number): WorldMapMarkerDto[] {
  return markers.filter((m) => m.dim === dim);
}
