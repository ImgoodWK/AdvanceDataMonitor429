package com.imgood.textech.webae.network;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;

import com.google.gson.Gson;
import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.client.KeyBindings;
import com.imgood.textech.utils.NetworkPacketCodec;
import com.imgood.textech.webae.icon.IconExportScope;
import com.imgood.textech.webae.icon.IconRenderMode;
import com.imgood.textech.webae.icon.IconStore;

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
    public static final String TYPE_ICONS_LOCAL = "icons_local";
    public static final String TYPE_ICON_VERIFY = "icon_verify";
    public static final String TYPE_ICONS_PULL = "icons_pull";

    public String uploadType;
    public String packName;
    /** Icon render mode id, or {@code all} for sequential multi-mode export. Empty → nei. */
    public String renderMode;
    /** Optional export scope id ({@link IconExportScope}); empty → client pending / all. */
    public String exportScope;
    /** JSON array of item ids when {@code exportScope} is list or snapshot. */
    public String itemIdsJson;
    /** Zero-based item-id batch index; a single packet uses {@code 0/1}. */
    public int batchIndex;
    /** Total item-id batches; a single packet uses {@code 1}. */
    public int batchCount = 1;
    private boolean valid = true;

    /** Keep a safety margin below FML's 32767-byte custom-payload limit. */
    public static final int MAX_PACKET_BODY_BYTES = 30000;
    private static final int MAX_UPLOAD_TYPE_BYTES = 32;
    private static final int MAX_PACK_NAME_BYTES = MAX_PACKET_BODY_BYTES - (5 * 4) - 8;
    private static final int MAX_RENDER_MODE_BYTES = 32;
    private static final int MAX_EXPORT_SCOPE_BYTES = 32;
    private static final int MAX_ITEM_IDS_JSON_BYTES = MAX_PACKET_BODY_BYTES - (5 * 4) - 8;
    private static final int MAX_BATCH_COUNT = PacketWebIconExportScope.MAX_ITEM_IDS;

    private static final Gson GSON = new com.google.gson.GsonBuilder().create();

    public PacketWebUploadTrigger() {}

    public PacketWebUploadTrigger(String uploadType, String packName) {
        this(uploadType, packName, IconRenderMode.NEI.getId());
    }

    public PacketWebUploadTrigger(String uploadType, String packName, String renderMode) {
        this(uploadType, packName, renderMode, null, null);
    }

    public PacketWebUploadTrigger(String uploadType, String packName, String renderMode, IconExportScope scope,
        List<String> itemIds) {
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
        if (batchCount < 1 || batchCount > MAX_BATCH_COUNT || batchIndex < 0 || batchIndex >= batchCount) {
            throw new IllegalArgumentException("Invalid WebAE upload trigger batch metadata");
        }
        byte[] uploadTypeBytes = utf8(uploadType);
        byte[] packNameBytes = utf8(packName);
        byte[] renderModeBytes = utf8(renderMode);
        byte[] exportScopeBytes = utf8(exportScope);
        byte[] itemIdsBytes = utf8(itemIdsJson);
        int bodyBytes = 5 * 4 + 8
            + uploadTypeBytes.length
            + packNameBytes.length
            + renderModeBytes.length
            + exportScopeBytes.length
            + itemIdsBytes.length;
        if (uploadTypeBytes.length > MAX_UPLOAD_TYPE_BYTES || packNameBytes.length > MAX_PACK_NAME_BYTES
            || renderModeBytes.length > MAX_RENDER_MODE_BYTES
            || exportScopeBytes.length > MAX_EXPORT_SCOPE_BYTES
            || itemIdsBytes.length > MAX_ITEM_IDS_JSON_BYTES
            || bodyBytes > MAX_PACKET_BODY_BYTES) {
            throw new IllegalArgumentException("WebAE upload trigger exceeds packet limit");
        }
        writeUtf8(buf, uploadTypeBytes);
        writeUtf8(buf, packNameBytes);
        writeUtf8(buf, renderModeBytes);
        writeUtf8(buf, exportScopeBytes);
        writeUtf8(buf, itemIdsBytes);
        buf.writeInt(batchIndex);
        buf.writeInt(batchCount);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        valid = true;
        try {
            int start = buf.readerIndex();
            uploadType = readField(buf, start, MAX_UPLOAD_TYPE_BYTES, 4);
            packName = readField(buf, start, MAX_PACK_NAME_BYTES, 3);
            renderMode = readField(buf, start, MAX_RENDER_MODE_BYTES, 2);
            if (!buf.isReadable()) {
                // Preserve the original three-field trigger format used by older clients.
                exportScope = "";
                itemIdsJson = "";
                batchIndex = 0;
                batchCount = 1;
                return;
            }
            exportScope = readField(buf, start, MAX_EXPORT_SCOPE_BYTES, 1);
            itemIdsJson = readField(buf, start, MAX_ITEM_IDS_JSON_BYTES, 0);
            int remaining = buf.readableBytes();
            if (remaining == 0) {
                // Accept the pre-batch wire format from older servers.
                batchIndex = 0;
                batchCount = 1;
            } else if (remaining == 8) {
                batchIndex = buf.readInt();
                batchCount = buf.readInt();
            } else {
                throw new IllegalArgumentException("Invalid WebAE upload trigger batch framing");
            }
            if (buf.readerIndex() - start > MAX_PACKET_BODY_BYTES || buf.isReadable()
                || batchCount < 1
                || batchCount > MAX_BATCH_COUNT
                || batchIndex < 0
                || batchIndex >= batchCount) {
                throw new IllegalArgumentException("Invalid WebAE upload trigger payload");
            }
        } catch (RuntimeException e) {
            valid = false;
            uploadType = "";
            packName = "";
            renderMode = "";
            exportScope = "";
            itemIdsJson = "";
            batchIndex = 0;
            batchCount = 1;
        }
    }

    private static String readField(ByteBuf buf, int start, int maxBytes, int remainingFields) {
        int consumed = buf.readerIndex() - start;
        int budget = MAX_PACKET_BODY_BYTES - consumed - 4 - (remainingFields * 4) - 8;
        if (budget < 0) throw new IllegalArgumentException("WebAE upload trigger exceeds packet limit");
        return NetworkPacketCodec.readUtf8(buf, Math.min(maxBytes, budget));
    }

    private static byte[] utf8(String value) {
        return (value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
    }

    private static void writeUtf8(ByteBuf buf, byte[] bytes) {
        buf.writeInt(bytes.length);
        if (bytes.length > 0) buf.writeBytes(bytes);
    }

    /**
     * Build FML-safe trigger packets for recipe snapshots or icon scopes with explicit item ids.
     * The list is never truncated: an invalid item or an item that cannot fit alone aborts the
     * operation with an exception for the command caller to report.
     */
    public static List<PacketWebUploadTrigger> createItemIdBatches(String uploadType, String packName,
        String renderMode, IconExportScope scope, List<String> itemIds) {
        List<PacketWebUploadTrigger> result = new ArrayList<PacketWebUploadTrigger>();
        if (itemIds == null || itemIds.isEmpty()) {
            result.add(new PacketWebUploadTrigger(uploadType, packName, renderMode, scope, null));
            return result;
        }
        if (itemIds.size() > PacketWebIconExportScope.MAX_ITEM_IDS) {
            throw new IllegalArgumentException("Too many WebAE item ids");
        }

        List<String> current = new ArrayList<String>();
        for (String itemId : itemIds) {
            if (!validItemIdForPacket(itemId)) {
                throw new IllegalArgumentException("Invalid WebAE item id");
            }
            List<String> candidate = new ArrayList<String>(current);
            candidate.add(itemId);
            String candidateJson = GSON.toJson(candidate);
            if (!fitsItemIdBatch(uploadType, packName, renderMode, scope, candidateJson)) {
                if (current.isEmpty()) {
                    throw new IllegalArgumentException("WebAE item id batch cannot fit in one packet");
                }
                addItemIdBatch(result, uploadType, packName, renderMode, scope, current);
                current = new ArrayList<String>();
                current.add(itemId);
            } else {
                current.add(itemId);
            }
        }
        if (!current.isEmpty()) {
            addItemIdBatch(result, uploadType, packName, renderMode, scope, current);
        }
        if (result.isEmpty() || result.size() > MAX_BATCH_COUNT) {
            throw new IllegalArgumentException("Too many WebAE item id batches");
        }
        for (int i = 0; i < result.size(); i++) {
            result.get(i).batchIndex = i;
            result.get(i).batchCount = result.size();
        }
        return result;
    }

    private static void addItemIdBatch(List<PacketWebUploadTrigger> result, String uploadType, String packName,
        String renderMode, IconExportScope scope, List<String> itemIds) {
        String json = GSON.toJson(itemIds);
        PacketWebUploadTrigger packet;
        if (TYPE_RECIPES.equalsIgnoreCase(uploadType)) {
            // The legacy recipe trigger reuses packName for its snapshot JSON field.
            packet = new PacketWebUploadTrigger(uploadType, json, renderMode);
        } else {
            packet = new PacketWebUploadTrigger(uploadType, packName, renderMode, scope, itemIds);
        }
        if (!fitsPacket(
            packet.uploadType,
            packet.packName,
            packet.renderMode,
            packet.exportScope,
            packet.itemIdsJson)) {
            throw new IllegalArgumentException("WebAE item id batch exceeds packet limit");
        }
        result.add(packet);
    }

    private static boolean fitsItemIdBatch(String uploadType, String packName, String renderMode, IconExportScope scope,
        String itemIdsJson) {
        if (TYPE_RECIPES.equalsIgnoreCase(uploadType)) {
            return fitsPacket(uploadType, itemIdsJson, renderMode, "", "");
        }
        return fitsPacket(uploadType, packName, renderMode, scope == null ? "" : scope.getId(), itemIdsJson);
    }

    private static boolean fitsPacket(String uploadType, String packName, String renderMode, String exportScope,
        String itemIdsJson) {
        byte[] uploadTypeBytes = utf8(uploadType);
        byte[] packNameBytes = utf8(packName);
        byte[] renderModeBytes = utf8(renderMode);
        byte[] exportScopeBytes = utf8(exportScope);
        byte[] itemIdsBytes = utf8(itemIdsJson);
        return uploadTypeBytes.length <= MAX_UPLOAD_TYPE_BYTES && packNameBytes.length <= MAX_PACK_NAME_BYTES
            && renderModeBytes.length <= MAX_RENDER_MODE_BYTES
            && exportScopeBytes.length <= MAX_EXPORT_SCOPE_BYTES
            && itemIdsBytes.length <= MAX_ITEM_IDS_JSON_BYTES
            && 5 * 4 + 8
                + uploadTypeBytes.length
                + packNameBytes.length
                + renderModeBytes.length
                + exportScopeBytes.length
                + itemIdsBytes.length <= MAX_PACKET_BODY_BYTES;
    }

    private static boolean validItemIdForPacket(String itemId) {
        if (!IconStore.isValidItemId(itemId) || utf8(itemId).length > PacketWebIconExportScope.MAX_ITEM_ID_BYTES) {
            return false;
        }
        for (int i = 0; i < itemId.length(); i++) {
            char value = itemId.charAt(i);
            if (Character.isHighSurrogate(value)) {
                if (i + 1 >= itemId.length() || !Character.isLowSurrogate(itemId.charAt(++i))) return false;
            } else if (Character.isLowSurrogate(value)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Client-side handler: invokes the existing upload entry points.
     */
    @SideOnly(Side.CLIENT)
    public static class Handler implements IMessageHandler<PacketWebUploadTrigger, IMessage> {

        private static List<String> pendingBatchItemIds;
        private static String pendingBatchKey;
        private static int pendingBatchCount;
        private static int pendingBatchNext;

        @Override
        public IMessage onMessage(final PacketWebUploadTrigger message, MessageContext ctx) {
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
                List<String> batchedItemIds = message.batchCount > 1 ? collectItemIdBatch(message) : null;
                if (message.batchCount > 1 && batchedItemIds == null) return;
                if (TYPE_RECIPES.equalsIgnoreCase(message.uploadType)) {
                    String scope = message.renderMode != null && !message.renderMode.isEmpty() ? message.renderMode
                        : "full";
                    List<String> snapshotIds = batchedItemIds != null ? batchedItemIds
                        : parseSnapshotItemIds(message.packName);
                    if ("snapshot".equalsIgnoreCase(scope) && (snapshotIds == null || snapshotIds.isEmpty())) return;
                    KeyBindings.uploadNeiRecipes(scope, snapshotIds);
                } else if (TYPE_ICONS.equalsIgnoreCase(message.uploadType)
                    || TYPE_ICONS_LOCAL.equalsIgnoreCase(message.uploadType)) {
                        String pack = (message.packName != null && !message.packName.isEmpty()) ? message.packName
                            : "default";
                        String mode = (message.renderMode != null && !message.renderMode.isEmpty()) ? message.renderMode
                            : IconRenderMode.NEI.getId();
                        IconExportScope scope = null;
                        List<String> itemIds = null;
                        if (message.exportScope != null && !message.exportScope.isEmpty()) {
                            scope = IconExportScope.fromId(message.exportScope);
                            itemIds = batchedItemIds != null ? batchedItemIds
                                : PacketWebIconExportScope.parseItemIds(message.itemIdsJson);
                            if (itemIds == null) return;
                        }
                        boolean localOnly = TYPE_ICONS_LOCAL.equalsIgnoreCase(message.uploadType);
                        KeyBindings.triggerIconUpload(pack, mode, scope, itemIds, localOnly);
                    } else if (TYPE_ICONS_PULL.equalsIgnoreCase(message.uploadType)) {
                        String pack = (message.packName != null && !message.packName.isEmpty()) ? message.packName
                            : "default";
                        KeyBindings.triggerIconPull(pack);
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
        private static List<String> collectItemIdBatch(PacketWebUploadTrigger message) {
            if (message.batchCount < 2 || message.batchCount > MAX_BATCH_COUNT
                || message.batchIndex < 0
                || message.batchIndex >= message.batchCount) {
                resetBatch();
                return null;
            }
            boolean recipeSnapshot = TYPE_RECIPES.equalsIgnoreCase(message.uploadType)
                && "snapshot".equalsIgnoreCase(message.renderMode);
            boolean iconScope = (TYPE_ICONS.equalsIgnoreCase(message.uploadType)
                || TYPE_ICONS_LOCAL.equalsIgnoreCase(message.uploadType)) && message.exportScope != null
                && !message.exportScope.isEmpty();
            if (!recipeSnapshot && !iconScope) {
                resetBatch();
                return null;
            }
            String partJson = recipeSnapshot ? message.packName : message.itemIdsJson;
            List<String> part = PacketWebIconExportScope.parseItemIds(partJson);
            if (part == null) {
                resetBatch();
                return null;
            }
            String key = message.uploadType + "\u0000"
                + safe(message.renderMode)
                + "\u0000"
                + safe(message.exportScope);
            if (!recipeSnapshot) key += "\u0000" + safe(message.packName);
            if (message.batchIndex == 0) {
                resetBatch();
                pendingBatchItemIds = new ArrayList<String>();
                pendingBatchKey = key;
                pendingBatchCount = message.batchCount;
                pendingBatchNext = 0;
            }
            if (pendingBatchItemIds == null || pendingBatchCount != message.batchCount
                || !key.equals(pendingBatchKey)
                || message.batchIndex != pendingBatchNext
                || pendingBatchItemIds.size() + part.size() > PacketWebIconExportScope.MAX_ITEM_IDS) {
                resetBatch();
                return null;
            }
            pendingBatchItemIds.addAll(part);
            pendingBatchNext++;
            if (pendingBatchNext < pendingBatchCount) return null;
            List<String> complete = new ArrayList<String>(pendingBatchItemIds);
            resetBatch();
            return complete;
        }

        @SideOnly(Side.CLIENT)
        private static void resetBatch() {
            pendingBatchItemIds = null;
            pendingBatchKey = null;
            pendingBatchCount = 0;
            pendingBatchNext = 0;
        }

        private static String safe(String value) {
            return value == null ? "" : value;
        }

        @SideOnly(Side.CLIENT)
        private static List<String> parseSnapshotItemIds(String packName) {
            if (packName == null || packName.isEmpty()) {
                return new ArrayList<String>();
            }
            return PacketWebIconExportScope.parseItemIds(packName);
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
