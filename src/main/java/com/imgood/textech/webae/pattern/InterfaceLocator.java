package com.imgood.textech.webae.pattern;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.handler.HandlerTick;
import com.imgood.textech.webae.context.WebAeOwnerContext;
import com.imgood.textech.webae.dto.PatternDto.InterfaceDto;
import com.imgood.textech.webae.dto.PatternDto.InterfaceDto.ExistingPatternEntry;
import com.imgood.textech.webae.dto.PatternDto.InterfaceDto.SlotState;
import com.imgood.textech.webae.dto.PatternDto.PatternItemEntry;

import appeng.api.config.Upgrades;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.events.MENetworkCraftingPatternChange;
import appeng.api.util.DimensionalCoord;

/**
 * Enumerates all ME Interfaces in an AE network with multi-dimensional filtering.
 * Must run on the server thread. Uses HandlerTick.enqueueServerTask + CountDownLatch pattern.
 *
 * Uses reflection to access IInterface-like methods since the exact API interface
 * path varies across AE2 versions (IInterface, IInterfaceViewable, etc.).
 */
public class InterfaceLocator {

    private static final long TIMEOUT_MS = 10_000L;

    /**
     * Check if a tile entity is an AE2 interface (full block or cable part).
     */
    public static boolean isInterface(Object target) {
        if (target == null) return false;
        String className = target.getClass()
            .getName();
        return className.contains("TileInterface") || className.contains("PartInterface");
    }

    public static boolean isInterface(TileEntity te) {
        return isInterface((Object) te);
    }

    /**
     * Blocking enumeration for HTTP handlers. Enqueues on server thread and waits.
     */
    public static List<InterfaceDto> locateBlocking(String playerUuid, int networkId) {
        final List<InterfaceDto>[] holder = new List[1];
        final CountDownLatch latch = new CountDownLatch(1);

        HandlerTick.enqueueServerTask(new Runnable() {

            @Override
            public void run() {
                try {
                    EntityPlayerMP player = WebAeOwnerContext.getOwnerPlayerOrFake(playerUuid);
                    if (player == null) {
                        holder[0] = Collections.emptyList();
                    } else {
                        holder[0] = locate(playerUuid, networkId);
                    }
                } catch (Throwable t) {
                    AdvanceDataMonitor.LOG.error("[WebAE] Interface enumeration failed", t);
                    holder[0] = Collections.emptyList();
                } finally {
                    latch.countDown();
                }
            }
        });

        try {
            if (latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                return holder[0];
            }
        } catch (InterruptedException e) {
            Thread.currentThread()
                .interrupt();
        }
        AdvanceDataMonitor.LOG
            .warn("[WebAE] Interface enumeration timed out player={} network={}", playerUuid, networkId);
        return Collections.emptyList();
    }

    /**
     * Main-thread enumeration scoped to an owner network.
     */
    public static List<InterfaceDto> locate(String ownerUuid, int networkId) {
        EntityPlayerMP player = WebAeOwnerContext.getOwnerPlayerOrFake(ownerUuid);
        WebAeOwnerContext.NetworkGroup group = WebAeOwnerContext.getNetworkGroup(ownerUuid, networkId);
        if (player == null || group == null) {
            return Collections.emptyList();
        }
        WebAeOwnerContext.positionPlayerAtMonitor(player, group);
        return locate(player, networkId, group);
    }

    /**
     * Main-thread enumeration. Finds the grid via link tiles, then enumerates all interfaces
     * by scanning loaded tile entities near the player.
     */
    public static List<InterfaceDto> locate(EntityPlayerMP player, int networkId) {
        List<InterfaceDto> result = new ArrayList<InterfaceDto>();

        IGrid grid = findGrid(player, networkId);
        if (grid == null) {
            if (player != null) {
                AdvanceDataMonitor.LOG
                    .warn("[WebAE] No AE grid found for player={} network={}", player.getUniqueID(), networkId);
            }
            return result;
        }

        return enumerateGridInterfaces(grid, player);
    }

    private static List<InterfaceDto> locate(EntityPlayerMP player, int networkId,
        WebAeOwnerContext.NetworkGroup group) {
        String ownerUuid = player != null ? player.getUniqueID()
            .toString() : "";
        IGrid grid = findGrid(ownerUuid, networkId);
        if (grid == null && group != null) {
            grid = gridFromGroup(group);
        }
        if (grid == null || player == null) {
            AdvanceDataMonitor.LOG.warn("[WebAE] No AE grid found for network={}", networkId);
            return Collections.emptyList();
        }
        return enumerateGridInterfaces(grid, player);
    }

    private static IGrid gridFromGroup(WebAeOwnerContext.NetworkGroup group) {
        TileEntity link = group.craftingLink != null ? group.craftingLink
            : group.storageLink != null ? group.storageLink : group.networkLink;
        if (!(link instanceof IGridHost)) {
            return null;
        }
        try {
            IGridNode node = ((IGridHost) link).getGridNode(ForgeDirection.UNKNOWN);
            return node != null ? node.getGrid() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static List<InterfaceDto> enumerateGridInterfaces(IGrid grid, EntityPlayerMP player) {
        List<InterfaceDto> result = new ArrayList<InterfaceDto>();
        // Primary: grid node traversal (cross-dimension, no radius limit)
        List<InterfaceDto> gridNodes = locateViaGridNodes(grid);
        java.util.Set<String> seen = new java.util.HashSet<String>();
        for (InterfaceDto dto : gridNodes) {
            String key = dto.interfaceId;
            if (seen.add(key)) {
                result.add(dto);
            }
        }

        // Fallback: radius scan for interfaces not reachable via grid nodes (loaded chunks only)
        int radius = 64;
        int px = (int) Math.floor(player.posX);
        int py = (int) Math.floor(player.posY);
        int pz = (int) Math.floor(player.posZ);

        for (int x = px - radius; x <= px + radius; x++) {
            for (int y = Math.max(0, py - radius); y <= Math.min(255, py + radius); y++) {
                for (int z = pz - radius; z <= pz + radius; z++) {
                    if (!player.worldObj.blockExists(x, y, z)) continue;
                    TileEntity te = player.worldObj.getTileEntity(x, y, z);
                    if (te == null || !isInterface(te)) continue;
                    if (!(te instanceof IGridHost)) continue;

                    // Verify this interface is on the same grid
                    try {
                        IGridNode node = ((IGridHost) te).getGridNode(ForgeDirection.UNKNOWN);
                        if (node == null || node.getGrid() != grid) continue;
                    } catch (Exception e) {
                        continue;
                    }

                    InterfaceDto dto = buildInterfaceDto(te);
                    if (dto != null) {
                        String key = dto.interfaceId;
                        if (seen.add(key)) {
                            result.add(dto);
                        }
                    }
                }
            }
        }

        return result;
    }

    /**
     * Enumerate ME Interfaces by walking {@link IGrid#getMachinesClasses()} — covers
     * cross-dimension interfaces registered on the grid without a player-radius scan.
     */
    public static List<InterfaceDto> locateViaGridNodes(IGrid grid) {
        List<InterfaceDto> result = new ArrayList<InterfaceDto>();
        if (grid == null) {
            return result;
        }
        try {
            for (Class<? extends IGridHost> clazz : grid.getMachinesClasses()) {
                if (clazz == null) {
                    continue;
                }
                String className = clazz.getName();
                if (!className.contains("Interface") && !className.contains("PatternProvider")) {
                    continue;
                }
                for (IGridNode node : grid.getMachines(clazz)) {
                    if (node == null) {
                        continue;
                    }
                    try {
                        Object host = node.getMachine();
                        if (!isInterface(host)) {
                            continue;
                        }
                        InterfaceDto dto = buildInterfaceDto(host);
                        if (dto != null) {
                            result.add(dto);
                        }
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.debug("[WebAE] Grid node interface enumeration failed", e);
        }
        return result;
    }

    /**
     * Find the AE grid for an owner network index.
     */
    public static IGrid findGrid(String ownerUuid, int networkId) {
        return WebAeOwnerContext.getGrid(ownerUuid, networkId);
    }

    /**
     * @deprecated use {@link #findGrid(String, int)}
     */
    public static IGrid findGrid(EntityPlayerMP player, int networkId) {
        if (player == null) {
            return null;
        }
        return findGrid(
            player.getUniqueID()
                .toString(),
            networkId);
    }

    /**
     * Build InterfaceDto from an interface TileEntity using reflection.
     */
    public static InterfaceDto buildInterfaceDto(TileEntity te) {
        return buildInterfaceDto((Object) te);
    }

    /** Build a DTO for either a full-block TileInterface or a cable-bus PartInterface. */
    public static InterfaceDto buildInterfaceDto(Object target) {
        InterfaceDto dto = new InterfaceDto();

        try {
            // Name
            dto.name = getTermName(target);

            // Location
            DimensionalCoord loc = getLocation(target);
            TileEntity tile = getTileEntity(target);
            if (loc != null) {
                dto.x = loc.x;
                dto.y = loc.y;
                dto.z = loc.z;
                dto.dim = loc.getDimension();
            } else if (tile != null && tile.getWorldObj() != null) {
                dto.x = tile.xCoord;
                dto.y = tile.yCoord;
                dto.z = tile.zCoord;
                dto.dim = tile.getWorldObj().provider.dimensionId;
            } else {
                return null;
            }

            ForgeDirection side = getPartSide(target);
            dto.part = !(target instanceof TileEntity);
            dto.partSide = dto.part && side != null && side != ForgeDirection.UNKNOWN ? side.name() : "";
            dto.interfaceId = address(dto.x, dto.y, dto.z, dto.dim, dto.partSide);

            // Capacity upgrades
            dto.capacityUpgrades = getInstalledUpgrades(target, Upgrades.PATTERN_CAPACITY);
            dto.activeSlots = (dto.capacityUpgrades + 1) * 9;

            // Slot states + existing pattern details
            IInventory patterns = getPatterns(target);
            if (patterns != null) {
                dto.activeSlots = Math.min(dto.activeSlots, patterns.getSizeInventory());
                for (int i = 0; i < dto.activeSlots; i++) {
                    ItemStack stack = patterns.getStackInSlot(i);
                    boolean occupied = stack != null && stack.getItem() != null;
                    String summary = "";
                    if (occupied) {
                        summary = buildPatternSummary(stack);
                        ExistingPatternEntry existing = buildExistingPatternEntry(
                            dto.x,
                            dto.y,
                            dto.z,
                            dto.dim,
                            dto.partSide,
                            i,
                            stack);
                        if (existing != null) {
                            dto.existingPatterns.add(existing);
                        }
                    }
                    dto.slots.add(new SlotState(i, occupied, summary));
                }
            }

            // Target machine info
            dto.targetMachineName = getTargetMachineName(target);
            dto.targetRecipePool = getTargetRecipePool(target);
            dto.machineRecipeType = buildMachineRecipeType(dto.targetMachineName, dto.targetRecipePool);

        } catch (Exception e) {
            AdvanceDataMonitor.LOG.warn("[WebAE] Failed to build InterfaceDto for {}", dto.name, e);
            return null;
        }

        return dto;
    }

    // ---- Reflection-based accessors (package-accessible for PatternInjector) ----

    public static String getTermName(TileEntity te) {
        return getTermName((Object) te);
    }

    public static String getTermName(Object target) {
        try {
            Object result = invokeNoArg(target, "getTermName");
            if (result == null) result = invokeNoArg(getDuality(target), "getTermName");
            if (result != null) {
                String name = result.toString();
                if (!name.isEmpty() && !"Interface".equals(name)) {
                    return name;
                }
            }
        } catch (Exception ignored) {}
        TileEntity tile = getTileEntity(target);
        ForgeDirection side = getPartSide(target);
        if (tile != null) {
            String suffix = side != null && side != ForgeDirection.UNKNOWN ? " " + side.name() : "";
            return "Interface (" + tile.xCoord + "," + tile.yCoord + "," + tile.zCoord + suffix + ")";
        }
        return "Interface";
    }

    public static DimensionalCoord getLocation(TileEntity te) {
        return getLocation((Object) te);
    }

    public static DimensionalCoord getLocation(Object target) {
        try {
            Object result = invokeNoArg(target, "getLocation");
            if (result == null) result = invokeNoArg(getDuality(target), "getLocation");
            if (result instanceof DimensionalCoord) {
                return (DimensionalCoord) result;
            }
        } catch (Exception ignored) {}
        return null;
    }

    public static int getInstalledUpgrades(TileEntity te, Upgrades upgrade) {
        return getInstalledUpgrades((Object) te, upgrade);
    }

    public static int getInstalledUpgrades(Object target, Upgrades upgrade) {
        try {
            Object result = invokeUpgrade(target, upgrade);
            if (result == null) result = invokeUpgrade(getDuality(target), upgrade);
            if (result instanceof Integer) {
                return (Integer) result;
            }
        } catch (Exception ignored) {}
        return 0;
    }

    public static IInventory getPatterns(TileEntity te) {
        return getPatterns((Object) te);
    }

    public static IInventory getPatterns(Object target) {
        try {
            Object result = invokeNoArg(target, "getPatterns");
            if (result == null) result = invokeNoArg(getDuality(target), "getPatterns");
            if (result instanceof IInventory) {
                return (IInventory) result;
            }
        } catch (Exception ignored) {}
        return null;
    }

    public static void saveChanges(TileEntity te) {
        saveChanges((Object) te);
    }

    public static void saveChanges(Object target) {
        try {
            if (!invokeVoidNoArg(target, "saveChanges")) invokeVoidNoArg(getDuality(target), "saveChanges");
        } catch (Exception ignored) {}
        TileEntity tile = getTileEntity(target);
        if (tile != null) tile.markDirty();
    }

    static String buildPatternSummary(ItemStack patternStack) {
        if (patternStack == null || patternStack.getTagCompound() == null) {
            return "Unknown";
        }
        try {
            net.minecraft.nbt.NBTTagCompound tag = patternStack.getTagCompound();
            boolean isCrafting = tag.getByte("crafting") != 0;
            StringBuilder sb = new StringBuilder();
            sb.append(isCrafting ? "[C] " : "[P] ");
            net.minecraft.nbt.NBTTagList outList = tag.getTagList("out", 10);
            if (outList != null && outList.tagCount() > 0) {
                net.minecraft.nbt.NBTTagCompound firstOut = outList.getCompoundTagAt(0);
                ItemStack outStack = ItemStack.loadItemStackFromNBT(firstOut);
                if (outStack != null && outStack.getItem() != null) {
                    sb.append(outStack.getDisplayName());
                    if (outStack.stackSize > 1) {
                        sb.append(" x")
                            .append(outStack.stackSize);
                    }
                    if (outList.tagCount() > 1) {
                        sb.append(" +")
                            .append(outList.tagCount() - 1);
                    }
                    return sb.toString();
                }
            }
            return sb.append("?")
                .toString();
        } catch (Exception e) {
            return "Pattern";
        }
    }

    static ExistingPatternEntry buildExistingPatternEntry(int x, int y, int z, int dim, String partSide, int slotIndex,
        ItemStack patternStack) {
        if (patternStack == null || patternStack.getTagCompound() == null) return null;
        net.minecraft.nbt.NBTTagCompound tag = patternStack.getTagCompound();
        ExistingPatternEntry entry = new ExistingPatternEntry();
        entry.slotIndex = slotIndex;
        entry.patternId = address(x, y, z, dim, partSide) + "#" + slotIndex;
        entry.crafting = tag.getByte("crafting") != 0;
        net.minecraft.nbt.NBTTagList outList = tag.getTagList("out", 10);
        if (outList != null) {
            for (int i = 0; i < outList.tagCount(); i++) {
                PatternItemEntry pe = stackTagToEntry(outList.getCompoundTagAt(i));
                if (pe != null) entry.outputs.add(pe);
            }
        }
        return entry;
    }

    private static PatternItemEntry stackTagToEntry(net.minecraft.nbt.NBTTagCompound stackTag) {
        if (stackTag == null) return null;
        ItemStack stack = ItemStack.loadItemStackFromNBT(stackTag);
        if (stack == null || stack.getItem() == null) return null;
        PatternItemEntry entry = new PatternItemEntry();
        Object nameObj = net.minecraft.item.Item.itemRegistry.getNameForObject(stack.getItem());
        entry.registryName = nameObj != null ? nameObj.toString() : "";
        entry.displayName = stack.getDisplayName();
        entry.meta = stack.getItemDamage();
        entry.stackSize = stack.stackSize;
        entry.isFluid = entry.registryName.contains("fluid") || entry.registryName.startsWith("ae2fc:");
        return entry;
    }

    private static String buildMachineRecipeType(String machineName, String recipePool) {
        if (machineName != null && !machineName.isEmpty()) {
            if (recipePool != null && !recipePool.isEmpty()) {
                return machineName + " / " + recipePool;
            }
            return machineName;
        }
        return recipePool != null ? recipePool : "";
    }

    private static String getTargetMachineName(Object target) {
        try {
            TileEntity adjacent = getAdjacentTile(target);
            if (adjacent != null) {
                try {
                    Class<?> igtteClass = Class.forName("gregtech.api.interfaces.tileentity.IGregTechTileEntity");
                    if (igtteClass.isInstance(adjacent)) {
                        Object machineName = igtteClass.getMethod("getMachineName")
                            .invoke(adjacent);
                        if (machineName != null) return machineName.toString();
                    }
                } catch (Exception ignored) {}
                return adjacent.getClass()
                    .getSimpleName()
                    .replace("TileEntity", "")
                    .replace("Tile", "");
            }
        } catch (Exception ignored) {}
        return "";
    }

    private static String getTargetRecipePool(Object target) {
        try {
            TileEntity adjacent = getAdjacentTile(target);
            if (adjacent != null) {
                try {
                    Class<?> igtteClass = Class.forName("gregtech.api.interfaces.tileentity.IGregTechTileEntity");
                    if (igtteClass.isInstance(adjacent)) {
                        Object recipeMap = igtteClass.getMethod("getRecipeMap")
                            .invoke(adjacent);
                        if (recipeMap != null) {
                            Object name = recipeMap.getClass()
                                .getField("unlocalizedName")
                                .get(recipeMap);
                            if (name != null) return name.toString();
                        }
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
        return "";
    }

    private static TileEntity getAdjacentTile(Object target) {
        try {
            TileEntity te = getTileEntity(target);
            if (te == null) return null;
            World world = te.getWorldObj();
            if (world == null) return null;
            ForgeDirection partSide = getPartSide(target);
            if (partSide != null && partSide != ForgeDirection.UNKNOWN) {
                TileEntity adjacent = world.getTileEntity(
                    te.xCoord + partSide.offsetX,
                    te.yCoord + partSide.offsetY,
                    te.zCoord + partSide.offsetZ);
                if (adjacent != null && !isInterface(adjacent)) return adjacent;
            }
            ForgeDirection[] dirs = ForgeDirection.values();
            for (ForgeDirection dir : dirs) {
                if (dir == ForgeDirection.UNKNOWN) continue;
                TileEntity adjacent = world
                    .getTileEntity(te.xCoord + dir.offsetX, te.yCoord + dir.offsetY, te.zCoord + dir.offsetZ);
                if (adjacent != null && !isInterface(adjacent)) {
                    return adjacent;
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    public static String address(int x, int y, int z, int dim, String partSide) {
        String base = x + ":" + y + ":" + z + ":" + dim;
        return partSide != null && !partSide.isEmpty() ? base + "@" + partSide : base;
    }

    /** Resolve a full-block interface or a specific cable-bus interface part from its WebAE address. */
    public static Object resolveInterface(int x, int y, int z, int dim, String partSide) {
        World world = net.minecraftforge.common.DimensionManager.getWorld(dim);
        if (world == null || !world.blockExists(x, y, z)) return null;
        TileEntity tile = world.getTileEntity(x, y, z);
        if (tile == null) return null;

        if (partSide == null || partSide.isEmpty()) {
            if (isInterface(tile)) return tile;
            Object only = null;
            for (ForgeDirection side : ForgeDirection.VALID_DIRECTIONS) {
                Object part = getPart(tile, side);
                if (!isInterface(part)) continue;
                if (only != null) return null; // Ambiguous legacy address: require @SIDE.
                only = part;
            }
            return only;
        }

        try {
            ForgeDirection side = ForgeDirection.valueOf(partSide.toUpperCase(java.util.Locale.ROOT));
            Object part = getPart(tile, side);
            return isInterface(part) ? part : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static TileEntity getTileEntity(Object target) {
        if (target instanceof TileEntity) return (TileEntity) target;
        Object tile = invokeNoArg(target, "getTileEntity");
        if (!(tile instanceof TileEntity)) tile = invokeNoArg(target, "getTile");
        return tile instanceof TileEntity ? (TileEntity) tile : null;
    }

    public static ForgeDirection getPartSide(Object target) {
        if (target instanceof TileEntity) return ForgeDirection.UNKNOWN;
        Object side = invokeNoArg(target, "getSide");
        return side instanceof ForgeDirection ? (ForgeDirection) side : ForgeDirection.UNKNOWN;
    }

    public static IGridNode getGridNode(Object target) {
        if (target instanceof IGridHost) {
            try {
                IGridNode node = ((IGridHost) target).getGridNode(ForgeDirection.UNKNOWN);
                if (node != null) return node;
            } catch (Throwable ignored) {}
        }
        Object node = invokeNoArg(target, "getGridNode");
        return node instanceof IGridNode ? (IGridNode) node : null;
    }

    public static boolean belongsToGrid(Object target, String ownerUuid, int networkId) {
        IGrid expected = findGrid(ownerUuid, networkId);
        IGridNode node = getGridNode(target);
        try {
            return expected != null && node != null && node.getGrid() == expected;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Verify that a coordinate-addressed target belongs to any network exposed by this WebAE owner. */
    public static boolean belongsToOwnerGrid(Object target, String ownerUuid) {
        IGridNode node = getGridNode(target);
        IGrid actual;
        try {
            actual = node != null ? node.getGrid() : null;
        } catch (Throwable ignored) {
            return false;
        }
        if (actual == null) return false;
        List<WebAeOwnerContext.NetworkGroup> groups = WebAeOwnerContext.findNetworkGroups(ownerUuid);
        for (int i = 0; i < groups.size(); i++) {
            if (findGrid(ownerUuid, i) == actual) return true;
        }
        return false;
    }

    /** Notify AE2 after a pattern inventory mutation. */
    public static void postPatternChangeEvent(Object target) {
        try {
            IGridNode node = getGridNode(target);
            if (node == null || node.getGrid() == null) return;
            Object provider = target instanceof ICraftingProvider ? target : getDuality(target);
            if (provider instanceof ICraftingProvider) {
                node.getGrid()
                    .postEvent(new MENetworkCraftingPatternChange((ICraftingProvider) provider, node));
            }
        } catch (Throwable t) {
            AdvanceDataMonitor.LOG.warn("[WebAE] Failed to post pattern change event: {}", t.getMessage());
        }
    }

    private static Object getPart(TileEntity tile, ForgeDirection side) {
        try {
            java.lang.reflect.Method method = tile.getClass()
                .getMethod("getPart", ForgeDirection.class);
            return method.invoke(tile, side);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object getDuality(Object target) {
        return invokeNoArg(target, "getInterfaceDuality");
    }

    private static Object invokeNoArg(Object target, String methodName) {
        if (target == null) return null;
        try {
            java.lang.reflect.Method method = target.getClass()
                .getMethod(methodName);
            return method.invoke(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object invokeUpgrade(Object target, Upgrades upgrade) {
        if (target == null) return null;
        try {
            java.lang.reflect.Method method = target.getClass()
                .getMethod("getInstalledUpgrades", Upgrades.class);
            return method.invoke(target, upgrade);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean invokeVoidNoArg(Object target, String methodName) {
        if (target == null) return false;
        try {
            java.lang.reflect.Method method = target.getClass()
                .getMethod(methodName);
            method.invoke(target);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static EntityPlayerMP getPlayer(String playerUuid) {
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || server.getConfigurationManager() == null) return null;
        for (Object obj : server.getConfigurationManager().playerEntityList) {
            if (obj instanceof EntityPlayerMP) {
                EntityPlayerMP mp = (EntityPlayerMP) obj;
                if (mp.getUniqueID()
                    .toString()
                    .equals(playerUuid)) {
                    return mp;
                }
            }
        }
        return null;
    }
}
