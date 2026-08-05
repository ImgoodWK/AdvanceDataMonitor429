package com.imgood.textech.webae.network;

import java.nio.charset.StandardCharsets;

import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.utils.NetworkPacketCodec;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

/**
 * S→C server acknowledges icon upload progress.
 *
 * Packet ID 29. Mirrors {@link PacketWebRecipeUploadAck}.
 */
public class PacketWebIconUploadAck implements IMessage {

    public boolean success;
    public int receivedChunks;
    public int totalChunks;
    public String message;
    public boolean malformed;

    private static final int MAX_MESSAGE_BYTES = 8 * 1024;
    private static final int MAX_PACKET_BYTES = 30_000;

    public PacketWebIconUploadAck() {}

    public PacketWebIconUploadAck(boolean success, int receivedChunks, int totalChunks, String message) {
        this.success = success;
        this.receivedChunks = receivedChunks;
        this.totalChunks = totalChunks;
        this.message = message;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        int start = buf.writerIndex();
        try {
            buf.writeBoolean(success);
            buf.writeInt(receivedChunks);
            buf.writeInt(totalChunks);
            byte[] msgBytes = message != null ? message.getBytes(StandardCharsets.UTF_8) : new byte[0];
            if (msgBytes.length > MAX_MESSAGE_BYTES) {
                throw new IllegalArgumentException("Icon upload acknowledgement exceeds packet limit");
            }
            buf.writeInt(msgBytes.length);
            buf.writeBytes(msgBytes);
            requirePacketBudget(buf, start);
        } catch (RuntimeException e) {
            buf.writerIndex(start);
            throw e;
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        malformed = false;
        try {
            if (buf == null || buf.readableBytes() > MAX_PACKET_BYTES) {
                throw new IllegalArgumentException("Icon upload acknowledgement exceeds packet budget");
            }
            success = buf.readBoolean();
            receivedChunks = buf.readInt();
            totalChunks = buf.readInt();
            message = NetworkPacketCodec.readUtf8(buf, MAX_MESSAGE_BYTES);
            if (buf.isReadable()) {
                throw new IllegalArgumentException("Icon upload acknowledgement has trailing bytes");
            }
        } catch (RuntimeException e) {
            malformed = true;
            message = "";
        }
    }

    private static void requirePacketBudget(ByteBuf buf, int start) {
        if (buf.writerIndex() - start > MAX_PACKET_BYTES) {
            throw new IllegalArgumentException("Icon upload acknowledgement exceeds packet budget");
        }
    }

    /**
     * Client-side handler: displays icon upload progress in chat.
     */
    @SideOnly(Side.CLIENT)
    public static class Handler implements IMessageHandler<PacketWebIconUploadAck, IMessage> {

        @Override
        public IMessage onMessage(PacketWebIconUploadAck message, MessageContext ctx) {
            if (message == null || message.malformed) return null;
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.thePlayer == null) return null;

            EnumChatFormatting color = message.success ? EnumChatFormatting.GREEN : EnumChatFormatting.RED;
            String prefix = message.success ? "[WebAE] " : "[WebAE Error] ";
            boolean isFinalChunk = message.receivedChunks >= message.totalChunks && message.totalChunks > 0;
            boolean isError = !message.success;
            boolean isCompletion = isFinalChunk && message.message != null
                && message.message.startsWith("Icon upload complete");
            boolean isLazySingle = isCompletion && message.message.startsWith("Icon upload complete. 1 icons");
            if (isError || (isCompletion && !isLazySingle)) {
                mc.thePlayer.addChatMessage(new ChatComponentText(color + prefix + message.message));
            }

            if (!message.success) {
                AdvanceDataMonitor.LOG.warn("[WebAE] Icon upload error: {}", message.message);
            }

            return null;
        }
    }
}
