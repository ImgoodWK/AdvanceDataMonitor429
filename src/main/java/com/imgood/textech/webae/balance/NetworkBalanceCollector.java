package com.imgood.textech.webae.balance;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.imgood.textech.webae.cache.SnapshotCache;
import com.imgood.textech.webae.dto.StorageDto;
import com.imgood.textech.webae.dto.StorageDto.EssentiaEntry;
import com.imgood.textech.webae.dto.StorageDto.FluidEntry;
import com.imgood.textech.webae.dto.StorageDto.ItemEntry;

/**
 * Compares cached AE storage snapshots across networks and emits read-only balance suggestions.
 */
public final class NetworkBalanceCollector {

    public static final long DEFAULT_MIN_SURPLUS = 64L;
    public static final long DEFAULT_MIN_SHORTAGE = 1L;
    public static final int DEFAULT_LIMIT = 50;

    private NetworkBalanceCollector() {}

    public static List<NetworkBalanceSuggestion> collect(String ownerUuid, int[] networkIds, long minSurplus,
        long minShortage, int limit) {
        if (ownerUuid == null || ownerUuid.isEmpty() || networkIds == null || networkIds.length < 2) {
            return Collections.emptyList();
        }
        if (minSurplus < 1L) {
            minSurplus = DEFAULT_MIN_SURPLUS;
        }
        if (minShortage < 1L) {
            minShortage = DEFAULT_MIN_SHORTAGE;
        }
        if (limit < 1) {
            limit = DEFAULT_LIMIT;
        }
        if (limit > 200) {
            limit = 200;
        }

        Map<Integer, StorageDto> storageByNet = new HashMap<Integer, StorageDto>();
        for (int i = 0; i < networkIds.length; i++) {
            int net = networkIds[i];
            StorageDto snap = SnapshotCache.instance()
                .getStale(ownerUuid, net, "storage");
            if (snap == null) {
                snap = SnapshotCache.instance()
                    .get(ownerUuid, net, "storage");
            }
            if (snap != null) {
                storageByNet.put(net, snap);
            }
        }
        if (storageByNet.size() < 2) {
            return Collections.emptyList();
        }

        List<NetworkBalanceSuggestion> out = new ArrayList<NetworkBalanceSuggestion>();
        collectItems(storageByNet, minSurplus, minShortage, out);
        collectFluids(storageByNet, minSurplus, minShortage, out);
        collectEssentia(storageByNet, minSurplus, minShortage, out);

        Collections.sort(out, new Comparator<NetworkBalanceSuggestion>() {

            @Override
            public int compare(NetworkBalanceSuggestion a, NetworkBalanceSuggestion b) {
                long diff = b.transferable - a.transferable;
                if (diff > 0L) {
                    return 1;
                }
                if (diff < 0L) {
                    return -1;
                }
                return 0;
            }
        });

        if (out.size() > limit) {
            return new ArrayList<NetworkBalanceSuggestion>(out.subList(0, limit));
        }
        return out;
    }

    private static void collectItems(Map<Integer, StorageDto> storageByNet, long minSurplus, long minShortage,
        List<NetworkBalanceSuggestion> out) {
        Map<String, Map<Integer, ItemEntry>> byKey = new HashMap<String, Map<Integer, ItemEntry>>();
        for (Map.Entry<Integer, StorageDto> e : storageByNet.entrySet()) {
            int net = e.getKey();
            StorageDto snap = e.getValue();
            if (snap.items == null) {
                continue;
            }
            for (ItemEntry item : snap.items) {
                if (item == null) {
                    continue;
                }
                String key = itemKey(item);
                if (key.isEmpty()) {
                    continue;
                }
                Map<Integer, ItemEntry> perNet = byKey.get(key);
                if (perNet == null) {
                    perNet = new HashMap<Integer, ItemEntry>();
                    byKey.put(key, perNet);
                }
                perNet.put(net, item);
            }
        }
        for (Map.Entry<String, Map<Integer, ItemEntry>> e : byKey.entrySet()) {
            addSuggestionsForAmounts(
                out,
                "item",
                e.getKey(),
                pickDisplayName(e.getValue()),
                amountsFromItems(e.getValue()),
                minSurplus,
                minShortage);
        }
    }

    private static void collectFluids(Map<Integer, StorageDto> storageByNet, long minSurplus, long minShortage,
        List<NetworkBalanceSuggestion> out) {
        Map<String, Map<Integer, Long>> byKey = new HashMap<String, Map<Integer, Long>>();
        for (Map.Entry<Integer, StorageDto> e : storageByNet.entrySet()) {
            int net = e.getKey();
            StorageDto snap = e.getValue();
            if (snap.fluids == null) {
                continue;
            }
            for (FluidEntry fluid : snap.fluids) {
                if (fluid == null || fluid.fluidName == null || fluid.fluidName.isEmpty()) {
                    continue;
                }
                String key = "fluid:" + fluid.fluidName.toLowerCase();
                Map<Integer, Long> perNet = byKey.get(key);
                if (perNet == null) {
                    perNet = new HashMap<Integer, Long>();
                    byKey.put(key, perNet);
                }
                perNet.put(net, fluid.amount);
            }
        }
        for (Map.Entry<String, Map<Integer, Long>> e : byKey.entrySet()) {
            String label = e.getKey()
                .startsWith("fluid:")
                    ? e.getKey()
                        .substring(6)
                    : e.getKey();
            addSuggestionsForAmounts(out, "fluid", e.getKey(), label, e.getValue(), minSurplus, minShortage);
        }
    }

    private static void collectEssentia(Map<Integer, StorageDto> storageByNet, long minSurplus, long minShortage,
        List<NetworkBalanceSuggestion> out) {
        Map<String, Map<Integer, Long>> byKey = new HashMap<String, Map<Integer, Long>>();
        for (Map.Entry<Integer, StorageDto> e : storageByNet.entrySet()) {
            int net = e.getKey();
            StorageDto snap = e.getValue();
            if (snap.essentia == null) {
                continue;
            }
            for (EssentiaEntry ess : snap.essentia) {
                if (ess == null || ess.aspect == null || ess.aspect.isEmpty()) {
                    continue;
                }
                String key = "essentia:" + ess.aspect.toLowerCase();
                Map<Integer, Long> perNet = byKey.get(key);
                if (perNet == null) {
                    perNet = new HashMap<Integer, Long>();
                    byKey.put(key, perNet);
                }
                perNet.put(net, ess.amount);
            }
        }
        for (Map.Entry<String, Map<Integer, Long>> e : byKey.entrySet()) {
            String label = e.getKey()
                .startsWith("essentia:")
                    ? e.getKey()
                        .substring(9)
                    : e.getKey();
            addSuggestionsForAmounts(out, "essentia", e.getKey(), label, e.getValue(), minSurplus, minShortage);
        }
    }

    private static Map<Integer, Long> amountsFromItems(Map<Integer, ItemEntry> perNet) {
        Map<Integer, Long> amounts = new HashMap<Integer, Long>();
        for (Map.Entry<Integer, ItemEntry> e : perNet.entrySet()) {
            if (e.getValue() != null) {
                amounts.put(e.getKey(), e.getValue().amount);
            }
        }
        return amounts;
    }

    private static String pickDisplayName(Map<Integer, ItemEntry> perNet) {
        for (ItemEntry item : perNet.values()) {
            if (item != null && item.displayName != null && !item.displayName.isEmpty()) {
                return item.displayName;
            }
        }
        for (ItemEntry item : perNet.values()) {
            if (item != null && item.registryName != null && !item.registryName.isEmpty()) {
                return item.registryName;
            }
        }
        return "";
    }

    private static String itemKey(ItemEntry item) {
        if (item.itemId != null && !item.itemId.isEmpty()) {
            return "item:" + item.itemId;
        }
        if (item.registryName != null && !item.registryName.isEmpty()) {
            return "item:" + item.registryName + ":" + item.meta;
        }
        return "";
    }

    private static void addSuggestionsForAmounts(List<NetworkBalanceSuggestion> out, String resourceType, String itemId,
        String displayName, Map<Integer, Long> amounts, long minSurplus, long minShortage) {
        if (amounts == null || amounts.size() < 2) {
            return;
        }
        long maxAmt = 0L;
        int maxNet = -1;
        long minAmt = Long.MAX_VALUE;
        int minNet = -1;
        for (Map.Entry<Integer, Long> e : amounts.entrySet()) {
            long amt = e.getValue() != null ? e.getValue()
                .longValue() : 0L;
            if (amt > maxAmt) {
                maxAmt = amt;
                maxNet = e.getKey();
            }
            if (amt < minAmt) {
                minAmt = amt;
                minNet = e.getKey();
            }
        }
        if (maxNet < 0 || minNet < 0 || maxNet == minNet) {
            return;
        }
        if (minAmt >= minShortage) {
            return;
        }
        long surplus = maxAmt - minAmt;
        if (surplus < minSurplus) {
            return;
        }
        NetworkBalanceSuggestion s = new NetworkBalanceSuggestion();
        s.resourceType = resourceType;
        s.itemId = itemId;
        s.displayName = displayName != null ? displayName : itemId;
        s.needyNetworkId = minNet;
        s.needyAmount = minAmt < 0L ? 0L : minAmt;
        s.sourceNetworkId = maxNet;
        s.sourceAmount = maxAmt;
        s.transferable = surplus;
        out.add(s);
    }
}
