package com.imgood.textech.webae.network;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.webae.dto.RecipeDto;

/**
 * Splits recipe lists into FML-safe upload batches (Forge 1.7.10 payload limit 32767 bytes).
 */
public final class RecipeUploadBatcher {

    /** Hard limit for Minecraft/FML custom payload body. */
    public static final int FML_MAX_PACKET_BYTES = 32767;

    /**
     * UUID string, batch ints/bools, length prefixes, and FML channel framing.
     * Keep conservative so {@link PacketWebRecipeUpload} never exceeds {@link #FML_MAX_PACKET_BYTES}.
     */
    private static final int PACKET_OVERHEAD_BYTES = 512;

    /** Maximum UTF-8 JSON bytes for {@code recipeDataJson} per packet. */
    public static final int MAX_RECIPE_JSON_BYTES = FML_MAX_PACKET_BYTES - PACKET_OVERHEAD_BYTES;

    private RecipeUploadBatcher() {}

    public static final class Batch {

        public final byte[] jsonBytes;
        public final int recipeCount;

        public Batch(byte[] jsonBytes, int recipeCount) {
            this.jsonBytes = jsonBytes;
            this.recipeCount = recipeCount;
        }
    }

    /**
     * @return serialized JSON batches ready for {@link PacketWebRecipeUpload}
     */
    public static List<Batch> buildBatches(List<RecipeDto> recipes, Gson gson) {
        List<Batch> out = new ArrayList<Batch>();
        if (recipes == null || recipes.isEmpty()) {
            return out;
        }

        List<RecipeDto> current = new ArrayList<RecipeDto>();
        for (RecipeDto source : recipes) {
            if (source == null) continue;
            RecipeDto prepared = prepareForUpload(source, gson);
            if (prepared == null) {
                AdvanceDataMonitor.LOG.warn(
                    "[WebAE] Skipping recipe upload entry (still too large after trim): {}:{}",
                    source.handlerId,
                    source.recipeIndex);
                continue;
            }

            current.add(prepared);
            byte[] bytes = serializeBatch(current, gson);
            while (bytes.length > MAX_RECIPE_JSON_BYTES && current.size() > 1) {
                RecipeDto overflow = current.remove(current.size() - 1);
                out.add(new Batch(serializeBatch(current, gson), current.size()));
                current.clear();
                current.add(overflow);
                bytes = serializeBatch(current, gson);
            }

            if (bytes.length > MAX_RECIPE_JSON_BYTES && current.size() == 1) {
                RecipeDto aggressive = aggressivelyTrim(current.get(0));
                current.set(0, aggressive);
                bytes = serializeBatch(current, gson);
                if (bytes.length > MAX_RECIPE_JSON_BYTES) {
                    AdvanceDataMonitor.LOG.warn(
                        "[WebAE] Skipping oversized recipe after aggressive trim: {}:{} ({} bytes)",
                        aggressive.handlerId,
                        aggressive.recipeIndex,
                        bytes.length);
                    current.clear();
                }
            }
        }

        if (!current.isEmpty()) {
            byte[] bytes = serializeBatch(current, gson);
            if (bytes.length <= MAX_RECIPE_JSON_BYTES) {
                out.add(new Batch(bytes, current.size()));
            } else {
                AdvanceDataMonitor.LOG.warn(
                    "[WebAE] Dropping final batch of {} recipes ({} bytes exceeds limit)",
                    current.size(),
                    bytes.length);
            }
        }
        return out;
    }

    /** Estimate full {@link PacketWebRecipeUpload} size for logging / diagnostics. */
    public static int estimatePacketBytes(int jsonLen, String playerUuid) {
        int uuidLen = playerUuid != null ? playerUuid.getBytes(StandardCharsets.UTF_8).length : 0;
        return 2 + 12 + 4 + uuidLen + 4 + jsonLen;
    }

    private static byte[] serializeBatch(List<RecipeDto> batch, Gson gson) {
        return gson.toJson(batch.toArray(new RecipeDto[batch.size()]))
            .getBytes(StandardCharsets.UTF_8);
    }

    private static RecipeDto prepareForUpload(RecipeDto source, Gson gson) {
        RecipeDto dto = cloneDto(source, gson);
        dto.rawJson = null;
        if (fitsSingle(dto, gson)) {
            return dto;
        }
        dto.gridSlots = new ArrayList<RecipeDto.GridSlot>();
        dto.gridWidth = 0;
        dto.gridHeight = 0;
        if (fitsSingle(dto, gson)) {
            return dto;
        }
        return null;
    }

    private static RecipeDto aggressivelyTrim(RecipeDto source) {
        RecipeDto dto = source;
        dto.rawJson = null;
        if (dto.gridSlots != null) {
            dto.gridSlots.clear();
        }
        dto.gridWidth = 0;
        dto.gridHeight = 0;
        if (dto.inputs != null && dto.inputs.size() > 64) {
            dto.inputs = new ArrayList<RecipeDto.ItemEntry>(dto.inputs.subList(0, 64));
        }
        if (dto.outputs != null && dto.outputs.size() > 16) {
            dto.outputs = new ArrayList<RecipeDto.ItemEntry>(dto.outputs.subList(0, 16));
        }
        return dto;
    }

    private static boolean fitsSingle(RecipeDto dto, Gson gson) {
        List<RecipeDto> one = new ArrayList<RecipeDto>();
        one.add(dto);
        return serializeBatch(one, gson).length <= MAX_RECIPE_JSON_BYTES;
    }

    private static RecipeDto cloneDto(RecipeDto source, Gson gson) {
        return gson.fromJson(gson.toJson(source), RecipeDto.class);
    }
}
