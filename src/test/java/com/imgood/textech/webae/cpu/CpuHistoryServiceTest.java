package com.imgood.textech.webae.cpu;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.After;
import org.junit.Test;

import com.imgood.textech.webae.dto.StorageDto.CpuEntry;

public class CpuHistoryServiceTest {

    private File tempRoot;

    @After
    public void cleanup() {
        delete(tempRoot);
    }

    @Test
    public void recordsLifecycleWithoutPersistingFreeRequestText() {
        CpuHistoryService service = service();
        long queued = System.currentTimeMillis();
        service.recordQueued("cpu-owner", 3, "0:1:2:3", "job-1", "CPU Alpha", "untrusted free text", queued);
        service.recordRunning(
            "cpu-owner",
            3,
            "0:1:2:3",
            "job-1",
            "CPU Alpha",
            Integer.valueOf(40),
            Integer.valueOf(2),
            queued + 50L);
        service.recordTerminal(
            "cpu-owner",
            3,
            "0:1:2:3",
            "job-1",
            CpuJobHistoryDto.STATUS_COMPLETED,
            "CPU Alpha",
            Integer.valueOf(85),
            queued + 250L);

        CpuHistoryResponseDto response = service
            .getHistory("cpu-owner", 3, "0:1:2:3", queued - 100L, queued + 1_000L, 10);
        assertEquals(1, response.jobs.size());
        CpuJobHistoryDto job = response.jobs.get(0);
        assertEquals(CpuJobHistoryDto.STATUS_COMPLETED, job.status);
        assertEquals(Long.valueOf(200L), job.durationMs);
        assertEquals(Long.valueOf(50L), job.queueMs);
        assertEquals(Integer.valueOf(100), job.progress);
        assertNull(job.recipeKey);

        service.recordQueued("cpu-owner", 3, "0:1:2:3", "job-2", "CPU Alpha", "mod:recipe#4", queued + 300L);
        response = service.getHistory("cpu-owner", 3, "0:1:2:3", queued - 100L, queued + 1_000L, 10);
        assertEquals("mod:recipe#4", response.jobs.get(0).recipeKey);
    }

    @Test
    public void terminalLifecycleIsIdempotentAndFirstTerminalResultWins() {
        CpuHistoryService service = service();
        long queued = System.currentTimeMillis();

        service.recordQueued("cpu-owner", 3, "0:1:2:3", "completed-job", "CPU One", null, queued);
        service.recordRunning(
            "cpu-owner",
            3,
            "0:1:2:3",
            "completed-job",
            "CPU One",
            Integer.valueOf(20),
            Integer.valueOf(1),
            queued + 100L);
        service.recordTerminal(
            "cpu-owner",
            3,
            "0:1:2:3",
            "completed-job",
            CpuJobHistoryDto.STATUS_COMPLETED,
            "CPU One",
            Integer.valueOf(20),
            queued + 300L);

        // Replaying the same terminal callback must not change the trusted
        // terminal timestamp or overwrite fields with late observations.
        service.recordTerminal(
            "cpu-owner",
            3,
            "0:1:2:3",
            "completed-job",
            CpuJobHistoryDto.STATUS_COMPLETED,
            "CPU Late",
            Integer.valueOf(1),
            queued + 900L);
        // A contradictory callback is ignored for the same reason.
        service.recordTerminal(
            "cpu-owner",
            3,
            "0:1:2:3",
            "completed-job",
            CpuJobHistoryDto.STATUS_FAILED,
            "CPU Wrong",
            Integer.valueOf(0),
            queued + 1_000L);

        service.recordQueued("cpu-owner", 3, "0:1:2:3", "failed-job", "CPU Two", null, queued + 10L);
        service.recordRunning(
            "cpu-owner",
            3,
            "0:1:2:3",
            "failed-job",
            "CPU Two",
            Integer.valueOf(50),
            null,
            queued + 110L);
        service.recordTerminal(
            "cpu-owner",
            3,
            "0:1:2:3",
            "failed-job",
            CpuJobHistoryDto.STATUS_FAILED,
            "CPU Two",
            Integer.valueOf(50),
            queued + 410L);

        service.recordQueued("cpu-owner", 3, "0:1:2:3", "cancelled-job", "CPU Three", null, queued + 20L);
        service.recordRunning(
            "cpu-owner",
            3,
            "0:1:2:3",
            "cancelled-job",
            "CPU Three",
            Integer.valueOf(10),
            null,
            queued + 120L);
        service.recordTerminal(
            "cpu-owner",
            3,
            "0:1:2:3",
            "cancelled-job",
            CpuJobHistoryDto.STATUS_CANCELLED,
            "CPU Three",
            Integer.valueOf(10),
            queued + 520L);

        CpuHistoryResponseDto response = service
            .getHistory("cpu-owner", 3, "0:1:2:3", queued - 1L, queued + 2_000L, 10);
        assertEquals(3, response.jobs.size());

        CpuJobHistoryDto completed = find(response, "completed-job");
        assertEquals(CpuJobHistoryDto.STATUS_COMPLETED, completed.status);
        assertEquals("CPU One", completed.cpuName);
        assertEquals(Long.valueOf(queued + 300L), Long.valueOf(completed.finishedAt));
        assertEquals(Long.valueOf(200L), completed.durationMs);
        assertEquals(Integer.valueOf(100), completed.progress);
        assertEquals(CpuJobHistoryDto.STATUS_FAILED, find(response, "failed-job").status);
        assertEquals(CpuJobHistoryDto.STATUS_CANCELLED, find(response, "cancelled-job").status);
    }

    @Test
    public void readQueriesFilterRetentionWithoutMutatingOrPersistingState() {
        CpuHistoryService service = service();
        long now = System.currentTimeMillis();
        CpuHistoryState state = new CpuHistoryState("cpu-owner", 3, "0:1:2:3");

        CpuJobHistoryDto expired = new CpuJobHistoryDto();
        expired.jobId = "expired";
        expired.ownerUuid = state.ownerUuid;
        expired.networkId = state.networkId;
        expired.networkKey = state.networkKey;
        expired.status = CpuJobHistoryDto.STATUS_COMPLETED;
        expired.queuedAt = now - CpuHistoryService.RETENTION_MS - 2_000L;
        expired.startedAt = expired.queuedAt;
        expired.finishedAt = expired.queuedAt + 1_000L;
        state.jobs.add(expired);

        CpuJobHistoryDto current = new CpuJobHistoryDto();
        current.jobId = "current";
        current.ownerUuid = state.ownerUuid;
        current.networkId = state.networkId;
        current.networkKey = state.networkKey;
        current.status = CpuJobHistoryDto.STATUS_RUNNING;
        current.queuedAt = now;
        current.startedAt = now + 1L;
        state.jobs.add(current);

        CpuSnapshotHistoryDto expiredSnapshot = new CpuSnapshotHistoryDto();
        expiredSnapshot.timestamp = now - CpuHistoryService.RETENTION_MS - 2_000L;
        expiredSnapshot.cpuName = "CPU Old";
        state.snapshots.add(expiredSnapshot);
        CpuSnapshotHistoryDto currentSnapshot = new CpuSnapshotHistoryDto();
        currentSnapshot.timestamp = now;
        currentSnapshot.cpuName = "CPU Current";
        currentSnapshot.busy = true;
        state.snapshots.add(currentSnapshot);

        service.putForTests(state);
        int jobCountBefore = state.jobs.size();
        int snapshotCountBefore = state.snapshots.size();
        assertFalse(state.dirty);

        CpuHistoryResponseDto response = service.getHistory("cpu-owner", 3, "0:1:2:3", 0L, now + 2_000L, 10);
        assertEquals(1, response.jobs.size());
        assertEquals("current", response.jobs.get(0).jobId);
        assertEquals(1, response.snapshots.size());
        assertEquals("CPU Current", response.snapshots.get(0).cpuName);
        assertEquals(jobCountBefore, state.jobs.size());
        assertEquals(snapshotCountBefore, state.snapshots.size());
        assertFalse(state.dirty);

        CpuCapacityPlanDto capacity = service.getCapacity("cpu-owner", 3, "0:1:2:3", "24h", 0L, now + 2_000L);
        assertEquals(Long.valueOf(0L), Long.valueOf(capacity.from));
        assertEquals(jobCountBefore, state.jobs.size());
        assertEquals(snapshotCountBefore, state.snapshots.size());
        assertFalse(state.dirty);

        // No read may mark the state dirty, so a subsequent flush must not
        // create a backing file or invoke a store write.
        service.flushAll();
        assertFalse(new File(tempRoot(), "cpu-owner\\3.json").isFile());
    }

    @Test
    public void readDoesNotLoadUnactivatedStateFromDisk() {
        File root = tempRoot();
        CpuHistoryStore store = new CpuHistoryStore(root, null);
        long now = System.currentTimeMillis();
        CpuHistoryState persisted = new CpuHistoryState("cpu-owner", 3, "0:1:2:3");
        CpuJobHistoryDto job = new CpuJobHistoryDto();
        job.jobId = "disk-job";
        job.ownerUuid = persisted.ownerUuid;
        job.networkId = persisted.networkId;
        job.networkKey = persisted.networkKey;
        job.status = CpuJobHistoryDto.STATUS_COMPLETED;
        job.queuedAt = now;
        job.startedAt = now;
        job.finishedAt = now + 1L;
        job.durationMs = Long.valueOf(1L);
        persisted.jobs.add(job);
        assertTrue(store.save(persisted));

        CpuHistoryService service = new CpuHistoryService(store);
        CpuHistoryResponseDto beforeActivation = service
            .getHistory("cpu-owner", 3, "0:1:2:3", now - 1_000L, now + 1_000L, 10);
        assertTrue(beforeActivation.jobs.isEmpty());
        service.flushAll();

        // A normal mutation/sampler call is the activation boundary and may
        // load the persisted state. The read above must not have done so.
        service.recordCpuSnapshots("cpu-owner", 3, "0:1:2:3", Collections.<CpuEntry>emptyList(), now + 2L);
        CpuHistoryResponseDto afterActivation = service
            .getHistory("cpu-owner", 3, "0:1:2:3", now - 1_000L, now + 1_000L, 10);
        assertEquals(1, afterActivation.jobs.size());
        assertEquals("disk-job", afterActivation.jobs.get(0).jobId);
    }

    @Test
    public void missingCpuSnapshotDoesNotInventCompletionAndRestartMakesInFlightJobUnknown() {
        CpuHistoryService original = service();
        long now = System.currentTimeMillis();
        original.recordQueued("cpu-owner", 3, "0:1:2:3", "job-1", "CPU One", null, now);
        original.recordRunning(
            "cpu-owner",
            3,
            "0:1:2:3",
            "job-1",
            "CPU One",
            Integer.valueOf(25),
            Integer.valueOf(1),
            now + 100L);
        original.recordQueued("cpu-owner", 3, "0:1:2:3", "queued-only", "CPU Two", null, now + 150L);
        original.recordCpuSnapshots("cpu-owner", 3, "0:1:2:3", Collections.<CpuEntry>emptyList(), now + 200L);

        CpuHistoryResponseDto beforeRestart = original
            .getHistory("cpu-owner", 3, "0:1:2:3", now - 1L, now + 1_000L, 10);
        assertEquals(CpuJobHistoryDto.STATUS_RUNNING, find(beforeRestart, "job-1").status);
        original.flushAll();

        CpuHistoryService restored = new CpuHistoryService(new CpuHistoryStore(tempRoot(), null));
        // A normal server-tick collection is what lazily reloads persisted
        // history. No HTTP query needs to touch disk.
        restored.recordCpuSnapshots("cpu-owner", 3, "0:1:2:3", Collections.<CpuEntry>emptyList(), now + 300L);
        CpuHistoryResponseDto afterRestart = restored.getHistory("cpu-owner", 3, "0:1:2:3", now - 1L, now + 1_000L, 10);
        assertEquals(CpuJobHistoryDto.STATUS_UNKNOWN, find(afterRestart, "job-1").status);
        assertEquals(CpuJobHistoryDto.STATUS_UNKNOWN, find(afterRestart, "queued-only").status);
    }

    @Test
    public void keepsOwnerAndStableNetworkKeyIsolatedAndPrunesRetention() {
        CpuHistoryService service = service();
        long now = System.currentTimeMillis();
        service.recordQueued("cpu-owner", 3, "0:1:2:3", "visible", "CPU", null, now);
        service.recordQueued("cpu-owner", 3, "0:4:5:6", "other-network", "CPU", null, now);
        service
            .recordQueued("cpu-owner", 3, "0:1:2:3", "expired", "CPU", null, now - CpuHistoryService.RETENTION_MS - 1L);

        CpuHistoryResponseDto visible = service.getHistory("cpu-owner", 3, "0:1:2:3", now - 10L, now + 10L, 10);
        assertEquals(1, visible.jobs.size());
        assertEquals("visible", visible.jobs.get(0).jobId);
        CpuHistoryResponseDto other = service.getHistory("cpu-owner", 3, "0:4:5:6", now - 10L, now + 10L, 10);
        assertEquals(1, other.jobs.size());
        assertEquals("other-network", other.jobs.get(0).jobId);
    }

    @Test
    public void capsJobsAndSnapshotsAtThePublicRetentionLimits() {
        CpuHistoryService service = service();
        long now = System.currentTimeMillis();
        for (int i = 0; i < CpuHistoryService.MAX_JOBS_PER_NETWORK + 4; i++) {
            service.recordQueued("cpu-owner", 3, "0:1:2:3", "job-" + i, "CPU", null, now + i);
        }
        List<CpuEntry> entries = new ArrayList<CpuEntry>();
        CpuEntry cpu = new CpuEntry();
        cpu.name = "CPU";
        entries.add(cpu);
        for (int i = 0; i < CpuHistoryService.MAX_SNAPSHOTS_PER_NETWORK + 4; i++) {
            service.recordCpuSnapshots("cpu-owner", 3, "0:1:2:3", entries, now + i);
        }

        CpuHistoryResponseDto response = service.getHistory(
            "cpu-owner",
            3,
            "0:1:2:3",
            now - 1L,
            now + CpuHistoryService.MAX_SNAPSHOTS_PER_NETWORK + 20L,
            CpuHistoryService.MAX_HISTORY_JOBS_RESPONSE);
        assertEquals(CpuHistoryService.MAX_HISTORY_JOBS_RESPONSE, response.jobs.size());
        assertTrue(response.truncated);
        assertEquals(CpuHistoryService.MAX_HISTORY_SNAPSHOTS_RESPONSE, response.snapshots.size());
        assertFalse(response.snapshots.isEmpty());
    }

    private CpuHistoryService service() {
        return new CpuHistoryService(new CpuHistoryStore(tempRoot(), null));
    }

    private static CpuJobHistoryDto find(CpuHistoryResponseDto response, String jobId) {
        for (CpuJobHistoryDto job : response.jobs) {
            if (jobId.equals(job.jobId)) {
                return job;
            }
        }
        throw new AssertionError("Missing job " + jobId);
    }

    private File tempRoot() {
        if (tempRoot == null) {
            tempRoot = new File(
                System.getProperty("java.io.tmpdir"),
                "textech-cpu-history-service-" + System.nanoTime());
            assertTrue(tempRoot.mkdirs());
        }
        return tempRoot;
    }

    private static void delete(File target) {
        if (target == null || !target.exists()) {
            return;
        }
        File[] children = target.listFiles();
        if (children != null) {
            for (int i = 0; i < children.length; i++) {
                delete(children[i]);
            }
        }
        target.delete();
    }
}
