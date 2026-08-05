package com.imgood.textech.gui.guiscreen;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;

import javax.imageio.ImageIO;

import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import com.imgood.textech.gui.framework.GuiBlitUtil;
import com.imgood.textech.gui.framework.UiPanel;
import com.imgood.textech.gui.framework.UiThemes;
import com.imgood.textech.gui.custom.ADM_GuiScreen;
import com.imgood.textech.gui.custom.AdmGuiTextures;
import com.imgood.textech.webae.icon.IconExportResolver;
import com.imgood.textech.webae.icon.IconItemEnumerator;
import com.imgood.textech.webae.icon.IconRenderGuard;
import com.imgood.textech.webae.icon.IconRenderMode;
import com.imgood.textech.webae.icon.IconStore;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Three-column comparison of the in-game item render, a freshly exported PNG, and the cached server PNG.
 */
@SideOnly(Side.CLIENT)
public class GuiIconVerifyScreen extends ADM_GuiScreen {

    private final String itemId;
    private final String packName;
    private final IconItemEnumerator.StackTask task;
    private final IconExportResolver resolver = IconExportResolver.createStandalone();
    private ResourceLocation liveTexLoc;
    private DynamicTexture liveDynamic;
    private ResourceLocation cachedTexLoc;
    private DynamicTexture cachedDynamic;

    public GuiIconVerifyScreen(String itemId, String packName) {
        this.itemId = itemId;
        this.packName = packName;
        this.task = IconItemEnumerator.resolveSingle(itemId);
        setBackgroundTexture(AdmGuiTextures.BACKGROUND_SUB);
        setSize(480, 220);
        setStretch(false);
    }

    @Override
    public void initGui() {
        super.initGui();
        setPosition((width - panelWidth()) / 2, (height - panelHeight()) / 2);
        releaseTextures();
        mc.renderEngine.bindTexture(TextureMap.locationItemsTexture);
        mc.renderEngine.bindTexture(TextureMap.locationBlocksTexture);
        if (task != null && task.stack != null) {
            try {
                IconExportResolver.ResolveResult result = resolver.resolve(mc, task.stack, task.itemId, null);
                BufferedImage liveImage = result.png != null ? ImageIO.read(new ByteArrayInputStream(result.png)) : null;
                if (liveImage != null) {
                    liveDynamic = new DynamicTexture(liveImage);
                    liveTexLoc = mc.getTextureManager()
                        .getDynamicTextureLocation("icon_verify_live_" + itemId.hashCode(), liveDynamic);
                }
            } catch (Exception ignored) {
            } finally {
                IconRenderGuard.afterRender(mc);
            }
        }
        java.io.File cached = IconStore.instance()
            .getIconFile(packName, IconRenderMode.NEI.getId(), itemId);
        if (cached != null && cached.isFile()) {
            try {
                BufferedImage cachedImage = ImageIO.read(cached);
                if (cachedImage != null) {
                    cachedDynamic = new DynamicTexture(cachedImage);
                    cachedTexLoc = mc.getTextureManager()
                        .getDynamicTextureLocation("icon_verify_cached_" + itemId.hashCode(), cachedDynamic);
                }
            } catch (Exception ignored) {}
        }
        resolver.reset();
    }

    @Override
    public void onGuiClosed() {
        releaseTextures();
        super.onGuiClosed();
    }

    @Override
    protected void drawAdmScreen(int mouseX, int mouseY, float partialTicks) {
        int left = panelX();
        int top = panelY();
        int panelCenter = left + panelWidth() / 2;
        UiPanel.drawSection(UiThemes.ADM, left + 12, top + 38, panelWidth() - 24, panelHeight() - 76);
        String shortItemId = fontRendererObj.trimStringToWidth(itemId, panelWidth() - 150);
        drawCenteredString(
            fontRendererObj,
            EnumChatFormatting.AQUA + I18n.format("adm.icon_verify.title", shortItemId),
            panelCenter,
            top + 12,
            0xFFFFFF);
        int contentX = left + 16;
        int columnWidth = (panelWidth() - 32) / 3;
        int labelY = top + 52;
        int iconY = top + 82;
        int runtimeCenter = contentX + columnWidth / 2;
        int freshCenter = runtimeCenter + columnWidth;
        int cachedCenter = freshCenter + columnWidth;
        drawColumnLabel(runtimeCenter, labelY, columnWidth, I18n.format("adm.icon_verify.runtime_label"));
        drawColumnLabel(freshCenter, labelY, columnWidth, I18n.format("adm.icon_verify.fresh_label"));
        drawColumnLabel(
            cachedCenter,
            labelY,
            columnWidth,
            I18n.format("adm.icon_verify.cached_label", packName, IconRenderMode.NEI.getId()));

        if (task != null && task.stack != null) {
            GL11.glEnable(GL12.GL_RESCALE_NORMAL);
            RenderHelper.enableGUIStandardItemLighting();
            RenderItem.getInstance()
                .renderItemAndEffectIntoGUI(fontRendererObj, mc.getTextureManager(), task.stack, runtimeCenter - 8, iconY + 24);
            RenderHelper.disableStandardItemLighting();
            GL11.glDisable(GL12.GL_RESCALE_NORMAL);
        } else {
            drawColumnStatus(runtimeCenter, iconY + 28, columnWidth, I18n.format("adm.icon_verify.unresolved"), 0xFF5555);
        }

        if (liveTexLoc != null) {
            GuiBlitUtil.drawFullTexture(liveTexLoc, freshCenter - 32, iconY, 64, 64);
        } else {
            drawColumnStatus(freshCenter, iconY + 28, columnWidth, I18n.format("adm.icon_verify.no_live_icon"), 0xAAAAAA);
        }
        if (cachedTexLoc != null) {
            GuiBlitUtil.drawFullTexture(cachedTexLoc, cachedCenter - 32, iconY, 64, 64);
        } else {
            drawColumnStatus(cachedCenter, iconY + 28, columnWidth, I18n.format("adm.icon_verify.no_cached_icon"), 0xAAAAAA);
        }

        drawCenteredString(
            fontRendererObj,
            EnumChatFormatting.GRAY + I18n.format("adm.icon_verify.close_hint"),
            panelCenter,
            top + panelHeight() - 20,
            0xAAAAAA);
        if (!shortItemId.equals(itemId) && mouseY >= top + 8 && mouseY < top + 28) {
            drawAdmTooltip(mouseX, mouseY, panelWidth() - 60, itemId);
        }
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private void drawColumnLabel(int centerX, int y, int columnWidth, String label) {
        drawCenteredString(
            fontRendererObj,
            EnumChatFormatting.YELLOW + fontRendererObj.trimStringToWidth(label, columnWidth - 8),
            centerX,
            y,
            0xFFFFFF);
    }

    private void drawColumnStatus(int centerX, int y, int columnWidth, String text, int color) {
        drawCenteredString(fontRendererObj, fontRendererObj.trimStringToWidth(text, columnWidth - 8), centerX, y, color);
    }

    private void releaseTextures() {
        if (liveDynamic != null) {
            liveDynamic.deleteGlTexture();
            liveDynamic = null;
            liveTexLoc = null;
        }
        if (cachedDynamic != null) {
            cachedDynamic.deleteGlTexture();
            cachedDynamic = null;
            cachedTexLoc = null;
        }
    }

    @Override
    protected void keyTyped(char typedChar, int key) {
        if (key == 1) {
            mc.displayGuiScreen(null);
            return;
        }
        super.keyTyped(typedChar, key);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return true;
    }
}
