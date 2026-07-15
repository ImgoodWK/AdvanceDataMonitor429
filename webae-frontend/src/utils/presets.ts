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

export type { ChartStyle } from '@/theme/pageStyles';

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
    | 'radarChart'
    /** Nested GridStack container; children are regular widgets. */
    | 'group'
    /** Static note / markdown-plain text. */
    | 'textNote'
    /** Visual spacer / divider (no data). */
    | 'spacer'
    /** Active WebAE alerts summary. */
    | 'alertsSummary'
    /** Busy crafting CPUs queue. */
    | 'craftingQueue';
  dataSource: string;
  scope: 'global' | 'perNetwork';
  networkId?: number;
  title: string;
  /** progressBar only: horizontal | circular */
  style?: string;
  /**
   * Chart visual recipe for line/bar/pie/radar.
   * `inherit` (default) follows pageStyle → DashboardSettings.defaultChartStyle → classic.
   */
  chartStyle?: import('@/theme/pageStyles').ChartStyle;
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
  /** Nested widgets when type === 'group'. */
  children?: DashboardWidgetConfig[];
  /** textNote body (plain text; newlines preserved). */
  noteText?: string;
  /** GridStack: lock position+size in edit mode. */
  locked?: boolean;
  /** GridStack: disallow drag. */
  noMove?: boolean;
  /** GridStack: disallow resize. */
  noResize?: boolean;
  /** GridStack sizeToContent (dataTable / textNote / alerts / crafting). */
  sizeToContent?: boolean;
  /** Optional soft threshold for conditional tint (stat/gauge percent or absolute). */
  alertThreshold?: number;
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
  /**
   * Default chart visual recipe when widget.chartStyle is omitted or inherit.
   * Still resolves through pageStyle when value is inherit.
   */
  defaultChartStyle?: import('@/theme/pageStyles').ChartStyle;
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
    /** Orthogonal page chrome / material style (classic | linear | …). */
    pageStyle?: string;
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
  defaultChartStyle: 'inherit',
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
  | 'defaultChartStyle'
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
    defaultChartStyle: parsed.defaultChartStyle ?? defaults.defaultChartStyle ?? DEFAULT_DASHBOARD_SETTINGS.defaultChartStyle,
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
        pageStyle: 'classic',
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
        pageStyle: 'linear',
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
        pageStyle: 'viz',
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
    {
      id: 'builtin-style-rhodes',
      name: t('presetStyleRhodes_name'),
      createdAt: 0,
      settings: {
        themeColor: 'rhodes-ink',
        themeLayout: 'compact',
        pageStyle: 'rhodes',
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
      id: 'builtin-style-cupertino',
      name: t('presetStyleCupertino_name'),
      createdAt: 0,
      settings: {
        themeColor: 'light',
        themeLayout: 'wide',
        pageStyle: 'cupertino',
        lang: 'en',
        displayMode: 'merged',
        numberFormat: 'short',
        iconPack: 'default',
        iconRenderMode: 'nei',
        localIconPack: '',
        sidebarMode: 'expanded',
        effectsLevel: 'subtle',
        dashboard: null,
      },
    },
    {
      id: 'builtin-style-vercel',
      name: t('presetStyleVercel_name'),
      createdAt: 0,
      settings: {
        themeColor: 'carbon',
        themeLayout: 'standard',
        pageStyle: 'vercel',
        lang: 'en',
        displayMode: 'split',
        numberFormat: 'thousands',
        iconPack: 'default',
        iconRenderMode: 'nei',
        localIconPack: '',
        sidebarMode: 'expanded',
        effectsLevel: 'subtle',
        dashboard: null,
      },
    },
    {
      id: 'builtin-style-grafana',
      name: t('presetStyleGrafana_name'),
      createdAt: 0,
      settings: {
        themeColor: 'midnight',
        themeLayout: 'wide',
        pageStyle: 'grafana',
        lang: 'en',
        displayMode: 'split',
        numberFormat: 'ae',
        iconPack: 'default',
        iconRenderMode: 'nei',
        localIconPack: '',
        sidebarMode: 'collapsed',
        effectsLevel: 'subtle',
        dashboard: null,
      },
    },
    {
      id: 'builtin-style-swiss',
      name: t('presetStyleSwiss_name'),
      createdAt: 0,
      settings: {
        themeColor: 'light',
        themeLayout: 'sidebar-right',
        pageStyle: 'swiss',
        lang: 'en',
        displayMode: 'merged',
        numberFormat: 'thousands',
        iconPack: 'default',
        iconRenderMode: 'nei',
        localIconPack: '',
        sidebarMode: 'expanded',
        effectsLevel: 'none',
        dashboard: null,
      },
    },
    {
      id: 'builtin-style-yorha',
      name: t('presetStyleYorha_name'),
      createdAt: 0,
      settings: {
        themeColor: 'yorha-black',
        themeLayout: 'standard',
        pageStyle: 'yorha',
        lang: 'en',
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
      id: 'builtin-style-hsr',
      name: t('presetStyleHsr_name'),
      createdAt: 0,
      settings: {
        themeColor: 'lavender',
        themeLayout: 'standard',
        pageStyle: 'hsr',
        lang: 'zh',
        displayMode: 'split',
        numberFormat: 'short',
        iconPack: 'default',
        iconRenderMode: 'nei',
        localIconPack: '',
        sidebarMode: 'expanded',
        effectsLevel: 'full',
        dashboard: null,
      },
    },
    {
      id: 'builtin-style-bloomberg',
      name: t('presetStyleBloomberg_name'),
      createdAt: 0,
      settings: {
        themeColor: 'carbon',
        themeLayout: 'compact',
        pageStyle: 'bloomberg',
        lang: 'en',
        displayMode: 'split',
        numberFormat: 'ae',
        iconPack: 'default',
        iconRenderMode: 'nei',
        localIconPack: '',
        sidebarMode: 'collapsed',
        effectsLevel: 'subtle',
        dashboard: null,
      },
    },
    {
      id: 'builtin-style-raycast',
      name: t('presetStyleRaycast_name'),
      createdAt: 0,
      settings: {
        themeColor: 'midnight',
        themeLayout: 'topnav',
        pageStyle: 'raycast',
        lang: 'en',
        displayMode: 'merged',
        numberFormat: 'short',
        iconPack: 'default',
        iconRenderMode: 'nei',
        localIconPack: '',
        sidebarMode: 'hidden',
        effectsLevel: 'full',
        dashboard: null,
      },
    },
    {
      id: 'builtin-style-brutal',
      name: t('presetStyleBrutal_name'),
      createdAt: 0,
      settings: {
        themeColor: 'brutal-poster',
        themeLayout: 'wide',
        pageStyle: 'brutal',
        lang: 'en',
        displayMode: 'merged',
        numberFormat: 'thousands',
        iconPack: 'default',
        iconRenderMode: 'nei',
        localIconPack: '',
        sidebarMode: 'expanded',
        effectsLevel: 'none',
        dashboard: null,
      },
    },
    {
      id: 'builtin-style-steam',
      name: t('presetStyleSteam_name'),
      createdAt: 0,
      settings: {
        themeColor: 'ocean',
        themeLayout: 'standard',
        pageStyle: 'steam',
        lang: 'en',
        displayMode: 'split',
        numberFormat: 'short',
        iconPack: 'default',
        iconRenderMode: 'nei',
        localIconPack: '',
        sidebarMode: 'expanded',
        effectsLevel: 'subtle',
        dashboard: null,
      },
    },
    {
      id: 'builtin-style-inkwash',
      name: t('presetStyleInkwash_name'),
      createdAt: 0,
      settings: {
        themeColor: 'ink-paper',
        themeLayout: 'standard',
        pageStyle: 'inkwash',
        lang: 'zh',
        displayMode: 'merged',
        numberFormat: 'thousands',
        iconPack: 'default',
        iconRenderMode: 'nei',
        localIconPack: '',
        sidebarMode: 'expanded',
        effectsLevel: 'subtle',
        dashboard: null,
      },
    },
    {
      id: 'builtin-style-arc-reactor',
      name: t('presetStyleArcReactor_name'),
      createdAt: 0,
      settings: {
        themeColor: 'reactor-cyan',
        themeLayout: 'floating',
        pageStyle: 'arc-reactor',
        lang: 'en',
        displayMode: 'split',
        numberFormat: 'short',
        iconPack: 'default',
        iconRenderMode: 'nei',
        localIconPack: '',
        sidebarMode: 'expanded',
        effectsLevel: 'full',
        dashboard: null,
      },
    },
    {
      id: 'builtin-style-winclassic',
      name: t('presetStyleWinclassic_name'),
      createdAt: 0,
      settings: {
        themeColor: 'win-teal',
        themeLayout: 'standard',
        pageStyle: 'winclassic',
        lang: 'en',
        displayMode: 'merged',
        numberFormat: 'thousands',
        iconPack: 'default',
        iconRenderMode: 'nei',
        localIconPack: '',
        sidebarMode: 'expanded',
        effectsLevel: 'none',
        dashboard: null,
      },
    },
    {
      id: 'builtin-style-cyberdeck',
      name: t('presetStyleCyberdeck_name'),
      createdAt: 0,
      settings: {
        themeColor: 'deck-magenta',
        themeLayout: 'compact',
        pageStyle: 'cyberdeck',
        lang: 'en',
        displayMode: 'split',
        numberFormat: 'ae',
        iconPack: 'default',
        iconRenderMode: 'nei',
        localIconPack: '',
        sidebarMode: 'expanded',
        effectsLevel: 'full',
        dashboard: null,
      },
    },
    {
      id: 'builtin-style-origami',
      name: t('presetStyleOrigami_name'),
      createdAt: 0,
      settings: {
        themeColor: 'light',
        themeLayout: 'wide',
        pageStyle: 'origami',
        lang: 'en',
        displayMode: 'merged',
        numberFormat: 'thousands',
        iconPack: 'default',
        iconRenderMode: 'nei',
        localIconPack: '',
        sidebarMode: 'expanded',
        effectsLevel: 'subtle',
        dashboard: null,
      },
    },
    {
      id: 'builtin-style-hexcell',
      name: t('presetStyleHexcell_name'),
      createdAt: 0,
      settings: {
        themeColor: 'hex-amber',
        themeLayout: 'wide',
        pageStyle: 'hexcell',
        lang: 'en',
        displayMode: 'split',
        numberFormat: 'short',
        iconPack: 'default',
        iconRenderMode: 'nei',
        localIconPack: '',
        sidebarMode: 'expanded',
        effectsLevel: 'full',
        dashboard: null,
      },
    },
    {
      id: 'builtin-style-vaporwave',
      name: t('presetStyleVaporwave_name'),
      createdAt: 0,
      settings: {
        themeColor: 'vapor-dusk',
        themeLayout: 'standard',
        pageStyle: 'vaporwave',
        lang: 'en',
        displayMode: 'merged',
        numberFormat: 'short',
        iconPack: 'default',
        iconRenderMode: 'nei',
        localIconPack: '',
        sidebarMode: 'expanded',
        effectsLevel: 'full',
        dashboard: null,
      },
    },
    {
      id: 'builtin-style-broadsheet',
      name: t('presetStyleBroadsheet_name'),
      createdAt: 0,
      settings: {
        themeColor: 'light',
        themeLayout: 'sidebar-right',
        pageStyle: 'broadsheet',
        lang: 'en',
        displayMode: 'merged',
        numberFormat: 'thousands',
        iconPack: 'default',
        iconRenderMode: 'nei',
        localIconPack: '',
        sidebarMode: 'expanded',
        effectsLevel: 'none',
        dashboard: null,
      },
    },
    {
      id: 'builtin-style-dmg',
      name: t('presetStyleDmg_name'),
      createdAt: 0,
      settings: {
        themeColor: 'dmg-olive',
        themeLayout: 'compact',
        pageStyle: 'dmg',
        lang: 'en',
        displayMode: 'split',
        numberFormat: 'thousands',
        iconPack: 'default',
        iconRenderMode: 'nei',
        localIconPack: '',
        sidebarMode: 'expanded',
        effectsLevel: 'subtle',
        dashboard: null,
      },
    },
    {
      id: 'builtin-style-liquid',
      name: t('presetStyleLiquid_name'),
      createdAt: 0,
      settings: {
        themeColor: 'midnight',
        themeLayout: 'floating',
        pageStyle: 'liquid',
        lang: 'en',
        displayMode: 'merged',
        numberFormat: 'short',
        iconPack: 'default',
        iconRenderMode: 'nei',
        localIconPack: '',
        sidebarMode: 'expanded',
        effectsLevel: 'full',
        dashboard: null,
      },
    },
    {
      id: 'builtin-style-blueprint',
      name: t('presetStyleBlueprint_name'),
      createdAt: 0,
      settings: {
        themeColor: 'blueprint-navy',
        themeLayout: 'wide',
        pageStyle: 'blueprint',
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
    {
      id: 'builtin-style-screentone',
      name: t('presetStyleScreentone_name'),
      createdAt: 0,
      settings: {
        themeColor: 'light',
        themeLayout: 'standard',
        pageStyle: 'screentone',
        lang: 'zh',
        displayMode: 'merged',
        numberFormat: 'thousands',
        iconPack: 'default',
        iconRenderMode: 'nei',
        localIconPack: '',
        sidebarMode: 'expanded',
        effectsLevel: 'subtle',
        dashboard: null,
      },
    },
    {
      id: 'builtin-style-arcade',
      name: t('presetStyleArcade_name'),
      createdAt: 0,
      settings: {
        themeColor: 'arcade-pink',
        themeLayout: 'bottomnav',
        pageStyle: 'arcade',
        lang: 'en',
        displayMode: 'split',
        numberFormat: 'short',
        iconPack: 'default',
        iconRenderMode: 'nei',
        localIconPack: '',
        sidebarMode: 'hidden',
        effectsLevel: 'full',
        dashboard: null,
      },
    },
    {
      id: 'builtin-style-bauhaus',
      name: t('presetStyleBauhaus_name'),
      createdAt: 0,
      settings: {
        themeColor: 'light',
        themeLayout: 'split-chrome',
        pageStyle: 'bauhaus',
        lang: 'en',
        displayMode: 'merged',
        numberFormat: 'thousands',
        iconPack: 'default',
        iconRenderMode: 'nei',
        localIconPack: '',
        sidebarMode: 'expanded',
        effectsLevel: 'none',
        dashboard: null,
      },
    },
    {
      id: 'builtin-style-obsidian',
      name: t('presetStyleObsidian_name'),
      createdAt: 0,
      settings: {
        themeColor: 'carbon',
        themeLayout: 'standard',
        pageStyle: 'obsidian',
        lang: 'en',
        displayMode: 'split',
        numberFormat: 'thousands',
        iconPack: 'default',
        iconRenderMode: 'nei',
        localIconPack: '',
        sidebarMode: 'expanded',
        effectsLevel: 'subtle',
        dashboard: null,
      },
    },
    {
      id: 'builtin-style-mesh',
      name: t('presetStyleMesh_name'),
      createdAt: 0,
      settings: {
        themeColor: 'stripe-indigo',
        themeLayout: 'wide',
        pageStyle: 'mesh',
        lang: 'en',
        displayMode: 'merged',
        numberFormat: 'short',
        iconPack: 'default',
        iconRenderMode: 'nei',
        localIconPack: '',
        sidebarMode: 'expanded',
        effectsLevel: 'subtle',
        dashboard: null,
      },
    },
    {
      id: 'builtin-style-doomhud',
      name: t('presetStyleDoomhud_name'),
      createdAt: 0,
      settings: {
        themeColor: 'doom-steel',
        themeLayout: 'bottomnav',
        pageStyle: 'doomhud',
        lang: 'en',
        displayMode: 'split',
        numberFormat: 'ae',
        iconPack: 'default',
        iconRenderMode: 'nei',
        localIconPack: '',
        sidebarMode: 'hidden',
        effectsLevel: 'full',
        dashboard: null,
      },
    },
    {
      id: 'builtin-style-sakura',
      name: t('presetStyleSakura_name'),
      createdAt: 0,
      settings: {
        themeColor: 'sakura-mist',
        themeLayout: 'floating',
        pageStyle: 'sakura',
        lang: 'zh',
        displayMode: 'merged',
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
      id: 'builtin-style-pcb',
      name: t('presetStylePcb_name'),
      createdAt: 0,
      settings: {
        themeColor: 'pcb-green',
        themeLayout: 'standard',
        pageStyle: 'pcb',
        lang: 'en',
        displayMode: 'split',
        numberFormat: 'ae',
        iconPack: 'default',
        iconRenderMode: 'nei',
        localIconPack: '',
        sidebarMode: 'expanded',
        effectsLevel: 'full',
        dashboard: null,
      },
    },
    {
      id: 'builtin-style-polaroid',
      name: t('presetStylePolaroid_name'),
      createdAt: 0,
      settings: {
        themeColor: 'light',
        themeLayout: 'wide',
        pageStyle: 'polaroid',
        lang: 'en',
        displayMode: 'merged',
        numberFormat: 'thousands',
        iconPack: 'default',
        iconRenderMode: 'nei',
        localIconPack: '',
        sidebarMode: 'expanded',
        effectsLevel: 'subtle',
        dashboard: null,
      },
    },
    {
      id: 'builtin-style-warp',
      name: t('presetStyleWarp_name'),
      createdAt: 0,
      settings: {
        themeColor: 'warp-void',
        themeLayout: 'split-chrome',
        pageStyle: 'warp',
        lang: 'en',
        displayMode: 'split',
        numberFormat: 'short',
        iconPack: 'default',
        iconRenderMode: 'nei',
        localIconPack: '',
        sidebarMode: 'expanded',
        effectsLevel: 'full',
        dashboard: null,
      },
    },
    {
      id: 'builtin-style-emberforge',
      name: t('presetStyleEmberforge_name'),
      createdAt: 0,
      settings: {
        themeColor: 'ember-crimson',
        themeLayout: 'standard',
        pageStyle: 'emberforge',
        lang: 'en',
        displayMode: 'split',
        numberFormat: 'short',
        iconPack: 'default',
        iconRenderMode: 'nei',
        localIconPack: '',
        sidebarMode: 'expanded',
        effectsLevel: 'full',
        dashboard: null,
      },
    },
    {
      id: 'builtin-style-frostglass',
      name: t('presetStyleFrostglass_name'),
      createdAt: 0,
      settings: {
        themeColor: 'frost-ice',
        themeLayout: 'wide',
        pageStyle: 'frostglass',
        lang: 'en',
        displayMode: 'split',
        numberFormat: 'short',
        iconPack: 'default',
        iconRenderMode: 'nei',
        localIconPack: '',
        sidebarMode: 'expanded',
        effectsLevel: 'full',
        dashboard: null,
      },
    },
    {
      id: 'builtin-style-noirfilm',
      name: t('presetStyleNoirfilm_name'),
      createdAt: 0,
      settings: {
        themeColor: 'noir-silver',
        themeLayout: 'compact',
        pageStyle: 'noirfilm',
        lang: 'en',
        displayMode: 'split',
        numberFormat: 'short',
        iconPack: 'default',
        iconRenderMode: 'nei',
        localIconPack: '',
        sidebarMode: 'expanded',
        effectsLevel: 'subtle',
        dashboard: null,
      },
    },
    {
      id: 'builtin-style-emerald-circuit',
      name: t('presetStyleEmeraldCircuit_name'),
      createdAt: 0,
      settings: {
        themeColor: 'emerald-teal',
        themeLayout: 'standard',
        pageStyle: 'emerald-circuit',
        lang: 'en',
        displayMode: 'split',
        numberFormat: 'short',
        iconPack: 'default',
        iconRenderMode: 'nei',
        localIconPack: '',
        sidebarMode: 'expanded',
        effectsLevel: 'full',
        dashboard: null,
      },
    },
    {
      id: 'builtin-style-desert-terminal',
      name: t('presetStyleDesertTerminal_name'),
      createdAt: 0,
      settings: {
        themeColor: 'desert-sand',
        themeLayout: 'wide',
        pageStyle: 'desert-terminal',
        lang: 'en',
        displayMode: 'split',
        numberFormat: 'short',
        iconPack: 'default',
        iconRenderMode: 'nei',
        localIconPack: '',
        sidebarMode: 'expanded',
        effectsLevel: 'subtle',
        dashboard: null,
      },
    },
    {
      id: 'builtin-style-lunar',
      name: t('presetStyleLunar_name'),
      createdAt: 0,
      settings: {
        themeColor: 'lunar-grey',
        themeLayout: 'floating',
        pageStyle: 'lunar',
        lang: 'en',
        displayMode: 'split',
        numberFormat: 'short',
        iconPack: 'default',
        iconRenderMode: 'nei',
        localIconPack: '',
        sidebarMode: 'expanded',
        effectsLevel: 'subtle',
        dashboard: null,
      },
    },
    {
      id: 'builtin-style-coral-reef',
      name: t('presetStyleCoralReef_name'),
      createdAt: 0,
      settings: {
        themeColor: 'reef-coral',
        themeLayout: 'standard',
        pageStyle: 'coral-reef',
        lang: 'en',
        displayMode: 'split',
        numberFormat: 'short',
        iconPack: 'default',
        iconRenderMode: 'nei',
        localIconPack: '',
        sidebarMode: 'expanded',
        effectsLevel: 'full',
        dashboard: null,
      },
    },
    {
      id: 'builtin-style-papercraft',
      name: t('presetStylePapercraft_name'),
      createdAt: 0,
      settings: {
        themeColor: 'kraft-brown',
        themeLayout: 'sidebar-right',
        pageStyle: 'papercraft',
        lang: 'en',
        displayMode: 'split',
        numberFormat: 'short',
        iconPack: 'default',
        iconRenderMode: 'nei',
        localIconPack: '',
        sidebarMode: 'expanded',
        effectsLevel: 'subtle',
        dashboard: null,
      },
    },
    {
      id: 'builtin-style-neon-tokyo',
      name: t('presetStyleNeonTokyo_name'),
      createdAt: 0,
      settings: {
        themeColor: 'tokyo-neon',
        themeLayout: 'compact',
        pageStyle: 'neon-tokyo',
        lang: 'en',
        displayMode: 'split',
        numberFormat: 'short',
        iconPack: 'default',
        iconRenderMode: 'nei',
        localIconPack: '',
        sidebarMode: 'expanded',
        effectsLevel: 'full',
        dashboard: null,
      },
    },
    {
      id: 'builtin-style-medieval',
      name: t('presetStyleMedieval_name'),
      createdAt: 0,
      settings: {
        themeColor: 'parchment-gold',
        themeLayout: 'standard',
        pageStyle: 'medieval',
        lang: 'en',
        displayMode: 'split',
        numberFormat: 'short',
        iconPack: 'default',
        iconRenderMode: 'nei',
        localIconPack: '',
        sidebarMode: 'expanded',
        effectsLevel: 'subtle',
        dashboard: null,
      },
    },
    {
      id: 'builtin-style-biotank',
      name: t('presetStyleBiotank_name'),
      createdAt: 0,
      settings: {
        themeColor: 'bio-green',
        themeLayout: 'wide',
        pageStyle: 'biotank',
        lang: 'en',
        displayMode: 'split',
        numberFormat: 'short',
        iconPack: 'default',
        iconRenderMode: 'nei',
        localIconPack: '',
        sidebarMode: 'expanded',
        effectsLevel: 'full',
        dashboard: null,
      },
    },
    {
      id: 'builtin-style-stardust',
      name: t('presetStyleStardust_name'),
      createdAt: 0,
      settings: {
        themeColor: 'stardust-violet',
        themeLayout: 'floating',
        pageStyle: 'stardust',
        lang: 'en',
        displayMode: 'split',
        numberFormat: 'short',
        iconPack: 'default',
        iconRenderMode: 'nei',
        localIconPack: '',
        sidebarMode: 'expanded',
        effectsLevel: 'full',
        dashboard: null,
      },
    },
    {
      id: 'builtin-style-coppersteam',
      name: t('presetStyleCoppersteam_name'),
      createdAt: 0,
      settings: {
        themeColor: 'copper-rust',
        themeLayout: 'standard',
        pageStyle: 'coppersteam',
        lang: 'en',
        displayMode: 'split',
        numberFormat: 'short',
        iconPack: 'default',
        iconRenderMode: 'nei',
        localIconPack: '',
        sidebarMode: 'expanded',
        effectsLevel: 'subtle',
        dashboard: null,
      },
    },
    {
      id: 'builtin-style-cleanlab',
      name: t('presetStyleCleanlab_name'),
      createdAt: 0,
      settings: {
        themeColor: 'lab-white',
        themeLayout: 'wide',
        pageStyle: 'cleanlab',
        lang: 'en',
        displayMode: 'split',
        numberFormat: 'short',
        iconPack: 'default',
        iconRenderMode: 'nei',
        localIconPack: '',
        sidebarMode: 'expanded',
        effectsLevel: 'none',
        dashboard: null,
      },
    },
    {
      id: 'builtin-style-abyss',
      name: t('presetStyleAbyss_name'),
      createdAt: 0,
      settings: {
        themeColor: 'abyss-deep',
        themeLayout: 'split-chrome',
        pageStyle: 'abyss',
        lang: 'en',
        displayMode: 'split',
        numberFormat: 'short',
        iconPack: 'default',
        iconRenderMode: 'nei',
        localIconPack: '',
        sidebarMode: 'expanded',
        effectsLevel: 'full',
        dashboard: null,
      },
    },
    {
      id: 'builtin-style-candypop',
      name: t('presetStyleCandypop_name'),
      createdAt: 0,
      settings: {
        themeColor: 'candy-pastel',
        themeLayout: 'floating',
        pageStyle: 'candypop',
        lang: 'en',
        displayMode: 'split',
        numberFormat: 'short',
        iconPack: 'default',
        iconRenderMode: 'nei',
        localIconPack: '',
        sidebarMode: 'expanded',
        effectsLevel: 'subtle',
        dashboard: null,
      },
    },
    {
      id: 'builtin-style-military',
      name: t('presetStyleMilitary_name'),
      createdAt: 0,
      settings: {
        themeColor: 'mil-olive',
        themeLayout: 'bottomnav',
        pageStyle: 'military',
        lang: 'en',
        displayMode: 'split',
        numberFormat: 'short',
        iconPack: 'default',
        iconRenderMode: 'nei',
        localIconPack: '',
        sidebarMode: 'expanded',
        effectsLevel: 'subtle',
        dashboard: null,
      },
    },
    {
      id: 'builtin-style-retrocrit',
      name: t('presetStyleRetrocrit_name'),
      createdAt: 0,
      settings: {
        themeColor: 'crt-green',
        themeLayout: 'compact',
        pageStyle: 'retrocrit',
        lang: 'en',
        displayMode: 'split',
        numberFormat: 'short',
        iconPack: 'default',
        iconRenderMode: 'nei',
        localIconPack: '',
        sidebarMode: 'expanded',
        effectsLevel: 'full',
        dashboard: null,
      },
    },
    {
      id: 'builtin-style-terracotta',
      name: t('presetStyleTerracotta_name'),
      createdAt: 0,
      settings: {
        themeColor: 'clay-terra',
        themeLayout: 'sidebar-right',
        pageStyle: 'terracotta',
        lang: 'en',
        displayMode: 'split',
        numberFormat: 'short',
        iconPack: 'default',
        iconRenderMode: 'nei',
        localIconPack: '',
        sidebarMode: 'expanded',
        effectsLevel: 'subtle',
        dashboard: null,
      },
    },
    {
      id: 'builtin-style-prism',
      name: t('presetStylePrism_name'),
      createdAt: 0,
      settings: {
        themeColor: 'prism-spectrum',
        themeLayout: 'wide',
        pageStyle: 'prism',
        lang: 'en',
        displayMode: 'split',
        numberFormat: 'short',
        iconPack: 'default',
        iconRenderMode: 'nei',
        localIconPack: '',
        sidebarMode: 'expanded',
        effectsLevel: 'full',
        dashboard: null,
      },
    },
  ];
}

/** Migrate legacy widget configs (sparkline → lineChart, array category colors → named). Recurses into group children. */
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
    if (next.type === 'group') {
      next = {
        ...next,
        dataSource: next.dataSource || 'none',
        children: migrateDashboardWidgets(next.children || []),
      };
    }
    if (
      next.type === 'textNote'
      || next.type === 'spacer'
      || next.type === 'alertsSummary'
      || next.type === 'craftingQueue'
    ) {
      next = { ...next, dataSource: next.dataSource || 'none' };
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
      chartStretchMode: parsed.chartStretchMode ?? DEFAULT_DASHBOARD_SETTINGS.chartStretchMode,
      defaultChartStyle: parsed.defaultChartStyle ?? DEFAULT_DASHBOARD_SETTINGS.defaultChartStyle,
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
