package com.imgood.textech.gui.guiscreen;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.util.EnumChatFormatting;

import org.lwjgl.opengl.GL11;

import com.imgood.textech.webae.icon.IconGridExporter;
import com.imgood.textech.webae.icon.IconItemEnumerator;
import com.imgood.textech.webae.icon.IconRenderer;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Isolated GUI context for NEI-style grid icon export. Pauses world rendering while active.
 */
@SideOnly(Side.CLIENT)
public class GuiIconExportScreen extends GuiScreen {

    private final IconRenderer session;
    private final IconGridExporter gridExporter = new IconGridExporter();
    private int pageIndex;
    private int renderedItems;
    private boolean cancelled;

    public GuiIconExportScreen(IconRenderer session) {
        this.session = session;
    }

    @Override
    public void initGui() {
        super.initGui();
        mc.renderEngine.bindTexture(TextureMap.locationItemsTexture);
        mc.renderEngine.bindTexture(TextureMap.locationBlocksTexture);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        if (cancelled) {
            session.onExportCancelled();
            mc.displayGuiScreen(null);
            return;
        }

        int total = session.getPendingCount();
        if (pageIndex * IconGridExporter.SLOTS_PER_PAGE >= total) {
            session.onExportComplete(gridExporter);
            gridExporter.reset();
            mc.displayGuiScreen(null);
            return;
        }

        int start = pageIndex * IconGridExporter.SLOTS_PER_PAGE;
        int end = Math.min(start + IconGridExporter.SLOTS_PER_PAGE, total);
        java.util.List<IconItemEnumerator.StackTask> page = session.getPendingSubList(start, end);
        java.util.Map<String, byte[]> rendered = gridExporter.renderPage(mc, page);
        session.mergeRenderedIcons(rendered);
        renderedItems += page.size();
        pageIndex++;

        String title = EnumChatFormatting.AQUA + "WebAE Icon Export";
        String progress = EnumChatFormatting.WHITE + "Rendered "
            + Math.min(renderedItems, total)
            + " / "
            + total
            + " (page "
            + pageIndex
            + ")";
        String hint = EnumChatFormatting.GRAY + "Press ESC to cancel";
        drawCenteredString(fontRendererObj, title, width / 2, height / 2 - 24, 0xFFFFFF);
        drawCenteredString(fontRendererObj, progress, width / 2, height / 2 - 8, 0xFFFFFF);
        drawCenteredString(fontRendererObj, hint, width / 2, height / 2 + 8, 0xAAAAAA);

        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    @Override
    protected void keyTyped(char typedChar, int key) {
        if (key == 1) {
            cancelled = true;
            return;
        }
        super.keyTyped(typedChar, key);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return true;
    }
}
