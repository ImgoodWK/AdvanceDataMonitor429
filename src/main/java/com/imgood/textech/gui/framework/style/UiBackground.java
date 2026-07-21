package com.imgood.textech.gui.framework.style;

import net.minecraft.util.ResourceLocation;

import com.imgood.textech.gui.framework.NineSliceRegion;

/**
 * Background fill mode for {@link UiStyle}.
 */
public final class UiBackground {

    public enum Kind {
        NONE,
        SOLID,
        NINE_SLICE,
        FULL_TEXTURE
    }

    public final Kind kind;
    public final int solidArgb;
    public final NineSliceRegion nineSlice;
    public final ResourceLocation texture;

    private UiBackground(Kind kind, int solidArgb, NineSliceRegion nineSlice, ResourceLocation texture) {
        this.kind = kind;
        this.solidArgb = solidArgb;
        this.nineSlice = nineSlice;
        this.texture = texture;
    }

    public static UiBackground none() {
        return new UiBackground(Kind.NONE, 0, null, null);
    }

    public static UiBackground solid(int argb) {
        return new UiBackground(Kind.SOLID, argb, null, null);
    }

    public static UiBackground nineSlice(NineSliceRegion region) {
        return new UiBackground(Kind.NINE_SLICE, 0, region, null);
    }

    public static UiBackground fullTexture(ResourceLocation texture) {
        return new UiBackground(Kind.FULL_TEXTURE, 0, null, texture);
    }
}
