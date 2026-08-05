package com.imgood.textech.webae.worldmap;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class WorldMapSnapshotDiffServiceTest {

    private static final String OWNER = "10000000-0000-0000-0000-000000000004";
    private static final int NETWORK = 999_904;
    private static final Charset UTF8 = Charset.forName("UTF-8");

    @Before
    @After
    public void clearFixture() {
        delete(WorldMapSnapshotStore.networkDir(OWNER, NETWORK));
    }

    @Test
    public void defaultsToPreviousCurrentAndReportsTileStatuses() {
        WorldMapSnapshotManifest one = manifest(1, 100L);
        tile(one, "terrain:0:0:0", repeat('a'), 10);
        tile(one, "terrain:0:1:0", repeat('b'), 11);
        tile(one, "terrain:0:2:0", repeat('c'), 12);
        finalizeVersion(one, null);

        WorldMapSnapshotManifest two = manifest(2, 200L);
        tile(two, "terrain:0:0:0", repeat('a'), 999);
        tile(two, "terrain:0:1:0", repeat('d'), 11);
        tile(two, "terrain:0:3:0", repeat('e'), 13);
        finalizeVersion(two, null);

        WorldMapSnapshotDiffDto diff = WorldMapSnapshotDiffService.diff(OWNER, NETWORK,
            new WorldMapSnapshotDiffOptions());
        Assert.assertTrue(diff.success);
        Assert.assertEquals(1, diff.fromVersion);
        Assert.assertEquals(2, diff.toVersion);
        Assert.assertEquals(1, diff.summary.tilesAdded);
        Assert.assertEquals(1, diff.summary.tilesRemoved);
        Assert.assertEquals(1, diff.summary.tilesChanged);
        Assert.assertEquals(1, diff.summary.tilesUnchanged);
        Assert.assertEquals(4, diff.tileChanges.size());
        Assert.assertEquals("unchanged", diff.tileChanges.get(0).status);
        Assert.assertEquals(10L, diff.tileChanges.get(0).fromSize);
        Assert.assertEquals(999L, diff.tileChanges.get(0).toSize);

        WorldMapSnapshotVersionsDto versions = WorldMapSnapshotDiffService.listVersions(OWNER, NETWORK);
        Assert.assertTrue(versions.success);
        Assert.assertEquals(2, versions.currentVersion);
        Assert.assertEquals(1, versions.previousVersion);
        Assert.assertEquals(2, versions.versions.size());
    }

    @Test
    public void exposesExplicitSelectionCodes() {
        finalizeVersion(manifest(1, 100L), null);
        Assert.assertEquals("no_previous", WorldMapSnapshotDiffService.diff(OWNER, NETWORK,
            new WorldMapSnapshotDiffOptions()).code);
        WorldMapSnapshotDiffDto same = WorldMapSnapshotDiffService.diff(OWNER, NETWORK, 1, 1,
            new WorldMapSnapshotDiffOptions());
        Assert.assertFalse(same.success);
        Assert.assertEquals("error", same.status);
        Assert.assertEquals("same", same.code);
        Assert.assertEquals("not_retained", WorldMapSnapshotDiffService.diff(OWNER, NETWORK, 2, 2,
            new WorldMapSnapshotDiffOptions()).code);
        Assert.assertEquals("invalid", WorldMapSnapshotDiffService.diff(OWNER, NETWORK, 0, 1,
            new WorldMapSnapshotDiffOptions()).code);
        Assert.assertEquals("invalid", WorldMapSnapshotDiffService.diff(
            OWNER,
            NETWORK,
            null,
            Integer.valueOf(0),
            new WorldMapSnapshotDiffOptions()).code);
        Assert.assertEquals("not_retained", WorldMapSnapshotDiffService.diff(OWNER, NETWORK, 2, 1,
            new WorldMapSnapshotDiffOptions()).code);

        clearFixture();
        Assert.assertEquals("no_versions", WorldMapSnapshotDiffService.diff(OWNER, NETWORK,
            new WorldMapSnapshotDiffOptions()).code);
    }

    @Test
    public void comparesLogicalPlacementsAndLeavesAmbiguousMovesAsRemoveAdd() {
        WorldMapLogicalIndex oneIndex = logical(1, 100L);
        oneIndex.aePlacements.add(placement(0, 64, 0, "block", "Device", "ae:item", "old"));
        oneIndex.aePlacements.add(placement(32, 64, 0, "cable", "Cable", "ae:cable", "a"));
        oneIndex.aePlacements.add(placement(48, 64, 0, "cable", "Cable", "ae:cable", "b"));
        finalizeVersion(manifest(1, 100L), oneIndex);

        WorldMapLogicalIndex twoIndex = logical(2, 200L);
        twoIndex.aePlacements.add(placement(16, 64, 0, "block", "Device", "ae:item", "old"));
        twoIndex.aePlacements.add(placement(64, 64, 0, "cable", "Cable", "ae:cable", "c"));
        finalizeVersion(manifest(2, 200L), twoIndex);

        WorldMapSnapshotDiffDto diff = WorldMapSnapshotDiffService.diff(OWNER, NETWORK,
            new WorldMapSnapshotDiffOptions());
        Assert.assertTrue(diff.logicalAvailable);
        Assert.assertEquals(1, diff.summary.markersMoved);
        Assert.assertEquals(2, diff.summary.markersRemoved);
        Assert.assertEquals(1, diff.summary.markersAdded);
    }

    @Test
    public void prefersOneDeterministicPlacementPerCoordinateOverMarkerFallback() {
        WorldMapLogicalIndex oneIndex = logical(1, 100L);
        WorldMapMarkerDto beforeMarker = marker(0, 0, 64, 0);
        beforeMarker.displayName = "before";
        oneIndex.markers.add(beforeMarker);
        oneIndex.aePlacements.add(placement(0, 64, 0, "block", "Device", "ae:item", "device"));
        oneIndex.aePlacements.add(placement(0, 64, 0, "part", "Part", "ae:part", "part"));
        finalizeVersion(manifest(1, 100L), oneIndex);

        WorldMapLogicalIndex twoIndex = logical(2, 200L);
        WorldMapMarkerDto afterMarker = marker(0, 0, 64, 0);
        afterMarker.displayName = "after";
        twoIndex.markers.add(afterMarker);
        twoIndex.aePlacements.add(placement(0, 64, 0, "block", "Device", "ae:item", "device"));
        twoIndex.aePlacements.add(placement(0, 64, 0, "part", "Part", "ae:part", "part"));
        finalizeVersion(manifest(2, 200L), twoIndex);

        WorldMapSnapshotDiffDto diff = WorldMapSnapshotDiffService.diff(OWNER, NETWORK,
            new WorldMapSnapshotDiffOptions());
        Assert.assertTrue(diff.logicalAvailable);
        Assert.assertEquals(0, diff.summary.markersChanged);
        Assert.assertEquals(0, diff.summary.markersMoved);
        Assert.assertEquals(0, diff.summary.markersRemoved);
        Assert.assertEquals(0, diff.summary.markersAdded);
        Assert.assertTrue(diff.markerChanges.isEmpty());
    }

    @Test
    public void reportsMarkerOnlyChangesAtTheSameCoordinate() {
        WorldMapLogicalIndex oneIndex = logical(1, 100L);
        WorldMapMarkerDto before = marker(0, 0, 64, 0);
        before.displayName = "before";
        oneIndex.markers.add(before);
        finalizeVersion(manifest(1, 100L), oneIndex);

        WorldMapLogicalIndex twoIndex = logical(2, 200L);
        WorldMapMarkerDto after = marker(0, 0, 64, 0);
        after.displayName = "after";
        twoIndex.markers.add(after);
        finalizeVersion(manifest(2, 200L), twoIndex);

        WorldMapSnapshotDiffDto diff = WorldMapSnapshotDiffService.diff(OWNER, NETWORK,
            new WorldMapSnapshotDiffOptions());
        Assert.assertEquals(1, diff.summary.markersChanged);
        Assert.assertEquals(1, diff.markerChanges.size());
        Assert.assertEquals("changed", diff.markerChanges.get(0).status);
        Assert.assertEquals("marker", diff.markerChanges.get(0).source);
    }

    @Test
    public void markerOnlyRecordsWithoutClassNameAreNeverGuessedAsMoved() {
        WorldMapLogicalIndex oneIndex = logical(1, 100L);
        oneIndex.markers.add(marker(0, 0, 64, 0));
        finalizeVersion(manifest(1, 100L), oneIndex);

        WorldMapLogicalIndex twoIndex = logical(2, 200L);
        twoIndex.markers.add(marker(0, 16, 64, 0));
        finalizeVersion(manifest(2, 200L), twoIndex);

        WorldMapSnapshotDiffDto diff = WorldMapSnapshotDiffService.diff(OWNER, NETWORK,
            new WorldMapSnapshotDiffOptions());
        Assert.assertEquals(0, diff.summary.markersMoved);
        Assert.assertEquals(1, diff.summary.markersRemoved);
        Assert.assertEquals(1, diff.summary.markersAdded);
    }

    @Test
    public void missingSidecarNeverFallsBackAndMissingManifestIsUnknown() throws Exception {
        finalizeVersion(manifest(1, 100L), logical(1, 100L));
        finalizeVersion(manifest(2, 200L), null);
        WorldMapSnapshotDiffDto withoutLogical = WorldMapSnapshotDiffService.diff(OWNER, NETWORK,
            new WorldMapSnapshotDiffOptions());
        Assert.assertTrue(withoutLogical.success);
        Assert.assertFalse(withoutLogical.logicalAvailable);
        Assert.assertTrue(withoutLogical.markerChanges.isEmpty());

        Assert.assertTrue(WorldMapSnapshotStore.manifestFile(OWNER, NETWORK, 1).delete());
        WorldMapSnapshotDiffDto unknown = WorldMapSnapshotDiffService.diff(OWNER, NETWORK,
            new WorldMapSnapshotDiffOptions());
        Assert.assertFalse(unknown.success);
        Assert.assertEquals("unknown", unknown.status);
        Assert.assertEquals("unknown_manifest", unknown.code);

        writeUtf8(WorldMapSnapshotStore.manifestFile(OWNER, NETWORK, 1), "{not-json");
        WorldMapSnapshotDiffDto malformed = WorldMapSnapshotDiffService.diff(OWNER, NETWORK,
            new WorldMapSnapshotDiffOptions());
        Assert.assertFalse(malformed.success);
        Assert.assertEquals("unknown", malformed.status);
        Assert.assertEquals("unknown_manifest", malformed.code);
    }

    @Test
    public void pendingRetainedManifestIsUnknownInsteadOfBeingGuessed() {
        WorldMapSnapshotManifest one = manifest(1, 100L);
        finalizeVersion(one, logical(1, 100L));
        finalizeVersion(manifest(2, 200L), logical(2, 200L));

        one.source = "pending";
        Assert.assertTrue(WorldMapSnapshotStore.saveManifest(one));
        WorldMapSnapshotDiffDto unknown = WorldMapSnapshotDiffService.diff(OWNER, NETWORK,
            new WorldMapSnapshotDiffOptions());
        Assert.assertFalse(unknown.success);
        Assert.assertEquals("unknown", unknown.status);
        Assert.assertEquals("unknown_manifest", unknown.code);

        WorldMapSnapshotVersionsDto versions = WorldMapSnapshotDiffService.listVersions(OWNER, NETWORK);
        Assert.assertTrue(versions.success);
        Assert.assertFalse(versions.versions.get(1).manifestAvailable);
    }

    @Test
    public void appliesDimensionAndBlockIntersectionFilters() {
        WorldMapSnapshotManifest one = manifest(1, 100L);
        tile(one, "terrain:0:0:0", repeat('a'), 1);
        tile(one, "terrain:0:1:0", repeat('a'), 1);
        tile(one, "terrain:1:0:0", repeat('a'), 1);
        finalizeVersion(one, null);
        WorldMapSnapshotManifest two = manifest(2, 200L);
        tile(two, "terrain:0:0:0", repeat('b'), 1);
        tile(two, "terrain:0:1:0", repeat('b'), 1);
        tile(two, "terrain:1:0:0", repeat('b'), 1);
        finalizeVersion(two, null);

        WorldMapSnapshotDiffOptions filter = new WorldMapSnapshotDiffOptions();
        filter.dimension = Integer.valueOf(0);
        filter.minX = Integer.valueOf(15);
        filter.maxX = Integer.valueOf(16);
        filter.minZ = Integer.valueOf(0);
        filter.maxZ = Integer.valueOf(0);
        WorldMapSnapshotDiffDto diff = WorldMapSnapshotDiffService.diff(OWNER, NETWORK, filter);
        Assert.assertEquals(2, diff.summary.tilesChanged);
    }

    @Test
    public void summaryPrecedesCombinedDetailTruncation() {
        WorldMapSnapshotManifest one = manifest(1, 100L);
        WorldMapSnapshotManifest two = manifest(2, 200L);
        for (int i = 0; i < WorldMapSnapshotDiffService.MAX_DETAIL + 1; i++) {
            tile(two, "terrain:0:" + i + ":0", repeat('a'), 1);
        }
        finalizeVersion(one, null);
        finalizeVersion(two, null);
        WorldMapSnapshotDiffDto diff = WorldMapSnapshotDiffService.diff(OWNER, NETWORK,
            new WorldMapSnapshotDiffOptions());
        Assert.assertEquals(WorldMapSnapshotDiffService.MAX_DETAIL + 1, diff.summary.tilesAdded);
        Assert.assertEquals(WorldMapSnapshotDiffService.MAX_DETAIL, diff.tileChanges.size());
        Assert.assertTrue(diff.truncated);
    }

    @Test
    public void rejectsInvalidLogicalIndexesAndLoadsMissingAsUnavailable() throws Exception {
        WorldMapLogicalIndex invalid = logical(1, 100L);
        WorldMapAePlacementRecord placement = placement(0, 64, 0, "block", "Device", "ae:item", "ok");
        placement.x = Integer.MAX_VALUE;
        invalid.aePlacements.add(placement);
        Assert.assertFalse(WorldMapSnapshotStore.saveLogicalIndex(OWNER, NETWORK, 1, invalid));

        WorldMapLogicalIndex tooLong = logical(1, 100L);
        WorldMapMarkerDto marker = marker(0, 0, 64, 0);
        char[] chars = new char[WorldMapLogicalIndex.MAX_STRING_LENGTH + 1];
        Arrays.fill(chars, 'x');
        marker.displayName = new String(chars);
        tooLong.markers.add(marker);
        Assert.assertFalse(WorldMapSnapshotStore.saveLogicalIndex(OWNER, NETWORK, 1, tooLong));

        WorldMapLogicalIndex missing = WorldMapSnapshotStore.loadLogicalIndex(OWNER, NETWORK, 1);
        Assert.assertFalse(missing.logicalAvailable);
        Assert.assertTrue(missing.markers.isEmpty());

        File version = WorldMapSnapshotStore.versionDir(OWNER, NETWORK, 1);
        Assert.assertTrue(version.mkdirs() || version.isDirectory());
        writeUtf8(WorldMapSnapshotStore.logicalIndexFile(OWNER, NETWORK, 1), "{not-json");
        Assert.assertFalse(WorldMapSnapshotStore.loadLogicalIndex(OWNER, NETWORK, 1).logicalAvailable);
    }

    private static void writeUtf8(File file, String value) throws Exception {
        FileOutputStream output = new FileOutputStream(file);
        try {
            output.write(value.getBytes(UTF8));
        } finally {
            output.close();
        }
    }

    private static WorldMapSnapshotManifest manifest(int version, long timestamp) {
        WorldMapSnapshotManifest manifest = new WorldMapSnapshotManifest();
        manifest.version = version;
        manifest.timestamp = timestamp;
        manifest.ownerUuid = OWNER;
        manifest.networkId = NETWORK;
        manifest.source = "client_gl";
        manifest.tilePx = 128;
        manifest.sourceStats = new HashMap<String, Integer>();
        manifest.layers = new ArrayList<String>();
        manifest.layers.add(WorldMapTileLayer.TERRAIN);
        manifest.dimensions = new ArrayList<WorldMapSnapshotManifest.DimensionEntry>();
        manifest.tiles = new HashMap<String, WorldMapSnapshotManifest.TileEntry>();
        manifest.missingChunks = new ArrayList<String>();
        return manifest;
    }

    private static void tile(WorldMapSnapshotManifest manifest, String key, String sha, long size) {
        WorldMapSnapshotManifest.TileEntry entry = new WorldMapSnapshotManifest.TileEntry();
        entry.sha256 = sha;
        entry.size = size;
        manifest.tiles.put(key, entry);
    }

    private static String repeat(char value) {
        char[] chars = new char[64];
        Arrays.fill(chars, value);
        return new String(chars);
    }

    private static WorldMapLogicalIndex logical(int version, long timestamp) {
        WorldMapLogicalIndex index = new WorldMapLogicalIndex();
        index.version = version;
        index.timestamp = timestamp;
        index.logicalAvailable = true;
        return index;
    }

    private static WorldMapAePlacementRecord placement(int x, int y, int z, String kind, String className,
        String icon, String displayName) {
        WorldMapAePlacementRecord record = new WorldMapAePlacementRecord();
        record.x = x;
        record.y = y;
        record.z = z;
        record.dim = 0;
        record.kind = kind;
        record.className = className;
        record.iconItemId = icon;
        record.displayName = displayName;
        return record;
    }

    private static WorldMapMarkerDto marker(int dim, int x, int y, int z) {
        WorldMapMarkerDto marker = new WorldMapMarkerDto();
        marker.dim = dim;
        marker.x = x;
        marker.y = y;
        marker.z = z;
        marker.id = WorldMapMarkerBuilder.markerId(dim, x, y, z);
        marker.nodeId = "node";
        marker.type = "device";
        marker.subtype = "";
        marker.displayName = "device";
        marker.iconItemId = "ae:item";
        return marker;
    }

    private static void finalizeVersion(WorldMapSnapshotManifest manifest, WorldMapLogicalIndex logical) {
        Assert.assertTrue(WorldMapSnapshotStore.saveManifest(manifest));
        if (logical != null) {
            Assert.assertTrue(WorldMapSnapshotStore.saveLogicalIndex(OWNER, NETWORK, manifest.version, logical));
        }
        Assert.assertTrue(WorldMapSnapshotStore.finalizeSnapshot(manifest));
    }

    private static void delete(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                delete(child);
            }
        }
        file.delete();
    }
}
