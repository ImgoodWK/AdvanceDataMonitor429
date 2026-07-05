package com.imgood.textech.webae.topology;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.webae.context.WebAeOwnerContext;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.util.DimensionalCoord;

/**
 * Enumerates AE P2P tunnel endpoints grouped by frequency (Phase 10).
 * Must run on the server main thread.
 */
public final class P2pTunnelEnumerator {

    private P2pTunnelEnumerator() {}

    public static List<P2pTunnelDto> enumerate(String ownerUuid, int networkId) {
        List<P2pTunnelDto> result = new ArrayList<P2pTunnelDto>();
        EntityPlayerMP player = WebAeOwnerContext.getOwnerPlayerOrFake(ownerUuid);
        WebAeOwnerContext.NetworkGroup group = WebAeOwnerContext.getNetworkGroup(ownerUuid, networkId);
        if (player == null || group == null) {
            return result;
        }
        WebAeOwnerContext.positionPlayerAtMonitor(player, group);

        IGrid grid = WebAeOwnerContext.getGrid(ownerUuid, networkId);
        if (grid == null) {
            return result;
        }

        try {
            for (Class<? extends IGridHost> clazz : grid.getMachinesClasses()) {
                if (clazz == null) {
                    continue;
                }
                String simple = clazz.getSimpleName();
                if (simple == null || !simple.contains("P2P")) {
                    continue;
                }
                for (IGridNode node : grid.getMachines(clazz)) {
                    if (node == null) {
                        continue;
                    }
                    P2pTunnelDto dto = fromNode(node, clazz);
                    if (dto != null) {
                        result.add(dto);
                    }
                }
            }
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.error("[WebAE] P2P enumeration failed", e);
        }
        return result;
    }

    private static P2pTunnelDto fromNode(IGridNode node, Class<? extends IGridHost> clazz) {
        try {
            IGridHost host = node.getMachine();
            if (host == null) {
                return null;
            }
            P2pTunnelDto dto = new P2pTunnelDto();
            dto.type = clazz.getSimpleName();
            dto.displayName = TopologyRules.displayNameFor(TopologyNodeType.P2P);

            DimensionalCoord loc = null;
            try {
                loc = node.getGridBlock()
                    .getLocation();
            } catch (Exception ignored) {}

            if (loc != null) {
                dto.x = loc.x;
                dto.y = loc.y;
                dto.z = loc.z;
                dto.dim = loc.getDimension();
            } else if (host instanceof TileEntity) {
                TileEntity te = (TileEntity) host;
                dto.x = te.xCoord;
                dto.y = te.yCoord;
                dto.z = te.zCoord;
                if (te.getWorldObj() != null) {
                    dto.dim = te.getWorldObj().provider.dimensionId;
                }
            }

            dto.frequency = probeFrequency(host);
            dto.frequencyHex = String.format("%04X", dto.frequency & 0xFFFF);
            dto.inputSide = probeInputSide(host);
            return dto;
        } catch (Exception e) {
            return null;
        }
    }

    private static int probeFrequency(Object host) {
        try {
            Method m = host.getClass()
                .getMethod("getFrequency");
            Object val = m.invoke(host);
            if (val instanceof Number) {
                return ((Number) val).intValue();
            }
        } catch (Exception ignored) {}
        try {
            Method m = host.getClass()
                .getMethod("getFreq");
            Object val = m.invoke(host);
            if (val instanceof Number) {
                return ((Number) val).intValue();
            }
        } catch (Exception ignored) {}
        return 0;
    }

    private static boolean probeInputSide(Object host) {
        try {
            Method m = host.getClass()
                .getMethod("isOutput");
            Object val = m.invoke(host);
            if (val instanceof Boolean) {
                return !((Boolean) val).booleanValue();
            }
        } catch (Exception ignored) {}
        try {
            Method m = host.getClass()
                .getMethod("isInput");
            Object val = m.invoke(host);
            if (val instanceof Boolean) {
                return ((Boolean) val).booleanValue();
            }
        } catch (Exception ignored) {}
        return false;
    }
}
