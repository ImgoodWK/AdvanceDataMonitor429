package com.imgood.textech.gui.guiscreen;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.util.EnumChatFormatting;

import org.lwjgl.opengl.GL11;

import com.imgood.textech.Config;
import com.imgood.textech.webae.icon.IconExportResolver;
import com.imgood.textech.webae.icon.IconItemEnumerator;
import com.imgood.textech.webae.icon.IconRenderGuard;
import com.imgood.textech.webae.icon.IconRenderMode;
import com.imgood.textech.webae.icon.IconRenderer;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Isolated GUI context for NESQL-style per-icon export. Pauses world rendering while active.
 *
 * <p>
 * Renders {@link Config#webIconRenderPerTick} (or {@code webIconRenderPerTickAll}) items per frame
 * via {@link IconExportResolver} (fluid specials + {@code GuiContainerManager.drawItem} FBO).
 * Legacy grid batching lives in {@link com.imgood.textech.webae.icon.IconGridExporter} but is not used.
 * </p>
 */
@SideOnly(Side.CLIENT)
public class GuiIconExportScreen extends GuiScreen {

    private final IconRenderer session;
    private int cursor;
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
        if (cursor >= total) {
            session.onExportComplete(session.getGridExporter());
            session.getGridExporter()
                .reset();
            mc.displayGuiScreen(null);
            return;
        }

        int perTick = session.isUploadAllModes() ? Config.webIconRenderPerTickAll : Config.webIconRenderPerTick;
        if (perTick < 1) perTick = 1;
        int end = Math.min(cursor + perTick, total);
        List<IconItemEnumerator.StackTask> batch = session.getPendingSubList(cursor, end);
        Map<String, byte[]> rendered = new LinkedHashMap<String, byte[]>();
        IconExportResolver resolver = session.getExportResolver();
        for (IconItemEnumerator.StackTask task : batch) {
            if (task == null || task.stack == null) continue;
            try {
                IconExportResolver.ResolveResult result = resolver.resolve(mc, task.stack, task.itemId, null);
                if (result.png != null && result.png.length > 0) {
                    rendered.put(task.itemId, result.png);
                }
            } finally {
                IconRenderGuard.afterRender(mc);
            }
        }
        session.mergeRenderedIcons(rendered);
        renderedItems += batch.size();
        cursor = end;

        String modeLabel = session.getCurrentMode() != null ? session.getCurrentMode()
            .getId() : IconRenderMode.NEI.getId();
        String title = EnumChatFormatting.AQUA + "WebAE Icon Export (" + modeLabel + ")";
        String progress = EnumChatFormatting.WHITE + "Rendered " + Math.min(renderedItems, total) + " / " + total;
        String hint = EnumChatFormatting.GRAY + "Press ESC to cancel";
        drawCenteredString(fontRendererObj, title, width / 2, height / 2 - 24, 0xFFFFFF);
        drawCenteredString(fontRendererObj, progress, width / 2, height / 2 - 8, 0xFFFFFF);
        drawCenteredString(fontRendererObj, hint, width / 2, height / 2 + 8, 0xAAAAAA);

        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        IconRenderGuard.afterRender(mc);
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
