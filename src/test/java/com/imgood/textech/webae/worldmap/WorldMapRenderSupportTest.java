package com.imgood.textech.webae.worldmap;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.zip.CRC32;

import javax.imageio.ImageIO;

import org.junit.Assert;
import org.junit.Test;

public class WorldMapRenderSupportTest {

    @Test
    public void acceptsBoundedPngAndRejectsSizeAndDimensionViolations() throws Exception {
        byte[] png = png(64, 64);

        Assert.assertTrue(WorldMapRenderSupport.isValidTilePng(png));
        Assert.assertTrue(WorldMapRenderSupport.isValidBoundedPng(png, 0L, png.length, 64));
        Assert.assertFalse(WorldMapRenderSupport.isValidBoundedPng(png, 0L, png.length - 1L, 2048));
        Assert.assertFalse(WorldMapRenderSupport.isValidBoundedPng(png, 0L, png.length, 32));
        Assert.assertTrue(WorldMapRenderSupport.isValidBoundedPng(png, 0L, png.length, 2048));
    }

    @Test
    public void rejectsCorruptSignatureAndIhdrCrc() throws Exception {
        byte[] png = png(64, 64);
        byte[] badSignature = Arrays.copyOf(png, png.length);
        badSignature[0] = 0;
        Assert.assertFalse(WorldMapRenderSupport.isValidTilePng(badSignature));

        byte[] badCrc = Arrays.copyOf(png, png.length);
        badCrc[29] ^= 1;
        Assert.assertFalse(WorldMapRenderSupport.isValidTilePng(badCrc));
    }

    @Test
    public void rejectsUnsupportedIhdrFieldsEvenWithMatchingCrc() throws Exception {
        byte[] png = png(64, 64);
        Assert.assertFalse(WorldMapRenderSupport.isValidTilePng(withIhdrByte(png, 24, 3)));
        Assert.assertFalse(WorldMapRenderSupport.isValidTilePng(withIhdrByte(png, 25, 1)));
        Assert.assertFalse(WorldMapRenderSupport.isValidTilePng(withIhdrByte(png, 26, 1)));
        Assert.assertFalse(WorldMapRenderSupport.isValidTilePng(withIhdrByte(png, 27, 1)));
        Assert.assertFalse(WorldMapRenderSupport.isValidTilePng(withIhdrByte(png, 28, 2)));
    }

    private static byte[] png(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int red = (x * 17 + y * 3) & 0xff;
                int green = (x * 5 + y * 19) & 0xff;
                int blue = (x * 13 + y * 7) & 0xff;
                image.setRGB(x, y, 0xff000000 | red << 16 | green << 8 | blue);
            }
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Assert.assertTrue(ImageIO.write(image, "png", output));
        return output.toByteArray();
    }

    private static byte[] withIhdrByte(byte[] png, int offset, int value) {
        byte[] modified = Arrays.copyOf(png, png.length);
        modified[offset] = (byte) value;
        CRC32 crc = new CRC32();
        crc.update(modified, 12, 17);
        writeUint32(modified, 29, crc.getValue());
        return modified;
    }

    private static void writeUint32(byte[] bytes, int offset, long value) {
        bytes[offset] = (byte) (value >>> 24);
        bytes[offset + 1] = (byte) (value >>> 16);
        bytes[offset + 2] = (byte) (value >>> 8);
        bytes[offset + 3] = (byte) value;
    }
}
