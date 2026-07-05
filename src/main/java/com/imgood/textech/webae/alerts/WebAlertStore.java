package com.imgood.textech.webae.alerts;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import com.imgood.textech.webae.events.EventStreamHub;

/**
 * In-memory store of active WebAE alerts (per owner).
 */
public final class WebAlertStore {

    private static final WebAlertStore INSTANCE = new WebAlertStore();
    private static final int MAX_ALERTS_PER_OWNER = 100;
    private final ConcurrentHashMap<String, List<WebAlertDto>> byOwner = new ConcurrentHashMap<String, List<WebAlertDto>>();

    private WebAlertStore() {}

    public static WebAlertStore instance() {
        return INSTANCE;
    }

    public synchronized List<WebAlertDto> getAlerts(String ownerUuid) {
        List<WebAlertDto> list = byOwner.get(ownerUuid);
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }
        return new ArrayList<WebAlertDto>(list);
    }

    public synchronized void upsert(String ownerUuid, WebAlertDto alert) {
        if (ownerUuid == null || alert == null || alert.sourceKey == null || alert.sourceKey.isEmpty()) {
            return;
        }
        List<WebAlertDto> list = byOwner.get(ownerUuid);
        if (list == null) {
            list = new ArrayList<WebAlertDto>();
            byOwner.put(ownerUuid, list);
        }
        WebAlertDto published = null;
        for (WebAlertDto existing : list) {
            if (alert.sourceKey.equals(existing.sourceKey)) {
                existing.message = alert.message;
                existing.title = alert.title;
                existing.severity = alert.severity;
                existing.timestamp = alert.timestamp;
                existing.networkId = alert.networkId;
                existing.type = alert.type;
                published = existing;
                break;
            }
        }
        if (published == null) {
            alert.id = ownerUuid + ":" + alert.sourceKey + ":" + alert.timestamp;
            list.add(0, alert);
            published = alert;
            while (list.size() > MAX_ALERTS_PER_OWNER) {
                list.remove(list.size() - 1);
            }
            WebAlertHistoryStore.instance()
                .recordNew(ownerUuid, published);
            EventStreamHub.instance()
                .publishAlert(ownerUuid, published);
        } else {
            WebAlertHistoryStore.instance()
                .recordUpdate(ownerUuid, published);
        }
    }

    public synchronized void clearSource(String ownerUuid, String sourceKey) {
        List<WebAlertDto> list = byOwner.get(ownerUuid);
        if (list == null) {
            return;
        }
        Iterator<WebAlertDto> it = list.iterator();
        while (it.hasNext()) {
            if (sourceKey.equals(it.next().sourceKey)) {
                it.remove();
            }
        }
        WebAlertHistoryStore.instance()
            .markCleared(ownerUuid, sourceKey, System.currentTimeMillis());
    }

    public synchronized void clearAll(String ownerUuid) {
        byOwner.remove(ownerUuid);
        WebAlertHistoryStore.instance()
            .clearAll(ownerUuid);
    }
}
