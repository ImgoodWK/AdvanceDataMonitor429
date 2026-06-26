package com.imgood.advancedatamonitor.handler;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.imgood.advancedatamonitor.AdvanceDataMonitor;
import com.imgood.advancedatamonitor.assistant.AssistantDataFiles;

/**
 * Per-player Dimensional Pocket storage singleton.
 * Each player's pocket state is persisted to config/advancedatamonitor/pocket-<uuid>.json.
 * Content is bound to the player UUID, not to any specific item instance.
 * Modeled after GrapplePathStore.
 */
public final class PocketStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting()
        .create();
    private static final PocketStore INSTANCE = new PocketStore();
    private final Map<String, PocketState> cache = new HashMap<String, PocketState>();

    private PocketStore() {}

    public static PocketStore instance() {
        return INSTANCE;
    }

    public synchronized PocketState getOrCreate(EntityPlayerMP player) {
        if (player == null) return new PocketState();
        return getOrCreate(ownerUuid(player));
    }

    public synchronized PocketState getOrCreate(String uuid) {
        PocketState state = cache.get(uuid);
        if (state != null) return state;
        state = loadFromDisk(uuid);
        if (state == null) state = new PocketState();
        cache.put(uuid, state);
        return state;
    }

    public synchronized void save(EntityPlayerMP player) {
        if (player == null) return;
        save(ownerUuid(player));
    }

    public synchronized void save(String uuid) {
        PocketState state = cache.get(uuid);
        if (state == null) return;
        saveToDisk(uuid, state);
    }

    public synchronized void saveAll() {
        for (Map.Entry<String, PocketState> entry : cache.entrySet()) {
            saveToDisk(entry.getKey(), entry.getValue());
        }
    }

    private static String ownerUuid(EntityPlayerMP player) {
        UUID uuid = player == null ? new UUID(0L, 0L) : player.getUniqueID();
        return uuid.toString();
    }

    private static File fileFor(String uuid) {
        return AssistantDataFiles.dataFile("pocket-" + uuid + ".json");
    }

    private PocketState loadFromDisk(String uuid) {
        File file = fileFor(uuid);
        if (!file.exists()) return null;
        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(file), "UTF-8")) {
            JsonObject json = new JsonParser().parse(reader)
                .getAsJsonObject();
            if (json == null || !json.has("nbt")) return null;
            // Store as JSON-embedded NBT string for round-trip safety with ItemStack.
            String nbtString = json.get("nbt")
                .getAsString();
            NBTTagCompound tag = gsonToNbt(nbtString);
            PocketState state = new PocketState();
            state.readFromNBT(tag);
            return state;
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.error("Failed to load pocket state for " + uuid, e);
            return null;
        }
    }

    private void saveToDisk(String uuid, PocketState state) {
        File file = fileFor(uuid);
        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file), "UTF-8")) {
            NBTTagCompound tag = state.writeToNBT();
            String nbtString = nbtToGson(tag);
            JsonObject json = new JsonObject();
            json.addProperty("nbt", nbtString);
            GSON.toJson(json, writer);
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.error("Failed to save pocket state for " + uuid, e);
        }
    }

    private static String nbtToGson(NBTTagCompound tag) {
        // Use Forge's NBT -> JSON string helper for round-trip with ItemStack NBT.
        return tag.toString();
    }

    private static NBTTagCompound gsonToNbt(String json) {
        // Parse JSON-formatted NBT back into NBTTagCompound.
        // 1.7.10 doesn't expose a direct JSON->NBT parser for arbitrary compounds
        // that contain ItemStacks; use the vanilla String->NBT parser which
        // understands the SNBT format that NBTTagCompound.toString() produces.
        try {
            return (NBTTagCompound) net.minecraft.nbt.JsonToNBT.func_150315_a(json);
        } catch (Throwable t) {
            AdvanceDataMonitor.LOG.error("Failed to parse pocket NBT", t);
            return new NBTTagCompound();
        }
    }
}
