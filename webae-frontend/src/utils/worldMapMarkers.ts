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

function isMultiblockMarker(marker: WorldMapMarkerDto): boolean {
  return (
    marker.type === 'cpu' ||
    marker.subtype === 'cpu' ||
    marker.type === 'controller' ||
    marker.subtype === 'controller'
  );
}

function cpuAnchorPriority(marker: WorldMapMarkerDto): number {
  const hay = `${marker.displayName ?? ''} ${marker.subtype ?? ''} ${marker.type ?? ''}`.toLowerCase();
  if (hay.includes('monitor')) return 0;
  if (hay.includes('coprocessor') || hay.includes('accelerator')) return 1;
  if (hay.includes('storage')) return 2;
  return 3;
}

function controllerAnchorCompare(a: WorldMapMarkerDto, b: WorldMapMarkerDto): number {
  if (a.y !== b.y) return a.y - b.y;
  if (a.x !== b.x) return a.x - b.x;
  return a.z - b.z;
}

function pickMultiblockAnchor(group: WorldMapMarkerDto[]): WorldMapMarkerDto {
  const first = group[0];
  const isController =
    first.type === 'controller' ||
    first.subtype === 'controller';
  if (isController) {
    return group.slice().sort(controllerAnchorCompare)[0];
  }
  return group.slice().sort((a, b) => cpuAnchorPriority(a) - cpuAnchorPriority(b))[0];
}

/** Merge multi-block CPU/controller markers into one icon per node. */
export function consolidateMultiblockMarkers(markers: WorldMapMarkerDto[]): WorldMapMarkerDto[] {
  const groups = new Map<string, WorldMapMarkerDto[]>();
  const out: WorldMapMarkerDto[] = [];
  for (const marker of markers) {
    if (!isMultiblockMarker(marker)) {
      out.push(marker);
      continue;
    }
    const list = groups.get(marker.nodeId) ?? [];
    list.push(marker);
    groups.set(marker.nodeId, list);
  }
  for (const group of groups.values()) {
    if (group.length === 0) continue;
    out.push(pickMultiblockAnchor(group));
  }
  return out;
}

/** @deprecated Use {@link consolidateMultiblockMarkers} */
export function consolidateCpuMarkers(markers: WorldMapMarkerDto[]): WorldMapMarkerDto[] {
  return consolidateMultiblockMarkers(markers);
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

function isMultiblockNode(node: TopologyNodeDto): boolean {
  return (
    node.type === 'cpu' ||
    node.subtype === 'cpu' ||
    node.type === 'controller' ||
    node.subtype === 'controller'
  );
}

/** Client-side fallback flatten when markers API is unavailable. */
export function flattenMarkersFromSnapshot(snapshot: TopologySnapshotDto): WorldMapMarkerDto[] {
  const raw: WorldMapMarkerDto[] = [];
  for (const node of snapshot.nodes ?? []) {
    const devices = node.devices ?? [];
    if (isMultiblockNode(node) && devices.length > 0) {
      const isController = node.type === 'controller' || node.subtype === 'controller';
      const anchor = isController
        ? devices
            .map((device) => ({
              device,
              priority: device.y * 1_000_000 + device.x * 1_000 + device.z,
            }))
            .sort((a, b) => a.priority - b.priority)[0]?.device
        : devices
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
