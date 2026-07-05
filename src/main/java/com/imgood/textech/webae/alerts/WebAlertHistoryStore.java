package com.imgood.textech.webae.alerts;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-owner alert occurrence history (newest first). Complements {@link WebAlertStore} active list.
 */
public final class WebAlertHistoryStore {

    private static final WebAlertHistoryStore INSTANCE = new WebAlertHistoryStore();
    private static final int MAX_HISTORY_PER_OWNER = 500;

    private final ConcurrentHashMap<String, List<WebAlertHistoryEntry>> byOwner =
        new ConcurrentHashMap<String, List<WebAlertHistoryEntry>>();

    private WebAlertHistoryStore() {}

    public static WebAlertHistoryStore instance() {
        return INSTANCE;
    }

    public synchronized void recordNew(String ownerUuid, WebAlertDto alert) {
        if (ownerUuid == null || alert == null || alert.id == null || alert.id.isEmpty()) {
            return;
        }
        List<WebAlertHistoryEntry> list = listFor(ownerUuid);
        WebAlertHistoryEntry entry = new WebAlertHistoryEntry();
        entry.id = alert.id;
        entry.type = alert.type;
        entry.severity = alert.severity;
        entry.title = alert.title;
        entry.message = alert.message;
        entry.firstSeenAt = alert.timestamp;
        entry.lastSeenAt = alert.timestamp;
        entry.clearedAt = 0L;
        entry.networkId = alert.networkId;
        entry.sourceKey = alert.sourceKey;
        entry.active = true;
        list.add(0, entry);
        trim(list);
    }

    public synchronized void recordUpdate(String ownerUuid, WebAlertDto alert) {
        if (ownerUuid == null || alert == null || alert.id == null || alert.id.isEmpty()) {
            return;
        }
        List<WebAlertHistoryEntry> list = byOwner.get(ownerUuid);
        if (list == null) {
            recordNew(ownerUuid, alert);
            return;
        }
        for (WebAlertHistoryEntry entry : list) {
            if (alert.id.equals(entry.id)) {
                entry.message = alert.message;
                entry.title = alert.title;
                entry.severity = alert.severity;
                entry.lastSeenAt = alert.timestamp;
                entry.networkId = alert.networkId;
                entry.active = true;
                entry.clearedAt = 0L;
                return;
            }
        }
        recordNew(ownerUuid, alert);
    }

    public synchronized void markCleared(String ownerUuid, String sourceKey, long clearedAt) {
        if (ownerUuid == null || sourceKey == null || sourceKey.isEmpty()) {
            return;
        }
        List<WebAlertHistoryEntry> list = byOwner.get(ownerUuid);
        if (list == null) {
            return;
        }
        for (WebAlertHistoryEntry entry : list) {
            if (entry.active && sourceKey.equals(entry.sourceKey)) {
                entry.active = false;
                entry.clearedAt = clearedAt;
                return;
            }
        }
    }

    public synchronized List<WebAlertHistoryEntry> getHistory(String ownerUuid, int offset, int limit) {
        List<WebAlertHistoryEntry> list = byOwner.get(ownerUuid);
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }
        int safeOffset = Math.max(0, offset);
        int safeLimit = Math.min(Math.max(1, limit), 200);
        if (safeOffset >= list.size()) {
            return Collections.emptyList();
        }
        int end = Math.min(safeOffset + safeLimit, list.size());
        return new ArrayList<WebAlertHistoryEntry>(list.subList(safeOffset, end));
    }

    public synchronized int count(String ownerUuid) {
        List<WebAlertHistoryEntry> list = byOwner.get(ownerUuid);
        return list == null ? 0 : list.size();
    }

    public synchronized void clearAll(String ownerUuid) {
        byOwner.remove(ownerUuid);
    }

    private static List<WebAlertHistoryEntry> listFor(String ownerUuid) {
        List<WebAlertHistoryEntry> list = INSTANCE.byOwner.get(ownerUuid);
        if (list == null) {
            list = new ArrayList<WebAlertHistoryEntry>();
            INSTANCE.byOwner.put(ownerUuid, list);
        }
        return list;
    }

    private static void trim(List<WebAlertHistoryEntry> list) {
        while (list.size() > MAX_HISTORY_PER_OWNER) {
            list.remove(list.size() - 1);
        }
    }
}
