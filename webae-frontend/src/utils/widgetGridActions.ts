import type { DashboardWidgetConfig } from '@/utils/presets';
import { flattenWidgets } from '@/utils/dashboardTree';
import { createWidgetId } from '@/utils/widgetId';

const VALID_TYPES: ReadonlySet<string> = new Set([
  'statCard',
  'progressBar',
  'lineChart',
  'barChart',
  'pieChart',
  'dataTable',
  'gauge',
  'radarChart',
  'group',
  'textNote',
  'spacer',
  'alertsSummary',
  'craftingQueue',
  'networkHealth',
  'powerFlow',
  'storageMatrix',
  'machineFleet',
  'playerPresence',
  'activityStream',
  'serverVitals',
]);

const MAX_NEST_DEPTH = 4;
const MAX_WIDTH = 12;
const MAX_HEIGHT = 20;

export class WidgetImportError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'WidgetImportError';
  }
}

/** Clone a widget with a new id, offset x by 1. Recurses into group children. */
export function copyWidgetConfig(widget: DashboardWidgetConfig, idPrefix = 'w-'): DashboardWidgetConfig {
  const copyOne = (w: DashboardWidgetConfig): DashboardWidgetConfig => {
    const next: DashboardWidgetConfig = {
      ...w,
      id: createWidgetId(idPrefix),
      x: w.x + 1,
      title: w.title ? w.title + ' (copy)' : w.title,
      colors: w.colors ? { ...w.colors } : undefined,
      pins: w.pins ? w.pins.map((p) => ({ ...p })) : [],
      radarAxes: w.radarAxes ? w.radarAxes.map((a) => ({ ...a })) : undefined,
      columns: w.columns ? [...w.columns] : undefined,
    };
    if (w.type === 'group') {
      next.children = (w.children || []).map((c) => copyOne(c));
    }
    return next;
  };
  return copyOne(widget);
}

export function exportWidgetsJson(widgets: DashboardWidgetConfig[]): string {
  return JSON.stringify({ version: 2, widgets }, null, 2);
}

function clampInt(n: unknown, min: number, max: number, fallback: number): number {
  const v = typeof n === 'number' && Number.isFinite(n) ? Math.round(n) : fallback;
  return Math.max(min, Math.min(max, v));
}

function assertUniqueIds(widgets: DashboardWidgetConfig[]): void {
  const seen = new Set<string>();
  for (const w of flattenWidgets(widgets)) {
    if (!w.id || typeof w.id !== 'string') {
      throw new WidgetImportError('widget missing id');
    }
    if (seen.has(w.id)) {
      throw new WidgetImportError(`duplicate widget id: ${w.id}`);
    }
    seen.add(w.id);
  }
}

function sanitizeWidget(
  raw: unknown,
  depth: number,
  opts: { assignMissingIds: boolean; idPrefix: string }
): DashboardWidgetConfig {
  if (!raw || typeof raw !== 'object') {
    throw new WidgetImportError('invalid widget entry');
  }
  if (depth > MAX_NEST_DEPTH) {
    throw new WidgetImportError('widget nest depth exceeded');
  }
  const w = raw as Record<string, unknown>;
  const type = String(w.type || '');
  if (!VALID_TYPES.has(type)) {
    throw new WidgetImportError(`unsupported widget type: ${type || '(empty)'}`);
  }
  let id = typeof w.id === 'string' && w.id.trim() ? w.id.trim() : '';
  if (!id) {
    if (!opts.assignMissingIds) {
      throw new WidgetImportError('widget missing id');
    }
    id = createWidgetId(opts.idPrefix);
  }
  const width = clampInt(w.width, 1, MAX_WIDTH, 3);
  const height = clampInt(w.height, 1, MAX_HEIGHT, 2);
  const x = clampInt(w.x, 0, 200, 0);
  const y = clampInt(w.y, 0, 500, 0);

  const next: DashboardWidgetConfig = {
    ...(w as unknown as DashboardWidgetConfig),
    id,
    type: type as DashboardWidgetConfig['type'],
    dataSource: typeof w.dataSource === 'string' ? w.dataSource : 'none',
    scope: w.scope === 'global' ? 'global' : 'perNetwork',
    title: typeof w.title === 'string' ? w.title : '',
    width,
    height,
    x,
    y,
  };

  if (type === 'group') {
    const childrenRaw = Array.isArray(w.children) ? w.children : [];
    next.children = childrenRaw.map((c) => sanitizeWidget(c, depth + 1, opts));
  } else {
    next.children = undefined;
  }
  return next;
}

export interface ParseWidgetsImportOptions {
  /** When true, missing ids are filled; duplicate ids still rejected. Default true. */
  assignMissingIds?: boolean;
  idPrefix?: string;
  /** Allow empty widgets array (explicit empty layout). Default true. */
  allowEmpty?: boolean;
}

/**
 * Parse widgets JSON (array or `{ version, widgets }`).
 * Rejects duplicate ids and illegal structure; clamps size/coords when safe.
 */
export function parseWidgetsImport(
  raw: string,
  options: ParseWidgetsImportOptions = {}
): DashboardWidgetConfig[] {
  const assignMissingIds = options.assignMissingIds !== false;
  const idPrefix = options.idPrefix || 'w-import-';
  const allowEmpty = options.allowEmpty !== false;

  let parsed: unknown;
  try {
    parsed = JSON.parse(raw);
  } catch {
    throw new WidgetImportError('invalid json');
  }

  const list: unknown = Array.isArray(parsed)
    ? parsed
    : parsed && typeof parsed === 'object' && Array.isArray((parsed as { widgets?: unknown }).widgets)
      ? (parsed as { widgets: unknown[] }).widgets
      : null;

  if (!Array.isArray(list)) {
    throw new WidgetImportError('invalid widgets json');
  }
  if (list.length === 0) {
    if (!allowEmpty) throw new WidgetImportError('empty widgets');
    return [];
  }

  const widgets = list.map((item) =>
    sanitizeWidget(item, 0, { assignMissingIds, idPrefix })
  );
  assertUniqueIds(widgets);
  return widgets;
}

/** Count leaf + group widgets for UI hints. */
export function countWidgetsDeep(widgets: DashboardWidgetConfig[]): number {
  return flattenWidgets(widgets).length;
}

/** Collect all ids; returns first duplicate or null. */
export function findDuplicateWidgetId(widgets: DashboardWidgetConfig[]): string | null {
  const seen = new Set<string>();
  for (const w of flattenWidgets(widgets)) {
    if (seen.has(w.id)) return w.id;
    seen.add(w.id);
  }
  return null;
}
