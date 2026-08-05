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
 * S→C server acknowledges recipe upload progress.
 *
 * Fields:
 * - success: whether the batch was processed successfully
 * - receivedBatches: number of batches received so far
 * - totalBatches: total number of batches expected
 * - message: human-readable status message
 */
public class PacketWebRecipeUploadAck implements IMessage {

    public boolean success;
    public int receivedBatches;
    public int totalBatches;
    public String message;
    public boolean malformed;

    private static final int MAX_MESSAGE_BYTES = 8 * 1024;
    private static final int MAX_PACKET_BYTES = 30_000;

    public PacketWebRecipeUploadAck() {}

    public PacketWebRecipeUploadAck(boolean success, int receivedBatches, int totalBatches, String message) {
        this.success = success;
        this.receivedBatches = receivedBatches;
        this.totalBatches = totalBatches;
        this.message = message;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        int start = buf.writerIndex();
        try {
            buf.writeBoolean(success);
            buf.writeInt(receivedBatches);
            buf.writeInt(totalBatches);
            byte[] msgBytes = message != null ? message.getBytes(StandardCharsets.UTF_8) : new byte[0];
            if (msgBytes.length > MAX_MESSAGE_BYTES) {
                throw new IllegalArgumentException("Recipe upload acknowledgement exceeds packet limit");
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
                throw new IllegalArgumentException("Recipe upload acknowledgement exceeds packet budget");
            }
            success = buf.readBoolean();
            receivedBatches = buf.readInt();
            totalBatches = buf.readInt();
            message = NetworkPacketCodec.readUtf8(buf, MAX_MESSAGE_BYTES);
            if (buf.isReadable()) {
                throw new IllegalArgumentException("Recipe upload acknowledgement has trailing bytes");
            }
        } catch (RuntimeException e) {
            malformed = true;
            message = "";
        }
    }

    private static void requirePacketBudget(ByteBuf buf, int start) {
        if (buf.writerIndex() - start > MAX_PACKET_BYTES) {
            throw new IllegalArgumentException("Recipe upload acknowledgement exceeds packet budget");
        }
    }

    /**
     * Client-side handler: displays upload progress in chat.
     */
    @SideOnly(Side.CLIENT)
    public static class Handler implements IMessageHandler<PacketWebRecipeUploadAck, IMessage> {

        @Override
        public IMessage onMessage(PacketWebRecipeUploadAck message, MessageContext ctx) {
            if (message == null || message.malformed) return null;
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.thePlayer == null) return null;

            EnumChatFormatting color = message.success ? EnumChatFormatting.GREEN : EnumChatFormatting.RED;
            String prefix = message.success ? "[WebAE] " : "[WebAE Error] ";
            mc.thePlayer.addChatMessage(new ChatComponentText(color + prefix + message.message));

            if (!message.success) {
                AdvanceDataMonitor.LOG.warn("[WebAE] Recipe upload error: {}", message.message);
            }

            return null;
        }
    }
}
