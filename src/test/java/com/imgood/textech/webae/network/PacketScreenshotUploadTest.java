package com.imgood.textech.webae.network;

import org.junit.Assert;
import org.junit.Test;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class PacketScreenshotUploadTest {

    @Test
    public void firstChunkRoundTripsWithinThePacketBudget() {
        PacketScreenshotUpload source = new PacketScreenshotUpload(
            repeat('u', 64),
            0,
            2,
            PacketScreenshotUpload.MAX_CHUNK_BYTES + 1,
            repeat('d', 16),
            repeat('t', 16),
            repeat('x', 192),
            repeat('c', 768),
            repeat('f', 192),
            1920,
            1080,
            new byte[PacketScreenshotUpload.MAX_CHUNK_BYTES]);
        ByteBuf buffer = Unpooled.buffer();
        source.toBytes(buffer);
        Assert.assertTrue(buffer.readableBytes() <= PacketScreenshotUpload.MAX_PACKET_BODY_BYTES);

        PacketScreenshotUpload decoded = new PacketScreenshotUpload();
        decoded.fromBytes(buffer);
        Assert.assertFalse(decoded.malformed);
        Assert.assertEquals(source.uploadId, decoded.uploadId);
        Assert.assertEquals(source.destination, decoded.destination);
        Assert.assertEquals(source.caption, decoded.caption);
        Assert.assertEquals(source.chunk.length, decoded.chunk.length);
    }

    @Test
    public void continuationDoesNotCarryMetadata() {
        PacketScreenshotUpload source = new PacketScreenshotUpload(
            "upload",
            1,
            2,
            2,
            "qq",
            "group",
            "123",
            "caption",
            "shot.jpg",
            1,
            1,
            new byte[] { 2 });
        ByteBuf buffer = Unpooled.buffer();
        source.toBytes(buffer);
        PacketScreenshotUpload decoded = new PacketScreenshotUpload();
        decoded.fromBytes(buffer);
        Assert.assertFalse(decoded.malformed);
        Assert.assertEquals("", decoded.destination);
        Assert.assertEquals("", decoded.caption);
        Assert.assertArrayEquals(new byte[] { 2 }, decoded.chunk);
    }

    @Test
    public void trailingTruncatedAndEmptyChunksAreRejected() {
        PacketScreenshotUpload source = new PacketScreenshotUpload(
            "upload",
            0,
            1,
            1,
            "local",
            "",
            "",
            "",
            "shot.jpg",
            1,
            1,
            new byte[] { 1 });
        ByteBuf trailing = Unpooled.buffer();
        source.toBytes(trailing);
        trailing.writeByte(2);
        PacketScreenshotUpload decoded = new PacketScreenshotUpload();
        decoded.fromBytes(trailing);
        Assert.assertTrue(decoded.malformed);

        ByteBuf truncated = Unpooled.buffer();
        truncated.writeInt(64);
        PacketScreenshotUpload shortPacket = new PacketScreenshotUpload();
        shortPacket.fromBytes(truncated);
        Assert.assertTrue(shortPacket.malformed);

        source.chunk = new byte[0];
        try {
            source.toBytes(Unpooled.buffer());
            Assert.fail("empty screenshot chunk must be rejected");
        } catch (IllegalArgumentException expected) {}
    }

    private static String repeat(char value, int count) {
        StringBuilder builder = new StringBuilder(count);
        for (int i = 0; i < count; i++) builder.append(value);
        return builder.toString();
    }
}
