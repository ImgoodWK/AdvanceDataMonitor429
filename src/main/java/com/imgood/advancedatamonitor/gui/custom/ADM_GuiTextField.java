package com.imgood.advancedatamonitor.gui.custom;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

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
    private boolean isEnabled = true;
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
        if (this.getVisible()) {
            ResourceLocation textureToDraw = this.isFocused() && this.focusedBackgroundTexture != null
                ? this.focusedBackgroundTexture
                : this.backgroundTexture;
            if (textureToDraw != null) {
                drawTexturedRect(
                    this.xPosition - 1,
                    this.yPosition - 1,
                    this.width + 2,
                    this.height + 2,
                    textureToDraw);
            } else {
                drawTexturedRect(
                    this.xPosition - 1,
                    this.yPosition - 1,
                    this.width + 2,
                    this.height + 2,
                    textureToDraw);
                // 使用原版逻辑绘制背景
                super.drawTextBox();
            }

            if (this.getText()
                .isEmpty() && !this.isFocused()
                && !hintText.isEmpty()) {
                this.fontRendererObj
                    .drawStringWithShadow(this.hintText, this.hintDrawX(), this.hintDrawY(), this.hintColor);
            } else {
                // 使用原版逻辑绘制文本
                super.drawTextBox();

            }
        }
    }

    private void drawTexturedRect(int x, int y, int width, int height, ResourceLocation texture) {
        // 重置颜色
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        Minecraft minecraft = Minecraft.getMinecraft();
        minecraft.getTextureManager()
            .bindTexture(texture);

        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();

        float zLevel = 10000000000F;

        tessellator.addVertexWithUV(x, y + height, zLevel, 0, 1);
        tessellator.addVertexWithUV(x + width, y + height, zLevel, 1, 1);
        tessellator.addVertexWithUV(x + width, y, zLevel, 1, 0);
        tessellator.addVertexWithUV(x, y, zLevel, 0, 0);

        tessellator.draw();
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

    public ResourceLocation getTextFieldTexture() {
        return this.backgroundTexture;
    }

    public ResourceLocation getFocusedTextFieldTexture() {
        return this.focusedBackgroundTexture;
    }
}
