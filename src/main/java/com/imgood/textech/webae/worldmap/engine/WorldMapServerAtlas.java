package com.imgood.textech.webae.worldmap.engine;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.Map;

import com.imgood.textech.Config;

/**
 * Server-side texture atlas baking high-frequency block face PNGs into one grid image.
 * Reduces per-texture {@link BufferedImage} object count during UV/ray rendering.
 */
public final class WorldMapServerAtlas {

    public static final class AtlasSlot {

        public final BufferedImage atlas;
        public final int originX;
        public final int originY;
        public final int width;
        public final int height;

        AtlasSlot(BufferedImage atlas, int originX, int originY, int width, int height) {
            this.atlas = atlas;
            this.originX = originX;
            this.originY = originY;
            this.width = width;
            this.height = height;
        }
    }

    private static final int CELL_PX = 16;
    private static volatile WorldMapServerAtlas instance;

    private final BufferedImage atlas;
    private final int cols;
    private final int rows;
    private int nextCell;
    private final Map<String, AtlasSlot> slots = new LinkedHashMap<String, AtlasSlot>();

    private WorldMapServerAtlas(int sizePx) {
        int size = Math.max(256, Math.min(4096, sizePx));
        cols = size / CELL_PX;
        rows = cols;
        atlas = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
    }

    public static WorldMapServerAtlas instance() {
        if (!Config.webWorldMapServerAtlasEnabled) {
            return null;
        }
        WorldMapServerAtlas atlasRef = instance;
        if (atlasRef == null) {
            synchronized (WorldMapServerAtlas.class) {
                atlasRef = instance;
                if (atlasRef == null) {
                    atlasRef = new WorldMapServerAtlas(Config.webWorldMapServerAtlasPx);
                    instance = atlasRef;
                }
            }
        }
        return atlasRef;
    }

    public static void reset() {
        synchronized (WorldMapServerAtlas.class) {
            instance = null;
        }
    }

    /**
     * Registers a loaded face texture in the atlas; returns slot for sampling.
     */
    public AtlasSlot register(String cacheKey, BufferedImage texture) {
        if (cacheKey == null || cacheKey.isEmpty() || texture == null) {
            return null;
        }
        synchronized (slots) {
            AtlasSlot existing = slots.get(cacheKey);
            if (existing != null) {
                return existing;
            }
            if (nextCell >= cols * rows) {
                return null;
            }
            int cellX = nextCell % cols;
            int cellY = nextCell / cols;
            nextCell++;
            int destX = cellX * CELL_PX;
            int destY = cellY * CELL_PX;
            int w = Math.min(CELL_PX, texture.getWidth());
            int h = Math.min(CELL_PX, texture.getHeight());
            Graphics2D g = atlas.createGraphics();
            g.drawImage(texture, destX, destY, destX + w, destY + h, 0, 0, w, h, null);
            g.dispose();
            AtlasSlot slot = new AtlasSlot(atlas, destX, destY, w, h);
            slots.put(cacheKey, slot);
            return slot;
        }
    }

    public AtlasSlot slotForKey(String cacheKey) {
        if (cacheKey == null) {
            return null;
        }
        synchronized (slots) {
            return slots.get(cacheKey);
        }
    }

    public int slotCount() {
        synchronized (slots) {
            return slots.size();
        }
    }

    public static int sampleSlotRgb(AtlasSlot slot, int u, int v) {
        if (slot == null || slot.atlas == null) {
            return -1;
        }
        int tx = slot.originX + (u & 15);
        int ty = slot.originY + (v & 15);
        if (tx < 0 || ty < 0 || tx >= slot.atlas.getWidth() || ty >= slot.atlas.getHeight()) {
            return -1;
        }
        int argb = slot.atlas.getRGB(tx, ty);
        int alpha = (argb >> 24) & 0xFF;
        if (alpha < 128) {
            return -1;
        }
        return argb & 0xFFFFFF;
    }
}
