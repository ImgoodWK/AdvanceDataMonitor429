import type { DashboardWidgetConfig } from '@/utils/presets';

/** Clone a widget with a new id, offset x by 1. */
export function copyWidgetConfig(widget: DashboardWidgetConfig, idPrefix = 'w-'): DashboardWidgetConfig {
  return {
    ...widget,
    id: idPrefix + Date.now(),
    x: widget.x + 1,
    title: widget.title ? widget.title + ' (copy)' : widget.title,
    colors: widget.colors ? { ...widget.colors } : undefined,
  };
}

export function exportWidgetsJson(widgets: DashboardWidgetConfig[]): string {
  return JSON.stringify({ version: 1, widgets }, null, 2);
}

export function parseWidgetsImport(raw: string): DashboardWidgetConfig[] {
  const parsed = JSON.parse(raw) as { widgets?: DashboardWidgetConfig[] } | DashboardWidgetConfig[];
  const list = Array.isArray(parsed) ? parsed : parsed.widgets;
  if (!Array.isArray(list) || list.length === 0) {
    throw new Error('invalid widgets json');
  }
  return list.map((w, i) => ({
    ...w,
    id: w.id || 'w-import-' + Date.now() + '-' + i,
  }));
}
