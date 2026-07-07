package com.imgood.textech.webae.network;

import java.nio.charset.StandardCharsets;

import net.minecraft.client.Minecraft;

import com.imgood.textech.AdvanceDataMonitor;
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

    public PacketWebIconRequest() {}

    public PacketWebIconRequest(String packName, String renderMode, String itemId) {
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

    @SideOnly(Side.CLIENT)
    public static class Handler implements IMessageHandler<PacketWebIconRequest, IMessage> {

        @Override
        public IMessage onMessage(final PacketWebIconRequest message, MessageContext ctx) {
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
                AdvanceDataMonitor.ADMCHANEL.sendToServer(
                    new PacketWebIconResolveNack(pack, mode, message.itemId));
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
