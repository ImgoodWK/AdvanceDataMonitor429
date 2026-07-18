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
  /** dataTable visible column keys. Undefined = defaults; empty = deliberately hide all. */
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
    // Preserve explicit empty layouts; only fall back when widgets is missing.
    widgets: Array.isArray(parsed.widgets) ? parsed.widgets : defaults.widgets,
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
    {
      id: 'builtin-style-notion-paper',
      name: t('presetStyleNotionPaper_name'),
      createdAt: 0,
      settings: {
        themeColor: 'notion-warm',
        themeLayout: 'dual-rail',
        pageStyle: 'notion-paper',
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
      id: 'builtin-style-figma-canvas',
      name: t('presetStyleFigmaCanvas_name'),
      createdAt: 0,
      settings: {
        themeColor: 'figma-violet',
        themeLayout: 'rail-only',
        pageStyle: 'figma-canvas',
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
      id: 'builtin-style-spotify-now',
      name: t('presetStyleSpotifyNow_name'),
      createdAt: 0,
      settings: {
        themeColor: 'spotify-green',
        themeLayout: 'dock',
        pageStyle: 'spotify-now',
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
      id: 'builtin-style-discord-guild',
      name: t('presetStyleDiscordGuild_name'),
      createdAt: 0,
      settings: {
        themeColor: 'discord-blurple',
        themeLayout: 'split-pane',
        pageStyle: 'discord-guild',
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
      id: 'builtin-style-netflix-stage',
      name: t('presetStyleNetflixStage_name'),
      createdAt: 0,
      settings: {
        themeColor: 'netflix-red',
        themeLayout: 'theater',
        pageStyle: 'netflix-stage',
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
      id: 'builtin-style-github-primer',
      name: t('presetStyleGithubPrimer_name'),
      createdAt: 0,
      settings: {
        themeColor: 'github-canvas',
        themeLayout: 'top-tabs',
        pageStyle: 'github-primer',
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
      id: 'builtin-style-stripe-ledger',
      name: t('presetStyleStripeLedger_name'),
      createdAt: 0,
      settings: {
        themeColor: 'stripe-violet',
        themeLayout: 'dense-ops',
        pageStyle: 'stripe-ledger',
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
      id: 'builtin-style-openai-atelier',
      name: t('presetStyleOpenaiAtelier_name'),
      createdAt: 0,
      settings: {
        themeColor: 'openai-sage',
        themeLayout: 'zen',
        pageStyle: 'openai-atelier',
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
      id: 'builtin-style-tesla-cockpit',
      name: t('presetStyleTeslaCockpit_name'),
      createdAt: 0,
      settings: {
        themeColor: 'tesla-crimson',
        themeLayout: 'hud-frame',
        pageStyle: 'tesla-cockpit',
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
      id: 'builtin-style-uber-dispatch',
      name: t('presetStyleUberDispatch_name'),
      createdAt: 0,
      settings: {
        themeColor: 'uber-carbon',
        themeLayout: 'command',
        pageStyle: 'uber-dispatch',
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
      id: 'builtin-style-adobe-spectrum',
      name: t('presetStyleAdobeSpectrum_name'),
      createdAt: 0,
      settings: {
        themeColor: 'adobe-red',
        themeLayout: 'tri-chrome',
        pageStyle: 'adobe-spectrum',
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
      id: 'builtin-style-airbnb-stay',
      name: t('presetStyleAirbnbStay_name'),
      createdAt: 0,
      settings: {
        themeColor: 'airbnb-rausch',
        themeLayout: 'magazine',
        pageStyle: 'airbnb-stay',
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
      id: 'builtin-style-nintendo-switch',
      name: t('presetStyleNintendoSwitch_name'),
      createdAt: 0,
      settings: {
        themeColor: 'switch-neon',
        themeLayout: 'island',
        pageStyle: 'nintendo-switch',
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
      id: 'builtin-style-portal-chamber',
      name: t('presetStylePortalChamber_name'),
      createdAt: 0,
      settings: {
        themeColor: 'aperture-orange',
        themeLayout: 'frame',
        pageStyle: 'portal-chamber',
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
      id: 'builtin-style-zelda-sheikah',
      name: t('presetStyleZeldaSheikah_name'),
      createdAt: 0,
      settings: {
        themeColor: 'sheikah-cyan',
        themeLayout: 'status-strip',
        pageStyle: 'zelda-sheikah',
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
      id: 'builtin-style-valorant-spike',
      name: t('presetStyleValorantSpike_name'),
      createdAt: 0,
      settings: {
        themeColor: 'valorant-red',
        themeLayout: 'widescreen',
        pageStyle: 'valorant-spike',
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
      id: 'builtin-style-persona-velvet',
      name: t('presetStylePersonaVelvet_name'),
      createdAt: 0,
      settings: {
        themeColor: 'persona-duo',
        themeLayout: 'hero-header',
        pageStyle: 'persona-velvet',
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
      id: 'builtin-style-genshin-teyvat',
      name: t('presetStyleGenshinTeyvat_name'),
      createdAt: 0,
      settings: {
        themeColor: 'teyvat-gold',
        themeLayout: 'card-stack',
        pageStyle: 'genshin-teyvat',
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
      id: 'builtin-style-evangelion-nerv',
      name: t('presetStyleEvangelionNerv_name'),
      createdAt: 0,
      settings: {
        themeColor: 'nerv-purple',
        themeLayout: 'pipeline',
        pageStyle: 'evangelion-nerv',
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
      id: 'builtin-style-ghost-shell',
      name: t('presetStyleGhostShell_name'),
      createdAt: 0,
      settings: {
        themeColor: 'shell-teal',
        themeLayout: 'drawer-peek',
        pageStyle: 'ghost-shell',
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
      id: 'builtin-style-hades-styx',
      name: t('presetStyleHadesStyx_name'),
      createdAt: 0,
      settings: {
        themeColor: 'styx-laurel',
        themeLayout: 'corner-hub',
        pageStyle: 'hades-styx',
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
      id: 'builtin-style-cyberpunk-edge',
      name: t('presetStyleCyberpunkEdge_name'),
      createdAt: 0,
      settings: {
        themeColor: 'edgerunner-yellow',
        themeLayout: 'right-drawer',
        pageStyle: 'cyberpunk-edge',
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
      id: 'builtin-style-hollow-knight',
      name: t('presetStyleHollowKnight_name'),
      createdAt: 0,
      settings: {
        themeColor: 'hallownest-bone',
        themeLayout: 'standard',
        pageStyle: 'hollow-knight',
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
      id: 'builtin-style-celeste-summit',
      name: t('presetStyleCelesteSummit_name'),
      createdAt: 0,
      settings: {
        themeColor: 'celeste-dash',
        themeLayout: 'floating',
        pageStyle: 'celeste-summit',
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
      id: 'builtin-style-lol-rift',
      name: t('presetStyleLolRift_name'),
      createdAt: 0,
      settings: {
        themeColor: 'hextech-blue',
        themeLayout: 'wide',
        pageStyle: 'lol-rift',
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
      id: 'builtin-style-minecraft-craft',
      name: t('presetStyleMinecraftCraft_name'),
      createdAt: 0,
      settings: {
        themeColor: 'mc-grass',
        themeLayout: 'compact',
        pageStyle: 'minecraft-craft',
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
      id: 'builtin-style-ff14-scion',
      name: t('presetStyleFf14Scion_name'),
      createdAt: 0,
      settings: {
        themeColor: 'eorzea-gold',
        themeLayout: 'topnav',
        pageStyle: 'ff14-scion',
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
      id: 'builtin-style-witcher-path',
      name: t('presetStyleWitcherPath_name'),
      createdAt: 0,
      settings: {
        themeColor: 'kaer-morhen',
        themeLayout: 'sidebar-right',
        pageStyle: 'witcher-path',
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
      id: 'builtin-style-destiny-light',
      name: t('presetStyleDestinyLight_name'),
      createdAt: 0,
      settings: {
        themeColor: 'engram-violet',
        themeLayout: 'bottomnav',
        pageStyle: 'destiny-light',
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
      id: 'builtin-style-overwatch-wp',
      name: t('presetStyleOverwatchWp_name'),
      createdAt: 0,
      settings: {
        themeColor: 'ow-orange',
        themeLayout: 'standard',
        pageStyle: 'overwatch-wp',
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
      id: 'builtin-style-acnh-horizon',
      name: t('presetStyleAcnhHorizon_name'),
      createdAt: 0,
      settings: {
        themeColor: 'acnh-leaf',
        themeLayout: 'floating',
        pageStyle: 'acnh-horizon',
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
      id: 'builtin-style-stardew-farm',
      name: t('presetStyleStardewFarm_name'),
      createdAt: 0,
      settings: {
        themeColor: 'stardew-spring',
        themeLayout: 'compact',
        pageStyle: 'stardew-farm',
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
      id: 'builtin-style-elden-grace',
      name: t('presetStyleEldenGrace_name'),
      createdAt: 0,
      settings: {
        themeColor: 'elden-gold',
        themeLayout: 'wide',
        pageStyle: 'elden-grace',
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
      id: 'builtin-style-metroid-suit',
      name: t('presetStyleMetroidSuit_name'),
      createdAt: 0,
      settings: {
        themeColor: 'metroid-orange',
        themeLayout: 'topnav',
        pageStyle: 'metroid-suit',
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
      id: 'builtin-style-smash-blast',
      name: t('presetStyleSmashBlast_name'),
      createdAt: 0,
      settings: {
        themeColor: 'smash-impact',
        themeLayout: 'bottomnav',
        pageStyle: 'smash-blast',
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
      id: 'builtin-style-terraria-torch',
      name: t('presetStyleTerrariaTorch_name'),
      createdAt: 0,
      settings: {
        themeColor: 'terraria-night',
        themeLayout: 'standard',
        pageStyle: 'terraria-torch',
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
      id: 'builtin-style-ghibli-sky',
      name: t('presetStyleGhibliSky_name'),
      createdAt: 0,
      settings: {
        themeColor: 'ghibli-soft',
        themeLayout: 'floating',
        pageStyle: 'ghibli-sky',
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
      id: 'builtin-style-hashira-blade',
      name: t('presetStyleHashiraBlade_name'),
      createdAt: 0,
      settings: {
        themeColor: 'nichirin-orange',
        themeLayout: 'split-chrome',
        pageStyle: 'hashira-blade',
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
      id: 'builtin-style-jjk-domain',
      name: t('presetStyleJjkDomain_name'),
      createdAt: 0,
      settings: {
        themeColor: 'jjk-navy',
        themeLayout: 'wide',
        pageStyle: 'jjk-domain',
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
      id: 'builtin-style-onepiece-log',
      name: t('presetStyleOnepieceLog_name'),
      createdAt: 0,
      settings: {
        themeColor: 'op-wanted',
        themeLayout: 'wide',
        pageStyle: 'onepiece-log',
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
      id: 'builtin-style-sxf-forger',
      name: t('presetStyleSxfForger_name'),
      createdAt: 0,
      settings: {
        themeColor: 'sxf-pastel',
        themeLayout: 'standard',
        pageStyle: 'sxf-forger',
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
      id: 'builtin-style-aot-survey',
      name: t('presetStyleAotSurvey_name'),
      createdAt: 0,
      settings: {
        themeColor: 'aot-green',
        themeLayout: 'compact',
        pageStyle: 'aot-survey',
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
      id: 'builtin-style-sailor-crystal',
      name: t('presetStyleSailorCrystal_name'),
      createdAt: 0,
      settings: {
        themeColor: 'sailor-pastel',
        themeLayout: 'floating',
        pageStyle: 'sailor-crystal',
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
      id: 'builtin-style-monogatari-pop',
      name: t('presetStyleMonogatariPop_name'),
      createdAt: 0,
      settings: {
        themeColor: 'monogatari-yellow',
        themeLayout: 'wide',
        pageStyle: 'monogatari-pop',
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
      id: 'builtin-style-bebop-jazz',
      name: t('presetStyleBebopJazz_name'),
      createdAt: 0,
      settings: {
        themeColor: 'bebop-noir',
        themeLayout: 'sidebar-right',
        pageStyle: 'bebop-jazz',
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
      id: 'builtin-style-frieren-journey',
      name: t('presetStyleFrierenJourney_name'),
      createdAt: 0,
      settings: {
        themeColor: 'frieren-mint',
        themeLayout: 'wide',
        pageStyle: 'frieren-journey',
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
      id: 'builtin-style-bocchi-stage',
      name: t('presetStyleBocchiStage_name'),
      createdAt: 0,
      settings: {
        themeColor: 'bocchi-pink',
        themeLayout: 'bottomnav',
        pageStyle: 'bocchi-stage',
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
      id: 'builtin-style-meshi-feast',
      name: t('presetStyleMeshiFeast_name'),
      createdAt: 0,
      settings: {
        themeColor: 'meshi-amber',
        themeLayout: 'standard',
        pageStyle: 'meshi-feast',
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
      id: 'builtin-style-nvidia-greenroom',
      name: t('presetStyleNvidiaGreenroom_name'),
      createdAt: 0,
      settings: {
        themeColor: 'nvidia-green',
        themeLayout: 'topnav',
        pageStyle: 'nvidia-greenroom',
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
      id: 'builtin-style-linear-opsdesk',
      name: t('presetStyleLinearOpsdesk_name'),
      createdAt: 0,
      settings: {
        themeColor: 'linear-indigo',
        themeLayout: 'topnav',
        pageStyle: 'linear-opsdesk',
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
      id: 'builtin-printstream',
      name: t('presetPrintstream_name'),
      createdAt: 0,
      settings: {
        themeColor: 'printstream',
        themeLayout: 'hud-frame',
        pageStyle: 'printstream-panel',
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
      id: 'builtin-style-printstream-panel',
      name: t('presetStylePrintstreamPanel_name'),
      createdAt: 0,
      settings: {
        themeColor: 'printstream',
        themeLayout: 'hud-frame',
        pageStyle: 'printstream-panel',
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
      id: 'builtin-style-printstream-void',
      name: t('presetStylePrintstreamVoid_name'),
      createdAt: 0,
      settings: {
        themeColor: 'printstream-void',
        themeLayout: 'frame',
        pageStyle: 'printstream-void',
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
      id: 'builtin-style-printstream-pearl',
      name: t('presetStylePrintstreamPearl_name'),
      createdAt: 0,
      settings: {
        themeColor: 'printstream-pearl',
        themeLayout: 'floating',
        pageStyle: 'printstream-pearl',
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
      id: 'builtin-style-printstream-cyan',
      name: t('presetStylePrintstreamCyan_name'),
      createdAt: 0,
      settings: {
        themeColor: 'printstream-cyan',
        themeLayout: 'dense-ops',
        pageStyle: 'printstream-cyan',
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
      id: 'builtin-style-printstream-magenta',
      name: t('presetStylePrintstreamMagenta_name'),
      createdAt: 0,
      settings: {
        themeColor: 'printstream-magenta',
        themeLayout: 'theater',
        pageStyle: 'printstream-magenta',
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
      id: 'builtin-style-printstream-spectrum',
      name: t('presetStylePrintstreamSpectrum_name'),
      createdAt: 0,
      settings: {
        themeColor: 'printstream-spectrum',
        themeLayout: 'magazine',
        pageStyle: 'printstream-spectrum',
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
      id: 'builtin-style-printstream-ascii',
      name: t('presetStylePrintstreamAscii_name'),
      createdAt: 0,
      settings: {
        themeColor: 'printstream-ascii',
        themeLayout: 'dense-ops',
        pageStyle: 'printstream-ascii',
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
      id: 'builtin-style-printstream-cross',
      name: t('presetStylePrintstreamCross_name'),
      createdAt: 0,
      settings: {
        themeColor: 'printstream-cross',
        themeLayout: 'hud-frame',
        pageStyle: 'printstream-cross',
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
      id: 'builtin-style-printstream-rect',
      name: t('presetStylePrintstreamRect_name'),
      createdAt: 0,
      settings: {
        themeColor: 'printstream-rect',
        themeLayout: 'card-stack',
        pageStyle: 'printstream-rect',
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
      id: 'builtin-style-printstream-neon',
      name: t('presetStylePrintstreamNeon_name'),
      createdAt: 0,
      settings: {
        themeColor: 'printstream-neon',
        themeLayout: 'frame',
        pageStyle: 'printstream-neon',
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
      id: 'builtin-style-printstream-mono',
      name: t('presetStylePrintstreamMono_name'),
      createdAt: 0,
      settings: {
        themeColor: 'printstream-mono',
        themeLayout: 'standard',
        pageStyle: 'printstream-mono',
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
      id: 'builtin-style-printstream-gloss',
      name: t('presetStylePrintstreamGloss_name'),
      createdAt: 0,
      settings: {
        themeColor: 'printstream-gloss',
        themeLayout: 'floating',
        pageStyle: 'printstream-gloss',
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
      id: 'builtin-aura',
      name: t('presetAura_name'),
      createdAt: 0,
      settings: {
        themeColor: 'aura',
        themeLayout: 'floating',
        pageStyle: 'aura-voxel',
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
      id: 'builtin-style-aura-voxel',
      name: t('presetStyleAuraVoxel_name'),
      createdAt: 0,
      settings: {
        themeColor: 'aura',
        themeLayout: 'floating',
        pageStyle: 'aura-voxel',
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
      id: 'builtin-style-aura-spore',
      name: t('presetStyleAuraSpore_name'),
      createdAt: 0,
      settings: {
        themeColor: 'aura-front',
        themeLayout: 'floating',
        pageStyle: 'aura-spore',
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
      id: 'builtin-style-aura-dome',
      name: t('presetStyleAuraDome_name'),
      createdAt: 0,
      settings: {
        themeColor: 'aura-design',
        themeLayout: 'floating',
        pageStyle: 'aura-dome',
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
      id: 'builtin-style-aura-sparks',
      name: t('presetStyleAuraSparks_name'),
      createdAt: 0,
      settings: {
        themeColor: 'aura-sys',
        themeLayout: 'dense-ops',
        pageStyle: 'aura-sparks',
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
      id: 'builtin-style-aura-bubble',
      name: t('presetStyleAuraBubble_name'),
      createdAt: 0,
      settings: {
        themeColor: 'aura-interact',
        themeLayout: 'floating',
        pageStyle: 'aura-bubble',
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
      // Preserve explicit empty layouts; only fall back when widgets is missing.
      widgets: migrateDashboardWidgets(
        Array.isArray(parsed.widgets) ? parsed.widgets : DEFAULT_DASHBOARD_WIDGETS
      ),
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
