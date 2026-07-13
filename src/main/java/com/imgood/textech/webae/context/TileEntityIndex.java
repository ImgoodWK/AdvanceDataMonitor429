package com.imgood.textech.webae.context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;

/**
 * Dimension-grouped TileEntity index to replace O(n) {@code loadedTileEntityList}
 * scans in WebAE collectors.
 *
 * <p>Populated lazily on first access and maintained incrementally via
 * {@link #onChunkLoad(Chunk, int)} / {@link #onChunkUnload(Chunk, int)}
 * called from the existing chunk event handler.</p>
 *
 * <p>Thread-safety: only accessed from the Minecraft server main thread.</p>
 */
public final class TileEntityIndex {

    /** dimension → list of TileEntity references. */
    private static final Map<Integer, List<TileEntity>> index = new ConcurrentHashMap<Integer, List<TileEntity>>();
    private static volatile boolean built;

    private TileEntityIndex() {}

    /** Ensure the index is built from all loaded worlds (call on first access). */
    private static void ensureBuilt() {
        if (built) return;
        synchronized (TileEntityIndex.class) {
            if (built) return;
            MinecraftServer server = MinecraftServer.getServer();
            if (server == null) return;
            for (int d = 0; d < server.worldServers.length; d++) {
                WorldServer world = server.worldServers[d];
                if (world == null) continue;
                int dim = world.provider.dimensionId;
                List<TileEntity> list = new ArrayList<TileEntity>();
                for (Object obj : world.loadedTileEntityList) {
                    if (obj instanceof TileEntity) {
                        list.add((TileEntity) obj);
                    }
                }
                index.put(Integer.valueOf(dim), list);
            }
            built = true;
        }
    }

    /**
     * Called when a chunk loads to register its tile entities in the index.
     */
    public static void onChunkLoad(Chunk chunk, int dim) {
        if (chunk == null) return;
        if (!built) return;
        List<TileEntity> list = index.get(Integer.valueOf(dim));
        if (list == null) {
            list = new ArrayList<TileEntity>();
            index.put(Integer.valueOf(dim), list);
        }
        for (Object obj : chunk.chunkTileEntityMap.values()) {
            if (obj instanceof TileEntity) {
                list.add((TileEntity) obj);
            }
        }
    }

    /**
     * Called when a chunk unloads to remove its tile entities from the index.
     */
    public static void onChunkUnload(Chunk chunk, int dim) {
        if (chunk == null) return;
        if (!built) return;
        List<TileEntity> list = index.get(Integer.valueOf(dim));
        if (list == null) return;
        for (Object obj : chunk.chunkTileEntityMap.values()) {
            if (obj instanceof TileEntity) {
                list.remove(obj);
            }
        }
    }

    /**
     * Invalidate the index for a dimension, forcing a rebuild on next access.
     */
    public static void invalidateDimension(int dim) {
        index.remove(Integer.valueOf(dim));
    }

    /**
     * Return all TileEntities of a specific type in a given dimension.
     * O(n) in dimension tile count, but avoids scanning ALL dimensions.
     */
    @SuppressWarnings("unchecked")
    public static <T extends TileEntity> List<T> getByType(int dim, Class<T> type) {
        ensureBuilt();
        List<TileEntity> list = index.get(Integer.valueOf(dim));
        if (list == null) return Collections.emptyList();
        List<T> result = new ArrayList<T>();
        for (TileEntity te : list) {
            if (type.isInstance(te)) {
                result.add((T) te);
            }
        }
        return result;
    }

    /**
     * Return all TileEntities in a given dimension (raw list).
     */
    public static List<TileEntity> getAllInDimension(int dim) {
        ensureBuilt();
        List<TileEntity> list = index.get(Integer.valueOf(dim));
        return list != null ? list : Collections.<TileEntity>emptyList();
    }

    /** Total number of indexed tile entities across all dimensions. */
    public static int totalCount() {
        ensureBuilt();
        int count = 0;
        for (List<TileEntity> list : index.values()) {
            count += list.size();
        }
        return count;
    }
}
