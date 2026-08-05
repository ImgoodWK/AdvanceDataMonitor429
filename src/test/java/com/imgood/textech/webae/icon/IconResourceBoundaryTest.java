package com.imgood.textech.webae.icon;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.imageio.ImageIO;

import org.junit.Assert;
import org.junit.Test;

public class IconResourceBoundaryTest {

    @Test
    public void validPngMustBeDecodableAndWithinDimensions() throws Exception {
        byte[] png = png(32, 32);

        Assert.assertTrue(IconStore.isValidPng(png));
        Assert.assertFalse(IconStore.isValidPng(Arrays.copyOf(png, png.length - 1)));
        Assert.assertFalse(IconStore.isValidPng(withoutPngSignature(png)));
        Assert.assertFalse(IconStore.isValidPng(png(IconStore.MAX_PNG_DIMENSION + 1, 1)));
    }

    @Test
    public void failedExtractionDoesNotLeaveEarlierEntries() throws Exception {
        File root = Files.createTempDirectory("webae-icon-extract-")
            .toFile();
        try {
            byte[] zip = zip(
                new Entry("ok.png", png(16, 16)),
                new Entry("broken.png", new byte[] { 0x01, 0x02, 0x03 }));

            try {
                IconLocalStore.extractZipToLocal(root, "test-pack", zip);
                Assert.fail("corrupt PNG must reject the complete ZIP");
            } catch (IOException expected) {
                // The important contract is that no earlier entry was promoted.
            }

            File destination = new File(
                new File(new File(root, "TeXTech"), "WebAE"),
                "icons-local/test-pack/nei");
            if (destination.exists()) {
                Assert.assertTrue(destination.isDirectory());
                File[] files = destination.listFiles();
                Assert.assertNotNull(files);
                Assert.assertEquals(0, files.length);
            }
        } finally {
            deleteTree(root);
        }
    }

    @Test
    public void validExtractionPromotesDecodedPng() throws Exception {
        File root = Files.createTempDirectory("webae-icon-extract-")
            .toFile();
        try {
            int written = IconLocalStore.extractZipToLocal(
                root,
                "test-pack",
                zip(new Entry("nei/item.png", png(16, 16))));

            Assert.assertEquals(1, written);
            File icon = new File(
                new File(new File(new File(root, "TeXTech"), "WebAE"), "icons-local/test-pack/nei"),
                "item.png");
            Assert.assertTrue(icon.isFile());
            Assert.assertTrue(IconStore.isValidPng(icon));
        } finally {
            deleteTree(root);
        }
    }

    @Test
    public void packetBundlePromotionIsAllOrNothing() throws Exception {
        File root = Files.createTempDirectory("webae-icon-batch-")
            .toFile();
        try {
            IconStore store = new IconStore(new File(root, "icons"));
            byte[] original = png(16, 16);
            Map<String, byte[]> initial = new LinkedHashMap<String, byte[]>();
            initial.put("mod:existing", original);
            Assert.assertTrue(store.writeIconPngBatch("test-pack", "hybrid", initial));

            Map<String, byte[]> invalid = new LinkedHashMap<String, byte[]>();
            invalid.put("mod:existing", png(24, 24));
            invalid.put("mod:broken", new byte[] { 1, 2, 3 });
            Assert.assertFalse(store.writeIconPngBatch("test-pack", "hybrid", invalid));

            File existing = store.resolveWriteTarget("test-pack", "hybrid", "mod:existing");
            File broken = store.resolveWriteTarget("test-pack", "hybrid", "mod:broken");
            Assert.assertArrayEquals(original, Files.readAllBytes(existing.toPath()));
            Assert.assertFalse(broken.exists());
        } finally {
            deleteTree(root);
        }
    }

    @Test
    public void packetBundleRejectsSanitizedTargetCollisions() throws Exception {
        File root = Files.createTempDirectory("webae-icon-batch-")
            .toFile();
        try {
            IconStore store = new IconStore(new File(root, "icons"));
            Map<String, byte[]> collision = new LinkedHashMap<String, byte[]>();
            collision.put("mod:item", png(16, 16));
            collision.put("mod/item", png(16, 16));

            Assert.assertFalse(store.writeIconPngBatch("test-pack", "hybrid", collision));
            File mode = new File(new File(new File(root, "icons"), "test-pack"), "hybrid");
            File[] files = mode.listFiles();
            Assert.assertNotNull(files);
            Assert.assertEquals(0, files.length);
        } finally {
            deleteTree(root);
        }
    }

    private static byte[] png(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(new Color(0x33, 0x66, 0x99, 0xff));
            graphics.fillRect(0, 0, width, height);
        } finally {
            graphics.dispose();
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Assert.assertTrue(ImageIO.write(image, "png", output));
        return output.toByteArray();
    }

    private static byte[] withoutPngSignature(byte[] png) {
        byte[] copy = png.clone();
        copy[0] = 0;
        return copy;
    }

    private static byte[] zip(Entry... entries) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ZipOutputStream zip = new ZipOutputStream(output);
        try {
            for (Entry entry : entries) {
                zip.putNextEntry(new ZipEntry(entry.name));
                zip.write(entry.bytes);
                zip.closeEntry();
            }
        } finally {
            zip.close();
        }
        return output.toByteArray();
    }

    private static void deleteTree(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) deleteTree(child);
        }
        file.delete();
    }

    private static final class Entry {

        final String name;
        final byte[] bytes;

        Entry(String name, byte[] bytes) {
            this.name = name;
            this.bytes = bytes;
        }
    }
}
