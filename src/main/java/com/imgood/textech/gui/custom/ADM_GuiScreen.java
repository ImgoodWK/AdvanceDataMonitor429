package com.imgood.textech.gui.custom;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import com.imgood.textech.gui.framework.GuiBlitUtil;
import com.imgood.textech.gui.framework.UiPanel;
import com.imgood.textech.gui.framework.UiThemes;
import com.imgood.textech.gui.framework.UiViewportTransform;
import com.imgood.textech.gui.framework.UnderlineFieldRegion;

public class ADM_GuiScreen extends GuiScreen {

    private ResourceLocation backgroundTexture;
    // 默认X起点
    private int x = 0;
    // 默认Y起点
    private int y = 0;
    // 默认宽度，0表示使用整个屏幕宽度
    public int bg_width = 0;
    // 默认高度，0表示使用整个屏幕高度
    public int bg_height = 0;
    private boolean viewportTransformEnabled = true;
    private int viewportMargin = 8;
    private UiViewportTransform viewportTransform;

    public ADM_GuiScreen() {}

    /**
     * Draws every ADM screen through one uniform transform. Subclasses implement
     * {@link #drawAdmScreen(int, int, float)} so text, controls, tooltips, and panel chrome remain in one coordinate
     * system at every GUI scale.
     */
    @Override
    public final void drawScreen(int mouseX, int mouseY, float partialTicks) {
        UiViewportTransform transform = refreshViewportTransform();
        int logicalMouseX = transform.toLogicalX(mouseX);
        int logicalMouseY = transform.toLogicalY(mouseY);
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(transform.originX(), transform.originY(), 0.0F);
            GL11.glScalef(transform.scale(), transform.scale(), 1.0F);
            this.drawBackground();
            drawAdmScreen(logicalMouseX, logicalMouseY, partialTicks);
        } finally {
            GL11.glPopMatrix();
        }
    }

    /** Draw logical screen content after the sparse ADM panel has been drawn. */
    protected void drawAdmScreen(int mouseX, int mouseY, float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    /** ADM panels deliberately leave the world visible outside their glass reading areas. */
    @Override
    public final void drawDefaultBackground() {}

    @Override
    protected final void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        UiViewportTransform transform = refreshViewportTransform();
        handleAdmMouseClicked(transform.toLogicalX(mouseX), transform.toLogicalY(mouseY), mouseButton);
    }

    protected void handleAdmMouseClicked(int mouseX, int mouseY, int mouseButton) {
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected final void mouseMovedOrUp(int mouseX, int mouseY, int which) {
        UiViewportTransform transform = refreshViewportTransform();
        handleAdmMouseMovedOrUp(transform.toLogicalX(mouseX), transform.toLogicalY(mouseY), which);
    }

    protected void handleAdmMouseMovedOrUp(int mouseX, int mouseY, int which) {
        super.mouseMovedOrUp(mouseX, mouseY, which);
    }

    @Override
    protected final void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        UiViewportTransform transform = refreshViewportTransform();
        handleAdmMouseClickMove(
            transform.toLogicalX(mouseX),
            transform.toLogicalY(mouseY),
            clickedMouseButton,
            timeSinceLastClick);
    }

    protected void handleAdmMouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
    }

    public void drawImage(ResourceLocation texture, int x, int y, int maxWidth, int maxHeight) {
        if (texture == null) return;

        if (AdmGuiTextures.SUB_GUI_TYPE_BOX.equals(texture)) {
            UiPanel.drawSection(UiThemes.ADM, x, y, maxWidth, maxHeight);
            return;
        }
        if (isLegacyPanelTexture(texture)) {
            UiPanel.draw(UiThemes.ADM, x, y, maxWidth, maxHeight);
            return;
        }
        if (isLegacyTextFieldTexture(texture)) {
            UnderlineFieldRegion underline = UiThemes.ADM.underlineField();
            if (underline != null) {
                GuiBlitUtil.drawUnderlineField(
                    underline,
                    isLegacyFocusedTextFieldTexture(texture) ? UnderlineFieldRegion.State.FOCUSED
                        : UnderlineFieldRegion.State.NORMAL,
                    x,
                    y,
                    maxWidth,
                    maxHeight);
            }
            return;
        }

        // 获取纹理的尺寸
        int[] dimensions = getTextureDimensions(texture);
        int originalWidth = dimensions[0];
        int originalHeight = dimensions[1];

        // 如果无法获取尺寸，则使用最大尺寸
        if (originalWidth == 0 || originalHeight == 0) {
            originalWidth = maxWidth;
            originalHeight = maxHeight;
        }

        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        this.mc.getTextureManager()
            .bindTexture(texture);

        // 计算缩放比例
        float scaleX = (float) maxWidth / originalWidth;
        float scaleY = (float) maxHeight / originalHeight;
        float scale = Math.min(scaleX, scaleY);

        // 计算实际绘制的宽度和高度
        int width = Math.round(originalWidth * scale);
        int height = Math.round(originalHeight * scale);

        // 计算居中位置
        int drawX = x + (maxWidth - width) / 2;
        int drawY = y + (maxHeight - height) / 2;

        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.addVertexWithUV(drawX, drawY + height, 0, 0, 1);
        tessellator.addVertexWithUV(drawX + width, drawY + height, 0, 1, 1);
        tessellator.addVertexWithUV(drawX + width, drawY, 0, 1, 0);
        tessellator.addVertexWithUV(drawX, drawY, 0, 0, 0);
        tessellator.draw();
    }

    // 获取纹理尺寸的辅助方法
    private int[] getTextureDimensions(ResourceLocation texture) {
        try {
            ITextureObject textureObject = mc.getTextureManager()
                .getTexture(texture);
            if (textureObject instanceof AbstractTexture) {
                AbstractTexture abstractTexture = (AbstractTexture) textureObject;
                int glTextureId = abstractTexture.getGlTextureId();

                // 绑定纹理
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, glTextureId);

                // 获取纹理宽度和高度
                int width = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
                int height = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);

                return new int[] { width, height };
            }
        } catch (Exception e) {
            // 处理可能的异常
            e.printStackTrace();
        }
        // 如果无法获取尺寸，返回0
        return new int[] { 0, 0 };
    }

    private void drawBackground() {
        int drawWidth = this.bg_width;
        int drawHeight = this.bg_height;

        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        if (this.backgroundTexture == null) {
            return;
        }
        if (isLegacyPanelTexture(this.backgroundTexture)) {
            UiPanel.draw(UiThemes.ADM, this.x, this.y, drawWidth, drawHeight);
            return;
        }
        // No arbitrary texture may be stretched. Generic artwork is aspect-fitted; ADM chrome is sparse above.
        drawImage(this.backgroundTexture, this.x, this.y, drawWidth, drawHeight);
    }

    // 链式调用方法
    public ADM_GuiScreen setBackgroundTexture(ResourceLocation texture) {
        this.backgroundTexture = texture;
        return this;
    }

    public ADM_GuiScreen setPosition(int x, int y) {
        this.x = x;
        this.y = y;
        return this;
    }

    public ADM_GuiScreen setSize(int width, int height) {
        this.bg_width = width;
        this.bg_height = height;
        return this;
    }

    /** Compatibility no-op: ADM artwork is tiled or aspect-fitted and is never non-uniformly stretched. */
    @Deprecated
    public ADM_GuiScreen setStretch(boolean ignored) {
        return this;
    }

    /** Opt out only for render-only screens whose framebuffer work must remain at native coordinates. */
    public ADM_GuiScreen setViewportTransformEnabled(boolean enabled) {
        this.viewportTransformEnabled = enabled;
        return this;
    }

    public ADM_GuiScreen setViewportMargin(int margin) {
        this.viewportMargin = Math.max(0, margin);
        return this;
    }

    protected final UiViewportTransform viewportTransform() {
        return refreshViewportTransform();
    }

    protected final int panelX() {
        return x;
    }

    protected final int panelY() {
        return y;
    }

    protected final int panelWidth() {
        return bg_width;
    }

    protected final int panelHeight() {
        return bg_height;
    }

    protected final int eventLogicalMouseX(int screenMouseX) {
        return refreshViewportTransform().toLogicalX(screenMouseX);
    }

    protected final int eventLogicalMouseY(int screenMouseY) {
        return refreshViewportTransform().toLogicalY(screenMouseY);
    }

    /** Draw a wrapped tooltip that remains inside the logical viewport before uniform scaling is applied. */
    protected final void drawAdmTooltip(int mouseX, int mouseY, int preferredWidth, String... paragraphs) {
        if (paragraphs == null || paragraphs.length == 0) {
            return;
        }
        int viewportWidth = Math.max(80, width - 32);
        int wrapWidth = Math.max(80, Math.min(preferredWidth, viewportWidth));
        List<String> lines = new ArrayList<String>();
        for (String paragraph : paragraphs) {
            if (paragraph == null || paragraph.isEmpty()) {
                continue;
            }
            lines.addAll(fontRendererObj.listFormattedStringToWidth(paragraph, wrapWidth));
        }
        if (!lines.isEmpty()) {
            drawHoveringText(lines, mouseX, mouseY, fontRendererObj);
        }
    }

    private UiViewportTransform refreshViewportTransform() {
        if (!viewportTransformEnabled || bg_width <= 0 || bg_height <= 0 || width <= 0 || height <= 0) {
            viewportTransform = UiViewportTransform
                .fitCenteredBounds(Math.max(1, width), Math.max(1, height), Math.max(1, width), Math.max(1, height), 0);
            return viewportTransform;
        }
        viewportTransform = UiViewportTransform.fitCenteredBounds(width, height, bg_width, bg_height, viewportMargin);
        return viewportTransform;
    }

    private static boolean isLegacyPanelTexture(ResourceLocation texture) {
        return AdmGuiTextures.BACKGROUND_SUB.equals(texture) || AdmGuiTextures.BACKGROUND_MONITOR_MAIN.equals(texture);
    }

    private static boolean isLegacyTextFieldTexture(ResourceLocation texture) {
        return AdmGuiTextures.TEXTFIELD_8020.equals(texture) || AdmGuiTextures.TEXTFIELD_HOVER_8020.equals(texture)
            || AdmGuiTextures.TEXTFIELD_SELECTED.equals(texture)
            || AdmGuiTextures.TEXTFIELD_SELECTED_ALT.equals(texture);
    }

    private static boolean isLegacyFocusedTextFieldTexture(ResourceLocation texture) {
        return AdmGuiTextures.TEXTFIELD_HOVER_8020.equals(texture) || AdmGuiTextures.TEXTFIELD_SELECTED.equals(texture)
            || AdmGuiTextures.TEXTFIELD_SELECTED_ALT.equals(texture);
    }
}
