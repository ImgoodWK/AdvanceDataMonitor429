import { describe, expect, it } from 'vitest';
import type { CpuJobHistoryDto } from '@/types/dto';
import {
  buildCpuBusyRatePoints,
  boundedCpuPercent,
  CPU_HISTORY_TABLE_PAGE_SIZE,
  cpuCapacityBottleneckLabelKey,
  cpuCapacityRecommendationLabelKey,
  cpuCapacityWindowLabelKey,
  cpuHistoryStatusLabelKey,
  cpuHistoryStatusTone,
  cpuHistoryIsTruncated,
  filterCpuHistoryJobs,
  normalizeCpuCapacityWindow,
  normalizeCpuHistoryStatus,
} from './cpuHistoryPresentation';

const job = (overrides: Partial<CpuJobHistoryDto>): CpuJobHistoryDto => ({
  jobId: 'job',
  networkId: 1,
  cpuName: 'CPU 1',
  status: 'completed',
  queuedAt: 100,
  startedAt: 110,
  finishedAt: 120,
  recipeKey: 'mod:recipe',
  ...overrides,
});

describe('CPU history presentation', () => {
  it('keeps unknown status conservative and distinct from idle/success', () => {
    expect(normalizeCpuHistoryStatus('future_status')).toBe('unknown');
    expect(cpuHistoryStatusLabelKey('future_status')).toBe('cpuHistoryStatus_unknown');
    expect(cpuHistoryStatusTone('future_status')).toBe('warning');
  });

  it('handles empty samples and aggregates busy rate by timestamp', () => {
    expect(buildCpuBusyRatePoints([])).toEqual([]);
    expect(buildCpuBusyRatePoints([
      { timestamp: 1, cpuName: 'A', busy: true, storageUsed: 0, storageMax: 0, progress: 0, coProcessors: 0 },
      { timestamp: 1, cpuName: 'B', busy: false, storageUsed: 0, storageMax: 0, progress: 0, coProcessors: 0 },
      { timestamp: 2, cpuName: 'A', busy: true, storageUsed: 0, storageMax: 0, progress: 0, coProcessors: 0 },
    ])).toEqual([{ ts: 1, value: 50 }, { ts: 2, value: 100 }]);
    expect(buildCpuBusyRatePoints([{ timestamp: 1, cpuName: 'A', busy: true, storageUsed: 0, storageMax: 0, progress: 0, coProcessors: 0 }], 'missing')).toEqual([]);
  });

  it('filters by CPU/status, preserves unknown records, and bounds long lists at the table boundary', () => {
    const jobs = Array.from({ length: 25 }, (_, index) => job({
      jobId: `job-${index}`,
      status: index === 0 ? 'future-status' : index % 2 === 0 ? 'running' : 'completed',
      finishedAt: 200 + index,
      cpuName: index === 24 ? 'CPU 2' : 'CPU 1',
    }));
    expect(filterCpuHistoryJobs(jobs, 'CPU 1')).toHaveLength(24);
    expect(CPU_HISTORY_TABLE_PAGE_SIZE).toBe(8);
    expect(filterCpuHistoryJobs(jobs, 'CPU 1', 'unknown').map((item) => item.jobId)).toEqual(['job-0']);
    expect(filterCpuHistoryJobs(jobs, 'CPU 1', 'running')).toHaveLength(11);
    expect(filterCpuHistoryJobs(jobs, 'CPU 1')[0]?.jobId).toBe('job-23');
  });

  it('supports a network-wide recent-job view without mutating or losing unknown states', () => {
    const jobs = [
      job({ jobId: 'older', cpuName: 'CPU 1', finishedAt: 200, status: 'completed' }),
      job({ jobId: 'newer', cpuName: 'CPU 2', finishedAt: 400, status: 'future-status' }),
      job({ jobId: 'middle', cpuName: 'CPU 3', finishedAt: 300, status: 'running' }),
    ];
    const result = filterCpuHistoryJobs(jobs, undefined);
    expect(result.map((item) => item.jobId)).toEqual(['newer', 'middle', 'older']);
    expect(result[0]?.status).toBe('future-status');
    expect(jobs.map((item) => item.jobId)).toEqual(['older', 'newer', 'middle']);
  });

  it('normalizes supported capacity windows and maps only fixed API codes', () => {
    expect(normalizeCpuCapacityWindow('7d')).toBe('7d');
    expect(normalizeCpuCapacityWindow('bogus')).toBe('24h');
    expect(cpuCapacityWindowLabelKey('14d')).toBe('cpuCapacityWindow_14d');
    expect(cpuCapacityBottleneckLabelKey('storage')).toBe('cpuCapacityBottleneck_storage');
    expect(cpuCapacityRecommendationLabelKey('review_cpu_count')).toBe('cpuCapacityRecommendation_review_cpu_count');
    expect(cpuCapacityBottleneckLabelKey('free-text')).toBe('cpuCapacityUnknownCode');
    expect(boundedCpuPercent(0.8)).toBe(80);
    expect(boundedCpuPercent(150)).toBe(100);
    expect(boundedCpuPercent(null)).toBeNull();
  });

  it('preserves the server truncation signal for a visible warning', () => {
    expect(cpuHistoryIsTruncated({ truncated: true })).toBe(true);
    expect(cpuHistoryIsTruncated({ truncated: false })).toBe(false);
    expect(cpuHistoryIsTruncated(null)).toBe(false);
  });
});
