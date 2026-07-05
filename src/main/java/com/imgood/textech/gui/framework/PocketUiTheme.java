package com.imgood.textech.gui.framework;

import net.minecraft.util.ResourceLocation;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.client.PocketPortalGuiRenderer;

/**
 * Pocket portal theme stub — maps {@link PocketPortalGuiRenderer} atlas constants.
 * Not wired into pocket GUIs in this iteration; reserved for future migration.
 */
public final class PocketUiTheme implements UiTheme {

    private static final PocketUiTheme INSTANCE = new PocketUiTheme();

    private final NineSliceRegion mainPanel;

    private PocketUiTheme() {
        mainPanel = new NineSliceRegion(
            PocketPortalGuiRenderer.PANEL_TEXTURE,
            PocketPortalGuiRenderer.TEX_SIZE,
            0,
            0,
            PocketPortalGuiRenderer.TEX_SIZE,
            PocketPortalGuiRenderer.TEX_SIZE,
            PocketPortalGuiRenderer.BORDER);
    }

    public static PocketUiTheme instance() {
        return INSTANCE;
    }

    @Override
    public String id() {
        return "pocket";
    }

    @Override
    public NineSliceRegion mainPanel() {
        return mainPanel;
    }

    @Override
    public NineSliceRegion buttonNormal() {
        return mainPanel;
    }

    @Override
    public NineSliceRegion buttonHover() {
        return mainPanel;
    }

    @Override
    public NineSliceRegion buttonDisabled() {
        return mainPanel;
    }

    @Override
    public NineSliceRegion textFieldNormal() {
        return mainPanel;
    }

    @Override
    public NineSliceRegion textFieldFocused() {
        return mainPanel;
    }

    @Override
    public int textPrimary() {
        return 0xFFFFFF;
    }

    @Override
    public int textAccent() {
        return 0x88AAFF;
    }

    @Override
    public int textDisabled() {
        return 0x888888;
    }

    @Override
    public ResourceLocation iconAtlas() {
        return new ResourceLocation(AdvanceDataMonitor.MODID, "textures/gui/pocket_portal_panel.png");
    }

    @Override
    public int iconAtlasSize() {
        return PocketPortalGuiRenderer.TEX_SIZE;
    }

    @Override
    public int iconSize() {
        return 16;
    }

    @Override
    public int iconGridU() {
        return 0;
    }

    @Override
    public int iconGridV() {
        return 0;
    }
}
