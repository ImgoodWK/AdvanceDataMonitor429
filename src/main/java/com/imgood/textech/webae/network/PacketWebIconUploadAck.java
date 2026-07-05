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
 * S→C server acknowledges icon upload progress.
 *
 * Packet ID 29. Mirrors {@link PacketWebRecipeUploadAck}.
 */
public class PacketWebIconUploadAck implements IMessage {

    public boolean success;
    public int receivedChunks;
    public int totalChunks;
    public String message;

    public PacketWebIconUploadAck() {}

    public PacketWebIconUploadAck(boolean success, int receivedChunks, int totalChunks, String message) {
        this.success = success;
        this.receivedChunks = receivedChunks;
        this.totalChunks = totalChunks;
        this.message = message;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(success);
        buf.writeInt(receivedChunks);
        buf.writeInt(totalChunks);
        byte[] msgBytes = message != null ? message.getBytes(StandardCharsets.UTF_8) : new byte[0];
        buf.writeInt(msgBytes.length);
        buf.writeBytes(msgBytes);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        success = buf.readBoolean();
        receivedChunks = buf.readInt();
        totalChunks = buf.readInt();
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
     * Client-side handler: displays icon upload progress in chat.
     */
    @SideOnly(Side.CLIENT)
    public static class Handler implements IMessageHandler<PacketWebIconUploadAck, IMessage> {

        @Override
        public IMessage onMessage(PacketWebIconUploadAck message, MessageContext ctx) {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.thePlayer == null) return null;

            EnumChatFormatting color = message.success ? EnumChatFormatting.GREEN : EnumChatFormatting.RED;
            String prefix = message.success ? "[WebAE] " : "[WebAE Error] ";
            boolean isFinalChunk = message.receivedChunks >= message.totalChunks && message.totalChunks > 0;
            boolean isError = !message.success;
            boolean isCompletion = isFinalChunk && message.message != null
                && message.message.startsWith("Icon upload complete");
            if (isError || isCompletion) {
                mc.thePlayer.addChatMessage(new ChatComponentText(color + prefix + message.message));
            }

            if (!message.success) {
                AdvanceDataMonitor.LOG.warn("[WebAE] Icon upload error: {}", message.message);
            }

            return null;
        }
    }
}
