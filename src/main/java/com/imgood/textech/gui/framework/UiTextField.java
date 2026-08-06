package com.imgood.textech.gui.framework;

import net.minecraft.client.gui.FontRenderer;

import com.imgood.textech.gui.custom.ADM_GuiTextField;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Four-state underline text field wrapper around {@link ADM_GuiTextField}.
 * <p>
 * Implemented for the UI framework; not wired into production GUIs in the initial debug pass.
 */
@SideOnly(Side.CLIENT)
public final class UiTextField {

    private final ADM_GuiTextField field;
    private final int drawX;
    private final int drawY;
    private final int drawW;
    private final int drawH;
    private UiTheme theme;

    public UiTextField(FontRenderer font, int x, int y, int width, int height) {
        this.drawX = x;
        this.drawY = y;
        this.drawW = width;
        this.drawH = height;
        this.field = new ADM_GuiTextField(font, x, y, width, height);
    }

    public UiTextField setTheme(UiTheme theme) {
        this.theme = theme;
        if (theme != null) {
            field.setBackgroundTexture(null)
                .setFocusedBackgroundTexture(null);
        }
        return this;
    }

    public UiTextField setHintText(String hint) {
        field.setHintText(hint);
        return this;
    }

    public UiTextField setMaxStringLength(int len) {
        field.setMaxStringLength(len);
        return this;
    }

    public UiTextField setText(String text) {
        field.setText(text);
        return this;
    }

    public UiTextField setInvalid(boolean invalid) {
        field.setInvalid(invalid);
        return this;
    }

    public UiTextField setEnabled(boolean enabled) {
        field.setEnabled(enabled);
        return this;
    }

    public ADM_GuiTextField delegate() {
        return field;
    }

    /** Draw stateful underline chrome; call before {@link #drawTextBox()}. */
    public void drawBackground(boolean focused) {
        if (theme == null) {
            return;
        }
        UnderlineFieldRegion underline = theme.underlineField();
        UnderlineFieldRegion.State state = UnderlineFieldRegion
            .stateFor(field.isVisualEnabled(), field.isInvalid(), focused);
        if (underline != null && GuiBlitUtil.hasResource(
            underline.style(state)
                .left()
                .texture())) {
            GuiBlitUtil.drawUnderlineField(underline, state, drawX, drawY, drawW, drawH);
            return;
        }
        TiledBarRegion bar = focused ? theme.textFieldFocusedBar() : theme.textFieldNormalBar();
        NineSliceRegion region = field.isInvalid() ? theme.textFieldInvalid()
            : !field.isVisualEnabled() ? theme.textFieldDisabled()
                : focused ? theme.textFieldFocused() : theme.textFieldNormal();
        if (bar != null && GuiBlitUtil.hasResource(
            bar.center()
                .texture())) {
            GuiBlitUtil.drawTiledBar(bar, drawX, drawY, drawW, drawH);
        } else if (region != null && GuiBlitUtil.hasResource(region.texture())) {
            GuiBlitUtil.drawHorizontalSlice(region, drawX, drawY, drawW, drawH);
        }
    }

    /**
     * Draw hint + text/cursor. When a {@link UiTheme} is set, background must be drawn via
     * {@link #drawBackground(boolean)} first — delegate textures stay unset intentionally.
     */
    public void drawTextBox() {
        field.drawTextBox();
    }
}
