export interface QuestDisplaySettings {
  /** Multiplier on BetterQuesting editor coordinates (layout spread). */
  coordScale: number;
  /** Node box size = max(minNodeSize, BQ size * nodeSizeScale). */
  nodeSizeScale: number;
  minNodeSize: number;
  labelFontSize: number;
  labelMaxWidth: number;
  showLabels: boolean;
  iconFillPercent: number;
  edgeWidth: number;
  showGhostNodes: boolean;
  linePanelWidth: number;
  detailPanelWidth: number;
  linePanelIconSize: number;
  linePanelFontSize: number;
  fitPadding: number;
  centerMinZoom: number;
  autoFitOnSettingsChange: boolean;
}

export const QUEST_DISPLAY_STORAGE_KEY = 'webae.quest.display';

export const DEFAULT_QUEST_DISPLAY: QuestDisplaySettings = {
  coordScale: 1.35,
  nodeSizeScale: 1.0,
  minNodeSize: 64,
  labelFontSize: 16,
  labelMaxWidth: 220,
  showLabels: true,
  iconFillPercent: 85,
  edgeWidth: 2.5,
  showGhostNodes: true,
  linePanelWidth: 250,
  detailPanelWidth: 380,
  linePanelIconSize: 28,
  linePanelFontSize: 14,
  fitPadding: 32,
  centerMinZoom: 1.2,
  autoFitOnSettingsChange: true,
};

function clampNum(value: unknown, min: number, max: number, fallback: number): number {
  if (typeof value !== 'number' || Number.isNaN(value)) return fallback;
  return Math.max(min, Math.min(max, value));
}

function clampBool(value: unknown, fallback: boolean): boolean {
  return typeof value === 'boolean' ? value : fallback;
}

export function mergeQuestDisplay(
  partial: Partial<QuestDisplaySettings> | null | undefined
): QuestDisplaySettings {
  const p = partial ?? {};
  return {
    coordScale: clampNum(p.coordScale, 0.6, 3.0, DEFAULT_QUEST_DISPLAY.coordScale),
    nodeSizeScale: clampNum(p.nodeSizeScale, 0.7, 1.8, DEFAULT_QUEST_DISPLAY.nodeSizeScale),
    minNodeSize: clampNum(p.minNodeSize, 48, 112, DEFAULT_QUEST_DISPLAY.minNodeSize),
    labelFontSize: clampNum(p.labelFontSize, 12, 22, DEFAULT_QUEST_DISPLAY.labelFontSize),
    labelMaxWidth: clampNum(p.labelMaxWidth, 180, 320, DEFAULT_QUEST_DISPLAY.labelMaxWidth),
    showLabels: clampBool(p.showLabels, DEFAULT_QUEST_DISPLAY.showLabels),
    iconFillPercent: clampNum(p.iconFillPercent, 60, 95, DEFAULT_QUEST_DISPLAY.iconFillPercent),
    edgeWidth: clampNum(p.edgeWidth, 1.5, 5, DEFAULT_QUEST_DISPLAY.edgeWidth),
    showGhostNodes: clampBool(p.showGhostNodes, DEFAULT_QUEST_DISPLAY.showGhostNodes),
    linePanelWidth: clampNum(p.linePanelWidth, 200, 360, DEFAULT_QUEST_DISPLAY.linePanelWidth),
    detailPanelWidth: clampNum(p.detailPanelWidth, 300, 520, DEFAULT_QUEST_DISPLAY.detailPanelWidth),
    linePanelIconSize: clampNum(p.linePanelIconSize, 24, 48, DEFAULT_QUEST_DISPLAY.linePanelIconSize),
    linePanelFontSize: clampNum(p.linePanelFontSize, 12, 18, DEFAULT_QUEST_DISPLAY.linePanelFontSize),
    fitPadding: clampNum(p.fitPadding, 16, 64, DEFAULT_QUEST_DISPLAY.fitPadding),
    centerMinZoom: clampNum(p.centerMinZoom, 1.0, 2.0, DEFAULT_QUEST_DISPLAY.centerMinZoom),
    autoFitOnSettingsChange: clampBool(
      p.autoFitOnSettingsChange,
      DEFAULT_QUEST_DISPLAY.autoFitOnSettingsChange
    ),
  };
}

export const QUEST_LAYOUT_SETTING_KEYS: (keyof QuestDisplaySettings)[] = [
  'coordScale',
  'nodeSizeScale',
  'minNodeSize',
  'fitPadding',
];
