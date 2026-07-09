package com.imgood.textech.webae.network;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.imgood.textech.client.worldmap.WorldMapSnapshotCaptureWorker;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

/**
 * S→C: start world map snapshot capture job. Packet ID 39.
 */
public class PacketWorldMapCaptureJob implements IMessage {

    public String ownerUuid;
    public int networkId;
    public int snapshotVersion;
    public int tilePx;
    public List<String> chunks = new ArrayList<String>();

    public PacketWorldMapCaptureJob() {}

    public PacketWorldMapCaptureJob(String ownerUuid, int networkId, int snapshotVersion, List<String> chunks,
        int tilePx) {
        this.ownerUuid = ownerUuid;
        this.networkId = networkId;
        this.snapshotVersion = snapshotVersion;
        this.tilePx = tilePx;
        if (chunks != null) {
            this.chunks = new ArrayList<String>(chunks);
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        writeUtf8(buf, ownerUuid);
        buf.writeInt(networkId);
        buf.writeInt(snapshotVersion);
        buf.writeInt(tilePx);
        buf.writeInt(chunks != null ? chunks.size() : 0);
        if (chunks != null) {
            for (String chunk : chunks) {
                writeUtf8(buf, chunk);
            }
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        ownerUuid = readUtf8(buf);
        networkId = buf.readInt();
        snapshotVersion = buf.readInt();
        tilePx = buf.readInt();
        int count = buf.readInt();
        chunks = new ArrayList<String>();
        for (int i = 0; i < count; i++) {
            chunks.add(readUtf8(buf));
        }
    }

    private static void writeUtf8(ByteBuf buf, String s) {
        if (s == null) {
            buf.writeInt(0);
            return;
        }
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        buf.writeInt(bytes.length);
        buf.writeBytes(bytes);
    }

    private static String readUtf8(ByteBuf buf) {
        int len = buf.readInt();
        if (len <= 0) {
            return "";
        }
        byte[] bytes = new byte[len];
        buf.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public static class Handler implements IMessageHandler<PacketWorldMapCaptureJob, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(PacketWorldMapCaptureJob message, MessageContext ctx) {
            WorldMapSnapshotCaptureWorker.instance()
                .startJob(message);
            return null;
        }
    }
}
