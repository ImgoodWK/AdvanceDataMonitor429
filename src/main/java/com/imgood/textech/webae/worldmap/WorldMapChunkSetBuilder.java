package com.imgood.textech.webae.worldmap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds the allowed chunk set for world map rendering: occupied AE-device chunks plus
 * Chebyshev (including diagonal) padding. Unlike bbox expansion, sparse L-shaped networks
 * do not fill interior empty chunks.
 */
public final class WorldMapChunkSetBuilder {

    /** Max allowed chunks listed verbatim in meta; above this only bbox is sent. */
    public static final int MAX_LISTED_CHUNKS = 256;

    private WorldMapChunkSetBuilder() {}

    public static final class DimensionChunkSet {

        public final int dim;
        public final Set<String> allowed = new LinkedHashSet<String>();
        public int minChunkX = Integer.MAX_VALUE;
        public int maxChunkX = Integer.MIN_VALUE;
        public int minChunkZ = Integer.MAX_VALUE;
        public int maxChunkZ = Integer.MIN_VALUE;

        DimensionChunkSet(int dim) {
            this.dim = dim;
        }

        void includeChunk(int chunkX, int chunkZ) {
            allowed.add(chunkKey(chunkX, chunkZ));
            if (chunkX < minChunkX) {
                minChunkX = chunkX;
            }
            if (chunkX > maxChunkX) {
                maxChunkX = chunkX;
            }
            if (chunkZ < minChunkZ) {
                minChunkZ = chunkZ;
            }
            if (chunkZ > maxChunkZ) {
                maxChunkZ = chunkZ;
            }
        }

        boolean isEmpty() {
            return allowed.isEmpty();
        }

        List<String> listedChunks() {
            if (allowed.size() > MAX_LISTED_CHUNKS) {
                return null;
            }
            return new ArrayList<String>(allowed);
        }
    }

    public static Map<Integer, DimensionChunkSet> buildByDimension(List<WorldMapMarkerDto> markers,
        int paddingChunks) {
        Map<Integer, Set<String>> occupiedByDim = new HashMap<Integer, Set<String>>();
        if (markers != null) {
            for (WorldMapMarkerDto marker : markers) {
                if (marker == null) {
                    continue;
                }
                Set<String> occupied = occupiedByDim.get(marker.dim);
                if (occupied == null) {
                    occupied = new HashSet<String>();
                    occupiedByDim.put(marker.dim, occupied);
                }
                occupied.add(chunkKey(floorDiv(marker.x, 16), floorDiv(marker.z, 16)));
            }
        }
        return expandOccupiedByDim(occupiedByDim, paddingChunks);
    }

    public static Map<Integer, DimensionChunkSet> buildByPlacements(List<WorldMapAePlacementRecord> placements,
        int paddingChunks) {
        Map<Integer, Set<String>> occupiedByDim = new HashMap<Integer, Set<String>>();
        if (placements != null) {
            for (WorldMapAePlacementRecord placement : placements) {
                if (placement == null) {
                    continue;
                }
                Set<String> occupied = occupiedByDim.get(placement.dim);
                if (occupied == null) {
                    occupied = new HashSet<String>();
                    occupiedByDim.put(placement.dim, occupied);
                }
                occupied.add(chunkKey(floorDiv(placement.x, 16), floorDiv(placement.z, 16)));
            }
        }
        return expandOccupiedByDim(occupiedByDim, paddingChunks);
    }

    private static Map<Integer, DimensionChunkSet> expandOccupiedByDim(Map<Integer, Set<String>> occupiedByDim,
        int paddingChunks) {
        Map<Integer, DimensionChunkSet> out = new HashMap<Integer, DimensionChunkSet>();
        int pad = Math.max(0, paddingChunks);
        for (Map.Entry<Integer, Set<String>> entry : occupiedByDim.entrySet()) {
            DimensionChunkSet dimSet = new DimensionChunkSet(entry.getKey());
            expandChebyshev(entry.getValue(), pad, dimSet);
            out.put(entry.getKey(), dimSet);
        }
        return out;
    }

    /**
     * Returns {@code true} when the chunk is in the allowed set for the given network scope.
     */
    public static boolean isAllowed(String ownerUuid, int networkId, int dim, int chunkX, int chunkZ) {
        WorldMapMetaDto meta = WorldMapBoundsBuilder.rebuild(ownerUuid, networkId);
        if (meta == null || meta.dimensions == null) {
            return false;
        }
        for (WorldMapMetaDto.DimensionInfo info : meta.dimensions) {
            if (info == null || info.dim != dim) {
                continue;
            }
            return containsChunk(info, chunkX, chunkZ);
        }
        return false;
    }

    public static boolean containsChunk(WorldMapMetaDto.DimensionInfo info, int chunkX, int chunkZ) {
        if (info.allowedChunks != null && !info.allowedChunks.isEmpty()) {
            return info.allowedChunks.contains(chunkKey(chunkX, chunkZ));
        }
        if (info.chunkCount <= 0) {
            return false;
        }
        return chunkX >= info.minChunkX && chunkX <= info.maxChunkX && chunkZ >= info.minChunkZ
            && chunkZ <= info.maxChunkZ;
    }

    private static void expandChebyshev(Set<String> occupied, int padding, DimensionChunkSet out) {
        if (occupied == null || occupied.isEmpty()) {
            return;
        }
        for (String key : occupied) {
            int[] coords = parseChunkKey(key);
            if (coords == null) {
                continue;
            }
            int ocx = coords[0];
            int ocz = coords[1];
            for (int dx = -padding; dx <= padding; dx++) {
                for (int dz = -padding; dz <= padding; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) <= padding) {
                        out.includeChunk(ocx + dx, ocz + dz);
                    }
                }
            }
        }
    }

    static String chunkKey(int chunkX, int chunkZ) {
        return chunkX + "," + chunkZ;
    }

    private static int[] parseChunkKey(String key) {
        if (key == null || key.isEmpty()) {
            return null;
        }
        int comma = key.indexOf(',');
        if (comma <= 0 || comma >= key.length() - 1) {
            return null;
        }
        try {
            return new int[] { Integer.parseInt(key.substring(0, comma).trim()),
                Integer.parseInt(key.substring(comma + 1).trim()) };
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static int floorDiv(int value, int divisor) {
        if (value >= 0) {
            return value / divisor;
        }
        return -(((-value) + divisor - 1) / divisor);
    }
}
