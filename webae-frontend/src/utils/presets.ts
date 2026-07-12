// Preset system — a preset captures all user-facing settings for one-click
// switching. Stored in localStorage as an array.

import type { EffectsLevel } from '@/theme/colors';

// 9-grid alignment for widget content (horizontal-vertical).
export type Alignment =
  | 'top-left'
  | 'top'
  | 'top-right'
  | 'left'
  | 'center'
  | 'right'
  | 'bottom-left'
  | 'bottom'
  | 'bottom-right';

export const ALL_ALIGNMENTS: Alignment[] = [
  'top-left', 'top', 'top-right',
  'left', 'center', 'right',
  'bottom-left', 'bottom', 'bottom-right',
];

export interface DashboardWidgetColorOverrides {
  inheritDefault: boolean;
  titleColor: string;
  chartColor: string;
  iconColor: string;
  backgroundColor: string;
  borderColor: string;
  /** Optional per-widget border width override (px); empty = inherit global. */
  borderWidth?: number;
  /** 折线/主序列线条色（空=继承 chartColor） */
  chartLineColor: string;
  /** 折线/主序列面积填充色 */
  chartAreaColor: string;
  /** 图表网格线色 */
  chartGridColor: string;
  /** 悬停数据点色 */
  chartPointColor: string;
  /** 第二序列线条色（如 Steam） */
  chartSecondaryLineColor: string;
  /** 第二序列面积色 */
  chartSecondaryAreaColor: string;
  /** 进度条轨道背景色（空=继承默认） */
  progressTrackColor: string;
  /** 进度条填充色（空=继承 chartColor） */
  progressFillColor: string;
  /** 仪表盘背景圈色（空=继承默认） */
  gaugeTrackColor: string;
  /** 仪表盘前景弧色（空=继承 chartColor） */
  gaugeStrokeColor: string;
  /** 柱状图分段色（多色，按顺序循环；空=继承 chartColor） */
  barSegmentColors: string[];
  /** 饼图扇区色（多色，按顺序循环；空=继承 chartColor） */
  pieSliceColors: string[];
  /** 数据表隔行背景色（空=继承默认） */
  dataTableRowAltColor: string;
  /** 趋势图坐标轴文字色（空=继承默认） */
  axisTextColor: string;
  /** 雷达图轴线色（空=继承默认） */
  radarAxisColor: string;
  /** storageByCategory 具名分类色 */
  categoryItemsColor: string;
  categoryFluidsColor: string;
  categoryEssentiaColor: string;
}

export interface DashboardWidgetConfig {
  id: string;
  type:
    | 'statCard'
    | 'progressBar'
    | 'lineChart'
    | 'barChart'
    | 'pieChart'
    | 'dataTable'
    | 'gauge'
    | 'radarChart';
  dataSource: string;
  scope: 'global' | 'perNetwork';
  networkId?: number;
  title: string;
  style?: string;
  timeWindow?: number;
  maxRows?: number;
  width: number;
  height: number;
  x: number;
  y: number;
  /** Radar chart multi-axis config (max 8). */
  radarAxes?: Array<{ dataSource: string; label?: string }>;
  colors?: DashboardWidgetColorOverrides;
  // Per-widget overrides (undefined → fall back to DashboardSettings defaults)
  fontSize?: number;
  chartSize?: number;       // 0-100, percentage of widget height used by chart
  alignment?: Alignment;
  /** Visual inset inside grid cell (px); does not affect GridStack snap. */
  contentInset?: number;
  /** Trend chart width stretch mode override. */
  chartStretchMode?: 'fit' | 'stretchX' | 'fill';
  /** statCard: show delta vs previous sample. */
  showDelta?: boolean;
  /** statCard: significant digits for large number format. */
  significantDigits?: number;
  /**
   * Content scale multiplier (0.5–2). Applied to text/chart sizing inside the widget.
   * Default 1 when omitted.
   */
  contentScale?: number;
  /** dataTable visible column keys (order = display order). Empty/undefined = defaults. */
  columns?: string[];
  /** Pinned rows / series / scalar targets (items, fluids, CPUs, etc.). */
  pins?: DashboardPin[];
  /** When true, dataTable shows only pins (no Top-N fill). */
  pinsOnly?: boolean;
  /** gauge: custom max when pinning an absolute amount (0 = auto). */
  gaugeThreshold?: number;
}

/** Kind of a dashboard pin target. */
export type DashboardPinKind =
  | 'item'
  | 'fluid'
  | 'essentia'
  | 'scalar'
  | 'power'
  | 'cpu'
  | 'gt'
  | 'balance';

export interface DashboardPin {
  kind: DashboardPinKind;
  /** Stable id: itemId, fluidName, aspect, dataSource key, cpu:name, gt:dim:x:y:z, balance resource key. */
  id: string;
  /** Optional display label override. */
  label?: string;
  /** Optional metric field for cpu/gt (e.g. craftingProgress, progressPercent). */
  metricField?: string;
}

export interface DashboardSettings {
  /** 页面边缘留白（px），应用于 Grid 容器 padding */
  margin: number;
  /** 组件之间的间距（px），传递给 GridStack margin */
  widgetGap: number;
  /** 组件视觉内边距（px），不改变 GridStack 吸附 */
  contentInset: number;
  borderWidth: number;
  /** 折线图横向拉伸模式：fit | stretchX | fill */
  chartStretchMode: 'fit' | 'stretchX' | 'fill';
  // Global defaults applied to every widget unless overridden
  fontSize: number;          // px, base font size for widget text
  chartSize: number;         // 0-100, default chart area percentage
  /** 趋势图左侧 Y 轴数值刻度（不受吸附/排列影响） */
  chartShowValueAxis: boolean;
  /** 趋势图底部时间戳刻度 */
  chartShowTimeAxis: boolean;
  /** 组件 footer 显示「N 秒前更新」 */
  showLastUpdated?: boolean;
  defaultAlignment: Alignment;
  defaultColors: {
    titleColor: string;
    chartColor: string;
    iconColor: string;
    backgroundColor: string;
    borderColor: string;
  };
  colorPresets: Array<{ name: string; colors: DashboardSettings['defaultColors'] }>;
  widgets: DashboardWidgetConfig[];
}

export interface AppPreset {
  id: string;
  name: string;
  createdAt: number;
  settings: {
    themeColor: string;
    themeLayout: string;
    lang: string;
    displayMode: string;
    numberFormat: string;
    iconPack: string;
    iconRenderMode?: string;
    localIconPack?: string;
    sidebarMode: 'expanded' | 'collapsed' | 'hidden';
    effectsLevel: EffectsLevel;
    dashboard: DashboardSettings | null;
  };
}

export const DEFAULT_DASHBOARD_WIDGETS: DashboardWidgetConfig[] = [
  {
    id: 'w-itemTypes',
    type: 'statCard',
    dataSource: 'itemCount',
    scope: 'perNetwork',
    title: 'itemTypes',
    width: 3,
    height: 2,
    x: 0,
    y: 0,
  },
  {
    id: 'w-fluidTypes',
    type: 'statCard',
    dataSource: 'fluidCount',
    scope: 'perNetwork',
    title: 'fluidTypes',
    width: 3,
    height: 2,
    x: 3,
    y: 0,
  },
  {
    id: 'w-bytesPercent',
    type: 'progressBar',
    dataSource: 'bytesPercent',
    scope: 'perNetwork',
    title: 'storageUsage',
    style: 'horizontal',
    width: 6,
    height: 2,
    x: 6,
    y: 0,
  },
  {
    id: 'w-playerOnlineCount',
    type: 'statCard',
    dataSource: 'playerOnlineCount',
    scope: 'global',
    title: 'dataSource_playerOnlineCount',
    width: 4,
    height: 2,
    x: 0,
    y: 2,
  },
  {
    id: 'w-playerOnlineTrend',
    type: 'lineChart',
    dataSource: 'playerOnlineTrend',
    scope: 'global',
    title: 'dataSource_playerOnlineTrend',
    chartStretchMode: 'fit',
    width: 4,
    height: 2,
    x: 4,
    y: 2,
  },
  {
    id: 'w-euStored',
    type: 'gauge',
    dataSource: 'euPercent',
    scope: 'perNetwork',
    title: 'euStored',
    width: 4,
    height: 3,
    x: 8,
    y: 2,
  },
  {
    id: 'w-powerHistory',
    type: 'lineChart',
    dataSource: 'powerHistory',
    scope: 'perNetwork',
    title: 'trend',
    timeWindow: 0,
    width: 12,
    height: 3,
    x: 0,
    y: 4,
  },
];

export const DEFAULT_DASHBOARD_SETTINGS: DashboardSettings = {
  margin: 12,
  widgetGap: 12,
  contentInset: 0,
  borderWidth: 1,
  chartStretchMode: 'stretchX',
  fontSize: 14,
  chartSize: 70,
  chartShowValueAxis: false,
  chartShowTimeAxis: false,
  showLastUpdated: false,
  defaultAlignment: 'center',
  defaultColors: {
    titleColor: '',
    chartColor: '',
    iconColor: '',
    backgroundColor: '',
    borderColor: '',
  },
  colorPresets: [],
  widgets: DEFAULT_DASHBOARD_WIDGETS,
};

/** Storage page overview row — reuses DashboardWidgetConfig + resolveProp inheritance. */
export type StorageOverviewSettings = Pick<
  DashboardSettings,
  | 'margin'
  | 'widgetGap'
  | 'contentInset'
  | 'borderWidth'
  | 'chartStretchMode'
  | 'fontSize'
  | 'chartSize'
  | 'chartShowValueAxis'
  | 'chartShowTimeAxis'
  | 'defaultAlignment'
  | 'defaultColors'
  | 'colorPresets'
  | 'widgets'
>;

export const STORAGE_OVERVIEW_CONFIG_KEY = 'webae_storage_overview_config';

export const DEFAULT_STORAGE_OVERVIEW_WIDGETS: DashboardWidgetConfig[] = [
  {
    id: 'so-itemTypes',
    type: 'statCard',
    dataSource: 'itemCount',
    scope: 'perNetwork',
    title: 'itemTypes',
    width: 3,
    height: 2,
    x: 0,
    y: 0,
  },
  {
    id: 'so-fluidTypes',
    type: 'statCard',
    dataSource: 'fluidCount',
    scope: 'perNetwork',
    title: 'fluidTypes',
    width: 3,
    height: 2,
    x: 3,
    y: 0,
  },
  {
    id: 'so-bytesUsed',
    type: 'progressBar',
    dataSource: 'bytesPercent',
    scope: 'perNetwork',
    title: 'bytesUsed',
    style: 'horizontal',
    width: 3,
    height: 2,
    x: 6,
    y: 0,
  },
  {
    id: 'so-activeCpu',
    type: 'statCard',
    dataSource: 'activeCpu',
    scope: 'perNetwork',
    title: 'activeCpus',
    width: 3,
    height: 2,
    x: 9,
    y: 0,
  },
];

export const DEFAULT_STORAGE_OVERVIEW_SETTINGS: StorageOverviewSettings = {
  margin: 12,
  widgetGap: 12,
  contentInset: 0,
  borderWidth: 1,
  chartStretchMode: 'stretchX',
  fontSize: 14,
  chartSize: 50,
  chartShowValueAxis: false,
  chartShowTimeAxis: false,
  defaultAlignment: 'center',
  defaultColors: {
    titleColor: '',
    chartColor: '',
    iconColor: '',
    backgroundColor: '',
    borderColor: '',
  },
  colorPresets: [],
  widgets: DEFAULT_STORAGE_OVERVIEW_WIDGETS,
};

/** CPU page overview row — same inheritance model as storage overview. */
export type CpuOverviewSettings = StorageOverviewSettings;

export const CPU_OVERVIEW_CONFIG_KEY = 'webae_cpu_overview_config';

export const DEFAULT_CPU_OVERVIEW_WIDGETS: DashboardWidgetConfig[] = [
  {
    id: 'co-activeTotal',
    type: 'statCard',
    dataSource: 'cpuActiveTotal',
    scope: 'perNetwork',
    title: 'cpuActiveTotal',
    width: 3,
    height: 2,
    x: 0,
    y: 0,
  },
  {
    id: 'co-storageUsage',
    type: 'progressBar',
    dataSource: 'totalCpuStoragePercent',
    scope: 'perNetwork',
    title: 'totalCpuStorageUsage',
    width: 3,
    height: 2,
    x: 3,
    y: 0,
  },
  {
    id: 'co-coprocessors',
    type: 'statCard',
    dataSource: 'totalCoProcessors',
    scope: 'perNetwork',
    title: 'totalCoProcessors',
    width: 3,
    height: 2,
    x: 6,
    y: 0,
  },
  {
    id: 'co-parallel',
    type: 'statCard',
    dataSource: 'parallelCrafting',
    scope: 'perNetwork',
    title: 'parallelCrafting',
    width: 3,
    height: 2,
    x: 9,
    y: 0,
  },
];

export const DEFAULT_CPU_OVERVIEW_SETTINGS: CpuOverviewSettings = {
  margin: 12,
  widgetGap: 12,
  contentInset: 0,
  borderWidth: 1,
  chartStretchMode: 'stretchX',
  fontSize: 14,
  chartSize: 50,
  chartShowValueAxis: false,
  chartShowTimeAxis: false,
  defaultAlignment: 'center',
  defaultColors: {
    titleColor: '',
    chartColor: '',
    iconColor: '',
    backgroundColor: '',
    borderColor: '',
  },
  colorPresets: [],
  widgets: DEFAULT_CPU_OVERVIEW_WIDGETS,
};

/** Power page — full GridStack layout with lineChart trend widget. */
export type PowerSettings = StorageOverviewSettings;

export const POWER_CONFIG_KEY = 'webae_power_config';

export const DASHBOARD_CONFIG_KEY = 'webae_dashboard_config';

export const DEFAULT_POWER_WIDGETS: DashboardWidgetConfig[] = [
  {
    id: 'pw-euGauge',
    type: 'gauge',
    dataSource: 'euPercent',
    scope: 'perNetwork',
    title: 'euStored',
    width: 3,
    height: 2,
    x: 0,
    y: 0,
  },
  {
    id: 'pw-euIn',
    type: 'statCard',
    dataSource: 'euInRate',
    scope: 'perNetwork',
    title: 'euInRate',
    width: 3,
    height: 2,
    x: 3,
    y: 0,
  },
  {
    id: 'pw-euOut',
    type: 'statCard',
    dataSource: 'euOutRate',
    scope: 'perNetwork',
    title: 'euOutRate',
    width: 3,
    height: 2,
    x: 6,
    y: 0,
  },
  {
    id: 'pw-steam',
    type: 'progressBar',
    dataSource: 'steamPercent',
    scope: 'perNetwork',
    title: 'steamStored',
    style: 'horizontal',
    width: 3,
    height: 2,
    x: 9,
    y: 0,
  },
  {
    id: 'pw-trend',
    type: 'lineChart',
    dataSource: 'powerHistory',
    scope: 'perNetwork',
    title: 'trend',
    width: 12,
    height: 4,
    x: 0,
    y: 2,
  },
  {
    id: 'pw-steamIn',
    type: 'statCard',
    dataSource: 'steamInRate',
    scope: 'perNetwork',
    title: 'steamIn',
    width: 3,
    height: 2,
    x: 0,
    y: 6,
  },
  {
    id: 'pw-steamOut',
    type: 'statCard',
    dataSource: 'steamOutRate',
    scope: 'perNetwork',
    title: 'steamOut',
    width: 3,
    height: 2,
    x: 3,
    y: 6,
  },
];

export const DEFAULT_POWER_SETTINGS: PowerSettings = {
  margin: 12,
  widgetGap: 12,
  contentInset: 0,
  borderWidth: 1,
  chartStretchMode: 'stretchX',
  fontSize: 14,
  chartSize: 75,
  chartShowValueAxis: false,
  chartShowTimeAxis: false,
  defaultAlignment: 'center',
  defaultColors: {
    titleColor: '',
    chartColor: '',
    iconColor: '',
    backgroundColor: '',
    borderColor: '',
  },
  colorPresets: [],
  widgets: DEFAULT_POWER_WIDGETS,
};

/** Merge partial saved overview config with defaults (localStorage upgrade path). */
export function mergeOverviewSettings<T extends StorageOverviewSettings>(
  defaults: T,
  parsed: Partial<T> | null | undefined
): T {
  if (!parsed) return defaults;
  return {
    ...defaults,
    ...parsed,
    widgetGap: parsed.widgetGap ?? defaults.widgetGap ?? DEFAULT_DASHBOARD_SETTINGS.widgetGap,
    contentInset: parsed.contentInset ?? defaults.contentInset ?? DEFAULT_DASHBOARD_SETTINGS.contentInset,
    chartStretchMode: parsed.chartStretchMode ?? defaults.chartStretchMode ?? DEFAULT_DASHBOARD_SETTINGS.chartStretchMode,
    chartShowValueAxis: parsed.chartShowValueAxis ?? defaults.chartShowValueAxis,
    chartShowTimeAxis: parsed.chartShowTimeAxis ?? defaults.chartShowTimeAxis,
    defaultColors: { ...defaults.defaultColors, ...parsed.defaultColors },
    colorPresets: parsed.colorPresets ?? defaults.colorPresets,
    widgets: parsed.widgets ?? defaults.widgets,
  };
}

/** Cast overview settings to DashboardSettings for shared drawer/modal components. */
export function overviewAsDashboardSettings(s: StorageOverviewSettings): DashboardSettings {
  return { ...DEFAULT_DASHBOARD_SETTINGS, ...s };
}

/**
 * Built-in default presets, initialized on first launch.
 */
export function builtinPresets(
  t: (key: string) => string
): AppPreset[] {
  return [
    {
      id: 'builtin-default',
      name: t('presetDefault_name'),
      createdAt: 0,
      settings: {
        themeColor: 'dark',
        themeLayout: 'standard',
        lang: 'zh',
        displayMode: 'split',
        numberFormat: 'thousands',
        iconPack: 'default',
        iconRenderMode: 'nei',
        localIconPack: '',
        sidebarMode: 'expanded',
        effectsLevel: 'full',
        dashboard: null,
      },
    },
    {
      id: 'builtin-dark-focus',
      name: t('presetDarkFocus_name'),
      createdAt: 0,
      settings: {
        themeColor: 'midnight',
        themeLayout: 'wide',
        lang: 'zh',
        displayMode: 'merged',
        numberFormat: 'short',
        iconPack: 'default',
        iconRenderMode: 'nei',
        localIconPack: '',
        sidebarMode: 'collapsed',
        effectsLevel: 'full',
        dashboard: null,
      },
    },
    {
      id: 'builtin-data-monitor',
      name: t('presetDataMonitor_name'),
      createdAt: 0,
      settings: {
        themeColor: 'gtnh-blue',
        themeLayout: 'wide',
        lang: 'en',
        displayMode: 'split',
        numberFormat: 'ae',
        iconPack: 'default',
        iconRenderMode: 'nei',
        localIconPack: '',
        sidebarMode: 'expanded',
        effectsLevel: 'subtle',
        dashboard: null,
      },
    },
  ];
}

/** Migrate legacy widget configs (sparkline → lineChart, array category colors → named). */
export function migrateDashboardWidgets(widgets: DashboardWidgetConfig[]): DashboardWidgetConfig[] {
  return widgets.map((w) => {
    let next: DashboardWidgetConfig = { ...w };
    if ((next.type as string) === 'sparkline') {
      next = {
        ...next,
        type: 'lineChart',
        chartStretchMode: next.chartStretchMode || 'fit',
      };
    }
    if (next.contentScale == null) {
      next = { ...next, contentScale: 1 };
    } else if (next.contentScale < 0.5) {
      next = { ...next, contentScale: 0.5 };
    } else if (next.contentScale > 2) {
      next = { ...next, contentScale: 2 };
    }
    if (!next.pins) {
      next = { ...next, pins: [] };
    }
    const colors = next.colors;
    if (colors && !colors.inheritDefault) {
      const migrated = { ...colors };
      if (colors.barSegmentColors?.length && !colors.categoryItemsColor) {
        migrated.categoryItemsColor = colors.barSegmentColors[0] || '';
        migrated.categoryFluidsColor = colors.barSegmentColors[1] || '';
        migrated.categoryEssentiaColor = colors.barSegmentColors[2] || '';
      }
      if (colors.pieSliceColors?.length && !colors.categoryItemsColor) {
        migrated.categoryItemsColor = colors.pieSliceColors[0] || '';
        migrated.categoryFluidsColor = colors.pieSliceColors[1] || '';
        migrated.categoryEssentiaColor = colors.pieSliceColors[2] || '';
      }
      next = { ...next, colors: migrated };
    }
    return next;
  });
}

/** Read dashboard layout from localStorage (sync, safe for useState lazy init). */
export function loadDashboardSettings(): DashboardSettings {
  try {
    const raw = localStorage.getItem(DASHBOARD_CONFIG_KEY);
    if (!raw) return DEFAULT_DASHBOARD_SETTINGS;
    const parsed = JSON.parse(raw) as Partial<DashboardSettings>;
    return {
      ...DEFAULT_DASHBOARD_SETTINGS,
      ...parsed,
      widgetGap: parsed.widgetGap ?? DEFAULT_DASHBOARD_SETTINGS.widgetGap,
      chartShowValueAxis: parsed.chartShowValueAxis ?? DEFAULT_DASHBOARD_SETTINGS.chartShowValueAxis,
      chartShowTimeAxis: parsed.chartShowTimeAxis ?? DEFAULT_DASHBOARD_SETTINGS.chartShowTimeAxis,
      defaultColors: { ...DEFAULT_DASHBOARD_SETTINGS.defaultColors, ...parsed.defaultColors },
      colorPresets: parsed.colorPresets ?? [],
      widgets: migrateDashboardWidgets(parsed.widgets ?? DEFAULT_DASHBOARD_WIDGETS),
    };
  } catch {
    return DEFAULT_DASHBOARD_SETTINGS;
  }
}

/** Read overview/power grid settings from localStorage (sync, safe for useState lazy init). */
export function loadOverviewSettingsFromStorage<T extends StorageOverviewSettings>(
  storageKey: string,
  defaults: T
): T {
  try {
    const raw = localStorage.getItem(storageKey);
    if (!raw) return defaults;
    return mergeOverviewSettings(defaults, JSON.parse(raw) as Partial<T>);
  } catch {
    return defaults;
  }
}

export const PRESETS_STORAGE_KEY = 'webae_presets';
