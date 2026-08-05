package com.imgood.textech.webae.worldmap;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Pure service for comparing the two versions retained by a world-map current
 * pointer. It performs no live topology reads and never guesses around an
 * invalid manifest.
 */
public final class WorldMapSnapshotDiffService {

    public static final int MAX_DETAIL = 1000;

    private WorldMapSnapshotDiffService() {}

    public static WorldMapSnapshotVersionsDto listVersions(String ownerUuid, int networkId) {
        WorldMapSnapshotVersionsDto out = new WorldMapSnapshotVersionsDto();
        if (!WorldMapPacketAuthorization.isValidOwnerUuid(ownerUuid)
            || !WorldMapPacketAuthorization.isValidNetworkId(networkId)) {
            out.success = false;
            out.status = "invalid";
            return out;
        }
        WorldMapSnapshotCurrentPointer pointer = WorldMapSnapshotStore
            .loadCurrentPointerUnchecked(ownerUuid, networkId);
        File pointerFile = WorldMapSnapshotStore.currentPointerFile(ownerUuid, networkId);
        if (pointer == null) {
            out.success = false;
            out.status = pointerFile != null && pointerFile.isFile() ? "unknown" : "no_versions";
            return out;
        }
        out.currentVersion = pointer.version;
        out.previousVersion = pointer.previousVersion;
        addVersionInfo(out, ownerUuid, networkId, pointer.version, pointer.timestamp, pointer.source, pointer.tilePx);
        if (pointer.previousVersion > 0) {
            addVersionInfo(out, ownerUuid, networkId, pointer.previousVersion, 0L, "", 0);
        }
        out.success = true;
        out.status = "ok";
        return out;
    }

    private static void addVersionInfo(WorldMapSnapshotVersionsDto out, String ownerUuid, int networkId, int version,
        long pointerTimestamp, String pointerSource, int pointerTilePx) {
        WorldMapSnapshotVersionsDto.VersionInfo info = new WorldMapSnapshotVersionsDto.VersionInfo();
        info.version = version;
        WorldMapSnapshotManifest manifest = WorldMapSnapshotStore.loadFinalizedManifest(ownerUuid, networkId, version);
        info.manifestAvailable = manifest != null;
        if (manifest != null) {
            info.timestamp = manifest.timestamp;
            info.source = manifest.source != null ? manifest.source : "";
            info.tilePx = manifest.tilePx;
        } else {
            // Pointer fields are only transport metadata; availability remains
            // explicitly false so callers cannot mistake them for a manifest.
            info.timestamp = pointerTimestamp;
            info.source = pointerSource != null ? pointerSource : "";
            info.tilePx = pointerTilePx;
        }
        WorldMapLogicalIndex logical = WorldMapSnapshotStore.loadLogicalIndex(ownerUuid, networkId, version);
        info.logicalAvailable = logical.logicalAvailable;
        out.versions.add(info);
    }

    /** Defaults to previous -> current when either argument is omitted. */
    public static WorldMapSnapshotDiffDto diff(String ownerUuid, int networkId, Integer fromVersion, Integer toVersion,
        WorldMapSnapshotDiffOptions options) {
        WorldMapSnapshotDiffDto out = new WorldMapSnapshotDiffDto();
        if (!WorldMapPacketAuthorization.isValidOwnerUuid(ownerUuid)
            || !WorldMapPacketAuthorization.isValidNetworkId(networkId)) {
            return failure(out, "invalid", "invalid");
        }
        WorldMapSnapshotCurrentPointer pointer = WorldMapSnapshotStore
            .loadCurrentPointerUnchecked(ownerUuid, networkId);
        File pointerFile = WorldMapSnapshotStore.currentPointerFile(ownerUuid, networkId);
        if (pointer == null) {
            return failure(
                out,
                pointerFile != null && pointerFile.isFile() ? "unknown" : "error",
                pointerFile != null && pointerFile.isFile() ? "unknown" : "no_versions");
        }

        if ((fromVersion != null && !WorldMapPacketAuthorization.isValidSnapshotVersion(fromVersion.intValue()))
            || (toVersion != null && !WorldMapPacketAuthorization.isValidSnapshotVersion(toVersion.intValue()))) {
            return failure(out, "error", "invalid");
        }
        if (fromVersion == null && pointer.previousVersion <= 0) {
            return failure(out, "error", "no_previous");
        }
        int to = toVersion != null ? toVersion.intValue() : pointer.version;
        int from = fromVersion != null ? fromVersion.intValue() : pointer.previousVersion;
        if (!isRetained(pointer, from) || !isRetained(pointer, to)) {
            return failure(out, "error", "not_retained");
        }
        if (from == to) {
            out.fromVersion = from;
            out.toVersion = to;
            return failure(out, "error", "same");
        }

        WorldMapSnapshotManifest fromManifest = WorldMapSnapshotStore.loadFinalizedManifest(ownerUuid, networkId, from);
        WorldMapSnapshotManifest toManifest = WorldMapSnapshotStore.loadFinalizedManifest(ownerUuid, networkId, to);
        if (fromManifest == null || toManifest == null) {
            out.fromVersion = from;
            out.toVersion = to;
            return failure(out, "unknown", "unknown_manifest");
        }
        out.fromVersion = from;
        out.toVersion = to;
        out.fromTimestamp = fromManifest.timestamp;
        out.toTimestamp = toManifest.timestamp;

        WorldMapSnapshotDiffOptions normalized = normalizeOptions(options);
        WorldMapLogicalIndex fromLogical = WorldMapSnapshotStore.loadLogicalIndex(ownerUuid, networkId, from);
        WorldMapLogicalIndex toLogical = WorldMapSnapshotStore.loadLogicalIndex(ownerUuid, networkId, to);
        out.logicalAvailable = fromLogical.logicalAvailable && toLogical.logicalAvailable;

        DetailCollector details = new DetailCollector();
        if (normalized.includeMarkers && out.logicalAvailable) {
            buildMarkerChanges(fromLogical, toLogical, normalized, out, details);
        }
        if (normalized.includeTiles) {
            buildTileChanges(fromManifest, toManifest, normalized, out, details);
        }
        for (Detail detail : details.sorted()) {
            if (detail.marker != null) {
                out.markerChanges.add(detail.marker);
            } else {
                out.tileChanges.add(detail.tile);
            }
        }
        out.truncated = details.isTruncated();
        out.summary.markerTotal = out.summary.markersAdded + out.summary.markersRemoved
            + out.summary.markersChanged
            + out.summary.markersMoved;
        out.summary.tileTotal = out.summary.tilesAdded + out.summary.tilesRemoved
            + out.summary.tilesChanged
            + out.summary.tilesUnchanged;
        out.summary.total = out.summary.markerTotal + out.summary.tileTotal;
        out.success = true;
        out.status = "ok";
        out.code = "ok";
        return out;
    }

    public static WorldMapSnapshotDiffDto diff(String ownerUuid, int networkId, int fromVersion, int toVersion,
        WorldMapSnapshotDiffOptions options) {
        return diff(ownerUuid, networkId, Integer.valueOf(fromVersion), Integer.valueOf(toVersion), options);
    }

    public static WorldMapSnapshotDiffDto diff(String ownerUuid, int networkId, WorldMapSnapshotDiffOptions options) {
        return diff(ownerUuid, networkId, null, null, options);
    }

    private static WorldMapSnapshotDiffDto failure(WorldMapSnapshotDiffDto out, String status, String code) {
        out.success = false;
        out.status = status;
        out.code = code;
        return out;
    }

    private static boolean isRetained(WorldMapSnapshotCurrentPointer pointer, int version) {
        return pointer != null && (pointer.version == version || pointer.previousVersion == version);
    }

    private static WorldMapSnapshotDiffOptions normalizeOptions(WorldMapSnapshotDiffOptions options) {
        WorldMapSnapshotDiffOptions normalized = options != null ? options.copy() : new WorldMapSnapshotDiffOptions();
        if (normalized.minX != null && normalized.maxX != null
            && normalized.minX.intValue() > normalized.maxX.intValue()) {
            Integer temp = normalized.minX;
            normalized.minX = normalized.maxX;
            normalized.maxX = temp;
        }
        if (normalized.minZ != null && normalized.maxZ != null
            && normalized.minZ.intValue() > normalized.maxZ.intValue()) {
            Integer temp = normalized.minZ;
            normalized.minZ = normalized.maxZ;
            normalized.maxZ = temp;
        }
        return normalized;
    }

    private static void buildTileChanges(WorldMapSnapshotManifest fromManifest, WorldMapSnapshotManifest toManifest,
        WorldMapSnapshotDiffOptions options, WorldMapSnapshotDiffDto out, DetailCollector details) {
        Map<String, WorldMapSnapshotManifest.TileEntry> from = fromManifest.tiles != null ? fromManifest.tiles
            : new HashMap<String, WorldMapSnapshotManifest.TileEntry>();
        Map<String, WorldMapSnapshotManifest.TileEntry> to = toManifest.tiles != null ? toManifest.tiles
            : new HashMap<String, WorldMapSnapshotManifest.TileEntry>();
        Set<String> keys = new TreeSet<String>();
        keys.addAll(from.keySet());
        keys.addAll(to.keySet());
        for (String key : keys) {
            TileCoords coords = parseTileKey(key);
            if (coords == null || !matchesTile(coords, options)) {
                continue;
            }
            WorldMapSnapshotManifest.TileEntry fromEntry = from.get(key);
            WorldMapSnapshotManifest.TileEntry toEntry = to.get(key);
            WorldMapSnapshotDiffDto.TileChange change = new WorldMapSnapshotDiffDto.TileChange();
            change.key = key;
            change.layer = coords.layer;
            change.dim = coords.dim;
            change.chunkX = coords.chunkX;
            change.chunkZ = coords.chunkZ;
            if (fromEntry == null) {
                change.status = "added";
                out.summary.tilesAdded++;
            } else if (toEntry == null) {
                change.status = "removed";
                out.summary.tilesRemoved++;
            } else {
                change.fromSha256 = value(fromEntry.sha256);
                change.toSha256 = value(toEntry.sha256);
                change.fromSize = fromEntry.size;
                change.toSize = toEntry.size;
                if (sameTile(fromEntry, toEntry)) {
                    change.status = "unchanged";
                    out.summary.tilesUnchanged++;
                } else {
                    change.status = "changed";
                    out.summary.tilesChanged++;
                }
            }
            if (fromEntry != null && toEntry == null) {
                change.fromSha256 = value(fromEntry.sha256);
                change.fromSize = fromEntry.size;
            }
            if (toEntry != null && fromEntry == null) {
                change.toSha256 = value(toEntry.sha256);
                change.toSize = toEntry.size;
            }
            details.add(Detail.tile(change));
        }
    }

    private static boolean sameTile(WorldMapSnapshotManifest.TileEntry from, WorldMapSnapshotManifest.TileEntry to) {
        return from != null && to != null && value(from.sha256).equals(value(to.sha256));
    }

    private static String value(String value) {
        return value != null ? value : "";
    }

    private static TileCoords parseTileKey(String key) {
        if (key == null) {
            return null;
        }
        String[] parts = key.split(":", -1);
        if (parts.length != 4 || !WorldMapPacketAuthorization.isValidLayer(parts[0])) {
            return null;
        }
        try {
            int dim = Integer.parseInt(parts[1]);
            int chunkX = Integer.parseInt(parts[2]);
            int chunkZ = Integer.parseInt(parts[3]);
            if (!WorldMapPacketAuthorization.isValidChunk(dim, chunkX, chunkZ)) {
                return null;
            }
            TileCoords out = new TileCoords();
            out.layer = WorldMapTileLayer.normalize(parts[0]);
            out.dim = dim;
            out.chunkX = chunkX;
            out.chunkZ = chunkZ;
            return out;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean matchesTile(TileCoords tile, WorldMapSnapshotDiffOptions options) {
        if (options.dimension != null && options.dimension.intValue() != tile.dim) {
            return false;
        }
        long minX = ((long) tile.chunkX) * 16L;
        long maxX = minX + 15L;
        long minZ = ((long) tile.chunkZ) * 16L;
        long maxZ = minZ + 15L;
        if (options.minX != null && maxX < options.minX.intValue()) {
            return false;
        }
        if (options.maxX != null && minX > options.maxX.intValue()) {
            return false;
        }
        if (options.minZ != null && maxZ < options.minZ.intValue()) {
            return false;
        }
        if (options.maxZ != null && minZ > options.maxZ.intValue()) {
            return false;
        }
        return true;
    }

    private static void buildMarkerChanges(WorldMapLogicalIndex from, WorldMapLogicalIndex to,
        WorldMapSnapshotDiffOptions options, WorldMapSnapshotDiffDto out, DetailCollector details) {
        Map<String, List<LogicalRecord>> fromById = markerMap(from, options);
        Map<String, List<LogicalRecord>> toById = markerMap(to, options);
        Set<String> ids = new TreeSet<String>();
        ids.addAll(fromById.keySet());
        ids.addAll(toById.keySet());
        List<LogicalRecord> removed = new ArrayList<LogicalRecord>();
        List<LogicalRecord> added = new ArrayList<LogicalRecord>();
        for (String id : ids) {
            List<LogicalRecord> left = fromById.get(id);
            List<LogicalRecord> right = toById.get(id);
            List<LogicalRecord> unmatchedLeft = new ArrayList<LogicalRecord>();
            List<LogicalRecord> unmatchedRight = new ArrayList<LogicalRecord>();
            collectUnmatched(left, right, unmatchedLeft, unmatchedRight);
            if (unmatchedLeft.size() == 1 && unmatchedRight.size() == 1) {
                WorldMapSnapshotDiffDto.MarkerChange change = markerChange(
                    "changed",
                    unmatchedLeft.get(0),
                    unmatchedRight.get(0));
                out.summary.markersChanged++;
                details.add(Detail.marker(change));
            } else {
                removed.addAll(unmatchedLeft);
                added.addAll(unmatchedRight);
            }
        }

        Map<MoveIdentity, List<LogicalRecord>> removedByMove = groupByMoveIdentity(removed);
        Map<MoveIdentity, List<LogicalRecord>> addedByMove = groupByMoveIdentity(added);
        Set<MoveIdentity> moveIdentities = new TreeSet<MoveIdentity>();
        moveIdentities.addAll(removedByMove.keySet());
        moveIdentities.addAll(addedByMove.keySet());
        Set<LogicalRecord> consumedRemoved = new HashSet<LogicalRecord>();
        Set<LogicalRecord> consumedAdded = new HashSet<LogicalRecord>();
        for (MoveIdentity identity : moveIdentities) {
            List<LogicalRecord> left = removedByMove.get(identity);
            List<LogicalRecord> right = addedByMove.get(identity);
            if (left != null && left.size() == 1 && right != null && right.size() == 1) {
                LogicalRecord before = left.get(0);
                LogicalRecord after = right.get(0);
                if (before.id.equals(after.id)) {
                    continue;
                }
                consumedRemoved.add(before);
                consumedAdded.add(after);
                WorldMapSnapshotDiffDto.MarkerChange change = markerChange("moved", before, after);
                out.summary.markersMoved++;
                details.add(Detail.marker(change));
            }
        }
        for (LogicalRecord before : removed) {
            if (!consumedRemoved.contains(before)) {
                out.summary.markersRemoved++;
                details.add(Detail.marker(markerChange("removed", before, null)));
            }
        }
        for (LogicalRecord after : added) {
            if (!consumedAdded.contains(after)) {
                out.summary.markersAdded++;
                details.add(Detail.marker(markerChange("added", null, after)));
            }
        }
    }

    /** Removes exact matches at one coordinate with a deterministic sorted merge. */
    private static void collectUnmatched(List<LogicalRecord> left, List<LogicalRecord> right,
        List<LogicalRecord> unmatchedLeft, List<LogicalRecord> unmatchedRight) {
        List<LogicalRecord> leftRecords = left != null ? left : Collections.<LogicalRecord>emptyList();
        List<LogicalRecord> rightRecords = right != null ? right : Collections.<LogicalRecord>emptyList();
        int leftIndex = 0;
        int rightIndex = 0;
        while (leftIndex < leftRecords.size() && rightIndex < rightRecords.size()) {
            LogicalRecord before = leftRecords.get(leftIndex);
            LogicalRecord after = rightRecords.get(rightIndex);
            int comparison = RECORD_COMPARATOR.compare(before, after);
            if (comparison == 0 && sameLogicalRecord(before, after)) {
                leftIndex++;
                rightIndex++;
            } else if (comparison < 0) {
                unmatchedLeft.add(before);
                leftIndex++;
            } else {
                unmatchedRight.add(after);
                rightIndex++;
            }
        }
        while (leftIndex < leftRecords.size()) {
            unmatchedLeft.add(leftRecords.get(leftIndex++));
        }
        while (rightIndex < rightRecords.size()) {
            unmatchedRight.add(rightRecords.get(rightIndex++));
        }
    }

    private static Map<MoveIdentity, List<LogicalRecord>> groupByMoveIdentity(List<LogicalRecord> records) {
        Map<MoveIdentity, List<LogicalRecord>> out = new TreeMap<MoveIdentity, List<LogicalRecord>>();
        for (LogicalRecord record : records) {
            MoveIdentity identity = MoveIdentity.of(record);
            if (identity == null) {
                continue;
            }
            List<LogicalRecord> group = out.get(identity);
            if (group == null) {
                group = new ArrayList<LogicalRecord>();
                out.put(identity, group);
            }
            group.add(record);
        }
        return out;
    }

    private static Map<String, List<LogicalRecord>> markerMap(WorldMapLogicalIndex index,
        WorldMapSnapshotDiffOptions options) {
        Map<String, List<LogicalRecord>> out = new TreeMap<String, List<LogicalRecord>>();
        if (index == null || !index.logicalAvailable) {
            return out;
        }
        // Placements are the richer logical source and therefore own a
        // coordinate whenever present. Markers are only a fallback. Keeping a
        // single deterministic record per coordinate also prevents the same
        // device being counted once as a marker and again as a placement.
        if (index.aePlacements != null) {
            for (WorldMapAePlacementRecord placement : index.aePlacements) {
                if (placement == null || !matches(placement.dim, placement.x, placement.z, options)) {
                    continue;
                }
                putPreferred(out, LogicalRecord.placement(placement));
            }
        }
        if (index.markers != null) {
            for (WorldMapMarkerDto marker : index.markers) {
                if (marker == null || !matches(marker.dim, marker.x, marker.z, options)) {
                    continue;
                }
                putPreferred(out, LogicalRecord.marker(marker));
            }
        }
        return out;
    }

    private static void putPreferred(Map<String, List<LogicalRecord>> map, LogicalRecord record) {
        List<LogicalRecord> list = map.get(record.id);
        if (list == null) {
            list = new ArrayList<LogicalRecord>();
            list.add(record);
            map.put(record.id, list);
            return;
        }
        LogicalRecord current = list.get(0);
        boolean recordIsPlacement = "placement".equals(record.source);
        boolean currentIsPlacement = "placement".equals(current.source);
        if ((recordIsPlacement && !currentIsPlacement)
            || (recordIsPlacement == currentIsPlacement && RECORD_COMPARATOR.compare(record, current) < 0)) {
            list.set(0, record);
        }
    }

    private static boolean matches(int dim, int x, int z, WorldMapSnapshotDiffOptions options) {
        if (options.dimension != null && options.dimension.intValue() != dim) {
            return false;
        }
        return (options.minX == null || x >= options.minX.intValue())
            && (options.maxX == null || x <= options.maxX.intValue())
            && (options.minZ == null || z >= options.minZ.intValue())
            && (options.maxZ == null || z <= options.maxZ.intValue());
    }

    private static boolean sameLogicalRecord(LogicalRecord before, LogicalRecord after) {
        if (before == null || after == null
            || !before.source.equals(after.source)
            || before.dim != after.dim
            || before.x != after.x
            || before.y != after.y
            || before.z != after.z) {
            return false;
        }
        if (before.marker != null || after.marker != null) {
            WorldMapMarkerDto a = before.marker;
            WorldMapMarkerDto b = after.marker;
            return a != null && b != null
                && eq(a.nodeId, b.nodeId)
                && eq(a.type, b.type)
                && eq(a.subtype, b.subtype)
                && eq(a.displayName, b.displayName)
                && eq(a.iconItemId, b.iconItemId)
                && a.channelCost == b.channelCost;
        }
        return eq(before.kind, after.kind) && eq(before.className, after.className)
            && eq(before.iconItemId, after.iconItemId)
            && eq(before.displayName, after.displayName);
    }

    private static boolean eq(String a, String b) {
        return value(a).equals(value(b));
    }

    private static WorldMapSnapshotDiffDto.MarkerChange markerChange(String status, LogicalRecord before,
        LogicalRecord after) {
        WorldMapSnapshotDiffDto.MarkerChange change = new WorldMapSnapshotDiffDto.MarkerChange();
        change.status = status;
        LogicalRecord primary = after != null ? after : before;
        change.id = primary != null ? primary.id : "";
        if (before != null) {
            change.source = before.source;
            change.fromDim = before.dim;
            change.fromX = before.x;
            change.fromY = before.y;
            change.fromZ = before.z;
            change.fromKind = value(before.kind);
            change.fromClassName = value(before.className);
            change.fromIconItemId = value(before.iconItemId);
            change.from = before.marker;
            change.fromPlacement = before.placement;
        }
        if (after != null) {
            change.source = after.source;
            change.toDim = after.dim;
            change.toX = after.x;
            change.toY = after.y;
            change.toZ = after.z;
            change.toKind = value(after.kind);
            change.toClassName = value(after.className);
            change.toIconItemId = value(after.iconItemId);
            change.to = after.marker;
            change.toPlacement = after.placement;
        }
        return change;
    }

    private static final Comparator<Detail> DETAIL_COMPARATOR = new Comparator<Detail>() {

        @Override
        public int compare(Detail a, Detail b) {
            if (a == b) {
                return 0;
            }
            if (a.marker != null && b.tile != null) {
                return -1;
            }
            if (a.tile != null && b.marker != null) {
                return 1;
            }
            String ak = a.marker != null ? a.marker.id : a.tile.key;
            String bk = b.marker != null ? b.marker.id : b.tile.key;
            int c = value(ak).compareTo(value(bk));
            if (c != 0) {
                return c;
            }
            String as = a.marker != null ? a.marker.status : a.tile.status;
            String bs = b.marker != null ? b.marker.status : b.tile.status;
            c = value(as).compareTo(value(bs));
            if (c != 0 || a.marker == null) {
                return c;
            }
            c = value(a.marker.source).compareTo(value(b.marker.source));
            if (c != 0) {
                return c;
            }
            c = comparePosition(
                a.marker.fromDim,
                a.marker.fromX,
                a.marker.fromY,
                a.marker.fromZ,
                b.marker.fromDim,
                b.marker.fromX,
                b.marker.fromY,
                b.marker.fromZ);
            if (c != 0) {
                return c;
            }
            c = comparePosition(
                a.marker.toDim,
                a.marker.toX,
                a.marker.toY,
                a.marker.toZ,
                b.marker.toDim,
                b.marker.toX,
                b.marker.toY,
                b.marker.toZ);
            if (c != 0) {
                return c;
            }
            c = value(a.marker.fromKind).compareTo(value(b.marker.fromKind));
            if (c != 0) {
                return c;
            }
            c = value(a.marker.toKind).compareTo(value(b.marker.toKind));
            if (c != 0) {
                return c;
            }
            c = value(a.marker.fromClassName).compareTo(value(b.marker.fromClassName));
            if (c != 0) {
                return c;
            }
            c = value(a.marker.toClassName).compareTo(value(b.marker.toClassName));
            if (c != 0) {
                return c;
            }
            c = value(a.marker.fromIconItemId).compareTo(value(b.marker.fromIconItemId));
            if (c != 0) {
                return c;
            }
            return value(a.marker.toIconItemId).compareTo(value(b.marker.toIconItemId));
        }
    };

    private static final Comparator<LogicalRecord> RECORD_COMPARATOR = new Comparator<LogicalRecord>() {

        @Override
        public int compare(LogicalRecord a, LogicalRecord b) {
            int c = value(a.id).compareTo(value(b.id));
            if (c != 0) {
                return c;
            }
            c = value(a.source).compareTo(value(b.source));
            if (c != 0) {
                return c;
            }
            c = value(a.kind).compareTo(value(b.kind));
            if (c != 0) {
                return c;
            }
            c = value(a.className).compareTo(value(b.className));
            if (c != 0) {
                return c;
            }
            c = value(a.iconItemId).compareTo(value(b.iconItemId));
            if (c != 0) {
                return c;
            }
            c = value(a.displayName).compareTo(value(b.displayName));
            if (c != 0) {
                return c;
            }
            if (a.marker != null || b.marker != null) {
                if (a.marker == null) {
                    return -1;
                }
                if (b.marker == null) {
                    return 1;
                }
                c = value(a.marker.nodeId).compareTo(value(b.marker.nodeId));
                if (c != 0) {
                    return c;
                }
                c = value(a.marker.subtype).compareTo(value(b.marker.subtype));
                if (c != 0) {
                    return c;
                }
                return Integer.compare(a.marker.channelCost, b.marker.channelCost);
            }
            return 0;
        }
    };

    private static int comparePosition(int aDim, int aX, int aY, int aZ, int bDim, int bX, int bY, int bZ) {
        int c = Integer.compare(aDim, bDim);
        if (c != 0) {
            return c;
        }
        c = Integer.compare(aX, bX);
        if (c != 0) {
            return c;
        }
        c = Integer.compare(aY, bY);
        return c != 0 ? c : Integer.compare(aZ, bZ);
    }

    /** Keeps only the deterministic first {@link #MAX_DETAIL} rows in memory. */
    private static final class DetailCollector {

        private final PriorityQueue<Detail> selected = new PriorityQueue<Detail>(
            MAX_DETAIL,
            Collections.reverseOrder(DETAIL_COMPARATOR));
        private int total;

        void add(Detail detail) {
            if (detail == null) {
                return;
            }
            total++;
            if (selected.size() < MAX_DETAIL) {
                selected.add(detail);
            } else if (DETAIL_COMPARATOR.compare(detail, selected.peek()) < 0) {
                selected.poll();
                selected.add(detail);
            }
        }

        List<Detail> sorted() {
            List<Detail> out = new ArrayList<Detail>(selected);
            Collections.sort(out, DETAIL_COMPARATOR);
            return out;
        }

        boolean isTruncated() {
            return total > MAX_DETAIL;
        }
    }

    private static final class Detail {

        final WorldMapSnapshotDiffDto.MarkerChange marker;
        final WorldMapSnapshotDiffDto.TileChange tile;

        private Detail(WorldMapSnapshotDiffDto.MarkerChange marker, WorldMapSnapshotDiffDto.TileChange tile) {
            this.marker = marker;
            this.tile = tile;
        }

        static Detail marker(WorldMapSnapshotDiffDto.MarkerChange marker) {
            return new Detail(marker, null);
        }

        static Detail tile(WorldMapSnapshotDiffDto.TileChange tile) {
            return new Detail(null, tile);
        }
    }

    private static final class TileCoords {

        String layer;
        int dim;
        int chunkX;
        int chunkZ;
    }

    private static final class MoveIdentity implements Comparable<MoveIdentity> {

        int dim;
        String kind;
        String className;
        String iconItemId;

        static MoveIdentity of(LogicalRecord record) {
            String kind = value(record.kind);
            String className = value(record.className);
            String iconItemId = value(record.iconItemId);
            if (kind.isEmpty() || className.isEmpty() || iconItemId.isEmpty()) {
                return null;
            }
            MoveIdentity out = new MoveIdentity();
            out.dim = record.dim;
            out.kind = kind;
            out.className = className;
            out.iconItemId = iconItemId;
            return out;
        }

        @Override
        public int compareTo(MoveIdentity other) {
            int c = Integer.compare(dim, other.dim);
            if (c != 0) {
                return c;
            }
            c = kind.compareTo(other.kind);
            if (c != 0) {
                return c;
            }
            c = className.compareTo(other.className);
            return c != 0 ? c : iconItemId.compareTo(other.iconItemId);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof MoveIdentity && compareTo((MoveIdentity) other) == 0;
        }

        @Override
        public int hashCode() {
            int result = dim;
            result = 31 * result + kind.hashCode();
            result = 31 * result + className.hashCode();
            return 31 * result + iconItemId.hashCode();
        }
    }

    private static final class LogicalRecord {

        String id;
        String source;
        int dim;
        int x;
        int y;
        int z;
        String kind = "";
        String className = "";
        String iconItemId = "";
        String displayName = "";
        WorldMapMarkerDto marker;
        WorldMapAePlacementRecord placement;

        static LogicalRecord marker(WorldMapMarkerDto marker) {
            LogicalRecord out = new LogicalRecord();
            out.id = WorldMapMarkerBuilder.markerId(marker.dim, marker.x, marker.y, marker.z);
            out.source = "marker";
            out.dim = marker.dim;
            out.x = marker.x;
            out.y = marker.y;
            out.z = marker.z;
            out.kind = value(marker.type);
            out.iconItemId = value(marker.iconItemId);
            out.displayName = value(marker.displayName);
            out.marker = marker;
            return out;
        }

        static LogicalRecord placement(WorldMapAePlacementRecord placement) {
            LogicalRecord out = new LogicalRecord();
            out.id = WorldMapMarkerBuilder.markerId(placement.dim, placement.x, placement.y, placement.z);
            out.source = "placement";
            out.dim = placement.dim;
            out.x = placement.x;
            out.y = placement.y;
            out.z = placement.z;
            out.kind = value(placement.kind);
            out.className = value(placement.className);
            out.iconItemId = value(placement.iconItemId);
            out.displayName = value(placement.displayName);
            out.placement = placement;
            return out;
        }
    }
}
