package com.imgood.textech.client.worldmap.dynmap;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.Config;
import com.imgood.textech.webae.worldmap.WorldMapTerrainCaptureResult;
import com.imgood.textech.webae.worldmap.WorldMapTerrainSourceId;
import com.imgood.textech.webae.worldmap.WorldMapView;
import com.imgood.textech.webae.worldmap.dynmap.WorldMapDynmapChunkCropper;
import com.imgood.textech.webae.worldmap.dynmap.WorldMapDynmapCoordMapper;
import com.imgood.textech.webae.worldmap.dynmap.WorldMapDynmapDetector;
import com.imgood.textech.webae.worldmap.dynmap.WorldMapDynmapWorldNames;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Client-side Dynmap terrain fetch: local tile crop first, then HTTP GET from dynmapBaseUrl.
 */
@SideOnly(Side.CLIENT)
public final class WorldMapDynmapClientFetcher {

    private static final int MAX_DYNMAP_ZOOM = 6;
    private static final int HTTP_TIMEOUT_MS = 4000;
    private static final WorldMapDynmapClientFetcher INSTANCE = new WorldMapDynmapClientFetcher();

    private int httpBudgetThisTick;

    private WorldMapDynmapClientFetcher() {}

    public static WorldMapDynmapClientFetcher instance() {
        return INSTANCE;
    }

    public void onClientTickEnd() {
        httpBudgetThisTick = 0;
    }

    public WorldMapTerrainCaptureResult capture(WorldMapView view, int dim, int chunkX, int chunkZ, int tilePx) {
        if (!Config.worldMapDynmapCaptureEnabled) {
            return null;
        }
        String viewId = view != null ? view.id : WorldMapView.FLAT.id;
        byte[] local = WorldMapDynmapChunkCropper.cropChunkPng(viewId, dim, chunkX, chunkZ, tilePx);
        if (local != null && local.length > 0) {
            return new WorldMapTerrainCaptureResult(local, WorldMapTerrainSourceId.DYNMAP);
        }
        if (httpBudgetThisTick >= 1) {
            return null;
        }
        byte[] remote = fetchHttpTile(viewId, dim, chunkX, chunkZ, tilePx);
        if (remote != null && remote.length > 0) {
            httpBudgetThisTick++;
            return new WorldMapTerrainCaptureResult(remote, WorldMapTerrainSourceId.DYNMAP);
        }
        return null;
    }

    private static byte[] fetchHttpTile(String viewId, int dim, int chunkX, int chunkZ, int tilePx) {
        String baseUrl = resolveFetchBaseUrl();
        if (baseUrl == null) {
            return null;
        }
        String worldName = WorldMapDynmapWorldNames.resolveForDimension(dim);
        if (worldName == null) {
            worldName = "world";
        }
        String perspective = WorldMapDynmapCoordMapper.toDynmapPerspective(viewId);
        int worldOriginX = chunkX << 4;
        int worldOriginZ = chunkZ << 4;

        for (int zoom = MAX_DYNMAP_ZOOM; zoom >= 0; zoom--) {
            int tileX = WorldMapDynmapCoordMapper.worldToTileX(worldOriginX, zoom);
            int tileZ = WorldMapDynmapCoordMapper.worldToTileZ(worldOriginZ, zoom);
            byte[] tilePng = httpGetTile(baseUrl, worldName, perspective, zoom, tileX, tileZ);
            if (tilePng == null || tilePng.length == 0) {
                continue;
            }
            byte[] cropped = cropFromDownloadedTile(tilePng, worldOriginX, worldOriginZ, tileX, tileZ, zoom, tilePx);
            if (cropped != null && cropped.length > 0) {
                return cropped;
            }
        }
        return null;
    }

    private static String resolveFetchBaseUrl() {
        String override = Config.worldMapDynmapClientFetchUrl;
        if (override != null && !override.trim().isEmpty()) {
            return trimTrailingSlash(override.trim());
        }
        if (Config.webDynmapBaseUrl != null && !Config.webDynmapBaseUrl.trim().isEmpty()) {
            return trimTrailingSlash(Config.webDynmapBaseUrl.trim());
        }
        return null;
    }

    private static String trimTrailingSlash(String url) {
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }

    private static byte[] httpGetTile(String baseUrl, String worldName, String perspective, int zoom, int tileX,
        int tileZ) {
        String zoomPrefix = WorldMapDynmapCoordMapper.zoomPrefix(zoom);
        String path = baseUrl + "/tiles/" + worldName + "/" + perspective + "/" + zoomPrefix + "/" + tileX + "_"
            + tileZ + ".png";
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(path).openConnection();
            connection.setConnectTimeout(HTTP_TIMEOUT_MS);
            connection.setReadTimeout(HTTP_TIMEOUT_MS);
            connection.setRequestMethod("GET");
            int code = connection.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                return null;
            }
            InputStream in = connection.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream(8192);
            byte[] buf = new byte[4096];
            int read;
            while ((read = in.read(buf)) >= 0) {
                out.write(buf, 0, read);
            }
            in.close();
            return out.toByteArray();
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.debug("[WebAE] Dynmap HTTP tile fetch failed: {}", e.getMessage());
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static byte[] cropFromDownloadedTile(byte[] tilePng, int worldOriginX, int worldOriginZ, int tileX,
        int tileZ, int zoom, int targetPx) {
        try {
            java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(tilePng));
            if (img == null) {
                return null;
            }
            int span = WorldMapDynmapCoordMapper.tileBlockSpan(zoom);
            int tileWorldX = WorldMapDynmapCoordMapper.tileToWorldX(tileX, zoom);
            int tileWorldZ = WorldMapDynmapCoordMapper.tileToWorldZ(tileZ, zoom);
            int blockOffsetX = worldOriginX - tileWorldX;
            int blockOffsetZ = worldOriginZ - tileWorldZ;
            int imgW = img.getWidth();
            int imgH = img.getHeight();
            if (imgW <= 0 || imgH <= 0) {
                return null;
            }
            double pxPerBlock = (double) imgW / (double) span;
            int cropX = (int) Math.round(blockOffsetX * pxPerBlock);
            int cropY = (int) Math.round(blockOffsetZ * pxPerBlock);
            int cropSize = Math.max(1, (int) Math.round(16.0D * pxPerBlock));
            if (cropX < 0 || cropY < 0 || cropX + cropSize > imgW || cropY + cropSize > imgH) {
                return null;
            }
            java.awt.image.BufferedImage cropped = img.getSubimage(cropX, cropY, cropSize, cropSize);
            if (targetPx > 0 && cropSize != targetPx) {
                java.awt.image.BufferedImage scaled = new java.awt.image.BufferedImage(
                    targetPx,
                    targetPx,
                    java.awt.image.BufferedImage.TYPE_INT_ARGB);
                java.awt.Graphics2D g = scaled.createGraphics();
                g.setRenderingHint(
                    java.awt.RenderingHints.KEY_INTERPOLATION,
                    java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.drawImage(cropped, 0, 0, targetPx, targetPx, null);
                g.dispose();
                cropped = scaled;
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream(cropSize * cropSize);
            javax.imageio.ImageIO.write(cropped, "png", out);
            return out.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean isAnyDynmapSourceAvailable() {
        return WorldMapDynmapDetector.isDynmapAvailable()
            || (Config.webDynmapBaseUrl != null && !Config.webDynmapBaseUrl.trim().isEmpty())
            || (Config.worldMapDynmapClientFetchUrl != null && !Config.worldMapDynmapClientFetchUrl.trim().isEmpty());
    }
}
