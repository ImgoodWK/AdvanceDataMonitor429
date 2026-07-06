import type { AeCableColorId } from '@/utils/aeCableColors';
import { DEFAULT_AE_CABLE_COLOR_ID, hexFromAeCableColorId } from '@/utils/aeCableColors';

export type TopologyLayoutDirection = 'LR' | 'TB';
export type TopologyRenderMode = 'abstract' | 'simulated';
export type TopologyLabelStrategy = 'external' | 'below' | 'hover';

export interface TopologyDisplaySettings {
  /** Abstract vs simulated cable-bus view (logical mode only). */
  renderMode: TopologyRenderMode;
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
}

export const TOPOLOGY_DISPLAY_STORAGE_KEY = 'webae_topology_display';

export const DEFAULT_TOPOLOGY_DISPLAY: TopologyDisplaySettings = {
  renderMode: 'abstract',
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
};

export function mergeTopologyDisplay(
  partial: Partial<TopologyDisplaySettings> | null | undefined
): TopologyDisplaySettings {
  if (!partial) {
    return {
      ...DEFAULT_TOPOLOGY_DISPLAY,
      colors: { ...DEFAULT_TOPOLOGY_DISPLAY.colors },
      cableColorPreset: { ...DEFAULT_TOPOLOGY_DISPLAY.cableColorPreset },
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
  };
}
