import type { DashboardWidgetConfig } from '@/utils/presets';
import { flattenWidgets } from '@/utils/dashboardTree';

/** Clone a widget with a new id, offset x by 1. Recurses into group children. */
export function copyWidgetConfig(widget: DashboardWidgetConfig, idPrefix = 'w-'): DashboardWidgetConfig {
  const stamp = Date.now();
  const copyOne = (w: DashboardWidgetConfig, suffix: string): DashboardWidgetConfig => {
    const next: DashboardWidgetConfig = {
      ...w,
      id: idPrefix + stamp + suffix,
      x: w.x + 1,
      title: w.title ? w.title + ' (copy)' : w.title,
      colors: w.colors ? { ...w.colors } : undefined,
      pins: w.pins ? w.pins.map((p) => ({ ...p })) : [],
      radarAxes: w.radarAxes ? w.radarAxes.map((a) => ({ ...a })) : undefined,
      columns: w.columns ? [...w.columns] : undefined,
    };
    if (w.type === 'group') {
      next.children = (w.children || []).map((c, i) => copyOne(c, `${suffix}-c${i}`));
    }
    return next;
  };
  return copyOne(widget, '');
}

export function exportWidgetsJson(widgets: DashboardWidgetConfig[]): string {
  return JSON.stringify({ version: 2, widgets }, null, 2);
}

export function parseWidgetsImport(raw: string): DashboardWidgetConfig[] {
  const parsed = JSON.parse(raw) as { widgets?: DashboardWidgetConfig[] } | DashboardWidgetConfig[];
  const list = Array.isArray(parsed) ? parsed : parsed.widgets;
  if (!Array.isArray(list) || list.length === 0) {
    throw new Error('invalid widgets json');
  }
  const stamp = Date.now();
  const assignIds = (w: DashboardWidgetConfig, i: string): DashboardWidgetConfig => {
    const id = w.id || `w-import-${stamp}-${i}`;
    const next: DashboardWidgetConfig = { ...w, id };
    if (w.type === 'group') {
      next.children = (w.children || []).map((c, j) => assignIds(c, `${i}-${j}`));
    }
    return next;
  };
  return list.map((w, i) => assignIds(w, String(i)));
}

/** Count leaf + group widgets for UI hints. */
export function countWidgetsDeep(widgets: DashboardWidgetConfig[]): number {
  return flattenWidgets(widgets).length;
}
