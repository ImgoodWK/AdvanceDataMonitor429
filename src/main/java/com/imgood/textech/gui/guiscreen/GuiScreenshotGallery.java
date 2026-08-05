package com.imgood.textech.gui.guiscreen;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import javax.imageio.ImageIO;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import com.imgood.textech.client.screenshot.ClientScreenshotService;
import com.imgood.textech.gui.custom.ADM_GuiButton;
import com.imgood.textech.gui.custom.ADM_GuiScreen;
import com.imgood.textech.gui.custom.AdmGuiTextures;
import com.imgood.textech.gui.framework.UiPanel;
import com.imgood.textech.gui.framework.UiThemes;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** In-game preview of locally saved framebuffer screenshots. */
@SideOnly(Side.CLIENT)
public final class GuiScreenshotGallery extends ADM_GuiScreen {

    private static final SimpleDateFormat DISPLAY_TIME = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private final List<File> history;
    private int index;
    private DynamicTexture texture;
    private ResourceLocation textureLocation;
    private int imageWidth;
    private int imageHeight;
    private String loadError = "";

    public GuiScreenshotGallery(List<File> history, int index) {
        this.history = history;
        this.index = Math.max(0, Math.min(history.size() - 1, index));
        setBackgroundTexture(AdmGuiTextures.BACKGROUND_SUB);
        setStretch(false);
    }

    @Override
    public void initGui() {
        super.initGui();
        Keyboard.enableRepeatEvents(true);
        setPosition(8, 4);
        setSize(Math.max(1, width - 16), Math.max(1, height - 8));
        buttonList.clear();
        int y = height - 28;
        buttonList
            .add(new ADM_GuiButton(1, width / 2 - 156, y, 72, 20, I18n.format("adm.screenshot.gallery.previous")));
        buttonList.add(new ADM_GuiButton(2, width / 2 - 78, y, 72, 20, I18n.format("adm.screenshot.gallery.next")));
        buttonList.add(new ADM_GuiButton(3, width / 2, y, 96, 20, I18n.format("adm.screenshot.gallery.send_web")));
        buttonList.add(new ADM_GuiButton(4, width / 2 + 102, y, 54, 20, I18n.format("gui.done")));
        loadCurrent();
        updateButtons();
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 1 && index + 1 < history.size()) {
            index++;
            loadCurrent();
        } else if (button.id == 2 && index > 0) {
            index--;
            loadCurrent();
        } else if (button.id == 3) {
            ClientScreenshotService.instance()
                .queueUpload("web", "", "", history.get(index), "");
        } else if (button.id == 4) {
            mc.displayGuiScreen(null);
        }
        updateButtons();
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_LEFT && index + 1 < history.size()) {
            index++;
            loadCurrent();
            updateButtons();
            return;
        }
        if (keyCode == Keyboard.KEY_RIGHT && index > 0) {
            index--;
            loadCurrent();
            updateButtons();
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void drawAdmScreen(int mouseX, int mouseY, float partialTicks) {
        File file = history.get(index);
        drawCenteredString(
            fontRendererObj,
            EnumChatFormatting.AQUA + I18n.format("adm.screenshot.gallery.title", index + 1, history.size()),
            width / 2,
            10,
            0xFFFFFF);
        if (textureLocation != null) {
            int areaX = 16;
            int areaY = 28;
            int areaWidth = Math.max(1, width - 32);
            int areaHeight = Math.max(1, height - 76);
            UiPanel.drawSection(UiThemes.ADM, areaX - 2, areaY - 2, areaWidth + 4, areaHeight + 4);
            double scale = Math.min((double) areaWidth / imageWidth, (double) areaHeight / imageHeight);
            int drawWidth = Math.max(1, (int) Math.round(imageWidth * scale));
            int drawHeight = Math.max(1, (int) Math.round(imageHeight * scale));
            drawTexture((width - drawWidth) / 2, areaY + (areaHeight - drawHeight) / 2, drawWidth, drawHeight);
        } else {
            drawCenteredString(
                fontRendererObj,
                EnumChatFormatting.RED + I18n.format("adm.screenshot.gallery.load_failed", loadError),
                width / 2,
                height / 2,
                0xFF5555);
        }
        String timestamp;
        synchronized (DISPLAY_TIME) {
            timestamp = DISPLAY_TIME.format(new Date(file.lastModified()));
        }
        drawCenteredString(
            fontRendererObj,
            EnumChatFormatting.GRAY + I18n.format(
                "adm.screenshot.gallery.metadata",
                file.getName(),
                imageWidth,
                imageHeight,
                file.length() / 1024L,
                timestamp),
            width / 2,
            height - 43,
            0xAAAAAA);
        super.drawAdmScreen(mouseX, mouseY, partialTicks);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        releaseTexture();
        super.onGuiClosed();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private void loadCurrent() {
        releaseTexture();
        loadError = "";
        imageWidth = 0;
        imageHeight = 0;
        try {
            BufferedImage source = ImageIO.read(history.get(index));
            if (source == null) throw new IllegalStateException("invalid image");
            imageWidth = source.getWidth();
            imageHeight = source.getHeight();
            int maxWidth = Math.max(64, width - 32);
            int maxHeight = Math.max(64, height - 76);
            double scale = Math.min(1.0D, Math.min((double) maxWidth / imageWidth, (double) maxHeight / imageHeight));
            int uploadWidth = Math.max(1, (int) Math.round(imageWidth * scale));
            int uploadHeight = Math.max(1, (int) Math.round(imageHeight * scale));
            BufferedImage display = source;
            if (uploadWidth != imageWidth || uploadHeight != imageHeight) {
                display = new BufferedImage(uploadWidth, uploadHeight, BufferedImage.TYPE_INT_RGB);
                Graphics2D graphics = display.createGraphics();
                try {
                    graphics.setRenderingHint(
                        RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    graphics.drawImage(source, 0, 0, uploadWidth, uploadHeight, null);
                } finally {
                    graphics.dispose();
                }
            }
            texture = new DynamicTexture(display);
            textureLocation = mc.getTextureManager()
                .getDynamicTextureLocation("textech_screenshot_preview", texture);
        } catch (Throwable error) {
            loadError = error.getMessage() == null ? error.getClass()
                .getSimpleName() : error.getMessage();
        }
    }

    private void updateButtons() {
        for (Object value : buttonList) {
            GuiButton button = (GuiButton) value;
            if (button.id == 1) button.enabled = index + 1 < history.size();
            if (button.id == 2) button.enabled = index > 0;
            if (button.id == 3) button.enabled = textureLocation != null && !ClientScreenshotService.instance()
                .isUploadBusy();
        }
    }

    private void drawTexture(int x, int y, int width, int height) {
        mc.getTextureManager()
            .bindTexture(textureLocation);
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.addVertexWithUV(x, y + height, 0.0D, 0.0D, 1.0D);
        tessellator.addVertexWithUV(x + width, y + height, 0.0D, 1.0D, 1.0D);
        tessellator.addVertexWithUV(x + width, y, 0.0D, 1.0D, 0.0D);
        tessellator.addVertexWithUV(x, y, 0.0D, 0.0D, 0.0D);
        tessellator.draw();
    }

    private void releaseTexture() {
        if (texture != null) {
            texture.deleteGlTexture();
            texture = null;
        }
        textureLocation = null;
    }
}
