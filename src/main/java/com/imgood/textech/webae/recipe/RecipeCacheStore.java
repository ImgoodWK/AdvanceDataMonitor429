package com.imgood.textech.webae.recipe;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.Config;
import com.imgood.textech.TeXTechDataDir;
import com.imgood.textech.webae.dto.RecipeDto;
import com.imgood.textech.webae.dto.RecipeDto.ItemEntry;

/**
 * Server-side recipe cache with LRU eviction and inverted index by output item.
 * Thread-safe: HTTP threads read, main thread writes.
 */
public class RecipeCacheStore {

    private static final Gson GSON = new GsonBuilder().create();
    private static final int SAVE_SCHEMA_VERSION = 1;
    private static final RecipeCacheStore INSTANCE = new RecipeCacheStore();

    private static final int EST_BYTES_PER_ENTRY = 600;
    private static final String JSON_FILENAME = "web-recipes.json";
    private static final String GZ_FILENAME = "web-recipes.json.gz";
    private static final String BROWSE_ALL = "all";

    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final LinkedHashMap<String, RecipeDto> recipeMap;
    private final ConcurrentHashMap<String, List<RecipeDto>> outputIndex;
    private final ConcurrentHashMap<String, List<RecipeDto>> inputIndex;
    private final ConcurrentHashMap<String, HandlerInfo> handlerInfoMap;
    /** registryName → suggest entry for autocomplete. */
    private final ConcurrentHashMap<String, ItemSuggest> itemSuggestMap;
    /** handlerId → sorted recipe keys for O(page) browse. */
    private final ConcurrentHashMap<String, List<String>> handlerRecipeKeys;
    /** 2-char lowercase prefix → registry names for suggest autocomplete. */
    private final ConcurrentHashMap<String, Set<String>> suggestPrefixIndex;

    private long lastUpdateTime;
    private int totalIngested;
    private volatile long lastDiskSave;
    private volatile long diskCacheSize;
    private volatile boolean savePending;
    private volatile boolean uploadSessionActive;
    private volatile boolean indexesDirty;
    private volatile boolean diskLoading;
    private volatile boolean diskLoadComplete;

    private RecipeCacheStore() {
        final boolean lruMode = isLruMode();
        int capacity = computeCapacity();
        this.recipeMap = new LinkedHashMap<String, RecipeDto>(capacity, 0.75f, lruMode) {

            @Override
            protected boolean removeEldestEntry(Map.Entry<String, RecipeDto> eldest) {
                if (!lruMode || size() <= capacity) return false;
                removeRecipeFromIndexes(eldest.getValue());
                return true;
            }
        };
        this.outputIndex = new ConcurrentHashMap<String, List<RecipeDto>>();
        this.inputIndex = new ConcurrentHashMap<String, List<RecipeDto>>();
        this.handlerInfoMap = new ConcurrentHashMap<String, HandlerInfo>();
        this.itemSuggestMap = new ConcurrentHashMap<String, ItemSuggest>();
        this.handlerRecipeKeys = new ConcurrentHashMap<String, List<String>>();
        this.suggestPrefixIndex = new ConcurrentHashMap<String, Set<String>>();
        this.lastUpdateTime = System.currentTimeMillis();
        this.totalIngested = 0;
        this.lastDiskSave = 0;
        this.diskCacheSize = 0;
        this.savePending = false;
        this.uploadSessionActive = false;
        this.indexesDirty = false;
        this.diskLoading = false;
        this.diskLoadComplete = false;
        startDiskLoadAsync();
    }

    private void startDiskLoadAsync() {
        Thread loader = new Thread(new Runnable() {
            @Override
            public void run() {
                load();
            }
        }, "WebAE-RecipeCache-Load");
        loader.setDaemon(true);
        loader.start();
    }

    private static boolean isLruMode() {
        String mode = Config.webRecipeCacheMode;
        return mode != null && "lru".equalsIgnoreCase(mode.trim());
    }

    public static RecipeCacheStore instance() {
        return INSTANCE;
    }

    private int computeCapacity() {
        int mb = Config.webMaxRecipeCacheMB;
        if (mb <= 0) mb = 64;
        int entries = (mb * 1024 * 1024) / EST_BYTES_PER_ENTRY;
        return Math.max(entries, 1000);
    }

    public void beginUploadSession() {
        uploadSessionActive = true;
    }

    public void endUploadSession() {
        uploadSessionActive = false;
        lock.writeLock()
            .lock();
        try {
            rebuildIndexesLocked();
        } finally {
            lock.writeLock()
                .unlock();
        }
        requestSave();
    }

    public void ingest(RecipeDto[] recipes) {
        if (recipes == null || recipes.length == 0) return;

        lock.writeLock()
            .lock();
        try {
            for (RecipeDto dto : recipes) {
                if (dto == null) continue;
                String key = makeKey(dto.handlerId, dto.recipeIndex);
                RecipeDto old = recipeMap.get(key);
                if (old != null) {
                    removeRecipeFromIndexes(old);
                }
                recipeMap.put(key, dto);

                HandlerInfo info = handlerInfoMap.get(dto.handlerId);
                if (info == null) {
                    info = new HandlerInfo(dto.handlerId, dto.handlerName, 0);
                    handlerInfoMap.put(dto.handlerId, info);
                } else if (dto.handlerName != null && !dto.handlerName.isEmpty()) {
                    info.handlerName = dto.handlerName;
                }

                indexRecipe(dto);
                appendHandlerRecipeKey(dto.handlerId, key);
                totalIngested++;
            }
            lastUpdateTime = System.currentTimeMillis();
            warnIfOverCapacity();
        } finally {
            lock.writeLock()
                .unlock();
        }
        if (!uploadSessionActive) {
            requestSave();
        }
    }

    private void warnIfOverCapacity() {
        if (isLruMode()) return;
        int capacity = computeCapacity();
        if (recipeMap.size() > capacity) {
            AdvanceDataMonitor.LOG.warn(
                "[WebAE] Recipe cache size {} exceeds configured capacity {} (full mode — no LRU eviction)",
                recipeMap.size(),
                capacity);
        }
    }

    private void indexRecipe(RecipeDto dto) {
        if (dto.outputs != null) {
            for (ItemEntry entry : dto.outputs) {
                if (entry.registryName != null && !entry.registryName.isEmpty()) {
                    addToIndex(entry.registryName, dto, outputIndex);
                    registerSuggest(entry);
                }
            }
        }
        if (dto.inputs != null) {
            for (ItemEntry entry : dto.inputs) {
                if (entry.registryName != null && !entry.registryName.isEmpty()) {
                    addToIndex(entry.registryName, dto, inputIndex);
                    registerSuggest(entry);
                }
            }
        }
    }

    private void registerSuggest(ItemEntry entry) {
        if (entry.registryName == null || entry.registryName.isEmpty()) return;
        ItemSuggest existing = itemSuggestMap.get(entry.registryName);
        if (existing == null) {
            existing = new ItemSuggest(
                entry.registryName,
                entry.displayName != null ? entry.displayName : entry.registryName,
                entry.itemId != null ? entry.itemId : entry.registryName);
            itemSuggestMap.put(entry.registryName, existing);
        } else if (entry.displayName != null && !entry.displayName.isEmpty()
            && (existing.displayName == null || existing.displayName.equals(existing.registryName))) {
                existing.displayName = entry.displayName;
            }
        addSuggestPrefixes(existing.registryName, existing.displayName);
    }

    private void addSuggestPrefixes(String registryName, String displayName) {
        if (registryName == null || registryName.length() < 2) return;
        indexSuggestText(registryName, registryName);
        if (displayName != null && displayName.length() >= 2) {
            indexSuggestText(displayName, registryName);
        }
    }

    private void indexSuggestText(String searchable, String registryName) {
        String lower = searchable.toLowerCase();
        for (int len = 2; len <= Math.min(4, lower.length()); len++) {
            String prefix = lower.substring(0, len);
            Set<String> bucket = suggestPrefixIndex.get(prefix);
            if (bucket == null) {
                bucket = Collections.synchronizedSet(new HashSet<String>());
                Set<String> existing = suggestPrefixIndex.putIfAbsent(prefix, bucket);
                if (existing != null) bucket = existing;
            }
            bucket.add(registryName);
        }
    }

    private void appendHandlerRecipeKey(String handlerId, String key) {
        if (handlerId == null || key == null) return;
        List<String> keys = handlerRecipeKeys.get(handlerId);
        if (keys == null) {
            keys = Collections.synchronizedList(new ArrayList<String>());
            List<String> existing = handlerRecipeKeys.putIfAbsent(handlerId, keys);
            if (existing != null) keys = existing;
        }
        synchronized (keys) {
            if (!keys.contains(key)) {
                keys.add(key);
            }
        }
    }

    private void removeHandlerRecipeKey(String handlerId, String key) {
        if (handlerId == null || key == null) return;
        List<String> keys = handlerRecipeKeys.get(handlerId);
        if (keys == null) return;
        synchronized (keys) {
            keys.remove(key);
        }
    }

    private void removeRecipeFromIndexes(RecipeDto dto) {
        removeFromIndex(dto, outputIndex);
        removeFromIndex(dto, inputIndex);
        removeHandlerRecipeKey(dto.handlerId, makeKey(dto.handlerId, dto.recipeIndex));
    }

    private void removeFromIndex(RecipeDto dto, ConcurrentHashMap<String, List<RecipeDto>> index) {
        if (dto.outputs != null && index == outputIndex) {
            for (ItemEntry entry : dto.outputs) {
                removeFromIndexList(entry.registryName, dto, index);
            }
        }
        if (dto.inputs != null && index == inputIndex) {
            for (ItemEntry entry : dto.inputs) {
                removeFromIndexList(entry.registryName, dto, index);
            }
        }
    }

    private void removeFromIndexList(String registryName, RecipeDto dto,
        ConcurrentHashMap<String, List<RecipeDto>> index) {
        if (registryName == null || registryName.isEmpty()) return;
        List<RecipeDto> list = index.get(registryName);
        if (list == null) return;
        synchronized (list) {
            for (int i = list.size() - 1; i >= 0; i--) {
                RecipeDto existing = list.get(i);
                if (existing.handlerId.equals(dto.handlerId) && existing.recipeIndex == dto.recipeIndex) {
                    list.remove(i);
                }
            }
        }
    }

    private void addToIndex(String registryName, RecipeDto dto, ConcurrentHashMap<String, List<RecipeDto>> index) {
        List<RecipeDto> list = index.get(registryName);
        if (list == null) {
            list = Collections.synchronizedList(new ArrayList<RecipeDto>());
            List<RecipeDto> existing = index.putIfAbsent(registryName, list);
            if (existing != null) list = existing;
        }
        synchronized (list) {
            for (RecipeDto existing : list) {
                if (existing.handlerId.equals(dto.handlerId) && existing.recipeIndex == dto.recipeIndex) {
                    return;
                }
            }
            list.add(dto);
        }
    }

    public RecipeDto getRecipe(String handlerId, int recipeIndex) {
        lock.readLock()
            .lock();
        try {
            return recipeMap.get(makeKey(handlerId, recipeIndex));
        } finally {
            lock.readLock()
                .unlock();
        }
    }

    public List<RecipeDto> searchByOutput(String registryName, String handlerFilter) {
        return searchByIndex(registryName, handlerFilter, outputIndex);
    }

    public List<RecipeDto> searchByInput(String registryName, String handlerFilter) {
        return searchByIndex(registryName, handlerFilter, inputIndex);
    }

    private List<RecipeDto> searchByIndex(String registryName, String handlerFilter,
        ConcurrentHashMap<String, List<RecipeDto>> index) {
        List<RecipeDto> matches = index.get(registryName);
        if (matches == null || matches.isEmpty()) {
            return Collections.emptyList();
        }
        List<RecipeDto> result = new ArrayList<RecipeDto>();
        synchronized (matches) {
            for (RecipeDto dto : matches) {
                if (handlerFilter != null && !handlerFilter.isEmpty()
                    && !handlerFilter.equals(BROWSE_ALL)
                    && !dto.handlerId.equals(handlerFilter)) {
                    continue;
                }
                result.add(dto);
            }
        }
        return result;
    }

    public BrowseResult browseByHandler(String handlerId, int offset, int limit) {
        return browseByHandlers(parseHandlerFilter(handlerId), offset, limit);
    }

    public BrowseResult browseByHandlers(List<String> handlerIds, int offset, int limit) {
        if (handlerIds == null) {
            handlerIds = Collections.emptyList();
        }
        lock.readLock()
            .lock();
        try {
            List<String> keys = collectBrowseKeys(handlerIds);
            int total = keys.size();
            int safeOffset = Math.max(0, offset);
            int safeLimit = Math.max(1, Math.min(limit, 200));
            List<RecipeDto> page = new ArrayList<RecipeDto>();
            if (safeOffset < keys.size()) {
                int end = Math.min(safeOffset + safeLimit, keys.size());
                for (int i = safeOffset; i < end; i++) {
                    RecipeDto dto = recipeMap.get(keys.get(i));
                    if (dto != null) page.add(dto);
                }
            }
            return new BrowseResult(page, total, safeOffset, safeLimit);
        } finally {
            lock.readLock()
                .unlock();
        }
    }

    private List<String> collectBrowseKeys(List<String> handlerIds) {
        List<String> keys = new ArrayList<String>();
        if (handlerIds == null || handlerIds.isEmpty()) {
            for (RecipeDto dto : recipeMap.values()) {
                keys.add(makeKey(dto.handlerId, dto.recipeIndex));
            }
            Collections.sort(keys);
            return keys;
        }
        if (handlerIds.size() == 1) {
            List<String> bucket = handlerRecipeKeys.get(handlerIds.get(0));
            if (bucket != null) {
                synchronized (bucket) {
                    keys.addAll(bucket);
                }
            }
            sortRecipeKeys(keys);
            return keys;
        }
        Set<String> dedup = new LinkedHashSet<String>();
        for (String handlerId : handlerIds) {
            List<String> bucket = handlerRecipeKeys.get(handlerId);
            if (bucket == null) continue;
            synchronized (bucket) {
                dedup.addAll(bucket);
            }
        }
        keys.addAll(dedup);
        sortRecipeKeys(keys);
        return keys;
    }

    private static void sortRecipeKeys(List<String> keys) {
        Collections.sort(keys, new Comparator<String>() {

            @Override
            public int compare(String a, String b) {
                int colonA = a.indexOf(':');
                int colonB = b.indexOf(':');
                if (colonA < 0 || colonB < 0) return a.compareTo(b);
                int handlerCmp = a.substring(0, colonA)
                    .compareTo(b.substring(0, colonB));
                if (handlerCmp != 0) return handlerCmp;
                try {
                    return Integer.parseInt(a.substring(colonA + 1)) - Integer.parseInt(b.substring(colonB + 1));
                } catch (NumberFormatException e) {
                    return a.compareTo(b);
                }
            }
        });
    }

    private static List<String> parseHandlerFilter(String handlerFilter) {
        if (handlerFilter == null || handlerFilter.isEmpty() || BROWSE_ALL.equals(handlerFilter)) {
            return Collections.emptyList();
        }
        if (handlerFilter.indexOf(',') >= 0) {
            List<String> out = new ArrayList<String>();
            for (String part : handlerFilter.split(",")) {
                if (part != null && !part.isEmpty() && !BROWSE_ALL.equals(part)) {
                    out.add(part.trim());
                }
            }
            return out;
        }
        List<String> single = new ArrayList<String>();
        single.add(handlerFilter);
        return single;
    }

    public QuerySearchResult searchByQuery(String query, String handlerFilter, String scope, int offset, int limit) {
        return searchByQuery(query, handlerFilter, parseHandlerFilter(handlerFilter), scope, offset, limit);
    }

    public QuerySearchResult searchByQuery(String query, String handlerFilter, List<String> handlerIds, String scope,
        int offset, int limit) {
        if (query == null || query.isEmpty()) {
            return new QuerySearchResult(Collections.<RecipeDto>emptyList(), 0, offset, limit);
        }
        String q = query.toLowerCase();
        int safeOffset = Math.max(0, offset);
        int safeLimit = Math.max(1, Math.min(limit, 200));

        if ("output".equals(scope) || "input".equals(scope)) {
            QuerySearchResult exact = searchScopedFuzzy(q, handlerFilter, handlerIds, scope, safeOffset, safeLimit);
            if (exact != null && exact.total > 0) return exact;
        }

        lock.readLock()
            .lock();
        try {
            List<RecipeDto> page = new ArrayList<RecipeDto>();
            int matchedCount = 0;
            boolean pageFilled = false;
            for (RecipeDto dto : recipeMap.values()) {
                if (!matchesHandlerFilter(dto, handlerFilter, handlerIds)) continue;
                if (!matchesQuery(dto, q, scope)) continue;
                if (!pageFilled) {
                    if (matchedCount >= safeOffset && page.size() < safeLimit) {
                        page.add(dto);
                        if (page.size() >= safeLimit) {
                            pageFilled = true;
                        }
                    }
                }
                matchedCount++;
            }
            return new QuerySearchResult(page, matchedCount, safeOffset, safeLimit);
        } finally {
            lock.readLock()
                .unlock();
        }
    }

    private QuerySearchResult searchScopedFuzzy(String q, String handlerFilter, List<String> handlerIds, String scope,
        int safeOffset, int safeLimit) {
        ConcurrentHashMap<String, List<RecipeDto>> index = "input".equals(scope) ? inputIndex : outputIndex;
        List<RecipeDto> candidates = new ArrayList<RecipeDto>();
        lock.readLock()
            .lock();
        try {
            for (Map.Entry<String, List<RecipeDto>> entry : index.entrySet()) {
                String registry = entry.getKey();
                if (registry == null || !registry.toLowerCase()
                    .contains(q)) {
                    continue;
                }
                List<RecipeDto> list = entry.getValue();
                if (list == null) continue;
                synchronized (list) {
                    for (RecipeDto dto : list) {
                        if (matchesHandlerFilter(dto, handlerFilter, handlerIds)) {
                            candidates.add(dto);
                        }
                    }
                }
            }
            int total = candidates.size();
            List<RecipeDto> page;
            if (safeOffset >= total) {
                page = Collections.emptyList();
            } else {
                int end = Math.min(safeOffset + safeLimit, total);
                page = new ArrayList<RecipeDto>(candidates.subList(safeOffset, end));
            }
            return new QuerySearchResult(page, total, safeOffset, safeLimit);
        } finally {
            lock.readLock()
                .unlock();
        }
    }

    private boolean matchesHandlerFilter(RecipeDto dto, String handlerFilter, List<String> handlerIds) {
        if (handlerIds != null && !handlerIds.isEmpty()) {
            return handlerIds.contains(dto.handlerId);
        }
        if (handlerFilter != null && !handlerFilter.isEmpty()
            && !handlerFilter.equals(BROWSE_ALL)
            && !dto.handlerId.equals(handlerFilter)) {
            return false;
        }
        return true;
    }

    public List<ItemSuggest> suggestItems(String query, int limit) {
        if (query == null || query.isEmpty()) {
            return Collections.emptyList();
        }
        String q = query.toLowerCase();
        int cap = Math.max(1, Math.min(limit, 50));
        List<ItemSuggest> result = new ArrayList<ItemSuggest>();
        lock.readLock()
            .lock();
        try {
            Set<String> candidates = collectSuggestCandidates(q);
            for (String registryName : candidates) {
                ItemSuggest item = itemSuggestMap.get(registryName);
                if (item == null) continue;
                if (matchesSuggest(item, q)) {
                    result.add(item);
                    if (result.size() >= cap) break;
                }
            }
            Collections.sort(result, new Comparator<ItemSuggest>() {

                @Override
                public int compare(ItemSuggest a, ItemSuggest b) {
                    return a.displayName.compareToIgnoreCase(b.displayName);
                }
            });
        } finally {
            lock.readLock()
                .unlock();
        }
        return result;
    }

    private Set<String> collectSuggestCandidates(String q) {
        if (q.length() >= 2) {
            String prefix = q.substring(0, Math.min(4, q.length()));
            Set<String> bucket = suggestPrefixIndex.get(prefix);
            if (bucket != null && !bucket.isEmpty()) {
                return new HashSet<String>(bucket);
            }
            if (q.length() >= 2) {
                bucket = suggestPrefixIndex.get(q.substring(0, 2));
                if (bucket != null && !bucket.isEmpty()) {
                    return new HashSet<String>(bucket);
                }
            }
        }
        return itemSuggestMap.keySet();
    }

    private boolean matchesSuggest(ItemSuggest item, String q) {
        if (item.registryName != null && item.registryName.toLowerCase()
            .contains(q)) return true;
        if (item.displayName != null && item.displayName.toLowerCase()
            .contains(q)) return true;
        return false;
    }

    private boolean matchesQuery(RecipeDto dto, String q, String scope) {
        if (scope == null || scope.isEmpty() || BROWSE_ALL.equals(scope)) {
            if (dto.handlerId != null && dto.handlerId.toLowerCase()
                .contains(q)) return true;
            if (dto.handlerName != null && dto.handlerName.toLowerCase()
                .contains(q)) return true;
            if (matchesEntries(dto.outputs, q)) return true;
            if (matchesEntries(dto.inputs, q)) return true;
            return false;
        }
        if ("output".equals(scope)) {
            return matchesEntries(dto.outputs, q);
        }
        if ("input".equals(scope)) {
            return matchesEntries(dto.inputs, q);
        }
        return matchesQuery(dto, q, BROWSE_ALL);
    }

    private boolean matchesEntries(List<ItemEntry> entries, String q) {
        if (entries == null) return false;
        for (ItemEntry e : entries) {
            if (e.registryName != null && e.registryName.toLowerCase()
                .contains(q)) return true;
            if (e.displayName != null && e.displayName.toLowerCase()
                .contains(q)) return true;
        }
        return false;
    }

    public void rebuildHandlerCounts() {
        lock.writeLock()
            .lock();
        try {
            rebuildHandlerCountsLocked();
        } finally {
            lock.writeLock()
                .unlock();
        }
    }

    public void rebuildIndexes() {
        lock.writeLock()
            .lock();
        try {
            rebuildIndexesLocked();
        } finally {
            lock.writeLock()
                .unlock();
        }
    }

    private void rebuildIndexesLocked() {
        outputIndex.clear();
        inputIndex.clear();
        itemSuggestMap.clear();
        suggestPrefixIndex.clear();
        handlerRecipeKeys.clear();
        for (RecipeDto dto : recipeMap.values()) {
            indexRecipe(dto);
            appendHandlerRecipeKey(dto.handlerId, makeKey(dto.handlerId, dto.recipeIndex));
        }
        for (List<String> keys : handlerRecipeKeys.values()) {
            sortRecipeKeys(keys);
        }
        rebuildHandlerCountsLocked();
        indexesDirty = false;
    }

    private void rebuildHandlerCountsLocked() {
        for (HandlerInfo info : handlerInfoMap.values()) {
            info.recipeCount = 0;
        }
        for (RecipeDto dto : recipeMap.values()) {
            HandlerInfo info = handlerInfoMap.get(dto.handlerId);
            if (info == null) {
                info = new HandlerInfo(dto.handlerId, dto.handlerName, 0);
                handlerInfoMap.put(dto.handlerId, info);
            }
            info.recipeCount++;
        }
    }

    public List<HandlerInfo> listHandlers() {
        lock.readLock()
            .lock();
        try {
            return new ArrayList<HandlerInfo>(handlerInfoMap.values());
        } finally {
            lock.readLock()
                .unlock();
        }
    }

    public int getRecipeCount() {
        lock.readLock()
            .lock();
        try {
            return recipeMap.size();
        } finally {
            lock.readLock()
                .unlock();
        }
    }

    public CacheStatus getStatus() {
        lock.readLock()
            .lock();
        try {
            return new CacheStatus(
                recipeMap.size(),
                handlerInfoMap.size(),
                totalIngested,
                lastUpdateTime,
                lastDiskSave,
                diskCacheSize,
                diskLoading,
                diskLoadComplete);
        } finally {
            lock.readLock()
                .unlock();
        }
    }

    /** Clear memory caches only (used at start of a full re-upload). Does not delete disk files until save. */
    public void clearMemoryOnly() {
        lock.writeLock()
            .lock();
        try {
            recipeMap.clear();
            outputIndex.clear();
            inputIndex.clear();
            handlerInfoMap.clear();
            itemSuggestMap.clear();
            handlerRecipeKeys.clear();
            suggestPrefixIndex.clear();
            totalIngested = 0;
            lastUpdateTime = System.currentTimeMillis();
            indexesDirty = false;
        } finally {
            lock.writeLock()
                .unlock();
        }
    }

    public void clear() {
        clearMemoryOnly();
        clearDiskCache();
    }

    public void clearDiskCache() {
        File json = TeXTechDataDir.webAeFile(JSON_FILENAME);
        File gz = TeXTechDataDir.webAeFile(GZ_FILENAME);
        boolean changed = false;
        if (json.exists()) {
            changed |= json.delete();
        }
        if (gz.exists()) {
            changed |= gz.delete();
        }
        if (changed) {
            lastDiskSave = 0;
            diskCacheSize = 0;
            AdvanceDataMonitor.LOG.info("[WebAE] Recipe cache files removed from disk.");
        }
    }

    public void requestSave() {
        if (savePending) return;
        savePending = true;
        // Take a snapshot quickly under the read lock, then delegate the heavy
        // GSON serialization, GZIP compression, and file I/O to a background
        // thread to avoid blocking the server main tick for 200-800ms.
        Thread worker = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    saveAsync();
                } finally {
                    savePending = false;
                }
            }
        }, "WebAE-RecipeCache-Save");
        worker.setDaemon(true);
        worker.start();
    }

    /** Synchronous save kept for server-stop scenarios; still blocks, but stop is not performance-critical. */
    public void save() {
        lock.readLock()
            .lock();
        try {
            doSaveIo(doTakeSnapshot());
        } finally {
            lock.readLock()
                .unlock();
        }
    }

    /** Snapshot in lock, then release lock and do I/O on the calling thread (background for normal saves). */
    private void saveAsync() {
        List<RecipeDto> snapshot;
        lock.readLock()
            .lock();
        try {
            snapshot = doTakeSnapshot();
        } finally {
            lock.readLock()
                .unlock();
        }
        doSaveIo(snapshot);
    }

    private List<RecipeDto> doTakeSnapshot() {
        return new ArrayList<RecipeDto>(recipeMap.values());
    }

    private void doSaveIo(List<RecipeDto> all) {
        try {
            boolean gzip = isGzipDiskFormat();
            String filename = gzip ? GZ_FILENAME : JSON_FILENAME;
            File file = TeXTechDataDir.webAeFile(filename);
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            File tmp = new File(parent, filename + ".tmp");

            RecipeCacheFile cacheFile = new RecipeCacheFile(SAVE_SCHEMA_VERSION, all.size(), all);
            OutputStream fos = new FileOutputStream(tmp);
            try {
                OutputStream out = gzip ? new GZIPOutputStream(fos) : fos;
                Writer writer = new OutputStreamWriter(out, "UTF-8");
                try {
                    GSON.toJson(cacheFile, writer);
                } finally {
                    writer.flush();
                    writer.close();
                }
            } finally {
                fos.close();
            }
            if (file.exists()) file.delete();
            if (!tmp.renameTo(file)) {
                AdvanceDataMonitor.LOG.warn("[WebAE] Failed to rename {} to {}", tmp.getName(), file.getName());
                return;
            }
            File alternate = TeXTechDataDir.webAeFile(gzip ? JSON_FILENAME : GZ_FILENAME);
            if (alternate.exists()) {
                alternate.delete();
            }
            diskCacheSize = file.length();
            lastDiskSave = System.currentTimeMillis();
            AdvanceDataMonitor.LOG.info(
                "[WebAE] Saved {} recipes ({}, {} bytes) to {}",
                all.size(),
                gzip ? "gzip" : "json",
                diskCacheSize,
                file.getAbsolutePath());
        } catch (IOException e) {
            AdvanceDataMonitor.LOG.error("[WebAE] Failed to save recipes", e);
        }
    }

    public void load() {
        diskLoading = true;
        diskLoadComplete = false;
        try {
            boolean gzipPreferred = isGzipDiskFormat();
            File primary = TeXTechDataDir.webAeFile(gzipPreferred ? GZ_FILENAME : JSON_FILENAME);
            File fallback = TeXTechDataDir.webAeFile(gzipPreferred ? JSON_FILENAME : GZ_FILENAME);
            if (primary.exists()) {
                loadFromFile(primary, primary.getName()
                    .endsWith(".gz"));
                return;
            }
            if (fallback.exists()) {
                AdvanceDataMonitor.LOG.info(
                    "[WebAE] Primary recipe file {} missing — falling back to {}",
                    primary.getName(),
                    fallback.getName());
                loadFromFile(fallback, fallback.getName()
                    .endsWith(".gz"));
            }
        } finally {
            diskLoading = false;
            diskLoadComplete = true;
        }
    }

    private static boolean isGzipDiskFormat() {
        String fmt = Config.webRecipeDiskFormat;
        return fmt != null && "gzip".equalsIgnoreCase(fmt.trim());
    }

    private void loadFromFile(File file, boolean gzip) {
        try {
            FileInputStream fis = new FileInputStream(file);
            try {
                Reader reader = new InputStreamReader(gzip ? new GZIPInputStream(fis) : fis, "UTF-8");
                JsonReader jsonReader = new JsonReader(reader);
                int loaded = streamParseRecipes(jsonReader);
                jsonReader.close();
                if (loaded > 0) {
                    AdvanceDataMonitor.LOG.info(
                        "[WebAE] Loaded {} recipes from {} (streaming{})",
                        loaded,
                        file.getAbsolutePath(),
                        gzip ? ", gzip" : "");
                }
                diskCacheSize = file.length();
                lastDiskSave = file.lastModified();
            } finally {
                fis.close();
            }
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.error("[WebAE] Failed to load recipes from {}", file.getAbsolutePath(), e);
        }
    }

    private int streamParseRecipes(JsonReader jsonReader) throws IOException {
        JsonToken first = jsonReader.peek();
        if (first == JsonToken.BEGIN_ARRAY) {
            int count = streamParseRecipeArray(jsonReader);
            rebuildIndexes();
            return count;
        }
        if (first == JsonToken.BEGIN_OBJECT) {
            jsonReader.beginObject();
            int count = 0;
            while (jsonReader.hasNext()) {
                String name = jsonReader.nextName();
                if ("recipes".equals(name)) {
                    count = streamParseRecipeArray(jsonReader);
                } else {
                    jsonReader.skipValue();
                }
            }
            jsonReader.endObject();
            rebuildIndexes();
            return count;
        }
        AdvanceDataMonitor.LOG.warn("[WebAE] Unexpected recipe cache JSON token: {}", first);
        return 0;
    }

    private int streamParseRecipeArray(JsonReader jsonReader) throws IOException {
        jsonReader.beginArray();
        int count = 0;
        while (jsonReader.hasNext()) {
            RecipeDto dto = GSON.fromJson(jsonReader, RecipeDto.class);
            if (dto != null) {
                ingestSingleRecipe(dto);
                count++;
                // Yield every 1000 recipes to reduce GC pressure during startup load.
                if (count % 1000 == 0) {
                    try {
                        Thread.sleep(1L);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        jsonReader.endArray();
        return count;
    }

    private void ingestSingleRecipe(RecipeDto dto) {
        lock.writeLock()
            .lock();
        try {
            String key = makeKey(dto.handlerId, dto.recipeIndex);
            recipeMap.put(key, dto);
            HandlerInfo info = handlerInfoMap.get(dto.handlerId);
            if (info == null) {
                info = new HandlerInfo(dto.handlerId, dto.handlerName, 0);
                handlerInfoMap.put(dto.handlerId, info);
            }
            indexRecipe(dto);
            totalIngested++;
            lastUpdateTime = System.currentTimeMillis();
        } finally {
            lock.writeLock()
                .unlock();
        }
    }

    private static String makeKey(String handlerId, int recipeIndex) {
        return handlerId + ":" + recipeIndex;
    }

    private static String escJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
            .replace("\"", "\\\"");
    }

    public static class HandlerInfo {

        public String handlerId;
        public String handlerName;
        public int recipeCount;

        public HandlerInfo(String handlerId, String handlerName, int recipeCount) {
            this.handlerId = handlerId;
            this.handlerName = handlerName;
            this.recipeCount = recipeCount;
        }
    }

    public static class CacheStatus {

        public int recipeCount;
        public int handlerCount;
        public int totalIngested;
        public long lastUpdateTime;
        public long lastDiskSave;
        public long diskCacheSize;
        public boolean diskLoading;
        public boolean diskLoadComplete;

        public CacheStatus(int recipeCount, int handlerCount, int totalIngested, long lastUpdateTime, long lastDiskSave,
            long diskCacheSize, boolean diskLoading, boolean diskLoadComplete) {
            this.recipeCount = recipeCount;
            this.handlerCount = handlerCount;
            this.totalIngested = totalIngested;
            this.lastUpdateTime = lastUpdateTime;
            this.lastDiskSave = lastDiskSave;
            this.diskCacheSize = diskCacheSize;
            this.diskLoading = diskLoading;
            this.diskLoadComplete = diskLoadComplete;
        }
    }

    public static class BrowseResult {

        public final List<RecipeDto> results;
        public final int total;
        public final int offset;
        public final int limit;

        public BrowseResult(List<RecipeDto> results, int total, int offset, int limit) {
            this.results = results;
            this.total = total;
            this.offset = offset;
            this.limit = limit;
        }
    }

    public static class QuerySearchResult {

        public final List<RecipeDto> results;
        public final int total;
        public final int offset;
        public final int limit;

        public QuerySearchResult(List<RecipeDto> results, int total, int offset, int limit) {
            this.results = results;
            this.total = total;
            this.offset = offset;
            this.limit = limit;
        }
    }

    public static class ItemSuggest {

        public String registryName;
        public String displayName;
        public String itemId;

        public ItemSuggest() {}

        public ItemSuggest(String registryName, String displayName, String itemId) {
            this.registryName = registryName;
            this.displayName = displayName;
            this.itemId = itemId;
        }
    }

    /** On-disk gzip wrapper with schema header. */
    public static class RecipeCacheFile {

        public int schemaVersion;
        public int recipeCount;
        public List<RecipeDto> recipes;

        public RecipeCacheFile() {}

        public RecipeCacheFile(int schemaVersion, int recipeCount, List<RecipeDto> recipes) {
            this.schemaVersion = schemaVersion;
            this.recipeCount = recipeCount;
            this.recipes = recipes;
        }
    }
}
