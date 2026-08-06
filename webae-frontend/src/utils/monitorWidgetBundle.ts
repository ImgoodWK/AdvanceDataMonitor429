import type { MonitorSourceKind, MonitorWidgetKind } from '@/types/dto';
import type { DashboardPin, DashboardWidgetConfig } from '@/utils/presets';
import { flattenWidgets } from '@/utils/dashboardTree';
import { createWidgetId } from '@/utils/widgetId';

export const MONITOR_WIDGET_BUNDLE_FORMAT = 'textech-monitor-widget-bundle' as const;
export const MONITOR_WIDGET_BUNDLE_VERSION = 1 as const;
export const MAX_MONITOR_WIDGETS = 36;

export const SHARED_MONITOR_KINDS: ReadonlySet<string> = new Set([
  'statCard',
  'progressBar',
  'gauge',
  'lineChart',
  'barChart',
  'pieChart',
  'dataTable',
]);

export interface MonitorWidgetSpec {
  kind: Extract<MonitorWidgetKind, 'statCard' | 'progressBar' | 'gauge' | 'lineChart' | 'barChart' | 'pieChart' | 'dataTable'>;
  sourceKind: MonitorSourceKind;
  metricKey: string;
  title: string;
  style?: string;
  targetValue?: number;
  pins?: DashboardPin[];
  /** Undefined = default columns; [] = explicitly hide all columns. */
  columns?: string[];
  sortMode?: string;
  maxRows?: number;
  colors?: DashboardWidgetConfig['colors'];
  seriesTransform?: string;
}

export interface MonitorWidgetBundle {
  format: typeof MONITOR_WIDGET_BUNDLE_FORMAT;
  version: typeof MONITOR_WIDGET_BUNDLE_VERSION;
  title: string;
  exportedAt: number;
  widgets: MonitorWidgetSpec[];
}

export class MonitorWidgetBundleError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'MonitorWidgetBundleError';
  }
}

export function inferMonitorSourceKind(metricKey: string): MonitorSourceKind {
  const key = metricKey.toLowerCase();
  if (key.startsWith('steam')) return 'wireless_steam';
  if (key.startsWith('eu')) return 'wireless_eu';
  if (key.includes('machine') || key.startsWith('gt')) return 'gt_summary';
  if (key.includes('storage') || key.includes('item') || key.includes('fluid') || key.includes('essentia')) {
    return 'storage_summary';
  }
  if (key.includes('cpu') || key.includes('craft')) return 'ae_metric';
  return 'ae_metric';
}

export function buildMonitorWidgetBundle(
  widgets: DashboardWidgetConfig[],
  title = 'WebAE dashboard',
  exportedAt = Date.now()
): MonitorWidgetBundle {
  const shared = flattenWidgets(widgets).filter((widget) => SHARED_MONITOR_KINDS.has(widget.type));
  if (shared.length > MAX_MONITOR_WIDGETS) {
    throw new MonitorWidgetBundleError(`monitor widget limit exceeded: ${shared.length}/${MAX_MONITOR_WIDGETS}`);
  }
  return {
    format: MONITOR_WIDGET_BUNDLE_FORMAT,
    version: MONITOR_WIDGET_BUNDLE_VERSION,
    title,
    exportedAt,
    widgets: shared.map(toSpec),
  };
}

export function exportMonitorWidgetBundleJson(
  widgets: DashboardWidgetConfig[],
  title?: string,
  exportedAt?: number
): string {
  return JSON.stringify(buildMonitorWidgetBundle(widgets, title, exportedAt), null, 2);
}

export function parseMonitorWidgetBundle(raw: string): DashboardWidgetConfig[] {
  let parsed: unknown;
  try {
    parsed = JSON.parse(raw);
  } catch {
    throw new MonitorWidgetBundleError('invalid json');
  }
  if (!parsed || typeof parsed !== 'object') throw new MonitorWidgetBundleError('invalid bundle');
  const bundle = parsed as Record<string, unknown>;
  if (bundle.format !== MONITOR_WIDGET_BUNDLE_FORMAT || bundle.version !== MONITOR_WIDGET_BUNDLE_VERSION) {
    throw new MonitorWidgetBundleError('unsupported monitor widget bundle');
  }
  if (!Array.isArray(bundle.widgets)) throw new MonitorWidgetBundleError('widgets must be an array');
  if (bundle.widgets.length > MAX_MONITOR_WIDGETS) {
    throw new MonitorWidgetBundleError(`monitor widget limit exceeded: ${bundle.widgets.length}/${MAX_MONITOR_WIDGETS}`);
  }
  return bundle.widgets.map((value, index) => fromSpec(validateSpec(value), index));
}

export function looksLikeMonitorWidgetBundle(raw: string): boolean {
  try {
    const parsed = JSON.parse(raw) as { format?: unknown };
    return parsed?.format === MONITOR_WIDGET_BUNDLE_FORMAT;
  } catch {
    return false;
  }
}

function toSpec(widget: DashboardWidgetConfig): MonitorWidgetSpec {
  const metricKey = widget.metricKey || widget.dataSource;
  const spec: MonitorWidgetSpec = {
    kind: widget.type as MonitorWidgetSpec['kind'],
    sourceKind: widget.sourceKind || inferMonitorSourceKind(metricKey),
    metricKey,
    title: widget.title || metricKey,
  };
  if (widget.style !== undefined) spec.style = widget.style;
  if (widget.targetValue !== undefined) spec.targetValue = widget.targetValue;
  if (widget.pins !== undefined) spec.pins = widget.pins.map((pin) => ({ ...pin }));
  if (widget.columns !== undefined) spec.columns = [...widget.columns];
  if (widget.sortMode !== undefined) spec.sortMode = widget.sortMode;
  if (widget.maxRows !== undefined) spec.maxRows = widget.maxRows;
  if (widget.colors !== undefined) spec.colors = { ...widget.colors };
  if (widget.seriesTransform !== undefined) spec.seriesTransform = widget.seriesTransform;
  return spec;
}

function validateSpec(value: unknown): MonitorWidgetSpec {
  if (!value || typeof value !== 'object') throw new MonitorWidgetBundleError('invalid widget entry');
  const raw = value as Record<string, unknown>;
  const kind = String(raw.kind || '');
  if (!SHARED_MONITOR_KINDS.has(kind)) throw new MonitorWidgetBundleError(`unsupported widget kind: ${kind}`);
  const sourceKind = String(raw.sourceKind || '');
  const validSources: ReadonlySet<string> = new Set([
    'tile_metric', 'ae_metric', 'wireless_eu', 'wireless_steam', 'storage_summary', 'gt_summary',
  ]);
  if (!validSources.has(sourceKind)) throw new MonitorWidgetBundleError(`unsupported source kind: ${sourceKind}`);
  const columns = raw.columns;
  if (columns !== undefined && columns !== null
    && (!Array.isArray(columns) || columns.some((column) => typeof column !== 'string'))) {
    throw new MonitorWidgetBundleError('columns must be a string array');
  }
  return {
    ...(raw as unknown as MonitorWidgetSpec),
    kind: kind as MonitorWidgetSpec['kind'],
    sourceKind: sourceKind as MonitorSourceKind,
    metricKey: typeof raw.metricKey === 'string' ? raw.metricKey : '',
    title: typeof raw.title === 'string' ? raw.title : '',
    columns: columns == null ? undefined : [...(columns as string[])],
    pins: Array.isArray(raw.pins) ? (raw.pins as DashboardPin[]).map((pin) => ({ ...pin })) : undefined,
  };
}

function fromSpec(spec: MonitorWidgetSpec, index: number): DashboardWidgetConfig {
  return {
    id: createWidgetId('w-monitor-'),
    type: spec.kind,
    dataSource: spec.metricKey || 'none',
    sourceKind: spec.sourceKind,
    metricKey: spec.metricKey,
    scope: 'perNetwork',
    title: spec.title,
    style: spec.style,
    targetValue: spec.targetValue,
    pins: spec.pins?.map((pin) => ({ ...pin })),
    columns: spec.columns === undefined ? undefined : [...spec.columns],
    sortMode: spec.sortMode,
    maxRows: spec.maxRows,
    colors: spec.colors ? { ...spec.colors } : undefined,
    seriesTransform: spec.seriesTransform,
    width: 4,
    height: spec.kind === 'dataTable' ? 3 : 2,
    x: (index % 3) * 4,
    y: Math.floor(index / 3) * 2,
  };
}
