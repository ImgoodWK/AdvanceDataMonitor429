package com.imgood.textech.webae.network;

import org.junit.Assert;
import org.junit.Test;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class PacketWorldMapSnapshotSyncPacketTest {

    private static final String OWNER = "00000000-0000-0000-0000-000000000001";

    @Test
    public void requestRoundTripsPageOffset() {
        PacketWorldMapSnapshotSyncRequest source = new PacketWorldMapSnapshotSyncRequest(OWNER, 7, 11, 321);
        ByteBuf buffer = Unpooled.buffer();
        source.toBytes(buffer);

        PacketWorldMapSnapshotSyncRequest decoded = new PacketWorldMapSnapshotSyncRequest();
        decoded.fromBytes(buffer);

        Assert.assertEquals(OWNER, decoded.ownerUuid);
        Assert.assertEquals(7, decoded.networkId);
        Assert.assertEquals(11, decoded.localVersion);
        Assert.assertEquals(321, decoded.tileOffset);
    }

    @Test
    public void responseRoundTripsPageMetadataWithinFmlBudget() {
        PacketWorldMapSnapshotSyncResponse source = new PacketWorldMapSnapshotSyncResponse();
        source.ownerUuid = OWNER;
        source.networkId = 7;
        source.serverVersion = 12;
        source.previousServerVersion = 11;
        source.batchOffset = 100;
        int index = 0;
        while (source.tryAddTileKey("terrain:0:" + index + ":" + -index)) {
            index++;
        }
        source.nextOffset = source.batchOffset + source.tileKeys.size();
        source.complete = false;

        ByteBuf buffer = Unpooled.buffer();
        source.toBytes(buffer);
        Assert.assertTrue(buffer.readableBytes() <= PacketWorldMapSnapshotSyncResponse.MAX_PACKET_BYTES);

        PacketWorldMapSnapshotSyncResponse decoded = new PacketWorldMapSnapshotSyncResponse();
        decoded.fromBytes(buffer);
        Assert.assertFalse(decoded.malformed);
        Assert.assertEquals(source.batchOffset, decoded.batchOffset);
        Assert.assertEquals(source.nextOffset, decoded.nextOffset);
        Assert.assertEquals(source.tileKeys, decoded.tileKeys);
        Assert.assertFalse(decoded.complete);
    }

    @Test
    public void responseRejectsDiscontinuousOffsets() {
        ByteBuf buffer = Unpooled.buffer();
        writeUtf8(buffer, OWNER);
        buffer.writeInt(7);
        buffer.writeInt(12);
        buffer.writeInt(11);
        buffer.writeInt(10);
        buffer.writeInt(12);
        buffer.writeBoolean(false);
        buffer.writeInt(1);
        writeUtf8(buffer, "terrain:0:0:0");

        PacketWorldMapSnapshotSyncResponse decoded = new PacketWorldMapSnapshotSyncResponse();
        decoded.fromBytes(buffer);
        Assert.assertTrue(decoded.malformed);
    }

    private static void writeUtf8(ByteBuf buffer, String value) {
        byte[] bytes = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        buffer.writeInt(bytes.length);
        buffer.writeBytes(bytes);
    }
}
