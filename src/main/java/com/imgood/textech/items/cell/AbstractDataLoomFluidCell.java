package com.imgood.textech.items.cell;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidStack;

import com.imgood.textech.Config;
import com.imgood.textech.compat.ae.AeCompat;

import appeng.api.config.FuzzyMode;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.core.localization.GuiText;
import appeng.tile.inventory.IAEStackInventory;
import appeng.util.item.AEFluidStackType;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Shared base for fluid-channel data loom cells. Uses AE2 2.9 {@link appeng.items.contents.CellConfig}
 * via {@link DataLoomFluidCellConfig} / factory for Cell Workbench partitions.
 */
public abstract class AbstractDataLoomFluidCell extends Item implements IDataLoomFluidCell {

    public AbstractDataLoomFluidCell() {
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
    public IAEStackInventory getConfigAEInventory(ItemStack is) {
        return AeCompat.fluidCellConfig()
            .createConfigInventory(is);
    }

    @Override
    public FuzzyMode getFuzzyMode(ItemStack is) {
        return FuzzyMode.IGNORE_ALL;
    }

    @Override
    public void setFuzzyMode(ItemStack is, FuzzyMode fzMode) {}

    @Override
    public int getBytes(ItemStack is) {
        long total = DataLoomCellCapacity.FLUID_TOTAL_BYTES;
        return total > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) total;
    }

    @Override
    public long getBytesLong(ItemStack is) {
        return DataLoomCellCapacity.FLUID_TOTAL_BYTES;
    }

    @Override
    public int BytePerType(ItemStack cell) {
        return getBytesPerType(cell);
    }

    @Override
    public int getBytesPerType(ItemStack is) {
        return DataLoomCellCapacity.getFluidBytesPerTypeForApi();
    }

    @Override
    public boolean isBlackListed(IAEStack requestedAddition) {
        return DataLoomCellUtil.isForbiddenFluidPartitionStack(requestedAddition);
    }

    @Override
    public boolean storableInStorageCell() {
        return false;
    }

    @Override
    public boolean isStorageCell(ItemStack is) {
        // Must be false so AE2FC default fluid handler does not replace DataLoomCellHandler.
        return false;
    }

    @Override
    public double getIdleDrain() {
        return 0.0D;
    }

    @Override
    public IAEStackType getStackType() {
        return AEFluidStackType.FLUID_STACK_TYPE;
    }

    @Override
    public int getTotalTypes(ItemStack is) {
        return getMaxFluidTypes();
    }

    /** Maximum distinct fluid types this cell can store (subclasses may override). */
    public int getMaxFluidTypes() {
        return DataLoomCellCapacity.FLUID_MAX_TYPES;
    }

    @Override
    public List<FluidStack> getMarkedFluids(ItemStack cellStack) {
        return DataLoomCellUtil.resolveMarkedFluids(cellStack);
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
        long[] stats = DataLoomCellUtil.readFluidStorageStats(stack);
        long storedTypes = stats[0];
        long usedBytes = stats[2];
        long totalBytes = DataLoomCellCapacity.FLUID_TOTAL_BYTES;
        int maxTypes = getMaxFluidTypes();

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

    protected void addCommonFluidTooltip(ItemStack stack, List tooltip) {
        appendStorageTooltip(stack, tooltip);
        tooltip.add("");
        DataLoomCellTooltipCache.appendCachedLines(stack, tooltip);
        tooltip.add("");
        tooltip.add(net.minecraft.util.StatCollector.translateToLocal("adm.tooltip.data_loom.energy_drain"));
        tooltip.add(net.minecraft.util.StatCollector.translateToLocal("adm.tooltip.data_loom.amplifier_only"));
        tooltip.add(
            String.format(
                net.minecraft.util.StatCollector.translateToLocal("adm.tooltip.data_loom.sync_interval"),
                Config.dataLoomCellSyncIntervalSeconds));
        tooltip.add(net.minecraft.util.StatCollector.translateToLocal("adm.tooltip.data_loom.storage_link_excluded"));
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings("unchecked")
    public void addInformation(ItemStack stack, EntityPlayer player, List tooltip, boolean advanced) {
        addCommonFluidTooltip(stack, tooltip);
    }
}
