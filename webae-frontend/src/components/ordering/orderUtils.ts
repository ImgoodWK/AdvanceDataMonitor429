import type { OrderStatus, PatternListEntryDto } from '@/types/dto';

export const BROWSE_PAGE_SIZE = 80;

export type OrderTab = 'query' | 'patterns' | 'craftTree';
export type QueryScope = 'output' | 'input';
export type PatternViewMode = 'byProduct' | 'byPattern';

export interface QueryHit {
  key: string;
  label: string;
  subLabel?: string;
  iconId?: string;
  item?: import('@/types/dto').StorageItem;
  orderName: string;
  patternId?: string;
  kind: 'item' | 'fluid' | 'pattern';
}

export function patternEntryKey(p: PatternListEntryDto): string {
  return p.patternId || p.gridKey || '';
}

export function isGridPattern(p: PatternListEntryDto): boolean {
  return p.source === 'grid';
}

export function formatStorageBytes(n: number): string {
  if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(1)}M`;
  if (n >= 1_000) return `${(n / 1_000).toFixed(1)}K`;
  return String(n);
}

export function renderCpuTooltip(status: OrderStatus, t: (k: string) => string): string | undefined {
  if (!status.cpuInfo) return undefined;
  return t('orderCpuTooltip')
    .replace('{co}', String(status.cpuInfo.coProcessors))
    .replace('{storage}', formatStorageBytes(status.cpuInfo.storage))
    .replace('{parallel}', String(status.cpuInfo.parallelism));
}

export function patternMatchesQuery(p: PatternListEntryDto, q: string, scope: QueryScope): boolean {
  const entries = scope === 'output' ? p.outputs : (p.inputs || []).filter(Boolean);
  return entries.some((e) => {
    if (!e) return false;
    const name = `${e.displayName || ''} ${e.registryName || ''}`.toLowerCase();
    return name.includes(q);
  });
}

export function orderProgress(row: OrderStatus): number {
  if (row.status === 'completed') return row.finalProgress ?? 100;
  // cancelled / failed: keep last known step progress (not forced to 0)
  return Math.max(0, Math.min(100, row.progressPercent ?? 0));
}

export function orderProgressTooltip(row: OrderStatus, t: (k: string) => string): string {
  const base = t('orderProgressStepsHint');
  const parts: string[] = [base];
  if (row.startItems != null && row.startItems > 0) {
    parts.push(
      t('orderProgressStepsDetail')
        .replace('{done}', String(Math.max(0, row.startItems - (row.remainingItems ?? 0))))
        .replace('{total}', String(row.startItems))
    );
  }
  if (row.failReason) parts.push(row.failReason);
  if (row.cancelReason) parts.push(`${t('orderCancelReason')}: ${row.cancelReason}`);
  return parts.filter(Boolean).join('\n');
}
