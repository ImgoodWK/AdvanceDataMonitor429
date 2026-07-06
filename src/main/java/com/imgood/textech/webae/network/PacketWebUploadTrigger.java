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
import com.imgood.textech.webae.icon.IconRenderMode;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

/**
 * S→C server tells the client to begin a WebAE upload that requires client-side
 * APIs (NEI recipe collection or OpenGL icon rendering).
 *
 * Packet ID 30. Triggered by {@code /admweb recipes upload} and
 * {@code /admweb icons upload} when an OP runs the command. The client handler
 * dispatches to the existing upload entry points.
 *
 * Fields:
 * - uploadType: "recipes" or "icons"
 * - packName: target icon pack name (ignored for recipes)
 */
public class PacketWebUploadTrigger implements IMessage {

    public static final String TYPE_RECIPES = "recipes";
    public static final String TYPE_ICONS = "icons";
    public static final String TYPE_ICON_VERIFY = "icon_verify";

    public String uploadType;
    public String packName;
    /** Icon render mode id, or {@code all} for sequential multi-mode export. Empty → nei. */
    public String renderMode;
    /** Optional export scope id ({@link IconExportScope}); empty → client pending / all. */
    public String exportScope;
    /** JSON array of item ids when {@code exportScope} is list or snapshot. */
    public String itemIdsJson;

    private static final Gson GSON = new com.google.gson.GsonBuilder().create();

    public PacketWebUploadTrigger() {}

    public PacketWebUploadTrigger(String uploadType, String packName) {
        this(uploadType, packName, IconRenderMode.NEI.getId());
    }

    public PacketWebUploadTrigger(String uploadType, String packName, String renderMode) {
        this(uploadType, packName, renderMode, null, null);
    }

    public PacketWebUploadTrigger(String uploadType, String packName, String renderMode,
        IconExportScope scope, List<String> itemIds) {
        this.uploadType = uploadType;
        this.packName = packName;
        this.renderMode = renderMode;
        this.exportScope = scope != null ? scope.getId() : "";
        if (itemIds != null && !itemIds.isEmpty()) {
            this.itemIdsJson = GSON.toJson(itemIds);
        } else {
            this.itemIdsJson = "";
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        writeUtf8(buf, uploadType);
        writeUtf8(buf, packName);
        writeUtf8(buf, renderMode);
        writeUtf8(buf, exportScope);
        writeUtf8(buf, itemIdsJson);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        uploadType = readUtf8(buf);
        packName = readUtf8(buf);
        renderMode = readUtf8(buf);
        if (buf.isReadable()) {
            exportScope = readUtf8(buf);
            itemIdsJson = readUtf8(buf);
        } else {
            exportScope = "";
            itemIdsJson = "";
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

    /**
     * Client-side handler: invokes the existing upload entry points.
     */
    @SideOnly(Side.CLIENT)
    public static class Handler implements IMessageHandler<PacketWebUploadTrigger, IMessage> {

        @Override
        public IMessage onMessage(final PacketWebUploadTrigger message, MessageContext ctx) {
            scheduleOnClientThread(new Runnable() {

                @Override
                public void run() {
                    handleOnMainThread(message);
                }
            });
            return null;
        }

        @SideOnly(Side.CLIENT)
        private static void handleOnMainThread(PacketWebUploadTrigger message) {
            try {
                com.imgood.textech.webae.debug.WebAeDebugLog.info(
                    com.imgood.textech.webae.debug.WebAeDebugLog.Feature.ICONS,
                    "upload trigger on client main thread: uploadType={} packName={} thread={}",
                    message.uploadType != null ? message.uploadType : "null",
                    message.packName != null ? message.packName : "",
                    Thread.currentThread()
                        .getName());
                if (message.uploadType == null) return;
                if (TYPE_RECIPES.equalsIgnoreCase(message.uploadType)) {
                    String scope = message.renderMode != null && !message.renderMode.isEmpty() ? message.renderMode
                        : "full";
                    List<String> snapshotIds = parseSnapshotItemIds(message.packName);
                    KeyBindings.uploadNeiRecipes(scope, snapshotIds);
                } else if (TYPE_ICONS.equalsIgnoreCase(message.uploadType)) {
                    String pack = (message.packName != null && !message.packName.isEmpty()) ? message.packName
                        : "default";
                    String mode = (message.renderMode != null && !message.renderMode.isEmpty()) ? message.renderMode
                        : IconRenderMode.NEI.getId();
                    IconExportScope scope = null;
                    List<String> itemIds = null;
                    if (message.exportScope != null && !message.exportScope.isEmpty()) {
                        scope = IconExportScope.fromId(message.exportScope);
                        itemIds = PacketWebIconExportScope.parseItemIds(message.itemIdsJson);
                    }
                    KeyBindings.triggerIconUpload(pack, mode, scope, itemIds);
                } else if (TYPE_ICON_VERIFY.equalsIgnoreCase(message.uploadType)) {
                    String pack = (message.packName != null && !message.packName.isEmpty()) ? message.packName
                        : "default";
                    String itemId = message.renderMode != null ? message.renderMode : "";
                    KeyBindings.openIconVerify(pack, itemId);
                } else {
                    AdvanceDataMonitor.LOG.warn("[WebAE] Unknown upload trigger type: {}", message.uploadType);
                }
            } catch (Throwable t) {
                AdvanceDataMonitor.LOG.error("[WebAE] Failed to handle upload trigger packet", t);
            }
        }

        @SideOnly(Side.CLIENT)
        private static List<String> parseSnapshotItemIds(String packName) {
            if (packName == null || packName.isEmpty()) {
                return null;
            }
            try {
                List<String> ids = new Gson().fromJson(packName, new TypeToken<List<String>>() {}.getType());
                return ids != null ? ids : new ArrayList<String>();
            } catch (Exception e) {
                return null;
            }
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
