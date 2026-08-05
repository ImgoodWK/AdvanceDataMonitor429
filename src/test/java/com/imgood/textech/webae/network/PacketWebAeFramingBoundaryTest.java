package com.imgood.textech.webae.network;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

@RunWith(Parameterized.class)
public class PacketWebAeFramingBoundaryTest {

    private static final String OWNER = "00000000-0000-0000-0000-000000000001";
    private static final int MAX_PACKET_BYTES = 30_000;

    private final IMessage source;

    public PacketWebAeFramingBoundaryTest(String name, IMessage source) {
        this.source = source;
    }

    @Parameters(name = "{0}")
    public static Collection<Object[]> packets() {
        return Arrays.asList(
            new Object[][] {
                { "icon-direct-request", iconDirectRequest("nei") },
                { "screenshot-ack", new PacketScreenshotUploadAck("upload", true, "ok", "attachment") },
                { "alert-notify", new PacketWebAlertNotify("warning", "title", "message", 10, 3, "top_right", true) },
                { "console-token", new PacketWebConsoleTokenNotify(
                    PacketWebConsoleTokenNotify.KIND_ISSUE,
                    "token",
                    8080,
                    "127.0.0.1") },
                { "icon-request", new PacketWebIconRequest("default", "nei", "minecraft:stone@0") },
                { "icon-resolve-nack", new PacketWebIconResolveNack("default", "nei", "minecraft:stone@0") },
                { "icon-upload-ack", new PacketWebIconUploadAck(true, 1, 1, "ok") },
                { "map-tile-job", new PacketWebMapTileJob("flat", "terrain", "high", 0, 1, 2, 7) },
                { "recipe-upload-ack", new PacketWebRecipeUploadAck(true, 1, 1, "ok") },
                { "capture-accept", new PacketWorldMapCaptureAccept("request") },
                { "capture-offer", new PacketWorldMapCaptureOffer("request", OWNER, 7, "requester", 12, 1234L) },
                { "direct-capture-request", directCaptureRequest("terrain") },
                { "snapshot-sync-request", new PacketWorldMapSnapshotSyncRequest(OWNER, 7, 1, 0) },
                { "snapshot-sync-response", snapshotSyncResponse() } });
    }

    @Test
    public void legalBodyRoundTripsAndAllTruncationsOrTrailingBytesAreRejected() throws Exception {
        byte[] body = encode(source);
        Assert.assertTrue("encoded body must fit the 30000-byte budget", body.length <= MAX_PACKET_BYTES);
        assertAccepted(body);

        for (int length = 0; length < body.length; length++) {
            assertRejected(Arrays.copyOf(body, length));
        }

        byte[] trailing = Arrays.copyOf(body, body.length + 1);
        trailing[trailing.length - 1] = 1;
        assertRejected(trailing);
    }

    @Test
    public void oversizedBodyIsRejectedBeforeDecode() throws Exception {
        assertRejected(new byte[MAX_PACKET_BYTES + 1]);
    }

    @Test
    public void malformedUtf8IsRejectedForBothStringFramings() throws Exception {
        if (!(source instanceof PacketScreenshotUploadAck) && !(source instanceof PacketWebAlertNotify)) {
            return;
        }
        ByteBuf wire = Unpooled.buffer();
        if (source instanceof PacketScreenshotUploadAck) {
            wire.writeInt(2);
        } else {
            wire.writeShort(2);
        }
        wire.writeByte(0xc3);
        wire.writeByte(0x28);
        assertRejected(freshPacket(), wire);
    }

    @Test
    public void unknownEnumAndKindValuesAreRejectedByDecoder() throws Exception {
        if (source instanceof PacketWebConsoleTokenNotify) {
            ByteBuf wire = Unpooled.buffer();
            wire.writeByte(127);
            writeUtf8(wire, "token");
            wire.writeInt(8080);
            writeUtf8(wire, "127.0.0.1");
            assertRejected(freshPacket(), wire);
        } else if (source instanceof PacketWebAlertNotify) {
            assertRejected(freshPacket(), alertWire("critical", "top_right"));
            assertRejected(freshPacket(), alertWire("warning", "center"));
        } else if (source instanceof PacketIconDirectCaptureRequest) {
            assertRejected(freshPacket(), iconDirectWire("unknown"));
        } else if (source instanceof PacketWebIconRequest || source instanceof PacketWebIconResolveNack) {
            assertRejected(freshPacket(), iconWire("unknown"));
        } else if (source instanceof PacketWebMapTileJob) {
            assertRejected(freshPacket(), mapTileJobWire("unknown", "terrain", "high"));
            assertRejected(freshPacket(), mapTileJobWire("flat", "unknown", "high"));
            assertRejected(freshPacket(), mapTileJobWire("flat", "terrain", "unknown"));
            assertRejected(freshPacket(), mapTileJobWire("flat", "terrain", ""));
        } else if (source instanceof PacketWorldMapDirectCaptureRequest) {
            assertRejected(freshPacket(), directCaptureWire("unknown"));
        }
    }

    @Test
    public void encoderRejectsUnknownEnumAndKindWithoutChangingWriterIndex() throws Exception {
        IMessage invalid = null;
        if (source instanceof PacketWebConsoleTokenNotify) {
            invalid = new PacketWebConsoleTokenNotify((byte) 127, "token", 8080, "127.0.0.1");
        } else if (source instanceof PacketWebAlertNotify) {
            invalid = new PacketWebAlertNotify("critical", "title", "message", 10, 3, "top_right", true);
        } else if (source instanceof PacketIconDirectCaptureRequest) {
            invalid = iconDirectRequest("unknown");
        } else if (source instanceof PacketWebIconRequest) {
            invalid = new PacketWebIconRequest("default", "unknown", "minecraft:stone@0");
        } else if (source instanceof PacketWebIconResolveNack) {
            invalid = new PacketWebIconResolveNack("default", "unknown", "minecraft:stone@0");
        } else if (source instanceof PacketWebMapTileJob) {
            PacketWebMapTileJob invalidJob = new PacketWebMapTileJob("flat", "terrain", "high", 0, 1, 2, 7);
            invalidJob.quality = "unknown";
            invalid = invalidJob;
        } else if (source instanceof PacketWorldMapDirectCaptureRequest) {
            invalid = directCaptureRequest("unknown");
        }
        if (invalid != null) {
            assertEncoderRollsBack(invalid);
        }
    }

    @Test
    public void lateStringFailureRollsBackPartialBody() throws Exception {
        IMessage invalid = null;
        if (source instanceof PacketScreenshotUploadAck) {
            invalid = new PacketScreenshotUploadAck("upload", true, "ok", repeat('a', 65));
        } else if (source instanceof PacketWebIconUploadAck) {
            invalid = new PacketWebIconUploadAck(true, 1, 1, repeat('a', 8 * 1024 + 1));
        } else if (source instanceof PacketWebRecipeUploadAck) {
            invalid = new PacketWebRecipeUploadAck(true, 1, 1, repeat('a', 8 * 1024 + 1));
        } else if (source instanceof PacketWorldMapCaptureOffer) {
            invalid = new PacketWorldMapCaptureOffer("request", OWNER, 7, repeat('a', 257), 1, 1234L);
        }
        if (invalid != null) {
            assertEncoderRollsBack(invalid);
        }
    }

    private void assertAccepted(byte[] body) throws Exception {
        IMessage decoded = freshPacket();
        ByteBuf wire = Unpooled.wrappedBuffer(body);
        try {
            decoded.fromBytes(wire);
            Assert.assertTrue("legal packet must decode successfully", isValid(decoded));
            Assert.assertFalse("successful decode must consume the complete body", wire.isReadable());
        } finally {
            wire.release();
        }
    }

    private void assertRejected(byte[] body) throws Exception {
        ByteBuf wire = Unpooled.wrappedBuffer(body);
        assertRejected(freshPacket(), wire);
    }

    private static void assertRejected(IMessage decoded, ByteBuf wire) throws Exception {
        try {
            decoded.fromBytes(wire);
            Assert.assertFalse("malformed packet must be marked invalid", isValid(decoded));
        } catch (RuntimeException e) {
            Assert.fail("decoder leaked " + e.getClass().getSimpleName() + ": " + e.getMessage());
        } finally {
            wire.release();
        }
    }

    private static void assertEncoderRollsBack(IMessage packet) {
        ByteBuf wire = Unpooled.buffer();
        try {
            wire.writeInt(0x13579bdf);
            int start = wire.writerIndex();
            try {
                packet.toBytes(wire);
                Assert.fail("expected encoder to reject invalid packet");
            } catch (IllegalArgumentException expected) {}
            Assert.assertEquals("failed encoder must restore writerIndex", start, wire.writerIndex());
            Assert.assertEquals(0x13579bdf, wire.getInt(0));
        } finally {
            wire.release();
        }
    }

    private IMessage freshPacket() throws Exception {
        return source.getClass()
            .newInstance();
    }

    private static boolean isValid(IMessage packet) throws Exception {
        try {
            Field valid = packet.getClass()
                .getDeclaredField("valid");
            valid.setAccessible(true);
            return valid.getBoolean(packet);
        } catch (NoSuchFieldException ignored) {
            Field malformed = packet.getClass()
                .getDeclaredField("malformed");
            malformed.setAccessible(true);
            return !malformed.getBoolean(packet);
        }
    }

    private static byte[] encode(IMessage packet) {
        ByteBuf wire = Unpooled.buffer();
        try {
            packet.toBytes(wire);
            byte[] body = new byte[wire.readableBytes()];
            wire.readBytes(body);
            return body;
        } finally {
            wire.release();
        }
    }

    private static PacketIconDirectCaptureRequest iconDirectRequest(String mode) {
        PacketIconDirectCaptureRequest packet = new PacketIconDirectCaptureRequest();
        packet.requestId = "request";
        packet.packName = "default";
        packet.renderMode = mode;
        packet.itemId = "minecraft:stone@0";
        return packet;
    }

    private static PacketWorldMapDirectCaptureRequest directCaptureRequest(String layer) {
        PacketWorldMapDirectCaptureRequest packet = new PacketWorldMapDirectCaptureRequest();
        packet.requestId = "request";
        packet.layer = layer;
        packet.ownerUuid = OWNER;
        packet.networkId = 7;
        packet.dim = 0;
        packet.chunkX = 1;
        packet.chunkZ = 2;
        packet.tilePx = 256;
        return packet;
    }

    private static PacketWorldMapSnapshotSyncResponse snapshotSyncResponse() {
        PacketWorldMapSnapshotSyncResponse packet = new PacketWorldMapSnapshotSyncResponse();
        packet.ownerUuid = OWNER;
        packet.networkId = 7;
        packet.serverVersion = 2;
        packet.previousServerVersion = 1;
        packet.batchOffset = 0;
        packet.nextOffset = 1;
        packet.complete = true;
        packet.tileKeys.add("terrain:0:1:2");
        return packet;
    }

    private static ByteBuf alertWire(String severity, String position) {
        ByteBuf wire = Unpooled.buffer();
        writeShortUtf8(wire, severity);
        writeShortUtf8(wire, "title");
        writeShortUtf8(wire, "message");
        wire.writeByte(10);
        wire.writeByte(3);
        writeShortUtf8(wire, position);
        wire.writeBoolean(true);
        return wire;
    }

    private static ByteBuf iconDirectWire(String mode) {
        ByteBuf wire = Unpooled.buffer();
        writeUtf8(wire, "request");
        writeUtf8(wire, "default");
        writeUtf8(wire, mode);
        writeUtf8(wire, "minecraft:stone@0");
        return wire;
    }

    private static ByteBuf iconWire(String mode) {
        ByteBuf wire = Unpooled.buffer();
        writeUtf8(wire, "default");
        writeUtf8(wire, mode);
        writeUtf8(wire, "minecraft:stone@0");
        return wire;
    }

    private static ByteBuf mapTileJobWire(String view, String layer, String quality) {
        ByteBuf wire = Unpooled.buffer();
        writeUtf8(wire, view);
        writeUtf8(wire, layer);
        writeUtf8(wire, quality);
        wire.writeInt(0);
        wire.writeInt(1);
        wire.writeInt(2);
        wire.writeInt(7);
        return wire;
    }

    private static ByteBuf directCaptureWire(String layer) {
        ByteBuf wire = Unpooled.buffer();
        writeUtf8(wire, "request");
        writeUtf8(wire, layer);
        writeUtf8(wire, OWNER);
        wire.writeInt(7);
        wire.writeInt(0);
        wire.writeInt(1);
        wire.writeInt(2);
        wire.writeInt(256);
        return wire;
    }

    private static void writeUtf8(ByteBuf wire, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        wire.writeInt(bytes.length);
        wire.writeBytes(bytes);
    }

    private static void writeShortUtf8(ByteBuf wire, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        wire.writeShort(bytes.length);
        wire.writeBytes(bytes);
    }

    private static String repeat(char value, int count) {
        char[] chars = new char[count];
        Arrays.fill(chars, value);
        return new String(chars);
    }
}
