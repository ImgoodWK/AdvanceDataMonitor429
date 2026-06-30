package com.imgood.textech.handler;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;

import com.imgood.textech.AdvanceDataMonitor;

/**
 * Per-player Dimensional Pocket storage singleton.
 * Each player's pocket state is persisted to <worldDir>/textech/pocket-<uuid>.dat
 * using Minecraft's compressed NBT format.
 * Content is bound to the player UUID, not to any specific item instance.
 * Modeled after GrapplePathStore.
 */
public final class PocketStore {

    private static final PocketStore INSTANCE = new PocketStore();
    private final Map<String, PocketState> cache = new HashMap<String, PocketState>();

    /** World save directory root; set via WorldEvent.Load to scope saves per-world. */
    private static File worldSaveDir = null;

    private PocketStore() {}

    public static PocketStore instance() {
        return INSTANCE;
    }

    /**
     * Set the world save directory root so pocket files are saved per-world
     * rather than in the global config directory.
     */
    public static void setWorldDirectory(File dir) {
        worldSaveDir = dir;
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
        if (worldSaveDir != null) {
            File newModDir = new File(worldSaveDir, AdvanceDataMonitor.MODID);
            File newFile = new File(newModDir, "pocket-" + uuid + ".dat");
            if (newFile.exists()) {
                return newFile;
            }
            // Legacy migration: check old "advancedatamonitor" directory
            File legacyModDir = new File(worldSaveDir, "advancedatamonitor");
            File legacyFile = new File(legacyModDir, "pocket-" + uuid + ".dat");
            if (legacyFile.exists()) {
                AdvanceDataMonitor.LOG
                    .info("[TeXTech] Pocket data found in legacy path, migrating: {}", legacyFile.getAbsolutePath());
                return legacyFile;
            }
            // Neither exists, use new path
            if (!newModDir.exists()) {
                newModDir.mkdirs();
            }
            return newFile;
        }
        // Fallback for server startup before world is loaded —use new MODID
        File dir = new File("config", AdvanceDataMonitor.MODID);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return new File(dir, "pocket-" + uuid + ".json");
    }

    private PocketState loadFromDisk(String uuid) {
        File file = fileFor(uuid);
        if (!file.exists()) return null;
        try (FileInputStream fis = new FileInputStream(file)) {
            NBTTagCompound tag = CompressedStreamTools.readCompressed(fis);
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
        NBTTagCompound tag = state.writeToNBT();
        try (FileOutputStream fos = new FileOutputStream(file)) {
            CompressedStreamTools.writeCompressed(tag, fos);
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.error("Failed to save pocket state for " + uuid, e);
        }
    }
}
