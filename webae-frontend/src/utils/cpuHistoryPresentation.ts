import type { ChartTrendPoint } from '@/components/dashboard/ChartTrendSvg';
import type {
  CpuCapacityWindow,
  CpuHistoryResponse,
  CpuJobHistoryDto,
  CpuSnapshotHistoryDto,
} from '@/types/dto';

export const CPU_HISTORY_TABLE_PAGE_SIZE = 8;

export const CPU_HISTORY_STATUSES = [
  'queued',
  'running',
  'completed',
  'failed',
  'cancelled',
  'stuck',
  'unknown',
] as const;

export type CpuHistoryStatus = (typeof CPU_HISTORY_STATUSES)[number];
export type CpuHistoryStatusFilter = CpuHistoryStatus | 'all';

const CPU_CAPACITY_WINDOWS = ['1h', '6h', '24h', '7d', '14d'] as const;
export const CPU_CAPACITY_WINDOW_OPTIONS: readonly CpuCapacityWindow[] = CPU_CAPACITY_WINDOWS;

const BOTTLENECK_CODES = new Set(['cpu', 'queue', 'storage', 'stuck', 'insufficient_data']);
const RECOMMENDATION_CODES = new Set([
  'collect_more_history',
  'review_cpu_count',
  'review_queueing',
  'review_cpu_storage',
  'investigate_stuck_jobs',
]);

/** Keep unknown/future server values conservative and visibly separate. */
export function normalizeCpuHistoryStatus(status: string | null | undefined): CpuHistoryStatus {
  return CPU_HISTORY_STATUSES.includes(status as CpuHistoryStatus)
    ? (status as CpuHistoryStatus)
    : 'unknown';
}

export function cpuHistoryStatusLabelKey(status: string | null | undefined): string {
  return `cpuHistoryStatus_${normalizeCpuHistoryStatus(status)}`;
}

export type CpuHistoryStatusTone = 'success' | 'processing' | 'error' | 'warning' | 'default';

export function cpuHistoryStatusTone(status: string | null | undefined): CpuHistoryStatusTone {
  switch (normalizeCpuHistoryStatus(status)) {
    case 'completed':
      return 'success';
    case 'running':
      return 'processing';
    case 'failed':
      return 'error';
    case 'cancelled':
      return 'warning';
    case 'stuck':
      return 'error';
    case 'unknown':
      return 'warning';
    default:
      return 'default';
  }
}

export function cpuHistoryStatusFilterKey(status: CpuHistoryStatusFilter): string {
  return status === 'all' ? 'cpuHistoryFilter_all' : cpuHistoryStatusLabelKey(status);
}

function latestJobTimestamp(job: CpuJobHistoryDto): number {
  return Math.max(job.finishedAt || 0, job.startedAt || 0, job.queuedAt || 0);
}

/** Filter and sort without mutating the bounded response array. */
export function filterCpuHistoryJobs(
  jobs: readonly CpuJobHistoryDto[] | null | undefined,
  cpuName?: string | null,
  status: CpuHistoryStatusFilter = 'all'
): CpuJobHistoryDto[] {
  return (jobs ?? [])
    .filter((job) => !cpuName || job.cpuName === cpuName)
    .filter((job) => status === 'all' || normalizeCpuHistoryStatus(job.status) === status)
    .slice()
    .sort((a, b) => latestJobTimestamp(b) - latestJobTimestamp(a));
}

export function cpuHistoryIsTruncated(response: Pick<CpuHistoryResponse, 'truncated'> | null | undefined): boolean {
  return response?.truncated === true;
}

/** Aggregate all CPU samples at the same timestamp into one busy-rate point. */
export function buildCpuBusyRatePoints(
  snapshots: readonly CpuSnapshotHistoryDto[] | null | undefined,
  cpuName?: string
): ChartTrendPoint[] {
  const grouped = new Map<number, { busy: number; total: number }>();
  for (const snapshot of snapshots ?? []) {
    if (!Number.isFinite(snapshot.timestamp) || snapshot.timestamp <= 0) continue;
    if (cpuName !== undefined && snapshot.cpuName !== cpuName) continue;
    const group = grouped.get(snapshot.timestamp) ?? { busy: 0, total: 0 };
    group.total += 1;
    if (snapshot.busy) group.busy += 1;
    grouped.set(snapshot.timestamp, group);
  }
  return [...grouped.entries()]
    .sort(([a], [b]) => a - b)
    .map(([ts, value]) => ({ ts, value: value.total > 0 ? (value.busy / value.total) * 100 : 0 }));
}

export function normalizeCpuCapacityWindow(window: string | null | undefined): CpuCapacityWindow {
  return CPU_CAPACITY_WINDOWS.includes(window as CpuCapacityWindow)
    ? (window as CpuCapacityWindow)
    : '24h';
}

export function cpuCapacityWindowLabelKey(window: string | null | undefined): string {
  return `cpuCapacityWindow_${normalizeCpuCapacityWindow(window)}`;
}

export function cpuCapacityBottleneckLabelKey(code: string | null | undefined): string {
  return code && BOTTLENECK_CODES.has(code)
    ? `cpuCapacityBottleneck_${code}`
    : 'cpuCapacityUnknownCode';
}

export function cpuCapacityRecommendationLabelKey(code: string | null | undefined): string {
  return code && RECOMMENDATION_CODES.has(code)
    ? `cpuCapacityRecommendation_${code}`
    : 'cpuCapacityUnknownCode';
}

export function boundedCpuPercent(value: number | null | undefined): number | null {
  if (value == null || !Number.isFinite(value)) return null;
  return Math.max(0, Math.min(100, value <= 1 ? value * 100 : value));
}
