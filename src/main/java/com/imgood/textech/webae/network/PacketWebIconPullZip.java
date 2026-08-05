package com.imgood.textech.webae.network;

import com.imgood.textech.utils.NetworkPacketCodec;
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
    private boolean valid = true;

    private static final int MAX_PACK_NAME_BYTES = 128;
    public static final int MAX_CHUNK_BYTES = WebAeBinaryTransfer.MAX_PACKET_CHUNK_BYTES;
    public static final int MAX_ZIP_BYTES = 8 * 1024 * 1024;
    public static final int MAX_TOTAL_CHUNKS = (MAX_ZIP_BYTES + MAX_CHUNK_BYTES - 1) / MAX_CHUNK_BYTES;

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
        if (totalChunks < 1 || totalChunks > MAX_TOTAL_CHUNKS
            || chunkIndex < 0
            || chunkIndex >= totalChunks
            || isStart != (chunkIndex == 0)
            || isEnd != (chunkIndex == totalChunks - 1)
            || data == null
            || data.length == 0
            || data.length > MAX_CHUNK_BYTES) {
            throw new IllegalArgumentException("Icon pull chunk exceeds packet limit");
        }
        buf.writeInt(data.length);
        buf.writeBytes(data);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        valid = true;
        try {
            isStart = buf.readBoolean();
            isEnd = buf.readBoolean();
            chunkIndex = buf.readInt();
            totalChunks = buf.readInt();
            packName = NetworkPacketCodec.readUtf8(buf, MAX_PACK_NAME_BYTES);
            data = NetworkPacketCodec.readBytes(buf, MAX_CHUNK_BYTES);
            if (totalChunks < 1 || totalChunks > MAX_TOTAL_CHUNKS
                || chunkIndex < 0
                || chunkIndex >= totalChunks
                || isStart != (chunkIndex == 0)
                || isEnd != (chunkIndex == totalChunks - 1)
                || data.length == 0) {
                throw new IllegalArgumentException("Invalid icon pull chunk");
            }
            if (buf.isReadable()) {
                throw new IllegalArgumentException("Icon pull chunk has trailing bytes");
            }
        } catch (RuntimeException e) {
            valid = false;
            data = new byte[0];
        }
    }

    private static void writeUtf8(ByteBuf buf, String s) {
        if (s == null) {
            buf.writeInt(0);
            return;
        }
        byte[] bytes = s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (bytes.length > MAX_PACK_NAME_BYTES) {
            throw new IllegalArgumentException("Icon pull pack name exceeds packet limit");
        }
        buf.writeInt(bytes.length);
        buf.writeBytes(bytes);
    }

    @SideOnly(Side.CLIENT)
    public static class Handler implements IMessageHandler<PacketWebIconPullZip, IMessage> {

        @Override
        public IMessage onMessage(final PacketWebIconPullZip message, MessageContext ctx) {
            if (message == null || !message.valid) return null;
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
