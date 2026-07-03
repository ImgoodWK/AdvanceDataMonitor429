package com.imgood.textech.gui.custom;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.resources.I18n;

import org.lwjgl.input.Keyboard;

/**
 * Thin base for small ADM configuration dialogs (item NBT, tile settings, HUD config).
 * Provides centered panel layout, shared textures, and save/cancel button factories.
 */
public abstract class AdmItemConfigScreen extends ADM_GuiScreen {

    public static final int BUTTON_SAVE = 0;
    public static final int BUTTON_CANCEL = 1;

    protected String errorTips = "";
    protected final int panelWidth;
    protected final int panelHeight;

    protected AdmItemConfigScreen(int panelWidth, int panelHeight) {
        this.panelWidth = panelWidth;
        this.panelHeight = panelHeight;
        setBackgroundTexture(AdmGuiTextures.BACKGROUND_SUB);
        setSize(panelWidth, panelHeight);
        setStretch(false);
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        setPosition((width - panelWidth) / 2, (height - panelHeight) / 2);
        buttonList.clear();
        initConfigContent();
    }

    /** Create text fields and extra controls. Called after panel is centered. */
    protected abstract void initConfigContent();

    protected int centerX() {
        return width / 2;
    }

    protected int centerY() {
        return height / 2;
    }

    protected ADM_GuiTextField createTextField(int x, int y, int w, int h) {
        ADM_GuiTextField field = new ADM_GuiTextField(fontRendererObj, x, y, w, h);
        field.setBackgroundTexture(AdmGuiTextures.TEXTFIELD_8020);
        field.setFocusedBackgroundTexture(AdmGuiTextures.TEXTFIELD_HOVER_8020);
        return field;
    }

    protected ADM_GuiButton createToggleButton(int id, int x, int y, int w, String label) {
        return new ADM_GuiButton(id, x, y, w, 20, label).setTexture(AdmGuiTextures.BUTTON)
            .setHoverTexture(AdmGuiTextures.BUTTON_HOVER)
            .setUseHoverEffect(true)
            .setTextColor(0x00FFFF)
            .setTextHoverColor(0x55FFFF);
    }

    protected ADM_GuiButton createSaveButton(int x, int y) {
        return actionButton(BUTTON_SAVE, x, y, I18n.format("adm.button.save"), 0x00FF00, 0x55FF55);
    }

    protected ADM_GuiButton createCancelButton(int x, int y) {
        return actionButton(BUTTON_CANCEL, x, y, I18n.format("adm.button.cancel"), 0xFF5555, 0xFF0000);
    }

    protected ADM_GuiButton actionButton(int id, int x, int y, String label, int color, int hoverColor) {
        return new ADM_GuiButton(id, x, y, 50, 20, label).setTexture(AdmGuiTextures.BUTTON)
            .setHoverTexture(AdmGuiTextures.BUTTON_HOVER)
            .setUseHoverEffect(true)
            .setTextColor(color)
            .setTextHoverColor(hoverColor);
    }

    protected void drawErrorTips(int y) {
        if (!errorTips.isEmpty()) {
            drawCenteredString(fontRendererObj, errorTips, centerX(), y, 0xFF5555);
        }
    }

    protected void closeScreen() {
        mc.displayGuiScreen(null);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == BUTTON_SAVE) {
            onSave();
        } else if (button.id == BUTTON_CANCEL) {
            closeScreen();
        } else {
            onConfigButton(button);
        }
    }

    protected abstract void onSave();

    protected void onConfigButton(GuiButton button) {}

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        super.onGuiClosed();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
