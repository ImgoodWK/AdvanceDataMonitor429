package com.imgood.textech.webae.network;

import java.nio.charset.StandardCharsets;

import org.junit.Assert;
import org.junit.Test;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class WebAeBinaryPacketTest {

    private static final String OWNER = "00000000-0000-0000-0000-000000000001";

    @Test
    public void maximumBinaryChunksRemainBelowTheFmlBudget() {
        byte[] chunk = new byte[WebAeBinaryTransfer.MAX_PACKET_CHUNK_BYTES];

        PacketIconDirectCaptureResponse icon = new PacketIconDirectCaptureResponse();
        icon.requestId = "icon-request";
        icon.success = true;
        icon.chunkIndex = 0;
        icon.totalChunks = 1;
        icon.png = chunk;
        assertBounded(iconBuffer(icon));

        PacketWorldMapDirectCaptureResponse direct = new PacketWorldMapDirectCaptureResponse();
        direct.requestId = "map-request";
        direct.success = true;
        direct.chunkIndex = 0;
        direct.totalChunks = 1;
        direct.png = chunk;
        assertBounded(directBuffer(direct));

        PacketWebIconPullZip zip = new PacketWebIconPullZip(true, true, 0, 1, "default", chunk);
        ByteBuf zipBuffer = Unpooled.buffer();
        zip.toBytes(zipBuffer);
        assertBounded(zipBuffer);

        PacketWebMapTileUpload tile = mapTile(chunk);
        ByteBuf tileBuffer = Unpooled.buffer();
        tile.toBytes(tileBuffer);
        assertBounded(tileBuffer);
    }

    @Test
    public void captureResponsesRejectTrailingAndFailurePayloadBytes() {
        PacketIconDirectCaptureResponse source = new PacketIconDirectCaptureResponse();
        source.requestId = "request";
        source.success = true;
        source.chunkIndex = 0;
        source.totalChunks = 1;
        source.png = new byte[] { 1 };
        ByteBuf trailing = iconBuffer(source);
        trailing.writeByte(99);
        PacketIconDirectCaptureResponse decoded = new PacketIconDirectCaptureResponse();
        decoded.fromBytes(trailing);
        Assert.assertEquals("", decoded.requestId);
        Assert.assertEquals(0, decoded.png.length);

        ByteBuf failureWithData = Unpooled.buffer();
        writeUtf8(failureWithData, "request");
        failureWithData.writeBoolean(false);
        failureWithData.writeInt(0);
        failureWithData.writeInt(1);
        failureWithData.writeInt(1);
        failureWithData.writeByte(1);
        PacketWorldMapDirectCaptureResponse failed = new PacketWorldMapDirectCaptureResponse();
        failed.fromBytes(failureWithData);
        Assert.assertEquals("", failed.requestId);
        Assert.assertEquals(0, failed.png.length);
    }

    @Test
    public void uploadAndDownloadPacketsRejectTrailingBytes() {
        PacketWebMapTileUpload tile = mapTile(new byte[] { 1, 2, 3 });
        ByteBuf tileBuffer = Unpooled.buffer();
        tile.toBytes(tileBuffer);
        tileBuffer.writeByte(7);
        PacketWebMapTileUpload decodedTile = new PacketWebMapTileUpload();
        decodedTile.fromBytes(tileBuffer);
        Assert.assertEquals(0, decodedTile.png.length);

        PacketWorldMapSnapshotTileData data = new PacketWorldMapSnapshotTileData();
        data.ownerUuid = OWNER;
        data.networkId = 4;
        data.snapshotVersion = 8;
        data.layer = "terrain";
        data.dim = 0;
        data.chunkX = 1;
        data.chunkZ = 2;
        data.chunkIndex = 0;
        data.totalChunks = 1;
        data.png = new byte[] { 1 };
        ByteBuf dataBuffer = Unpooled.buffer();
        data.toBytes(dataBuffer);
        dataBuffer.writeByte(7);
        PacketWorldMapSnapshotTileData decodedData = new PacketWorldMapSnapshotTileData();
        decodedData.fromBytes(dataBuffer);
        Assert.assertTrue(decodedData.malformed);

        PacketWorldMapSnapshotTilePull pull = new PacketWorldMapSnapshotTilePull();
        pull.ownerUuid = OWNER;
        pull.networkId = 4;
        pull.snapshotVersion = 8;
        pull.layer = "terrain";
        pull.dim = 0;
        pull.chunkX = 1;
        pull.chunkZ = 2;
        ByteBuf pullBuffer = Unpooled.buffer();
        pull.toBytes(pullBuffer);
        pullBuffer.writeByte(7);
        PacketWorldMapSnapshotTilePull decodedPull = new PacketWorldMapSnapshotTilePull();
        decodedPull.fromBytes(pullBuffer);
        Assert.assertEquals("", decodedPull.ownerUuid);
    }

    @Test
    public void snapshotFinalizeUsesNoBinaryChunk() {
        PacketWorldMapSnapshotTileUpload finalize = new PacketWorldMapSnapshotTileUpload();
        finalize.ownerUuid = OWNER;
        finalize.networkId = 4;
        finalize.snapshotVersion = 8;
        finalize.layer = "terrain";
        finalize.chunkIndex = 0;
        finalize.totalChunks = 1;
        finalize.finalizeSnapshot = true;
        finalize.source = "client_gl";
        finalize.sourceStatsJson = "{}";
        finalize.tilePx = 256;
        finalize.png = null;
        ByteBuf buffer = Unpooled.buffer();
        finalize.toBytes(buffer);
        PacketWorldMapSnapshotTileUpload decoded = new PacketWorldMapSnapshotTileUpload();
        decoded.fromBytes(buffer);
        Assert.assertTrue(decoded.finalizeSnapshot);
        Assert.assertEquals(0, decoded.png.length);
    }

    private static PacketWebMapTileUpload mapTile(byte[] png) {
        PacketWebMapTileUpload tile = new PacketWebMapTileUpload();
        tile.view = "surface";
        tile.layer = "terrain";
        tile.quality = "ultra";
        tile.dim = 0;
        tile.chunkX = 1;
        tile.chunkZ = 2;
        tile.networkId = 4;
        tile.ownerUuid = OWNER;
        tile.chunkIndex = 0;
        tile.totalChunks = 1;
        tile.png = png;
        return tile;
    }

    private static ByteBuf iconBuffer(PacketIconDirectCaptureResponse packet) {
        ByteBuf buffer = Unpooled.buffer();
        packet.toBytes(buffer);
        return buffer;
    }

    private static ByteBuf directBuffer(PacketWorldMapDirectCaptureResponse packet) {
        ByteBuf buffer = Unpooled.buffer();
        packet.toBytes(buffer);
        return buffer;
    }

    private static void assertBounded(ByteBuf buffer) {
        Assert.assertTrue(buffer.readableBytes() <= 30000);
    }

    private static void writeUtf8(ByteBuf buffer, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        buffer.writeInt(bytes.length);
        buffer.writeBytes(bytes);
    }
}
