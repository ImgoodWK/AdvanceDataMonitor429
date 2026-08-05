package com.imgood.textech.webae.cpu;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;

import org.junit.After;
import org.junit.Test;

public class CpuHistoryStoreTest {

    private File tempRoot;

    @After
    public void cleanup() {
        delete(tempRoot);
    }

    @Test
    public void savesAndLoadsOwnerNetworkBoundState() {
        CpuHistoryStore store = new CpuHistoryStore(tempRoot(), null);
        CpuHistoryState state = state();
        state.jobs.add(job("job-one", 100L));

        assertTrue(store.save(state));
        CpuHistoryState loaded = store.load("store-owner", 7, "0:10:20:30");
        assertEquals("store-owner", loaded.ownerUuid);
        assertEquals(7, loaded.networkId);
        assertEquals("0:10:20:30", loaded.networkKey);
        assertEquals(1, loaded.jobs.size());
        assertEquals("job-one", loaded.jobs.get(0).jobId);
    }

    @Test
    public void failedAtomicMoveLeavesExistingHistoryFileUntouched() {
        CpuHistoryStore reliable = new CpuHistoryStore(tempRoot(), null);
        CpuHistoryState original = state();
        original.jobs.add(job("persisted", 100L));
        assertTrue(reliable.save(original));

        original.jobs.add(job("must-not-replace", 200L));
        CpuHistoryStore failing = new CpuHistoryStore(tempRoot(), new CpuHistoryStore.FileMover() {

            @Override
            public void move(File source, File target) throws IOException {
                throw new IOException("simulated move failure");
            }
        });
        assertFalse(failing.save(original));

        CpuHistoryState loaded = reliable.load("store-owner", 7, "0:10:20:30");
        assertNotNull(loaded);
        assertEquals(1, loaded.jobs.size());
        assertEquals("persisted", loaded.jobs.get(0).jobId);
    }

    @Test
    public void rejectsUnsafeOwnerPathAndMismatchedStableKey() {
        CpuHistoryStore store = new CpuHistoryStore(tempRoot(), null);
        assertEquals(null, store.fileFor("../escape", 7, "0:10:20:30"));

        CpuHistoryState state = state();
        state.jobs.add(job("safe", 100L));
        assertTrue(store.save(state));
        CpuHistoryState mismatch = store.load("store-owner", 7, "0:11:20:30");
        assertEquals(0, mismatch.jobs.size());
    }

    @Test
    public void restoresStableHistoryAfterNetworkIdsAreReorderedWithoutOverwritingEitherSlot() {
        CpuHistoryStore store = new CpuHistoryStore(tempRoot(), null);
        CpuHistoryState first = state();
        first.jobs.add(job("first-job", 100L));
        assertTrue(store.save(first));

        CpuHistoryState second = new CpuHistoryState("store-owner", 8, "0:40:50:60");
        second.jobs.add(job("second-job", 200L, 8, "0:40:50:60"));
        assertTrue(store.save(second));

        // The stable keys have swapped numeric registry positions after a
        // restart. Both old files must remain recoverable, and saving one
        // recovered state must not replace the other key's file.
        CpuHistoryState restoredFirst = store.load("store-owner", 8, "0:10:20:30");
        assertEquals(1, restoredFirst.jobs.size());
        assertEquals("first-job", restoredFirst.jobs.get(0).jobId);
        assertEquals(8, restoredFirst.networkId);
        assertEquals(8, restoredFirst.jobs.get(0).networkId);
        assertTrue(store.save(restoredFirst));

        CpuHistoryState restoredSecond = store.load("store-owner", 7, "0:40:50:60");
        assertEquals(1, restoredSecond.jobs.size());
        assertEquals("second-job", restoredSecond.jobs.get(0).jobId);
        assertEquals(7, restoredSecond.networkId);
    }

    @Test
    public void writesStableSidecarInsteadOfReplacingAReassignedNumericSlot() {
        CpuHistoryStore store = new CpuHistoryStore(tempRoot(), null);
        CpuHistoryState occupied = state();
        occupied.jobs.add(job("occupied-job", 100L));
        assertTrue(store.save(occupied));

        CpuHistoryState incoming = new CpuHistoryState("store-owner", 7, "0:40:50:60");
        incoming.jobs.add(job("incoming-job", 200L, 7, "0:40:50:60"));
        assertTrue(store.save(incoming));

        CpuHistoryState original = store.load("store-owner", 7, "0:10:20:30");
        assertEquals(1, original.jobs.size());
        assertEquals("occupied-job", original.jobs.get(0).jobId);
        CpuHistoryState restoredIncoming = store.load("store-owner", 7, "0:40:50:60");
        assertEquals(1, restoredIncoming.jobs.size());
        assertEquals("incoming-job", restoredIncoming.jobs.get(0).jobId);
    }

    private CpuHistoryState state() {
        return new CpuHistoryState("store-owner", 7, "0:10:20:30");
    }

    private static CpuJobHistoryDto job(String id, long at) {
        return job(id, at, 7, "0:10:20:30");
    }

    private static CpuJobHistoryDto job(String id, long at, int networkId, String networkKey) {
        CpuJobHistoryDto job = new CpuJobHistoryDto();
        job.jobId = id;
        job.ownerUuid = "store-owner";
        job.networkId = networkId;
        job.networkKey = networkKey;
        job.status = CpuJobHistoryDto.STATUS_QUEUED;
        job.queuedAt = at;
        return job;
    }

    private File tempRoot() {
        if (tempRoot == null) {
            tempRoot = new File(System.getProperty("java.io.tmpdir"), "textech-cpu-history-store-" + System.nanoTime());
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
