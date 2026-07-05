package com.imgood.textech.gui.custom;

import net.minecraft.util.ResourceLocation;

import com.imgood.textech.AdvanceDataMonitor;

/**
 * Shared ADM GUI texture {@link ResourceLocation} constants.
 * Use instead of duplicating paths across {@code guiscreen} classes.
 */
public final class AdmGuiTextures {

    private AdmGuiTextures() {}

    public static final ResourceLocation BACKGROUND_SUB = rl("textures/gui/background_ADM_Sub.png");
    public static final ResourceLocation BACKGROUND_MONITOR_MAIN = rl(
        "textures/gui/background_AdvanceDataMonitor_Main.png");
    public static final ResourceLocation BUTTON = rl("textures/gui/button_ADM.png");
    public static final ResourceLocation BUTTON_HOVER = rl("textures/gui/button_hover_ADM.png");
    public static final ResourceLocation BUTTON_2020 = rl("textures/gui/button_ADM_2020.png");
    public static final ResourceLocation BUTTON_HOVER_2020 = rl("textures/gui/button_hover_ADM_2020.png");
    public static final ResourceLocation TEXTFIELD_8020 = rl("textures/gui/textfield_ADM_8020.png");
    public static final ResourceLocation TEXTFIELD_HOVER_8020 = rl("textures/gui/textfield_hover_ADM_8020.png");
    public static final ResourceLocation TEXTFIELD_SELECTED = rl("textures/gui/textfield_selected_ADM.png");
    public static final ResourceLocation TEXTFIELD_SELECTED_ALT = rl("textures/gui/textfield_selected_ADM_1.png");
    public static final ResourceLocation SUB_GUI_TYPE_BOX = rl("textures/gui/ADMSubGuiTypeBox.png");
    public static final ResourceLocation ADVANCE_STORAGE_LINK = rl("textures/gui/advance_storage_link.png");
    public static final ResourceLocation UI_ATLAS = rl("textures/gui/adm_ui_atlas.png");

    private static ResourceLocation rl(String path) {
        return new ResourceLocation(AdvanceDataMonitor.MODID, path);
    }
}
