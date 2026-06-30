package com.imgood.textech.items.cell;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import com.imgood.textech.Config;

import appeng.api.config.FuzzyMode;
import appeng.api.implementations.items.IStorageCell;
import appeng.api.storage.ICellWorkbenchItem;
import appeng.api.storage.data.IAEItemStack;
import appeng.core.localization.GuiText;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public abstract class AbstractDataLoomItemCell extends Item implements IStorageCell, ICellWorkbenchItem {

    public AbstractDataLoomItemCell() {
        this.setMaxStackSize(1);
    }

    @Override
    public boolean isEditable(ItemStack is) {
        return true;
    }

    @Override
    public IInventory getUpgradesInventory(ItemStack is) {
        return DataLoomCellUtil.createUpgradesInventory(is);
    }

    @Override
    public FuzzyMode getFuzzyMode(ItemStack is) {
        return FuzzyMode.IGNORE_ALL;
    }

    @Override
    public void setFuzzyMode(ItemStack is, FuzzyMode fzMode) {}

    @Override
    public int getBytes(ItemStack cellItem) {
        long total = getBytesLong(cellItem);
        if (total > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) total;
    }

    @Override
    public long getBytesLong(ItemStack cellItem) {
        return DataLoomCellCapacity.ITEM_TOTAL_BYTES;
    }

    @Override
    public int BytePerType(ItemStack cell) {
        return getBytesPerType(cell);
    }

    @Override
    public int getBytesPerType(ItemStack cellItem) {
        return DataLoomCellCapacity.getItemBytesPerTypeForApi();
    }

    @Override
    public int getTotalTypes(ItemStack cellItem) {
        return DataLoomCellCapacity.ITEM_MAX_TYPES;
    }

    @Override
    public boolean isBlackListed(ItemStack cellItem, IAEItemStack requestedAddition) {
        if (requestedAddition == null) {
            return false;
        }
        ItemStack stack = requestedAddition.getItemStack();
        return DataLoomCellUtil.isModOwnItem(stack);
    }

    @Override
    public boolean storableInStorageCell() {
        return false;
    }

    @Override
    public boolean isStorageCell(ItemStack is) {
        // Must be false so AE2/AE2FC default cell handlers do not replace DataLoomCellHandler.
        return false;
    }

    @Override
    public double getIdleDrain() {
        return 0.0D;
    }

    public abstract double getItemRatePerSecond();

    public List<ItemStack> getMarkedItems(ItemStack cellStack) {
        return DataLoomCellUtil.resolveMarkedItems(cellStack);
    }

    @Override
    public ItemStack onItemRightClick(ItemStack is, World w, EntityPlayer p) {
        return is;
    }

    @Override
    public boolean showDurabilityBar(ItemStack stack) {
        return false;
    }

    @SideOnly(Side.CLIENT)
    protected void appendStorageTooltip(ItemStack stack, List tooltip) {
        long[] stats = DataLoomCellUtil.readItemStorageStats(stack);
        long storedTypes = stats[0];
        long usedBytes = stats[2];
        long totalBytes = DataLoomCellCapacity.ITEM_TOTAL_BYTES;
        int maxTypes = DataLoomCellCapacity.ITEM_MAX_TYPES;

        NumberFormat nf = NumberFormat.getInstance(Locale.ENGLISH);
        tooltip.add(
            EnumChatFormatting.WHITE + nf.format(usedBytes)
                + EnumChatFormatting.GRAY
                + " "
                + GuiText.Of.getLocal()
                + " "
                + EnumChatFormatting.DARK_GREEN
                + NumberFormat.getInstance()
                    .format(totalBytes)
                + " "
                + EnumChatFormatting.GRAY
                + GuiText.BytesUsed.getLocal());
        tooltip.add(
            EnumChatFormatting.WHITE + nf.format(storedTypes)
                + EnumChatFormatting.GRAY
                + " "
                + GuiText.Of.getLocal()
                + " "
                + EnumChatFormatting.DARK_GREEN
                + NumberFormat.getInstance()
                    .format(maxTypes)
                + " "
                + EnumChatFormatting.GRAY
                + GuiText.Types.getLocal());
    }

    protected void addCommonTooltip(ItemStack stack, List tooltip) {
        appendStorageTooltip(stack, tooltip);
        tooltip.add("");
        DataLoomCellTooltipCache.appendCachedLines(stack, tooltip);
        tooltip.add("");
        tooltip.add(StatCollector.translateToLocal("adm.tooltip.data_loom.energy_drain"));
        tooltip.add(StatCollector.translateToLocal("adm.tooltip.data_loom.amplifier_only"));
        tooltip.add(
            String.format(
                StatCollector.translateToLocal("adm.tooltip.data_loom.sync_interval"),
                Config.dataLoomCellSyncIntervalSeconds));
        tooltip.add(StatCollector.translateToLocal("adm.tooltip.data_loom.storage_link_excluded"));
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings("unchecked")
    public void addInformation(ItemStack stack, EntityPlayer player, List tooltip, boolean advanced) {
        addCommonTooltip(stack, tooltip);
    }
}
