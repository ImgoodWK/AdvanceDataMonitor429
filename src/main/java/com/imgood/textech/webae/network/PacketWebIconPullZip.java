package com.imgood.textech.webae.network;

import java.nio.charset.StandardCharsets;

import com.imgood.textech.webae.icon.IconLocalStore;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

/**
 * S→C chunked zip of server icon pack for {@code /admweb icons pull}.
 * Packet ID 49.
 */
public class PacketWebIconPullZip implements IMessage {

    public boolean isStart;
    public boolean isEnd;
    public int chunkIndex;
    public int totalChunks;
    public String packName;
    public byte[] data;

    public PacketWebIconPullZip() {}

    public PacketWebIconPullZip(boolean isStart, boolean isEnd, int chunkIndex, int totalChunks, String packName,
        byte[] data) {
        this.isStart = isStart;
        this.isEnd = isEnd;
        this.chunkIndex = chunkIndex;
        this.totalChunks = totalChunks;
        this.packName = packName != null ? packName : "default";
        this.data = data != null ? data : new byte[0];
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(isStart);
        buf.writeBoolean(isEnd);
        buf.writeInt(chunkIndex);
        buf.writeInt(totalChunks);
        writeUtf8(buf, packName);
        buf.writeInt(data.length);
        buf.writeBytes(data);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        isStart = buf.readBoolean();
        isEnd = buf.readBoolean();
        chunkIndex = buf.readInt();
        totalChunks = buf.readInt();
        packName = readUtf8(buf);
        int len = buf.readInt();
        if (len < 0) len = 0;
        if (len > 65536) len = 65536;
        data = new byte[len];
        if (len > 0) buf.readBytes(data);
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
        if (len <= 0) return "";
        byte[] bytes = new byte[len];
        buf.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    @SideOnly(Side.CLIENT)
    public static class Handler implements IMessageHandler<PacketWebIconPullZip, IMessage> {

        @Override
        public IMessage onMessage(final PacketWebIconPullZip message, MessageContext ctx) {
            scheduleOnClientThread(new Runnable() {

                @Override
                public void run() {
                    IconLocalStore.onPullChunk(
                        message.isStart,
                        message.isEnd,
                        message.chunkIndex,
                        message.totalChunks,
                        message.packName,
                        message.data);
                }
            });
            return null;
        }

        @SideOnly(Side.CLIENT)
        private static void scheduleOnClientThread(Runnable runnable) {
            try {
                net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
                mc.getClass()
                    .getMethod("func_152344_a", Runnable.class)
                    .invoke(mc, runnable);
            } catch (Exception e) {
                runnable.run();
            }
        }
    }
}
