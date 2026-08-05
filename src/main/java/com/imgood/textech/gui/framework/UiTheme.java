package com.imgood.textech.gui.framework;

import net.minecraft.util.ResourceLocation;

/**
 * Visual theme for the ADM UI framework — atlas regions, text colors, icon grid.
 */
public interface UiTheme {

    String id();

    NineSliceRegion mainPanel();

    /** Sparse ADM outer frame. Themes that do not provide it keep the legacy panel contract. */
    default SparseFrameRegion sparseMainFrame() {
        return null;
    }

    /** Non-stretching frame used by the ADM rework. */
    default TiledFrameRegion mainFrame() {
        return null;
    }

    /** Inset cards, list bodies, and secondary groups. */
    default NineSliceRegion sectionPanel() {
        return mainPanel();
    }

    default SparseFrameRegion sparseSectionFrame() {
        return sparseMainFrame();
    }

    default AtlasRegion titleOrnament() {
        return null;
    }

    default AtlasRegion footerOrnament() {
        return null;
    }

    default TiledFrameRegion sectionFrame() {
        return mainFrame();
    }

    NineSliceRegion buttonNormal();

    /** Complete button shells for fixed aspect-ratio families. */
    default FixedAspectButtonFamily fixedAspectButtons() {
        return null;
    }

    default TiledBarRegion buttonNormalBar() {
        return null;
    }

    NineSliceRegion buttonHover();

    default TiledBarRegion buttonHoverBar() {
        return buttonNormalBar();
    }

    /** Mouse-down state. Themes without a dedicated region may reuse hover. */
    default NineSliceRegion buttonPressed() {
        return buttonHover();
    }

    default TiledBarRegion buttonPressedBar() {
        return buttonHoverBar();
    }

    NineSliceRegion buttonDisabled();

    default TiledBarRegion buttonDisabledBar() {
        return buttonNormalBar();
    }

    NineSliceRegion textFieldNormal();

    default TiledBarRegion textFieldNormalBar() {
        return null;
    }

    NineSliceRegion textFieldFocused();

    default NineSliceRegion textFieldInvalid() {
        return textFieldFocused();
    }

    default NineSliceRegion textFieldDisabled() {
        return textFieldNormal();
    }

    /** Four-state underline field chrome. */
    default UnderlineFieldRegion underlineField() {
        return null;
    }

    default TiledBarRegion textFieldFocusedBar() {
        return textFieldNormalBar();
    }

    /** Exact-size or 9-slice inventory slot chrome; null keeps the procedural fallback. */
    default NineSliceRegion slot() {
        return null;
    }

    default AtlasRegion slotRegion() {
        return null;
    }

    default NineSliceRegion scrollTrack() {
        return textFieldNormal();
    }

    default NineSliceRegion scrollThumb() {
        return buttonHover();
    }

    default AtlasRegion scrollTrackRegion() {
        return null;
    }

    default AtlasRegion scrollThumbRegion() {
        return null;
    }

    default NineSliceRegion divider() {
        return null;
    }

    default AtlasRegion dividerRegion() {
        return footerOrnament();
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

    default AtlasRegion toggleOffRegion() {
        return null;
    }

    default AtlasRegion toggleOnRegion() {
        return null;
    }

    default AtlasRegion toggleDisabledRegion() {
        return null;
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

    default AtlasRegion checkOffRegion() {
        return null;
    }

    default AtlasRegion checkOnRegion() {
        return null;
    }

    default AtlasRegion checkDisabledRegion() {
        return null;
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

    default int iconHoverGridV() {
        return iconGridV();
    }
}
