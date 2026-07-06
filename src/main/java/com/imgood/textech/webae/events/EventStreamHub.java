package com.imgood.textech.webae.events;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.webae.alerts.WebAlertDto;

/**
 * SSE subscriber hub for {@code GET /api/events/stream} (Phase 9).
 */
public final class EventStreamHub {

    private static final EventStreamHub INSTANCE = new EventStreamHub();
    private static final Gson GSON = new GsonBuilder().serializeNulls()
        .create();
    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final long HEARTBEAT_MS = 15_000L;

    private final ConcurrentHashMap<String, CopyOnWriteArrayList<Subscriber>> byOwner = new ConcurrentHashMap<String, CopyOnWriteArrayList<Subscriber>>();

    private EventStreamHub() {}

    public static EventStreamHub instance() {
        return INSTANCE;
    }

    public Subscriber register(String ownerUuid, OutputStream out) throws IOException {
        Subscriber sub = new Subscriber(ownerUuid, out);
        CopyOnWriteArrayList<Subscriber> list = byOwner.get(ownerUuid);
        if (list == null) {
            list = new CopyOnWriteArrayList<Subscriber>();
            CopyOnWriteArrayList<Subscriber> existing = byOwner.putIfAbsent(ownerUuid, list);
            if (existing != null) {
                list = existing;
            }
        }
        list.add(sub);
        writeRaw(sub, "event: connected\ndata: {\"type\":\"connected\"}\n\n");
        return sub;
    }

    public void unregister(Subscriber sub) {
        if (sub == null || sub.ownerUuid == null) {
            return;
        }
        CopyOnWriteArrayList<Subscriber> list = byOwner.get(sub.ownerUuid);
        if (list != null) {
            list.remove(sub);
            if (list.isEmpty()) {
                byOwner.remove(sub.ownerUuid, list);
            }
        }
        sub.closeQuietly();
    }

    /** Broadcast a new/updated alert to all SSE clients for this owner. */
    public void publishAlert(String ownerUuid, WebAlertDto alert) {
        if (ownerUuid == null || alert == null) {
            return;
        }
        CopyOnWriteArrayList<Subscriber> list = byOwner.get(ownerUuid);
        if (list == null || list.isEmpty()) {
            return;
        }
        String payload = GSON.toJson(alert);
        String frame = "event: alert\ndata: " + payload + "\n\n";
        for (Subscriber sub : list) {
            writeRaw(sub, frame);
        }
    }

    /** Notify all SSE clients that an icon is available (lazy-load retry). */
    public void publishIconReady(String pack, String mode, String itemId) {
        if (itemId == null || itemId.isEmpty()) return;
        java.util.Map<String, String> payload = new java.util.LinkedHashMap<String, String>();
        payload.put("type", "icon-ready");
        payload.put("pack", pack != null ? pack : "default");
        payload.put("mode", mode != null ? mode : "nei");
        payload.put("itemId", itemId);
        String json = GSON.toJson(payload);
        String frame = "event: icon-ready\ndata: " + json + "\n\n";
        for (CopyOnWriteArrayList<Subscriber> list : byOwner.values()) {
            for (Subscriber sub : list) {
                writeRaw(sub, frame);
            }
        }
    }

    /** Called from server tick — sends heartbeat to all open streams. */
    public void tickHeartbeats() {
        long now = System.currentTimeMillis();
        for (Map.Entry<String, CopyOnWriteArrayList<Subscriber>> entry : byOwner.entrySet()) {
            CopyOnWriteArrayList<Subscriber> list = entry.getValue();
            Iterator<Subscriber> it = list.iterator();
            while (it.hasNext()) {
                Subscriber sub = it.next();
                if (sub.isClosed()) {
                    list.remove(sub);
                    continue;
                }
                if (now - sub.lastWriteMs >= HEARTBEAT_MS) {
                    if (!writeRaw(sub, ": heartbeat\n\n")) {
                        list.remove(sub);
                        sub.closeQuietly();
                    }
                }
            }
            if (list.isEmpty()) {
                byOwner.remove(entry.getKey(), list);
            }
        }
    }

    private static boolean writeRaw(Subscriber sub, String frame) {
        if (sub == null || sub.closed) {
            return false;
        }
        try {
            sub.out.write(frame.getBytes(UTF8));
            sub.out.flush();
            sub.lastWriteMs = System.currentTimeMillis();
            return true;
        } catch (IOException e) {
            sub.closed = true;
            return false;
        }
    }

    public static final class Subscriber {

        public final String ownerUuid;
        public final OutputStream out;
        volatile long lastWriteMs = System.currentTimeMillis();
        volatile boolean closed;

        Subscriber(String ownerUuid, OutputStream out) {
            this.ownerUuid = ownerUuid;
            this.out = out;
        }

        boolean isClosed() {
            return closed;
        }

        void closeQuietly() {
            closed = true;
            try {
                out.close();
            } catch (IOException e) {
                AdvanceDataMonitor.LOG.debug("[WebAE] SSE stream close: {}", e.getMessage());
            }
        }
    }
}
