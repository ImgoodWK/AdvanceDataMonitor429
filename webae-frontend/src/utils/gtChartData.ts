import type { GtMachineDto } from '@/types/dto';
import {
  classifyGtStatus,
  getGtStatusBreakdown,
  type ChartCategory,
} from '@/utils/overviewDataSources';

export { classifyGtStatus, getGtStatusBreakdown };
export type { ChartCategory };

const STATUS_COLORS: Record<string, string> = {
  active: 'var(--success, #52c41a)',
  error: 'var(--error, #ff4d4f)',
  idle: 'var(--text-dim, #8c8c8c)',
};

const RECIPE_PALETTE = [
  'var(--primary, #1677ff)',
  'var(--success, #52c41a)',
  'var(--warning, #faad14)',
  'var(--accent, #722ed1)',
  '#13c2c2',
  '#eb2f96',
  '#2f54eb',
  '#a0d911',
];

/** Resolve chart segment color for GT summary widgets. */
export function gtCategoryColor(cat: ChartCategory, index: number): string {
  if (cat.colorKey && STATUS_COLORS[cat.colorKey]) {
    return STATUS_COLORS[cat.colorKey];
  }
  return RECIPE_PALETTE[index % RECIPE_PALETTE.length];
}

/** Top recipe maps by machine count; remainder grouped as "Other". */
export function getGtRecipeMapBreakdown(
  machines: GtMachineDto[] | undefined,
  t: (key: string) => string,
  maxSlices = 8
): ChartCategory[] {
  const counts = new Map<string, number>();
  for (const m of machines || []) {
    const name = (m.recipeMapName || '').trim() || t('gtUnknownRecipeMap');
    counts.set(name, (counts.get(name) || 0) + 1);
  }
  if (counts.size === 0) return [];

  const sorted = [...counts.entries()].sort((a, b) => b[1] - a[1]);
  const top = sorted.slice(0, maxSlices);
  const other = sorted.slice(maxSlices).reduce((sum, [, v]) => sum + v, 0);

  const result: ChartCategory[] = top.map(([label, value]) => ({ label, value }));
  if (other > 0) {
    result.push({
      label: t('gtRecipeMapOther'),
      value: other,
      colorKey: 'idle',
    });
  }
  return result;
}

/** Whether a machine row should be visually highlighted as error/problem. */
export function isGtMachineErrorRow(machine: GtMachineDto): boolean {
  return (
    machine.statusText === 'Error' ||
    machine.statusText === 'Problem' ||
    machine.errorId !== 0
  );
}
