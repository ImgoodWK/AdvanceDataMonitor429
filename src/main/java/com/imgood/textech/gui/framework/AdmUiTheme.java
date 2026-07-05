package com.imgood.textech.gui.framework;

import net.minecraft.util.ResourceLocation;

import com.imgood.textech.gui.custom.AdmGuiTextures;

/**
 * Default ADM sci-fi theme backed by {@code textures/gui/adm_ui_atlas.png}.
 */
public final class AdmUiTheme implements UiTheme {

    public static final ResourceLocation ATLAS = AdmGuiTextures.UI_ATLAS;

    public static final int ATLAS_SIZE = 256;
    public static final int PANEL_BORDER = 16;
    public static final int BUTTON_BORDER = 8;
    public static final int ICON_SIZE = 16;

    private static final AdmUiTheme INSTANCE = new AdmUiTheme();

    private final NineSliceRegion mainPanel;
    private final NineSliceRegion buttonNormal;
    private final NineSliceRegion buttonHover;
    private final NineSliceRegion buttonDisabled;
    private final NineSliceRegion textFieldNormal;
    private final NineSliceRegion textFieldFocused;

    private AdmUiTheme() {
        mainPanel = new NineSliceRegion(ATLAS, ATLAS_SIZE, 0, 0, 64, 64, PANEL_BORDER);
        buttonNormal = new NineSliceRegion(ATLAS, ATLAS_SIZE, 0, 64, 48, 20, BUTTON_BORDER);
        buttonHover = new NineSliceRegion(ATLAS, ATLAS_SIZE, 0, 84, 48, 20, BUTTON_BORDER);
        buttonDisabled = new NineSliceRegion(ATLAS, ATLAS_SIZE, 0, 104, 48, 20, BUTTON_BORDER);
        textFieldNormal = new NineSliceRegion(ATLAS, ATLAS_SIZE, 64, 64, 80, 20, 6);
        textFieldFocused = new NineSliceRegion(ATLAS, ATLAS_SIZE, 64, 84, 80, 20, 6);
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
    public NineSliceRegion buttonNormal() {
        return buttonNormal;
    }

    @Override
    public NineSliceRegion buttonHover() {
        return buttonHover;
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
    public int textPrimary() {
        return 0x404040;
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
        return 64;
    }

    @Override
    public int iconGridV() {
        return 0;
    }
}
