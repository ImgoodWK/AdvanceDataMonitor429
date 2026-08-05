package com.imgood.textech.webae.network;

import net.minecraft.entity.player.EntityPlayerMP;

import com.imgood.textech.network.handler.PacketHandlers;
import com.imgood.textech.utils.NetworkPacketCodec;
import com.imgood.textech.webae.icon.IconMissingQueue;
import com.imgood.textech.webae.icon.IconStore;
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
    private boolean valid = true;

    private static final int MAX_PACK_NAME_BYTES = 128;
    private static final int MAX_RENDER_MODE_BYTES = 32;
    private static final int MAX_ITEM_ID_BYTES = 512;
    private static final int MAX_PACKET_BYTES = 30_000;

    public PacketWebIconResolveNack() {}

    public PacketWebIconResolveNack(String packName, String renderMode, String itemId) {
        this.packName = packName;
        this.renderMode = renderMode;
        this.itemId = itemId;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        int start = buf.writerIndex();
        try {
            if (!IconRenderMode.isValidModeId(renderMode)) {
                throw new IllegalArgumentException("Invalid icon resolve render mode");
            }
            writeUtf8(buf, packName, MAX_PACK_NAME_BYTES);
            writeUtf8(buf, renderMode, MAX_RENDER_MODE_BYTES);
            writeUtf8(buf, itemId, MAX_ITEM_ID_BYTES);
            requirePacketBudget(buf, start);
        } catch (RuntimeException e) {
            buf.writerIndex(start);
            throw e;
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        valid = true;
        try {
            if (buf == null || buf.readableBytes() > MAX_PACKET_BYTES) {
                throw new IllegalArgumentException("Icon resolve packet exceeds packet budget");
            }
            packName = NetworkPacketCodec.readUtf8(buf, MAX_PACK_NAME_BYTES);
            renderMode = NetworkPacketCodec.readUtf8(buf, MAX_RENDER_MODE_BYTES);
            if (!IconRenderMode.isValidModeId(renderMode)) {
                throw new IllegalArgumentException("Invalid icon resolve render mode");
            }
            itemId = NetworkPacketCodec.readUtf8(buf, MAX_ITEM_ID_BYTES);
            if (buf.isReadable()) {
                throw new IllegalArgumentException("Icon resolve packet has trailing bytes");
            }
        } catch (RuntimeException e) {
            valid = false;
            itemId = "";
        }
    }

    private static void writeUtf8(ByteBuf buf, String s, int maxBytes) {
        if (s == null) {
            buf.writeInt(0);
            return;
        }
        byte[] bytes = s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (bytes.length > maxBytes) {
            throw new IllegalArgumentException("Icon resolve field exceeds packet limit");
        }
        buf.writeInt(bytes.length);
        buf.writeBytes(bytes);
    }

    private static void requirePacketBudget(ByteBuf buf, int start) {
        if (buf.writerIndex() - start > MAX_PACKET_BYTES) {
            throw new IllegalArgumentException("Icon resolve packet exceeds packet budget");
        }
    }

    public static class Handler implements IMessageHandler<PacketWebIconResolveNack, IMessage> {

        @Override
        public IMessage onMessage(final PacketWebIconResolveNack message, MessageContext ctx) {
            final EntityPlayerMP player = ctx == null || ctx.getServerHandler() == null ? null
                : ctx.getServerHandler().playerEntity;
            if (message == null || !message.valid || player == null) {
                return null;
            }
            return PacketHandlers.runOnServer(ctx, new Runnable() {

                @Override
                public void run() {
                    if (message.itemId == null || message.itemId.isEmpty()
                        || !IconStore.isValidItemId(message.itemId)
                        || !IconMissingQueue.instance()
                            .isConsentedProvider(player)) {
                        return;
                    }
                    String pack = message.packName != null && !message.packName.isEmpty() ? message.packName
                        : "default";
                    String mode = message.renderMode != null && !message.renderMode.isEmpty() ? message.renderMode
                        : IconRenderMode.NEI.getId();
                    if (!IconStore.isValidPackName(pack)) {
                        return;
                    }
                    mode = IconStore.normalizeModeId(mode);
                    if (!IconStore.isValidModeDirName(mode)) {
                        return;
                    }
                    IconMissingQueue.instance()
                        .markUnresolvable(pack, mode, message.itemId);
                }
            });
        }
    }
}
