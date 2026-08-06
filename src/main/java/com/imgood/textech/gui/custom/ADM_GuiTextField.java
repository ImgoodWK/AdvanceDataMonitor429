package com.imgood.textech.gui.custom;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.ResourceLocation;

import com.imgood.textech.gui.framework.GuiBlitUtil;
import com.imgood.textech.gui.framework.TiledBarRegion;
import com.imgood.textech.gui.framework.UiThemes;
import com.imgood.textech.gui.framework.UnderlineFieldRegion;

public class ADM_GuiTextField extends GuiTextField {

    private static final int TEXT_OFFSET_X = 21;
    private static final int TEXT_OFFSET_Y = 8;

    private ResourceLocation backgroundTexture;
    private ResourceLocation focusedBackgroundTexture;
    private String hintText = "";
    private int hintColor = 0x7F7F7F;
    private FontRenderer fontRendererObj = Minecraft.getMinecraft().fontRenderer;
    public int xPosition;
    public int yPosition;
    public int width;
    public int height;
    private String text = "";
    private int cursorCounter;
    private boolean isFocused;
    private boolean visualEnabled = true;
    private boolean invalid;
    private Runnable onTextChanged;
    private int lineScrollOffset;
    private int cursorPosition;
    private int selectionEnd;
    private int enabledColor = 14737632;
    private int disabledColor = 7368816;

    public ADM_GuiTextField(FontRenderer fontRendererObj, int x, int y, int width, int height) {
        super(fontRendererObj, x, y, width, height);
        this.setEnableBackgroundDrawing(false);
        this.yPosition = y - TEXT_OFFSET_Y;
        this.xPosition = x - TEXT_OFFSET_X;
        this.width = width;
        this.height = height;
        this.backgroundTexture = AdmGuiTextures.TEXTFIELD_8020;
        this.focusedBackgroundTexture = AdmGuiTextures.TEXTFIELD_HOVER_8020;
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int button) {
        if (!this.getVisible()) {
            return;
        }
        boolean visualHit = mouseX >= this.xPosition && mouseX < this.xPosition + this.width + 2
            && mouseY >= this.yPosition
            && mouseY < this.yPosition + this.height + 2;
        if (button == 0) {
            if (visualHit) {
                super.mouseClicked(mouseX - TEXT_OFFSET_X, mouseY - TEXT_OFFSET_Y, button);
            } else {
                this.setFocused(false);
            }
        }
    }

    private int hintDrawX() {
        return this.xPosition + TEXT_OFFSET_X + 4;
    }

    private int hintDrawY() {
        return this.yPosition + TEXT_OFFSET_Y + (this.height - 8) / 2;
    }

    @Override
    public void drawTextBox() {
        if (!this.getVisible()) {
            return;
        }
        ResourceLocation textureToDraw = this.isFocused() && this.focusedBackgroundTexture != null
            ? this.focusedBackgroundTexture
            : this.backgroundTexture;
        if (textureToDraw != null) {
            if (isLegacyTextFieldTexture(textureToDraw)) {
                UnderlineFieldRegion underline = UiThemes.ADM.underlineField();
                UnderlineFieldRegion.State state = UnderlineFieldRegion
                    .stateFor(visualEnabled, invalid, this.isFocused());
                TiledBarRegion bar = this.isFocused() ? UiThemes.ADM.textFieldFocusedBar()
                    : UiThemes.ADM.textFieldNormalBar();
                if (underline != null) {
                    GuiBlitUtil.drawUnderlineField(
                        underline,
                        state,
                        this.xPosition - 1,
                        this.yPosition - 1,
                        this.width + 2,
                        this.height + 2);
                } else if (bar != null) {
                    GuiBlitUtil
                        .drawTiledBar(bar, this.xPosition - 1, this.yPosition - 1, this.width + 2, this.height + 2);
                } else {
                    GuiBlitUtil.drawHorizontalSlice(
                        this.isFocused() ? UiThemes.ADM.textFieldFocused() : UiThemes.ADM.textFieldNormal(),
                        this.xPosition - 1,
                        this.yPosition - 1,
                        this.width + 2,
                        this.height + 2);
                }
            } else {
                drawTexturedRect(
                    this.xPosition - 1,
                    this.yPosition - 1,
                    this.width + 2,
                    this.height + 2,
                    textureToDraw);
            }
        }

        if (this.getText()
            .isEmpty() && !this.isFocused()
            && !hintText.isEmpty()) {
            this.fontRendererObj
                .drawStringWithShadow(this.hintText, this.hintDrawX(), this.hintDrawY(), this.hintColor);
        } else {
            super.drawTextBox();
        }
    }

    private void drawTexturedRect(int x, int y, int width, int height, ResourceLocation texture) {
        GuiBlitUtil.drawFullTexture(texture, x, y, width, height);
    }

    public ADM_GuiTextField setBackgroundTexture(ResourceLocation texture) {
        this.backgroundTexture = texture;
        return this;
    }

    public ADM_GuiTextField setFocusedBackgroundTexture(ResourceLocation texture) {
        this.focusedBackgroundTexture = texture;
        return this;
    }

    public ADM_GuiTextField setHintText(String hintText) {
        this.hintText = hintText;
        return this;
    }

    public ADM_GuiTextField setHintColor(int color) {
        this.hintColor = color;
        return this;
    }

    @Override
    public void setText(String value) {
        String before = super.getText();
        super.setText(value != null ? value : "");
        notifyTextChanged(before);
    }

    @Override
    public boolean textboxKeyTyped(char typedChar, int keyCode) {
        String before = super.getText();
        boolean handled = super.textboxKeyTyped(typedChar, keyCode);
        notifyTextChanged(before);
        return handled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.visualEnabled = enabled;
        super.setEnabled(enabled);
        if (!enabled) {
            setFocused(false);
        }
    }

    public ADM_GuiTextField setInvalid(boolean invalid) {
        this.invalid = invalid;
        return this;
    }

    public boolean isInvalid() {
        return invalid;
    }

    public boolean isVisualEnabled() {
        return visualEnabled;
    }

    public ADM_GuiTextField setOnTextChanged(Runnable listener) {
        this.onTextChanged = listener;
        return this;
    }

    private void notifyTextChanged(String before) {
        if (!super.getText().equals(before)) {
            invalid = false;
            if (onTextChanged != null) {
                onTextChanged.run();
            }
        }
    }

    public ResourceLocation getTextFieldTexture() {
        return this.backgroundTexture;
    }

    public ResourceLocation getFocusedTextFieldTexture() {
        return this.focusedBackgroundTexture;
    }

    private static boolean isLegacyTextFieldTexture(ResourceLocation texture) {
        return AdmGuiTextures.TEXTFIELD_8020.equals(texture) || AdmGuiTextures.TEXTFIELD_HOVER_8020.equals(texture)
            || AdmGuiTextures.TEXTFIELD_SELECTED.equals(texture)
            || AdmGuiTextures.TEXTFIELD_SELECTED_ALT.equals(texture);
    }
}
