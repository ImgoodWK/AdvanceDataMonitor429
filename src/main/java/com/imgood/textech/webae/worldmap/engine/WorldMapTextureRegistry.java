package com.imgood.textech.webae.worldmap.engine;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import net.minecraft.block.Block;

import com.imgood.textech.Config;
import com.imgood.textech.webae.worldmap.WorldMapBlockColorResolver;

import cpw.mods.fml.client.FMLClientHandler;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.registry.GameRegistry;

/**
 * Lazy-loaded block face textures from mod JAR {@code assets/.../textures/blocks/*.png} with LRU cache.
 * When running on an integrated server (client available), resource pack textures are preferred over
 * JAR-internal textures. On a dedicated server, only JAR textures are used.
 */
public final class WorldMapTextureRegistry {

    private static volatile Field textureNameField;
    private static volatile Map<String, BufferedImage> cache;
    private static volatile boolean resourcePacksProbed;
    private static volatile List<File> resourcePackRoots;
    private static volatile boolean isClientAvailable;

    private WorldMapTextureRegistry() {}

    /**
     * @return cached face texture, or {@code null} on miss
     */
    public static BufferedImage faceTexture(Block block, int meta, WorldMapBlockColorResolver.BlockFace face) {
        if (block == null) {
            return null;
        }
        WorldMapBlockColorResolver.BlockFace sampleFace = face != null ? face
            : WorldMapBlockColorResolver.BlockFace.TOP;
        String modId = resolveModId(block);
        String baseName = resolveTextureBaseName(block);
        if (baseName == null || baseName.isEmpty()) {
            return null;
        }
        String cacheKey = modId + ":" + baseName + ":" + meta + ":" + sampleFace.name();
        Map<String, BufferedImage> map = cacheMap();
        synchronized (map) {
            BufferedImage cached = map.get(cacheKey);
            if (cached != null) {
                return cached;
            }
        }
        BufferedImage loaded = loadFaceTexture(block, modId, baseName, meta, sampleFace);
        if (loaded != null && Config.webWorldMapServerAtlasEnabled) {
            WorldMapServerAtlas atlas = WorldMapServerAtlas.instance();
            if (atlas != null) {
                atlas.register(cacheKey, loaded);
            }
        }
        synchronized (map) {
            map.put(cacheKey, loaded);
        }
        return loaded;
    }

    public static int samplePixelRgb(BufferedImage texture, int u, int v) {
        if (texture == null) {
            return -1;
        }
        int w = texture.getWidth();
        int h = texture.getHeight();
        if (w <= 0 || h <= 0) {
            return -1;
        }
        int tx = u;
        int ty = v;
        if (tx < 0) {
            tx = 0;
        }
        if (ty < 0) {
            ty = 0;
        }
        if (tx >= w) {
            tx = w - 1;
        }
        if (ty >= h) {
            ty = h - 1;
        }
        int argb = texture.getRGB(tx, ty);
        int alpha = (argb >> 24) & 0xFF;
        if (alpha < 128) {
            return -1;
        }
        return argb & 0xFFFFFF;
    }

    private static Map<String, BufferedImage> cacheMap() {
        Map<String, BufferedImage> map = cache;
        if (map == null) {
            synchronized (WorldMapTextureRegistry.class) {
                map = cache;
                if (map == null) {
                    final int max = Math.max(64, Config.webWorldMapTextureCacheMax);
                    map = new LinkedHashMap<String, BufferedImage>(64, 0.75f, true) {

                        private static final long serialVersionUID = 1L;

                        @Override
                        protected boolean removeEldestEntry(Map.Entry<String, BufferedImage> eldest) {
                            return size() > max;
                        }
                    };
                    cache = map;
                }
            }
        }
        return map;
    }

    private static BufferedImage loadFaceTexture(Block block, String modId, String baseName, int meta,
        WorldMapBlockColorResolver.BlockFace face) {
        String[] candidates = buildFaceCandidates(modId, baseName, meta, face);

        // 1. Try resource pack file system (integrated server only)
        for (String relativePath : candidates) {
            BufferedImage img = readFromResourcePack(relativePath);
            if (img != null) {
                return img;
            }
        }

        // 2. Fall back to JAR classloader (server-only or resource pack miss)
        ClassLoader[] loaders = new ClassLoader[] { block.getClass()
            .getClassLoader(), WorldMapTextureRegistry.class.getClassLoader(),
            Thread.currentThread()
                .getContextClassLoader() };
        for (String path : candidates) {
            for (ClassLoader loader : loaders) {
                if (loader == null) {
                    continue;
                }
                BufferedImage img = readImage(loader, path);
                if (img != null) {
                    return img;
                }
            }
        }
        return null;
    }

    /**
     * Tries to read a texture from active resource pack directories/zips.
     * Only works when running on an integrated server (client side is present).
     */
    private static BufferedImage readFromResourcePack(String relativePath) {
        if (!isClientAvailable()) {
            return null;
        }
        List<File> roots = resourcePackRoots();
        if (roots.isEmpty()) {
            return null;
        }
        for (File root : roots) {
            if (root == null || !root.exists()) {
                continue;
            }
            // Direct directory resource pack
            if (root.isDirectory()) {
                File target = new File(root, relativePath);
                if (target.isFile()) {
                    BufferedImage img = readFileImage(target);
                    if (img != null) {
                        return img;
                    }
                }
            } else if (root.isFile()) {
                // Zip-based resource pack: try reading as zip entry
                BufferedImage img = readZipImage(root, relativePath);
                if (img != null) {
                    return img;
                }
            }
        }
        return null;
    }

    private static boolean isClientAvailable() {
        if (!resourcePacksProbed) {
            synchronized (WorldMapTextureRegistry.class) {
                if (!resourcePacksProbed) {
                    try {
                        isClientAvailable = FMLCommonHandler.instance()
                            .getSide()
                            .isClient();
                    } catch (Throwable ignored) {
                        isClientAvailable = false;
                    }
                    resourcePacksProbed = true;
                }
            }
        }
        return isClientAvailable;
    }

    private static List<File> resourcePackRoots() {
        List<File> roots = resourcePackRoots;
        if (roots == null) {
            synchronized (WorldMapTextureRegistry.class) {
                roots = resourcePackRoots;
                if (roots == null) {
                    roots = new ArrayList<File>();
                    try {
                        FMLClientHandler client = FMLClientHandler.instance();
                        if (client != null && client.getClient() != null) {
                            // Try to get resource pack list
                            File resourcePacksDir = new File(client.getClient().mcDataDir, "resourcepacks");
                            if (resourcePacksDir.isDirectory()) {
                                File[] packs = resourcePacksDir.listFiles();
                                if (packs != null) {
                                    for (File pack : packs) {
                                        if (pack.isDirectory() || (pack.isFile() && pack.getName()
                                            .endsWith(".zip"))) {
                                            roots.add(pack);
                                        }
                                    }
                                }
                            }
                        }
                    } catch (Throwable ignored) {}
                    resourcePackRoots = roots;
                }
            }
        }
        return roots;
    }

    private static BufferedImage readFileImage(File file) {
        try {
            InputStream in = new FileInputStream(file);
            try {
                return ImageIO.read(in);
            } finally {
                in.close();
            }
        } catch (Exception ignored) {
            return null;
        }
    }

    private static BufferedImage readZipImage(File zipFile, String entryPath) {
        try {
            java.util.zip.ZipFile zf = new java.util.zip.ZipFile(zipFile);
            try {
                java.util.zip.ZipEntry entry = zf.getEntry(entryPath);
                if (entry != null) {
                    InputStream in = zf.getInputStream(entry);
                    try {
                        return ImageIO.read(in);
                    } finally {
                        in.close();
                    }
                }
            } finally {
                zf.close();
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    private static String resolveModId(Block block) {
        GameRegistry.UniqueIdentifier uid = GameRegistry.findUniqueIdentifierFor(block);
        if (uid != null && uid.modId != null && !uid.modId.isEmpty()) {
            return uid.modId;
        }
        return "minecraft";
    }

    private static String resolveTextureBaseName(Block block) {
        Field field = textureNameField;
        if (field == null) {
            synchronized (WorldMapTextureRegistry.class) {
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

    private static BufferedImage readImage(ClassLoader loader, String resourcePath) {
        InputStream in = null;
        try {
            in = loader.getResourceAsStream(resourcePath);
            if (in == null) {
                return null;
            }
            return ImageIO.read(in);
        } catch (Exception ignored) {
            return null;
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (Exception ignored) {}
            }
        }
    }
}
