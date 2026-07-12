package com.imgood.textech.webae.api.handler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.xml.bind.DatatypeConverter;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.imgood.textech.webae.cache.SnapshotCache;
import com.imgood.textech.webae.cache.SnapshotScheduler;
import com.imgood.textech.webae.dto.StorageDto;
import com.imgood.textech.webae.dto.StorageDto.EssentiaEntry;
import com.imgood.textech.webae.dto.StorageDto.FluidEntry;
import com.imgood.textech.webae.dto.StorageDto.ItemEntry;
import com.imgood.textech.webae.dto.StoragePagedDto;

import fi.iki.elonen.NanoHTTPD;

/**
 * Cursor-paginated storage reads — slices in-memory cached {@link StorageDto} lists
 * without blocking AE on the HTTP thread.
 *
 * <ul>
 * <li>GET /api/storage/items?network=&amp;cursor=&amp;limit=&amp;sort=&amp;search=</li>
 * <li>GET /api/storage/fluids?network=&amp;cursor=&amp;limit=&amp;sort=&amp;search=</li>
 * <li>GET /api/storage/essentia?network=&amp;cursor=&amp;limit=&amp;sort=&amp;search=</li>
 * </ul>
 */
public final class StoragePagedHandler {

    private static final Gson GSON = new GsonBuilder().serializeNulls()
        .create();
    private static final int DEFAULT_LIMIT = 200;
    private static final int MAX_LIMIT = 500;
    private static final String SORT_AMOUNT_DESC = "amount_desc";
    private static final String SORT_AMOUNT_ASC = "amount_asc";
    private static final String SORT_NAME_ASC = "name_asc";
    private static final String SORT_NAME_DESC = "name_desc";

    private enum ListKind {
        ITEMS,
        FLUIDS,
        ESSENTIA
    }

    private StoragePagedHandler() {}

    public static NanoHTTPD.Response handle(String uri, Map<String, String> params, String playerUuid) {
        ListKind kind;
        if ("/api/storage/items".equals(uri)) {
            kind = ListKind.ITEMS;
        } else if ("/api/storage/fluids".equals(uri)) {
            kind = ListKind.FLUIDS;
        } else if ("/api/storage/essentia".equals(uri)) {
            kind = ListKind.ESSENTIA;
        } else {
            return json(
                NanoHTTPD.Response.Status.NOT_FOUND,
                "{\"success\":false,\"message\":\"Unknown storage paged endpoint\"}");
        }
        return handlePaged(kind, params, playerUuid);
    }

    private static NanoHTTPD.Response handlePaged(ListKind kind, Map<String, String> params, String playerUuid) {
        Integer networkId = parseNetwork(params);
        if (networkId == null) {
            return json(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                "{\"success\":false,\"message\":\"Missing or invalid 'network' parameter\"}");
        }

        SnapshotScheduler.markActive(playerUuid, networkId);

        StorageDto snapshot = SnapshotCache.instance()
            .get(playerUuid, networkId, "storage");
        boolean fromCache = snapshot != null;
        if (snapshot == null) {
            snapshot = SnapshotCache.instance()
                .getStale(playerUuid, networkId, "storage");
        }

        long snapshotVersion = SnapshotCache.instance()
            .snapshotVersion(playerUuid, networkId, "storage");
        long cacheAgeMs = snapshotVersion > 0 ? System.currentTimeMillis() - snapshotVersion : 0L;

        if (snapshot == null) {
            StoragePagedDto empty = new StoragePagedDto();
            empty.success = true;
            empty.fromCache = false;
            empty.cacheAgeMs = 0L;
            empty.snapshotVersion = 0L;
            empty.networkId = networkId;
            return json(NanoHTTPD.Response.Status.OK, GSON.toJson(empty));
        }

        String sort = normalizeSort(params.get("sort"));
        String search = params.get("search") != null ? params.get("search")
            .trim() : "";
        int searchHash = search.toLowerCase(Locale.ROOT)
            .hashCode();
        int limit = clampLimit(parseIntParam(params.get("limit"), DEFAULT_LIMIT));

        CursorState cursorState = decodeCursor(params.get("cursor"));
        if (cursorState != null) {
            if (cursorState.snapshotVersion != snapshotVersion) {
                return json(
                    NanoHTTPD.Response.Status.CONFLICT,
                    "{\"success\":false,\"code\":\"cursor_stale\",\"snapshotVersion\":" + snapshotVersion
                        + ",\"message\":\"Snapshot refreshed; restart pagination\"}");
            }
            if (!cursorState.sort.equals(sort) || cursorState.searchHash != searchHash) {
                return json(
                    NanoHTTPD.Response.Status.CONFLICT,
                    "{\"success\":false,\"code\":\"cursor_stale\",\"snapshotVersion\":" + snapshotVersion
                        + ",\"message\":\"Sort or search changed; restart pagination\"}");
            }
        }

        int offset = cursorState != null ? cursorState.offset : 0;
        List<?> filtered = filterAndSort(kind, snapshot, search, sort);
        int total = filtered.size();
        int end = Math.min(offset + limit, total);
        List<?> page = offset >= total ? Collections.emptyList() : filtered.subList(offset, end);

        StoragePagedDto dto = new StoragePagedDto();
        dto.success = true;
        dto.totalEstimate = total;
        dto.fromCache = fromCache;
        dto.cacheAgeMs = cacheAgeMs;
        dto.snapshotVersion = snapshotVersion;
        dto.networkId = snapshot.networkId;
        dto.bytesUsed = snapshot.bytesUsed;
        dto.bytesMax = snapshot.bytesMax;
        dto.cpus = snapshot.cpus != null ? snapshot.cpus : dto.cpus;

        if (kind == ListKind.ITEMS) {
            long sum = 0L;
            for (Object o : filtered) {
                sum += ((ItemEntry) o).amount;
            }
            dto.totalAmountSum = sum;
        }

        if (kind == ListKind.ITEMS) {
            for (Object o : page) {
                dto.items.add((ItemEntry) o);
            }
        } else if (kind == ListKind.FLUIDS) {
            for (Object o : page) {
                dto.fluids.add((FluidEntry) o);
            }
        } else {
            for (Object o : page) {
                dto.essentia.add((EssentiaEntry) o);
            }
        }

        if (end < total) {
            CursorState next = new CursorState(end, sort, searchHash, snapshotVersion);
            dto.nextCursor = encodeCursor(next);
        }

        return json(NanoHTTPD.Response.Status.OK, GSON.toJson(dto));
    }

    private static List<?> filterAndSort(ListKind kind, StorageDto snapshot, String search, String sort) {
        if (kind == ListKind.ITEMS) {
            return filterSortItems(snapshot.items, search, sort);
        }
        if (kind == ListKind.FLUIDS) {
            return filterSortFluids(snapshot.fluids, search, sort);
        }
        return filterSortEssentia(snapshot.essentia, search, sort);
    }

    private static List<ItemEntry> filterSortItems(List<ItemEntry> source, String search, String sort) {
        // Use the snapshot list directly when sort order matches the pre-sorted default
        // (amount_desc). This avoids an O(n log n) re-sort on every paginated page load.
        List<ItemEntry> list;
        if (SORT_AMOUNT_DESC.equals(sort) && (search == null || search.isEmpty())) {
            list = source; // already pre-sorted by amount desc during collection
        } else {
            // Only copy + sort when the user requests a different order or search filter
            list = new ArrayList<ItemEntry>(source != null ? source : Collections.<ItemEntry>emptyList());
            if (!SORT_AMOUNT_DESC.equals(sort)) {
                sortItems(list, sort);
            }
        }
        // Search filter is a light pass — only copy elements matching the query
        if (search != null && !search.isEmpty()) {
            String q = search.toLowerCase(Locale.ROOT);
            List<ItemEntry> filtered = new ArrayList<ItemEntry>();
            for (ItemEntry item : list) {
                if (item == null) continue;
                String name = item.displayName != null ? item.displayName.toLowerCase(Locale.ROOT) : "";
                String reg = item.registryName != null ? item.registryName.toLowerCase(Locale.ROOT) : "";
                if (name.contains(q) || reg.contains(q)) {
                    filtered.add(item);
                }
            }
            return filtered;
        }
        return list;
    }

    private static List<FluidEntry> filterSortFluids(List<FluidEntry> source, String search, String sort) {
        List<FluidEntry> list;
        if (SORT_AMOUNT_DESC.equals(sort) && (search == null || search.isEmpty())) {
            list = source; // pre-sorted
        } else {
            list = new ArrayList<FluidEntry>(source != null ? source : Collections.<FluidEntry>emptyList());
            if (!SORT_AMOUNT_DESC.equals(sort)) {
                sortFluids(list, sort);
            }
        }
        if (search != null && !search.isEmpty()) {
            String q = search.toLowerCase(Locale.ROOT);
            List<FluidEntry> filtered = new ArrayList<FluidEntry>();
            for (FluidEntry fluid : list) {
                if (fluid == null) continue;
                String name = fluid.fluidName != null ? fluid.fluidName.toLowerCase(Locale.ROOT) : "";
                if (name.contains(q)) {
                    filtered.add(fluid);
                }
            }
            return filtered;
        }
        return list;
    }

    private static List<EssentiaEntry> filterSortEssentia(List<EssentiaEntry> source, String search, String sort) {
        List<EssentiaEntry> list;
        if (SORT_AMOUNT_DESC.equals(sort) && (search == null || search.isEmpty())) {
            list = source;
        } else {
            list = new ArrayList<EssentiaEntry>(source != null ? source : Collections.<EssentiaEntry>emptyList());
            if (!SORT_AMOUNT_DESC.equals(sort)) {
                sortEssentia(list, sort);
            }
        }
        if (search != null && !search.isEmpty()) {
            String q = search.toLowerCase(Locale.ROOT);
            List<EssentiaEntry> filtered = new ArrayList<EssentiaEntry>();
            for (EssentiaEntry entry : list) {
                if (entry == null) continue;
                String aspect = entry.aspect != null ? entry.aspect.toLowerCase(Locale.ROOT) : "";
                if (aspect.contains(q)) {
                    filtered.add(entry);
                }
            }
            return filtered;
        }
        return list;
    }

    private static void sortItems(List<ItemEntry> list, String sort) {
        Comparator<ItemEntry> cmp;
        if (SORT_AMOUNT_ASC.equals(sort)) {
            cmp = new Comparator<ItemEntry>() {

                @Override
                public int compare(ItemEntry a, ItemEntry b) {
                    int c = Long.compare(a.amount, b.amount);
                    if (c != 0) {
                        return c;
                    }
                    return safeName(a.displayName, a.registryName)
                        .compareToIgnoreCase(safeName(b.displayName, b.registryName));
                }
            };
        } else if (SORT_NAME_ASC.equals(sort)) {
            cmp = new Comparator<ItemEntry>() {

                @Override
                public int compare(ItemEntry a, ItemEntry b) {
                    return safeName(a.displayName, a.registryName)
                        .compareToIgnoreCase(safeName(b.displayName, b.registryName));
                }
            };
        } else if (SORT_NAME_DESC.equals(sort)) {
            cmp = new Comparator<ItemEntry>() {

                @Override
                public int compare(ItemEntry a, ItemEntry b) {
                    return safeName(b.displayName, b.registryName)
                        .compareToIgnoreCase(safeName(a.displayName, a.registryName));
                }
            };
        } else {
            cmp = new Comparator<ItemEntry>() {

                @Override
                public int compare(ItemEntry a, ItemEntry b) {
                    int c = Long.compare(b.amount, a.amount);
                    if (c != 0) {
                        return c;
                    }
                    return safeName(a.displayName, a.registryName)
                        .compareToIgnoreCase(safeName(b.displayName, b.registryName));
                }
            };
        }
        Collections.sort(list, cmp);
    }

    private static void sortFluids(List<FluidEntry> list, String sort) {
        Comparator<FluidEntry> cmp;
        if (SORT_AMOUNT_ASC.equals(sort)) {
            cmp = new Comparator<FluidEntry>() {

                @Override
                public int compare(FluidEntry a, FluidEntry b) {
                    int c = Long.compare(a.amount, b.amount);
                    if (c != 0) {
                        return c;
                    }
                    return safeStr(a.fluidName).compareToIgnoreCase(safeStr(b.fluidName));
                }
            };
        } else if (SORT_NAME_ASC.equals(sort)) {
            cmp = new Comparator<FluidEntry>() {

                @Override
                public int compare(FluidEntry a, FluidEntry b) {
                    return safeStr(a.fluidName).compareToIgnoreCase(safeStr(b.fluidName));
                }
            };
        } else if (SORT_NAME_DESC.equals(sort)) {
            cmp = new Comparator<FluidEntry>() {

                @Override
                public int compare(FluidEntry a, FluidEntry b) {
                    return safeStr(b.fluidName).compareToIgnoreCase(safeStr(a.fluidName));
                }
            };
        } else {
            cmp = new Comparator<FluidEntry>() {

                @Override
                public int compare(FluidEntry a, FluidEntry b) {
                    int c = Long.compare(b.amount, a.amount);
                    if (c != 0) {
                        return c;
                    }
                    return safeStr(a.fluidName).compareToIgnoreCase(safeStr(b.fluidName));
                }
            };
        }
        Collections.sort(list, cmp);
    }

    private static void sortEssentia(List<EssentiaEntry> list, String sort) {
        Comparator<EssentiaEntry> cmp;
        if (SORT_AMOUNT_ASC.equals(sort)) {
            cmp = new Comparator<EssentiaEntry>() {

                @Override
                public int compare(EssentiaEntry a, EssentiaEntry b) {
                    int c = Long.compare(a.amount, b.amount);
                    if (c != 0) {
                        return c;
                    }
                    return safeStr(a.aspect).compareToIgnoreCase(safeStr(b.aspect));
                }
            };
        } else if (SORT_NAME_ASC.equals(sort)) {
            cmp = new Comparator<EssentiaEntry>() {

                @Override
                public int compare(EssentiaEntry a, EssentiaEntry b) {
                    return safeStr(a.aspect).compareToIgnoreCase(safeStr(b.aspect));
                }
            };
        } else if (SORT_NAME_DESC.equals(sort)) {
            cmp = new Comparator<EssentiaEntry>() {

                @Override
                public int compare(EssentiaEntry a, EssentiaEntry b) {
                    return safeStr(b.aspect).compareToIgnoreCase(safeStr(a.aspect));
                }
            };
        } else {
            cmp = new Comparator<EssentiaEntry>() {

                @Override
                public int compare(EssentiaEntry a, EssentiaEntry b) {
                    int c = Long.compare(b.amount, a.amount);
                    if (c != 0) {
                        return c;
                    }
                    return safeStr(a.aspect).compareToIgnoreCase(safeStr(b.aspect));
                }
            };
        }
        Collections.sort(list, cmp);
    }

    private static String normalizeSort(String raw) {
        if (raw == null || raw.isEmpty()) {
            return SORT_AMOUNT_DESC;
        }
        String s = raw.trim()
            .toLowerCase(Locale.ROOT);
        if (SORT_AMOUNT_ASC.equals(s) || SORT_NAME_ASC.equals(s) || SORT_NAME_DESC.equals(s)) {
            return s;
        }
        return SORT_AMOUNT_DESC;
    }

    private static String safeName(String displayName, String registryName) {
        if (displayName != null && !displayName.isEmpty()) {
            return displayName;
        }
        return registryName != null ? registryName : "";
    }

    private static String safeStr(String s) {
        return s != null ? s : "";
    }

    private static int clampLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private static Integer parseNetwork(Map<String, String> params) {
        String s = params.get("network");
        if (s == null || s.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int parseIntParam(String raw, int defaultValue) {
        if (raw == null || raw.isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static final class CursorState {

        final int offset;
        final String sort;
        final int searchHash;
        final long snapshotVersion;

        CursorState(int offset, String sort, int searchHash, long snapshotVersion) {
            this.offset = offset;
            this.sort = sort;
            this.searchHash = searchHash;
            this.snapshotVersion = snapshotVersion;
        }
    }

    private static String encodeCursor(CursorState state) {
        JsonObject obj = new JsonObject();
        obj.addProperty("offset", state.offset);
        obj.addProperty("sort", state.sort);
        obj.addProperty("searchHash", state.searchHash);
        obj.addProperty("snapshotVersion", state.snapshotVersion);
        return DatatypeConverter.printBase64Binary(
            GSON.toJson(obj)
                .getBytes());
    }

    private static CursorState decodeCursor(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            byte[] decoded = DatatypeConverter.parseBase64Binary(raw);
            JsonObject obj = new JsonParser().parse(new String(decoded))
                .getAsJsonObject();
            int offset = obj.get("offset")
                .getAsInt();
            String sort = obj.get("sort")
                .getAsString();
            int searchHash = obj.get("searchHash")
                .getAsInt();
            long snapshotVersion = obj.get("snapshotVersion")
                .getAsLong();
            return new CursorState(offset, sort, searchHash, snapshotVersion);
        } catch (Exception e) {
            return null;
        }
    }

    private static NanoHTTPD.Response json(NanoHTTPD.Response.Status status, String body) {
        return NanoHTTPD.newFixedLengthResponse(status, "application/json", body);
    }
}
