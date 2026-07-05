package com.imgood.textech.webae.topology;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.webae.context.WebAeOwnerContext;
import com.imgood.textech.webae.pattern.InterfaceLocator;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.util.DimensionalCoord;

/**
 * Enumerates all AE grid devices for topology via {@link IGrid#getMachinesClasses()}.
 * Must run on the server main thread.
 */
public final class NetworkDeviceEnumerator {

    private NetworkDeviceEnumerator() {}

    public static List<RawDevice> enumerate(String ownerUuid, int networkId) {
        List<RawDevice> result = new ArrayList<RawDevice>();
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
                    RawDevice device = fromNode(node, clazz);
                    if (device != null) {
                        result.add(device);
                    }
                }
            }
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.error("[WebAE] Topology device enumeration failed", e);
        }
        return result;
    }

    private static RawDevice fromNode(IGridNode node, Class<? extends IGridHost> clazz) {
        try {
            IGridHost host = node.getMachine();
            if (host == null) {
                return null;
            }
            String className = host.getClass()
                .getName();
            if (className == null || className.isEmpty()) {
                className = clazz != null ? clazz.getName() : "unknown";
            }

            int x = 0;
            int y = 0;
            int z = 0;
            int dim = 0;
            String displayName = TopologyRules.displayNameFor(TopologyRules.classify(className));

            DimensionalCoord loc = null;
            try {
                loc = node.getGridBlock()
                    .getLocation();
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

            if (host instanceof TileEntity && InterfaceLocator.isInterface((TileEntity) host)) {
                try {
                    String term = InterfaceLocator.getTermName((TileEntity) host);
                    if (term != null && !term.isEmpty()) {
                        displayName = term;
                    }
                } catch (Exception ignored) {}
            }

            TopologyNodeType type = TopologyRules.classify(className);
            RawDevice device = new RawDevice();
            device.className = className;
            device.displayName = displayName;
            device.type = type;
            device.x = x;
            device.y = y;
            device.z = z;
            device.dim = dim;
            device.channelCost = TopologyRules.channelCostFor(type);
            return device;
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.debug("[WebAE] Skipped topology node: {}", e.getMessage());
            return null;
        }
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

    /** Raw device record before aggregation. */
    public static final class RawDevice {

        public String className;
        public String displayName;
        public TopologyNodeType type;
        public int x;
        public int y;
        public int z;
        public int dim;
        public int channelCost;
    }
}
