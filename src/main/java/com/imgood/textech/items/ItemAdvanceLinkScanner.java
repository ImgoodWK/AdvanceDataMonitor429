package com.imgood.textech.items;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.client.ItemClientGui;
import com.imgood.textech.network.packet.PacketLinkScannerAction;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Display names / 显示名称:
 * - EN: Advance Link Scanner
 * - ZH: 高级链接扫描�?
 * Lang keys: item.advanceLinkScanner.name, adm.scanner.title
 */
public class ItemAdvanceLinkScanner extends Item {

    public static final String NBT_KEY_ENTRIES = "scannerEntries";
    public static final String NBT_KEY_NEXT_SLOT = "nextSlotIndex";
    public static final String NBT_KEY_OWNER_FILTER = "ownerFilter";
    public static final String NBT_KEY_NAME_FILTER = "nameFilter";

    public static final int OWNER_FILTER_ALL = 0;
    public static final int OWNER_FILTER_SELF = 1;
    public static final int OWNER_FILTER_OTHERS = 2;

    public static final int NAME_FILTER_ALL = 0;
    public static final int NAME_FILTER_UNNAMED = 1;
    public static final int NAME_FILTER_NAMED = 2;

    public ItemAdvanceLinkScanner() {
        setMaxStackSize(1);
        setCreativeTab(CreativeTabs.tabTools);
        setUnlocalizedName("advanceLinkScanner");
        setTextureName(AdvanceDataMonitor.MODID + ":advance_link_scanner");
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (world.isRemote) {
            if (player.isSneaking()) {
                int slot = player.inventory.currentItem;
                AdvanceDataMonitor.ADMCHANEL.sendToServer(PacketLinkScannerAction.scan(slot));
            } else {
                openScannerGui(stack, player);
            }
        }
        return stack;
    }

    @SideOnly(Side.CLIENT)
    private void openScannerGui(ItemStack stack, EntityPlayer player) {
        ItemClientGui.openLinkScannerGui(stack, player);
    }

    public static NBTTagCompound getOrCreateScannerNBT(ItemStack stack) {
        if (stack == null) {
            return new NBTTagCompound();
        }
        NBTTagCompound root = stack.getTagCompound();
        if (root == null) {
            root = new NBTTagCompound();
            stack.setTagCompound(root);
        }
        if (!root.hasKey(NBT_KEY_ENTRIES)) {
            root.setTag(NBT_KEY_ENTRIES, new NBTTagList());
        }
        if (!root.hasKey(NBT_KEY_NEXT_SLOT)) {
            root.setInteger(NBT_KEY_NEXT_SLOT, 1);
        }
        if (!root.hasKey(NBT_KEY_OWNER_FILTER)) {
            root.setInteger(NBT_KEY_OWNER_FILTER, OWNER_FILTER_ALL);
        }
        if (!root.hasKey(NBT_KEY_NAME_FILTER)) {
            root.setInteger(NBT_KEY_NAME_FILTER, NAME_FILTER_ALL);
        }
        return root;
    }

    public static List<LinkScanEntry> getAllEntries(ItemStack stack) {
        NBTTagList list = getOrCreateScannerNBT(stack).getTagList(NBT_KEY_ENTRIES, 10);
        List<LinkScanEntry> entries = new ArrayList<LinkScanEntry>();
        for (int i = 0; i < list.tagCount(); i++) {
            LinkScanEntry entry = LinkScanEntry.fromNBT(list.getCompoundTagAt(i));
            if (entry != null) {
                entries.add(entry);
            }
        }
        Collections.sort(entries, new Comparator<LinkScanEntry>() {

            @Override
            public int compare(LinkScanEntry a, LinkScanEntry b) {
                return a.slotIndex - b.slotIndex;
            }
        });
        return entries;
    }

    public static int getOwnerFilter(ItemStack stack) {
        return getOrCreateScannerNBT(stack).getInteger(NBT_KEY_OWNER_FILTER);
    }

    public static void setOwnerFilter(ItemStack stack, int filter) {
        getOrCreateScannerNBT(stack).setInteger(NBT_KEY_OWNER_FILTER, filter);
    }

    public static int cycleOwnerFilter(ItemStack stack) {
        int next = (getOwnerFilter(stack) + 1) % 3;
        setOwnerFilter(stack, next);
        return next;
    }

    public static int getNameFilter(ItemStack stack) {
        return getOrCreateScannerNBT(stack).getInteger(NBT_KEY_NAME_FILTER);
    }

    public static void setNameFilter(ItemStack stack, int filter) {
        getOrCreateScannerNBT(stack).setInteger(NBT_KEY_NAME_FILTER, filter);
    }

    public static int cycleNameFilter(ItemStack stack) {
        int next = (getNameFilter(stack) + 1) % 3;
        setNameFilter(stack, next);
        return next;
    }

    public static void setEntryAlias(ItemStack stack, int slotIndex, String alias) {
        NBTTagList list = getOrCreateScannerNBT(stack).getTagList(NBT_KEY_ENTRIES, 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound tag = list.getCompoundTagAt(i);
            if (tag.getInteger("slotIndex") == slotIndex) {
                tag.setString("alias", alias == null ? "" : alias);
                return;
            }
        }
    }

    public static LinkScanEntry getEntry(ItemStack stack, int slotIndex) {
        NBTTagList list = getOrCreateScannerNBT(stack).getTagList(NBT_KEY_ENTRIES, 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound tag = list.getCompoundTagAt(i);
            if (tag.getInteger("slotIndex") == slotIndex) {
                return LinkScanEntry.fromNBT(tag);
            }
        }
        return null;
    }

    public static boolean passesFilters(LinkScanEntry entry, ItemStack stack, String playerName) {
        if (entry == null) {
            return false;
        }
        int ownerFilter = getOwnerFilter(stack);
        boolean hasOwner = entry.hasOwner();
        if (ownerFilter == OWNER_FILTER_SELF) {
            if (!hasOwner || !entry.owner.equals(playerName)) {
                return false;
            }
        } else if (ownerFilter == OWNER_FILTER_OTHERS) {
            if (!hasOwner || entry.owner.equals(playerName)) {
                return false;
            }
        }

        int nameFilter = getNameFilter(stack);
        if (nameFilter == NAME_FILTER_UNNAMED && entry.hasAlias()) {
            return false;
        }
        if (nameFilter == NAME_FILTER_NAMED && !entry.hasAlias()) {
            return false;
        }
        return true;
    }

    public static String formatOwnerDisplay(String owner) {
        if (owner == null || owner.isEmpty()) {
            return StatCollector.translateToLocal("adm.scanner.owner_none");
        }
        return owner;
    }

    public static void syncToServer(EntityPlayer player, ItemStack stack) {
        if (player == null || stack == null) {
            return;
        }
        int slot = player.inventory.currentItem;
        NBTTagCompound nbt = stack.getTagCompound();
        if (nbt == null) {
            nbt = new NBTTagCompound();
        }
        AdvanceDataMonitor.ADMCHANEL.sendToServer(PacketLinkScannerAction.sync(slot, (NBTTagCompound) nbt.copy()));
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        list.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("adm.scanner.tooltip.shift_scan"));
        list.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("adm.scanner.tooltip.open_gui"));
        list.add(EnumChatFormatting.DARK_GRAY + StatCollector.translateToLocal("adm.scanner.tooltip.storage_hint"));
    }
}
