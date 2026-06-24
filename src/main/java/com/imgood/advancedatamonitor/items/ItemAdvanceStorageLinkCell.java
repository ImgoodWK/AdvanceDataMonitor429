package com.imgood.advancedatamonitor.items;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidStack;

import appeng.api.config.FuzzyMode;
import appeng.api.config.Upgrades;
import appeng.api.implementations.items.IUpgradeModule;
import appeng.api.storage.ICellWorkbenchItem;
import appeng.items.contents.CellConfig;
import appeng.items.contents.CellUpgrades;

/**
 * Display names / 显示名称:
 * - EN: Advanced Storage Link Cell
 * - ZH: 高级存储链接元件
 * Lang keys: item.advanceStorageLinkCell.name
 *
 * Cell used by Advanced Storage Linker. The AE cell workbench partition slots define
 * which items this cell contributes to the Storage Link display.
 */
public class ItemAdvanceStorageLinkCell extends Item implements ICellWorkbenchItem {

    public ItemAdvanceStorageLinkCell() {
        this.setMaxStackSize(1);
    }

    @Override
    public ItemStack onItemRightClick(ItemStack is, World w, EntityPlayer p) {
        return is;
    }

    @Override
    public boolean isEditable(ItemStack is) {
        return true;
    }

    @Override
    public IInventory getUpgradesInventory(ItemStack is) {
        return new CellUpgrades(is, 2);
    }

    @Override
    public IInventory getConfigInventory(ItemStack is) {
        return new CellConfig(is);
    }

    public FuzzyMode getFuzzyMode(ItemStack is) {
        if (is == null) return FuzzyMode.IGNORE_ALL;
        NBTTagCompound tag = getOrCreateTag(is);
        try {
            return FuzzyMode.valueOf(tag.getString("FuzzyMode"));
        } catch (Throwable ignored) {
            return FuzzyMode.IGNORE_ALL;
        }
    }

    public void setFuzzyMode(ItemStack is, FuzzyMode fzMode) {
        if (is == null || fzMode == null) return;
        getOrCreateTag(is).setString("FuzzyMode", fzMode.name());
    }

    public boolean hasFuzzyCard(ItemStack is) {
        return hasUpgrade(is, Upgrades.FUZZY);
    }

    public boolean hasInverterCard(ItemStack is) {
        return hasUpgrade(is, Upgrades.INVERTER);
    }

    private boolean hasUpgrade(ItemStack is, Upgrades upgrade) {
        if (is == null || upgrade == null) return false;
        IInventory upgrades = getUpgradesInventory(is);
        for (int slot = 0; slot < upgrades.getSizeInventory(); slot++) {
            ItemStack upgradeStack = upgrades.getStackInSlot(slot);
            if (upgradeStack != null && upgradeStack.getItem() instanceof IUpgradeModule) {
                Upgrades type = ((IUpgradeModule) upgradeStack.getItem()).getType(upgradeStack);
                if (type == upgrade) return true;
            }
        }
        return false;
    }

    private NBTTagCompound getOrCreateTag(ItemStack is) {
        if (is.getTagCompound() == null) is.setTagCompound(new NBTTagCompound());
        return is.getTagCompound();
    }

    @Override
    public String getOreFilter(ItemStack is) {
        return ICellWorkbenchItem.super.getOreFilter(is);
    }

    @Override
    public void setOreFilter(ItemStack is, String filter) {
        ICellWorkbenchItem.super.setOreFilter(is, filter);
    }

    public boolean hasOreCard(ItemStack is) {
        String filter = getOreFilter(is);
        return filter != null && !filter.isEmpty();
    }

    public List<FluidStack> getFluidMarkers(ItemStack is) {
        List<FluidStack> markers = new ArrayList<>();
        if (is == null || is.getTagCompound() == null) return markers;
        NBTTagCompound tag = is.getTagCompound();
        if (!tag.hasKey("fluidMarkers")) return markers;

        NBTTagList fluidList = tag.getTagList("fluidMarkers", 10);
        for (int i = 0; i < fluidList.tagCount(); i++) {
            NBTTagCompound fluidTag = fluidList.getCompoundTagAt(i);
            FluidStack fluidStack = FluidStack.loadFluidStackFromNBT(fluidTag);
            if (fluidStack != null) markers.add(fluidStack);
        }
        return markers;
    }

    public void setFluidMarkers(ItemStack is, List<FluidStack> fluidStacks) {
        if (is == null) return;
        NBTTagCompound tag = getOrCreateTag(is);
        NBTTagList fluidList = new NBTTagList();
        if (fluidStacks != null) {
            for (FluidStack fs : fluidStacks) {
                if (fs != null) {
                    NBTTagCompound fluidTag = new NBTTagCompound();
                    fs.writeToNBT(fluidTag);
                    fluidList.appendTag(fluidTag);
                }
            }
        }
        tag.setTag("fluidMarkers", fluidList);
    }

    public boolean hasFluidMarker(ItemStack is) {
        if (is == null || is.getTagCompound() == null) return false;
        NBTTagCompound tag = is.getTagCompound();
        return tag.hasKey("fluidMarkers") && tag.getTagList("fluidMarkers", 10)
            .tagCount() > 0;
    }

    // ===== Essentia Marker support (Thaumcraft) =====

    /**
     * Simple container for an essentia marker: aspect tag name + amount.
     */
    public static class EssentiaMarker {

        public final String aspectTag;
        public final int amount;

        public EssentiaMarker(String aspectTag, int amount) {
            this.aspectTag = aspectTag;
            this.amount = amount;
        }
    }

    public List<EssentiaMarker> getEssentiaMarkers(ItemStack is) {
        List<EssentiaMarker> markers = new ArrayList<>();
        if (is == null || is.getTagCompound() == null) return markers;
        NBTTagCompound tag = is.getTagCompound();
        if (!tag.hasKey("essentiaMarkers")) return markers;

        NBTTagList essentiaList = tag.getTagList("essentiaMarkers", 10);
        for (int i = 0; i < essentiaList.tagCount(); i++) {
            NBTTagCompound entry = essentiaList.getCompoundTagAt(i);
            String aspectTag = entry.getString("aspect");
            int amount = entry.getInteger("amount");
            if (aspectTag != null && !aspectTag.isEmpty()) {
                markers.add(new EssentiaMarker(aspectTag, amount));
            }
        }
        return markers;
    }

    public void setEssentiaMarkers(ItemStack is, List<EssentiaMarker> markers) {
        if (is == null) return;
        NBTTagCompound tag = getOrCreateTag(is);
        NBTTagList essentiaList = new NBTTagList();
        if (markers != null) {
            for (EssentiaMarker marker : markers) {
                if (marker != null && marker.aspectTag != null && !marker.aspectTag.isEmpty()) {
                    NBTTagCompound entry = new NBTTagCompound();
                    entry.setString("aspect", marker.aspectTag);
                    entry.setInteger("amount", marker.amount);
                    essentiaList.appendTag(entry);
                }
            }
        }
        tag.setTag("essentiaMarkers", essentiaList);
    }

    public boolean hasEssentiaMarker(ItemStack is) {
        if (is == null || is.getTagCompound() == null) return false;
        NBTTagCompound tag = is.getTagCompound();
        return tag.hasKey("essentiaMarkers") && tag.getTagList("essentiaMarkers", 10)
            .tagCount() > 0;
    }

    public List<ItemStack> getMarkedItems(ItemStack cellStack) {
        List<ItemStack> markedItems = new ArrayList<>();
        if (cellStack == null || cellStack.getItem() != this) return markedItems;

        IInventory config = getConfigInventory(cellStack);
        for (int slot = 0; slot < config.getSizeInventory(); slot++) {
            ItemStack marked = config.getStackInSlot(slot);
            if (marked != null && marked.getItem() != null) {
                ItemStack copy = marked.copy();
                copy.stackSize = 1;
                markedItems.add(copy);
            }
        }
        return markedItems;
    }
}
