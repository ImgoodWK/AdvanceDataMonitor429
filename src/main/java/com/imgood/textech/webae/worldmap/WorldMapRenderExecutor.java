package com.imgood.textech.webae.worldmap;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.Config;

/**
 * Fixed-size thread pool for offloading world map tile rendering from the main server tick.
 * Access must be via {@link #instance()}.
 */
public final class WorldMapRenderExecutor {

    private static final WorldMapRenderExecutor INSTANCE = new WorldMapRenderExecutor();

    private volatile ExecutorService pool;
    private volatile int configuredThreads;

    private WorldMapRenderExecutor() {}

    public static WorldMapRenderExecutor instance() {
        return INSTANCE;
    }

    /**
     * Lazily creates the thread pool on first use, respecting {@code Config.webWorldMapRenderThreads}.
     */
    private ExecutorService pool() {
        ExecutorService p = pool;
        if (p == null || p.isShutdown()) {
            synchronized (this) {
                p = pool;
                if (p == null || p.isShutdown()) {
                    int threads = Config.webWorldMapRenderThreads;
                    if (threads <= 0) {
                        threads = Math.max(
                            1,
                            Runtime.getRuntime()
                                .availableProcessors() / 2);
                    }
                    configuredThreads = threads;
                    final int n = threads;
                    p = Executors.newFixedThreadPool(n, new ThreadFactory() {

                        private final AtomicInteger counter = new AtomicInteger(1);

                        @Override
                        public Thread newThread(Runnable r) {
                            Thread t = new Thread(r, "WebAE-WorldMapRender-" + counter.getAndIncrement());
                            t.setDaemon(true);
                            t.setPriority(Thread.NORM_PRIORITY - 1);
                            return t;
                        }
                    });
                    pool = p;
                    AdvanceDataMonitor.LOG.info("[WebAE] WorldMapRenderExecutor started with {} thread(s)", n);
                }
            }
        }
        return p;
    }

    /**
     * Submits a rendering task. If the pool is shut down the task is silently dropped.
     */
    public void submit(Runnable task) {
        if (task == null) {
            return;
        }
        ExecutorService p = pool();
        if (p == null || p.isShutdown()) {
            return;
        }
        p.submit(task);
    }

    /**
     * Initiates an orderly shutdown. In-flight tasks are allowed to complete.
     */
    public void shutdown() {
        ExecutorService p = pool;
        if (p != null && !p.isShutdown()) {
            AdvanceDataMonitor.LOG
                .info("[WebAE] WorldMapRenderExecutor shutting down ({} thread(s))", configuredThreads);
            p.shutdown();
        }
    }

    /**
     * Forcibly terminates all running tasks.
     */
    public void shutdownNow() {
        ExecutorService p = pool;
        if (p != null && !p.isShutdown()) {
            p.shutdownNow();
        }
    }

    public int activeCount() {
        ExecutorService p = pool;
        if (p == null || p.isShutdown()) {
            return 0;
        }
        // approximate
        return configuredThreads;
    }
}
