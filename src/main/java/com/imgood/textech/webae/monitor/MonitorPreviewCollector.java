package com.imgood.textech.webae.monitor;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;

import com.imgood.textech.tileentity.TileEntityAdvanceDataMonitor;
import com.imgood.textech.webae.context.WebAeOwnerContext;

/**
 * Collects line-chart preview data from a monitor tile (Phase 11).
 */
public final class MonitorPreviewCollector {

    private MonitorPreviewCollector() {}

    public static MonitorPreviewDto collect(String ownerUuid, int dim, int x, int y, int z, int slotIndex) {
        String ownerName = WebAeOwnerContext.resolveOwnerName(ownerUuid);
        if (ownerName.isEmpty()) {
            return null;
        }
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || dim < 0 || dim >= server.worldServers.length) {
            return null;
        }
        WorldServer world = server.worldServers[dim];
        if (world == null) {
            return null;
        }
        if (!world.blockExists(x, y, z)) {
            return null;
        }
        if (!(world.getTileEntity(x, y, z) instanceof TileEntityAdvanceDataMonitor)) {
            return null;
        }
        TileEntityAdvanceDataMonitor monitor = (TileEntityAdvanceDataMonitor) world.getTileEntity(x, y, z);
        if (!ownerName.equals(monitor.getOwnerName())) {
            return null;
        }
        if (slotIndex < 0 || slotIndex >= monitor.getDataBoundCount()) {
            return null;
        }

        MonitorPreviewDto dto = new MonitorPreviewDto();
        dto.monitorDim = dim;
        dto.monitorX = x;
        dto.monitorY = y;
        dto.monitorZ = z;
        dto.slotIndex = slotIndex;
        dto.dataType = monitor.getDataTypeString(slotIndex);
        dto.displayName = monitor.getDisplayName(slotIndex);
        dto.enabled = monitor.getEnable(slotIndex);
        dto.values = monitor.getDoubleValues(slotIndex);
        dto.yMin = monitor.getYMin(slotIndex);
        dto.yMax = monitor.getYMax(slotIndex);
        dto.dataLimit = monitor.getDataLimit(slotIndex);
        dto.timestamp = System.currentTimeMillis();
        return dto;
    }
}
