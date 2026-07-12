import type { DashboardPin, DashboardWidgetConfig } from '@/utils/presets';

/** Default / optional columns for each list data source. */
export const DATA_TABLE_COLUMNS: Record<string, { key: string; defaultVisible: boolean }[]> = {
  topItems: [
    { key: 'icon', defaultVisible: true },
    { key: 'name', defaultVisible: true },
    { key: 'amount', defaultVisible: true },
    { key: 'registryName', defaultVisible: false },
  ],
  cpuList: [
    { key: 'name', defaultVisible: true },
    { key: 'status', defaultVisible: true },
    { key: 'coProcessors', defaultVisible: true },
    { key: 'storage', defaultVisible: true },
    { key: 'items', defaultVisible: true },
    { key: 'elapsedTime', defaultVisible: true },
    { key: 'finalOutput', defaultVisible: true },
  ],
  gtMachineList: [
    { key: 'recipe', defaultVisible: true },
    { key: 'status', defaultVisible: true },
    { key: 'coords', defaultVisible: true },
    { key: 'progress', defaultVisible: true },
    { key: 'parallel', defaultVisible: true },
    { key: 'output', defaultVisible: true },
  ],
  networkBalance: [
    { key: 'resource', defaultVisible: true },
    { key: 'type', defaultVisible: true },
    { key: 'needy', defaultVisible: true },
    { key: 'surplus', defaultVisible: true },
    { key: 'gap', defaultVisible: true },
  ],
  customPins: [
    { key: 'icon', defaultVisible: true },
    { key: 'name', defaultVisible: true },
    { key: 'amount', defaultVisible: true },
    { key: 'kind', defaultVisible: true },
  ],
};

export function defaultColumnsFor(dataSource: string): string[] {
  const cols = DATA_TABLE_COLUMNS[dataSource];
  if (!cols) return [];
  return cols.filter((c) => c.defaultVisible).map((c) => c.key);
}

export function resolveColumns(widget: DashboardWidgetConfig): string[] {
  if (widget.columns && widget.columns.length > 0) {
    return widget.columns;
  }
  return defaultColumnsFor(widget.dataSource);
}

export function clampContentScale(scale: number | undefined): number {
  if (scale == null || Number.isNaN(scale)) return 1;
  return Math.min(2, Math.max(0.5, scale));
}

/**
 * Effective scale for a widget cell. When the user left contentScale at default (1),
 * shrink automatically for very small cells (approx cellHeight 64).
 */
export function effectiveContentScale(
  widget: DashboardWidgetConfig,
  cellWidthPx: number,
  cellHeightPx: number
): number {
  const manual = clampContentScale(widget.contentScale);
  const autoShrink = widget.contentScale == null || widget.contentScale === 1;
  if (!autoShrink) return manual;
  const area = Math.max(1, cellWidthPx * cellHeightPx);
  // Reference: ~3x2 at 64px → ~3*80 * 2*64 ≈ 30000; 1x1 → ~80*64 ≈ 5120
  if (area < 6000) return Math.max(0.55, manual * 0.55);
  if (area < 12000) return Math.max(0.7, manual * 0.75);
  return manual;
}

export function pinKey(pin: DashboardPin): string {
  return `${pin.kind}:${pin.id}`;
}
