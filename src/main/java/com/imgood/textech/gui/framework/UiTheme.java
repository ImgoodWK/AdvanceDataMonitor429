package com.imgood.textech.gui.framework;

import net.minecraft.util.ResourceLocation;

/**
 * Visual theme for the ADM UI framework — atlas regions, text colors, icon grid.
 */
public interface UiTheme {

    String id();

    NineSliceRegion mainPanel();

    NineSliceRegion buttonNormal();

    NineSliceRegion buttonHover();

    NineSliceRegion buttonDisabled();

    NineSliceRegion textFieldNormal();

    NineSliceRegion textFieldFocused();

    /** Container foreground labels (vanilla-style dark gray). */
    int textPrimary();

    /** ADM accent cyan for highlights. */
    int textAccent();

    int textDisabled();

    /** Icon atlas within the theme texture; may be null. */
    ResourceLocation iconAtlas();

    int iconAtlasSize();

    int iconSize();

    /** Icon grid origin on {@link #iconAtlas()}. */
    int iconGridU();

    int iconGridV();
}
