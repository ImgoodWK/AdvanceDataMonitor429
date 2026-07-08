import type { AeCableColorId } from '@/utils/aeCableColors';
import { DEFAULT_AE_CABLE_COLOR_ID, hexFromAeCableColorId } from '@/utils/aeCableColors';
import {
  DEFAULT_WORLD_MAP_AE_CATEGORY_COLORS,
  type WorldMapAeCategoryId,
} from '@/utils/worldMapAeCategories';

export type TopologyLayoutDirection = 'LR' | 'TB';
export type TopologyRenderMode = 'abstract' | 'simulated';
export type TopologyAbstractLayout = 'tree' | 'star';
export type TopologyLabelStrategy = 'external' | 'below' | 'hover';

export type WorldMapObliqueDirection = 'se' | 'sw' | 'ne' | 'nw';
export type WorldMapQualityTierId = 'low' | 'medium' | 'high' | 'ultra';

export interface TopologyDisplaySettings {
  /** Abstract vs simulated cable-bus view (logical mode only). */
  renderMode: TopologyRenderMode;
  /** Tree vs double-ring star abstract layout (logical mode only). */
  abstractLayout: TopologyAbstractLayout;
  layoutDirection: TopologyLayoutDirection;
  depthGap: number;
  siblingGap: number;
  labelMargin: number;
  nodeRadius: number;
  labelStrategy: TopologyLabelStrategy;
  showCountLabels: boolean;
  showEdgeChannelLabels: boolean;
  hideCableNodes: boolean;
  /** Simulated view grid cell size (px). */
  cableCellPx: number;
  nodeBlockPx: number;
  colors: {
    smart: string;
    covered: string;
    dense: string;
    nodeStroke: string;
    nodeFill: string;
    label: string;
    labelDim: string;
  };
  /** AE2 cable tint per tier (drives icon meta + abstract edge color). */
  cableColorPreset: {
    smart: AeCableColorId;
    covered: AeCableColorId;
    dense: AeCableColorId;
  };
  /** Oblique world map orbit direction (mineshot-style). */
  worldMapObliqueDirection: WorldMapObliqueDirection;
  /** World map tile quality tier (clamped by server max on apply). */
  worldMapQuality: WorldMapQualityTierId;
  /** Show terrain tile layer on world map. */
  showWorldMapTerrain: boolean;
  /** Show AE overlay tile layer (devices + cables). */
  showWorldMapAeOverlay: boolean;
  /** Show device icon markers on world map. */
  showWorldMapDeviceIcons: boolean;
  /** AE overlay opacity (0.5–1.0). */
  worldMapAeOverlayOpacity: number;
  /** Per-category colors for world map AE overlay tinting. */
  worldMapAeCategoryColors: Record<WorldMapAeCategoryId, string>;
  /** Optional iconItemId → hex overrides (reserved; category tint applies per pixel today). */
  worldMapAeItemColorOverrides: Record<string, string>;
}

export const TOPOLOGY_DISPLAY_STORAGE_KEY = 'webae_topology_display';

export const DEFAULT_TOPOLOGY_DISPLAY: TopologyDisplaySettings = {
  renderMode: 'abstract',
  abstractLayout: 'tree',
  layoutDirection: 'LR',
  depthGap: 140,
  siblingGap: 72,
  labelMargin: 24,
  nodeRadius: 18,
  labelStrategy: 'external',
  showCountLabels: true,
  showEdgeChannelLabels: true,
  hideCableNodes: true,
  cableCellPx: 24,
  nodeBlockPx: 32,
  colors: {
    smart: hexFromAeCableColorId(DEFAULT_AE_CABLE_COLOR_ID),
    covered: hexFromAeCableColorId(DEFAULT_AE_CABLE_COLOR_ID),
    dense: hexFromAeCableColorId(DEFAULT_AE_CABLE_COLOR_ID),
    nodeStroke: '#334155',
    nodeFill: '#1a2332',
    label: '#e6edf3',
    labelDim: '#8b949e',
  },
  cableColorPreset: {
    smart: DEFAULT_AE_CABLE_COLOR_ID,
    covered: DEFAULT_AE_CABLE_COLOR_ID,
    dense: DEFAULT_AE_CABLE_COLOR_ID,
  },
  worldMapObliqueDirection: 'se',
  worldMapQuality: 'medium',
  showWorldMapTerrain: true,
  showWorldMapAeOverlay: false,
  showWorldMapDeviceIcons: true,
  worldMapAeOverlayOpacity: 0.85,
  worldMapAeCategoryColors: { ...DEFAULT_WORLD_MAP_AE_CATEGORY_COLORS },
  worldMapAeItemColorOverrides: {},
};

export function mergeTopologyDisplay(
  partial: Partial<TopologyDisplaySettings> | null | undefined
): TopologyDisplaySettings {
  if (!partial) {
    return {
      ...DEFAULT_TOPOLOGY_DISPLAY,
      colors: { ...DEFAULT_TOPOLOGY_DISPLAY.colors },
      cableColorPreset: { ...DEFAULT_TOPOLOGY_DISPLAY.cableColorPreset },
      worldMapAeCategoryColors: { ...DEFAULT_TOPOLOGY_DISPLAY.worldMapAeCategoryColors },
      worldMapAeItemColorOverrides: { ...DEFAULT_TOPOLOGY_DISPLAY.worldMapAeItemColorOverrides },
    };
  }
  return {
    ...DEFAULT_TOPOLOGY_DISPLAY,
    ...partial,
    colors: { ...DEFAULT_TOPOLOGY_DISPLAY.colors, ...(partial.colors ?? {}) },
    cableColorPreset: {
      ...DEFAULT_TOPOLOGY_DISPLAY.cableColorPreset,
      ...(partial.cableColorPreset ?? {}),
    },
    worldMapAeCategoryColors: {
      ...DEFAULT_TOPOLOGY_DISPLAY.worldMapAeCategoryColors,
      ...(partial.worldMapAeCategoryColors ?? {}),
    },
    worldMapAeItemColorOverrides: {
      ...DEFAULT_TOPOLOGY_DISPLAY.worldMapAeItemColorOverrides,
      ...(partial.worldMapAeItemColorOverrides ?? {}),
    },
  };
}
