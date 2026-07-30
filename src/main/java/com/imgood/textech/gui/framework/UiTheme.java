package com.imgood.textech.gui.framework;

import net.minecraft.util.ResourceLocation;

/**
 * Visual theme for the ADM UI framework — atlas regions, text colors, icon grid.
 */
public interface UiTheme {

    String id();

    NineSliceRegion mainPanel();

    /** Inset cards, list bodies, and secondary groups. */
    default NineSliceRegion sectionPanel() {
        return mainPanel();
    }

    NineSliceRegion buttonNormal();

    NineSliceRegion buttonHover();

    /** Mouse-down state. Themes without a dedicated region may reuse hover. */
    default NineSliceRegion buttonPressed() {
        return buttonHover();
    }

    NineSliceRegion buttonDisabled();

    NineSliceRegion textFieldNormal();

    NineSliceRegion textFieldFocused();

    /** Exact-size or 9-slice inventory slot chrome; null keeps the procedural fallback. */
    default NineSliceRegion slot() {
        return null;
    }

    default NineSliceRegion scrollTrack() {
        return textFieldNormal();
    }

    default NineSliceRegion scrollThumb() {
        return buttonHover();
    }

    default NineSliceRegion divider() {
        return null;
    }

    default NineSliceRegion toggleOff() {
        return buttonNormal();
    }

    default NineSliceRegion toggleOn() {
        return buttonHover();
    }

    default NineSliceRegion toggleDisabled() {
        return buttonDisabled();
    }

    default NineSliceRegion checkOff() {
        return buttonNormal();
    }

    default NineSliceRegion checkOn() {
        return buttonHover();
    }

    default NineSliceRegion checkDisabled() {
        return buttonDisabled();
    }

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
