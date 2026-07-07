package com.imgood.textech.client.worldmap;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javax.imageio.ImageIO;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.Config;
import com.imgood.textech.webae.icon.IconRenderGuard;
import com.imgood.textech.webae.worldmap.WorldMapRenderSupport;
import com.imgood.textech.webae.worldmap.WorldMapView;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Off-screen FBO renderer for a single chunk tile using client block textures.
 */
@SideOnly(Side.CLIENT)
public final class WorldMapChunkGlRenderer {

    private Framebuffer fbo;
    private int fboSize;
    private boolean prevScissorEnabled;
    private final RenderBlocks renderBlocks = new RenderBlocks();

    public byte[] render(Minecraft mc, WorldMapView view, int dim, int chunkX, int chunkZ) {
        return renderTerrain(mc, view, dim, chunkX, chunkZ);
    }

    public byte[] renderTerrain(Minecraft mc, WorldMapView view, int dim, int chunkX, int chunkZ) {
        if (mc == null || mc.theWorld == null || view == null) {
            return null;
        }
        if (mc.theWorld.provider.dimensionId != dim) {
            return null;
        }

        int tilePx = Math.max(16, Config.webWorldMapTilePx);
        Chunk chunk = mc.theWorld.getChunkFromChunkCoords(chunkX, chunkZ);
        if (chunk == null) {
            return null;
        }

        WorldMapOrthoCamera camera = WorldMapOrthoCamera.forView(view, chunkX, chunkZ);
        try {
            ensureFbo(tilePx);
            beginFboRender(tilePx);
            camera.apply();
            setupSceneLighting();

            renderBlocks.blockAccess = mc.theWorld;
            if (view == WorldMapView.FLAT) {
                renderFlat(mc.theWorld, chunk, chunkX, chunkZ);
            } else if (view.isOblique()) {
                renderOblique(mc.theWorld, chunk, chunkX, chunkZ, view.obliqueDirection);
            } else {
                return null;
            }

            byte[] png = readPixelsToPng(tilePx);
            finishFboRender(mc);
            return png;
        } catch (Throwable t) {
            AdvanceDataMonitor.LOG.debug(
                "[WebAE] HD world map GL render failed view={} dim={} cx={} cz={}: {}",
                view.id,
                dim,
                chunkX,
                chunkZ,
                t.getMessage());
            return null;
        } finally {
            IconRenderGuard.afterRender(mc);
        }
    }

    /** Renders only AE-related blocks in the chunk onto a transparent PNG. */
    public byte[] renderAeOverlay(Minecraft mc, WorldMapView view, int dim, int chunkX, int chunkZ) {
        if (mc == null || mc.theWorld == null || view == null) {
            return null;
        }
        if (mc.theWorld.provider.dimensionId != dim) {
            return null;
        }

        int tilePx = Math.max(16, Config.webWorldMapTilePx);
        Chunk chunk = mc.theWorld.getChunkFromChunkCoords(chunkX, chunkZ);
        if (chunk == null) {
            return null;
        }

        WorldMapOrthoCamera camera = WorldMapOrthoCamera.forView(view, chunkX, chunkZ);
        try {
            ensureFbo(tilePx);
            beginFboRender(tilePx);
            camera.apply();
            setupSceneLighting();

            renderBlocks.blockAccess = mc.theWorld;
            int painted = 0;
            if (view == WorldMapView.FLAT) {
                painted = renderAeFlat(mc.theWorld, chunk, chunkX, chunkZ);
            } else if (view.isOblique()) {
                painted = renderAeOblique(mc.theWorld, chunk, chunkX, chunkZ, view.obliqueDirection);
            } else {
                return null;
            }
            if (painted <= 0) {
                finishFboRender(mc);
                return null;
            }

            byte[] png = readPixelsToPng(tilePx);
            finishFboRender(mc);
            return png;
        } catch (Throwable t) {
            AdvanceDataMonitor.LOG.debug(
                "[WebAE] HD world map AE overlay failed view={} dim={} cx={} cz={}: {}",
                view.id,
                dim,
                chunkX,
                chunkZ,
                t.getMessage());
            return null;
        } finally {
            IconRenderGuard.afterRender(mc);
        }
    }

    private int renderAeFlat(World world, Chunk chunk, int chunkX, int chunkZ) {
        int painted = 0;
        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                int topY = -1;
                Block topBlock = null;
                int maxY = world.getHeight() - 1;
                if (maxY > 255) {
                    maxY = 255;
                }
                for (int y = maxY; y >= 0; y--) {
                    Block block = chunk.getBlock(lx, y, lz);
                    if (!isAeRelatedBlock(block, world, (chunkX << 4) + lx, y, (chunkZ << 4) + lz)) {
                        continue;
                    }
                    topY = y;
                    topBlock = block;
                    break;
                }
                if (topY < 0 || topBlock == null) {
                    continue;
                }
                int wx = (chunkX << 4) + lx;
                int wz = (chunkZ << 4) + lz;
                renderBlocks.renderBlockByRenderType(topBlock, wx, topY, wz);
                painted++;
            }
        }
        return painted;
    }

    private int renderAeOblique(World world, Chunk chunk, int chunkX, int chunkZ,
        com.imgood.textech.webae.worldmap.WorldMapObliqueDirection direction) {
        if (direction == null) {
            direction = com.imgood.textech.webae.worldmap.WorldMapObliqueDirection.SE;
        }
        int painted = 0;
        int[] mapped = new int[2];
        List<Column> columns = new ArrayList<Column>();
        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                direction.mapLocal(lx, lz, mapped);
                int sampleX = mapped[0];
                int sampleZ = mapped[1];
                int maxY = world.getHeight() - 1;
                if (maxY > 255) {
                    maxY = 255;
                }
                for (int y = maxY; y >= 0; y--) {
                    Block block = chunk.getBlock(sampleX, y, sampleZ);
                    if (!isAeRelatedBlock(block, world, (chunkX << 4) + sampleX, y, (chunkZ << 4) + sampleZ)) {
                        continue;
                    }
                    columns.add(new Column(lx, lz, sampleX, sampleZ, y));
                    break;
                }
            }
        }
        Collections.sort(
            columns,
            new Comparator<Column>() {

                @Override
                public int compare(Column a, Column b) {
                    int sumA = a.lx + a.lz;
                    int sumB = b.lx + b.lz;
                    if (sumA != sumB) {
                        return sumB - sumA;
                    }
                    if (a.lz != b.lz) {
                        return b.lz - a.lz;
                    }
                    return b.lx - a.lx;
                }
            });

        for (Column col : columns) {
            int southY = neighborTopAeY(chunk, world, col.sampleX, col.sampleZ + 1, chunkX, chunkZ);
            int eastY = neighborTopAeY(chunk, world, col.sampleX + 1, col.sampleZ, chunkX, chunkZ);
            int minY = col.topY;
            if (southY >= 0 && southY < minY) {
                minY = southY;
            }
            if (eastY >= 0 && eastY < minY) {
                minY = eastY;
            }
            for (int y = minY; y <= col.topY; y++) {
                Block block = chunk.getBlock(col.sampleX, y, col.sampleZ);
                if (!isAeRelatedBlock(block, world, (chunkX << 4) + col.sampleX, y, (chunkZ << 4) + col.sampleZ)) {
                    continue;
                }
                int wx = (chunkX << 4) + col.sampleX;
                int wz = (chunkZ << 4) + col.sampleZ;
                renderBlocks.renderBlockByRenderType(block, wx, y, wz);
                painted++;
            }
        }
        return painted;
    }

    private static int neighborTopAeY(Chunk chunk, World world, int lx, int lz, int chunkX, int chunkZ) {
        if (lx < 0 || lx >= 16 || lz < 0 || lz >= 16) {
            return -1;
        }
        int maxY = world.getHeight() - 1;
        if (maxY > 255) {
            maxY = 255;
        }
        for (int y = maxY; y >= 0; y--) {
            Block block = chunk.getBlock(lx, y, lz);
            if (isAeRelatedBlock(block, world, (chunkX << 4) + lx, y, (chunkZ << 4) + lz)) {
                return y;
            }
        }
        return -1;
    }

    private static boolean isAeRelatedBlock(Block block, World world, int x, int y, int z) {
        if (block == null || block == Blocks.air) {
            return false;
        }
        String reg = net.minecraft.block.Block.blockRegistry.getNameForObject(block);
        if (reg != null) {
            String lower = reg.toLowerCase();
            if (lower.contains("appliedenergistics") || lower.contains("ae2") || lower.contains("appeng")) {
                return true;
            }
        }
        net.minecraft.tileentity.TileEntity te = world.getTileEntity(x, y, z);
        if (te != null) {
            String cn = te.getClass()
                .getName();
            if (cn != null && cn.contains("appeng")) {
                return true;
            }
        }
        return false;
    }

    public void reset() {
        if (fbo != null) {
            fbo.deleteFramebuffer();
            fbo = null;
            fboSize = 0;
        }
    }

    private void setupSceneLighting() {
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240.0F, 240.0F);
        RenderHelper.enableStandardItemLighting();
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private void renderFlat(World world, Chunk chunk, int chunkX, int chunkZ) {
        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                int topY = findTopSolidY(chunk, lx, lz, world);
                if (topY < 0) {
                    continue;
                }
                Block block = chunk.getBlock(lx, topY, lz);
                if (!WorldMapRenderSupport.isMapSolid(block)) {
                    continue;
                }
                int meta = chunk.getBlockMetadata(lx, topY, lz);
                int wx = (chunkX << 4) + lx;
                int wz = (chunkZ << 4) + lz;
                renderBlocks.renderBlockByRenderType(block, wx, topY, wz);
            }
        }
    }

    private void renderOblique(World world, Chunk chunk, int chunkX, int chunkZ,
        com.imgood.textech.webae.worldmap.WorldMapObliqueDirection direction) {
        if (direction == null) {
            direction = com.imgood.textech.webae.worldmap.WorldMapObliqueDirection.SE;
        }
        int[] mapped = new int[2];
        List<Column> columns = new ArrayList<Column>();
        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                direction.mapLocal(lx, lz, mapped);
                int sampleX = mapped[0];
                int sampleZ = mapped[1];
                int topY = findTopSolidY(chunk, sampleX, sampleZ, world);
                if (topY >= 0) {
                    columns.add(new Column(lx, lz, sampleX, sampleZ, topY));
                }
            }
        }
        Collections.sort(
            columns,
            new Comparator<Column>() {

                @Override
                public int compare(Column a, Column b) {
                    int sumA = a.lx + a.lz;
                    int sumB = b.lx + b.lz;
                    if (sumA != sumB) {
                        return sumB - sumA;
                    }
                    if (a.lz != b.lz) {
                        return b.lz - a.lz;
                    }
                    return b.lx - a.lx;
                }
            });

        for (Column col : columns) {
            int southY = neighborTopY(chunk, col.sampleX, col.sampleZ + 1);
            int eastY = neighborTopY(chunk, col.sampleX + 1, col.sampleZ);
            int minY = col.topY;
            if (southY >= 0 && southY < minY) {
                minY = southY;
            }
            if (eastY >= 0 && eastY < minY) {
                minY = eastY;
            }
            for (int y = minY; y <= col.topY; y++) {
                Block block = chunk.getBlock(col.sampleX, y, col.sampleZ);
                if (!WorldMapRenderSupport.isMapSolid(block)) {
                    continue;
                }
                int wx = (chunkX << 4) + col.sampleX;
                int wz = (chunkZ << 4) + col.sampleZ;
                renderBlocks.renderBlockByRenderType(block, wx, y, wz);
            }
        }
    }

    private static int findTopSolidY(Chunk chunk, int lx, int lz, World world) {
        int maxY = world.getHeight() - 1;
        if (maxY > 255) {
            maxY = 255;
        }
        for (int y = maxY; y >= 0; y--) {
            Block block = chunk.getBlock(lx, y, lz);
            if (block == null || block == Blocks.air) {
                continue;
            }
            if (WorldMapRenderSupport.isSoftBlock(block)) {
                continue;
            }
            return y;
        }
        return -1;
    }

    private static int neighborTopY(Chunk chunk, int lx, int lz) {
        if (lx < 0 || lx >= 16 || lz < 0 || lz >= 16) {
            return -1;
        }
        int maxY = 255;
        for (int y = maxY; y >= 0; y--) {
            Block block = chunk.getBlock(lx, y, lz);
            if (block == null || block == Blocks.air) {
                continue;
            }
            if (WorldMapRenderSupport.isSoftBlock(block)) {
                continue;
            }
            return y;
        }
        return -1;
    }

    private void ensureFbo(int size) {
        if (fbo == null || fboSize != size) {
            if (fbo != null) {
                fbo.deleteFramebuffer();
            }
            fbo = new Framebuffer(size, size, true);
            fboSize = size;
        }
    }

    private void beginFboRender(int size) {
        try {
            prevScissorEnabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        } catch (Throwable ignored) {
            prevScissorEnabled = false;
        }
        fbo.bindFramebuffer(true);
        GL11.glViewport(0, 0, size, size);
        GL11.glScissor(0, 0, size, size);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glClearColor(0.0F, 0.0F, 0.0F, 0.0F);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    }

    private void finishFboRender(Minecraft mc) {
        GL11.glDisable(GL11.GL_BLEND);
        RenderHelper.disableStandardItemLighting();
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL12.GL_RESCALE_NORMAL);
        if (!prevScissorEnabled) {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
        }
        mc.getFramebuffer()
            .bindFramebuffer(true);
    }

    private byte[] readPixelsToPng(int size) {
        ByteBuffer buf = BufferUtils.createByteBuffer(size * size * 4);
        GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
        GL11.glReadPixels(0, 0, size, size, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buf);

        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        int[] row = new int[size];
        for (int y = 0; y < size; y++) {
            int srcY = size - 1 - y;
            buf.position(srcY * size * 4);
            for (int x = 0; x < size; x++) {
                int r = buf.get() & 0xFF;
                int g = buf.get() & 0xFF;
                int b = buf.get() & 0xFF;
                int a = buf.get() & 0xFF;
                row[x] = (a << 24) | (r << 16) | (g << 8) | b;
            }
            img.setRGB(0, y, size, 1, row, 0, size);
        }

        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream(size * size);
            ImageIO.write(img, "png", out);
            return out.toByteArray();
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.warn("[WebAE] Failed to encode HD world map PNG", e);
            return null;
        }
    }

    private static final class Column {

        final int lx;
        final int lz;
        final int sampleX;
        final int sampleZ;
        final int topY;

        Column(int lx, int lz, int sampleX, int sampleZ, int topY) {
            this.lx = lx;
            this.lz = lz;
            this.sampleX = sampleX;
            this.sampleZ = sampleZ;
            this.topY = topY;
        }
    }
}
