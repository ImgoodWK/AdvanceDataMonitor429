package com.imgood.advancedatamonitor.gui.guiscreen;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import com.imgood.advancedatamonitor.gui.manual.ManualChapter;
import com.imgood.advancedatamonitor.gui.manual.ManualDataLoader;
import com.imgood.advancedatamonitor.gui.manual.ManualPage;
import com.imgood.advancedatamonitor.renders.ManualPageRenderer;

/**
 * Display names / 显示名称:
 * - EN: AdvanceDataMonitor Manual
 * - ZH: 高级数据监视器手册
 * Lang keys: item.manual.name
 *
 * Main manual GUI with left sidebar chapter navigation and right content area.
 */
public class GuiManual extends GuiScreen {

    private static final int GUI_WIDTH = 320;
    private static final int GUI_HEIGHT = 246;
    private static final int SIDEBAR_WIDTH = 62;
    private static final int CONTENT_X = SIDEBAR_WIDTH + 2;
    private static final int CONTENT_WIDTH = GUI_WIDTH - SIDEBAR_WIDTH - 4;
    private static final int CONTENT_TOP = 16;
    private static final int CONTENT_BOTTOM = GUI_HEIGHT - 28;
    private static final int CONTENT_HEIGHT_VAL = CONTENT_BOTTOM - CONTENT_TOP;
    private static final int SIDEBAR_ITEM_HEIGHT = 26;
    // Blue-cyan color palette: dark backgrounds, light text
    private static final int BG_COLOR = 0xFF0E1A30;
    private static final int SIDEBAR_BG = 0xFF122040;
    private static final int SELECTED_COLOR = 0xFF1A6080;
    private static final int HOVER_COLOR = 0xFF1A3A5C;
    private static final int SIDEBAR_SEPARATOR = 0xFF1E5080;
    private static final int BORDER_COLOR = 0xFF0A1220;
    private static final int SIDEBAR_TEXT = 0xFFC8E0FF;
    private static final int SIDEBAR_TEXT_SELECTED = 0xFF00E5FF;
    private static final int PAGE_COUNTER_COLOR = 0xFF5080B0;
    private static final int CHAPTER_TITLE_COLOR = 0xFF20B8E0;

    private static final RenderItem itemRenderer = new RenderItem();

    private int guiLeft;
    private int guiTop;

    private List<ManualChapter> chapters;
    private int selectedChapter = 0;
    private int currentPage = 0;
    private int configScrollOffset = 0;

    private GuiButton prevButton;
    private GuiButton nextButton;

    public GuiManual() {
        chapters = ManualDataLoader.loadChapters();
    }

    @Override
    public void initGui() {
        guiLeft = (width - GUI_WIDTH) / 2;
        guiTop = (height - GUI_HEIGHT) / 2;

        int buttonWidth = 56;
        int buttonHeight = 14;
        int buttonY = guiTop + GUI_HEIGHT - 22;

        buttonList.clear();
        prevButton = new GuiButton(
            0,
            guiLeft + CONTENT_X + 4,
            buttonY,
            buttonWidth,
            buttonHeight,
            I18n.format("adm.manual.prev_page"));
        nextButton = new GuiButton(
            1,
            guiLeft + CONTENT_X + CONTENT_WIDTH - buttonWidth - 4,
            buttonY,
            buttonWidth,
            buttonHeight,
            I18n.format("adm.manual.next_page"));
        buttonList.add(prevButton);
        buttonList.add(nextButton);
        updateButtons();
    }

    private void updateButtons() {
        if (chapters.isEmpty()) {
            prevButton.enabled = false;
            nextButton.enabled = false;
            return;
        }
        ManualChapter chapter = getCurrentChapter();
        prevButton.enabled = currentPage > 0;
        nextButton.enabled = chapter != null && currentPage < chapter.getPageCount() - 1;

        // For config_ref, always allow next/prev (auto-split)
        ManualPage page = getCurrentPage();
        if (page != null && page.isConfigRef()) {
            // Config pages can scroll, always allow navigation between categories
            prevButton.enabled = selectedChapter > 0 || currentPage > 0;
            nextButton.enabled = selectedChapter < chapters.size() - 1 || currentPage < chapter.getPageCount() - 1;
        }
    }

    private ManualChapter getCurrentChapter() {
        if (selectedChapter < 0 || selectedChapter >= chapters.size()) return null;
        return chapters.get(selectedChapter);
    }

    private ManualPage getCurrentPage() {
        ManualChapter chapter = getCurrentChapter();
        if (chapter == null || currentPage < 0 || currentPage >= chapter.getPageCount()) return null;
        return chapter.getPage(currentPage);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawBackground();
        drawSidebar(mouseX, mouseY);
        drawContent(mouseX, mouseY);
        drawPageCounter();
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawBackground() {
        // Main background
        drawRect(guiLeft, guiTop, guiLeft + GUI_WIDTH, guiTop + GUI_HEIGHT, BG_COLOR);
        // Border
        drawRect(guiLeft - 1, guiTop - 1, guiLeft + GUI_WIDTH + 1, guiTop, BORDER_COLOR);
        drawRect(guiLeft - 1, guiTop + GUI_HEIGHT, guiLeft + GUI_WIDTH + 1, guiTop + GUI_HEIGHT + 1, BORDER_COLOR);
        drawRect(guiLeft - 1, guiTop, guiLeft, guiTop + GUI_HEIGHT, BORDER_COLOR);
        drawRect(guiLeft + GUI_WIDTH, guiTop, guiLeft + GUI_WIDTH + 1, guiTop + GUI_HEIGHT, BORDER_COLOR);
        // Sidebar background
        drawRect(guiLeft, guiTop, guiLeft + SIDEBAR_WIDTH, guiTop + GUI_HEIGHT, SIDEBAR_BG);
        // Sidebar separator
        drawRect(guiLeft + SIDEBAR_WIDTH, guiTop, guiLeft + SIDEBAR_WIDTH + 1, guiTop + GUI_HEIGHT, SIDEBAR_SEPARATOR);
    }

    private void drawSidebar(int mouseX, int mouseY) {
        for (int i = 0; i < chapters.size(); i++) {
            ManualChapter chapter = chapters.get(i);
            int y = guiTop + 6 + i * SIDEBAR_ITEM_HEIGHT;
            int x = guiLeft + 2;

            boolean hovered = mouseX >= x && mouseX < x + SIDEBAR_WIDTH - 4
                && mouseY >= y
                && mouseY < y + SIDEBAR_ITEM_HEIGHT;

            if (i == selectedChapter) {
                drawRect(x, y, x + SIDEBAR_WIDTH - 4, y + SIDEBAR_ITEM_HEIGHT, SELECTED_COLOR);
            } else if (hovered) {
                drawRect(x, y, x + SIDEBAR_WIDTH - 4, y + SIDEBAR_ITEM_HEIGHT, HOVER_COLOR);
            }

            // Draw chapter icon (small, 12x12)
            ItemStack icon = getIcon(chapter.icon);
            if (icon != null) {
                renderSmallIcon(icon, x + 1, y + 3);
            }

            // Draw chapter name (wrapped to fit sidebar)
            String name = I18n.format(chapter.chapterTitleKey);
            List<String> nameLines = fontRendererObj.listFormattedStringToWidth(name, SIDEBAR_WIDTH - 18);
            int textY = y + 2;
            int textColor = (i == selectedChapter) ? SIDEBAR_TEXT_SELECTED : SIDEBAR_TEXT;
            for (String line : nameLines) {
                fontRendererObj.drawString(line, x + 14, textY, textColor);
                textY += 9;
            }
        }
    }

    private void drawContent(int mouseX, int mouseY) {
        ManualChapter chapter = getCurrentChapter();
        ManualPage page = getCurrentPage();
        if (chapter == null || page == null) return;

        int cx = guiLeft + CONTENT_X + 4;
        int cy = guiTop + CONTENT_TOP;

        if (page.isTextPage()) {
            ManualPageRenderer.renderTextPage(fontRendererObj, page, cx, cy, CONTENT_WIDTH - 8, CONTENT_HEIGHT_VAL);
        } else if (page.isItemShowcase()) {
            ManualPageRenderer.renderItemShowcase(fontRendererObj, page, cx, cy, CONTENT_WIDTH - 8, CONTENT_HEIGHT_VAL);
        } else if (page.isConfigRef()) {
            renderConfigRefWithScroll(page, cx, cy);
        }
    }

    private void renderConfigRefWithScroll(ManualPage page, int x, int y) {
        // For config_ref pages, handle scrolling with mouse wheel
        GL11.glPushMatrix();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        Minecraft mc = Minecraft.getMinecraft();
        int scaleFactor = new net.minecraft.client.gui.ScaledResolution(mc, mc.displayWidth, mc.displayHeight)
            .getScaleFactor();
        int scissorY = mc.displayHeight - (guiTop + CONTENT_BOTTOM) * scaleFactor;
        GL11.glScissor(
            (guiLeft + CONTENT_X + 4) * scaleFactor,
            scissorY,
            (CONTENT_WIDTH - 8) * scaleFactor,
            (CONTENT_HEIGHT_VAL) * scaleFactor);

        // Translate by scroll offset
        GL11.glTranslatef(0, -configScrollOffset, 0);
        ManualPageRenderer
            .renderConfigRef(fontRendererObj, page, x, y, CONTENT_WIDTH - 8, CONTENT_HEIGHT_VAL + configScrollOffset);
        GL11.glTranslatef(0, configScrollOffset, 0);

        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glPopMatrix();
    }

    private void drawPageCounter() {
        ManualChapter chapter = getCurrentChapter();
        if (chapter == null) return;

        String text = (currentPage + 1) + " / " + chapter.getPageCount();
        int textWidth = fontRendererObj.getStringWidth(text);
        fontRendererObj.drawString(
            text,
            guiLeft + CONTENT_X + (CONTENT_WIDTH - textWidth) / 2,
            guiTop + GUI_HEIGHT - 20,
            PAGE_COUNTER_COLOR);

        // Chapter title at top of content area
        String chapterTitle = I18n.format(chapter.chapterTitleKey);
        int titleWidth = fontRendererObj.getStringWidth(chapterTitle);
        fontRendererObj.drawString(
            chapterTitle,
            guiLeft + CONTENT_X + (CONTENT_WIDTH - titleWidth) / 2,
            guiTop + 5,
            CHAPTER_TITLE_COLOR);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        super.mouseClicked(mouseX, mouseY, button);

        // Check sidebar clicks
        if (button == 0) {
            for (int i = 0; i < chapters.size(); i++) {
                int y = guiTop + 6 + i * SIDEBAR_ITEM_HEIGHT;
                int x = guiLeft + 2;
                if (mouseX >= x && mouseX < x + SIDEBAR_WIDTH - 4 && mouseY >= y && mouseY < y + SIDEBAR_ITEM_HEIGHT) {
                    if (i != selectedChapter) {
                        selectedChapter = i;
                        currentPage = 0;
                        configScrollOffset = 0;
                        updateButtons();
                    }
                    return;
                }
            }
        }

        // Previous/Next page buttons
        if (button == 0) {
            if (prevButton.mousePressed(mc, mouseX, mouseY)) {
                goToPrevPage();
            } else if (nextButton.mousePressed(mc, mouseX, mouseY)) {
                goToNextPage();
            }
        }
    }

    private void goToPrevPage() {
        if (currentPage > 0) {
            currentPage--;
        } else if (selectedChapter > 0) {
            selectedChapter--;
            currentPage = chapters.get(selectedChapter)
                .getPageCount() - 1;
        }
        configScrollOffset = 0;
        updateButtons();
    }

    private void goToNextPage() {
        ManualChapter chapter = getCurrentChapter();
        if (chapter != null && currentPage < chapter.getPageCount() - 1) {
            currentPage++;
        } else if (selectedChapter < chapters.size() - 1) {
            selectedChapter++;
            currentPage = 0;
        }
        configScrollOffset = 0;
        updateButtons();
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        ManualPage page = getCurrentPage();
        if (page != null && page.isConfigRef() && wheel != 0) {
            configScrollOffset += wheel / 10;
            if (configScrollOffset < 0) configScrollOffset = 0;
            // Max scroll could be limited but for now let it scroll freely
            if (configScrollOffset > 800) configScrollOffset = 800;
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == 1 || keyCode == mc.gameSettings.keyBindInventory.getKeyCode()) {
            mc.thePlayer.closeScreen();
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private ItemStack getIcon(String iconId) {
        if (iconId == null || iconId.isEmpty()) return null;
        try {
            // Try full name first
            Object obj = Item.itemRegistry.getObject(iconId);
            if (obj instanceof Item) return new ItemStack((Item) obj);
            Object blockObj = net.minecraft.block.Block.blockRegistry.getObject(iconId);
            if (blockObj instanceof net.minecraft.block.Block)
                return new ItemStack((net.minecraft.block.Block) blockObj);
            // Try bare name
            if (iconId.contains(":")) {
                String bareName = iconId.substring(iconId.indexOf(':') + 1);
                obj = Item.itemRegistry.getObject(bareName);
                if (obj instanceof Item) return new ItemStack((Item) obj);
                blockObj = net.minecraft.block.Block.blockRegistry.getObject(bareName);
                if (blockObj instanceof net.minecraft.block.Block)
                    return new ItemStack((net.minecraft.block.Block) blockObj);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private void renderSmallIcon(ItemStack stack, int x, int y) {
        GL11.glPushMatrix();
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        RenderHelper.enableGUIStandardItemLighting();
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glScalef(0.75f, 0.75f, 1.0f);
        int scaledX = (int) (x / 0.75f);
        int scaledY = (int) (y / 0.75f);
        itemRenderer.renderItemAndEffectIntoGUI(fontRendererObj, mc.getTextureManager(), stack, scaledX, scaledY);
        RenderHelper.disableStandardItemLighting();
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glPopMatrix();
    }
}
