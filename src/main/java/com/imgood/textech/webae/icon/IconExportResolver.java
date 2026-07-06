package com.imgood.textech.webae.icon;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Unified multi-stage fallback for icon export: grid crop → GL custom → GL vanilla → atlas →
 * ItemBlock/entity → placeholder. Shared by bulk grid export and lazy single-icon upload.
 */
@SideOnly(Side.CLIENT)
public final class IconExportResolver {

    public enum Source {
        GRID,
        GL_NEI,
        GL_INVENTORY,
        GL_INVENTORY_FLAT,
        GL_VANILLA,
        ATLAS,
        BLOCK,
        ENTITY,
        PLACEHOLDER
    }

    public static final class ResolveResult {

        public final byte[] png;
        public final Source source;

        ResolveResult(byte[] png, Source source) {
            this.png = png;
            this.source = source;
        }
    }

    private final IconAtlasSampler atlasSampler;
    private final IconGlFallback glFallback;

    private int gridCount;
    private int glCount;
    private int vanillaCount;
    private int atlasCount;
    private int blockCount;
    private int entityCount;
    private int placeholderCount;

    public IconExportResolver(IconAtlasSampler atlasSampler, IconGlFallback glFallback) {
        this.atlasSampler = atlasSampler;
        this.glFallback = glFallback;
    }

    /** Standalone resolver for verify/preview screens outside {@link IconRenderer} session. */
    public static IconExportResolver createStandalone() {
        return new IconExportResolver(new IconAtlasSampler(), new IconGlFallback());
    }

    public void reset() {
        atlasSampler.reset();
        glFallback.reset();
        resetCounts();
    }

    public void resetCounts() {
        gridCount = 0;
        glCount = 0;
        vanillaCount = 0;
        atlasCount = 0;
        blockCount = 0;
        entityCount = 0;
        placeholderCount = 0;
    }

    public int getGridCount() {
        return gridCount;
    }

    public int getGlCount() {
        return glCount;
    }

    public int getVanillaCount() {
        return vanillaCount;
    }

    public int getAtlasCount() {
        return atlasCount;
    }

    public int getBlockCount() {
        return blockCount;
    }

    public int getEntityCount() {
        return entityCount;
    }

    public int getPlaceholderCount() {
        return placeholderCount;
    }

    /**
     * @param gridCropPng optional pre-cropped grid cell; may be null or blank
     */
    public ResolveResult resolve(Minecraft mc, ItemStack stack, String itemId, byte[] gridCropPng) {
        if (stack == null) {
            return placeholder(itemId);
        }

        byte[] png = gridCropPng;
        if (!IconAtlasSampler.isPngBlank(png)) {
            gridCount++;
            return new ResolveResult(png, Source.GRID);
        }

        png = glFallback.renderNeiSlotIcon(mc, stack);
        if (!IconAtlasSampler.isPngBlank(png)) {
            glCount++;
            return new ResolveResult(png, Source.GL_NEI);
        }

        png = glFallback.renderFlatInventoryIcon(mc, stack);
        if (!IconAtlasSampler.isPngBlank(png)) {
            glCount++;
            return new ResolveResult(png, Source.GL_INVENTORY_FLAT);
        }

        png = glFallback.renderInventoryIcon(mc, stack);
        if (!IconAtlasSampler.isPngBlank(png)) {
            glCount++;
            return new ResolveResult(png, Source.GL_INVENTORY);
        }

        png = glFallback.renderVanillaNeiSlotIcon(mc, stack);
        if (!IconAtlasSampler.isPngBlank(png)) {
            vanillaCount++;
            return new ResolveResult(png, Source.GL_VANILLA);
        }

        atlasSampler.ensureAtlases(mc);
        png = atlasSampler.sampleItemStack(mc, stack);
        if (!IconAtlasSampler.isPngBlank(png)) {
            atlasCount++;
            return new ResolveResult(png, Source.ATLAS);
        }

        png = IconBlockRenderer.render(mc, stack, glFallback);
        if (!IconAtlasSampler.isPngBlank(png)) {
            blockCount++;
            return new ResolveResult(png, Source.BLOCK);
        }

        png = glFallback.renderEntityIcon(mc, stack);
        if (!IconAtlasSampler.isPngBlank(png)) {
            entityCount++;
            return new ResolveResult(png, Source.ENTITY);
        }

        return placeholder(itemId);
    }

    private ResolveResult placeholder(String itemId) {
        placeholderCount++;
        return new ResolveResult(IconRenderer.createPlaceholderPng(itemId), Source.PLACEHOLDER);
    }
}
