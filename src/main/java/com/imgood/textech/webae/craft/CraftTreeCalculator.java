package com.imgood.textech.webae.craft;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.imgood.textech.webae.cache.SnapshotCache;
import com.imgood.textech.webae.cache.SnapshotScheduler;
import com.imgood.textech.webae.dto.RecipeDto;
import com.imgood.textech.webae.dto.RecipeDto.ItemEntry;
import com.imgood.textech.webae.dto.StorageDto;
import com.imgood.textech.webae.pattern.PatternBrowseService;
import com.imgood.textech.webae.recipe.RecipeCacheStore;

/**
 * Expands a target item into a material tree using the NEI recipe cache (Phase 6).
 */
public final class CraftTreeCalculator {

    private static final int DEFAULT_MAX_DEPTH = 8;
    private static final int MAX_DEPTH_CAP = 16;

    private CraftTreeCalculator() {}

    public static CraftTreeNodeDto build(String ownerUuid, int networkId, String itemKey, long amount, int maxDepth) {
        if (itemKey == null || itemKey.isEmpty()) {
            return null;
        }
        int depth = maxDepth <= 0 ? DEFAULT_MAX_DEPTH : Math.min(maxDepth, MAX_DEPTH_CAP);
        long qty = amount <= 0 ? 1L : amount;
        StorageDto storage = ownerUuid != null && !ownerUuid.isEmpty() ? SnapshotCache.instance()
            .getStale(ownerUuid, networkId, SnapshotScheduler.TYPE_STORAGE) : null;
        Set<String> visiting = new HashSet<String>();
        return expand(ownerUuid, networkId, itemKey, qty, depth, storage, visiting);
    }

    private static CraftTreeNodeDto expand(String ownerUuid, int networkId, String itemKey, long amount, int depthLeft,
        StorageDto storage, Set<String> visiting) {
        CraftTreeNodeDto node = new CraftTreeNodeDto();
        node.registryName = itemKey;
        node.itemId = itemKey;
        node.displayName = itemKey;
        node.required = amount;
        node.available = findAvailable(storage, itemKey);
        node.inStock = node.available;
        node.missing = Math.max(0L, node.required - node.available);
        node.toCraft = node.missing;
        if (ownerUuid != null && !ownerUuid.isEmpty() && node.toCraft > 0L) {
            String patternId = PatternBrowseService.findPatternIdForOutput(ownerUuid, networkId, itemKey);
            if (patternId != null && !patternId.isEmpty()) {
                node.patternId = patternId;
            }
        }

        if (depthLeft <= 0 || visiting.contains(itemKey.toLowerCase())) {
            node.leaf = true;
            return node;
        }

        List<RecipeDto> recipes = RecipeCacheStore.instance()
            .searchByOutput(itemKey, null);
        if (recipes == null || recipes.isEmpty()) {
            node.leaf = true;
            return node;
        }

        RecipeDto recipe = pickRecipe(recipes);
        if (recipe == null || recipe.outputs == null || recipe.outputs.isEmpty()) {
            node.leaf = true;
            return node;
        }

        ItemEntry primaryOut = recipe.outputs.get(0);
        if (primaryOut != null) {
            if (primaryOut.itemId != null && !primaryOut.itemId.isEmpty()) {
                node.itemId = primaryOut.itemId;
            }
            if (primaryOut.registryName != null && !primaryOut.registryName.isEmpty()) {
                node.registryName = primaryOut.registryName;
            }
            if (primaryOut.displayName != null && !primaryOut.displayName.isEmpty()) {
                node.displayName = primaryOut.displayName;
            }
            node.meta = primaryOut.meta;
        }
        node.leaf = false;
        node.recipeHandlerId = recipe.handlerId != null ? recipe.handlerId : "";
        node.recipeIndex = recipe.recipeIndex;

        int outSize = primaryOut != null && primaryOut.stackSize > 0 ? primaryOut.stackSize : 1;
        long craftsNeeded = (amount + outSize - 1L) / outSize;

        visiting.add(itemKey.toLowerCase());
        if (recipe.inputs != null) {
            for (ItemEntry input : recipe.inputs) {
                if (input == null) {
                    continue;
                }
                String inKey = inputKey(input);
                if (inKey.isEmpty()) {
                    continue;
                }
                int inSize = input.stackSize > 0 ? input.stackSize : 1;
                long childAmount = craftsNeeded * inSize;
                CraftTreeNodeDto child = expand(
                    ownerUuid,
                    networkId,
                    inKey,
                    childAmount,
                    depthLeft - 1,
                    storage,
                    visiting);
                if (child != null) {
                    if (input.displayName != null && !input.displayName.isEmpty()) {
                        child.displayName = input.displayName;
                    }
                    child.meta = input.meta;
                    node.children.add(child);
                }
            }
        }
        visiting.remove(itemKey.toLowerCase());
        return node;
    }

    private static RecipeDto pickRecipe(List<RecipeDto> recipes) {
        RecipeDto best = null;
        int bestInputs = Integer.MAX_VALUE;
        for (RecipeDto dto : recipes) {
            if (dto == null || dto.inputs == null) {
                continue;
            }
            int count = dto.inputs.size();
            if (count < bestInputs) {
                bestInputs = count;
                best = dto;
            }
        }
        return best != null ? best : recipes.get(0);
    }

    private static String inputKey(ItemEntry input) {
        if (input.registryName != null && !input.registryName.isEmpty()) {
            return input.registryName;
        }
        if (input.itemId != null && !input.itemId.isEmpty()) {
            return input.itemId;
        }
        return "";
    }

    private static long findAvailable(StorageDto storage, String itemKey) {
        if (storage == null || storage.items == null || itemKey == null) {
            return 0L;
        }
        String needle = itemKey.toLowerCase();
        long total = 0L;
        for (StorageDto.ItemEntry item : storage.items) {
            if (item == null) {
                continue;
            }
            String id = item.itemId != null ? item.itemId.toLowerCase() : "";
            String reg = item.registryName != null ? item.registryName.toLowerCase() : "";
            if (id.equals(needle) || reg.equals(needle) || id.contains(needle) || reg.contains(needle)) {
                total += item.amount;
            }
        }
        return total;
    }
}
