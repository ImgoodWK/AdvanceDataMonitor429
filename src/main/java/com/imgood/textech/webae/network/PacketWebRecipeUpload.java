package com.imgood.textech.webae.network;

import java.nio.charset.StandardCharsets;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.webae.dto.RecipeDto;
import com.imgood.textech.webae.recipe.RecipeCacheStore;
import com.imgood.textech.webae.recipe.RecipeUploadSession;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * C→S client uploads NEI recipes in batches.
 *
 * Fields:
 * - isStart: true for first batch (carries total recipe count info)
 * - isEnd: true for last batch
 * - batchIndex: current batch index (0-based)
 * - totalBatches: total number of batches
 * - playerUuid: UUID of the uploading player
 * - recipeDataJson: Gson-serialized RecipeDto[] as UTF-8 bytes
 */
public class PacketWebRecipeUpload implements IMessage {

    private static final Gson GSON = new GsonBuilder().create();

    public boolean isStart;
    public boolean isEnd;
    public int batchIndex;
    public int totalBatches;
    public int batchCount;
    public String playerUuid;
    public byte[] recipeDataJson;

    public PacketWebRecipeUpload() {}

    public PacketWebRecipeUpload(boolean isStart, boolean isEnd, int batchIndex, int totalBatches, int batchCount,
        String playerUuid, byte[] recipeDataJson) {
        this.isStart = isStart;
        this.isEnd = isEnd;
        this.batchIndex = batchIndex;
        this.totalBatches = totalBatches;
        this.batchCount = batchCount;
        this.playerUuid = playerUuid;
        this.recipeDataJson = recipeDataJson;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(isStart);
        buf.writeBoolean(isEnd);
        buf.writeInt(batchIndex);
        buf.writeInt(totalBatches);
        buf.writeInt(batchCount);
        writeUtf8(buf, playerUuid);
        if (recipeDataJson != null) {
            buf.writeInt(recipeDataJson.length);
            buf.writeBytes(recipeDataJson);
        } else {
            buf.writeInt(0);
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        isStart = buf.readBoolean();
        isEnd = buf.readBoolean();
        batchIndex = buf.readInt();
        totalBatches = buf.readInt();
        batchCount = buf.readInt();
        playerUuid = readUtf8(buf);
        int dataLen = buf.readInt();
        if (dataLen > 0) {
            recipeDataJson = new byte[dataLen];
            buf.readBytes(recipeDataJson);
        } else {
            recipeDataJson = new byte[0];
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
     * Server-side handler: receives recipe batches and stores them in RecipeCacheStore.
     */
    public static class Handler implements IMessageHandler<PacketWebRecipeUpload, IMessage> {

        @Override
        public IMessage onMessage(PacketWebRecipeUpload message, MessageContext ctx) {
            try {
                if (message.recipeDataJson != null
                    && message.recipeDataJson.length > RecipeUploadBatcher.MAX_RECIPE_JSON_BYTES) {
                    AdvanceDataMonitor.LOG.warn(
                        "[WebAE] Rejected recipe upload batch {}: JSON payload {} bytes exceeds limit",
                        message.batchIndex,
                        message.recipeDataJson.length);
                    return new PacketWebRecipeUploadAck(
                        false,
                        message.batchIndex,
                        message.totalBatches,
                        "Batch payload too large (" + message.recipeDataJson.length + " bytes).");
                }

                if (message.isStart) {
                    if (RecipeUploadSession.onStart(message.playerUuid, message.totalBatches)) {
                        RecipeCacheStore.instance()
                            .beginUploadSession();
                        RecipeCacheStore.instance()
                            .clearMemoryOnly();
                    }
                }

                RecipeUploadSession.onBatch(message.playerUuid);

                RecipeDto[] recipes = GSON.fromJson(
                    new String(message.recipeDataJson, StandardCharsets.UTF_8),
                    new TypeToken<RecipeDto[]>() {}.getType());

                if (recipes != null && recipes.length > 0) {
                    RecipeCacheStore.instance()
                        .ingest(recipes);
                }

                if (message.isEnd) {
                    if (RecipeUploadSession.onEnd(message.playerUuid)) {
                        RecipeCacheStore.instance()
                            .endUploadSession();
                    } else {
                        RecipeCacheStore.instance()
                            .rebuildHandlerCounts();
                    }
                    int totalCount = RecipeCacheStore.instance()
                        .getRecipeCount();
                    AdvanceDataMonitor.LOG.info(
                        "[WebAE] Recipe upload complete: {} batches, {} total recipes stored from player {}",
                        message.totalBatches,
                        totalCount,
                        message.playerUuid);

                    return new PacketWebRecipeUploadAck(
                        true,
                        message.batchIndex + 1,
                        message.totalBatches,
                        "Upload complete. Total recipes in cache: " + totalCount);
                }

                return new PacketWebRecipeUploadAck(
                    true,
                    message.batchIndex + 1,
                    message.totalBatches,
                    "Batch " + (message.batchIndex + 1) + "/" + message.totalBatches + " received.");
            } catch (Exception e) {
                AdvanceDataMonitor.LOG.error("[WebAE] Failed to process recipe upload batch", e);
                return new PacketWebRecipeUploadAck(
                    false,
                    message.batchIndex,
                    message.totalBatches,
                    "Error processing batch: " + e.getMessage());
            }
        }
    }
}
