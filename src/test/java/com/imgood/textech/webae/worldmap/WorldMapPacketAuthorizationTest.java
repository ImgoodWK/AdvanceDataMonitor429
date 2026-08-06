package com.imgood.textech.webae.worldmap;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class WorldMapPacketAuthorizationTest {

    private static final String OWNER = "123e4567-e89b-12d3-a456-426614174000";

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void ownerDirectoryKeyRequiresCanonicalUuid() {
        Assert.assertTrue(WorldMapPacketAuthorization.isValidOwnerUuid(OWNER));
        Assert.assertEquals(OWNER, WorldMapPacketAuthorization.canonicalOwnerUuid(OWNER));
        Assert.assertFalse(WorldMapPacketAuthorization.isValidOwnerUuid(OWNER.toUpperCase()));
        Assert.assertNull(WorldMapPacketAuthorization.canonicalOwnerUuid("../../outside"));
    }

    @Test
    public void networkSnapshotAndTileBoundsAreClosed() {
        Assert.assertTrue(WorldMapPacketAuthorization.isValidNetworkId(0));
        Assert.assertTrue(WorldMapPacketAuthorization.isValidNetworkId(WorldMapPacketAuthorization.MAX_NETWORK_ID));
        Assert.assertFalse(WorldMapPacketAuthorization.isValidNetworkId(-1));
        Assert
            .assertFalse(WorldMapPacketAuthorization.isValidNetworkId(WorldMapPacketAuthorization.MAX_NETWORK_ID + 1));

        Assert.assertTrue(WorldMapPacketAuthorization.isValidSnapshotVersion(1));
        Assert.assertFalse(WorldMapPacketAuthorization.isValidSnapshotVersion(0));
        Assert.assertTrue(WorldMapPacketAuthorization.isValidTilePx(WorldMapPacketAuthorization.MAX_TILE_PX));
        Assert.assertFalse(WorldMapPacketAuthorization.isValidTilePx(WorldMapPacketAuthorization.MAX_TILE_PX + 1));
        Assert.assertTrue(
            WorldMapPacketAuthorization.isValidChunk(
                WorldMapPacketAuthorization.MAX_DIMENSION,
                WorldMapPacketAuthorization.MAX_CHUNK_COORDINATE,
                -WorldMapPacketAuthorization.MAX_CHUNK_COORDINATE));
        Assert
            .assertFalse(WorldMapPacketAuthorization.isValidChunk(WorldMapPacketAuthorization.MAX_DIMENSION + 1, 0, 0));
    }

    @Test
    public void layerAndSourceAreAllowLists() {
        Assert.assertTrue(WorldMapPacketAuthorization.isValidLayer("terrain"));
        Assert.assertTrue(WorldMapPacketAuthorization.isValidLayer("AE"));
        Assert.assertFalse(WorldMapPacketAuthorization.isValidLayer("terrain/../outside"));
        Assert.assertTrue(WorldMapPacketAuthorization.isValidSource("client_gl"));
        Assert.assertTrue(WorldMapPacketAuthorization.isValidManifestSource("pending"));
        Assert.assertFalse(WorldMapPacketAuthorization.isValidSource("pending"));
        Assert.assertFalse(WorldMapPacketAuthorization.isValidManifestSource("file:/outside"));
    }

    @Test
    public void snapshotPathsRejectInvalidComponents() {
        Assert.assertNotNull(WorldMapSnapshotStore.networkDir(OWNER, 7));
        Assert.assertNull(WorldMapSnapshotStore.networkDir("../../outside", 7));
        Assert.assertNull(WorldMapSnapshotStore.networkDir(OWNER, -1));
        Assert.assertNotNull(WorldMapSnapshotStore.tileFile(OWNER, 7, 1, "terrain", 0, 0, 0));
        Assert.assertNull(WorldMapSnapshotStore.tileFile(OWNER, 7, 1, "../../outside", 0, 0, 0));
        Assert.assertNull(WorldMapSnapshotStore.tileFile(OWNER, 7, 1, "terrain", 0, 0, Integer.MAX_VALUE));
    }

    @Test
    public void snapshotRootRejectsLexicalEscapesAndIntermediateSymlinks() throws IOException {
        File root = temporaryFolder.newFolder("snapshots");
        File normal = new File(root, "owner/network/v1/manifest.json");
        Assert.assertTrue(WorldMapSnapshotStore.isWithinSnapshotsRoot(root, normal));
        Assert
            .assertFalse(WorldMapSnapshotStore.isWithinSnapshotsRoot(root, new File(root, "../outside/current.json")));

        File target = new File(root, "other-owner");
        Assert.assertTrue(target.mkdir());
        Path link = new File(root, "linked-owner").toPath();
        try {
            Files.createSymbolicLink(link, target.toPath());
        } catch (UnsupportedOperationException e) {
            Assume.assumeNoException(e);
        } catch (SecurityException e) {
            Assume.assumeNoException(e);
        } catch (IOException e) {
            Assume.assumeNoException(e);
        }
        Assert.assertFalse(
            WorldMapSnapshotStore.isWithinSnapshotsRoot(root, new File(link.toFile(), "network/current.json")));
    }
}
