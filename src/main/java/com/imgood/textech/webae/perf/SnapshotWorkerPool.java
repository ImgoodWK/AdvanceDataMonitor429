package com.imgood.textech.webae.perf;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.handler.HandlerTick;

/**
 * Single-thread daemon executor that schedules AE snapshot work onto the Minecraft
 * server thread with backpressure and per-task timeout protection.
 *
 * <p>AE2 API calls must run on the server thread; this pool only controls
 * <em>when</em> tasks are dispatched so {@link com.imgood.textech.webae.cache.SnapshotScheduler}
 * does not flood the server task queue from a single tick.</p>
 *
 * <p>Warn timeout ({@link #TASK_TIMEOUT_MS}) only logs; {@code inFlight} stays held until the
 * server task finishes (or {@link #HARD_WAIT_MS} elapses). Non-forced submits are rejected when
 * {@link HandlerTick#getServerTaskQueueDepth()} is at or above {@link #QUEUE_DEPTH_LIMIT}.</p>
 */
public final class SnapshotWorkerPool {

    public static final long TASK_TIMEOUT_MS = 500L;
    /** Max wait after soft timeout before giving up the flight gate (task may still finish later). */
    public static final long HARD_WAIT_MS = 30_000L;
    /** Reject non-forced submits when HandlerTick queue is at least this deep. */
    public static final int QUEUE_DEPTH_LIMIT = 48;

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(new ThreadFactory() {

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "WebAE-Snapshot-Worker");
            t.setDaemon(true);
            return t;
        }
    });

    /** Single-flight gate: at most one snapshot job in flight (running or queued on worker). */
    private static final AtomicInteger inFlight = new AtomicInteger(0);
    private static final AtomicLong timeoutCount = new AtomicLong(0);
    private static final AtomicLong skipBusyCount = new AtomicLong(0);
    private static final AtomicLong skipQueueCount = new AtomicLong(0);

    private SnapshotWorkerPool() {}

    /** True when a snapshot task is running or queued on the worker. */
    public static boolean isBusy() {
        return inFlight.get() > 0;
    }

    public static long getTimeoutCount() {
        return timeoutCount.get();
    }

    public static long getSkipBusyCount() {
        return skipBusyCount.get();
    }

    public static long getSkipQueueCount() {
        return skipQueueCount.get();
    }

    /**
     * Schedule {@code serverThreadWork} on the Minecraft server thread via the worker.
     *
     * @return {@code false} when backpressure skips the submission
     */
    public static boolean submitServerTask(final String label, final Runnable serverThreadWork) {
        return submitInternal(label, serverThreadWork, false);
    }

    /** Submit even when the worker is busy (admin force-collect). */
    public static void submitServerTaskForced(final String label, final Runnable serverThreadWork) {
        submitInternal(label, serverThreadWork, true);
    }

    private static boolean submitInternal(final String label, final Runnable serverThreadWork, boolean forced) {
        if (serverThreadWork == null) {
            return false;
        }
        if (!forced) {
            int depth = HandlerTick.getServerTaskQueueDepth();
            if (depth >= QUEUE_DEPTH_LIMIT) {
                skipQueueCount.incrementAndGet();
                AdvanceDataMonitor.LOG.debug(
                    "[WebAE] Snapshot queue depth {} >= {}, skipping {}",
                    Integer.valueOf(depth),
                    Integer.valueOf(QUEUE_DEPTH_LIMIT),
                    label);
                return false;
            }
            if (!inFlight.compareAndSet(0, 1)) {
                skipBusyCount.incrementAndGet();
                AdvanceDataMonitor.LOG.debug("[WebAE] Snapshot worker busy, skipping {}", label);
                return false;
            }
        } else {
            inFlight.incrementAndGet();
        }
        WORKER.execute(new Runnable() {

            @Override
            public void run() {
                try {
                    final CountDownLatch latch = new CountDownLatch(1);
                    final AtomicBoolean completed = new AtomicBoolean(false);

                    HandlerTick.enqueueServerTask(new Runnable() {

                        @Override
                        public void run() {
                            long t0 = System.nanoTime();
                            try {
                                serverThreadWork.run();
                                completed.set(true);
                            } catch (Throwable t) {
                                AdvanceDataMonitor.LOG.error("[WebAE] Snapshot server task failed: {}", label, t);
                            } finally {
                                long ms = (System.nanoTime() - t0) / 1_000_000L;
                                if (ms > TASK_TIMEOUT_MS) {
                                    AdvanceDataMonitor.LOG.warn(
                                        "[WebAE] Snapshot task '{}' exceeded {}ms (took {}ms)",
                                        label,
                                        Long.valueOf(TASK_TIMEOUT_MS),
                                        Long.valueOf(ms));
                                }
                                latch.countDown();
                            }
                        }
                    });

                    try {
                        if (!latch.await(TASK_TIMEOUT_MS, TimeUnit.MILLISECONDS) && !completed.get()) {
                            timeoutCount.incrementAndGet();
                            AdvanceDataMonitor.LOG.warn(
                                "[WebAE] Snapshot task '{}' timed out waiting after {}ms (still holding inFlight)",
                                label,
                                Long.valueOf(TASK_TIMEOUT_MS));
                            // Keep inFlight until the server task finishes (or hard wait).
                            long remaining = HARD_WAIT_MS - TASK_TIMEOUT_MS;
                            if (remaining > 0L) {
                                latch.await(remaining, TimeUnit.MILLISECONDS);
                            }
                            if (!completed.get()) {
                                AdvanceDataMonitor.LOG.warn(
                                    "[WebAE] Snapshot task '{}' still incomplete after {}ms hard wait",
                                    label,
                                    Long.valueOf(HARD_WAIT_MS));
                            }
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread()
                            .interrupt();
                    }
                } finally {
                    inFlight.decrementAndGet();
                }
            }
        });
        return true;
    }
}
