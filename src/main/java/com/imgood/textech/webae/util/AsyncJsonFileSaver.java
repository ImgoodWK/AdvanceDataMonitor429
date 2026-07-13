package com.imgood.textech.webae.util;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.imgood.textech.AdvanceDataMonitor;

/**
 * Generic async JSON file saver for WebAE.
 *
 * <p>The main thread serializes data to JSON and enqueues it into a
 * {@link LinkedBlockingQueue}. A single daemon worker thread consumes
 * the queue and writes files to disk. This eliminates disk I/O spikes
 * on the server main tick.</p>
 *
 * <p>Usage:
 * <pre>{@code
 * private static final AsyncJsonFileSaver saver = new AsyncJsonFileSaver("WebAE-Chat");
 * public void save(Object data, File file) {
 *     saver.schedule(data, file);
 * }
 * }</pre>
 * </p>
 */
public final class AsyncJsonFileSaver {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final LinkedBlockingQueue<SaveTask> queue;
    private final Thread worker;
    private final AtomicBoolean started;

    /**
     * @param threadName name for the daemon worker thread.
     */
    public AsyncJsonFileSaver(String threadName) {
        this.queue = new LinkedBlockingQueue<SaveTask>();
        this.started = new AtomicBoolean(false);
        this.worker = new Thread(new Runnable() {
            @Override
            public void run() {
                runLoop();
            }
        }, "WebAE-AsyncSave-" + (threadName != null ? threadName : "default"));
        this.worker.setDaemon(true);
    }

    /**
     * Serialize {@code data} to JSON and enqueue for background file write.
     * The caller provides a pre-serialized string to minimize work on the
     * caller's thread; alternatively pass the object and let the worker
     * serialize it.
     *
     * @param data  the object to serialize (snapshot on caller thread).
     * @param file  destination file.
     */
    public void schedule(Object data, File file) {
        ensureStarted();
        if (data == null || file == null) return;
        // Serialize on the calling thread — GSON is CPU-bound and fast enough
        // that we prefer doing it here rather than making the worker thread
        // hold a reference to mutable data.
        String json;
        try {
            json = GSON.toJson(data);
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.error("[WebAE] AsyncJsonFileSaver: JSON serialization failed for {}", file.getAbsolutePath(), e);
            return;
        }
        if (json == null) return;
        queue.offer(new SaveTask(json, file));
    }

    private void ensureStarted() {
        if (started.compareAndSet(false, true)) {
            worker.start();
        }
    }

    private void runLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            SaveTask task = null;
            try {
                task = queue.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            if (task == null) continue;
            writeTask(task);
            // Drain remaining tasks without blocking to reduce queue backlog.
            SaveTask next;
            while ((next = queue.poll()) != null) {
                writeTask(next);
            }
        }
    }

    private static void writeTask(SaveTask task) {
        File parent = task.file.getParentFile();
        if (parent != null && !parent.exists()) {
            if (!parent.mkdirs()) {
                AdvanceDataMonitor.LOG.warn("[WebAE] AsyncJsonFileSaver: Failed to create dir {}", parent.getAbsolutePath());
            }
        }
        BufferedWriter writer = null;
        try {
            writer = new BufferedWriter(new FileWriter(task.file, false));
            writer.write(task.json);
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.error("[WebAE] AsyncJsonFileSaver: Failed to write {}", task.file.getAbsolutePath(), e);
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (Exception ignored) {}
            }
        }
    }

    private static final class SaveTask {
        final String json;
        final File file;

        SaveTask(String json, File file) {
            this.json = json;
            this.file = file;
        }
    }
}
