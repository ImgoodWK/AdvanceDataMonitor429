package com.imgood.textech.webae.worldmap;

/**
 * Result of a single-chunk terrain capture attempt.
 */
public final class WorldMapTerrainCaptureResult {

    public final byte[] png;
    public final WorldMapTerrainSourceId source;

    public WorldMapTerrainCaptureResult(byte[] png, WorldMapTerrainSourceId source) {
        this.png = png;
        this.source = source;
    }

    public boolean isValid() {
        return png != null && png.length > 0 && source != null;
    }
}
