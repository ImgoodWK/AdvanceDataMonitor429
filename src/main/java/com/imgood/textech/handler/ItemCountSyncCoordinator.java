package com.imgood.textech.handler;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.imgood.textech.tileentity.TileEntityAdvanceStorageLink;

/**
 * Debounces {@link TileEntityAdvanceStorageLink#handleItemCountSyncRequest()} onto the server tick thread.
 */
public final class ItemCountSyncCoordinator {

    private static final long DEBOUNCE_MS = 200L;
    private static final Object LOCK = new Object();
    private static final Map<String, Pending> PENDING = new HashMap<String, Pending>();

    private ItemCountSyncCoordinator() {}

    public static void schedule(World world, int x, int y, int z) {
        if (world == null || world.isRemote) {
            return;
        }
        final int dim = world.provider.dimensionId;
        final String key = key(dim, x, y, z);
        synchronized (LOCK) {
            Pending existing = PENDING.get(key);
            if (existing != null) {
                existing.dueAtMs = System.currentTimeMillis() + DEBOUNCE_MS;
                return;
            }
            PENDING.put(key, new Pending(world, x, y, z, System.currentTimeMillis() + DEBOUNCE_MS));
        }
        HandlerTick.enqueueServerTask(new Runnable() {

            @Override
            public void run() {
                flush(key);
            }
        });
    }

    private static void flush(String key) {
        Pending pending;
        synchronized (LOCK) {
            pending = PENDING.get(key);
            if (pending == null) {
                return;
            }
            if (System.currentTimeMillis() < pending.dueAtMs) {
                HandlerTick.enqueueServerTask(new Runnable() {

                    @Override
                    public void run() {
                        flush(key);
                    }
                });
                return;
            }
            PENDING.remove(key);
        }
        World world = pending.world;
        if (world == null) {
            return;
        }
        TileEntity te = world.getTileEntity(pending.x, pending.y, pending.z);
        if (te instanceof TileEntityAdvanceStorageLink) {
            ((TileEntityAdvanceStorageLink) te).handleItemCountSyncRequest();
        }
    }

    private static String key(int dim, int x, int y, int z) {
        return dim + ":" + x + ":" + y + ":" + z;
    }

    private static final class Pending {

        final World world;
        final int x;
        final int y;
        final int z;
        long dueAtMs;

        Pending(World world, int x, int y, int z, long dueAtMs) {
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.dueAtMs = dueAtMs;
        }
    }
}
