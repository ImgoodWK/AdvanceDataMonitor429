package com.imgood.textech.gui.custom;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import com.imgood.textech.gui.framework.FixedAspectButtonFamily;
import com.imgood.textech.gui.framework.GuiBlitUtil;
import com.imgood.textech.gui.framework.NineSliceRegion;
import com.imgood.textech.gui.framework.TiledBarRegion;
import com.imgood.textech.gui.framework.UiThemes;

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
        int fittedWidth = FixedAspectButtonFamily.normalizedWidthFor(width, height);
        int fittedHeight = FixedAspectButtonFamily.normalizedHeightFor(width, height);
        this.xPosition = x + (width - fittedWidth) / 2;
        this.yPosition = y + (height - fittedHeight) / 2;
        this.width = fittedWidth;
        this.height = fittedHeight;
        this.textColor = 0xFFFFFF;
        this.textColorHover = 0xFFFFFF;
        disabledTextColor = 0xA0A0A0;
        this.useHoverEffect = true;
        this.useRGBEffect = false;
        this.startTime = System.currentTimeMillis();
        this.texture = AdmGuiTextures.BUTTON;
        this.hoverTexture = AdmGuiTextures.BUTTON_HOVER;
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
        if (!isLegacyButtonTexture(this.texture) && isLegacyButtonTexture(this.hoverTexture)) {
            this.hoverTexture = null;
            this.useHoverEffect = false;
        }
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

            boolean isHovered = mouseX >= this.xPosition && mouseY >= this.yPosition
                && mouseX < this.xPosition + this.width
                && mouseY < this.yPosition + this.height;

            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

            GL11.glDisable(GL11.GL_DEPTH_TEST);

            // 根据按钮尺寸缩放纹理坐标
            boolean pressed = this.enabled && isHovered && Mouse.isButtonDown(0);
            if (usesAdmTheme()) {
                FixedAspectButtonFamily family = UiThemes.ADM.fixedAspectButtons();
                FixedAspectButtonFamily.State state = !this.enabled ? FixedAspectButtonFamily.State.DISABLED
                    : pressed ? FixedAspectButtonFamily.State.PRESSED
                        : isHovered && this.useHoverEffect ? FixedAspectButtonFamily.State.HOVER
                            : FixedAspectButtonFamily.State.NORMAL;
                TiledBarRegion bar = !this.enabled ? UiThemes.ADM.buttonDisabledBar()
                    : pressed ? UiThemes.ADM.buttonPressedBar()
                        : isHovered && this.useHoverEffect ? UiThemes.ADM.buttonHoverBar()
                            : UiThemes.ADM.buttonNormalBar();
                NineSliceRegion region = !this.enabled ? UiThemes.ADM.buttonDisabled()
                    : pressed ? UiThemes.ADM.buttonPressed()
                        : isHovered && this.useHoverEffect ? UiThemes.ADM.buttonHover() : UiThemes.ADM.buttonNormal();
                if (family != null) {
                    GuiBlitUtil
                        .drawFixedAspectButton(family, state, this.xPosition, this.yPosition, this.width, this.height);
                } else if (bar != null) {
                    GuiBlitUtil.drawTiledBar(bar, this.xPosition, this.yPosition, this.width, this.height);
                } else {
                    GuiBlitUtil.drawHorizontalSlice(region, this.xPosition, this.yPosition, this.width, this.height);
                }
            } else {
                ResourceLocation currentTexture = isHovered && this.useHoverEffect && this.hoverTexture != null
                    ? this.hoverTexture
                    : this.texture;
                this.zLevel = 0.0F;
                GuiBlitUtil.drawFullTexture(currentTexture, this.xPosition, this.yPosition, this.width, this.height);
            }

            GL11.glEnable(GL11.GL_DEPTH_TEST);

            // 绘制左侧装饰 失效
            if (this.leftDecoration != null && !isLegacyButtonTexture(this.leftDecoration)) {
                mc.getTextureManager()
                    .bindTexture(this.leftDecoration);
                this.drawTexturedModalRect(this.xPosition, this.yPosition, 0, 10, decorationWidth, this.height);
            }

            // 绘制右侧装饰 失效
            if (this.rightDecoration != null && !isLegacyButtonTexture(this.rightDecoration)) {
                mc.getTextureManager()
                    .bindTexture(this.rightDecoration);
                this.drawTexturedModalRect(
                    this.xPosition + this.width - decorationWidth,
                    this.yPosition,
                    0,
                    10,
                    decorationWidth,
                    this.height);
            }

            // 绘制按钮文本
            int textColor = this.textColor;
            if (!this.enabled) {
                // 不可用状总
                textColor = disabledTextColor;
            } else if (isHovered) {
                // 鼠标悬停状总
                textColor = textColorHover;
            } else if (this.useRGBEffect) {
                // RGB 效果
                textColor = getRGBColor();
            }

            String fittedLabel = fitLabel(mc, this.displayString, Math.max(0, this.width - 8));
            this.drawCenteredString(
                mc.fontRenderer,
                fittedLabel,
                this.xPosition + this.width / 2,
                this.yPosition + (this.height - 8) / 2,
                textColor);

            GL11.glDisable(GL11.GL_BLEND);
        }
    }

    private static String fitLabel(Minecraft mc, String label, int availableWidth) {
        String value = label == null ? "" : label;
        if (mc.fontRenderer.getStringWidth(value) <= availableWidth) {
            return value;
        }
        String suffix = "...";
        int contentWidth = Math.max(0, availableWidth - mc.fontRenderer.getStringWidth(suffix));
        String trimmed = mc.fontRenderer.trimStringToWidth(value, contentWidth);
        return availableWidth >= mc.fontRenderer.getStringWidth(suffix) ? trimmed + suffix : "";
    }

    private boolean usesAdmTheme() {
        return isLegacyButtonTexture(texture);
    }

    private static boolean isLegacyButtonTexture(ResourceLocation resource) {
        return AdmGuiTextures.BUTTON.equals(resource) || AdmGuiTextures.BUTTON_HOVER.equals(resource)
            || AdmGuiTextures.BUTTON_2020.equals(resource)
            || AdmGuiTextures.BUTTON_HOVER_2020.equals(resource);
    }
}
