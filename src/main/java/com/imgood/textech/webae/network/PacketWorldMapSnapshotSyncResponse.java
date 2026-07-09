package com.imgood.textech.webae.network;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.imgood.textech.client.worldmap.WorldMapSnapshotDownloadHandler;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

/**
 * S→C: server snapshot version and tile key list for sync. Packet ID 42.
 */
public class PacketWorldMapSnapshotSyncResponse implements IMessage {

    public String ownerUuid;
    public int networkId;
    public int serverVersion;
    public List<String> tileKeys = new ArrayList<String>();

    public PacketWorldMapSnapshotSyncResponse() {}

    @Override
    public void toBytes(ByteBuf buf) {
        writeUtf8(buf, ownerUuid);
        buf.writeInt(networkId);
        buf.writeInt(serverVersion);
        buf.writeInt(tileKeys != null ? tileKeys.size() : 0);
        if (tileKeys != null) {
            for (String key : tileKeys) {
                writeUtf8(buf, key);
            }
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        ownerUuid = readUtf8(buf);
        networkId = buf.readInt();
        serverVersion = buf.readInt();
        int count = buf.readInt();
        tileKeys = new ArrayList<String>();
        for (int i = 0; i < count; i++) {
            tileKeys.add(readUtf8(buf));
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

    public static class Handler implements IMessageHandler<PacketWorldMapSnapshotSyncResponse, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(PacketWorldMapSnapshotSyncResponse message, MessageContext ctx) {
            WorldMapSnapshotDownloadHandler.instance()
                .onSyncResponse(message);
            return null;
        }
    }
}
