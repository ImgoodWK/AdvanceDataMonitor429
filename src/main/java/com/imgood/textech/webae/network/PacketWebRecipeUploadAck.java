package com.imgood.textech.webae.network;

import java.nio.charset.StandardCharsets;

import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import com.imgood.textech.AdvanceDataMonitor;

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

    public PacketWebRecipeUploadAck() {}

    public PacketWebRecipeUploadAck(boolean success, int receivedBatches, int totalBatches, String message) {
        this.success = success;
        this.receivedBatches = receivedBatches;
        this.totalBatches = totalBatches;
        this.message = message;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(success);
        buf.writeInt(receivedBatches);
        buf.writeInt(totalBatches);
        byte[] msgBytes = message != null ? message.getBytes(StandardCharsets.UTF_8) : new byte[0];
        buf.writeInt(msgBytes.length);
        buf.writeBytes(msgBytes);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        success = buf.readBoolean();
        receivedBatches = buf.readInt();
        totalBatches = buf.readInt();
        int msgLen = buf.readInt();
        if (msgLen > 0) {
            byte[] msgBytes = new byte[msgLen];
            buf.readBytes(msgBytes);
            message = new String(msgBytes, StandardCharsets.UTF_8);
        } else {
            message = "";
        }
    }

    /**
     * Client-side handler: displays upload progress in chat.
     */
    @SideOnly(Side.CLIENT)
    public static class Handler implements IMessageHandler<PacketWebRecipeUploadAck, IMessage> {

        @Override
        public IMessage onMessage(PacketWebRecipeUploadAck message, MessageContext ctx) {
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
