package com.imgood.textech.tileentity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ChatComponentText;
import net.minecraftforge.common.util.Constants;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.items.ItemAdvanceStorageLinkCell;
import com.imgood.textech.network.packet.PacketItemCountSync;

import appeng.api.AEApi;
import appeng.api.config.FuzzyMode;
import appeng.api.networking.IGrid;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.me.GridAccessException;
import appeng.tile.TileEvent;
import appeng.tile.events.TileEventType;
import appeng.tile.grid.AENetworkTile;
import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.NetworkRegistry;
import io.netty.buffer.ByteBuf;

/**
 * Display names / 显示名称:
 * - EN: Advanced Storage Linker
 * - ZH: 高级存储链接器
 * Lang keys: tile.StorageLinkBlock.name (parent block)
 */
public class TileEntityAdvanceStorageLink extends AENetworkTile implements IInventory, IOwnableTile {

    private String ownerName = "";

    private static final int SLOT_COUNT = 36;
    private static final int DELTA_STATISTICS_INTERVAL_TICKS = 20;
    private final ItemStack[] cellItems = new ItemStack[SLOT_COUNT];
    private final Map<Integer, Long> itemCountCache = new HashMap<>();
    private final Map<String, Long> previousSnapshotCounts = new HashMap<>();
    private final Map<String, Long> displayedSnapshotDeltas = new HashMap<>();
    private long lastDeltaSampleTick = -1L;
    private boolean requiresNbtSync = false;

    private static final String CELL_ITEMS_TAG = "AdvStorageLink_CellItems";
    private static final String LEGACY_MARKED_ITEMS_TAG = "AdvStorageLink_MarkedItems";
    private static final String CACHE_DATA_TAG = "AdvStorageLink_CacheData";

    @Override
    public String getOwnerName() {
        return ownerName == null ? "" : ownerName;
    }

    @Override
    public void setOwnerName(String name) {
        this.ownerName = name == null ? "" : name;
        markDirty();
    }

    @Override
    public void setOwnerFromPlacer(EntityLivingBase placer) {
        setOwnerName(OwnableTileUtil.nameFromPlacer(placer));
    }

    @Override
    public void claimOwnerIfEmpty(EntityPlayer player) {
        // Storage link: no claim-on-open; re-place to set owner.
    }

    @Override
    public int getSizeInventory() {
        return cellItems.length;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        if (slot < 0 || slot >= getSizeInventory()) return null;
        return cellItems[slot];
    }

    @Override
    public ItemStack decrStackSize(int slot, int amount) {
        ItemStack stack = getStackInSlot(slot);
        if (stack == null) return null;

        if (stack.stackSize <= amount) {
            setInventorySlotContents(slot, null);
            return stack;
        }

        ItemStack split = stack.splitStack(amount);
        if (stack.stackSize == 0) setInventorySlotContents(slot, null);
        markForUpdate();
        return split;
    }

    @Override
    public ItemStack getStackInSlotOnClosing(int slot) {
        ItemStack stack = getStackInSlot(slot);
        setInventorySlotContents(slot, null);
        return stack;
    }

    @Override
    public void setInventorySlotContents(int slot, ItemStack stack) {
        if (slot < 0 || slot >= getSizeInventory()) return;
        if (stack != null && !isItemValidForSlot(slot, stack)) return;

        cellItems[slot] = stack;
        if (stack != null && stack.stackSize > 1) stack.stackSize = 1;
        updateItemCountCache(slot);
        markForUpdate();
        markDirty();
    }

    @Override
    public String getInventoryName() {
        return "Advance Storage Link";
    }

    @Override
    public boolean hasCustomInventoryName() {
        return false;
    }

    @Override
    public int getInventoryStackLimit() {
        return 1;
    }

    @Override
    public void markDirty() {
        super.markDirty();
        if (worldObj != null && !worldObj.isRemote) {
            worldObj.markTileEntityChunkModified(xCoord, yCoord, zCoord, this);
        }
    }

    @Override
    public boolean isUseableByPlayer(EntityPlayer player) {
        return worldObj.getTileEntity(xCoord, yCoord, zCoord) == this
            && player.getDistanceSq(xCoord + 0.5, yCoord + 0.5, zCoord + 0.5) <= 64;
    }

    @Override
    public void openInventory() {}

    @Override
    public void closeInventory() {}

    @Override
    public boolean isItemValidForSlot(int slot, ItemStack stack) {
        return isStorageLinkCell(stack);
    }

    public boolean isStorageLinkCell(ItemStack stack) {
        return stack != null && stack.getItem() instanceof ItemAdvanceStorageLinkCell;
    }

    public ItemStack getCellItem(int slot) {
        return getStackInSlot(slot);
    }

    public ItemStack getMarkedItem(int slot) {
        List<ItemStack> marked = getMarkedItems(slot);
        return marked.isEmpty() ? null : marked.get(0);
    }

    public List<ItemStack> getMarkedItems(int slot) {
        ItemStack cell = getCellItem(slot);
        if (cell != null && cell.getItem() instanceof ItemAdvanceStorageLinkCell) {
            return ((ItemAdvanceStorageLinkCell) cell.getItem()).getMarkedItems(cell);
        }
        return java.util.Collections.emptyList();
    }

    public boolean hasFuzzyCard(int slot) {
        ItemStack cell = getCellItem(slot);
        return cell != null && cell.getItem() instanceof ItemAdvanceStorageLinkCell
            && ((ItemAdvanceStorageLinkCell) cell.getItem()).hasFuzzyCard(cell);
    }

    public boolean hasOreCard(int slot) {
        ItemStack cell = getCellItem(slot);
        return cell != null && cell.getItem() instanceof ItemAdvanceStorageLinkCell
            && ((ItemAdvanceStorageLinkCell) cell.getItem()).hasOreCard(cell);
    }

    public boolean hasFluidMarker(int slot) {
        ItemStack cell = getCellItem(slot);
        return cell != null && cell.getItem() instanceof ItemAdvanceStorageLinkCell
            && ((ItemAdvanceStorageLinkCell) cell.getItem()).hasFluidMarker(cell);
    }

    public void setMarkedItem(int slot, ItemStack stack, EntityPlayer player) {
        setInventorySlotContents(slot, stack);
    }

    public void saveMarkedItem(int slot, ItemStack stack) {
        setInventorySlotContents(slot, stack);
    }

    public void markForUpdate() {
        markDirty();
        if (worldObj != null && !worldObj.isRemote) {
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
            requiresNbtSync = true;
        }
    }

    private void updateItemCountCache(int slot) {
        if (worldObj == null || worldObj.isRemote) return;
        ItemStack cell = getCellItem(slot);
        if (cell == null) {
            itemCountCache.remove(slot);
            return;
        }

        ItemAdvanceStorageLinkCell cellItem = (ItemAdvanceStorageLinkCell) cell.getItem();

        if (cellItem.hasOreCard(cell)) {
            try {
                itemCountCache.put(slot, countOreDictMatches(getStorageGrid(), cellItem.getOreFilter(cell)));
            } catch (GridAccessException e) {
                itemCountCache.put(slot, 0L);
            }
            return;
        }

        if (cellItem.hasFluidMarker(cell)) {
            long total = 0;
            for (net.minecraftforge.fluids.FluidStack marker : cellItem.getFluidMarkers(cell)) {
                try {
                    total += getFluidAmountInNetwork(marker);
                } catch (GridAccessException e) {
                    AdvanceDataMonitor.LOG.error("Failed to get fluid amount for cache: " + e.getMessage());
                }
            }
            itemCountCache.put(slot, total);
            return;
        }

        if (cellItem.hasEssentiaMarker(cell)) {
            long total = 0;
            for (ItemAdvanceStorageLinkCell.EssentiaMarker marker : cellItem.getEssentiaMarkers(cell)) {
                try {
                    total += getEssentiaAmountInNetwork(marker.aspectTag);
                } catch (Exception e) {
                    AdvanceDataMonitor.LOG.error("Failed to get essentia amount for cache: " + e.getMessage());
                }
            }
            itemCountCache.put(slot, total);
            return;
        }

        boolean useFuzzy = cellItem.hasFuzzyCard(cell);
        FuzzyMode fuzzyMode = useFuzzy ? cellItem.getFuzzyMode(cell) : null;
        long total = 0;
        for (ItemStack marked : cellItem.getMarkedItems(cell)) {
            try {
                total += getItemCountInNetwork(marked, fuzzyMode);
            } catch (GridAccessException e) {
                AdvanceDataMonitor.LOG.error("Failed to update item count cache: " + e.getMessage());
            }
        }
        itemCountCache.put(slot, total);
    }

    public long getCachedItemCount(int slot) {
        return itemCountCache.getOrDefault(slot, 0L);
    }

    public NBTTagList createStorageItemsSnapshot() {
        NBTTagList list = new NBTTagList();
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            ItemStack cell = getCellItem(slot);
            if (!isStorageLinkCell(cell)) continue;

            ItemAdvanceStorageLinkCell cellItem = (ItemAdvanceStorageLinkCell) cell.getItem();

            if (cellItem.hasOreCard(cell)) {
                appendOreDictStorageItems(list, slot, cellItem.getOreFilter(cell));
                continue;
            }

            if (cellItem.hasFluidMarker(cell)) {
                appendFluidStorageItems(list, slot, cellItem);
                continue;
            }

            if (cellItem.hasEssentiaMarker(cell)) {
                appendEssentiaStorageItems(list, slot, cellItem);
                continue;
            }

            List<ItemStack> markedItems = cellItem.getMarkedItems(cell);
            boolean useFuzzy = cellItem.hasFuzzyCard(cell);
            FuzzyMode fuzzyMode = useFuzzy ? cellItem.getFuzzyMode(cell) : null;

            if (cellItem.hasInverterCard(cell)) {
                appendInvertedStorageItems(list, slot, markedItems, fuzzyMode);
            } else {
                appendMarkedStorageItems(list, slot, markedItems, fuzzyMode);
            }
        }
        return list;
    }

    private void appendOreDictStorageItems(NBTTagList list, int slot, String oreFilter) {
        try {
            IStorageGrid storageGrid = getStorageGrid();
            if (storageGrid == null) return;

            java.util.List<ItemStack> oreStacks = net.minecraftforge.oredict.OreDictionary.getOres(oreFilter);
            if (oreStacks.isEmpty()) return;

            int markedIndex = 0;
            for (IAEItemStack stored : storageGrid.getItemInventory()
                .getStorageList()) {
                if (stored == null) continue;
                ItemStack storedStack = stored.getItemStack();
                if (storedStack == null || storedStack.getItem() == null) continue;

                boolean matchesOre = false;
                for (ItemStack oreStack : oreStacks) {
                    if (oreStack != null && oreStack.getItem() == storedStack.getItem()
                        && (oreStack.getItemDamage() == net.minecraftforge.oredict.OreDictionary.WILDCARD_VALUE
                            || oreStack.getItemDamage() == storedStack.getItemDamage())) {
                        matchesOre = true;
                        break;
                    }
                }
                if (matchesOre) {
                    ItemStack displayStack = storedStack.copy();
                    displayStack.stackSize = 1;
                    appendStorageEntry(list, slot, markedIndex++, displayStack, stored.getStackSize());
                }
            }
        } catch (GridAccessException e) {
            AdvanceDataMonitor.LOG.error("Error getting ore dict storage for slot " + slot, e);
        }
    }

    private void appendFluidStorageItems(NBTTagList list, int slot, ItemAdvanceStorageLinkCell cellItem) {
        try {
            IGrid grid = this.getProxy()
                .getGrid();
            if (grid == null) return;

            IStorageGrid storageGrid = grid.getCache(IStorageGrid.class);
            if (storageGrid == null) return;

            java.util.List<net.minecraftforge.fluids.FluidStack> fluidMarkers = cellItem
                .getFluidMarkers(getCellItem(slot));
            if (fluidMarkers == null || fluidMarkers.isEmpty()) return;

            int markedIndex = 0;
            for (net.minecraftforge.fluids.FluidStack marker : fluidMarkers) {
                if (marker == null || marker.getFluid() == null) continue;
                long amount = 0;
                try {
                    amount = getFluidAmountInNetwork(marker);
                } catch (GridAccessException e) {
                    AdvanceDataMonitor.LOG.error("Error getting fluid amount for slot " + slot, e);
                }
                ItemStack displayStack = new ItemStack(net.minecraft.init.Items.water_bucket);
                displayStack.setStackDisplayName(
                    marker.getLocalizedName() + " ("
                        + marker.getFluid()
                            .getName()
                        + ")");
                NBTTagCompound fluidTag = new NBTTagCompound();
                marker.writeToNBT(fluidTag);
                displayStack.setTagCompound(fluidTag);
                appendStorageEntry(list, slot, markedIndex++, displayStack, amount, "fluid");
            }
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.error("Error getting fluid storage for slot " + slot, e);
        }
    }

    private void appendEssentiaStorageItems(NBTTagList list, int slot, ItemAdvanceStorageLinkCell cellItem) {
        java.util.List<ItemAdvanceStorageLinkCell.EssentiaMarker> essentiaMarkers = cellItem
            .getEssentiaMarkers(getCellItem(slot));
        if (essentiaMarkers == null || essentiaMarkers.isEmpty()) return;

        int markedIndex = 0;
        for (ItemAdvanceStorageLinkCell.EssentiaMarker marker : essentiaMarkers) {
            if (marker == null || marker.aspectTag == null || marker.aspectTag.isEmpty()) continue;
            long amount = 0;
            try {
                amount = getEssentiaAmountInNetwork(marker.aspectTag);
            } catch (Exception e) {
                AdvanceDataMonitor.LOG.error("Error getting essentia amount for slot " + slot, e);
            }
            ItemStack displayStack = new ItemStack(net.minecraft.init.Items.water_bucket);
            displayStack.setStackDisplayName("§5" + marker.aspectTag + "§r (Essentia)");
            NBTTagCompound essentiaTag = new NBTTagCompound();
            essentiaTag.setString("aspectTag", marker.aspectTag);
            displayStack.setTagCompound(essentiaTag);
            appendStorageEntry(list, slot, markedIndex++, displayStack, amount, "essentia");
        }
    }

    private long getEssentiaAmountInNetwork(String aspectTag) {
        if (aspectTag == null || aspectTag.isEmpty()) return 0;
        try {
            IGrid grid = this.getProxy()
                .getGrid();
            if (grid == null) return 0;

            IStorageGrid storageGrid = grid.getCache(IStorageGrid.class);
            if (storageGrid == null) return 0;

            // Essentia in GTNH is stored as fluid with the aspect tag as fluid name
            net.minecraftforge.fluids.Fluid essentiaFluid = net.minecraftforge.fluids.FluidRegistry.getFluid(aspectTag);
            if (essentiaFluid == null) return 0;

            IAEFluidStack query = AEApi.instance()
                .storage()
                .createFluidStack(new net.minecraftforge.fluids.FluidStack(essentiaFluid, 1));
            if (query == null) return 0;

            long total = 0;
            for (IAEFluidStack stored : storageGrid.getFluidInventory()
                .getStorageList()) {
                if (stored != null && stored.getFluidStack() != null
                    && stored.getFluidStack()
                        .getFluid() != null
                    && stored.getFluidStack()
                        .getFluid()
                        .getID() == essentiaFluid.getID()) {
                    total += stored.getStackSize();
                }
            }
            return total;
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.error("Error getting essentia amount for " + aspectTag + ": " + e.getMessage());
            return 0;
        }
    }

    private void appendMarkedStorageItems(NBTTagList list, int slot, List<ItemStack> markedItems, FuzzyMode fuzzyMode) {
        int markedIndex = 0;
        for (ItemStack marked : markedItems) {
            long count = 0;
            try {
                count = getItemCountInNetwork(marked, fuzzyMode);
            } catch (GridAccessException e) {
                AdvanceDataMonitor.LOG.error("Error getting item count for slot " + slot, e);
            }
            appendStorageEntry(list, slot, markedIndex++, marked, count);
        }
    }

    private void appendInvertedStorageItems(NBTTagList list, int slot, List<ItemStack> markedItems,
        FuzzyMode fuzzyMode) {
        try {
            int markedIndex = 0;
            for (IAEItemStack stored : getStoredItemStacks()) {
                if (stored == null) continue;
                ItemStack stack = stored.getItemStack();
                if (stack == null || stack.getItem() == null) continue;
                if (matchesAnyMarkedItem(stack, markedItems, fuzzyMode)) continue;
                stack.stackSize = 1;
                appendStorageEntry(list, slot, markedIndex++, stack, stored.getStackSize());
            }
        } catch (GridAccessException e) {
            AdvanceDataMonitor.LOG.error("Error getting inverted storage snapshot for slot " + slot, e);
        }
    }

    private void appendStorageEntry(NBTTagList list, int slot, int markedIndex, ItemStack stack, long count,
        String type) {
        NBTTagCompound itemTag = new NBTTagCompound();
        stack.writeToNBT(itemTag);

        String snapshotKey = createSnapshotKey(slot, markedIndex, stack);
        long countDelta = displayedSnapshotDeltas.containsKey(snapshotKey) ? displayedSnapshotDeltas.get(snapshotKey)
            : 0L;

        NBTTagCompound entry = new NBTTagCompound();
        entry.setInteger("slot", slot);
        entry.setInteger("markedIndex", markedIndex);
        entry.setTag("item", itemTag);
        entry.setLong("count", count);
        entry.setLong("countDelta", countDelta);
        entry.setString("displayName", stack.getDisplayName());
        entry.setString("type", type);
        list.appendTag(entry);
    }

    private void appendStorageEntry(NBTTagList list, int slot, int markedIndex, ItemStack stack, long count) {
        appendStorageEntry(list, slot, markedIndex, stack, count, "item");
    }

    private String createSnapshotKey(int slot, int markedIndex, ItemStack stack) {
        String itemName = stack.getItem()
            .getUnlocalizedName(stack);
        return slot + ":" + markedIndex + ":" + itemName + ":" + stack.getItemDamage();
    }

    public long getItemCountInNetwork(ItemStack stack) throws GridAccessException {
        return getItemCountInNetwork(stack, null, null);
    }

    public long getItemCountInNetwork(ItemStack stack, String oreFilter) throws GridAccessException {
        return getItemCountInNetwork(stack, null, oreFilter);
    }

    public long getItemCountInNetwork(ItemStack stack, FuzzyMode fuzzyMode) throws GridAccessException {
        return getItemCountInNetwork(stack, fuzzyMode, null);
    }

    public long getItemCountInNetwork(ItemStack stack, FuzzyMode fuzzyMode, String oreFilter)
        throws GridAccessException {
        if (stack == null || stack.getItem() == null) return 0;

        try {
            IStorageGrid storageGrid = getStorageGrid();
            if (storageGrid == null) return 0;

            IAEItemStack query = AEApi.instance()
                .storage()
                .createItemStack(stack);
            if (query == null) return 0;

            boolean useFuzzy = fuzzyMode != null && fuzzyMode != FuzzyMode.IGNORE_ALL;

            if (useFuzzy) {
                java.util.Collection<IAEItemStack> storedItems = storageGrid.getItemInventory()
                    .getStorageList()
                    .findFuzzy(query, fuzzyMode);
                long total = 0;
                for (IAEItemStack item : storedItems) total += item.getStackSize();
                return total;
            }

            if (oreFilter != null && !oreFilter.isEmpty()) {
                return countOreDictMatches(storageGrid, oreFilter);
            }

            return countExactMatches(storageGrid, stack);
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.error("Error getting item count: " + e.getMessage());
            return 0;
        }
    }

    private long countExactMatches(IStorageGrid storageGrid, ItemStack target) {
        if (target == null || target.getItem() == null) return 0;
        long total = 0;
        for (IAEItemStack stored : storageGrid.getItemInventory()
            .getStorageList()) {
            if (stored == null) continue;
            ItemStack storedStack = stored.getItemStack();
            if (storedStack != null && target.isItemEqual(storedStack)) {
                total += stored.getStackSize();
            }
        }
        return total;
    }

    private long countOreDictMatches(IStorageGrid storageGrid, String oreName) {
        long total = 0;
        java.util.List<ItemStack> oreStacks = net.minecraftforge.oredict.OreDictionary.getOres(oreName);
        if (oreStacks.isEmpty()) return 0;

        for (IAEItemStack stored : storageGrid.getItemInventory()
            .getStorageList()) {
            if (stored == null) continue;
            ItemStack storedStack = stored.getItemStack();
            if (storedStack == null) continue;

            for (ItemStack oreStack : oreStacks) {
                if (oreStack != null && oreStack.getItem() == storedStack.getItem()
                    && (oreStack.getItemDamage() == net.minecraftforge.oredict.OreDictionary.WILDCARD_VALUE
                        || oreStack.getItemDamage() == storedStack.getItemDamage())) {
                    total += stored.getStackSize();
                    break;
                }
            }
        }
        return total;
    }

    private List<IAEItemStack> getStoredItemStacks() throws GridAccessException {
        List<IAEItemStack> items = new ArrayList<>();
        IStorageGrid storageGrid = getStorageGrid();
        if (storageGrid == null) return items;

        for (IAEItemStack item : storageGrid.getItemInventory()
            .getStorageList()) {
            if (item != null) items.add(item.copy());
        }
        return items;
    }

    private IStorageGrid getStorageGrid() throws GridAccessException {
        IGrid grid = this.getProxy()
            .getGrid();
        return grid == null ? null : grid.getCache(IStorageGrid.class);
    }

    private boolean matchesAnyMarkedItem(ItemStack stack, List<ItemStack> markedItems, FuzzyMode fuzzyMode) {
        if (markedItems == null || markedItems.isEmpty()) return false;
        boolean useFuzzy = fuzzyMode != null && fuzzyMode != FuzzyMode.IGNORE_ALL;
        for (ItemStack marked : markedItems) {
            if (marked == null || marked.getItem() == null) continue;
            try {
                if (useFuzzy) {
                    IAEItemStack query = AEApi.instance()
                        .storage()
                        .createItemStack(marked);
                    IAEItemStack candidate = AEApi.instance()
                        .storage()
                        .createItemStack(stack);
                    if (query != null && candidate != null && candidate.fuzzyComparison(query, fuzzyMode)) {
                        return true;
                    }
                } else {
                    if (marked.isItemEqual(stack)) return true;
                }
            } catch (Throwable ignored) {
                if (marked.isItemEqual(stack)) return true;
            }
        }
        return false;
    }

    public long getFluidAmountInNetwork(net.minecraftforge.fluids.FluidStack fluidStack) throws GridAccessException {
        if (fluidStack == null || fluidStack.getFluid() == null) return 0;

        try {
            IGrid grid = this.getProxy()
                .getGrid();
            if (grid == null) return 0;

            IStorageGrid storageGrid = grid.getCache(IStorageGrid.class);
            if (storageGrid == null) return 0;

            IAEFluidStack query = AEApi.instance()
                .storage()
                .createFluidStack(fluidStack);
            if (query == null) return 0;

            long total = 0;
            for (IAEFluidStack stored : storageGrid.getFluidInventory()
                .getStorageList()) {
                if (stored != null && stored.getFluidStack() != null
                    && stored.getFluidStack()
                        .getFluid() != null
                    && stored.getFluidStack()
                        .getFluid()
                        .getID()
                        == fluidStack.getFluid()
                            .getID()) {
                    total += stored.getStackSize();
                }
            }
            return total;
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.error("Error getting fluid amount: " + e.getMessage());
            return 0;
        }
    }

    @TileEvent(TileEventType.WORLD_NBT_WRITE)
    public void writeCustomDataToNBT(NBTTagCompound nbt) {
        NBTTagList list = new NBTTagList();
        for (int i = 0; i < SLOT_COUNT; i++) {
            if (cellItems[i] != null) {
                NBTTagCompound tag = new NBTTagCompound();
                tag.setInteger("Slot", i);
                cellItems[i].writeToNBT(tag);
                list.appendTag(tag);
            }
        }
        nbt.setTag(CELL_ITEMS_TAG, list);

        if (worldObj != null && !worldObj.isRemote) {
            NBTTagCompound cacheNbt = new NBTTagCompound();
            for (Map.Entry<Integer, Long> entry : itemCountCache.entrySet()) {
                cacheNbt.setLong("slot_" + entry.getKey(), entry.getValue());
            }
            nbt.setTag(CACHE_DATA_TAG, cacheNbt);
        }
        OwnableTileUtil.writeOwner(nbt, ownerName);
    }

    @TileEvent(TileEventType.WORLD_NBT_READ)
    public void readCustomDataFromNBT(NBTTagCompound nbt) {
        for (int i = 0; i < SLOT_COUNT; i++) cellItems[i] = null;

        String itemTagName = nbt.hasKey(CELL_ITEMS_TAG) ? CELL_ITEMS_TAG : LEGACY_MARKED_ITEMS_TAG;
        if (nbt.hasKey(itemTagName)) {
            NBTTagList list = nbt.getTagList(itemTagName, Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < list.tagCount(); i++) {
                NBTTagCompound tag = list.getCompoundTagAt(i);
                int slot = tag.getInteger("Slot");
                ItemStack stack = ItemStack.loadItemStackFromNBT(tag);
                if (slot >= 0 && slot < SLOT_COUNT && isStorageLinkCell(stack)) cellItems[slot] = stack;
            }
        }

        itemCountCache.clear();
        if (nbt.hasKey(CACHE_DATA_TAG)) {
            NBTTagCompound cacheNbt = nbt.getCompoundTag(CACHE_DATA_TAG);
            for (int i = 0; i < SLOT_COUNT; i++) {
                String key = "slot_" + i;
                if (cacheNbt.hasKey(key)) itemCountCache.put(i, cacheNbt.getLong(key));
            }
        }
        ownerName = OwnableTileUtil.readOwner(nbt);
    }

    @TileEvent(TileEventType.TICK)
    public void onTickEvent() {
        if (worldObj != null && !worldObj.isRemote) {
            sampleStorageDeltasIfNeeded();
            if (requiresNbtSync) {
                worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
                requiresNbtSync = false;
            }
        }
    }

    private void sampleStorageDeltasIfNeeded() {
        long currentTick = worldObj.getTotalWorldTime();
        if (lastDeltaSampleTick >= 0 && currentTick - lastDeltaSampleTick < DELTA_STATISTICS_INTERVAL_TICKS) return;

        Map<String, Long> currentCounts = createCurrentStorageCounts();
        Map<String, Long> newDeltas = new HashMap<>();
        for (Map.Entry<String, Long> entry : currentCounts.entrySet()) {
            Long previousCount = previousSnapshotCounts.get(entry.getKey());
            newDeltas.put(entry.getKey(), previousCount == null ? 0L : entry.getValue() - previousCount);
        }

        previousSnapshotCounts.clear();
        previousSnapshotCounts.putAll(currentCounts);
        displayedSnapshotDeltas.clear();
        displayedSnapshotDeltas.putAll(newDeltas);
        lastDeltaSampleTick = currentTick;
    }

    private Map<String, Long> createCurrentStorageCounts() {
        Map<String, Long> currentCounts = new HashMap<>();
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            ItemStack cell = getCellItem(slot);
            if (!isStorageLinkCell(cell)) continue;

            ItemAdvanceStorageLinkCell cellItem = (ItemAdvanceStorageLinkCell) cell.getItem();

            if (cellItem.hasOreCard(cell)) {
                collectOreDictStorageCounts(currentCounts, slot, cellItem.getOreFilter(cell));
                continue;
            }

            if (cellItem.hasFluidMarker(cell)) {
                collectFluidStorageCounts(currentCounts, slot, cellItem);
                continue;
            }

            if (cellItem.hasEssentiaMarker(cell)) {
                collectEssentiaStorageCounts(currentCounts, slot, cellItem);
                continue;
            }

            List<ItemStack> markedItems = cellItem.getMarkedItems(cell);
            boolean useFuzzy = cellItem.hasFuzzyCard(cell);
            FuzzyMode fuzzyMode = useFuzzy ? cellItem.getFuzzyMode(cell) : null;
            if (cellItem.hasInverterCard(cell)) {
                collectInvertedStorageCounts(currentCounts, slot, markedItems, fuzzyMode);
            } else {
                collectMarkedStorageCounts(currentCounts, slot, markedItems, fuzzyMode);
            }
        }
        return currentCounts;
    }

    private void collectOreDictStorageCounts(Map<String, Long> currentCounts, int slot, String oreFilter) {
        try {
            IStorageGrid storageGrid = getStorageGrid();
            if (storageGrid == null) return;

            java.util.List<ItemStack> oreStacks = net.minecraftforge.oredict.OreDictionary.getOres(oreFilter);
            if (oreStacks.isEmpty()) return;

            int markedIndex = 0;
            for (IAEItemStack stored : storageGrid.getItemInventory()
                .getStorageList()) {
                if (stored == null) continue;
                ItemStack storedStack = stored.getItemStack();
                if (storedStack == null || storedStack.getItem() == null) continue;

                boolean matchesOre = false;
                for (ItemStack oreStack : oreStacks) {
                    if (oreStack != null && oreStack.getItem() == storedStack.getItem()
                        && (oreStack.getItemDamage() == net.minecraftforge.oredict.OreDictionary.WILDCARD_VALUE
                            || oreStack.getItemDamage() == storedStack.getItemDamage())) {
                        matchesOre = true;
                        break;
                    }
                }
                if (matchesOre) {
                    ItemStack displayStack = storedStack.copy();
                    displayStack.stackSize = 1;
                    currentCounts.put(createSnapshotKey(slot, markedIndex++, displayStack), stored.getStackSize());
                }
            }
        } catch (GridAccessException e) {
            AdvanceDataMonitor.LOG.error("Error sampling ore dict delta for slot " + slot, e);
        }
    }

    private void collectFluidStorageCounts(Map<String, Long> currentCounts, int slot,
        ItemAdvanceStorageLinkCell cellItem) {
        try {
            java.util.List<net.minecraftforge.fluids.FluidStack> fluidMarkers = cellItem
                .getFluidMarkers(getCellItem(slot));
            if (fluidMarkers == null || fluidMarkers.isEmpty()) return;

            int markedIndex = 0;
            for (net.minecraftforge.fluids.FluidStack marker : fluidMarkers) {
                if (marker == null || marker.getFluid() == null) continue;
                long amount = 0;
                try {
                    amount = getFluidAmountInNetwork(marker);
                } catch (GridAccessException e) {
                    AdvanceDataMonitor.LOG.error("Error sampling fluid delta for slot " + slot, e);
                }
                ItemStack displayStack = new ItemStack(net.minecraft.init.Items.water_bucket);
                displayStack.setStackDisplayName(
                    marker.getLocalizedName() + " ("
                        + marker.getFluid()
                            .getName()
                        + ")");
                currentCounts.put(createSnapshotKey(slot, markedIndex++, displayStack), amount);
            }
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.error("Error sampling fluid storage counts for slot " + slot, e);
        }
    }

    private void collectEssentiaStorageCounts(Map<String, Long> currentCounts, int slot,
        ItemAdvanceStorageLinkCell cellItem) {
        java.util.List<ItemAdvanceStorageLinkCell.EssentiaMarker> essentiaMarkers = cellItem
            .getEssentiaMarkers(getCellItem(slot));
        if (essentiaMarkers == null || essentiaMarkers.isEmpty()) return;

        int markedIndex = 0;
        for (ItemAdvanceStorageLinkCell.EssentiaMarker marker : essentiaMarkers) {
            if (marker == null || marker.aspectTag == null || marker.aspectTag.isEmpty()) continue;
            long amount = 0;
            try {
                amount = getEssentiaAmountInNetwork(marker.aspectTag);
            } catch (Exception e) {
                AdvanceDataMonitor.LOG.error("Error sampling essentia delta for slot " + slot, e);
            }
            ItemStack displayStack = new ItemStack(net.minecraft.init.Items.water_bucket);
            displayStack.setStackDisplayName("§5" + marker.aspectTag + "§r (Essentia)");
            currentCounts.put(createSnapshotKey(slot, markedIndex++, displayStack), amount);
        }
    }

    private void collectMarkedStorageCounts(Map<String, Long> currentCounts, int slot, List<ItemStack> markedItems,
        FuzzyMode fuzzyMode) {
        int markedIndex = 0;
        for (ItemStack marked : markedItems) {
            long count = 0L;
            try {
                count = getItemCountInNetwork(marked, fuzzyMode);
            } catch (GridAccessException e) {
                AdvanceDataMonitor.LOG.error("Error sampling item delta for slot " + slot, e);
            }
            currentCounts.put(createSnapshotKey(slot, markedIndex++, marked), count);
        }
    }

    private void collectInvertedStorageCounts(Map<String, Long> currentCounts, int slot, List<ItemStack> markedItems,
        FuzzyMode fuzzyMode) {
        try {
            int markedIndex = 0;
            for (IAEItemStack stored : getStoredItemStacks()) {
                if (stored == null) continue;
                ItemStack stack = stored.getItemStack();
                if (stack == null || stack.getItem() == null) continue;
                if (matchesAnyMarkedItem(stack, markedItems, fuzzyMode)) continue;
                stack.stackSize = 1;
                currentCounts.put(createSnapshotKey(slot, markedIndex++, stack), stored.getStackSize());
            }
        } catch (GridAccessException e) {
            AdvanceDataMonitor.LOG.error("Error sampling inverted item delta for slot " + slot, e);
        }
    }

    @TileEvent(TileEventType.NETWORK_WRITE)
    public void writeToStream(ByteBuf data) {
        int count = 0;
        for (int i = 0; i < SLOT_COUNT; i++) if (cellItems[i] != null) count++;
        data.writeByte(count);

        for (int i = 0; i < SLOT_COUNT; i++) {
            if (cellItems[i] != null) {
                data.writeByte(i);
                ByteBufUtils.writeItemStack(data, cellItems[i]);
            }
        }

        data.writeByte(itemCountCache.size());
        for (Map.Entry<Integer, Long> entry : itemCountCache.entrySet()) {
            data.writeByte(entry.getKey());
            data.writeLong(entry.getValue());
        }
    }

    @TileEvent(TileEventType.NETWORK_READ)
    public boolean readFromStream(ByteBuf data) {
        for (int i = 0; i < SLOT_COUNT; i++) cellItems[i] = null;

        int itemCount = data.readByte();
        for (int i = 0; i < itemCount; i++) {
            int slot = data.readByte();
            cellItems[slot] = ByteBufUtils.readItemStack(data);
        }

        itemCountCache.clear();
        int cacheCount = data.readByte();
        for (int i = 0; i < cacheCount; i++) {
            int slot = data.readByte();
            long count = data.readLong();
            itemCountCache.put(slot, count);
        }

        return true;
    }

    public void handleItemCountSyncRequest() {
        if (worldObj.isRemote) return;
        for (int slot = 0; slot < SLOT_COUNT; slot++) if (cellItems[slot] != null) updateItemCountCache(slot);

        AdvanceDataMonitor.ADMCHANEL.sendToAllAround(
            new PacketItemCountSync(xCoord, yCoord, zCoord, itemCountCache),
            new NetworkRegistry.TargetPoint(worldObj.provider.dimensionId, xCoord, yCoord, zCoord, 64));
    }

    public void updateClientCache(Map<Integer, Long> newCache) {
        this.itemCountCache.clear();
        this.itemCountCache.putAll(newCache);
        worldObj.markBlockRangeForRenderUpdate(xCoord, yCoord, zCoord, xCoord, yCoord, zCoord);
    }

    public void handleRightClick(EntityPlayer player) {
        if (worldObj.isRemote) return;

        boolean foundItems = false;
        StringBuilder output = new StringBuilder("Storage item counts:");
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            ItemStack cell = getCellItem(slot);
            if (!isStorageLinkCell(cell)) continue;

            ItemAdvanceStorageLinkCell cellItem = (ItemAdvanceStorageLinkCell) cell.getItem();

            if (cellItem.hasOreCard(cell)) {
                foundItems = true;
                try {
                    long count = countOreDictMatches(getStorageGrid(), cellItem.getOreFilter(cell));
                    output.append("\nSlot ")
                        .append(slot)
                        .append(" [Ore:")
                        .append(cellItem.getOreFilter(cell))
                        .append("]: ")
                        .append(count)
                        .append(" items");
                } catch (GridAccessException e) {
                    output.append("\nSlot ")
                        .append(slot)
                        .append(" [Ore:")
                        .append(cellItem.getOreFilter(cell))
                        .append("]: error");
                }
                continue;
            }

            if (cellItem.hasFluidMarker(cell)) {
                foundItems = true;
                for (net.minecraftforge.fluids.FluidStack marker : cellItem.getFluidMarkers(cell)) {
                    try {
                        long amount = getFluidAmountInNetwork(marker);
                        output.append("\nSlot ")
                            .append(slot)
                            .append(": ")
                            .append(marker.getLocalizedName())
                            .append(" x ")
                            .append(amount)
                            .append(" mB");
                    } catch (GridAccessException e) {
                        output.append("\nSlot ")
                            .append(slot)
                            .append(": ")
                            .append(marker.getLocalizedName())
                            .append(": error");
                    }
                }
                continue;
            }

            if (cellItem.hasEssentiaMarker(cell)) {
                foundItems = true;
                for (ItemAdvanceStorageLinkCell.EssentiaMarker marker : cellItem.getEssentiaMarkers(cell)) {
                    try {
                        long amount = getEssentiaAmountInNetwork(marker.aspectTag);
                        output.append("\nSlot ")
                            .append(slot)
                            .append(" [Essentia:")
                            .append(marker.aspectTag)
                            .append("]: ")
                            .append(amount)
                            .append(" units");
                    } catch (Exception e) {
                        output.append("\nSlot ")
                            .append(slot)
                            .append(" [Essentia:")
                            .append(marker.aspectTag)
                            .append("]: error");
                    }
                }
                continue;
            }

            boolean useFuzzy = cellItem.hasFuzzyCard(cell);
            FuzzyMode fuzzyMode = useFuzzy ? cellItem.getFuzzyMode(cell) : null;
            for (ItemStack marked : cellItem.getMarkedItems(cell)) {
                foundItems = true;
                long count = 0;
                try {
                    count = getItemCountInNetwork(marked, fuzzyMode);
                } catch (GridAccessException e) {
                    AdvanceDataMonitor.LOG.error("Error getting item count for slot " + slot, e);
                }
                output.append("\nSlot ")
                    .append(slot)
                    .append(": ")
                    .append(marked.getDisplayName())
                    .append(" x ")
                    .append(count);
            }
        }

        player.addChatMessage(new ChatComponentText(foundItems ? output.toString() : "No monitored storage cells"));
    }
}
