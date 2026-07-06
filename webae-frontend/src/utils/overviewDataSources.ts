import type {
  GtMachineDto,
  GtMachineListDto,
  PowerDto,
  StorageDto,
  StorageCpu,
} from '@/types/dto';

/** Aggregated snapshot slice used by storage / CPU overview widgets. */
export interface OverviewSnapshot {
  items?: StorageDto['items'];
  fluids?: StorageDto['fluids'];
  essentia?: StorageDto['essentia'];
  bytesUsed: number;
  bytesMax: number;
  cpus: StorageCpu[];
  /** When set, overrides items.length for count widgets (paged storage API). */
  itemCount?: number;
  itemTotal?: number;
  fluidCount?: number;
  essentiaCount?: number;
}

/** Label + value pair for bar/pie dashboard charts. */
export interface ChartCategory {
  label: string;
  value: number;
  /** Optional token for color lookup (e.g. active / error / idle). */
  colorKey?: string;
}

export type GtStatusGroup = 'active' | 'error' | 'idle';

export interface NetworkCompareRow {
  networkId: number;
  bytesUsed: number;
  euStored: number;
  gtMachineCount: number;
}

export interface NetworkCompareMetric {
  key: 'bytesUsed' | 'euStored' | 'gtMachineCount';
  label: string;
  max: number;
  format: (v: number) => string;
}

/** Classify GT machine into active / error / idle buckets for dashboard charts. */
export function classifyGtStatus(machine: GtMachineDto): GtStatusGroup {
  if (machine.statusText === 'Running' || machine.isActive) return 'active';
  if (
    machine.statusText === 'Error' ||
    machine.statusText === 'Problem' ||
    machine.errorId !== 0
  ) {
    return 'error';
  }
  return 'idle';
}

export function getStorageCategoryBreakdown(
  snap: OverviewSnapshot | null,
  t: (key: string) => string
): ChartCategory[] {
  return [
    { label: t('items'), value: snap?.itemCount ?? snap?.items?.length ?? 0, colorKey: 'items' },
    { label: t('fluids'), value: snap?.fluidCount ?? snap?.fluids?.length ?? 0, colorKey: 'fluids' },
    { label: t('essentia'), value: snap?.essentiaCount ?? snap?.essentia?.length ?? 0, colorKey: 'essentia' },
  ];
}

export function getGtStatusBreakdown(
  machines: GtMachineDto[] | undefined,
  t: (key: string) => string
): ChartCategory[] {
  let active = 0;
  let error = 0;
  let idle = 0;
  for (const m of machines || []) {
    const group = classifyGtStatus(m);
    if (group === 'active') active++;
    else if (group === 'error') error++;
    else idle++;
  }
  return [
    { label: t('gtStatusActive'), value: active, colorKey: 'active' },
    { label: t('gtStatusError'), value: error, colorKey: 'error' },
    { label: t('gtStatusIdle'), value: idle, colorKey: 'idle' },
  ];
}

export function getGtMachinesForTable(
  gt: GtMachineListDto | null | undefined,
  maxRows: number
): GtMachineDto[] {
  if (!gt?.machines?.length) return [];
  return gt.machines.slice(0, maxRows);
}

export function buildNetworkCompareRows(
  networkIds: number[],
  storageMap: Record<number, StorageDto>,
  powerMap: Record<number, PowerDto>,
  gtMap: Record<number, GtMachineListDto>
): NetworkCompareRow[] {
  const rows: NetworkCompareRow[] = [];
  for (const networkId of networkIds) {
    rows.push({
      networkId,
      bytesUsed: storageMap[networkId]?.bytesUsed || 0,
      euStored: powerMap[networkId]?.euStored || 0,
      gtMachineCount: gtMap[networkId]?.machines?.length || 0,
    });
  }
  return rows;
}

export function buildNetworkCompareMetrics(
  rows: NetworkCompareRow[],
  t: (key: string) => string,
  formatBytes: (n: number) => string,
  fmtNum: (n: number) => string
): NetworkCompareMetric[] {
  const maxBytes = Math.max(...rows.map((r) => r.bytesUsed), 1);
  const maxEu = Math.max(...rows.map((r) => r.euStored), 1);
  const maxGt = Math.max(...rows.map((r) => r.gtMachineCount), 1);
  return [
    {
      key: 'bytesUsed',
      label: t('dataSource_bytesUsed'),
      max: maxBytes,
      format: formatBytes,
    },
    {
      key: 'euStored',
      label: t('dataSource_euStored'),
      max: maxEu,
      format: fmtNum,
    },
    {
      key: 'gtMachineCount',
      label: t('dataSource_gtMachineCount'),
      max: maxGt,
      format: fmtNum,
    },
  ];
}

/** Status tag color aligned with {@link GtMachinesPage}. */
export function gtStatusTagColor(statusText: string): string {
  const map: Record<string, string> = {
    Running: 'success',
    Idle: 'default',
    Error: 'error',
    Problem: 'warning',
    Maintenance: 'warning',
  };
  return map[statusText] || 'default';
}

export function aggregateCpuStats(cpus: StorageCpu[]) {
  const total = cpus.length;
  const active = cpus.filter((c) => c.isBusy).length;
  let usedStorage = 0;
  let totalStorage = 0;
  let coProcessors = 0;
  for (const c of cpus) {
    usedStorage += c.usedStorage || 0;
    totalStorage += (c.usedStorage || 0) + (c.availableStorage || 0);
    coProcessors += c.coProcessors || 0;
  }
  const totalCpuStoragePercent =
    totalStorage > 0 ? (usedStorage / totalStorage) * 100 : 0;
  return { total, active, usedStorage, totalStorage, coProcessors, totalCpuStoragePercent };
}

export function getOverviewDataSourceValue(
  ds: string,
  snap: OverviewSnapshot | null
): number {
  if (!snap) return 0;
  const cpuStats = aggregateCpuStats(snap.cpus || []);
  switch (ds) {
    case 'itemCount':
      return snap.itemCount ?? snap.items?.length ?? 0;
    case 'itemTotal': {
      if (snap.itemTotal != null) return snap.itemTotal;
      let total = 0;
      for (const item of snap.items || []) {
        total += item.amount || 0;
      }
      return total;
    }
    case 'fluidCount':
      return snap.fluidCount ?? snap.fluids?.length ?? 0;
    case 'essentiaCount':
      return snap.essentiaCount ?? snap.essentia?.length ?? 0;
    case 'bytesUsed':
      return snap.bytesUsed || 0;
    case 'bytesMax':
      return snap.bytesMax || 0;
    case 'bytesPercent':
      return snap.bytesMax > 0 ? (snap.bytesUsed / snap.bytesMax) * 100 : 0;
    case 'activeCpu':
    case 'busyCpu':
      return cpuStats.active;
    case 'cpuTotalCount':
      return cpuStats.total;
    case 'cpuBusyRatio':
      return cpuStats.total > 0 ? (cpuStats.active / cpuStats.total) * 100 : 0;
    case 'cpuActiveTotal':
      // Encoded as active*10000+total for statCard formatter; display handled in WidgetContent.
      return cpuStats.active * 10000 + cpuStats.total;
    case 'totalCpuStoragePercent':
      return cpuStats.totalCpuStoragePercent;
    case 'totalCoProcessors':
      return cpuStats.coProcessors;
    case 'parallelCrafting':
      return cpuStats.active;
    default:
      return 0;
  }
}

export const STORAGE_OVERVIEW_DATA_SOURCES = [
  'itemCount',
  'itemTotal',
  'fluidCount',
  'essentiaCount',
  'bytesUsed',
  'bytesMax',
  'bytesPercent',
  'activeCpu',
  'cpuTotalCount',
  'cpuBusyRatio',
];

export const CPU_OVERVIEW_DATA_SOURCES = [
  'cpuActiveTotal',
  'totalCpuStoragePercent',
  'totalCoProcessors',
  'parallelCrafting',
  'activeCpu',
  'cpuTotalCount',
  'cpuBusyRatio',
];
