package com.imgood.textech.webae.network;

import net.minecraft.client.Minecraft;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.utils.NetworkPacketCodec;
import com.imgood.textech.webae.icon.IconItemEnumerator;
import com.imgood.textech.webae.icon.IconLazyRenderQueue;
import com.imgood.textech.webae.icon.IconRenderMode;
import com.imgood.textech.webae.icon.IconRenderer;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

/**
 * S→C: ask the icon-provider client to render and upload a single missing icon.
 * Packet ID 33.
 */
public class PacketWebIconRequest implements IMessage {

    public String packName;
    public String renderMode;
    public String itemId;
    private boolean valid = true;

    private static final int MAX_PACK_NAME_BYTES = 128;
    private static final int MAX_RENDER_MODE_BYTES = 32;
    private static final int MAX_ITEM_ID_BYTES = 256;
    private static final int MAX_PACKET_BYTES = 30_000;

    public PacketWebIconRequest() {}

    public PacketWebIconRequest(String packName, String renderMode, String itemId) {
        this.packName = packName;
        this.renderMode = renderMode;
        this.itemId = itemId;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        int start = buf.writerIndex();
        try {
            if (!IconRenderMode.isValidModeId(renderMode)) {
                throw new IllegalArgumentException("Invalid icon request render mode");
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
                throw new IllegalArgumentException("Icon request exceeds packet budget");
            }
            packName = NetworkPacketCodec.readUtf8(buf, MAX_PACK_NAME_BYTES);
            renderMode = NetworkPacketCodec.readUtf8(buf, MAX_RENDER_MODE_BYTES);
            if (!IconRenderMode.isValidModeId(renderMode)) {
                throw new IllegalArgumentException("Invalid icon request render mode");
            }
            itemId = NetworkPacketCodec.readUtf8(buf, MAX_ITEM_ID_BYTES);
            if (buf.isReadable()) {
                throw new IllegalArgumentException("Icon request has trailing bytes");
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
            throw new IllegalArgumentException("Icon request field exceeds packet limit");
        }
        buf.writeInt(bytes.length);
        buf.writeBytes(bytes);
    }

    private static void requirePacketBudget(ByteBuf buf, int start) {
        if (buf.writerIndex() - start > MAX_PACKET_BYTES) {
            throw new IllegalArgumentException("Icon request exceeds packet budget");
        }
    }

    @SideOnly(Side.CLIENT)
    public static class Handler implements IMessageHandler<PacketWebIconRequest, IMessage> {

        @Override
        public IMessage onMessage(final PacketWebIconRequest message, MessageContext ctx) {
            if (message == null || !message.valid) return null;
            scheduleOnClientThread(new Runnable() {

                @Override
                public void run() {
                    handleOnMainThread(message);
                }
            });
            return null;
        }

        @SideOnly(Side.CLIENT)
        private static void handleOnMainThread(PacketWebIconRequest message) {
            if (message == null || message.itemId == null || message.itemId.isEmpty()) return;
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.thePlayer == null) return;
            if (IconRenderer.instance()
                .isRunning()) {
                AdvanceDataMonitor.LOG.debug("[WebAE] Ignoring icon request while bulk export running");
                return;
            }
            String pack = message.packName != null && !message.packName.isEmpty() ? message.packName : "default";
            String mode = message.renderMode != null && !message.renderMode.isEmpty() ? message.renderMode
                : IconRenderMode.NEI.getId();
            IconItemEnumerator.StackTask task = resolveTask(message.itemId);
            if (task == null) {
                AdvanceDataMonitor.LOG.debug("[WebAE] Icon request could not resolve stack: {}", message.itemId);
                AdvanceDataMonitor.ADMCHANEL.sendToServer(new PacketWebIconResolveNack(pack, mode, message.itemId));
                return;
            }
            IconLazyRenderQueue.instance()
                .enqueue(pack, mode, task);
        }

        @SideOnly(Side.CLIENT)
        private static IconItemEnumerator.StackTask resolveTask(String itemId) {
            return IconItemEnumerator.resolveSingle(itemId);
        }

        @SideOnly(Side.CLIENT)
        private static void scheduleOnClientThread(Runnable runnable) {
            try {
                Minecraft mc = Minecraft.getMinecraft();
                mc.getClass()
                    .getMethod("func_152344_a", Runnable.class)
                    .invoke(mc, runnable);
            } catch (Exception e) {
                runnable.run();
            }
        }
    }
}
