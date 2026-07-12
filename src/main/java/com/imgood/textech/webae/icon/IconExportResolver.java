package com.imgood.textech.webae.icon;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Unified icon export resolver. Active path: fluid specials → NESQL-style drawItem → placeholder.
 *
 * <p>
 * Legacy multi-stage fallback (grid crop → GL variants → atlas → block → entity) is retained in
 * {@link #resolveLegacy} for archival / comparison; active callers must not use it.
 * </p>
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
        NESQL,
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
    private final IconNesqlStyleRenderer nesqlRenderer;

    private int gridCount;
    private int glCount;
    private int vanillaCount;
    private int atlasCount;
    private int blockCount;
    private int entityCount;
    private int nesqlCount;
    private int placeholderCount;

    public IconExportResolver(IconAtlasSampler atlasSampler, IconGlFallback glFallback) {
        this.atlasSampler = atlasSampler;
        this.glFallback = glFallback;
        this.nesqlRenderer = new IconNesqlStyleRenderer();
    }

    /** Standalone resolver for verify/preview screens outside {@link IconRenderer} session. */
    public static IconExportResolver createStandalone() {
        return new IconExportResolver(new IconAtlasSampler(), new IconGlFallback());
    }

    public void reset() {
        atlasSampler.reset();
        glFallback.reset();
        nesqlRenderer.reset();
        resetCounts();
    }

    public void resetCounts() {
        gridCount = 0;
        glCount = 0;
        vanillaCount = 0;
        atlasCount = 0;
        blockCount = 0;
        entityCount = 0;
        nesqlCount = 0;
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

    public int getNesqlCount() {
        return nesqlCount;
    }

    public int getPlaceholderCount() {
        return placeholderCount;
    }

    /**
     * Active resolve: fluid-aware GL → NESQL drawItem FBO → placeholder.
     *
     * @param gridCropPng ignored on the active path (kept for call-site compatibility)
     */
    public ResolveResult resolve(Minecraft mc, ItemStack stack, String itemId, byte[] gridCropPng) {
        if (stack == null) {
            return placeholder(itemId);
        }

        if (IconFluidRenderer.needsInGameItemRender(stack)) {
            byte[] png = glFallback.renderFluidAwareSlotIcon(mc, stack);
            if (!IconAtlasSampler.isPngBlank(png)) {
                glCount++;
                return new ResolveResult(png, Source.GL_NEI);
            }
        }

        byte[] png = nesqlRenderer.renderItem(mc, stack);
        if (!IconAtlasSampler.isPngBlank(png)) {
            nesqlCount++;
            return new ResolveResult(png, Source.NESQL);
        }

        return placeholder(itemId);
    }

    /**
     * Archived multi-stage fallback used before NESQL-style became the sole active path.
     * Not called by bulk/lazy/direct export.
     */
    @Deprecated
    public ResolveResult resolveLegacy(Minecraft mc, ItemStack stack, String itemId, byte[] gridCropPng) {
        if (stack == null) {
            return placeholder(itemId);
        }

        if (IconFluidRenderer.needsInGameItemRender(stack)) {
            byte[] png = glFallback.renderFluidAwareSlotIcon(mc, stack);
            if (!IconAtlasSampler.isPngBlank(png)) {
                glCount++;
                return new ResolveResult(png, Source.GL_NEI);
            }
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
