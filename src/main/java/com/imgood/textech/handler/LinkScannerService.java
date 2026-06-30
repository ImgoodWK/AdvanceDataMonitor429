package com.imgood.textech.handler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;

import com.imgood.textech.items.ItemAdvanceLinkScanner;
import com.imgood.textech.items.LinkScanBlockType;
import com.imgood.textech.items.LinkScanEntry;
import com.imgood.textech.tileentity.IOwnableTile;

public final class LinkScannerService {

    private LinkScannerService() {}

    public static int scanLoadedTiles(EntityPlayerMP player, ItemStack scannerStack) {
        if (player == null || scannerStack == null || player.worldObj == null) {
            return 0;
        }
        World world = player.worldObj;
        int dimension = world.provider.dimensionId;

        Map<String, String> previousAliases = collectAliasMap(scannerStack);
        List<LinkScanEntry> found = new ArrayList<LinkScanEntry>();
        int nextSlot = 1;

        for (Object obj : world.loadedTileEntityList) {
            if (!(obj instanceof TileEntity)) {
                continue;
            }
            TileEntity tile = (TileEntity) obj;
            LinkScanBlockType type = LinkScanBlockType.fromTileEntity(tile);
            if (type == null) {
                continue;
            }
            String owner = "";
            if (tile instanceof IOwnableTile) {
                owner = ((IOwnableTile) tile).getOwnerName();
                if (owner == null) {
                    owner = "";
                }
            }
            String key = dimension + ":" + tile.xCoord + ":" + tile.yCoord + ":" + tile.zCoord;
            String alias = previousAliases.containsKey(key) ? previousAliases.get(key) : "";
            found.add(
                new LinkScanEntry(nextSlot++, dimension, tile.xCoord, tile.yCoord, tile.zCoord, owner, type, alias));
        }

        writeEntries(scannerStack, found);
        return found.size();
    }

    public static void notifyScanResult(EntityPlayerMP player, int count) {
        if (player == null) {
            return;
        }
        player.addChatMessage(new ChatComponentTranslation("adm.scanner.scan_done", count));
    }

    public static String teleportTo(EntityPlayerMP player, int dimension, int x, int y, int z) {
        if (player == null || player.worldObj == null) {
            return "adm.scanner.teleport_failed";
        }
        if (player.worldObj.provider.dimensionId != dimension) {
            return "adm.scanner.teleport_wrong_dim";
        }

        int[] landing = findSafeLanding(player.worldObj, x, y, z);
        if (landing == null) {
            return "adm.scanner.teleport_failed";
        }

        player.setPositionAndUpdate(landing[0] + 0.5D, landing[1], landing[2] + 0.5D);
        return "adm.scanner.teleport_ok";
    }

    public static boolean hasScannerEntry(ItemStack scannerStack, int dimension, int x, int y, int z) {
        if (scannerStack == null) {
            return false;
        }
        List<LinkScanEntry> entries = ItemAdvanceLinkScanner.getAllEntries(scannerStack);
        for (int i = 0; i < entries.size(); i++) {
            LinkScanEntry entry = entries.get(i);
            if (entry.dimension == dimension && entry.x == x && entry.y == y && entry.z == z) {
                return true;
            }
        }
        return false;
    }

    private static int[] findSafeLanding(World world, int x, int y, int z) {
        int[][] offsets = new int[][] { { 0, 1, 0 }, { 1, 1, 0 }, { -1, 1, 0 }, { 0, 1, 1 }, { 0, 1, -1 }, { 1, 1, 1 },
            { 1, 1, -1 }, { -1, 1, 1 }, { -1, 1, -1 }, { 0, 2, 0 }, { 0, 3, 0 }, { 0, 0, 0 }, { 0, -1, 0 } };

        for (int i = 0; i < offsets.length; i++) {
            int tx = x + offsets[i][0];
            int ty = y + offsets[i][1];
            int tz = z + offsets[i][2];
            if (ty < 0 || ty >= world.getHeight() - 1) {
                continue;
            }
            if (isPassable(world, tx, ty, tz) && isPassable(world, tx, ty + 1, tz)) {
                return new int[] { tx, ty, tz };
            }
        }
        return null;
    }

    private static boolean isPassable(World world, int x, int y, int z) {
        return world.isAirBlock(x, y, z);
    }

    private static Map<String, String> collectAliasMap(ItemStack stack) {
        Map<String, String> map = new HashMap<String, String>();
        List<LinkScanEntry> existing = ItemAdvanceLinkScanner.getAllEntries(stack);
        for (int i = 0; i < existing.size(); i++) {
            LinkScanEntry entry = existing.get(i);
            map.put(entry.locationKey(), entry.alias == null ? "" : entry.alias);
        }
        return map;
    }

    private static void writeEntries(ItemStack stack, List<LinkScanEntry> entries) {
        NBTTagCompound root = ItemAdvanceLinkScanner.getOrCreateScannerNBT(stack);
        NBTTagList list = new NBTTagList();
        int nextSlot = 1;
        for (int i = 0; i < entries.size(); i++) {
            LinkScanEntry entry = entries.get(i);
            entry.slotIndex = nextSlot++;
            list.appendTag(entry.toNBT());
        }
        root.setTag(ItemAdvanceLinkScanner.NBT_KEY_ENTRIES, list);
        root.setInteger(ItemAdvanceLinkScanner.NBT_KEY_NEXT_SLOT, nextSlot);
    }
}
