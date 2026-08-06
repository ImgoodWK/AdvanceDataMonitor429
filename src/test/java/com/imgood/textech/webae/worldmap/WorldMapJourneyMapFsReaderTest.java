package com.imgood.textech.webae.worldmap;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;

import javax.imageio.ImageIO;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class WorldMapJourneyMapFsReaderTest {

    @Rule
    public final TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void requiresExactContainedWorldNameUnderCustomRoot() throws Exception {
        File root = temp.newFolder("journeymap-data");
        File exact = new File(root, "sp/exact-world");
        Assert.assertTrue(exact.mkdirs());
        File other = new File(root, "sp/other-world");
        Assert.assertTrue(other.mkdirs());

        Assert.assertEquals(
            exact.getCanonicalFile(),
            WorldMapJourneyMapFsReader.resolveWorldRoot(root, false, "exact-world"));
        Assert.assertNull(WorldMapJourneyMapFsReader.resolveWorldRoot(root, false, "missing-world"));
        Assert.assertNull(WorldMapJourneyMapFsReader.resolveWorldRoot(root, false, "../other-world"));
        Assert.assertNull(WorldMapJourneyMapFsReader.resolveWorldRoot(root, false, "sub/world"));
        Assert.assertNull(WorldMapJourneyMapFsReader.resolveWorldRoot(root, false, ""));
    }

    @Test
    public void rejectsSymlinkedWorldDirectoryAndTile() throws Exception {
        File root = temp.newFolder("symlink-root");
        File mode = new File(root, "sp");
        Assert.assertTrue(mode.mkdirs());
        File outsideWorld = temp.newFolder("outside-world");
        File worldLink = new File(mode, "linked-world");
        try {
            Files.createSymbolicLink(worldLink.toPath(), outsideWorld.toPath());
        } catch (Exception e) {
            Assume.assumeNoException("Symbolic links are unavailable on this host", e);
        }
        Assert.assertNull(WorldMapJourneyMapFsReader.resolveWorldRoot(root, false, "linked-world"));

        File world = new File(mode, "exact-world");
        File day = new File(world, "DIM0/day");
        Assert.assertTrue(day.mkdirs());
        File outsideTile = new File(temp.newFolder("outside-tile"), "tile.png");
        writePng(outsideTile, 512, 512);
        Files.createSymbolicLink(new File(day, "0_0.png").toPath(), outsideTile.toPath());

        Assert.assertNull(
            WorldMapJourneyMapFsReader.instance()
                .readChunkTerrain(root, false, "exact-world", 0, 0, 0, 128));
    }

    @Test
    public void validatesFileSizeAndIhdrDimensionsBeforeDecode() throws Exception {
        File root = temp.newFolder("bounded-root");
        File day = new File(root, "sp/exact-world/DIM0/day");
        Assert.assertTrue(day.mkdirs());
        File tile = new File(day, "0_0.png");

        writePng(tile, 512, 512);
        byte[] valid = WorldMapJourneyMapFsReader.instance()
            .readChunkTerrain(root, false, "exact-world", 0, 0, 0, 128);
        Assert.assertNotNull(valid);
        Assert.assertTrue(WorldMapRenderSupport.isValidTilePng(valid));

        writePng(tile, WorldMapPacketAuthorization.MAX_TILE_PX + 1, 64);
        Assert.assertNull(
            WorldMapJourneyMapFsReader.instance()
                .readChunkTerrain(root, false, "exact-world", 0, 0, 0, 128));

        writePng(tile, 64, 64);
        FileOutputStream output = new FileOutputStream(tile, true);
        try {
            byte[] padding = new byte[8192];
            while (tile.length() <= WorldMapRenderSupport.MAX_VALID_TILE_BYTES) {
                output.write(padding);
            }
        } finally {
            output.close();
        }
        Assert.assertTrue(tile.length() > WorldMapRenderSupport.MAX_VALID_TILE_BYTES);
        Assert.assertNull(
            WorldMapJourneyMapFsReader.instance()
                .readChunkTerrain(root, false, "exact-world", 0, 0, 0, 128));
    }

    @Test
    public void rejectsSymlinkedCustomRoot() throws Exception {
        File realRoot = temp.newFolder("real-root");
        File link = new File(temp.getRoot(), "root-link");
        try {
            Files.createSymbolicLink(link.toPath(), realRoot.toPath());
        } catch (Exception e) {
            Assume.assumeNoException("Symbolic links are unavailable on this host", e);
        }
        Assert.assertNull(WorldMapJourneyMapFsReader.resolveWorldRoot(link, false, "world"));
    }

    private static void writePng(File file, int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int red = (x * 17 + y * 3) & 0xff;
                int green = (x * 5 + y * 19) & 0xff;
                int blue = (x * 13 + y * 7) & 0xff;
                image.setRGB(x, y, 0xff000000 | red << 16 | green << 8 | blue);
            }
        }
        Assert.assertTrue(ImageIO.write(image, "png", file));
    }
}
