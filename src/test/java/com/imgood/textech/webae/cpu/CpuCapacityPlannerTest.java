package com.imgood.textech.webae.cpu;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public class CpuCapacityPlannerTest {

    @Test
    public void calculatesPeakWithTouchingIntervalsAndFixedSafetyFactor() {
        List<CpuJobHistoryDto> jobs = Arrays.asList(
            terminal("one", 100L, 200L, 100L, 10L),
            terminal("two", 150L, 250L, 100L, 20L),
            terminal("three", 200L, 300L, 100L, 30L));

        CpuCapacityPlanDto plan = CpuCapacityPlanner
            .plan(0, "0:1:2:3", "1h", 0L, 500L, jobs, Collections.<CpuSnapshotHistoryDto>emptyList());

        // At t=200 the first job ends before the third starts, so the peak is
        // two rather than an artificial three.
        assertEquals(Integer.valueOf(2), plan.peakConcurrent);
        assertEquals(Integer.valueOf(3), plan.requiredCpuCountEstimate);
        assertEquals(Long.valueOf(100L), plan.p50DurationMs);
        assertEquals(Long.valueOf(30L), plan.p95QueueMs);
    }

    @Test
    public void calculatesPercentilesBusyRatioStoragePressureAndBottlenecks() {
        List<CpuJobHistoryDto> jobs = Arrays.asList(
            terminal("a", 1L, 11L, 10L, 1L),
            terminal("b", 20L, 40L, 20L, 2L),
            terminal("c", 50L, 80L, 30L, 30L),
            terminal("d", 90L, 130L, 40L, 40L),
            stuck("stuck-job", "CPU A", 140L));
        List<CpuSnapshotHistoryDto> snapshots = new ArrayList<CpuSnapshotHistoryDto>();
        snapshots.add(snapshot(150L, "CPU A", true, 90L, 100L, 4));
        snapshots.add(snapshot(150L, "CPU B", true, 50L, 100L, 2));
        snapshots.add(snapshot(160L, "CPU A", true, 10L, 100L, 4));
        snapshots.add(snapshot(160L, "CPU B", false, 10L, 100L, 2));

        CpuCapacityPlanDto plan = CpuCapacityPlanner.plan(0, "0:1:2:3", "1h", 0L, 200L, jobs, snapshots);

        assertEquals(Long.valueOf(20L), plan.p50DurationMs);
        assertEquals(Long.valueOf(40L), plan.p95DurationMs);
        assertEquals(Long.valueOf(40L), plan.p95QueueMs);
        assertEquals(Double.valueOf(0.75d), plan.busyRatio);
        assertEquals(Double.valueOf(0.90d), plan.storagePressure);
        assertEquals(Integer.valueOf(2), plan.currentCpuCount);
        assertEquals(Integer.valueOf(4), plan.coProcessorObservedMax);
        assertEquals(Integer.valueOf(1), plan.stuckCount);
        assertTrue(plan.bottlenecks.contains(CpuCapacityPlanner.BOTTLENECK_STORAGE));
        assertTrue(plan.bottlenecks.contains(CpuCapacityPlanner.BOTTLENECK_STUCK));
        assertTrue(plan.bottlenecks.contains(CpuCapacityPlanner.BOTTLENECK_QUEUE));
    }

    @Test
    public void returnsNoCpuCountEstimateWhenThereIsNoValidExecutionInterval() {
        CpuJobHistoryDto queued = new CpuJobHistoryDto();
        queued.jobId = "queued-only";
        queued.status = CpuJobHistoryDto.STATUS_QUEUED;
        queued.queuedAt = 100L;

        CpuCapacityPlanDto plan = CpuCapacityPlanner.plan(
            0,
            "0:1:2:3",
            "1h",
            0L,
            200L,
            Collections.singletonList(queued),
            Collections.<CpuSnapshotHistoryDto>emptyList());

        assertNull(plan.peakConcurrent);
        assertNull(plan.requiredCpuCountEstimate);
        assertTrue(plan.bottlenecks.contains(CpuCapacityPlanner.BOTTLENECK_INSUFFICIENT_DATA));
        assertTrue(plan.recommendations.contains(CpuCapacityPlanner.RECOMMEND_COLLECT_MORE_HISTORY));
    }

    @Test
    public void localStuckObservationDoesNotInflateKnownConcurrency() {
        CpuJobHistoryDto local = stuck("local-stuck-123", "CPU only", 100L);
        CpuCapacityPlanDto plan = CpuCapacityPlanner.plan(
            0,
            "0:1:2:3",
            "1h",
            0L,
            200L,
            Collections.singletonList(local),
            Collections.<CpuSnapshotHistoryDto>emptyList());

        assertNull(plan.peakConcurrent);
        assertEquals(Integer.valueOf(1), plan.stuckCount);
    }

    private static CpuJobHistoryDto terminal(String id, long start, long finish, long duration, long queue) {
        CpuJobHistoryDto job = new CpuJobHistoryDto();
        job.jobId = id;
        job.status = CpuJobHistoryDto.STATUS_COMPLETED;
        job.queuedAt = Math.max(0L, start - queue);
        job.startedAt = start;
        job.finishedAt = finish;
        job.durationMs = Long.valueOf(duration);
        job.queueMs = Long.valueOf(queue);
        return job;
    }

    private static CpuJobHistoryDto stuck(String id, String cpuName, long queuedAt) {
        CpuJobHistoryDto job = new CpuJobHistoryDto();
        job.jobId = id;
        job.cpuName = cpuName;
        job.status = CpuJobHistoryDto.STATUS_STUCK;
        job.queuedAt = queuedAt;
        job.startedAt = queuedAt;
        return job;
    }

    private static CpuSnapshotHistoryDto snapshot(long at, String cpuName, boolean busy, long used, long max,
        int coProcessors) {
        CpuSnapshotHistoryDto snapshot = new CpuSnapshotHistoryDto();
        snapshot.timestamp = at;
        snapshot.cpuName = cpuName;
        snapshot.busy = busy;
        snapshot.storageUsed = used;
        snapshot.storageMax = max;
        snapshot.coProcessors = coProcessors;
        return snapshot;
    }
}
