package com.imgood.textech.webae.worldmap.dynmap;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import javax.imageio.ImageIO;

import com.imgood.textech.AdvanceDataMonitor;

/**
 * Crops a single 16×16 Minecraft chunk region from a Dynmap/GWM pre-rendered tile PNG.
 */
public final class WorldMapDynmapChunkCropper {

    private static final int MAX_DYNMAP_ZOOM = 6;

    private WorldMapDynmapChunkCropper() {}

    /**
     * @param targetPx output edge length in pixels ({@code <= 0} keeps native crop size)
     * @return PNG bytes or {@code null}
     */
    public static byte[] cropChunkPng(String view, int dim, int chunkX, int chunkZ, int targetPx) {
        if (!WorldMapDynmapDetector.isDynmapAvailable()) {
            return null;
        }
        String worldName = WorldMapDynmapWorldNames.resolveForDimension(dim);
        if (worldName == null) {
            return null;
        }
        String perspective = WorldMapDynmapCoordMapper.toDynmapPerspective(view);
        int worldOriginX = chunkX << 4;
        int worldOriginZ = chunkZ << 4;

        for (int zoom = MAX_DYNMAP_ZOOM; zoom >= 0; zoom--) {
            int span = WorldMapDynmapCoordMapper.tileBlockSpan(zoom);
            int tileX = WorldMapDynmapCoordMapper.worldToTileX(worldOriginX, zoom);
            int tileZ = WorldMapDynmapCoordMapper.worldToTileZ(worldOriginZ, zoom);
            byte[] tilePng = WorldMapDynmapTileProvider.getTile(worldName, perspective, zoom, tileX, tileZ);
            if (tilePng == null || tilePng.length == 0) {
                continue;
            }
            byte[] cropped = cropFromTile(tilePng, worldOriginX, worldOriginZ, tileX, tileZ, zoom, targetPx);
            if (cropped != null) {
                return cropped;
            }
        }
        return null;
    }

    private static byte[] cropFromTile(byte[] tilePng, int worldOriginX, int worldOriginZ, int tileX, int tileZ,
        int zoom, int targetPx) {
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(tilePng));
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

            BufferedImage cropped = img.getSubimage(cropX, cropY, cropSize, cropSize);
            if (targetPx > 0 && cropSize != targetPx) {
                BufferedImage scaled = new BufferedImage(targetPx, targetPx, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = scaled.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.drawImage(cropped, 0, 0, targetPx, targetPx, null);
                g.dispose();
                cropped = scaled;
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream(cropSize * cropSize);
            ImageIO.write(cropped, "png", out);
            return out.toByteArray();
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.debug(
                "[WebAE] Dynmap chunk crop failed cx={} cz={} zoom={}: {}",
                tileX,
                tileZ,
                zoom,
                e.getMessage());
            return null;
        }
    }
}
