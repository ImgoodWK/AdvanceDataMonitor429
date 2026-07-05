package com.imgood.textech.webae.pattern;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;

import com.imgood.textech.Config;
import com.imgood.textech.assistant.ItemStackUtils;
import com.imgood.textech.webae.context.WebAeOwnerContext;
import com.imgood.textech.webae.dto.PatternBrowseEntryDto;
import com.imgood.textech.webae.dto.PatternDto.InterfaceDto;
import com.imgood.textech.webae.dto.PatternDto.PatternItemEntry;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingGrid;

/**
 * Merges Grid + Interface pattern sources with TTL caching and pagination.
 * Heavy cache rebuild runs on the server thread via {@link com.imgood.textech.webae.cache.SnapshotScheduler};
 * HTTP handlers read cache only and paginate on the HTTP thread.
 */
public final class PatternBrowseService {

    private static final ConcurrentHashMap<String, CachedBrowseData> cache = new ConcurrentHashMap<String, CachedBrowseData>();

    private PatternBrowseService() {}

    /**
     * Paginate from an existing cache entry. Safe to call from any thread (read-only).
     */
    public static BrowseResult paginate(CachedBrowseData cached, String query, int offset, int limit,
        String source) {
        if (cached == null) {
            return emptyResult(offset, limit);
        }
        String q = query == null ? "" : query.trim();
        List<PatternBrowseEntryDto> filtered;
        if (q.isEmpty()) {
            filtered = selectSource(cached, source);
        } else {
            filtered = filterByQuery(selectSource(cached, source), q);
        }

        int total = filtered.size();
        int maxTotal = Config.webPatternBrowseMaxTotal;
        boolean truncated = total > maxTotal;
        if (truncated) {
            filtered = filtered.subList(0, maxTotal);
            total = maxTotal;
        }

        int safeOffset = Math.max(0, offset);
        int safeLimit = Math.min(Math.max(1, limit), 200);
        int end = Math.min(safeOffset + safeLimit, filtered.size());
        List<PatternBrowseEntryDto> page = safeOffset >= filtered.size() ? new ArrayList<PatternBrowseEntryDto>()
            : new ArrayList<PatternBrowseEntryDto>(filtered.subList(safeOffset, end));

        BrowseResult result = new BrowseResult();
        result.entries = page;
        result.total = total;
        result.offset = safeOffset;
        result.limit = safeLimit;
        result.truncated = truncated;
        result.gridCount = cached.gridEntries.size();
        result.interfaceCount = cached.interfaceEntries.size();
        return result;
    }

    /**
     * Read-only browse from TTL cache (no blocking rebuild). Used by HTTP and search.
     */
    public static BrowseResult browse(String ownerUuid, int networkId, String query, int offset, int limit,
        String source) {
        CachedBrowseData cached = getFresh(ownerUuid, networkId);
        if (cached == null) {
            cached = getStale(ownerUuid, networkId);
        }
        if (cached == null) {
            return emptyResult(offset, limit);
        }
        return paginate(cached, query, offset, limit, source);
    }

    /**
     * Build and store browse cache on the server thread. Returns the new entry or null on failure.
     */
    public static CachedBrowseData buildAndStoreCache(String ownerUuid, int networkId) {
        EntityPlayerMP player = WebAeOwnerContext.getOwnerPlayerOrFake(ownerUuid);
        if (player == null) {
            return null;
        }
        WebAeOwnerContext.NetworkGroup group = WebAeOwnerContext.getNetworkGroup(ownerUuid, networkId);
        if (group != null) {
            WebAeOwnerContext.positionPlayerAtMonitor(player, group);
        }
        CachedBrowseData cached = buildCache(ownerUuid, networkId, player);
        cache.put(cacheKey(ownerUuid, networkId), cached);
        return cached;
    }

    public static CachedBrowseData getFresh(String ownerUuid, int networkId) {
        CachedBrowseData cached = cache.get(cacheKey(ownerUuid, networkId));
        if (cached == null) {
            return null;
        }
        if (System.currentTimeMillis() - cached.timestamp > Config.webPatternCacheTtlMs) {
            return null;
        }
        return cached;
    }

    public static CachedBrowseData getStale(String ownerUuid, int networkId) {
        return cache.get(cacheKey(ownerUuid, networkId));
    }

    public static long timestampOf(String ownerUuid, int networkId) {
        CachedBrowseData cached = cache.get(cacheKey(ownerUuid, networkId));
        return cached != null ? cached.timestamp : 0L;
    }

    public static boolean isFresh(String ownerUuid, int networkId) {
        return getFresh(ownerUuid, networkId) != null;
    }

    public static void invalidateCache(String ownerUuid, int networkId) {
        cache.remove(cacheKey(ownerUuid, networkId));
    }

    /** Drop all browse caches (e.g. after pattern mutation anywhere on the network). */
    public static void invalidateAll() {
        cache.clear();
    }

    /** Drop browse caches for every player viewing the given network id. */
    public static void invalidateNetwork(int networkId) {
        String suffix = ":" + networkId;
        for (String key : cache.keySet()) {
            if (key.endsWith(suffix)) {
                cache.remove(key);
            }
        }
    }

    /**
     * Resolve a single grid browse entry by {@code grid:<index>} key. Must run on server thread.
     */
    public static PatternBrowseEntryDto getGridEntry(String ownerUuid, int networkId, String gridKey) {
        if (gridKey == null || !gridKey.startsWith("grid:")) {
            return null;
        }
        int targetIndex;
        try {
            targetIndex = Integer.parseInt(gridKey.substring(5));
        } catch (NumberFormatException e) {
            return null;
        }
        if (targetIndex < 0) {
            return null;
        }
        IGrid grid = InterfaceLocator.findGrid(ownerUuid, networkId);
        if (grid == null) {
            return null;
        }
        ICraftingGrid craftingGrid = grid.getCache(ICraftingGrid.class);
        return CraftingGridPatternQuery.getByIndex(craftingGrid, targetIndex);
    }

    private static CachedBrowseData buildCache(String ownerUuid, int networkId, EntityPlayerMP player) {
        CachedBrowseData cached = new CachedBrowseData();
        cached.timestamp = System.currentTimeMillis();

        IGrid grid = InterfaceLocator.findGrid(ownerUuid, networkId);
        if (grid != null) {
            ICraftingGrid craftingGrid = grid.getCache(ICraftingGrid.class);
            cached.gridEntries = CraftingGridPatternQuery.collect(craftingGrid, "");
        } else {
            cached.gridEntries = new ArrayList<PatternBrowseEntryDto>();
        }

        cached.interfaceEntries = collectInterfacePatterns(ownerUuid, networkId);
        return cached;
    }

    private static String cacheKey(String ownerUuid, int networkId) {
        return ownerUuid + ":" + networkId;
    }

    private static BrowseResult emptyResult(int offset, int limit) {
        BrowseResult result = new BrowseResult();
        result.offset = Math.max(0, offset);
        result.limit = Math.min(Math.max(1, limit), 200);
        return result;
    }

    private static List<PatternBrowseEntryDto> selectSource(CachedBrowseData cached, String source) {
        String src = source == null ? "both"
            : source.trim()
                .toLowerCase();
        if ("grid".equals(src)) {
            return new ArrayList<PatternBrowseEntryDto>(cached.gridEntries);
        }
        if ("interface".equals(src)) {
            return new ArrayList<PatternBrowseEntryDto>(cached.interfaceEntries);
        }
        List<PatternBrowseEntryDto> merged = new ArrayList<PatternBrowseEntryDto>(
            cached.gridEntries.size() + cached.interfaceEntries.size());
        merged.addAll(cached.gridEntries);
        merged.addAll(cached.interfaceEntries);
        return merged;
    }

    private static List<PatternBrowseEntryDto> filterByQuery(List<PatternBrowseEntryDto> entries, String query) {
        List<PatternBrowseEntryDto> result = new ArrayList<PatternBrowseEntryDto>();
        for (PatternBrowseEntryDto entry : entries) {
            if (matchesEntry(entry, query)) {
                result.add(entry);
            }
        }
        return result;
    }

    private static boolean matchesEntry(PatternBrowseEntryDto entry, String query) {
        if (entry.displayName != null && entry.displayName.toLowerCase()
            .contains(query.toLowerCase())) {
            return true;
        }
        if (entry.sourceInterfaceName != null && entry.sourceInterfaceName.toLowerCase()
            .contains(query.toLowerCase())) {
            return true;
        }
        if (entry.author != null && entry.author.toLowerCase()
            .contains(query.toLowerCase())) {
            return true;
        }
        for (PatternItemEntry pe : entry.outputs) {
            if (pe != null && fuzzyMatch(pe, query)) {
                return true;
            }
        }
        for (PatternItemEntry pe : entry.inputs) {
            if (pe != null && fuzzyMatch(pe, query)) {
                return true;
            }
        }
        return false;
    }

    private static boolean fuzzyMatch(PatternItemEntry pe, String query) {
        if (pe.displayName != null && pe.displayName.toLowerCase()
            .contains(query.toLowerCase())) {
            return true;
        }
        if (pe.registryName != null && pe.registryName.toLowerCase()
            .contains(query.toLowerCase())) {
            return true;
        }
        ItemStack stack = stackFromEntry(pe);
        return stack != null && ItemStackUtils.fuzzyNameMatches(stack, query);
    }

    private static ItemStack stackFromEntry(PatternItemEntry pe) {
        if (pe.registryName == null || pe.registryName.isEmpty()) {
            return null;
        }
        Object itemObj = Item.itemRegistry.getObject(pe.registryName);
        if (!(itemObj instanceof Item)) {
            return null;
        }
        ItemStack stack = new ItemStack((Item) itemObj, 1, pe.meta);
        if (pe.displayName != null && !pe.displayName.isEmpty()) {
            stack.setStackDisplayName(pe.displayName);
        }
        return stack;
    }

    private static List<PatternBrowseEntryDto> collectInterfacePatterns(String ownerUuid, int networkId) {
        List<PatternBrowseEntryDto> result = new ArrayList<PatternBrowseEntryDto>();
        List<InterfaceDto> interfaces = InterfaceLocator.locate(ownerUuid, networkId);
        if (interfaces == null || interfaces.isEmpty()) {
            return result;
        }
        for (InterfaceDto iface : interfaces) {
            TileEntity te = findTileEntityAt(iface.x, iface.y, iface.z, iface.dim);
            if (te == null || !InterfaceLocator.isInterface(te)) {
                continue;
            }
            IInventory patterns = InterfaceLocator.getPatterns(te);
            if (patterns == null) {
                continue;
            }
            int activeSlots = (iface.capacityUpgrades + 1) * 9;
            for (int slot = 0; slot < activeSlots; slot++) {
                ItemStack stack = patterns.getStackInSlot(slot);
                if (stack == null || stack.getItem() == null) {
                    continue;
                }
                NBTTagCompound nbt = stack.getTagCompound();
                if (nbt == null) {
                    continue;
                }
                PatternBrowseEntryDto entry = decodeInterfacePattern(nbt, iface, slot);
                if (entry != null) {
                    result.add(entry);
                }
            }
        }
        return result;
    }

    private static PatternBrowseEntryDto decodeInterfacePattern(NBTTagCompound nbt, InterfaceDto iface, int slot) {
        PatternBrowseEntryDto entry = new PatternBrowseEntryDto();
        entry.source = "interface";
        entry.sourceInterface = iface.x + ":" + iface.y + ":" + iface.z + ":" + iface.dim;
        entry.sourceInterfaceName = iface.name != null ? iface.name : entry.sourceInterface;
        entry.slotIndex = slot;
        entry.patternId = entry.sourceInterface + "#" + slot;
        entry.crafting = nbt.getByte("crafting") != 0;
        entry.substitute = nbt.getByte("substitute") != 0;
        entry.beSubstitute = nbt.hasKey("beSubstitute") && nbt.getByte("beSubstitute") != 0;
        entry.author = nbt.getString("author");
        if (entry.author == null) {
            entry.author = "";
        }

        NBTTagList inList = nbt.getTagList("in", 10);
        if (inList != null) {
            for (int i = 0; i < inList.tagCount(); i++) {
                PatternItemEntry pe = stackTagToEntry(inList.getCompoundTagAt(i));
                if (pe != null) {
                    entry.inputs.add(pe);
                }
            }
        }
        NBTTagList outList = nbt.getTagList("out", 10);
        if (outList != null) {
            for (int i = 0; i < outList.tagCount(); i++) {
                PatternItemEntry pe = stackTagToEntry(outList.getCompoundTagAt(i));
                if (pe != null) {
                    entry.outputs.add(pe);
                }
            }
        }
        entry.inputsCount = entry.inputs.size();
        entry.outputsCount = entry.outputs.size();
        if (entry.outputs.isEmpty()) {
            return null;
        }
        PatternItemEntry primary = entry.outputs.get(0);
        entry.displayName = primary.displayName;
        entry.registryName = primary.registryName;
        entry.meta = primary.meta;
        entry.amount = primary.stackSize;
        return entry;
    }

    private static PatternItemEntry stackTagToEntry(NBTTagCompound stackTag) {
        if (stackTag == null) {
            return null;
        }
        ItemStack stack = ItemStack.loadItemStackFromNBT(stackTag);
        if (stack == null || stack.getItem() == null) {
            return null;
        }
        PatternItemEntry entry = new PatternItemEntry();
        Object nameObj = Item.itemRegistry.getNameForObject(stack.getItem());
        entry.registryName = nameObj != null ? nameObj.toString() : "";
        entry.displayName = stack.getDisplayName();
        entry.meta = stack.getItemDamage();
        if (entry.meta == Short.MAX_VALUE) {
            entry.meta = 0;
        }
        entry.stackSize = stack.stackSize;
        entry.isFluid = entry.registryName.contains("fluid") || entry.registryName.startsWith("ae2fc:");
        return entry;
    }

    private static TileEntity findTileEntityAt(int x, int y, int z, int dim) {
        World world = DimensionManager.getWorld(dim);
        if (world == null) {
            return null;
        }
        if (!world.blockExists(x, y, z)) {
            return null;
        }
        return world.getTileEntity(x, y, z);
    }

    public static final class BrowseResult {

        public List<PatternBrowseEntryDto> entries = new ArrayList<PatternBrowseEntryDto>();
        public int total;
        public int offset;
        public int limit;
        public boolean truncated;
        public int gridCount;
        public int interfaceCount;
    }

    public static final class CachedBrowseData {

        public long timestamp;
        public List<PatternBrowseEntryDto> gridEntries = new ArrayList<PatternBrowseEntryDto>();
        public List<PatternBrowseEntryDto> interfaceEntries = new ArrayList<PatternBrowseEntryDto>();
    }
}
