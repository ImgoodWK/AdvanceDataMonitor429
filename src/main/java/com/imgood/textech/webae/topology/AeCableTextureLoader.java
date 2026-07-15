package com.imgood.textech.webae.topology;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.concurrent.ConcurrentHashMap;

import com.imgood.textech.AdvanceDataMonitor;

/**
 * Loads AE2 default (Fluix) cable part textures from the AE2 JAR classpath.
 * Used by topology simulated-cable view — not dye-tinted MECable_* overlays.
 *
 * @deprecated Gated by {@code Config.webTopologySimulatedEnabled} (default false).
 */
@Deprecated
public final class AeCableTextureLoader {

    private static final String[] AE_ASSET_MOD_IDS = { "appliedenergistics2", "appeng" };
    private static final ConcurrentHashMap<String, byte[]> CACHE = new ConcurrentHashMap<String, byte[]>();
    private static final byte[] EMPTY = new byte[0];

    private AeCableTextureLoader() {}

    /**
     * @param cableType {@code smart} | {@code covered} | {@code dense} (aliases accepted)
     * @return PNG bytes, or null when AE2 assets are unavailable
     */
    public static byte[] loadDefaultPng(String cableType) {
        String key = normalizeType(cableType);
        byte[] cached = CACHE.get(key);
        if (cached != null) {
            return cached.length == 0 ? null : cached;
        }
        String fileName = textureFileFor(key);
        byte[] png = readClasspathPng(fileName);
        if (png == null || png.length == 0) {
            CACHE.put(key, EMPTY);
            AdvanceDataMonitor.LOG.warn("[WebAE] AE2 cable texture missing: {}", fileName);
            return null;
        }
        CACHE.put(key, png);
        return png;
    }

    public static String normalizeType(String cableType) {
        if (cableType == null || cableType.isEmpty()) {
            return "covered";
        }
        String t = cableType.trim()
            .toLowerCase();
        if ("dense".equals(t) || "cable_dense".equals(t) || "dense_covered".equals(t)) {
            return "dense";
        }
        if ("smart".equals(t) || "cable_smart".equals(t)) {
            return "smart";
        }
        if ("glass".equals(t) || "cable_glass".equals(t)) {
            return "glass";
        }
        return "covered";
    }

    /** AE2 Fluix / Transparent default part textures (not dye MECable_*). */
    public static String textureFileFor(String normalizedType) {
        if ("dense".equals(normalizedType)) {
            return "ItemPart.CableDenseCovered.png";
        }
        if ("smart".equals(normalizedType)) {
            return "ItemPart.CableSmart.png";
        }
        if ("glass".equals(normalizedType)) {
            return "ItemPart.CableGlass.png";
        }
        return "ItemPart.CableCovered.png";
    }

    private static byte[] readClasspathPng(String fileName) {
        ClassLoader[] loaders = classLoaders();
        for (String modId : AE_ASSET_MOD_IDS) {
            String path = "assets/" + modId + "/textures/blocks/" + fileName;
            for (ClassLoader loader : loaders) {
                if (loader == null) {
                    continue;
                }
                byte[] data = readFully(loader, path);
                if (data != null && data.length > 0) {
                    return data;
                }
            }
        }
        return null;
    }

    private static ClassLoader[] classLoaders() {
        ClassLoader ae = null;
        try {
            ae = Class.forName("appeng.items.parts.ItemMultiPart")
                .getClassLoader();
        } catch (Throwable ignored) {}
        return new ClassLoader[] { ae, AeCableTextureLoader.class.getClassLoader(), Thread.currentThread()
            .getContextClassLoader() };
    }

    private static byte[] readFully(ClassLoader loader, String resourcePath) {
        InputStream in = null;
        try {
            in = loader.getResourceAsStream(resourcePath);
            if (in == null) {
                return null;
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream(4096);
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) >= 0) {
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        } catch (Exception e) {
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
