package com.imgood.textech.webae.monitor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;

import com.imgood.textech.monitor.MonitorWidgetSpec;
import com.imgood.textech.tileentity.TileEntityAdvanceDataMonitor;
import com.imgood.textech.webae.context.WebAeOwnerContext;
import com.imgood.textech.webae.gt.GtMachineBinding;

/**
 * Collects read-only monitor binding data for WebAE (edit remains in-game).
 */
public final class MonitorBindingCollector {

    private MonitorBindingCollector() {}

    public static List<MonitorBindingDto> collect(String ownerUuid) {
        String ownerName = WebAeOwnerContext.resolveOwnerName(ownerUuid);
        if (ownerName.isEmpty()) {
            return Collections.emptyList();
        }
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null) {
            return Collections.emptyList();
        }

        List<MonitorBindingDto> result = new ArrayList<MonitorBindingDto>();
        for (int d = 0; d < server.worldServers.length; d++) {
            WorldServer world = server.worldServers[d];
            if (world == null) {
                continue;
            }
            int dim = world.provider.dimensionId;
            // Use the TE index to scan only data monitors in this dimension.
            List<TileEntityAdvanceDataMonitor> monitors = com.imgood.textech.webae.context.TileEntityIndex
                .getByType(dim, TileEntityAdvanceDataMonitor.class);
            for (TileEntityAdvanceDataMonitor monitor : monitors) {
                if (!ownerName.equals(monitor.getOwnerName())) {
                    continue;
                }
                MonitorBindingDto dto = new MonitorBindingDto();
                dto.monitorDim = dim;
                dto.monitorX = monitor.xCoord;
                dto.monitorY = monitor.yCoord;
                dto.monitorZ = monitor.zCoord;
                dto.owner = monitor.getOwnerName();
                dto.dataBindings = collectDataBindings(monitor);
                dto.gtBindings = collectGtBindings(monitor);
                result.add(dto);
            }
        }
        return result;
    }

    private static List<MonitorDataBindingDto> collectDataBindings(TileEntityAdvanceDataMonitor monitor) {
        List<MonitorDataBindingDto> list = new ArrayList<MonitorDataBindingDto>();
        Map<Integer, NBTTagCompound> bound = monitor.getDataBoundList();
        for (Map.Entry<Integer, NBTTagCompound> entry : bound.entrySet()) {
            NBTTagCompound nbt = entry.getValue();
            if (nbt == null) {
                continue;
            }
            MonitorWidgetSpec.normalizeBinding(nbt, monitor.xCoord, monitor.yCoord, monitor.zCoord);
            MonitorDataBindingDto slot = new MonitorDataBindingDto();
            slot.slotIndex = entry.getKey();
            slot.dataType = safeString(nbt.getString("dataType"), "line");
            slot.kind = MonitorWidgetSpec.getKind(nbt);
            slot.sourceKind = MonitorWidgetSpec.getSourceKind(nbt);
            slot.metricKey = MonitorWidgetSpec.getMetricKey(nbt);
            slot.title = MonitorWidgetSpec.getTitle(nbt);
            slot.displayName = slot.title;
            slot.xyz = safeString(nbt.getString("XYZ"), "0,0,0");
            parseXyz(slot);
            slot.enabled = nbt.getBoolean("enable");
            slot.networkWide = nbt.getBoolean("monitorNetworkWide");
            slot.targetValue = MonitorWidgetSpec.getTargetValue(nbt);
            slot.revision = MonitorWidgetSpec.getRevision(nbt);
            list.add(slot);
        }
        return list;
    }

    private static List<MonitorGtBindingDto> collectGtBindings(TileEntityAdvanceDataMonitor monitor) {
        List<MonitorGtBindingDto> list = new ArrayList<MonitorGtBindingDto>();
        for (GtMachineBinding.BoundMachine bm : GtMachineBinding.getBoundMachines(monitor)) {
            MonitorGtBindingDto gt = new MonitorGtBindingDto();
            gt.dim = bm.dim;
            gt.x = bm.x;
            gt.y = bm.y;
            gt.z = bm.z;
            list.add(gt);
        }
        return list;
    }

    private static void parseXyz(MonitorDataBindingDto slot) {
        if (slot.xyz == null || slot.xyz.isEmpty()) {
            return;
        }
        String[] parts = slot.xyz.split(",");
        if (parts.length >= 4) {
            try {
                slot.bindDim = Integer.parseInt(parts[0].trim());
                slot.bindX = Integer.parseInt(parts[1].trim());
                slot.bindY = Integer.parseInt(parts[2].trim());
                slot.bindZ = Integer.parseInt(parts[3].trim());
            } catch (NumberFormatException ignored) {}
        } else if (parts.length >= 3) {
            try {
                slot.bindX = Integer.parseInt(parts[0].trim());
                slot.bindY = Integer.parseInt(parts[1].trim());
                slot.bindZ = Integer.parseInt(parts[2].trim());
            } catch (NumberFormatException ignored) {}
        }
    }

    private static String safeString(String value, String fallback) {
        return value == null || value.isEmpty() ? fallback : value;
    }
}
