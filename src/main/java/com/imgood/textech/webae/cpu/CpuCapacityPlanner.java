package com.imgood.textech.webae.cpu;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Pure capacity calculation over bounded CPU history. It intentionally emits
 * recommendation codes, rather than free-form instructions or any automatic
 * AE2 action.
 */
public final class CpuCapacityPlanner {

    public static final String BOTTLENECK_CPU = "cpu";
    public static final String BOTTLENECK_QUEUE = "queue";
    public static final String BOTTLENECK_STORAGE = "storage";
    public static final String BOTTLENECK_STUCK = "stuck";
    public static final String BOTTLENECK_INSUFFICIENT_DATA = "insufficient_data";

    public static final String RECOMMEND_COLLECT_MORE_HISTORY = "collect_more_history";
    public static final String RECOMMEND_REVIEW_CPU_COUNT = "review_cpu_count";
    public static final String RECOMMEND_REVIEW_QUEUEING = "review_queueing";
    public static final String RECOMMEND_REVIEW_CPU_STORAGE = "review_cpu_storage";
    public static final String RECOMMEND_INVESTIGATE_STUCK = "investigate_stuck_jobs";

    private CpuCapacityPlanner() {}

    public static CpuCapacityPlanDto plan(int networkId, String networkKey, String window, long from, long to,
        List<CpuJobHistoryDto> jobs, List<CpuSnapshotHistoryDto> snapshots) {
        CpuCapacityPlanDto plan = new CpuCapacityPlanDto();
        plan.networkId = networkId;
        plan.networkKey = networkKey;
        plan.window = window;
        plan.from = from;
        plan.to = to;

        List<IntervalEvent> events = new ArrayList<IntervalEvent>();
        List<Long> durations = new ArrayList<Long>();
        List<Long> queueTimes = new ArrayList<Long>();
        int stuckCount = 0;

        if (jobs != null) {
            for (int i = 0; i < jobs.size(); i++) {
                CpuJobHistoryDto job = jobs.get(i);
                if (job == null) {
                    continue;
                }
                if (CpuJobHistoryDto.STATUS_STUCK.equals(job.status) && intersects(job, from, to)) {
                    stuckCount++;
                }
                addTerminalMeasurements(job, durations, queueTimes);
                addConcurrencyInterval(job, from, to, events);
            }
        }

        if (!events.isEmpty()) {
            Collections.sort(events, new Comparator<IntervalEvent>() {

                @Override
                public int compare(IntervalEvent a, IntervalEvent b) {
                    if (a.at < b.at) {
                        return -1;
                    }
                    if (a.at > b.at) {
                        return 1;
                    }
                    // At touching endpoints, consume the prior job's end before
                    // starting the next job so [start,end) intervals do not overlap.
                    return a.delta < b.delta ? -1 : a.delta == b.delta ? 0 : 1;
                }
            });
            int running = 0;
            int peak = 0;
            for (int i = 0; i < events.size(); i++) {
                running += events.get(i).delta;
                if (running > peak) {
                    peak = running;
                }
            }
            plan.peakConcurrent = Integer.valueOf(peak);
            // The safety factor is fixed by the public API contract. There
            // is no synthetic "at least one CPU" recommendation without data.
            plan.requiredCpuCountEstimate = Integer.valueOf(Math.max(1, (int) Math.ceil(peak / 0.8d)));
        }

        plan.p50DurationMs = percentile(durations, 0.50d);
        plan.p95DurationMs = percentile(durations, 0.95d);
        plan.p95QueueMs = percentile(queueTimes, 0.95d);
        plan.stuckCount = Integer.valueOf(stuckCount);
        addSnapshotMeasurements(plan, snapshots, from, to);
        addBottlenecks(plan);
        return plan;
    }

    private static void addConcurrencyInterval(CpuJobHistoryDto job, long from, long to, List<IntervalEvent> events) {
        if (job.jobId != null && job.jobId.startsWith("local-")) {
            // A CPU-stuck observation with no one-to-one WebAE order cannot
            // masquerade as a known concurrent job.
            return;
        }
        if (job.startedAt <= 0L) {
            return;
        }

        final long end;
        if (job.isTerminal()) {
            if (job.finishedAt < job.startedAt) {
                return;
            }
            end = job.finishedAt;
        } else if (CpuJobHistoryDto.STATUS_RUNNING.equals(job.status)
            || CpuJobHistoryDto.STATUS_STUCK.equals(job.status)) {
                end = to;
            } else {
                // queued and unknown records have no proven execution interval.
                return;
            }

        long startInWindow = Math.max(from, job.startedAt);
        long endInWindow = Math.min(to, end);
        if (endInWindow <= startInWindow) {
            return;
        }
        events.add(new IntervalEvent(startInWindow, 1));
        events.add(new IntervalEvent(endInWindow, -1));
    }

    private static void addTerminalMeasurements(CpuJobHistoryDto job, List<Long> durations, List<Long> queueTimes) {
        if (!job.isTerminal() || job.startedAt <= 0L
            || job.finishedAt < job.startedAt
            || job.durationMs == null
            || job.durationMs.longValue() < 0L) {
            return;
        }
        durations.add(job.durationMs);
        if (job.queueMs != null && job.queueMs.longValue() >= 0L) {
            queueTimes.add(job.queueMs);
        }
    }

    private static void addSnapshotMeasurements(CpuCapacityPlanDto plan, List<CpuSnapshotHistoryDto> snapshots,
        long from, long to) {
        int validCount = 0;
        int busyCount = 0;
        long latestTimestamp = Long.MIN_VALUE;
        Set<String> currentCpuNames = new HashSet<String>();
        double maxPressure = -1.0d;
        int observedCoProcessors = -1;

        if (snapshots != null) {
            for (int i = 0; i < snapshots.size(); i++) {
                CpuSnapshotHistoryDto snapshot = snapshots.get(i);
                if (!isValidSnapshot(snapshot) || snapshot.timestamp < from || snapshot.timestamp > to) {
                    continue;
                }
                validCount++;
                if (snapshot.busy) {
                    busyCount++;
                }
                if (snapshot.timestamp > latestTimestamp) {
                    latestTimestamp = snapshot.timestamp;
                    currentCpuNames.clear();
                }
                if (snapshot.timestamp == latestTimestamp) {
                    currentCpuNames.add(snapshot.cpuName);
                }
                if (snapshot.storageMax > 0L && snapshot.storageUsed >= 0L) {
                    double pressure = (double) snapshot.storageUsed / (double) snapshot.storageMax;
                    pressure = Math.max(0.0d, Math.min(1.0d, pressure));
                    if (pressure > maxPressure) {
                        maxPressure = pressure;
                    }
                }
                if (snapshot.coProcessors >= 0 && snapshot.coProcessors > observedCoProcessors) {
                    observedCoProcessors = snapshot.coProcessors;
                }
            }
        }

        if (validCount > 0) {
            plan.busyRatio = Double.valueOf((double) busyCount / (double) validCount);
            plan.currentCpuCount = Integer.valueOf(currentCpuNames.size());
        }
        if (maxPressure >= 0.0d) {
            plan.storagePressure = Double.valueOf(maxPressure);
        }
        if (observedCoProcessors >= 0) {
            plan.coProcessorObservedMax = Integer.valueOf(observedCoProcessors);
        }
    }

    private static boolean isValidSnapshot(CpuSnapshotHistoryDto snapshot) {
        return snapshot != null && snapshot.timestamp > 0L && snapshot.cpuName != null && !snapshot.cpuName.isEmpty();
    }

    private static boolean intersects(CpuJobHistoryDto job, long from, long to) {
        long start = job.queuedAt > 0L ? job.queuedAt : job.startedAt;
        if (start <= 0L) {
            return false;
        }
        long end = job.finishedAt > 0L ? job.finishedAt : to;
        return start <= to && end >= from;
    }

    private static Long percentile(List<Long> values, double percentile) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        Collections.sort(values);
        int index = (int) Math.ceil(percentile * values.size()) - 1;
        index = Math.max(0, Math.min(values.size() - 1, index));
        return values.get(index);
    }

    private static void addBottlenecks(CpuCapacityPlanDto plan) {
        boolean insufficient = plan.peakConcurrent == null || plan.busyRatio == null;
        if (insufficient) {
            add(plan.bottlenecks, BOTTLENECK_INSUFFICIENT_DATA);
            add(plan.recommendations, RECOMMEND_COLLECT_MORE_HISTORY);
        }
        if (plan.busyRatio != null && plan.busyRatio.doubleValue() >= 0.80d) {
            add(plan.bottlenecks, BOTTLENECK_CPU);
            add(plan.recommendations, RECOMMEND_REVIEW_CPU_COUNT);
        }
        if (plan.p95QueueMs != null && plan.p95QueueMs.longValue() > 0L
            && (plan.p50DurationMs == null || plan.p95QueueMs.longValue() >= plan.p50DurationMs.longValue())) {
            add(plan.bottlenecks, BOTTLENECK_QUEUE);
            add(plan.recommendations, RECOMMEND_REVIEW_QUEUEING);
        }
        if (plan.storagePressure != null && plan.storagePressure.doubleValue() >= 0.80d) {
            add(plan.bottlenecks, BOTTLENECK_STORAGE);
            add(plan.recommendations, RECOMMEND_REVIEW_CPU_STORAGE);
        }
        if (plan.stuckCount != null && plan.stuckCount.intValue() > 0) {
            add(plan.bottlenecks, BOTTLENECK_STUCK);
            add(plan.recommendations, RECOMMEND_INVESTIGATE_STUCK);
        }
    }

    private static void add(List<String> values, String value) {
        if (!values.contains(value)) {
            values.add(value);
        }
    }

    private static final class IntervalEvent {

        final long at;
        final int delta;

        IntervalEvent(long at, int delta) {
            this.at = at;
            this.delta = delta;
        }
    }
}
