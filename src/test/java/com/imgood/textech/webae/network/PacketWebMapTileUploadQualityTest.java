package com.imgood.textech.webae.network;

import org.junit.Assert;
import org.junit.Test;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class PacketWebMapTileUploadQualityTest {

    private static final String OWNER = "00000000-0000-0000-0000-000000000001";

    @Test
    public void qualityIsAnExactClosedSet() {
        Assert.assertTrue(PacketWebMapTileUpload.isValidQuality("low"));
        Assert.assertTrue(PacketWebMapTileUpload.isValidQuality("medium"));
        Assert.assertTrue(PacketWebMapTileUpload.isValidQuality("high"));
        Assert.assertTrue(PacketWebMapTileUpload.isValidQuality("ultra"));
        Assert.assertFalse(PacketWebMapTileUpload.isValidQuality(null));
        Assert.assertFalse(PacketWebMapTileUpload.isValidQuality(""));
        Assert.assertFalse(PacketWebMapTileUpload.isValidQuality("MEDIUM"));
        Assert.assertFalse(PacketWebMapTileUpload.isValidQuality("unknown"));
    }

    @Test
    public void encoderAndDecoderRejectUnknownQuality() {
        PacketWebMapTileUpload packet = packet("unknown");
        try {
            packet.toBytes(Unpooled.buffer());
            Assert.fail("Expected unknown quality to be rejected by encoder");
        } catch (IllegalArgumentException expected) {}

        PacketWebMapTileUpload missingQuality = new PacketWebMapTileUpload(
            "flat",
            "terrain",
            null,
            0,
            0,
            0,
            7,
            OWNER,
            new byte[] { 1 });
        try {
            missingQuality.toBytes(Unpooled.buffer());
            Assert.fail("Expected missing quality to be rejected by encoder");
        } catch (IllegalArgumentException expected) {}

        ByteBuf wire = Unpooled.buffer();
        writeUtf8(wire, "flat");
        writeUtf8(wire, "terrain");
        writeUtf8(wire, "unknown");
        wire.writeInt(0);
        wire.writeInt(0);
        wire.writeInt(0);
        wire.writeInt(7);
        writeUtf8(wire, OWNER);
        wire.writeInt(0);
        wire.writeInt(1);
        wire.writeInt(1);
        wire.writeByte(1);

        PacketWebMapTileUpload decoded = new PacketWebMapTileUpload();
        decoded.fromBytes(wire);
        Assert.assertFalse(decoded.isValid());
    }

    @Test
    public void validQualityRoundTripsWithoutUsingFallbackParser() {
        PacketWebMapTileUpload source = packet("high");
        ByteBuf wire = Unpooled.buffer();
        source.toBytes(wire);

        PacketWebMapTileUpload decoded = new PacketWebMapTileUpload();
        decoded.fromBytes(wire);
        Assert.assertTrue(decoded.isValid());
        Assert.assertEquals("high", decoded.quality);
    }

    private static PacketWebMapTileUpload packet(String quality) {
        PacketWebMapTileUpload packet = new PacketWebMapTileUpload();
        packet.view = "flat";
        packet.layer = "terrain";
        packet.quality = quality;
        packet.dim = 0;
        packet.chunkX = 0;
        packet.chunkZ = 0;
        packet.networkId = 7;
        packet.ownerUuid = OWNER;
        packet.chunkIndex = 0;
        packet.totalChunks = 1;
        packet.png = new byte[] { 1 };
        return packet;
    }

    private static void writeUtf8(ByteBuf buffer, String value) {
        byte[] bytes = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        buffer.writeInt(bytes.length);
        buffer.writeBytes(bytes);
    }
}
