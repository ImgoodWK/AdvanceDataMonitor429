import type { TopologyDeviceRecordDto, TopologyNodeDto } from '@/types/dto';

export interface FlatDeviceRow {
  key: string;
  nodeId: string;
  nodeType: string;
  nodeDisplayName: string;
  device: TopologyDeviceRecordDto;
}

export interface DeviceListNodeRow {
  key: string;
  node: TopologyNodeDto;
  isCable: boolean;
}

export interface DeviceTypeGroup<T> {
  type: string;
  rows: T[];
}

const CABLE_TYPES = new Set(['cable_smart', 'cable_dense', 'cable_covered']);

/** Role-pod kinds for ae_budget_v2 channel-lane topology. */
export const POD_KIND_ORDER = [
  'access',
  'io',
  'craft',
  'sense',
  'tunnel',
  'storage0',
  'craft0',
  'link0',
  'power0',
  'misc',
] as const;

export type PodKindId = (typeof POD_KIND_ORDER)[number];

export const POD_KIND_LABEL_KEYS: Record<PodKindId, string> = {
  access: 'topologyPod_access',
  io: 'topologyPod_io',
  craft: 'topologyPod_craft',
  sense: 'topologyPod_sense',
  tunnel: 'topologyPod_tunnel',
  storage0: 'topologyPod_storage0',
  craft0: 'topologyPod_craft0',
  link0: 'topologyPod_link0',
  power0: 'topologyPod_power0',
  misc: 'topologyPod_misc',
};

export function resolvePodKind(node: Pick<TopologyNodeDto, 'podKind' | 'subtype' | 'type'>): string {
  if (node.podKind) return node.podKind;
  const subtype = node.subtype || node.type || '';
  if (subtype.startsWith('terminal_') || subtype === 'wireless_access_point' || subtype === 'security_terminal') {
    return 'access';
  }
  if (subtype.startsWith('bus_') || subtype === 'level_maintainer') return 'io';
  if (subtype === 'interface' || subtype === 'pattern_provider') return 'craft';
  if (subtype.startsWith('monitor_') || subtype.startsWith('emitter_')) return 'sense';
  if (subtype.startsWith('p2p_')) return 'tunnel';
  if (subtype === 'drive' || subtype === 'chest' || subtype === 'io_port') return 'storage0';
  if (subtype === 'cpu') return 'craft0';
  if (subtype === 'quantum') return 'link0';
  if (subtype === 'energy_cell' || subtype === 'energy_acceptor' || subtype === 'energy') return 'power0';
  return 'misc';
}

export function isSpineNode(node: TopologyNodeDto): boolean {
  const layer = node.layer ?? '';
  return (
    layer === 'trunk' ||
    layer === 'lane' ||
    layer === 'pod' ||
    node.type === 'cable_dense' ||
    node.type === 'cable_smart' ||
    node.type === 'pod' ||
    node.role === 'trunk' ||
    node.role === 'lane' ||
    node.role === 'pod'
  );
}

/** Fixed display order for device-type collapse groups. */
export const DEVICE_TYPE_ORDER = [
  'controller',
  'energy_cell',
  'energy_acceptor',
  'drive',
  'chest',
  'io_port',
  'terminal_me',
  'terminal_crafting',
  'terminal_pattern_encoding',
  'terminal_pattern_access',
  'terminal_wireless',
  'wireless_access_point',
  'security_terminal',
  'terminal_other',
  'terminal',
  'bus_import',
  'bus_export',
  'bus_storage',
  'bus_ore_filter',
  'bus',
  'interface',
  'pattern_provider',
  'monitor_storage',
  'monitor_conversion',
  'emitter_level',
  'emitter_energy',
  'monitor',
  'cpu',
  'p2p_me',
  'p2p_item',
  'p2p_fluid',
  'p2p_power',
  'p2p_light',
  'p2p_other',
  'p2p',
  'quantum',
  'energy',
  'level_maintainer',
  'misc',
  'spatial_bin',
] as const;

/** Subtypes that show in world-map popup list and open a detail drawer. */
export const DETAIL_PAGE_SUBTYPES = new Set<string>([
  'interface',
  'drive',
  'cpu',
  'bus_storage',
  'bus_import',
  'bus_export',
  'chest',
  'security_terminal',
  'level_maintainer',
  'controller',
  'io_port',
  'quantum',
  'energy_cell',
  'energy_acceptor',
  'p2p_me',
  'p2p_item',
  'p2p_fluid',
  'p2p_power',
  'p2p_light',
  'p2p_other',
]);

export function nodeHasDetailPage(node: Pick<TopologyNodeDto, 'type' | 'subtype'>): boolean {
  return DETAIL_PAGE_SUBTYPES.has(resolveGroupType(node as TopologyNodeDto));
}

export function filterNodesWithDetailPage(nodes: TopologyNodeDto[]): TopologyNodeDto[] {
  return nodes.filter(nodeHasDetailPage);
}

/** Safe label for topology nodes — API may serialize null displayName (Gson serializeNulls). */
export function topologyNodeLabel(node: Pick<TopologyNodeDto, 'displayName' | 'type' | 'id' | 'subtype'>): string {
  const name = node.displayName?.trim();
  if (name) return name;
  const subtype = node.subtype?.trim();
  if (subtype) return subtype;
  if (node.type) return node.type;
  if (node.id) return node.id;
  return '?';
}

export function isCableNode(node: TopologyNodeDto): boolean {
  return (
    CABLE_TYPES.has(node.type) ||
    (node.id != null && node.id.startsWith('cable-')) ||
    node.simKind === 'junction' ||
    !!node.simKind?.startsWith('cable')
  );
}

export function isCellNode(node: TopologyNodeDto): boolean {
  return node.type === 'cell';
}

function isCableClassName(className?: string): boolean {
  if (!className) return false;
  return /Cable|GridBlock|GridNode|MultiblockNode/i.test(className);
}

/** True when a physical-device row represents a cable block and should be hidden. */
export function isCableDeviceRow(row: FlatDeviceRow): boolean {
  if (CABLE_TYPES.has(row.nodeType) || row.nodeId.startsWith('cable-')) return true;
  return isCableClassName(row.device.className);
}

export function flattenDevices(nodes: TopologyNodeDto[]): FlatDeviceRow[] {
  const rows: FlatDeviceRow[] = [];
  for (const node of nodes) {
    if (isCableNode(node) || isCellNode(node)) continue;
    for (let i = 0; i < (node.devices?.length ?? 0); i++) {
      const device = node.devices![i];
      if (isCableClassName(device.className)) continue;
      rows.push({
        key: `${node.id}:${i}:${device.x},${device.y},${device.z}`,
        nodeId: node.id,
        nodeType: node.subtype || node.type,
        nodeDisplayName: topologyNodeLabel(node),
        device,
      });
    }
  }
  return rows;
}

export function filterDeviceRows(rows: FlatDeviceRow[], query: string): FlatDeviceRow[] {
  let out = rows.filter((r) => !isCableDeviceRow(r));
  const q = query.trim().toLowerCase();
  if (!q) return out;
  return out.filter((row) => {
    const d = row.device;
    const hay = [
      row.nodeDisplayName,
      row.nodeType,
      d.displayName,
      d.className,
      `${d.x},${d.y},${d.z}`,
    ]
      .filter(Boolean)
      .join(' ')
      .toLowerCase();
    return hay.includes(q);
  });
}

export function filterNodeRows(rows: DeviceListNodeRow[], query: string, _hideCable: boolean): DeviceListNodeRow[] {
  let out = rows.filter((r) => !r.isCable && !isCellNode(r.node));
  const q = query.trim().toLowerCase();
  if (!q) return out;
  return out.filter((r) => {
    const n = r.node;
    const hay = [topologyNodeLabel(n), n.type, n.id, String(n.count)].join(' ').toLowerCase();
    return hay.includes(q);
  });
}

export function buildNodeListRows(nodes: TopologyNodeDto[]): DeviceListNodeRow[] {
  return nodes
    .filter((node) => !isCableNode(node) && !isCellNode(node) && node.type !== 'pod' && node.role !== 'pod')
    .map((node) => ({
      key: node.id,
      node,
      isCable: false,
    }));
}

export function groupRowsByPodKind<T extends { node?: TopologyNodeDto; nodeType?: string }>(
  rows: T[]
): DeviceTypeGroup<T>[] {
  const buckets = new Map<string, T[]>();
  for (const row of rows) {
    const kind = row.node ? resolvePodKind(row.node) : 'misc';
    const list = buckets.get(kind) ?? [];
    list.push(row);
    buckets.set(kind, list);
  }
  const ordered: DeviceTypeGroup<T>[] = [];
  for (const type of POD_KIND_ORDER) {
    const list = buckets.get(type);
    if (list && list.length > 0) {
      ordered.push({ type, rows: list });
      buckets.delete(type);
    }
  }
  for (const [type, list] of buckets) {
    if (list.length > 0) ordered.push({ type, rows: list });
  }
  return ordered;
}

export function resolveGroupType(node: TopologyNodeDto): string {
  return node.subtype || node.type || 'misc';
}

export function groupRowsByDeviceType<T extends { nodeType?: string; node?: TopologyNodeDto }>(
  rows: T[]
): DeviceTypeGroup<T>[] {
  const buckets = new Map<string, T[]>();
  for (const row of rows) {
    const type = row.nodeType ?? row.node?.subtype ?? row.node?.type ?? 'misc';
    const list = buckets.get(type) ?? [];
    list.push(row);
    buckets.set(type, list);
  }
  const ordered: DeviceTypeGroup<T>[] = [];
  for (const type of DEVICE_TYPE_ORDER) {
    const list = buckets.get(type);
    if (list && list.length > 0) {
      ordered.push({ type, rows: list });
      buckets.delete(type);
    }
  }
  for (const [type, list] of buckets) {
    if (list.length > 0) ordered.push({ type, rows: list });
  }
  return ordered;
}
