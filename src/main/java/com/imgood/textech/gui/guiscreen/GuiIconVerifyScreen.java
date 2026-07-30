package com.imgood.textech.gui.guiscreen;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;

import javax.imageio.ImageIO;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import com.imgood.textech.gui.framework.UiPanel;
import com.imgood.textech.gui.framework.UiThemes;
import com.imgood.textech.webae.icon.IconExportResolver;
import com.imgood.textech.webae.icon.IconItemEnumerator;
import com.imgood.textech.webae.icon.IconRenderGuard;
import com.imgood.textech.webae.icon.IconRenderMode;
import com.imgood.textech.webae.icon.IconStore;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Side-by-side preview of live NESQL-style render vs cached server icon PNG.
 */
@SideOnly(Side.CLIENT)
public class GuiIconVerifyScreen extends GuiScreen {

    private final String itemId;
    private final String packName;
    private final IconItemEnumerator.StackTask task;
    private final IconExportResolver resolver = IconExportResolver.createStandalone();
    private byte[] livePng;
    private BufferedImage cachedImage;
    private ResourceLocation cachedTexLoc;
    private DynamicTexture cachedDynamic;

    public GuiIconVerifyScreen(String itemId, String packName) {
        this.itemId = itemId;
        this.packName = packName;
        this.task = IconItemEnumerator.resolveSingle(itemId);
    }

    @Override
    public void initGui() {
        super.initGui();
        mc.renderEngine.bindTexture(TextureMap.locationItemsTexture);
        mc.renderEngine.bindTexture(TextureMap.locationBlocksTexture);
        if (task != null && task.stack != null) {
            try {
                IconExportResolver.ResolveResult result = resolver.resolve(mc, task.stack, task.itemId, null);
                livePng = result.png;
            } finally {
                IconRenderGuard.afterRender(mc);
            }
        }
        java.io.File cached = IconStore.instance()
            .getIconFile(packName, IconRenderMode.NEI.getId(), itemId);
        if (cached != null && cached.isFile()) {
            try {
                cachedImage = ImageIO.read(cached);
                if (cachedImage != null) {
                    cachedDynamic = new DynamicTexture(cachedImage);
                    cachedTexLoc = mc.getTextureManager()
                        .getDynamicTextureLocation("icon_verify_" + itemId.hashCode(), cachedDynamic);
                }
            } catch (Exception ignored) {}
        }
        resolver.reset();
    }

    @Override
    public void onGuiClosed() {
        if (cachedDynamic != null) {
            cachedDynamic.deleteGlTexture();
            cachedDynamic = null;
        }
        super.onGuiClosed();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        UiPanel.draw(UiThemes.ADM, 16, 12, Math.max(176, width - 32), Math.max(132, height - 24));
        drawCenteredString(
            fontRendererObj,
            EnumChatFormatting.AQUA + "Icon Verify: " + itemId,
            width / 2,
            20,
            0xFFFFFF);
        int y = 48;
        int slot = 64;
        drawString(fontRendererObj, EnumChatFormatting.YELLOW + "Live NESQL drawItem:", 32, y, 0xFFFFFF);
        drawString(
            fontRendererObj,
            EnumChatFormatting.YELLOW + "Cached PNG (" + packName + "/nei):",
            width / 2 + 16,
            y,
            0xFFFFFF);

        if (task != null && task.stack != null) {
            GL11.glEnable(GL12.GL_RESCALE_NORMAL);
            RenderHelper.enableGUIStandardItemLighting();
            RenderItem.getInstance()
                .renderItemAndEffectIntoGUI(fontRendererObj, mc.getTextureManager(), task.stack, 32, y + 14);
            RenderHelper.disableStandardItemLighting();
            GL11.glDisable(GL12.GL_RESCALE_NORMAL);
        } else {
            drawCenteredString(
                fontRendererObj,
                EnumChatFormatting.RED + "Could not resolve stack",
                width / 4,
                y + 40,
                0xFF5555);
        }

        drawPng(livePng, 32, y + 14 + 20, slot);
        if (cachedTexLoc != null) {
            drawTexturedRect(width / 2 + 16, y + 14, slot, slot, cachedTexLoc);
        } else {
            drawCenteredString(
                fontRendererObj,
                EnumChatFormatting.GRAY + "No cached icon",
                width / 2 + 16 + slot / 2,
                y + 40,
                0xAAAAAA);
        }

        drawCenteredString(
            fontRendererObj,
            EnumChatFormatting.GRAY + "Press ESC to close",
            width / 2,
            height - 24,
            0xAAAAAA);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private void drawPng(byte[] png, int x, int y, int size) {
        if (png == null) return;
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(png));
            if (img == null) return;
            BufferedImage scaled = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = scaled.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(img, 0, 0, size, size, null);
            g.dispose();
            DynamicTexture tex = new DynamicTexture(scaled);
            ResourceLocation loc = mc.getTextureManager()
                .getDynamicTextureLocation("icon_verify_live", tex);
            drawTexturedRect(x, y, size, size, loc);
            tex.deleteGlTexture();
        } catch (Exception ignored) {}
    }

    private void drawTexturedRect(int x, int y, int w, int h, ResourceLocation tex) {
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        mc.getTextureManager()
            .bindTexture(tex);
        drawTexturedModalRect(x, y, 0, 0, w, h);
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
