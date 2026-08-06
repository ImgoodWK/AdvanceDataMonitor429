package com.imgood.textech.webae.network;

import java.nio.charset.StandardCharsets;

import net.minecraft.entity.player.EntityPlayerMP;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.Config;
import com.imgood.textech.network.handler.PacketHandlers;
import com.imgood.textech.utils.NetworkPacketCodec;
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
    private static final int MAX_PACKET_BODY_BYTES = 30000;
    private static final int MAX_PLAYER_UUID_BYTES = 64;
    private static final int MAX_TOTAL_BATCHES = 4096;
    private static final int MAX_RECIPES_PER_BATCH = 2048;
    private boolean valid = true;

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
        int startIndex = buf.writerIndex();
        buf.writeBoolean(isStart);
        buf.writeBoolean(isEnd);
        buf.writeInt(batchIndex);
        buf.writeInt(totalBatches);
        buf.writeInt(batchCount);
        writeUtf8(buf, playerUuid);
        if (recipeDataJson == null || recipeDataJson.length == 0
            || recipeDataJson.length > RecipeUploadBatcher.MAX_RECIPE_JSON_BYTES) {
            throw new IllegalArgumentException("Recipe JSON payload is empty or exceeds packet limit");
        }
        buf.writeInt(recipeDataJson.length);
        buf.writeBytes(recipeDataJson);
        if (buf.writerIndex() - startIndex > MAX_PACKET_BODY_BYTES) {
            throw new IllegalArgumentException("Recipe upload packet exceeds packet body limit");
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        valid = true;
        try {
            if (buf.readableBytes() > MAX_PACKET_BODY_BYTES) {
                throw new IllegalArgumentException("Recipe upload packet exceeds packet body limit");
            }
            isStart = buf.readBoolean();
            isEnd = buf.readBoolean();
            batchIndex = buf.readInt();
            totalBatches = buf.readInt();
            batchCount = buf.readInt();
            playerUuid = NetworkPacketCodec.readUtf8(buf, MAX_PLAYER_UUID_BYTES);
            recipeDataJson = NetworkPacketCodec.readBytes(buf, RecipeUploadBatcher.MAX_RECIPE_JSON_BYTES);
            if (recipeDataJson.length == 0 || buf.readableBytes() != 0) {
                throw new IllegalArgumentException("Invalid recipe upload packet framing");
            }
        } catch (RuntimeException e) {
            valid = false;
            recipeDataJson = new byte[0];
        }
    }

    private static void writeUtf8(ByteBuf buf, String s) {
        if (s == null) {
            buf.writeInt(0);
            return;
        }
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_PLAYER_UUID_BYTES) {
            throw new IllegalArgumentException("Player UUID exceeds packet limit");
        }
        buf.writeInt(bytes.length);
        buf.writeBytes(bytes);
    }

    /**
     * Server-side handler: receives recipe batches and stores them in RecipeCacheStore.
     */
    public static class Handler implements IMessageHandler<PacketWebRecipeUpload, IMessage> {

        @Override
        public IMessage onMessage(final PacketWebRecipeUpload message, MessageContext ctx) {
            final EntityPlayerMP player = ctx == null || ctx.getServerHandler() == null ? null
                : ctx.getServerHandler().playerEntity;
            if (!isValid(message, player)) {
                return null;
            }
            return PacketHandlers.runOnServer(ctx, new Runnable() {

                @Override
                public void run() {
                    IMessage ack = processOnServerThread(message, player);
                    if (ack != null && player != null) {
                        AdvanceDataMonitor.ADMCHANEL.sendTo(ack, player);
                    }
                }
            });
        }

        private static boolean isValid(PacketWebRecipeUpload message, EntityPlayerMP player) {
            if (message == null || !message.valid || player == null) {
                return false;
            }
            if (!Config.webRecipeUploadEnabled || !player.canCommandSenderUseCommand(2, "admweb")) {
                return false;
            }
            if (!matchesPlayerUuid(message.playerUuid, player)) {
                AdvanceDataMonitor.LOG.warn("[WebAE] Rejected recipe upload with mismatched player UUID");
                return false;
            }
            if (message.totalBatches < 1 || message.totalBatches > MAX_TOTAL_BATCHES
                || message.batchIndex < 0
                || message.batchIndex >= message.totalBatches
                || message.batchCount < 0
                || message.batchCount > MAX_RECIPES_PER_BATCH
                || message.isStart != (message.batchIndex == 0)
                || message.isEnd != (message.batchIndex == message.totalBatches - 1)) {
                return false;
            }
            return message.recipeDataJson != null && message.recipeDataJson.length > 0
                && message.recipeDataJson.length <= RecipeUploadBatcher.MAX_RECIPE_JSON_BYTES;
        }

        private static IMessage processOnServerThread(PacketWebRecipeUpload message, EntityPlayerMP player) {
            String actorUuid = player.getUniqueID()
                .toString();
            RecipeDto[] recipes;
            try {
                recipes = GSON.fromJson(
                    NetworkPacketCodec.decodeUtf8(message.recipeDataJson),
                    new TypeToken<RecipeDto[]>() {}.getType());
                if (recipes == null || recipes.length > MAX_RECIPES_PER_BATCH || recipes.length != message.batchCount) {
                    return ack(false, message, "Invalid recipe batch.");
                }

                RecipeUploadSession.BatchDecision decision = RecipeUploadSession
                    .acceptBatch(actorUuid, message.batchIndex, message.totalBatches, message.isStart, message.isEnd);
                if (!decision.accepted) {
                    return ack(false, message, "Recipe upload batch is out of order or the session is not active.");
                }
                if (decision.newSession) {
                    RecipeCacheStore.instance()
                        .beginUploadSession();
                    RecipeCacheStore.instance()
                        .clearMemoryOnly();
                }
                if (recipes != null && recipes.length > 0) {
                    RecipeCacheStore.instance()
                        .ingest(recipes);
                }

                if (decision.completed) {
                    RecipeCacheStore.instance()
                        .endUploadSession();
                    int totalCount = RecipeCacheStore.instance()
                        .getRecipeCount();
                    AdvanceDataMonitor.LOG.info(
                        "[WebAE] Recipe upload complete: {} batches, {} total recipes stored from player {}",
                        message.totalBatches,
                        totalCount,
                        actorUuid);
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
                RecipeUploadSession.abort(actorUuid);
                AdvanceDataMonitor.LOG.error("[WebAE] Failed to process recipe upload batch", e);
                return ack(false, message, "Error processing batch.");
            }
        }

        private static IMessage ack(boolean success, PacketWebRecipeUpload message, String text) {
            return new PacketWebRecipeUploadAck(success, message.batchIndex, message.totalBatches, text);
        }

        private static boolean matchesPlayerUuid(String supplied, EntityPlayerMP player) {
            return supplied == null || supplied.isEmpty()
                || supplied.equalsIgnoreCase(
                    player.getUniqueID()
                        .toString());
        }
    }
}
