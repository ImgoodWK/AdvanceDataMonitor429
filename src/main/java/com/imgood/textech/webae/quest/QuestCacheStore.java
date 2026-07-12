package com.imgood.textech.webae.quest;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import com.imgood.textech.Config;
import com.imgood.textech.webae.dto.QuestLineSummaryDto;

/**
 * TTL caches for quest definitions (global) and progress (per questing UUID).
 */
public final class QuestCacheStore {

    private static final QuestCacheStore INSTANCE = new QuestCacheStore();

    private final ConcurrentHashMap<String, CacheEntry<List<QuestLineSummaryDto>>> lineListCache =
        new ConcurrentHashMap<String, CacheEntry<List<QuestLineSummaryDto>>>();
    private final ConcurrentHashMap<String, CacheEntry<Object>> progressCache =
        new ConcurrentHashMap<String, CacheEntry<Object>>();

    public static QuestCacheStore instance() {
        return INSTANCE;
    }

    public List<QuestLineSummaryDto> getLines(List<QuestLineSummaryDto> loader) {
        long ttlMs = Math.max(30_000L, Config.webQuestCacheTtlSec * 1000L);
        CacheEntry<List<QuestLineSummaryDto>> cached = lineListCache.get("lines");
        long now = System.currentTimeMillis();
        if (cached != null && now - cached.atMs < ttlMs) {
            return cached.value;
        }
        List<QuestLineSummaryDto> fresh = loader;
        lineListCache.put("lines", new CacheEntry<List<QuestLineSummaryDto>>(fresh, now));
        return fresh;
    }

    @SuppressWarnings("unchecked")
    public <T> T getProgress(String questingUuid, ProgressLoader<T> loader) {
        if (questingUuid == null || questingUuid.isEmpty()) {
            return loader.load();
        }
        long ttlMs = Math.min(30_000L, Math.max(5_000L, Config.webQuestCacheTtlSec * 1000L / 10L));
        CacheEntry<Object> cached = progressCache.get(questingUuid);
        long now = System.currentTimeMillis();
        if (cached != null && now - cached.atMs < ttlMs) {
            return (T) cached.value;
        }
        T fresh = loader.load();
        progressCache.put(questingUuid, new CacheEntry<Object>(fresh, now));
        return fresh;
    }

    public void invalidateProgress(String questingUuid) {
        if (questingUuid != null) {
            progressCache.remove(questingUuid);
        }
    }

    public void invalidateAll() {
        lineListCache.clear();
        progressCache.clear();
    }

    public interface ProgressLoader<T> {
        T load();
    }

    private static final class CacheEntry<T> {
        private final T value;
        private final long atMs;

        private CacheEntry(T value, long atMs) {
            this.value = value;
            this.atMs = atMs;
        }
    }
}
