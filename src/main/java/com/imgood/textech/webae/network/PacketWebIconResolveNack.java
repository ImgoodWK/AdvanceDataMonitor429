package com.imgood.textech.webae.network;

import java.nio.charset.StandardCharsets;

import com.imgood.textech.webae.icon.IconMissingQueue;
import com.imgood.textech.webae.icon.IconRenderMode;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * C→S: icon-provider client could not resolve an item id to a renderable stack.
 * Packet ID 36.
 */
public class PacketWebIconResolveNack implements IMessage {

    public String packName;
    public String renderMode;
    public String itemId;

    public PacketWebIconResolveNack() {}

    public PacketWebIconResolveNack(String packName, String renderMode, String itemId) {
        this.packName = packName;
        this.renderMode = renderMode;
        this.itemId = itemId;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        writeUtf8(buf, packName);
        writeUtf8(buf, renderMode);
        writeUtf8(buf, itemId);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        packName = readUtf8(buf);
        renderMode = readUtf8(buf);
        itemId = readUtf8(buf);
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

    public static class Handler implements IMessageHandler<PacketWebIconResolveNack, IMessage> {

        @Override
        public IMessage onMessage(PacketWebIconResolveNack message, MessageContext ctx) {
            if (message == null || message.itemId == null || message.itemId.isEmpty()) {
                return null;
            }
            String pack = message.packName != null && !message.packName.isEmpty() ? message.packName : "default";
            String mode = message.renderMode != null && !message.renderMode.isEmpty() ? message.renderMode
                : IconRenderMode.NEI.getId();
            IconMissingQueue.instance()
                .markUnresolvable(pack, mode, message.itemId);
            return null;
        }
    }
}
