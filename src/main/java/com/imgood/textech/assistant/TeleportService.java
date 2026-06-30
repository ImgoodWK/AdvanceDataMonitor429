package com.imgood.textech.assistant;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.util.Constants;

import com.imgood.textech.AdvanceDataMonitor;

import cpw.mods.fml.common.FMLCommonHandler;

/**
 * Handles scanning player inventory for Draconic Evolution Teleporter MKII
 * (Advanced Dislocator), extracting teleport destinations, and executing
 * teleports.
 *
 * Based on DE GTNH source:
 * TeleporterMKII: com.brandon3055.draconicevolution.common.items.tools.TeleporterMKII
 * TeleportLocation: com.brandon3055.draconicevolution.common.utils.Teleporter.TeleportLocation
 * NBT writeToNBT keys: X(double), Y(double), Z(double), Dimension(int),
 * Pitch(float), Yaw(float), Name(string), DimentionName(string), WP(boolean)
 */
public final class TeleportService {

    private TeleportService() {}

    /** Fully qualified class name of the Teleporter MKII (Advanced Dislocator). */
    private static final String TELEPORTER_MKII_CLASS = "com.brandon3055.draconicevolution.common.items.tools.TeleporterMKII";

    /** NBT compound-list key that holds all saved locations. */
    private static final String KEY_LOCATIONS = "Locations";

    /** Per-location NBT keys matching Teleporter.TeleportLocation.writeToNBT(). */
    private static final String KEY_X = "X";
    private static final String KEY_Y = "Y";
    private static final String KEY_Z = "Z";
    private static final String KEY_DIMENSION = "Dimension";
    private static final String KEY_NAME = "Name";
    private static final String KEY_DIMENSION_NAME = "DimentionName"; // typo in original DE code

    /**
     * Scan the player's entire inventory for Teleporter MKII items and extract all
     * saved teleport destinations.
     */
    public static List<TeleportDestination> scanDislocators(EntityPlayerMP player) {
        List<TeleportDestination> destinations = new ArrayList<TeleportDestination>();
        if (player == null || player.inventory == null) {
            return destinations;
        }
        int globalIndex = 1;
        for (int slot = 0; slot < player.inventory.getSizeInventory(); slot++) {
            ItemStack stack = player.inventory.getStackInSlot(slot);
            if (!isTeleporterMKII(stack)) {
                continue;
            }
            List<TeleportDestination> fromThisItem = extractDestinations(stack, globalIndex);
            destinations.addAll(fromThisItem);
            globalIndex += fromThisItem.size();
        }
        AdvanceDataMonitor.LOG.info(
            "[ADM Teleport] Scanned player inventory, found {} destination(s) across inventory",
            destinations.size());
        return destinations;
    }

    /**
     * Check if an ItemStack is a Draconic Evolution Teleporter MKII
     * by walking the class hierarchy for the exact class name.
     */
    private static boolean isTeleporterMKII(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return false;
        }
        Class<?> itemClass = stack.getItem()
            .getClass();
        while (itemClass != null) {
            if (TELEPORTER_MKII_CLASS.equals(itemClass.getName())) {
                return true;
            }
            itemClass = itemClass.getSuperclass();
        }
        return false;
    }

    /**
     * Extract teleport destinations from a single Teleporter MKII's NBT data.
     *
     * DE stores locations as "Locations" �?NBTTagList of NBTTagCompound,
     * each compound following TeleportLocation.writeToNBT() layout.
     */
    private static List<TeleportDestination> extractDestinations(ItemStack stack, int startIndex) {
        List<TeleportDestination> destinations = new ArrayList<TeleportDestination>();
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null || !tag.hasKey(KEY_LOCATIONS)) {
            return destinations;
        }

        NBTTagList list = tag.getTagList(KEY_LOCATIONS, Constants.NBT.TAG_COMPOUND);
        int count = list.tagCount();
        AdvanceDataMonitor.LOG.info("[ADM Teleport] Found {} location(s) in Teleporter MKII NBT", count);

        for (int i = 0; i < count; i++) {
            NBTTagCompound entry = list.getCompoundTagAt(i);
            TeleportDestination dest = parseTeleportLocation(entry, startIndex + destinations.size());
            if (dest != null) {
                destinations.add(dest);
            }
        }
        return destinations;
    }

    /**
     * Parse a single NBTTagCompound into a TeleportDestination,
     * matching Teleporter.TeleportLocation.readFromNBT() fields.
     */
    private static TeleportDestination parseTeleportLocation(NBTTagCompound entry, int index) {
        if (entry == null) {
            return null;
        }
        // Required: X, Y, Z, Dimension
        if (!entry.hasKey(KEY_X) || !entry.hasKey(KEY_Y) || !entry.hasKey(KEY_Z)) {
            return null;
        }
        int x = (int) entry.getDouble(KEY_X);
        int y = (int) entry.getDouble(KEY_Y);
        int z = (int) entry.getDouble(KEY_Z);
        int dim = entry.getInteger(KEY_DIMENSION);

        // Optional: custom name
        String name = entry.hasKey(KEY_NAME) ? entry.getString(KEY_NAME) : "";

        // Try the stored dimension name first (DE stores it with typo "DimentionName"),
        // fall back to ID-based resolution
        String dimensionName;
        if (entry.hasKey(KEY_DIMENSION_NAME)) {
            dimensionName = entry.getString(KEY_DIMENSION_NAME);
            if (dimensionName.isEmpty()) {
                dimensionName = resolveDimensionName(dim);
            }
        } else {
            dimensionName = resolveDimensionName(dim);
        }

        return new TeleportDestination(index, name, dim, dimensionName, x, y, z, null);
    }

    /**
     * Resolve a dimension ID to a human-readable name.
     */
    private static String resolveDimensionName(int dimId) {
        switch (dimId) {
            case -1:
                return "Nether";
            case 0:
                return "Overworld";
            case 1:
                return "The End";
            default:
                try {
                    WorldServer world = DimensionManager.getWorld(dimId);
                    if (world != null && world.provider != null) {
                        return world.provider.getDimensionName();
                    }
                } catch (Throwable ignored) {}
                return "Dim" + dimId;
        }
    }

    /**
     * Execute the actual teleport for a player to a given destination.
     * Handles dimension change via MinecraftServer.configurationManager.
     */
    public static String executeTeleport(EntityPlayerMP player, TeleportDestination dest, String locale) {
        if (player == null || dest == null) {
            return text(locale, "传送失败：无效的传送目标�?, "Teleport failed: invalid destination.");
        }
        AdvanceDataMonitor.LOG.info(
            "[ADM Teleport] Executing teleport for {} to '{}' [dim={}] ({},{},{})",
            player.getCommandSenderName(),
            dest.name,
            dest.dimensionId,
            dest.x,
            dest.y,
            dest.z);

        int currentDim = player.dimension;
        int targetDim = dest.dimensionId;

        if (currentDim != targetDim) {
            MinecraftServer server = FMLCommonHandler.instance()
                .getMinecraftServerInstance();
            if (server != null) {
                server.getConfigurationManager()
                    .transferPlayerToDimension(
                        player,
                        targetDim,
                        new net.minecraft.world.Teleporter(DimensionManager.getWorld(targetDim)) {

                            @Override
                            public void placeInPortal(net.minecraft.entity.Entity entity, double px, double py,
                                double pz, float yaw) {
                                entity.setPosition(dest.x + 0.5D, dest.y + 0.5D, dest.z + 0.5D);
                            }
                        });
                player.setPositionAndUpdate(dest.x + 0.5D, dest.y + 0.5D, dest.z + 0.5D);
                AdvanceDataMonitor.LOG.info("[ADM Teleport] Cross-dimension: {} �?{}", currentDim, targetDim);
            }
        } else {
            player.setPositionAndUpdate(dest.x + 0.5D, dest.y + 0.5D, dest.z + 0.5D);
        }

        String destName = dest.name.isEmpty() ? (dest.x + "," + dest.y + "," + dest.z) : dest.name;
        return text(
            locale,
            "已传送到 " + destName + " [" + dest.dimensionName + "]",
            "Teleported to " + destName + " [" + dest.dimensionName + "]");
    }

    /**
     * Filter destinations by fuzzy name matching.
     * Exact match (case-insensitive) takes priority; falls back to substring match.
     */
    public static List<TeleportDestination> filterDestinations(List<TeleportDestination> allDestinations,
        String target) {
        if (target == null || target.trim()
            .isEmpty()) {
            return allDestinations;
        }
        String normalized = target.trim()
            .toLowerCase();

        List<TeleportDestination> exactMatches = new ArrayList<TeleportDestination>();
        List<TeleportDestination> partialMatches = new ArrayList<TeleportDestination>();

        for (TeleportDestination dest : allDestinations) {
            String destName = dest.name.toLowerCase();
            if (destName.equals(normalized)) {
                exactMatches.add(dest);
            } else if (destName.contains(normalized) || normalized.contains(destName)) {
                partialMatches.add(dest);
            }
        }
        return !exactMatches.isEmpty() ? exactMatches : partialMatches;
    }

    private static String text(String locale, String zhText, String enText) {
        boolean zh = locale == null || locale.trim()
            .isEmpty()
            || locale.toLowerCase()
                .startsWith("zh");
        return zh ? zhText : enText;
    }
}
