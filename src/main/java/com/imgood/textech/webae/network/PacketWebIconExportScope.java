package com.imgood.textech.webae.network;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.client.KeyBindings;
import com.imgood.textech.webae.icon.IconExportScope;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

/**
 * S→C: supplies export scope and optional explicit item id list before icon upload starts.
 * Packet ID 32.
 */
public class PacketWebIconExportScope implements IMessage {

    private static final Gson GSON = new com.google.gson.GsonBuilder().create();

    public String exportScope;
    public String itemIdsJson;

    public PacketWebIconExportScope() {}

    public PacketWebIconExportScope(IconExportScope scope, List<String> itemIds) {
        this.exportScope = scope != null ? scope.getId() : IconExportScope.ALL.getId();
        if (itemIds != null && !itemIds.isEmpty()) {
            this.itemIdsJson = GSON.toJson(itemIds);
        } else {
            this.itemIdsJson = "";
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        writeUtf8(buf, exportScope);
        writeUtf8(buf, itemIdsJson);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        exportScope = readUtf8(buf);
        itemIdsJson = readUtf8(buf);
    }

    @SideOnly(Side.CLIENT)
    public static List<String> parseItemIds(String json) {
        if (json == null || json.isEmpty()) return new ArrayList<String>();
        try {
            List<String> ids = GSON.fromJson(json, new TypeToken<List<String>>() {}.getType());
            return ids != null ? ids : new ArrayList<String>();
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.warn("[WebAE] Failed to parse icon export scope item ids", e);
            return new ArrayList<String>();
        }
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
    public static class Handler implements IMessageHandler<PacketWebIconExportScope, IMessage> {

        @Override
        public IMessage onMessage(final PacketWebIconExportScope message, MessageContext ctx) {
            scheduleOnClientThread(new Runnable() {

                @Override
                public void run() {
                    IconExportScope scope = IconExportScope.fromId(message.exportScope);
                    List<String> ids = parseItemIds(message.itemIdsJson);
                    KeyBindings.setPendingIconExportScope(scope, ids);
                }
            });
            return null;
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
