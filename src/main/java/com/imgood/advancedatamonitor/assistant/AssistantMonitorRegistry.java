package com.imgood.advancedatamonitor.assistant;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.imgood.advancedatamonitor.tileentity.TileEntityAdvanceDataMonitor;

public final class AssistantMonitorRegistry {

    private static final Map<String, MonitorLocation> LAST_MONITOR_BY_PLAYER = new HashMap<String, MonitorLocation>();

    private AssistantMonitorRegistry() {}

    public static void record(EntityPlayer player, int dimension, int x, int y, int z) {
        if (player == null) {
            return;
        }
        LAST_MONITOR_BY_PLAYER.put(playerKey(player), new MonitorLocation(dimension, x, y, z));
    }

    public static MonitorLocation get(EntityPlayerMP player) {
        if (player == null) {
            return null;
        }
        MonitorLocation location = LAST_MONITOR_BY_PLAYER.get(playerKey(player));
        if (location == null || player.worldObj == null || player.worldObj.provider.dimensionId != location.dimension) {
            return null;
        }
        TileEntity tile = player.worldObj.getTileEntity(location.x, location.y, location.z);
        if (!(tile instanceof TileEntityAdvanceDataMonitor)) {
            LAST_MONITOR_BY_PLAYER.remove(playerKey(player));
            return null;
        }
        return location;
    }

    public static TileEntityAdvanceDataMonitor getMonitor(EntityPlayerMP player) {
        MonitorLocation location = get(player);
        if (location == null || player == null || player.worldObj == null) {
            return null;
        }
        TileEntity tile = player.worldObj.getTileEntity(location.x, location.y, location.z);
        return tile instanceof TileEntityAdvanceDataMonitor ? (TileEntityAdvanceDataMonitor) tile : null;
    }

    public static TileEntityAdvanceDataMonitor findNearest(EntityPlayer player, int radius) {
        if (player == null || player.worldObj == null) {
            return null;
        }
        World world = player.worldObj;
        int px = (int) Math.floor(player.posX);
        int py = (int) Math.floor(player.posY);
        int pz = (int) Math.floor(player.posZ);
        TileEntityAdvanceDataMonitor best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int x = px - radius; x <= px + radius; x++) {
            for (int y = Math.max(0, py - radius); y <= Math.min(world.getHeight() - 1, py + radius); y++) {
                for (int z = pz - radius; z <= pz + radius; z++) {
                    TileEntity tile = world.getTileEntity(x, y, z);
                    if (!(tile instanceof TileEntityAdvanceDataMonitor)) {
                        continue;
                    }
                    double distance = player.getDistanceSq(x + 0.5D, y + 0.5D, z + 0.5D);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = (TileEntityAdvanceDataMonitor) tile;
                    }
                }
            }
        }
        return best;
    }

    public static TileEntityAdvanceDataMonitor findNearbyAndRecord(EntityPlayerMP player, int radius) {
        TileEntityAdvanceDataMonitor best = findNearest(player, radius);
        if (best != null && player != null && player.worldObj != null) {
            record(player, player.worldObj.provider.dimensionId, best.xCoord, best.yCoord, best.zCoord);
        }
        return best;
    }

    private static String playerKey(EntityPlayer player) {
        try {
            UUID id = player.getUniqueID();
            if (id != null) {
                return id.toString();
            }
        } catch (Throwable ignored) {}
        return player.getCommandSenderName();
    }

    public static final class MonitorLocation {

        public final int dimension;
        public final int x;
        public final int y;
        public final int z;

        private MonitorLocation(int dimension, int x, int y, int z) {
            this.dimension = dimension;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public String format() {
            return "dim " + this.dimension + " @ " + this.x + "," + this.y + "," + this.z;
        }
    }
}
