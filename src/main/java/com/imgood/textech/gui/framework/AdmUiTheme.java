package com.imgood.textech.gui.framework;

import net.minecraft.util.ResourceLocation;

import com.imgood.textech.gui.custom.AdmGuiTextures;

/** Default ADM sci-fi theme backed by the approved sparse 512x512 runtime atlas. */
public final class AdmUiTheme implements UiTheme {

    public static final ResourceLocation ATLAS = AdmGuiTextures.UI_ATLAS;
    public static final int ATLAS_SIZE = 512;
    public static final int PANEL_BORDER = 22;
    public static final int SECTION_BORDER = 14;
    public static final int BUTTON_BORDER = 10;
    public static final int ICON_SIZE = 16;

    private static final AdmUiTheme INSTANCE = new AdmUiTheme();

    private final SparseFrameRegion sparseMainFrame;
    private final SparseFrameRegion sparseSectionFrame;
    private final AtlasRegion titleOrnament;
    private final AtlasRegion footerOrnament;
    private final FixedAspectButtonFamily fixedAspectButtons;
    private final UnderlineFieldRegion underlineField;
    private final NineSliceRegion mainPanel;
    private final NineSliceRegion sectionPanel;
    private final NineSliceRegion buttonNormal;
    private final NineSliceRegion buttonHover;
    private final NineSliceRegion buttonPressed;
    private final NineSliceRegion buttonDisabled;
    private final NineSliceRegion textFieldNormal;
    private final NineSliceRegion textFieldFocused;
    private final NineSliceRegion textFieldInvalid;
    private final NineSliceRegion textFieldDisabled;
    private final NineSliceRegion slot;
    private final NineSliceRegion scrollTrack;
    private final NineSliceRegion scrollThumb;
    private final NineSliceRegion divider;
    private final NineSliceRegion toggleOff;
    private final NineSliceRegion toggleOn;
    private final NineSliceRegion toggleDisabled;
    private final NineSliceRegion checkOff;
    private final NineSliceRegion checkOn;
    private final NineSliceRegion checkDisabled;
    private final AtlasRegion slotRegion;
    private final AtlasRegion scrollTrackRegion;
    private final AtlasRegion scrollThumbRegion;
    private final AtlasRegion toggleOffRegion;
    private final AtlasRegion toggleOnRegion;
    private final AtlasRegion toggleDisabledRegion;
    private final AtlasRegion checkOffRegion;
    private final AtlasRegion checkOnRegion;
    private final AtlasRegion checkDisabledRegion;

    private AdmUiTheme() {
        sparseMainFrame = new SparseFrameRegion(
            region(0, 0, 22, 22), region(24, 0, 22, 22), region(0, 24, 22, 22), region(24, 24, 22, 22),
            region(264, 0, 64, 64));
        sparseSectionFrame = new SparseFrameRegion(
            region(0, 66, 14, 14), region(16, 66, 14, 14), region(0, 82, 14, 14), region(16, 82, 14, 14),
            region(156, 66, 64, 64));
        titleOrnament = region(222, 66, 160, 12);
        footerOrnament = region(222, 80, 160, 8);
        fixedAspectButtons = new FixedAspectButtonFamily(
            buttonRegions(140), buttonRegions(184), buttonRegions(228), buttonRegions(272));
        underlineField = new UnderlineFieldRegion(
            underlineStyle(320, 320), underlineStyle(325, 342), underlineStyle(330, 364),
            underlineStyle(335, 386));

        // Compatibility descriptors are retained for themes and later-batch pages still using the old interface.
        mainPanel = new NineSliceRegion(ATLAS, ATLAS_SIZE, 0, 0, 46, 46, PANEL_BORDER);
        sectionPanel = new NineSliceRegion(ATLAS, ATLAS_SIZE, 0, 66, 30, 30, SECTION_BORDER);
        buttonNormal = new NineSliceRegion(ATLAS, ATLAS_SIZE, 0, 162, 100, 20, BUTTON_BORDER);
        buttonHover = new NineSliceRegion(ATLAS, ATLAS_SIZE, 0, 206, 100, 20, BUTTON_BORDER);
        buttonPressed = new NineSliceRegion(ATLAS, ATLAS_SIZE, 0, 250, 100, 20, BUTTON_BORDER);
        buttonDisabled = new NineSliceRegion(ATLAS, ATLAS_SIZE, 0, 294, 100, 20, BUTTON_BORDER);
        textFieldNormal = new NineSliceRegion(ATLAS, ATLAS_SIZE, 482, 320, 8, 20, 3);
        textFieldFocused = new NineSliceRegion(ATLAS, ATLAS_SIZE, 482, 342, 8, 20, 3);
        textFieldInvalid = new NineSliceRegion(ATLAS, ATLAS_SIZE, 482, 364, 8, 20, 3);
        textFieldDisabled = new NineSliceRegion(ATLAS, ATLAS_SIZE, 482, 386, 8, 20, 3);

        slotRegion = region(300, 452, 18, 18);
        scrollTrackRegion = region(320, 452, 8, 42);
        scrollThumbRegion = region(330, 452, 8, 20);
        toggleOffRegion = region(342, 452, 14, 14);
        toggleOnRegion = region(358, 452, 14, 14);
        toggleDisabledRegion = region(374, 452, 14, 14);
        checkOffRegion = region(342, 468, 14, 14);
        checkOnRegion = region(358, 468, 14, 14);
        checkDisabledRegion = region(374, 468, 14, 14);
        slot = legacy(slotRegion, 3);
        scrollTrack = legacy(scrollTrackRegion, 2);
        scrollThumb = legacy(scrollThumbRegion, 2);
        divider = legacy(footerOrnament, 1);
        toggleOff = legacy(toggleOffRegion, 3);
        toggleOn = legacy(toggleOnRegion, 3);
        toggleDisabled = legacy(toggleDisabledRegion, 3);
        checkOff = legacy(checkOffRegion, 3);
        checkOn = legacy(checkOnRegion, 3);
        checkDisabled = legacy(checkDisabledRegion, 3);
    }

    private static AtlasRegion region(int u, int v, int width, int height) {
        return new AtlasRegion(ATLAS, ATLAS_SIZE, u, v, width, height);
    }

    private static NineSliceRegion legacy(AtlasRegion region, int border) {
        return new NineSliceRegion(region.texture(), region.atlasSize(), region.u(), region.v(), region.width(),
            region.height(), border);
    }

    private static AtlasRegion[] buttonRegions(int blockY) {
        return new AtlasRegion[] {
            region(306, blockY + 22, 20, 20), region(252, blockY + 22, 50, 20),
            region(188, blockY + 22, 60, 20), region(104, blockY + 22, 80, 20),
            region(0, blockY + 22, 100, 20), region(244, blockY, 200, 20), region(0, blockY, 240, 20)
        };
    }

    private static UnderlineFieldRegion.Style underlineStyle(int bottomV, int sidesV) {
        return UnderlineFieldRegion.style(
            region(482, sidesV, 3, 20), region(487, sidesV, 3, 20), region(0, bottomV, 480, 3));
    }

    public static AdmUiTheme instance() { return INSTANCE; }

    @Override public String id() { return "adm"; }
    @Override public NineSliceRegion mainPanel() { return mainPanel; }
    @Override public SparseFrameRegion sparseMainFrame() { return sparseMainFrame; }
    @Override public NineSliceRegion sectionPanel() { return sectionPanel; }
    @Override public SparseFrameRegion sparseSectionFrame() { return sparseSectionFrame; }
    @Override public AtlasRegion titleOrnament() { return titleOrnament; }
    @Override public AtlasRegion footerOrnament() { return footerOrnament; }
    @Override public FixedAspectButtonFamily fixedAspectButtons() { return fixedAspectButtons; }
    @Override public NineSliceRegion buttonNormal() { return buttonNormal; }
    @Override public NineSliceRegion buttonHover() { return buttonHover; }
    @Override public NineSliceRegion buttonPressed() { return buttonPressed; }
    @Override public NineSliceRegion buttonDisabled() { return buttonDisabled; }
    @Override public NineSliceRegion textFieldNormal() { return textFieldNormal; }
    @Override public NineSliceRegion textFieldFocused() { return textFieldFocused; }
    @Override public NineSliceRegion textFieldInvalid() { return textFieldInvalid; }
    @Override public NineSliceRegion textFieldDisabled() { return textFieldDisabled; }
    @Override public UnderlineFieldRegion underlineField() { return underlineField; }
    @Override public NineSliceRegion slot() { return slot; }
    @Override public AtlasRegion slotRegion() { return slotRegion; }
    @Override public NineSliceRegion scrollTrack() { return scrollTrack; }
    @Override public NineSliceRegion scrollThumb() { return scrollThumb; }
    @Override public AtlasRegion scrollTrackRegion() { return scrollTrackRegion; }
    @Override public AtlasRegion scrollThumbRegion() { return scrollThumbRegion; }
    @Override public NineSliceRegion divider() { return divider; }
    @Override public AtlasRegion dividerRegion() { return footerOrnament; }
    @Override public NineSliceRegion toggleOff() { return toggleOff; }
    @Override public NineSliceRegion toggleOn() { return toggleOn; }
    @Override public NineSliceRegion toggleDisabled() { return toggleDisabled; }
    @Override public AtlasRegion toggleOffRegion() { return toggleOffRegion; }
    @Override public AtlasRegion toggleOnRegion() { return toggleOnRegion; }
    @Override public AtlasRegion toggleDisabledRegion() { return toggleDisabledRegion; }
    @Override public NineSliceRegion checkOff() { return checkOff; }
    @Override public NineSliceRegion checkOn() { return checkOn; }
    @Override public NineSliceRegion checkDisabled() { return checkDisabled; }
    @Override public AtlasRegion checkOffRegion() { return checkOffRegion; }
    @Override public AtlasRegion checkOnRegion() { return checkOnRegion; }
    @Override public AtlasRegion checkDisabledRegion() { return checkDisabledRegion; }
    @Override public int textPrimary() { return 0xD7F7FF; }
    @Override public int textAccent() { return 0x00FFFF; }
    @Override public int textDisabled() { return 0xA0A0A0; }
    @Override public ResourceLocation iconAtlas() { return ATLAS; }
    @Override public int iconAtlasSize() { return ATLAS_SIZE; }
    @Override public int iconSize() { return ICON_SIZE; }
    @Override public int iconGridU() { return 0; }
    @Override public int iconGridV() { return 350; }
    @Override public int iconHoverGridV() { return 398; }
}
