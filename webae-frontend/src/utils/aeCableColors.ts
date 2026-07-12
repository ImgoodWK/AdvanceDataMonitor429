/**
 * AE2 cable color presets (Fluix + 15 dye colors) — matches in-game ME cable tint order.
 * Icon meta values follow GTNH AE2 ItemMultiPart damage for covered/smart/dense parts.
 */

export type AeCableColorId =
  | 'fluix'
  | 'iron'
  | 'gold'
  | 'diamond'
  | 'emerald'
  | 'nether_quartz'
  | 'redstone'
  | 'cobblestone'
  | 'stone'
  | 'glowstone'
  | 'nether_brick'
  | 'black'
  | 'brown'
  | 'blue'
  | 'lime'
  | 'orange';

export interface AeCableColorPreset {
  id: AeCableColorId;
  /** Display hex for abstract tree edges. */
  hex: string;
  /** AE2 ItemMultiPart damage offset for covered cable (Fluix base ≈ 140). */
  coveredMeta: number;
  /** Smart cable damage offset (Fluix base ≈ 120). */
  smartMeta: number;
  /** Dense covered damage offset (Fluix base ≈ 92). */
  denseMeta: number;
}

/** AE2 standard 16 cable colors in game order. */
export const AE_CABLE_COLORS: AeCableColorPreset[] = [
  { id: 'fluix', hex: '#8b5cf6', coveredMeta: 140, smartMeta: 120, denseMeta: 92 },
  { id: 'iron', hex: '#d8d8d8', coveredMeta: 141, smartMeta: 121, denseMeta: 93 },
  { id: 'gold', hex: '#f0c020', coveredMeta: 142, smartMeta: 122, denseMeta: 94 },
  { id: 'diamond', hex: '#7ec8e8', coveredMeta: 143, smartMeta: 123, denseMeta: 95 },
  { id: 'emerald', hex: '#4caf50', coveredMeta: 144, smartMeta: 124, denseMeta: 95 },
  { id: 'nether_quartz', hex: '#ececec', coveredMeta: 145, smartMeta: 125, denseMeta: 96 },
  { id: 'redstone', hex: '#c62828', coveredMeta: 146, smartMeta: 126, denseMeta: 97 },
  { id: 'cobblestone', hex: '#7a7a7a', coveredMeta: 147, smartMeta: 127, denseMeta: 98 },
  { id: 'stone', hex: '#5a5a5a', coveredMeta: 148, smartMeta: 128, denseMeta: 99 },
  { id: 'glowstone', hex: '#ffcc33', coveredMeta: 149, smartMeta: 129, denseMeta: 100 },
  { id: 'nether_brick', hex: '#4a2020', coveredMeta: 150, smartMeta: 130, denseMeta: 101 },
  { id: 'black', hex: '#1a1a1a', coveredMeta: 151, smartMeta: 131, denseMeta: 102 },
  { id: 'brown', hex: '#6d4c2e', coveredMeta: 152, smartMeta: 132, denseMeta: 103 },
  { id: 'blue', hex: '#1565c0', coveredMeta: 153, smartMeta: 133, denseMeta: 104 },
  { id: 'lime', hex: '#8bc34a', coveredMeta: 154, smartMeta: 134, denseMeta: 105 },
  { id: 'orange', hex: '#ef6c00', coveredMeta: 155, smartMeta: 135, denseMeta: 106 },
];

export const DEFAULT_AE_CABLE_COLOR_ID: AeCableColorId = 'fluix';

export function findAeCableColor(id: AeCableColorId | string | undefined): AeCableColorPreset {
  return AE_CABLE_COLORS.find((c) => c.id === id) ?? AE_CABLE_COLORS[0];
}

export function hexFromAeCableColorId(id: AeCableColorId | string | undefined): string {
  return findAeCableColor(id).hex;
}

/** Build item-damage icon id for AE cable (legacy — item form, rendered as flat inventory item). */
export function aeCableIconId(
  cableType: 'smart' | 'covered' | 'dense',
  colorId: AeCableColorId | string | undefined
): string {
  const preset = findAeCableColor(colorId);
  const meta =
    cableType === 'dense' ? preset.denseMeta : cableType === 'smart' ? preset.smartMeta : preset.coveredMeta;
  return `appeng:item.ItemMultiPart:${meta}`;
}

/** Build block-tile icon id for AE cable bus texture — simulates block face (not flat item). */
export function aeCableBlockIconId(
  cableType: 'smart' | 'covered' | 'dense',
  colorId: AeCableColorId | string | undefined
): string {
  const preset = findAeCableColor(colorId);
  const meta =
    cableType === 'dense' ? preset.denseMeta : cableType === 'smart' ? preset.smartMeta : preset.coveredMeta;
  // BlockCableBus uses the same ItemBlock damage values for cable colors
  return `appeng:tile.BlockCableBus:${meta}`;
}

/** Block texture icon ids for simulated topology devices. */
export const AE_BLOCK_ICON_IDS: Record<string, string> = {
  controller: 'appeng:tile.BlockController',
  drive: 'appeng:tile.BlockDrive',
  interface: 'appeng:tile.BlockInterface',
  cpu: 'appliedenergistics2:tile.BlockCraftingUnit',
  p2p: 'appeng:item.ItemMultiPart',
  quantum: 'appeng:tile.BlockQuantumLinkChamber',
  cable_dense: 'appeng:tile.BlockCableBus',
  cable_smart: 'appeng:tile.BlockCableBus',
  cable_covered: 'appeng:tile.BlockCableBus',
};

/** Crafting CPU multiblock part icons (GTNH AE2 item registry names / NESQL cache). */
export const AE_CPU_COMPONENT_ICON_IDS = {
  storage: 'appliedenergistics2:tile.BlockCraftingUnit',
  monitor: 'appliedenergistics2:tile.BlockCraftingMonitor',
  accelerator: 'appliedenergistics2:tile.BlockCraftingAccelerator',
  /** Generic crafting block when type is unknown. */
  unit: 'appliedenergistics2:tile.BlockCraftingUnit',
} as const;

export type AeCpuComponentKind = keyof typeof AE_CPU_COMPONENT_ICON_IDS;

export function blockIconIdForNode(nodeType: string, iconItemId?: string): string {
  if (iconItemId) return iconItemId;
  switch (nodeType) {
    case 'controller':
      return AE_BLOCK_ICON_IDS.controller;
    case 'drive':
      return AE_BLOCK_ICON_IDS.drive;
    case 'interface':
      return AE_BLOCK_ICON_IDS.interface;
    case 'cpu':
      return AE_BLOCK_ICON_IDS.cpu;
    case 'p2p':
      return AE_BLOCK_ICON_IDS.p2p;
    case 'quantum':
      return AE_BLOCK_ICON_IDS.quantum;
    case 'cable_dense':
      return AE_BLOCK_ICON_IDS.cable_dense;
    case 'cable_smart':
      return AE_BLOCK_ICON_IDS.cable_smart;
    default:
      return 'appeng:item.ItemMultiMaterial';
  }
}
