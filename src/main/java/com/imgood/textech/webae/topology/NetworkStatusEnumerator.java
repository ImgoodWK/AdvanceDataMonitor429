package com.imgood.textech.webae.topology;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.webae.context.WebAeOwnerContext;
import com.imgood.textech.webae.pattern.InterfaceLocator;

import appeng.api.AEApi;
import appeng.api.implementations.tiles.IChestOrDrive;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridBlock;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.storage.ICellInventory;
import appeng.api.storage.ICellInventoryHandler;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.StorageChannel;
import appeng.api.util.DimensionalCoord;
import appeng.tile.storage.TileChest;
import appeng.tile.storage.TileDrive;

/**
 * Enumerates AE grid facilities the same way {@code ContainerNetworkStatus} does for the in-game
 * network tool: iterate {@link IGrid#getMachinesClasses()}, skip nodes without
 * {@link IGridBlock#getMachineRepresentation()}.
 */
public final class NetworkStatusEnumerator {

    private NetworkStatusEnumerator() {}

    public static List<NetworkFacility> enumerate(String ownerUuid, int networkId) {
        List<NetworkFacility> result = new ArrayList<NetworkFacility>();
        EntityPlayerMP player = WebAeOwnerContext.getOwnerPlayerOrFake(ownerUuid);
        WebAeOwnerContext.NetworkGroup group = WebAeOwnerContext.getNetworkGroup(ownerUuid, networkId);
        if (player == null || group == null) {
            return result;
        }
        WebAeOwnerContext.positionPlayerAtMonitor(player, group);

        IGrid grid = WebAeOwnerContext.getGrid(ownerUuid, networkId);
        if (grid == null) {
            grid = gridFromGroup(group);
        }
        if (grid == null) {
            AdvanceDataMonitor.LOG.warn("[WebAE] Topology: no AE grid for owner={} network={}", ownerUuid, networkId);
            return result;
        }

        try {
            for (Class<? extends IGridHost> clazz : grid.getMachinesClasses()) {
                if (clazz == null) {
                    continue;
                }
                for (IGridNode node : grid.getMachines(clazz)) {
                    if (node == null) {
                        continue;
                    }
                    NetworkFacility facility = fromNode(node, clazz);
                    if (facility != null) {
                        result.add(facility);
                    }
                }
            }
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.error("[WebAE] Network status enumeration failed", e);
        }
        return result;
    }

    private static NetworkFacility fromNode(IGridNode node, Class<? extends IGridHost> clazz) {
        try {
            IGridHost host = node.getMachine();
            if (host == null) {
                return null;
            }
            IGridBlock block = node.getGridBlock();
            if (block == null) {
                return null;
            }

            String className = host.getClass()
                .getName();
            if (className == null || className.isEmpty()) {
                className = clazz != null ? clazz.getName() : "unknown";
            }

            ItemStack representation = block.getMachineRepresentation();
            if (representation == null || representation.getItem() == null) {
                representation = fallbackRepresentation(host, className);
            }
            if (representation == null || representation.getItem() == null) {
                AdvanceDataMonitor.LOG.debug(
                    "[WebAE] Topology skipped node without representation: {} @ {}",
                    className,
                    block.getLocation());
                return null;
            }

            // Cables are pure transport — never emit them as topology devices.
            if (TopologyRules.isCableFacility(className)) {
                return null;
            }

            int x = 0;
            int y = 0;
            int z = 0;
            int dim = 0;
            DimensionalCoord loc = null;
            try {
                loc = block.getLocation();
            } catch (Exception ignored) {}
            if (loc != null) {
                x = loc.x;
                y = loc.y;
                z = loc.z;
                dim = loc.getDimension();
            } else if (host instanceof TileEntity) {
                TileEntity te = (TileEntity) host;
                x = te.xCoord;
                y = te.yCoord;
                z = te.zCoord;
                if (te.getWorldObj() != null) {
                    dim = te.getWorldObj().provider.dimensionId;
                }
            }

            TopologyNodeType type = TopologyRules.classify(className, representation);
            String subtype = TopologyRules.classifySubtype(className, representation);
            String displayName = representation.getDisplayName();
            if (displayName == null || displayName.trim()
                .isEmpty()) {
                displayName = TopologyRules.displayNameForSubtype(subtype, type);
            }
            if (host instanceof TileEntity && InterfaceLocator.isInterface((TileEntity) host)) {
                try {
                    String term = InterfaceLocator.getTermName((TileEntity) host);
                    if (term != null && !term.isEmpty()) {
                        displayName = term;
                    }
                } catch (Exception ignored) {}
            }

            NetworkFacility facility = new NetworkFacility();
            facility.className = className;
            facility.displayName = displayName;
            facility.type = type;
            facility.subtype = subtype;
            facility.x = x;
            facility.y = y;
            facility.z = z;
            facility.dim = dim;
            facility.representationItemId = stackItemId(representation);
            facility.channelCost = resolveChannelCost(node, type);
            facility.idlePower = block.getIdlePowerUsage();

            if (host instanceof IChestOrDrive || host instanceof TileDrive || host instanceof TileChest) {
                populateDriveCells(host, facility);
                facility.patternCount = countPatterns(facility.cells);
            }
            return facility;
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.debug("[WebAE] Skipped network facility: {}", e.getMessage());
            return null;
        }
    }

    private static void populateDriveCells(IGridHost host, NetworkFacility facility) {
        if (host instanceof TileDrive) {
            TileDrive drive = (TileDrive) host;
            for (int slot = 0; slot < drive.getInternalInventory()
                .getSizeInventory(); slot++) {
                ItemStack stack = drive.getInternalInventory()
                    .getStackInSlot(slot);
                NetworkFacility.CellSlot cell = describeCell(slot, stack);
                if (cell != null) {
                    facility.cells.add(cell);
                }
            }
            return;
        }
        if (host instanceof TileChest) {
            TileChest chest = (TileChest) host;
            ItemStack stack = chest.getInternalInventory()
                .getStackInSlot(0);
            NetworkFacility.CellSlot cell = describeCell(0, stack);
            if (cell != null) {
                facility.cells.add(cell);
            }
        }
    }

    private static NetworkFacility.CellSlot describeCell(int slot, ItemStack stack) {
        NetworkFacility.CellSlot cell = new NetworkFacility.CellSlot();
        cell.slot = slot;
        if (stack == null || stack.getItem() == null) {
            cell.displayName = "Empty slot";
            cell.empty = true;
            return cell;
        }
        cell.empty = false;
        cell.displayName = stack.getDisplayName();
        cell.itemId = stackItemId(stack);
        cell.count = stack.stackSize;
        cell.isPattern = isPatternStack(stack);
        cell.itemBytes = cellBytes(stack, StorageChannel.ITEMS);
        cell.fluidBytes = cellBytes(stack, StorageChannel.FLUIDS);
        return cell;
    }

    private static boolean isPatternStack(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return false;
        }
        try {
            Class<?> patternIface = Class.forName("appeng.api.implementations.items.ICraftingPatternItem");
            if (patternIface.isInstance(stack.getItem())) {
                return true;
            }
        } catch (Throwable ignored) {}
        String reg = net.minecraft.item.Item.itemRegistry.getNameForObject(stack.getItem());
        if (reg != null) {
            String lower = reg.toLowerCase();
            if (lower.contains("pattern") && !lower.contains("provider")) {
                return true;
            }
            if (lower.contains("encodedpattern") || lower.contains("craftingitem")) {
                return true;
            }
        }
        return false;
    }

    private static int countPatterns(List<NetworkFacility.CellSlot> cells) {
        int count = 0;
        for (NetworkFacility.CellSlot cell : cells) {
            if (cell != null && cell.isPattern) {
                count++;
            }
        }
        return count;
    }

    private static long cellBytes(ItemStack stack, StorageChannel channel) {
        try {
            IMEInventoryHandler inv = AEApi.instance()
                .registries()
                .cell()
                .getCellInventory(stack, null, channel);
            if (inv instanceof ICellInventoryHandler) {
                ICellInventory cell = ((ICellInventoryHandler) inv).getCellInv();
                if (cell != null) {
                    return cell.getTotalBytes();
                }
            }
        } catch (Exception ignored) {}
        return 0L;
    }

    private static int resolveChannelCost(IGridNode node, TopologyNodeType type) {
        int used = probeNodeInt(node, true);
        if (used > 0) {
            return used;
        }
        if (requiresChannel(node)) {
            return 1;
        }
        if (type == TopologyNodeType.INTERFACE || type == TopologyNodeType.P2P) {
            return 1;
        }
        return 0;
    }

    private static boolean requiresChannel(IGridNode node) {
        try {
            Method m = node.getClass()
                .getMethod("isActive");
            Object val = m.invoke(node);
            if (val instanceof Boolean && !(Boolean) val) {
                return false;
            }
        } catch (Exception ignored) {}
        try {
            Method m = node.getClass()
                .getMethod("getUsedChannels");
            Object val = m.invoke(node);
            if (val instanceof Number) {
                return ((Number) val).intValue() > 0;
            }
        } catch (Exception ignored) {}
        return false;
    }

    private static int probeNodeInt(IGridNode node, boolean used) {
        String[] names = used ? new String[] { "getUsedChannels", "getChannelsUsed", "getChannelUsed" }
            : new String[] { "getMaxChannels", "getChannelCapacity", "getChannelMax" };
        for (String name : names) {
            try {
                Method m = node.getClass()
                    .getMethod(name);
                Object val = m.invoke(node);
                if (val instanceof Integer) {
                    return (Integer) val;
                }
                if (val instanceof Number) {
                    return ((Number) val).intValue();
                }
            } catch (Exception ignored) {}
        }
        return -1;
    }

    private static ItemStack fallbackRepresentation(IGridHost host, String className) {
        if (host == null || className == null) {
            return null;
        }
        if (TopologyRules.isCableFacility(className)) {
            return null;
        }
        String[] methods = { "getItemStack", "getDisplayStack", "getCableItem" };
        for (String methodName : methods) {
            try {
                Method m = host.getClass()
                    .getMethod(methodName);
                Object val = m.invoke(host);
                if (val instanceof ItemStack) {
                    ItemStack stack = (ItemStack) val;
                    if (stack.getItem() != null) {
                        return stack;
                    }
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    private static String stackItemId(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return "";
        }
        return net.minecraft.item.Item.itemRegistry.getNameForObject(stack.getItem())
            + (stack.getItemDamage() != 0 ? ":" + stack.getItemDamage() : "");
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

    /** One grid node / machine row, mirroring the network tool facility list. */
    public static final class NetworkFacility {

        public String className;
        public String displayName;
        public TopologyNodeType type;
        public String subtype = TopologyRules.SUB_MISC;
        public int x;
        public int y;
        public int z;
        public int dim;
        public String representationItemId = "";
        public int channelCost;
        public double idlePower;
        public int patternCount;
        public List<CellSlot> cells = new ArrayList<CellSlot>();

        public static final class CellSlot {

            public int slot;
            public boolean empty;
            public boolean isPattern;
            public String displayName = "";
            public String itemId = "";
            public int count;
            public long itemBytes;
            public long fluidBytes;
        }
    }
}
