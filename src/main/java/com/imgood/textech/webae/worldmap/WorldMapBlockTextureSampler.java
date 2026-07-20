package com.imgood.textech.webae.worldmap;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.imageio.ImageIO;

import net.minecraft.block.Block;

import cpw.mods.fml.common.registry.GameRegistry;

/**
 * Server-side block texture PNG reader (mod JAR {@code assets/.../textures/blocks/*.png}) with LRU cache.
 * Samples average RGB from the top-face texture; returns {@code -1} on miss.
 */
public final class WorldMapBlockTextureSampler {

    private static final int CACHE_MAX = 512;
    private static volatile Field textureNameField;

    private static final Map<String, Integer> CACHE = new LinkedHashMap<String, Integer>(64, 0.75f, true) {

        private static final long serialVersionUID = 1L;

        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Integer> eldest) {
            return size() > CACHE_MAX;
        }
    };

    private WorldMapBlockTextureSampler() {}

    /**
     * @return 24-bit RGB, or {@code -1} when no texture could be loaded
     */
    public static int sampleTopColor(Block block, int meta) {
        return sampleFaceColor(block, meta, WorldMapBlockColorResolver.BlockFace.TOP);
    }

    /**
     * @return 24-bit RGB, or {@code -1} when no texture could be loaded
     */
    public static int sampleFaceColor(Block block, int meta, WorldMapBlockColorResolver.BlockFace face) {
        if (block == null) {
            return -1;
        }
        WorldMapBlockColorResolver.BlockFace sampleFace = face != null ? face
            : WorldMapBlockColorResolver.BlockFace.TOP;
        String modId = resolveModId(block);
        String baseName = resolveTextureBaseName(block);
        if (baseName == null || baseName.isEmpty()) {
            return -1;
        }
        String cacheKey = modId + ":" + baseName + ":" + meta + ":" + sampleFace.name();
        synchronized (CACHE) {
            Integer cached = CACHE.get(cacheKey);
            if (cached != null) {
                return cached.intValue();
            }
        }
        int rgb = loadFaceColor(block, modId, baseName, meta, sampleFace);
        synchronized (CACHE) {
            CACHE.put(cacheKey, Integer.valueOf(rgb));
        }
        return rgb;
    }

    private static String resolveModId(Block block) {
        GameRegistry.UniqueIdentifier uid = GameRegistry.findUniqueIdentifierFor(block);
        if (uid != null && uid.modId != null && !uid.modId.isEmpty()) {
            return uid.modId;
        }
        return "minecraft";
    }

    /** MCP 1.7.10 Block has no public texture-name getter; read field or fall back to registry name. */
    private static String resolveTextureBaseName(Block block) {
        Field field = textureNameField;
        if (field == null) {
            synchronized (WorldMapBlockTextureSampler.class) {
                field = textureNameField;
                if (field == null) {
                    field = locateTextureNameField();
                    textureNameField = field;
                }
            }
        }
        if (field != null) {
            try {
                Object value = field.get(block);
                if (value instanceof String) {
                    String name = ((String) value).trim();
                    if (!name.isEmpty()) {
                        return name;
                    }
                }
            } catch (Exception ignored) {}
        }
        GameRegistry.UniqueIdentifier uid = GameRegistry.findUniqueIdentifierFor(block);
        if (uid == null || uid.name == null || uid.name.isEmpty()) {
            return null;
        }
        String registry = uid.name;
        if (registry.startsWith("tile.")) {
            registry = registry.substring("tile.".length());
        }
        return registry;
    }

    private static Field locateTextureNameField() {
        String[] names = { "textureName", "blockTextureName", "field_149761_L" };
        for (String name : names) {
            try {
                Field field = Block.class.getDeclaredField(name);
                if (field.getType() == String.class) {
                    field.setAccessible(true);
                    return field;
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    private static int loadFaceColor(Block block, String modId, String baseName, int meta,
        WorldMapBlockColorResolver.BlockFace face) {
        String[] candidates = buildFaceCandidates(modId, baseName, meta, face);
        ClassLoader[] loaders = new ClassLoader[] { block.getClass()
            .getClassLoader(), WorldMapBlockTextureSampler.class.getClassLoader(),
            Thread.currentThread()
                .getContextClassLoader() };
        for (String path : candidates) {
            for (ClassLoader loader : loaders) {
                if (loader == null) {
                    continue;
                }
                int rgb = readAverageRgb(loader, path);
                if (rgb >= 0) {
                    return rgb;
                }
            }
        }
        return -1;
    }

    private static String[] buildFaceCandidates(String modId, String baseName, int meta,
        WorldMapBlockColorResolver.BlockFace face) {
        String prefix = "assets/" + modId + "/textures/blocks/";
        if (face == WorldMapBlockColorResolver.BlockFace.TOP) {
            return new String[] { prefix + baseName + "_top.png", prefix + "top_" + baseName + ".png",
                prefix + baseName + ".png", prefix + baseName + "_" + meta + ".png" };
        }
        if (face == WorldMapBlockColorResolver.BlockFace.BOTTOM) {
            return new String[] { prefix + baseName + "_bottom.png", prefix + "bottom_" + baseName + ".png",
                prefix + baseName + ".png", prefix + baseName + "_" + meta + ".png" };
        }
        if (face == WorldMapBlockColorResolver.BlockFace.NORTH) {
            return new String[] { prefix + baseName + "_north.png", prefix + baseName + "_side.png",
                prefix + baseName + ".png", prefix + baseName + "_" + meta + ".png" };
        }
        if (face == WorldMapBlockColorResolver.BlockFace.SOUTH) {
            return new String[] { prefix + baseName + "_south.png", prefix + baseName + "_side.png",
                prefix + baseName + ".png", prefix + baseName + "_" + meta + ".png" };
        }
        if (face == WorldMapBlockColorResolver.BlockFace.EAST) {
            return new String[] { prefix + baseName + "_east.png", prefix + baseName + "_side.png",
                prefix + baseName + ".png", prefix + baseName + "_" + meta + ".png" };
        }
        return new String[] { prefix + baseName + "_west.png", prefix + baseName + "_side.png",
            prefix + baseName + ".png", prefix + baseName + "_" + meta + ".png" };
    }

    private static int readAverageRgb(ClassLoader loader, String resourcePath) {
        InputStream in = null;
        try {
            in = loader.getResourceAsStream(resourcePath);
            if (in == null) {
                return -1;
            }
            BufferedImage img = ImageIO.read(in);
            return averageOpaqueRgb(img);
        } catch (Exception ignored) {
            return -1;
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (Exception ignored) {}
            }
        }
    }

    private static int averageOpaqueRgb(BufferedImage img) {
        if (img == null) {
            return -1;
        }
        int w = img.getWidth();
        int h = img.getHeight();
        if (w <= 0 || h <= 0) {
            return -1;
        }
        long rSum = 0;
        long gSum = 0;
        long bSum = 0;
        long count = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = img.getRGB(x, y);
                int alpha = (argb >> 24) & 0xFF;
                if (alpha < 128) {
                    continue;
                }
                rSum += (argb >> 16) & 0xFF;
                gSum += (argb >> 8) & 0xFF;
                bSum += argb & 0xFF;
                count++;
            }
        }
        if (count == 0) {
            return -1;
        }
        int r = (int) (rSum / count);
        int g = (int) (gSum / count);
        int b = (int) (bSum / count);
        return (r << 16) | (g << 8) | b;
    }
}
