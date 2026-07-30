package com.imgood.textech.gui.framework;

import net.minecraft.util.ResourceLocation;

import com.imgood.textech.gui.custom.AdmGuiTextures;

/**
 * Default ADM sci-fi theme backed by {@code textures/gui/adm_ui_atlas.png}.
 */
public final class AdmUiTheme implements UiTheme {

    public static final ResourceLocation ATLAS = AdmGuiTextures.UI_ATLAS;

    public static final int ATLAS_SIZE = 256;
    public static final int PANEL_BORDER = 10;
    public static final int SECTION_BORDER = 8;
    public static final int BUTTON_BORDER = 8;
    public static final int ICON_SIZE = 16;

    private static final AdmUiTheme INSTANCE = new AdmUiTheme();

    private final NineSliceRegion mainPanel;
    private final NineSliceRegion sectionPanel;
    private final NineSliceRegion buttonNormal;
    private final NineSliceRegion buttonHover;
    private final NineSliceRegion buttonPressed;
    private final NineSliceRegion buttonDisabled;
    private final NineSliceRegion textFieldNormal;
    private final NineSliceRegion textFieldFocused;
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

    private AdmUiTheme() {
        mainPanel = new NineSliceRegion(ATLAS, ATLAS_SIZE, 0, 0, 96, 108, PANEL_BORDER);
        sectionPanel = new NineSliceRegion(ATLAS, ATLAS_SIZE, 100, 0, 88, 56, SECTION_BORDER);
        buttonNormal = new NineSliceRegion(ATLAS, ATLAS_SIZE, 100, 60, 48, 20, BUTTON_BORDER);
        buttonHover = new NineSliceRegion(ATLAS, ATLAS_SIZE, 100, 82, 48, 20, BUTTON_BORDER);
        buttonPressed = new NineSliceRegion(ATLAS, ATLAS_SIZE, 100, 104, 48, 20, BUTTON_BORDER);
        buttonDisabled = new NineSliceRegion(ATLAS, ATLAS_SIZE, 100, 126, 48, 20, BUTTON_BORDER);
        textFieldNormal = new NineSliceRegion(ATLAS, ATLAS_SIZE, 152, 60, 48, 20, 6);
        textFieldFocused = new NineSliceRegion(ATLAS, ATLAS_SIZE, 204, 60, 48, 20, 6);
        slot = new NineSliceRegion(ATLAS, ATLAS_SIZE, 152, 84, 18, 18, 3);
        scrollTrack = new NineSliceRegion(ATLAS, ATLAS_SIZE, 174, 84, 10, 42, 3);
        scrollThumb = new NineSliceRegion(ATLAS, ATLAS_SIZE, 188, 84, 10, 20, 3);
        divider = new NineSliceRegion(ATLAS, ATLAS_SIZE, 204, 84, 48, 4, 1);
        toggleOff = new NineSliceRegion(ATLAS, ATLAS_SIZE, 152, 108, 28, 14, 4);
        toggleOn = new NineSliceRegion(ATLAS, ATLAS_SIZE, 184, 108, 28, 14, 4);
        toggleDisabled = new NineSliceRegion(ATLAS, ATLAS_SIZE, 216, 108, 28, 14, 4);
        checkOff = new NineSliceRegion(ATLAS, ATLAS_SIZE, 152, 126, 14, 14, 3);
        checkOn = new NineSliceRegion(ATLAS, ATLAS_SIZE, 170, 126, 14, 14, 3);
        checkDisabled = new NineSliceRegion(ATLAS, ATLAS_SIZE, 188, 126, 14, 14, 3);
    }

    public static AdmUiTheme instance() {
        return INSTANCE;
    }

    @Override
    public String id() {
        return "adm";
    }

    @Override
    public NineSliceRegion mainPanel() {
        return mainPanel;
    }

    @Override
    public NineSliceRegion sectionPanel() {
        return sectionPanel;
    }

    @Override
    public NineSliceRegion buttonNormal() {
        return buttonNormal;
    }

    @Override
    public NineSliceRegion buttonHover() {
        return buttonHover;
    }

    @Override
    public NineSliceRegion buttonPressed() {
        return buttonPressed;
    }

    @Override
    public NineSliceRegion buttonDisabled() {
        return buttonDisabled;
    }

    @Override
    public NineSliceRegion textFieldNormal() {
        return textFieldNormal;
    }

    @Override
    public NineSliceRegion textFieldFocused() {
        return textFieldFocused;
    }

    @Override
    public NineSliceRegion slot() {
        return slot;
    }

    @Override
    public NineSliceRegion scrollTrack() {
        return scrollTrack;
    }

    @Override
    public NineSliceRegion scrollThumb() {
        return scrollThumb;
    }

    @Override
    public NineSliceRegion divider() {
        return divider;
    }

    @Override
    public NineSliceRegion toggleOff() {
        return toggleOff;
    }

    @Override
    public NineSliceRegion toggleOn() {
        return toggleOn;
    }

    @Override
    public NineSliceRegion toggleDisabled() {
        return toggleDisabled;
    }

    @Override
    public NineSliceRegion checkOff() {
        return checkOff;
    }

    @Override
    public NineSliceRegion checkOn() {
        return checkOn;
    }

    @Override
    public NineSliceRegion checkDisabled() {
        return checkDisabled;
    }

    @Override
    public int textPrimary() {
        return 0xD7F7FF;
    }

    @Override
    public int textAccent() {
        return 0x00FFFF;
    }

    @Override
    public int textDisabled() {
        return 0xA0A0A0;
    }

    @Override
    public ResourceLocation iconAtlas() {
        return ATLAS;
    }

    @Override
    public int iconAtlasSize() {
        return ATLAS_SIZE;
    }

    @Override
    public int iconSize() {
        return ICON_SIZE;
    }

    @Override
    public int iconGridU() {
        return 0;
    }

    @Override
    public int iconGridV() {
        return 160;
    }
}
