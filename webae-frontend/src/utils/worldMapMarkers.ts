import type { TopologyNodeDto, TopologySnapshotDto, WorldMapMarkerDto } from '@/types/dto';
import type { TopologyDisplaySettings } from '@/types/topologyDisplay';
import { blockIconIdForNode } from '@/utils/aeCableColors';
import { resolveMarkerAeCategory } from '@/utils/worldMapAeCategories';

export interface ClusterIconSummary {
  iconId: string;
  count: number;
  label: string;
}

export const CLUSTER_TOOLTIP_MAX_ICON_TYPES = 10;

/** Group cluster markers by rendered icon id, sorted by count descending. */
export function summarizeClusterMarkersByIcon(markers: WorldMapMarkerDto[]): ClusterIconSummary[] {
  const groups = new Map<string, ClusterIconSummary>();
  for (const marker of markers) {
    const iconId = blockIconIdForNode(marker.type, marker.iconItemId);
    const existing = groups.get(iconId);
    if (existing) {
      existing.count += 1;
      continue;
    }
    groups.set(iconId, {
      iconId,
      count: 1,
      label: marker.displayName || marker.type,
    });
  }
  return Array.from(groups.values()).sort((a, b) => b.count - a.count);
}

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

function cpuAnchorPriority(marker: WorldMapMarkerDto): number {
  const hay = `${marker.displayName ?? ''} ${marker.subtype ?? ''} ${marker.type ?? ''}`.toLowerCase();
  if (hay.includes('monitor')) return 0;
  if (hay.includes('coprocessor') || hay.includes('accelerator')) return 1;
  if (hay.includes('storage')) return 2;
  return 3;
}

/** Merge multi-block crafting CPU markers into one icon per node (monitor > coprocessor > storage > first). */
export function consolidateCpuMarkers(markers: WorldMapMarkerDto[]): WorldMapMarkerDto[] {
  const cpuGroups = new Map<string, WorldMapMarkerDto[]>();
  const out: WorldMapMarkerDto[] = [];
  for (const marker of markers) {
    const isCpu = marker.type === 'cpu' || marker.subtype === 'cpu';
    if (!isCpu) {
      out.push(marker);
      continue;
    }
    const list = cpuGroups.get(marker.nodeId) ?? [];
    list.push(marker);
    cpuGroups.set(marker.nodeId, list);
  }
  for (const group of cpuGroups.values()) {
    if (group.length === 0) continue;
    const best = group.slice().sort((a, b) => cpuAnchorPriority(a) - cpuAnchorPriority(b))[0];
    out.push(best);
  }
  return out;
}

export function filterMarkersByCategoryVisibility(
  markers: WorldMapMarkerDto[],
  visibility: TopologyDisplaySettings['worldMapAeCategoryVisibility']
): WorldMapMarkerDto[] {
  return markers.filter((m) => {
    const cat = resolveMarkerAeCategory(m);
    return visibility[cat] !== false;
  });
}

/** Client-side fallback flatten when markers API is unavailable. */
export function flattenMarkersFromSnapshot(snapshot: TopologySnapshotDto): WorldMapMarkerDto[] {
  const raw: WorldMapMarkerDto[] = [];
  for (const node of snapshot.nodes ?? []) {
    const isCpu = node.type === 'cpu' || node.subtype === 'cpu';
    const devices = node.devices ?? [];
    if (isCpu && devices.length > 0) {
      const anchor = devices
        .map((device) => ({
          device,
          priority: cpuAnchorPriority({
            id: '',
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
          }),
        }))
        .sort((a, b) => a.priority - b.priority)[0]?.device;
      const device = anchor ?? devices[0];
      raw.push({
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
      continue;
    }
    for (const device of devices) {
      raw.push({
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
  return raw;
}

export function filterMarkersByDim(markers: WorldMapMarkerDto[], dim: number): WorldMapMarkerDto[] {
  return markers.filter((m) => m.dim === dim);
}

export function uniqueNodesFromMarkers(
  markers: WorldMapMarkerDto[],
  nodeIndex: Map<string, TopologyNodeDto>
): TopologyNodeDto[] {
  const seen = new Set<string>();
  const nodes: TopologyNodeDto[] = [];
  for (const marker of markers) {
    if (seen.has(marker.nodeId)) continue;
    seen.add(marker.nodeId);
    const node = nodeIndex.get(marker.nodeId);
    if (node) nodes.push(node);
  }
  return nodes;
}
