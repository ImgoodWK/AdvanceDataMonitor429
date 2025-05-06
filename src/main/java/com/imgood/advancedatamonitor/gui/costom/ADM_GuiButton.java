package com.imgood.advancedatamonitor.gui.costom;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

public class ADM_GuiButton extends GuiButton {
    private static final ResourceLocation DEFAULT_TEXTURE = new ResourceLocation("textures/gui/widgets.png");

    private ResourceLocation texture;
    private ResourceLocation hoverTexture;
    private ResourceLocation leftDecoration;
    private ResourceLocation rightDecoration;

    private int textColor;
    private int textColorHover;
    private int disabledTextColor;
    private int decorationWidth = 16;

    private boolean useHoverEffect;
    private boolean useRGBEffect;

    private long startTime;

    public ADM_GuiButton(int id, int x, int y, int width, int height, String text) {
        super(id, x, y, width, height, text);
        this.textColor = 0xFFFFFF;
        this.textColorHover = 0xFFFFFF;
        disabledTextColor = 0xA0A0A0;
        this.useHoverEffect = false;
        this.useRGBEffect = false;
        this.startTime = System.currentTimeMillis();
        this.texture = DEFAULT_TEXTURE;
    }

    public ADM_GuiButton setTextHoverColor(int textColor) {
        this.textColorHover = textColor;
        return this;
    }

    public ADM_GuiButton setLeftDecoration(ResourceLocation leftDecoration) {
        this.leftDecoration = leftDecoration;
        return this;
    }

    public ADM_GuiButton setRightDecoration(ResourceLocation rightDecoration) {
        this.rightDecoration = rightDecoration;
        return this;
    }

    public ADM_GuiButton setDecorationWidth(int width) {
        this.decorationWidth = width;
        return this;
    }

    public ADM_GuiButton setTexture(ResourceLocation texture) {
        this.texture = texture != null ? texture : DEFAULT_TEXTURE;
        return this;
    }

    public ADM_GuiButton setHoverTexture(ResourceLocation hoverTexture) {
        this.hoverTexture = hoverTexture;
        this.useHoverEffect = (hoverTexture != null);
        return this;
    }

    public ADM_GuiButton setTextColor(int color) {
        this.textColor = color;
        return this;
    }

    public ADM_GuiButton setUseHoverEffect(boolean useHoverEffect) {
        this.useHoverEffect = useHoverEffect;
        return this;
    }

    public ADM_GuiButton setUseRGBEffect(boolean useRGBEffect) {
        this.useRGBEffect = useRGBEffect;
        return this;
    }

    public ADM_GuiButton setDisabledTextColor(int disabledTextColor) {
        this.disabledTextColor = disabledTextColor;
        return this;
    }

    public boolean getUseRGBEffect() {
        return this.useRGBEffect;
    }
    private int getRGBColor() {
        long elapsed = System.currentTimeMillis() - startTime;
        float hue = (elapsed % 3000) / 3000f;
        return java.awt.Color.HSBtoRGB(hue, 1.0f, 1.0f) & 0xFFFFFF;
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY) {
        if (this.visible) {

            boolean isHovered = mouseX >= this.xPosition && mouseY >= this.yPosition && mouseX < this.xPosition + this.width && mouseY < this.yPosition + this.height;


            ResourceLocation currentTexture = isHovered && this.useHoverEffect && this.hoverTexture != null ? this.hoverTexture : this.texture;
            mc.getTextureManager().bindTexture(currentTexture);

            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

            GL11.glDisable(GL11.GL_DEPTH_TEST);

            // 根据按钮尺寸缩放纹理坐标
            float u = 0.0F, v = 0.0F, u1 = 1.0F, v1 = 1.0F;

            // 绘制拉伸后的材质
            this.zLevel = 0.0F;
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glTexCoord2f(u, v); GL11.glVertex3f(this.xPosition, this.yPosition, this.zLevel);
            GL11.glTexCoord2f(u, v1); GL11.glVertex3f(this.xPosition, this.yPosition + this.height, this.zLevel);
            GL11.glTexCoord2f(u1, v1); GL11.glVertex3f(this.xPosition + this.width, this.yPosition + this.height, this.zLevel);
            GL11.glTexCoord2f(u1, v); GL11.glVertex3f(this.xPosition + this.width, this.yPosition, this.zLevel);
            GL11.glEnd();

            GL11.glEnable(GL11.GL_DEPTH_TEST);

            // 绘制左侧装饰 失效
            if (this.leftDecoration != null) {
                mc.getTextureManager().bindTexture(this.leftDecoration);
                this.drawTexturedModalRect(this.xPosition, this.yPosition, 0, 10, decorationWidth, this.height);
            }

            // 绘制右侧装饰 失效
            if (this.rightDecoration != null) {
                mc.getTextureManager().bindTexture(this.rightDecoration);
                this.drawTexturedModalRect(this.xPosition + this.width - decorationWidth, this.yPosition, 0, 10, decorationWidth, this.height);
            }

            // 绘制按钮文本
            int textColor = this.textColor;
            if (!this.enabled) {
                this.setUseHoverEffect(false);
                // 不可用状态
                textColor = disabledTextColor;
            } else if (isHovered) {
                // 鼠标悬停状态
                textColor = textColorHover;
            } else if (this.useRGBEffect) {
                // RGB 效果
                textColor = getRGBColor();
            }

            this.drawCenteredString(mc.fontRenderer, this.displayString,
                this.xPosition + this.width / 2,
                this.yPosition + (this.height - 8) / 2,
                textColor);

            GL11.glDisable(GL11.GL_BLEND);
        }
    }
    }
