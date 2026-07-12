import { AE_BLOCK_ICON_IDS } from '@/utils/aeCableColors';
import type { WorldMapAePlacementDto, WorldMapMarkerDto } from '@/types/dto';

/** AE world-map overlay category ids (R channel in server ID tiles). */
export type WorldMapAeCategoryId =
  | 'controller'
  | 'drive'
  | 'cell'
  | 'interface'
  | 'terminal'
  | 'cable'
  | 'energy'
  | 'p2p'
  | 'bus'
  | 'cpu'
  | 'other';

export const WORLD_MAP_AE_CATEGORY_IDS: WorldMapAeCategoryId[] = [
  'controller',
  'drive',
  'cell',
  'interface',
  'terminal',
  'cable',
  'energy',
  'p2p',
  'bus',
  'cpu',
  'other',
];

/** Server WorldMapAeCategory id → frontend key. */
export const WORLD_MAP_AE_CATEGORY_ID_BY_BYTE: Record<number, WorldMapAeCategoryId> = {
  1: 'controller',
  2: 'drive',
  3: 'cell',
  4: 'interface',
  5: 'terminal',
  6: 'cable',
  7: 'energy',
  8: 'p2p',
  9: 'bus',
  10: 'other',
  11: 'cpu',
};

/** Representative item/block icon per category (settings UI preview). */
export const WORLD_MAP_AE_CATEGORY_ICON_IDS: Record<WorldMapAeCategoryId, string> = {
  controller: AE_BLOCK_ICON_IDS.controller,
  drive: AE_BLOCK_ICON_IDS.drive,
  cell: 'appeng:item.ItemBasicStorageCell:16384',
  interface: AE_BLOCK_ICON_IDS.interface,
  terminal: 'appeng:item.ItemCraftingTerminal',
  cable: AE_BLOCK_ICON_IDS.cable_smart,
  energy: 'appeng:tile.BlockEnergyAcceptor',
  p2p: AE_BLOCK_ICON_IDS.p2p,
  bus: 'appeng:item.ItemMultiPart:220',
  cpu: 'appliedenergistics2:tile.BlockCraftingUnit',
  other: 'appeng:item.ItemMultiMaterial',
};

export const DEFAULT_WORLD_MAP_AE_CATEGORY_COLORS: Record<WorldMapAeCategoryId, string> = {
  controller: '#3a4a6a',
  drive: '#2a3344',
  cell: '#334455',
  interface: '#445566',
  terminal: '#556677',
  cable: '#6688aa',
  energy: '#446644',
  p2p: '#6a4a7a',
  bus: '#555566',
  cpu: '#6a5a44',
  other: '#8899aa',
};

export function resolveCategoryIdFromPixel(r: number): WorldMapAeCategoryId {
  return WORLD_MAP_AE_CATEGORY_ID_BY_BYTE[r] ?? 'other';
}

function containsAny(hay: string, needles: string[]): boolean {
  if (!hay) return false;
  for (const needle of needles) {
    if (needle && hay.includes(needle)) return true;
  }
  return false;
}

/** Mirrors server {@code WorldMapAeCategory.resolve}. */
export function resolveWorldMapAeCategory(placement: Pick<
  WorldMapAePlacementDto,
  'kind' | 'className' | 'iconItemId' | 'displayName'
>): WorldMapAeCategoryId {
  if (!placement) return 'other';
  if (placement.kind === 'cable') return 'cable';
  if (placement.kind === 'part') return 'bus';

  const icon = (placement.iconItemId ?? '').toLowerCase();
  const cls = (placement.className ?? '').toLowerCase();
  const name = (placement.displayName ?? '').toLowerCase();
  const hay = `${icon} ${cls} ${name}`;

  if (containsAny(hay, ['controller'])) return 'controller';
  if (containsAny(hay, ['me_drive', 'iodrive', 'drive'])) return 'drive';
  if (
    containsAny(hay, [
      'craftingcpu',
      'craftingtile',
      'blockcrafting',
      'crafting co-processor',
      'crafting storage',
      'crafting monitor',
      'crafting unit',
      'coprocessor',
      'accelerator',
    ])
  ) {
    return 'cpu';
  }
  if (containsAny(hay, ['cell', 'storage', 'chest'])) return 'cell';
  if (containsAny(hay, ['interface'])) return 'interface';
  if (containsAny(hay, ['terminal', 'monitor', 'pattern'])) return 'terminal';
  if (containsAny(hay, ['p2p', 'quantum'])) return 'p2p';
  if (containsAny(hay, ['energy', 'vibration', 'charger', 'crank'])) return 'energy';
  if (containsAny(hay, ['cable', 'glass', 'covered', 'smart', 'dense'])) return 'cable';
  if (containsAny(hay, ['bus', 'facade', 'part'])) return 'bus';
  return 'other';
}

/** Map a world-map device marker to an AE overlay category (for legend filter + icon tint). */
export function resolveMarkerAeCategory(
  marker: Pick<WorldMapMarkerDto, 'type' | 'subtype' | 'iconItemId' | 'displayName'>
): WorldMapAeCategoryId {
  const type = (marker.type ?? '').toLowerCase();
  if (type === 'cpu' || (marker.subtype ?? '').toLowerCase() === 'cpu') {
    return 'cpu';
  }
  if (type.includes('cable')) {
    return 'cable';
  }
  const subtype = (marker.subtype ?? '').toLowerCase();
  const kind =
    subtype.startsWith('bus_') || type === 'bus' || type === 'part' ? 'part' : 'block';
  return resolveWorldMapAeCategory({
    kind,
    className: marker.subtype || marker.type,
    iconItemId: marker.iconItemId,
    displayName: marker.displayName,
  });
}

export function markerStyleFromCategory(color: string): { borderColor: string; background: string } {
  return {
    borderColor: color,
    background: `color-mix(in srgb, ${color} 32%, var(--bg-secondary))`,
  };
}

export function groupIconIdsByAeCategory(
  placements: WorldMapAePlacementDto[]
): Record<WorldMapAeCategoryId, string[]> {
  const buckets = Object.fromEntries(
    WORLD_MAP_AE_CATEGORY_IDS.map((id) => [id, new Set<string>()])
  ) as Record<WorldMapAeCategoryId, Set<string>>;

  for (const placement of placements) {
    const iconId = placement.iconItemId?.trim();
    if (!iconId) continue;
    buckets[resolveWorldMapAeCategory(placement)].add(iconId);
  }

  return Object.fromEntries(
    WORLD_MAP_AE_CATEGORY_IDS.map((id) => [id, Array.from(buckets[id]).sort()])
  ) as Record<WorldMapAeCategoryId, string[]>;
}
