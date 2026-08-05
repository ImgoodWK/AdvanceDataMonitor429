package com.imgood.textech.webae.network;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.imgood.textech.client.KeyBindings;
import com.imgood.textech.utils.NetworkPacketCodec;
import com.imgood.textech.webae.icon.IconExportScope;
import com.imgood.textech.webae.icon.IconStore;

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
    private boolean valid = true;

    /** Keep a safety margin below FML's 32767-byte custom-payload limit. */
    public static final int MAX_PACKET_BODY_BYTES = 30000;
    private static final int MAX_SCOPE_BYTES = 32;
    private static final int MAX_ITEM_IDS_JSON_BYTES = MAX_PACKET_BODY_BYTES - 4 - MAX_SCOPE_BYTES - 4;
    public static final int MAX_ITEM_IDS = 4096;
    public static final int MAX_ITEM_ID_BYTES = 256;

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
        byte[] scopeBytes = utf8(exportScope);
        byte[] itemIdsBytes = utf8(itemIdsJson);
        if (scopeBytes.length > MAX_SCOPE_BYTES || itemIdsBytes.length > MAX_ITEM_IDS_JSON_BYTES
            || 4 + scopeBytes.length + 4 + itemIdsBytes.length > MAX_PACKET_BODY_BYTES) {
            throw new IllegalArgumentException("Icon export scope exceeds packet limit");
        }
        writeUtf8(buf, scopeBytes);
        writeUtf8(buf, itemIdsBytes);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        valid = true;
        try {
            int start = buf.readerIndex();
            exportScope = NetworkPacketCodec.readUtf8(
                buf,
                Math.min(MAX_SCOPE_BYTES, MAX_PACKET_BODY_BYTES - 8));
            int consumed = buf.readerIndex() - start;
            itemIdsJson = NetworkPacketCodec.readUtf8(
                buf,
                Math.min(MAX_ITEM_IDS_JSON_BYTES, MAX_PACKET_BODY_BYTES - consumed - 4));
            if (buf.readerIndex() - start > MAX_PACKET_BODY_BYTES || buf.isReadable()) {
                throw new IllegalArgumentException("Icon export scope has trailing or oversized payload");
            }
        } catch (RuntimeException e) {
            valid = false;
            exportScope = "";
            itemIdsJson = "";
        }
    }

    @SideOnly(Side.CLIENT)
    public static List<String> parseItemIds(String json) {
        if (json == null || json.isEmpty()) return new ArrayList<String>();
        if (json.getBytes(StandardCharsets.UTF_8).length > MAX_ITEM_IDS_JSON_BYTES) {
            return invalidItemIdList();
        }
        try {
            JsonElement root = new JsonParser().parse(json);
            if (root == null || !root.isJsonArray()) return invalidItemIdList();
            JsonArray array = root.getAsJsonArray();
            if (array.size() > MAX_ITEM_IDS) return invalidItemIdList();
            List<String> ids = new ArrayList<String>(array.size());
            for (JsonElement element : array) {
                if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
                    return invalidItemIdList();
                }
                String itemId = element.getAsString();
                if (!isValidItemId(itemId)) return invalidItemIdList();
                ids.add(itemId);
            }
            return ids;
        } catch (RuntimeException e) {
            return invalidItemIdList();
        }
    }

    private static List<String> invalidItemIdList() {
        return null;
    }

    private static boolean isValidItemId(String itemId) {
        if (itemId == null || itemId.isEmpty()) return false;
        for (int i = 0; i < itemId.length(); i++) {
            char c = itemId.charAt(i);
            if (Character.isISOControl(c)) return false;
            if (Character.isHighSurrogate(c)) {
                if (i + 1 >= itemId.length() || !Character.isLowSurrogate(itemId.charAt(++i))) return false;
            } else if (Character.isLowSurrogate(c)) {
                return false;
            }
        }
        return IconStore.isValidItemId(itemId)
            && itemId.getBytes(StandardCharsets.UTF_8).length <= MAX_ITEM_ID_BYTES;
    }

    private static byte[] utf8(String value) {
        return (value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
    }

    private static void writeUtf8(ByteBuf buf, byte[] bytes) {
        buf.writeInt(bytes.length);
        if (bytes.length > 0) buf.writeBytes(bytes);
    }

    @SideOnly(Side.CLIENT)
    public static class Handler implements IMessageHandler<PacketWebIconExportScope, IMessage> {

        @Override
        public IMessage onMessage(final PacketWebIconExportScope message, MessageContext ctx) {
            if (message == null || !message.valid) return null;
            scheduleOnClientThread(new Runnable() {

                @Override
                public void run() {
                    IconExportScope scope = IconExportScope.fromId(message.exportScope);
                    List<String> ids = parseItemIds(message.itemIdsJson);
                    if (ids == null) return;
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
