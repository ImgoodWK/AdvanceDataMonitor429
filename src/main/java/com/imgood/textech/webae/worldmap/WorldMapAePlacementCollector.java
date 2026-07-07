package com.imgood.textech.webae.worldmap;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.Config;
import com.imgood.textech.webae.context.WebAeOwnerContext;
import com.imgood.textech.webae.pattern.InterfaceLocator;
import com.imgood.textech.webae.topology.TopologyRules;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridBlock;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.util.DimensionalCoord;

/**
 * Enumerates AE grid placements including cables and cable-bus parts for world-map overlay scope and tiles.
 */
public final class WorldMapAePlacementCollector {

    private static final ForgeDirection[] PART_SIDES = ForgeDirection.VALID_DIRECTIONS;

    private WorldMapAePlacementCollector() {}

    public static List<WorldMapAePlacementRecord> collect(String ownerUuid, int networkId) {
        List<WorldMapAePlacementRecord> result = new ArrayList<WorldMapAePlacementRecord>();
        if (!Config.webWorldMapAeOverlayEnabled) {
            return result;
        }

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
                    collectFromNode(node, clazz, result);
                }
            }
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.error("[WebAE] AE placement collection failed", e);
        }
        return result;
    }

    private static void collectFromNode(IGridNode node, Class<? extends IGridHost> clazz,
        List<WorldMapAePlacementRecord> out) {
        try {
            IGridHost host = node.getMachine();
            if (host == null) {
                return;
            }
            IGridBlock block = node.getGridBlock();
            if (block == null) {
                return;
            }

            String className = host.getClass()
                .getName();
            if (className == null || className.isEmpty()) {
                className = clazz != null ? clazz.getName() : "unknown";
            }

            boolean cable = TopologyRules.isCableFacility(className);
            if (cable && !Config.webWorldMapAeOverlayIncludeCables) {
                return;
            }

            ItemStack representation = block.getMachineRepresentation();
            if (representation == null || representation.getItem() == null) {
                representation = fallbackRepresentation(host, className);
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

            WorldMapAePlacementRecord record = new WorldMapAePlacementRecord();
            record.x = x;
            record.y = y;
            record.z = z;
            record.dim = dim;
            record.className = className;
            record.kind = cable ? "cable" : "block";
            if (representation != null && representation.getItem() != null) {
                record.iconItemId = stackItemId(representation);
                String displayName = representation.getDisplayName();
                if (displayName != null && !displayName.trim()
                    .isEmpty()) {
                    record.displayName = displayName;
                }
            }
            if (record.displayName.isEmpty()) {
                record.displayName = TopologyRules.displayNameForSubtype(
                    TopologyRules.classifySubtype(className, representation),
                    TopologyRules.classify(className, representation));
            }
            if (host instanceof TileEntity && InterfaceLocator.isInterface((TileEntity) host)) {
                try {
                    String term = InterfaceLocator.getTermName((TileEntity) host);
                    if (term != null && !term.isEmpty()) {
                        record.displayName = term;
                    }
                } catch (Exception ignored) {}
            }
            out.add(record);

            if (!cable) {
                collectParts(host, x, y, z, dim, out);
            }
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.debug("[WebAE] Skipped AE placement: {}", e.getMessage());
        }
    }

    private static void collectParts(IGridHost host, int x, int y, int z, int dim,
        List<WorldMapAePlacementRecord> out) {
        Object partHost = host;
        if (!isPartHost(partHost)) {
            return;
        }
        for (ForgeDirection side : PART_SIDES) {
            Object part = getPart(partHost, side);
            if (part == null) {
                continue;
            }
            ItemStack stack = getPartStack(part);
            if (stack == null || stack.getItem() == null) {
                continue;
            }
            WorldMapAePlacementRecord record = new WorldMapAePlacementRecord();
            record.x = x;
            record.y = y;
            record.z = z;
            record.dim = dim;
            record.kind = "part";
            record.className = part.getClass()
                .getName();
            record.iconItemId = stackItemId(stack);
            String displayName = stack.getDisplayName();
            record.displayName = displayName != null ? displayName : record.className;
            out.add(record);
        }
    }

    private static boolean isPartHost(Object host) {
        if (host == null) {
            return false;
        }
        for (Class<?> iface : host.getClass()
            .getInterfaces()) {
            if ("appeng.api.parts.IPartHost".equals(iface.getName())) {
                return true;
            }
        }
        Class<?> superClass = host.getClass()
            .getSuperclass();
        while (superClass != null) {
            for (Class<?> iface : superClass.getInterfaces()) {
                if ("appeng.api.parts.IPartHost".equals(iface.getName())) {
                    return true;
                }
            }
            superClass = superClass.getSuperclass();
        }
        return false;
    }

    private static Object getPart(Object partHost, ForgeDirection side) {
        try {
            Method m = partHost.getClass()
                .getMethod("getPart", ForgeDirection.class);
            return m.invoke(partHost, side);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static ItemStack getPartStack(Object part) {
        if (part == null) {
            return null;
        }
        try {
            Class<?> stackType = Class.forName("appeng.api.parts.PartItemStack");
            Method m = part.getClass()
                .getMethod("getItemStack", stackType);
            Object val = m.invoke(part, Enum.valueOf((Class<Enum>) stackType, "World"));
            if (val instanceof ItemStack) {
                return (ItemStack) val;
            }
        } catch (Exception ignored) {}
        try {
            Method m = part.getClass()
                .getMethod("getItemStack");
            Object val = m.invoke(part);
            if (val instanceof ItemStack) {
                return (ItemStack) val;
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static ItemStack fallbackRepresentation(IGridHost host, String className) {
        if (host == null || className == null) {
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
}
