package com.imgood.textech.webae.search;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.imgood.textech.webae.cache.SnapshotCache;
import com.imgood.textech.webae.cache.SnapshotScheduler;
import com.imgood.textech.webae.context.WebAeOwnerContext;
import com.imgood.textech.webae.dto.GtMachineDto;
import com.imgood.textech.webae.dto.GtMachineListDto;
import com.imgood.textech.webae.dto.PatternBrowseEntryDto;
import com.imgood.textech.webae.dto.RecipeDto;
import com.imgood.textech.webae.dto.SearchResultDto;
import com.imgood.textech.webae.dto.StorageDto;
import com.imgood.textech.webae.pattern.PatternBrowseService;
import com.imgood.textech.webae.pattern.PatternBrowseService.BrowseResult;
import com.imgood.textech.webae.recipe.RecipeCacheStore;
import com.imgood.textech.webae.recipe.RecipeCacheStore.QuerySearchResult;
import com.imgood.textech.webae.snapshot.AeSnapshotCollector.NetworkInfo;

/**
 * Aggregates read-only search hits from snapshot cache, recipe cache, GT snapshots, and pattern browse.
 */
public final class WebSearchService {

    public static final int DEFAULT_LIMIT = 20;
    public static final int MAX_LIMIT = 50;
    public static final int PER_TYPE_CAP = 30;
    private static final String TYPE_STORAGE = "storage";
    private static final String TYPE_RECIPE = "recipe";
    private static final String TYPE_GT = "gt";
    private static final String TYPE_PATTERN = "pattern";

    private WebSearchService() {}

    public static final class SearchResponse {

        public String query;
        public int offset;
        public int limit;
        public int total;
        public List<SearchResultDto> results = new ArrayList<SearchResultDto>();
        public Map<String, Integer> countsByType = new HashMap<String, Integer>();
    }

    public static SearchResponse search(String ownerUuid, String query, int offset, int limit, Set<String> types,
        Integer networkFilter) {
        SearchResponse response = new SearchResponse();
        response.query = query;
        response.offset = Math.max(0, offset);
        response.limit = clampLimit(limit);

        String q = query == null ? "" : query.trim();
        if (q.isEmpty()) {
            response.total = 0;
            return response;
        }
        String qLower = q.toLowerCase();

        List<SearchResultDto> merged = new ArrayList<SearchResultDto>();
        List<Integer> networkIds = resolveNetworkIds(ownerUuid, networkFilter);

        if (types.contains(TYPE_STORAGE)) {
            List<SearchResultDto> storageHits = searchStorage(ownerUuid, networkIds, qLower);
            response.countsByType.put(TYPE_STORAGE, storageHits.size());
            merged.addAll(storageHits);
        }
        if (types.contains(TYPE_RECIPE)) {
            List<SearchResultDto> recipeHits = searchRecipes(q, qLower);
            response.countsByType.put(TYPE_RECIPE, recipeHits.size());
            merged.addAll(recipeHits);
        }
        if (types.contains(TYPE_GT)) {
            List<SearchResultDto> gtHits = searchGtMachines(ownerUuid, networkIds, qLower);
            response.countsByType.put(TYPE_GT, gtHits.size());
            merged.addAll(gtHits);
        }
        if (types.contains(TYPE_PATTERN)) {
            List<SearchResultDto> patternHits = searchPatterns(ownerUuid, networkIds, q);
            response.countsByType.put(TYPE_PATTERN, patternHits.size());
            merged.addAll(patternHits);
        }

        sortByRelevance(merged, qLower);
        response.total = merged.size();
        int end = Math.min(response.offset + response.limit, merged.size());
        if (response.offset >= merged.size()) {
            response.results = Collections.emptyList();
        } else {
            response.results = new ArrayList<SearchResultDto>(merged.subList(response.offset, end));
        }
        return response;
    }

    public static Set<String> parseTypes(String raw) {
        Set<String> all = defaultTypes();
        if (raw == null || raw.trim()
            .isEmpty()) {
            return all;
        }
        Set<String> selected = new HashSet<String>();
        String[] parts = raw.split(",");
        for (String part : parts) {
            String t = part.trim()
                .toLowerCase();
            if (all.contains(t)) {
                selected.add(t);
            }
        }
        return selected.isEmpty() ? all : selected;
    }

    public static Set<String> defaultTypes() {
        Set<String> types = new HashSet<String>();
        types.add(TYPE_STORAGE);
        types.add(TYPE_RECIPE);
        types.add(TYPE_GT);
        types.add(TYPE_PATTERN);
        return types;
    }

    public static int clampLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private static List<Integer> resolveNetworkIds(String ownerUuid, Integer networkFilter) {
        if (networkFilter != null) {
            List<Integer> single = new ArrayList<Integer>();
            single.add(networkFilter);
            return single;
        }
        List<NetworkInfo> networks = WebAeOwnerContext.findNetworksForOwner(ownerUuid);
        List<Integer> ids = new ArrayList<Integer>();
        if (networks != null) {
            for (NetworkInfo info : networks) {
                ids.add(info.networkId);
            }
        }
        return ids;
    }

    private static List<SearchResultDto> searchStorage(String ownerUuid, List<Integer> networkIds, String qLower) {
        List<SearchResultDto> hits = new ArrayList<SearchResultDto>();
        for (int networkId : networkIds) {
            SnapshotScheduler.markActive(ownerUuid, networkId);
            StorageDto dto = SnapshotCache.instance()
                .get(ownerUuid, networkId, "storage");
            if (dto == null) {
                dto = SnapshotCache.instance()
                    .getStale(ownerUuid, networkId, "storage");
            }
            if (dto == null) {
                continue;
            }
            collectStorageItems(hits, networkId, dto, qLower);
            collectStorageFluids(hits, networkId, dto, qLower);
            collectStorageEssentia(hits, networkId, dto, qLower);
            if (hits.size() >= PER_TYPE_CAP) {
                break;
            }
        }
        if (hits.size() > PER_TYPE_CAP) {
            return new ArrayList<SearchResultDto>(hits.subList(0, PER_TYPE_CAP));
        }
        return hits;
    }

    private static void collectStorageItems(List<SearchResultDto> hits, int networkId, StorageDto dto, String qLower) {
        if (dto.items == null) {
            return;
        }
        for (StorageDto.ItemEntry item : dto.items) {
            if (hits.size() >= PER_TYPE_CAP) {
                return;
            }
            if (!matchesStorageItem(item, qLower)) {
                continue;
            }
            SearchResultDto entry = new SearchResultDto();
            entry.type = TYPE_STORAGE;
            entry.category = "item";
            entry.networkId = networkId;
            entry.label = item.displayName != null && !item.displayName.isEmpty() ? item.displayName
                : item.registryName;
            entry.subtitle = "Network " + networkId + " · " + item.amount;
            entry.itemId = item.itemId;
            entry.registryName = item.registryName;
            entry.meta = item.meta;
            entry.amount = item.amount;
            entry.id = "storage:" + networkId + ":item:" + safeId(item.registryName) + ":" + item.meta;
            hits.add(entry);
        }
    }

    private static void collectStorageFluids(List<SearchResultDto> hits, int networkId, StorageDto dto, String qLower) {
        if (dto.fluids == null) {
            return;
        }
        for (StorageDto.FluidEntry fluid : dto.fluids) {
            if (hits.size() >= PER_TYPE_CAP) {
                return;
            }
            if (fluid.fluidName == null || !fluid.fluidName.toLowerCase()
                .contains(qLower)) {
                continue;
            }
            SearchResultDto entry = new SearchResultDto();
            entry.type = TYPE_STORAGE;
            entry.category = "fluid";
            entry.networkId = networkId;
            entry.label = fluid.fluidName;
            entry.subtitle = "Network " + networkId + " · " + fluid.amount + " mB";
            entry.registryName = fluid.fluidName;
            entry.amount = fluid.amount;
            entry.id = "storage:" + networkId + ":fluid:" + safeId(fluid.fluidName);
            hits.add(entry);
        }
    }

    private static void collectStorageEssentia(List<SearchResultDto> hits, int networkId, StorageDto dto,
        String qLower) {
        if (dto.essentia == null) {
            return;
        }
        for (StorageDto.EssentiaEntry ess : dto.essentia) {
            if (hits.size() >= PER_TYPE_CAP) {
                return;
            }
            if (ess.aspect == null || !ess.aspect.toLowerCase()
                .contains(qLower)) {
                continue;
            }
            SearchResultDto entry = new SearchResultDto();
            entry.type = TYPE_STORAGE;
            entry.category = "essentia";
            entry.networkId = networkId;
            entry.label = ess.aspect;
            entry.subtitle = "Network " + networkId + " · " + ess.amount;
            entry.registryName = ess.aspect;
            entry.amount = ess.amount;
            entry.id = "storage:" + networkId + ":essentia:" + safeId(ess.aspect);
            hits.add(entry);
        }
    }

    private static boolean matchesStorageItem(StorageDto.ItemEntry item, String qLower) {
        if (containsIgnoreCase(item.displayName, qLower)) {
            return true;
        }
        if (containsIgnoreCase(item.registryName, qLower)) {
            return true;
        }
        return containsIgnoreCase(item.itemId, qLower);
    }

    private static List<SearchResultDto> searchRecipes(String query, String qLower) {
        RecipeCacheStore.instance()
            .ensureLoaded();
        QuerySearchResult result = RecipeCacheStore.instance()
            .searchByQuery(query, null, "all", 0, PER_TYPE_CAP);
        List<SearchResultDto> hits = new ArrayList<SearchResultDto>();
        if (result.results == null) {
            return hits;
        }
        for (RecipeDto recipe : result.results) {
            SearchResultDto entry = new SearchResultDto();
            entry.type = TYPE_RECIPE;
            entry.handlerId = recipe.handlerId;
            entry.recipeIndex = recipe.recipeIndex;
            entry.label = primaryRecipeLabel(recipe);
            entry.subtitle = recipe.handlerName != null ? recipe.handlerName : recipe.handlerId;
            entry.id = "recipe:" + safeId(recipe.handlerId) + ":" + recipe.recipeIndex;
            if (recipe.outputs != null && !recipe.outputs.isEmpty()) {
                RecipeDto.ItemEntry out = recipe.outputs.get(0);
                entry.registryName = out.registryName;
                entry.itemId = out.itemId;
                entry.meta = out.meta;
            }
            hits.add(entry);
        }
        return hits;
    }

    private static String primaryRecipeLabel(RecipeDto recipe) {
        if (recipe.outputs != null && !recipe.outputs.isEmpty()) {
            RecipeDto.ItemEntry out = recipe.outputs.get(0);
            if (out.displayName != null && !out.displayName.isEmpty()) {
                return out.displayName;
            }
            if (out.registryName != null && !out.registryName.isEmpty()) {
                return out.registryName;
            }
        }
        if (recipe.handlerName != null && !recipe.handlerName.isEmpty()) {
            return recipe.handlerName;
        }
        return recipe.handlerId + "#" + recipe.recipeIndex;
    }

    private static List<SearchResultDto> searchGtMachines(String ownerUuid, List<Integer> networkIds, String qLower) {
        List<SearchResultDto> hits = new ArrayList<SearchResultDto>();
        for (int networkId : networkIds) {
            SnapshotScheduler.markActive(ownerUuid, networkId);
            GtMachineListDto dto = SnapshotCache.instance()
                .get(ownerUuid, networkId, "gt_machines");
            if (dto == null) {
                dto = SnapshotCache.instance()
                    .getStale(ownerUuid, networkId, "gt_machines");
            }
            if (dto == null || dto.machines == null) {
                continue;
            }
            for (GtMachineDto machine : dto.machines) {
                if (hits.size() >= PER_TYPE_CAP) {
                    return hits;
                }
                if (!matchesGtMachine(machine, qLower)) {
                    continue;
                }
                SearchResultDto entry = new SearchResultDto();
                entry.type = TYPE_GT;
                entry.networkId = networkId;
                entry.x = machine.x;
                entry.y = machine.y;
                entry.z = machine.z;
                entry.dim = machine.dim;
                entry.label = machine.recipeMapName != null && !machine.recipeMapName.isEmpty() ? machine.recipeMapName
                    : (machine.currentOutput != null ? machine.currentOutput : "GT Machine");
                entry.subtitle = "Network " + networkId
                    + " · "
                    + machine.x
                    + ","
                    + machine.y
                    + ","
                    + machine.z
                    + " · "
                    + (machine.statusText != null ? machine.statusText : "");
                entry.id = "gt:" + networkId + ":" + machine.x + ":" + machine.y + ":" + machine.z + ":" + machine.dim;
                hits.add(entry);
            }
        }
        return hits;
    }

    private static boolean matchesGtMachine(GtMachineDto machine, String qLower) {
        if (containsIgnoreCase(machine.recipeMapName, qLower)) {
            return true;
        }
        if (containsIgnoreCase(machine.statusText, qLower)) {
            return true;
        }
        if (containsIgnoreCase(machine.currentOutput, qLower)) {
            return true;
        }
        if (containsIgnoreCase(machine.machineMode, qLower)) {
            return true;
        }
        String coords = machine.x + "," + machine.y + "," + machine.z;
        return coords.contains(qLower);
    }

    private static List<SearchResultDto> searchPatterns(String ownerUuid, List<Integer> networkIds, String query) {
        List<SearchResultDto> hits = new ArrayList<SearchResultDto>();
        for (int networkId : networkIds) {
            if (hits.size() >= PER_TYPE_CAP) {
                break;
            }
            SnapshotScheduler.markActive(ownerUuid, networkId);
            int remaining = PER_TYPE_CAP - hits.size();
            BrowseResult browse = PatternBrowseService.browse(ownerUuid, networkId, query, 0, remaining, "both");
            if (browse.entries == null) {
                continue;
            }
            for (PatternBrowseEntryDto pattern : browse.entries) {
                SearchResultDto entry = new SearchResultDto();
                entry.type = TYPE_PATTERN;
                entry.networkId = networkId;
                entry.patternId = pattern.patternId;
                entry.gridKey = pattern.gridKey;
                entry.source = pattern.source;
                entry.label = pattern.displayName != null && !pattern.displayName.isEmpty() ? pattern.displayName
                    : pattern.registryName;
                entry.subtitle = "Network " + networkId + " · " + (pattern.source != null ? pattern.source : "pattern");
                entry.registryName = pattern.registryName;
                entry.meta = pattern.meta;
                entry.amount = pattern.amount;
                String idPart = pattern.patternId != null ? pattern.patternId
                    : (pattern.gridKey != null ? pattern.gridKey : "unknown");
                entry.id = "pattern:" + networkId + ":" + safeId(idPart);
                hits.add(entry);
            }
        }
        return hits;
    }

    private static void sortByRelevance(List<SearchResultDto> hits, final String qLower) {
        Collections.sort(hits, new Comparator<SearchResultDto>() {

            @Override
            public int compare(SearchResultDto a, SearchResultDto b) {
                int scoreA = relevanceScore(a, qLower);
                int scoreB = relevanceScore(b, qLower);
                if (scoreA != scoreB) {
                    return scoreB - scoreA;
                }
                String la = a.label != null ? a.label : "";
                String lb = b.label != null ? b.label : "";
                return la.compareToIgnoreCase(lb);
            }
        });
    }

    private static int relevanceScore(SearchResultDto entry, String qLower) {
        String label = entry.label != null ? entry.label.toLowerCase() : "";
        if (label.equals(qLower)) {
            return 100;
        }
        if (label.startsWith(qLower)) {
            return 80;
        }
        if (label.contains(qLower)) {
            return 60;
        }
        if (containsIgnoreCase(entry.registryName, qLower)) {
            return 40;
        }
        if (containsIgnoreCase(entry.subtitle, qLower)) {
            return 20;
        }
        return 0;
    }

    private static boolean containsIgnoreCase(String value, String qLower) {
        return value != null && value.toLowerCase()
            .contains(qLower);
    }

    private static String safeId(String raw) {
        if (raw == null) {
            return "unknown";
        }
        return raw.replace(":", "_")
            .replace(" ", "_");
    }
}
