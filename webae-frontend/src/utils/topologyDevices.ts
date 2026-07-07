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
  'misc',
  'spatial_bin',
] as const;

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
    .filter((node) => !isCableNode(node) && !isCellNode(node))
    .map((node) => ({
      key: node.id,
      node,
      isCable: false,
    }));
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
