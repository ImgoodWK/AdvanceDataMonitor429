package com.imgood.textech.webae.worldmap;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/** Focused contracts for the pure world-map annotation store/service. */
public class WorldMapAnnotationServiceTest {

    private static final String OWNER = "00000000-0000-0000-0000-000000000001";
    private static final String OTHER_OWNER = "00000000-0000-0000-0000-000000000002";
    private static final String ACTOR = "00000000-0000-0000-0000-000000000010";
    private static final Charset UTF8 = Charset.forName("UTF-8");

    private Path root;
    private WorldMapAnnotationStore store;
    private WorldMapAnnotationService service;

    @Before
    public void setUp() throws IOException {
        root = Files.createTempDirectory("textech-map-annotations-");
        store = new WorldMapAnnotationStore(root.toFile());
        service = new WorldMapAnnotationService(store);
    }

    @After
    public void tearDown() throws IOException {
        deleteRecursively(root);
    }

    @Test
    public void crudPersistsAndReloadsWithServerMetadata() {
        WorldMapAnnotationResult<WorldMapAnnotationDto> created = service.create(OWNER, 7, ACTOR,
            request(" Spawn ", "note", "#aBc123", 0, 4));
        assertSuccess(created);
        WorldMapAnnotationDto annotation = created.result;
        Assert.assertEquals("Spawn", annotation.label);
        Assert.assertEquals("#ABC123", annotation.color);
        Assert.assertTrue(annotation.id != null && annotation.id.length() == 36);
        Assert.assertEquals(OWNER, annotation.ownerUuid);
        Assert.assertEquals(7, annotation.networkId);
        Assert.assertEquals(ACTOR, annotation.createdBy);
        Assert.assertTrue(annotation.createdAt > 0L);
        Assert.assertTrue(annotation.updatedAt >= annotation.createdAt);
        Assert.assertEquals(root.resolve(OWNER).resolve("7.json").toFile().getAbsolutePath(),
            store.fileFor(OWNER, 7).getAbsolutePath());
        Assert.assertTrue(store.fileFor(OWNER, 7).isFile());

        WorldMapAnnotationService restarted = new WorldMapAnnotationService(
            new WorldMapAnnotationStore(root.toFile()));
        WorldMapAnnotationResult<List<WorldMapAnnotationDto>> loaded = restarted.list(OWNER, 7, 2);
        assertSuccess(loaded);
        Assert.assertEquals(1, loaded.result.size());
        Assert.assertEquals(annotation.id, loaded.result.get(0).id);

        WorldMapAnnotationRequest update = request("Updated", "changed", "#00ff00", 2, 0);
        WorldMapAnnotationResult<WorldMapAnnotationDto> updated = restarted.update(OWNER, 7, annotation.id, ACTOR,
            update);
        assertSuccess(updated);
        Assert.assertEquals("Updated", updated.result.label);
        Assert.assertEquals("#00FF00", updated.result.color);
        Assert.assertEquals(annotation.createdAt, updated.result.createdAt);
        Assert.assertEquals(annotation.createdBy, updated.result.createdBy);
        Assert.assertTrue(updated.result.updatedAt > annotation.updatedAt);

        WorldMapAnnotationResult<WorldMapAnnotationDto> removed = restarted.delete(OWNER, 7, annotation.id);
        assertSuccess(removed);
        Assert.assertEquals(0, restarted.list(OWNER, 7, 2).result.size());
    }

    @Test
    public void versionFilteringTreatsZeroEndpointsAsUnbounded() {
        Assert.assertTrue(service.create(OWNER, 8, ACTOR, request("both", "", "#000000", 0, 0)).success);
        Assert.assertTrue(service.create(OWNER, 8, ACTOR, request("through4", "", "#000000", 0, 4)).success);
        Assert.assertTrue(service.create(OWNER, 8, ACTOR, request("from5", "", "#000000", 5, 0)).success);
        Assert.assertTrue(service.create(OWNER, 8, ACTOR, request("middle", "", "#000000", 2, 4)).success);

        Assert.assertEquals(2, service.list(OWNER, 8, 1).result.size());
        Assert.assertEquals(3, service.list(OWNER, 8, 3).result.size());
        Assert.assertEquals(2, service.list(OWNER, 8, 5).result.size());
        Assert.assertEquals("invalid_version", service.list(OWNER, 8, 0).code);
    }

    @Test
    public void enforcesPerNetworkCap() {
        List<WorldMapAnnotationDto> records = new ArrayList<WorldMapAnnotationDto>();
        for (int i = 0; i < WorldMapAnnotationStore.MAX_RECORDS; i++) {
            records.add(storedRecord(9, "a" + i));
        }
        Assert.assertTrue(store.save(OWNER, 9, records).success);
        WorldMapAnnotationResult<WorldMapAnnotationDto> overflow = service.create(OWNER, 9, ACTOR,
            request("overflow", "", "#FFFFFF", 0, 0));
        Assert.assertFalse(overflow.success);
        Assert.assertEquals("record_limit", overflow.code);
        Assert.assertEquals(WorldMapAnnotationStore.MAX_RECORDS, service.list(OWNER, 9, 1).result.size());
    }

    @Test
    public void enforcesUnicodeCodePointBoundariesAndControls() {
        String emoji64 = repeat("😀", 64);
        String emoji65 = repeat("😀", 65);
        Assert.assertTrue(service.create(OWNER, 10, ACTOR, request(emoji64, "", "#123456", 0, 0)).success);
        Assert.assertFalse(service.create(OWNER, 10, ACTOR, request(emoji65, "", "#123456", 0, 0)).success);

        String emoji512 = repeat("🧭", 512);
        String emoji513 = repeat("🧭", 513);
        Assert.assertTrue(service.create(OWNER, 11, ACTOR, request("label", emoji512, "#123456", 0, 0)).success);
        Assert.assertFalse(service.create(OWNER, 11, ACTOR, request("label", emoji513, "#123456", 0, 0)).success);
        Assert.assertFalse(service.create(OWNER, 12, ACTOR, request("bad\nlabel", "", "#123456", 0, 0)).success);
        Assert.assertFalse(service.create(OWNER, 12, ACTOR, request("bad", "bad\u0000note", "#123456", 0, 0)).success);
    }

    @Test
    public void normalizesAndValidatesColor() {
        WorldMapAnnotationResult<WorldMapAnnotationDto> result = service.create(OWNER, 13, ACTOR,
            request("label", "", "#a1B2c3", 0, 0));
        assertSuccess(result);
        Assert.assertEquals("#A1B2C3", result.result.color);
        Assert.assertFalse(service.create(OWNER, 13, ACTOR, request("x", "", "a1b2c3", 0, 0)).success);
        Assert.assertFalse(service.create(OWNER, 13, ACTOR, request("x", "", "#12345", 0, 0)).success);
        Assert.assertFalse(service.create(OWNER, 13, ACTOR, request("x", "", "#1234567", 0, 0)).success);
    }

    @Test
    public void rejectsCoordinatesDimensionsAndRangesOutsideWorldMapBounds() {
        WorldMapAnnotationRequest badDimension = requestAt("x", 0, 0, 64, 0, 0);
        badDimension.dimension = WorldMapPacketAuthorization.MAX_DIMENSION + 1;
        Assert.assertFalse(service.create(OWNER, 14, ACTOR, badDimension).success);
        Assert.assertFalse(service.create(OWNER, 14, ACTOR,
            requestAt("x", WorldMapPacketAuthorization.MAX_CHUNK_COORDINATE * 16 + 16, 0, 64, 0, 0)).success);
        Assert.assertFalse(service.create(OWNER, 14, ACTOR, requestAt("x", 0, 0, -1, 0, 0)).success);
        Assert.assertFalse(service.create(OWNER, 14, ACTOR,
            requestAt("x", 0, 0, WorldMapAnnotationService.MAX_Y + 1, 0, 0)).success);
        Assert.assertFalse(service.create(OWNER, 14, ACTOR, requestAt("x", 0, 0, 64, 5, 4)).success);
        Assert.assertFalse(service.create(OWNER, 14, ACTOR,
            requestAt("x", 0, 0, 64, WorldMapPacketAuthorization.MAX_SNAPSHOT_VERSION + 1, 0)).success);
        Assert.assertFalse(service.create(OWNER, 14, ACTOR, requestAt("x", 0, 0, 64, -1, 0)).success);
        Assert.assertTrue(service.create(OWNER, 14, ACTOR,
            requestAt("edge", WorldMapPacketAuthorization.MAX_CHUNK_COORDINATE * 16, 0, 64, 0, 0)).success);
    }

    @Test
    public void isolatesOwnerAndNetworkAndRejectsImmutableMutation() {
        WorldMapAnnotationDto created = service.create(OWNER, 15, ACTOR, request("original", "", "#000000", 0, 0))
            .result;
        Assert.assertFalse(service.list(OTHER_OWNER, 15, 1).result.contains(created));
        Assert.assertFalse(service.update(OTHER_OWNER, 15, created.id, ACTOR,
            request("x", "", "#FFFFFF", 0, 0)).success);
        Assert.assertFalse(service.delete(OWNER, 16, created.id).success);

        WorldMapAnnotationRequest crossOwner = request("x", "", "#FFFFFF", 0, 0);
        crossOwner.ownerUuid = OTHER_OWNER;
        Assert.assertFalse(service.update(OWNER, 15, created.id, ACTOR, crossOwner).success);
        WorldMapAnnotationRequest crossNetwork = request("x", "", "#FFFFFF", 0, 0);
        crossNetwork.networkId = Integer.valueOf(99);
        Assert.assertFalse(service.update(OWNER, 15, created.id, ACTOR, crossNetwork).success);
        WorldMapAnnotationRequest changedId = request("x", "", "#FFFFFF", 0, 0);
        changedId.id = "00000000-0000-0000-0000-000000000099";
        Assert.assertFalse(service.update(OWNER, 15, created.id, ACTOR, changedId).success);
        WorldMapAnnotationRequest changedCreated = request("x", "", "#FFFFFF", 0, 0);
        changedCreated.createdAt = Long.valueOf(created.createdAt + 1L);
        Assert.assertFalse(service.update(OWNER, 15, created.id, ACTOR, changedCreated).success);
        WorldMapAnnotationRequest changedCreator = request("x", "", "#FFFFFF", 0, 0);
        changedCreator.createdBy = OTHER_OWNER;
        Assert.assertFalse(service.update(OWNER, 15, created.id, ACTOR, changedCreator).success);
        WorldMapAnnotationRequest spoofedCreate = request("spoofed", "", "#FFFFFF", 0, 0);
        spoofedCreate.id = "00000000-0000-0000-0000-000000000099";
        Assert.assertEquals("server_field", service.create(OWNER, 15, ACTOR, spoofedCreate).code);
    }

    @Test
    public void rejectsCorruptOversizeAndSymlinkFiles() throws IOException {
        Path file = store.fileFor(OWNER, 17).toPath();
        Files.createDirectories(file.getParent());
        Files.write(file, "not-json".getBytes(UTF8), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        Assert.assertEquals("corrupt_file", service.list(OWNER, 17, 1).code);

        byte[] oversize = new byte[WorldMapAnnotationStore.MAX_FILE_BYTES + 1];
        Files.write(file, oversize, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        Assert.assertEquals("oversize_file", service.list(OWNER, 17, 1).code);

        Path outside = Files.createTempFile("outside-map-annotations-", ".json");
        Path ownerDirectory = file.getParent();
        Files.deleteIfExists(file);
        Files.deleteIfExists(ownerDirectory);
        try {
            Files.createSymbolicLink(ownerDirectory, outside);
            Assert.assertEquals("unsafe_path", service.list(OWNER, 17, 1).code);
        } catch (UnsupportedOperationException e) {
            // Windows without developer-mode symlink privileges.
        } catch (IOException e) {
            // Symlink creation is an optional platform capability for this test.
        } finally {
            Files.deleteIfExists(ownerDirectory);
            Files.deleteIfExists(outside);
        }
    }

    @Test
    public void failedAtomicWriteLeavesPreviousFileIntact() {
        WorldMapAnnotationDto created = service.create(OWNER, 18, ACTOR, request("before", "", "#000000", 0, 0))
            .result;
        WorldMapAnnotationStore failingStore = new WorldMapAnnotationStore(root.toFile(),
            new WorldMapAnnotationStore.AtomicWriteStrategy() {
                @Override
                public void write(java.io.File target, byte[] bytes) throws IOException {
                    throw new IOException("injected failure");
                }
            });
        WorldMapAnnotationService failingService = new WorldMapAnnotationService(failingStore);
        WorldMapAnnotationResult<WorldMapAnnotationDto> failed = failingService.update(OWNER, 18, created.id, ACTOR,
            request("after", "", "#FFFFFF", 0, 0));
        Assert.assertFalse(failed.success);
        Assert.assertEquals("write_failed", failed.code);
        WorldMapAnnotationDto persisted = service.list(OWNER, 18, 1).result.get(0);
        Assert.assertEquals("before", persisted.label);
        Assert.assertEquals("#000000", persisted.color);
    }

    @Test
    public void idsAreUniqueAndListResultIsDetached() {
        Set<String> ids = new HashSet<String>();
        for (int i = 0; i < 3; i++) {
            WorldMapAnnotationDto created = service.create(OWNER, 19, ACTOR,
                request("label" + i, "", "#FFFFFF", 0, 0)).result;
            Assert.assertTrue(ids.add(created.id));
        }
        List<WorldMapAnnotationDto> listed = service.list(OWNER, 19, 1).result;
        listed.get(0).label = "caller mutation";
        Assert.assertFalse("caller mutation must not reach disk", "caller mutation".equals(
            service.list(OWNER, 19, 1).result.get(0).label));
    }

    @Test
    public void concurrentServiceInstancesDoNotLoseUpdates() throws Exception {
        final WorldMapAnnotationService first = new WorldMapAnnotationService(
            new WorldMapAnnotationStore(root.toFile()));
        final WorldMapAnnotationService second = new WorldMapAnnotationService(
            new WorldMapAnnotationStore(root.toFile()));
        final CountDownLatch start = new CountDownLatch(1);
        final List<String> failures = Collections.synchronizedList(new ArrayList<String>());
        Thread left = creatingThread(first, "left", start, failures);
        Thread right = creatingThread(second, "right", start, failures);
        left.start();
        right.start();
        start.countDown();
        left.join(30_000L);
        right.join(30_000L);
        Assert.assertFalse("worker did not finish", left.isAlive() || right.isAlive());
        Assert.assertTrue(failures.toString(), failures.isEmpty());
        Assert.assertEquals(20, service.list(OWNER, 20, 1).result.size());
    }

    private static WorldMapAnnotationRequest request(String label, String note, String color, int from, int to) {
        return requestAt(label, 0, 0, 64, from, to, note, color);
    }

    private static WorldMapAnnotationRequest requestAt(String label, int x, int z, int y, int from, int to) {
        return requestAt(label, x, z, y, from, to, "", "#FFFFFF");
    }

    private static WorldMapAnnotationRequest requestAt(String label, int x, int z, int y, int from, int to,
        String note, String color) {
        WorldMapAnnotationRequest request = new WorldMapAnnotationRequest();
        request.dimension = 0;
        request.x = x;
        request.y = y;
        request.z = z;
        request.label = label;
        request.note = note;
        request.color = color;
        request.fromVersion = from;
        request.toVersion = to;
        return request;
    }

    private static String repeat(String value, int count) {
        StringBuilder result = new StringBuilder(value.length() * count);
        for (int i = 0; i < count; i++) {
            result.append(value);
        }
        return result.toString();
    }

    private static WorldMapAnnotationDto storedRecord(int networkId, String label) {
        WorldMapAnnotationDto record = new WorldMapAnnotationDto();
        record.id = UUID.randomUUID().toString();
        record.ownerUuid = OWNER;
        record.networkId = networkId;
        record.dimension = 0;
        record.x = 0;
        record.y = 64;
        record.z = 0;
        record.label = label;
        record.note = "";
        record.color = "#FFFFFF";
        record.fromVersion = 0;
        record.toVersion = 0;
        record.createdAt = 1L;
        record.updatedAt = 1L;
        record.createdBy = ACTOR;
        return record;
    }

    private static Thread creatingThread(final WorldMapAnnotationService target, final String prefix,
        final CountDownLatch start, final List<String> failures) {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    start.await();
                    for (int i = 0; i < 10; i++) {
                        WorldMapAnnotationResult<WorldMapAnnotationDto> result = target.create(
                            OWNER,
                            20,
                            ACTOR,
                            request(prefix + i, "", "#FFFFFF", 0, 0));
                        if (!result.success) {
                            failures.add(result.code);
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    failures.add("interrupted");
                }
            }
        }, "annotation-test-" + prefix);
        thread.setDaemon(true);
        return thread;
    }

    private static void assertSuccess(WorldMapAnnotationResult<?> result) {
        Assert.assertTrue(result.code + ": " + result.message, result.success);
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (path == null || !Files.exists(path)) {
            return;
        }
        if (Files.isDirectory(path) && !Files.isSymbolicLink(path)) {
            java.nio.file.DirectoryStream<Path> children = Files.newDirectoryStream(path);
            try {
                for (Path child : children) {
                    deleteRecursively(child);
                }
            } finally {
                children.close();
            }
        }
        Files.deleteIfExists(path);
    }
}
