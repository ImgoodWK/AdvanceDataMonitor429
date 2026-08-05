package com.imgood.textech.webae.network;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import javax.imageio.ImageIO;

import org.junit.Assert;
import org.junit.Test;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class PacketWebUploadPacketTest {

    private static final String PLAYER_UUID = "00000000-0000-0000-0000-000000000001";

    @Test
    public void iconUploadRoundTripsMaximumChunkWithinBodyBudget() {
        PacketWebIconUpload source = new PacketWebIconUpload(
            true,
            true,
            0,
            1,
            "default",
            "hybrid",
            PLAYER_UUID,
            new byte[WebAeBinaryTransfer.MAX_PACKET_CHUNK_BYTES]);
        ByteBuf buffer = Unpooled.buffer();
        source.toBytes(buffer);
        Assert.assertTrue(buffer.readableBytes() <= 30000);

        PacketWebIconUpload decoded = new PacketWebIconUpload();
        decoded.fromBytes(buffer);
        Assert.assertEquals("default", decoded.packName);
        Assert.assertEquals(WebAeBinaryTransfer.MAX_PACKET_CHUNK_BYTES, decoded.chunkData.length);
    }

    @Test
    public void iconUploadRejectsTrailingAndEmptyChunks() {
        PacketWebIconUpload source = new PacketWebIconUpload(
            true,
            true,
            0,
            1,
            "default",
            "hybrid",
            PLAYER_UUID,
            new byte[] { 1 });
        ByteBuf trailing = Unpooled.buffer();
        source.toBytes(trailing);
        trailing.writeByte(2);
        PacketWebIconUpload decoded = new PacketWebIconUpload();
        decoded.fromBytes(trailing);
        Assert.assertEquals(0, decoded.chunkData.length);

        source.chunkData = new byte[0];
        try {
            source.toBytes(Unpooled.buffer());
            Assert.fail("empty icon chunk must be rejected");
        } catch (IllegalArgumentException expected) {}
    }

    @Test
    public void iconUploadAssemblerRequiresStrictOrder() {
        PacketWebIconUpload.ChunkSink.clearAllForTests();
        String player = "strict-order-player";
        PacketWebIconUpload.ChunkSink sink = PacketWebIconUpload.ChunkSink.get(player, "default", "hybrid", 2, true);
        try {
            Assert.assertNotNull(sink);
            Assert.assertFalse(sink.put(1, new byte[] { 2 }));
        } finally {
            PacketWebIconUpload.ChunkSink.remove(player, "default", "hybrid");
        }

        sink = PacketWebIconUpload.ChunkSink.get(player, "default", "hybrid", 2, true);
        try {
            Assert.assertTrue(sink.put(0, new byte[] { 1 }));
            Assert.assertTrue(sink.put(1, new byte[] { 2 }));
            Assert.assertArrayEquals(new byte[] { 1, 2 }, sink.reassemble());
        } finally {
            PacketWebIconUpload.ChunkSink.remove(player, "default", "hybrid");
            PacketWebIconUpload.ChunkSink.clearAllForTests();
        }
    }

    @Test
    public void iconBundleRequiresStrictJsonAndValidPngForEveryEntry() throws Exception {
        String valid = Base64.getEncoder()
            .encodeToString(png(16, 16));
        byte[] json = ("{\"mod:good\":\"" + valid + "\"}").getBytes(StandardCharsets.UTF_8);
        Map<String, byte[]> decoded = PacketWebIconUpload.decodeBundle(json);
        Assert.assertEquals(1, decoded.size());
        Assert.assertArrayEquals(png(16, 16), decoded.get("mod:good"));

        assertInvalidIconBundle("{\"mod:good\":\"" + valid + "\",\"mod:bad\":\"AQID\"}");
        assertInvalidIconBundle("{\"mod:good\":\"" + valid + "\",\"mod:good\":\"" + valid + "\"}");
        assertInvalidIconBundle("{\"mod:good\":\"" + valid + "\"} true");
    }

    @Test
    public void iconUploadEnforcesPerPlayerAndGlobalSessionReservations() {
        PacketWebIconUpload.ChunkSink.clearAllForTests();
        try {
            Assert.assertNotNull(PacketWebIconUpload.ChunkSink.get("player-a", "pack-a", "hybrid", 1, true));
            Assert.assertNotNull(PacketWebIconUpload.ChunkSink.get("player-a", "pack-b", "hybrid", 1, true));
            Assert.assertNull(PacketWebIconUpload.ChunkSink.get("player-a", "pack-c", "hybrid", 1, true));
            Assert.assertNull(PacketWebIconUpload.ChunkSink.get("player-a", "pack-a", "hybrid", 1, true));
            Assert.assertNotNull(PacketWebIconUpload.ChunkSink.get("player-a", "pack-a", "hybrid", 1, false));

            PacketWebIconUpload.ChunkSink.clearAllForTests();
            int maxChunks = (8 * 1024 * 1024 + WebAeBinaryTransfer.MAX_PACKET_CHUNK_BYTES - 1)
                / WebAeBinaryTransfer.MAX_PACKET_CHUNK_BYTES;
            for (int i = 0; i < 4; i++) {
                Assert.assertNotNull(
                    PacketWebIconUpload.ChunkSink.get("global-" + i, "pack", "hybrid", maxChunks, true));
            }
            Assert.assertNull(PacketWebIconUpload.ChunkSink.get("global-overflow", "pack", "hybrid", maxChunks, true));
        } finally {
            PacketWebIconUpload.ChunkSink.clearAllForTests();
        }
    }

    @Test
    public void recipeUploadRoundTripsAtProjectBudgetAndRejectsTrailingBytes() {
        byte[] json = new byte[RecipeUploadBatcher.MAX_RECIPE_JSON_BYTES];
        java.util.Arrays.fill(json, (byte) ' ');
        PacketWebRecipeUpload source = new PacketWebRecipeUpload(true, true, 0, 1, 0, PLAYER_UUID, json);
        ByteBuf buffer = Unpooled.buffer();
        source.toBytes(buffer);
        Assert.assertTrue(buffer.readableBytes() <= RecipeUploadBatcher.PACKET_BODY_BUDGET_BYTES);

        PacketWebRecipeUpload decoded = new PacketWebRecipeUpload();
        decoded.fromBytes(buffer);
        Assert.assertEquals(json.length, decoded.recipeDataJson.length);

        PacketWebRecipeUpload small = new PacketWebRecipeUpload(
            true,
            true,
            0,
            1,
            0,
            PLAYER_UUID,
            "[]".getBytes(StandardCharsets.UTF_8));
        ByteBuf trailing = Unpooled.buffer();
        small.toBytes(trailing);
        trailing.writeByte(2);
        decoded = new PacketWebRecipeUpload();
        decoded.fromBytes(trailing);
        Assert.assertEquals(0, decoded.recipeDataJson.length);
    }

    @Test
    public void recipeUploadRejectsEmptyPayload() {
        PacketWebRecipeUpload source = new PacketWebRecipeUpload(
            true,
            true,
            0,
            1,
            0,
            PLAYER_UUID,
            new byte[0]);
        try {
            source.toBytes(Unpooled.buffer());
            Assert.fail("empty recipe JSON must be rejected");
        } catch (IllegalArgumentException expected) {}
    }

    private static void assertInvalidIconBundle(String json) {
        try {
            PacketWebIconUpload.decodeBundle(json.getBytes(StandardCharsets.UTF_8));
            Assert.fail("invalid icon bundle must be rejected as one transaction");
        } catch (IllegalArgumentException expected) {}
    }

    private static byte[] png(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Assert.assertTrue(ImageIO.write(image, "png", output));
        return output.toByteArray();
    }
}
