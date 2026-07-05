package com.imgood.textech.webae.network;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.Config;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Client-side tick throttling for {@link PacketWebRecipeUpload} batches.
 */
@SideOnly(Side.CLIENT)
public final class RecipeUploadThrottler {

    private static final RecipeUploadThrottler INSTANCE = new RecipeUploadThrottler();
    private static final Gson GSON = new GsonBuilder().serializeNulls()
        .create();

    private final List<RecipeUploadBatcher.Batch> pending = new ArrayList<RecipeUploadBatcher.Batch>();
    private String playerUuid;
    private int sentIndex;
    private String label;

    private RecipeUploadThrottler() {}

    public static RecipeUploadThrottler instance() {
        return INSTANCE;
    }

    public boolean isUploading() {
        return !pending.isEmpty() && sentIndex < pending.size();
    }

    public void startUpload(String playerUuid, List<RecipeUploadBatcher.Batch> batches, String label) {
        this.pending.clear();
        if (batches != null) {
            this.pending.addAll(batches);
        }
        this.playerUuid = playerUuid;
        this.sentIndex = 0;
        this.label = label != null ? label : "recipes";
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (pending.isEmpty() || sentIndex >= pending.size()) return;

        int perTick = Config.webRecipeUploadBatchesPerTick;
        if (perTick <= 0) perTick = 3;
        int sent = 0;
        while (sentIndex < pending.size() && sent < perTick) {
            RecipeUploadBatcher.Batch batch = pending.get(sentIndex);
            AdvanceDataMonitor.ADMCHANEL.sendToServer(
                new PacketWebRecipeUpload(
                    sentIndex == 0,
                    sentIndex == pending.size() - 1,
                    sentIndex,
                    pending.size(),
                    batch.recipeCount,
                    playerUuid,
                    batch.jsonBytes));
            sentIndex++;
            sent++;
        }
        if (sentIndex >= pending.size()) {
            AdvanceDataMonitor.LOG.info("[WebAE] Recipe upload queued batches sent ({})", label);
            pending.clear();
        }
    }
}
