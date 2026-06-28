package com.imgood.advancedatamonitor.gui.guiscreen;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
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
import com.imgood.advancedatamonitor.gui.manual.ManualSearchIndex;
import com.imgood.advancedatamonitor.gui.manual.ManualSearchUtil;
import com.imgood.advancedatamonitor.gui.manual.ManualTextHighlighter;
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
    private static final int SIDEBAR_SEARCH_HEIGHT = 14;
    private static final int SIDEBAR_SCROLL_BTN_HEIGHT = 7;
    private static final int SIDEBAR_TOP_PADDING = 2;
    private static final int SIDEBAR_BOTTOM_PADDING = 6;
    private static final int SIDEBAR_SCROLLBAR_WIDTH = 4;
    private static final int SEARCH_FIELD_COLOR = 0xFF0A1830;
    private static final int SEARCH_FIELD_BORDER = 0xFF3068A0;
    private static final int SEARCH_HINT_COLOR = 0xFF6080A0;
    private static final int SCROLL_BTN_COLOR = 0xFF1A3A5C;
    private static final int SCROLL_BTN_HOVER = 0xFF2A5080;
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
    private final List<Integer> visibleChapterIndices = new ArrayList<>();
    private final List<String> chapterSearchTexts = new ArrayList<>();
    private int selectedChapter = 0;
    private int currentPage = 0;
    private int configScrollOffset = 0;
    private int sidebarScrollOffset = 0;
    private boolean sidebarScrollbarDragging = false;
    private int sidebarDragStartMouseY = 0;
    private int sidebarDragStartOffset = 0;
    private String lastSearchQuery = "";

    private GuiTextField searchField;
    private GuiButton prevButton;
    private GuiButton nextButton;

    public GuiManual() {
        chapters = ManualDataLoader.loadChapters();
        rebuildChapterSearchTexts();
        rebuildVisibleChapters();
    }

    private void rebuildChapterSearchTexts() {
        chapterSearchTexts.clear();
        for (ManualChapter chapter : chapters) {
            chapterSearchTexts.add(ManualSearchIndex.buildChapterSearchText(chapter));
        }
    }

    private String getSearchQuery() {
        return searchField == null ? "" : searchField.getText();
    }

    private void rebuildVisibleChapters() {
        visibleChapterIndices.clear();
        String query = getSearchQuery();
        if (ManualSearchUtil.isQueryEmpty(query)) {
            for (int i = 0; i < chapters.size(); i++) {
                visibleChapterIndices.add(i);
            }
            return;
        }
        for (int i = 0; i < chapters.size(); i++) {
            String blob = i < chapterSearchTexts.size() ? chapterSearchTexts.get(i) : "";
            if (ManualSearchUtil.textMatches(blob, query)) {
                visibleChapterIndices.add(i);
            }
        }
    }

    private void onSearchChanged() {
        String query = getSearchQuery();
        if (query.equals(lastSearchQuery)) {
            return;
        }
        lastSearchQuery = query;
        rebuildVisibleChapters();
        sidebarScrollOffset = 0;
        if (!visibleChapterIndices.isEmpty() && !visibleChapterIndices.contains(selectedChapter)) {
            selectedChapter = visibleChapterIndices.get(0);
            currentPage = 0;
            configScrollOffset = 0;
        }
        ensureSidebarShowsChapter(selectedChapter);
        updateButtons();
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

        searchField = new GuiTextField(fontRendererObj, guiLeft + 3, guiTop + SIDEBAR_TOP_PADDING, SIDEBAR_WIDTH - 6, 12);
        searchField.setMaxStringLength(64);
        searchField.setEnableBackgroundDrawing(false);
        searchField.setText(lastSearchQuery);
        rebuildVisibleChapters();
        ensureSidebarShowsChapter(selectedChapter);
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

    private int getSidebarListTop() {
        return guiTop + SIDEBAR_TOP_PADDING + SIDEBAR_SEARCH_HEIGHT + 2;
    }

    private int getSidebarListHeight() {
        return GUI_HEIGHT - SIDEBAR_TOP_PADDING - SIDEBAR_SEARCH_HEIGHT - 2 - SIDEBAR_BOTTOM_PADDING;
    }

    private int getSidebarScrollTrackTop() {
        return getSidebarListTop() + SIDEBAR_SCROLL_BTN_HEIGHT;
    }

    private int getSidebarScrollTrackHeight() {
        return Math.max(0, getSidebarListHeight() - SIDEBAR_SCROLL_BTN_HEIGHT * 2);
    }

    private int getVisibleChapterCount() {
        return visibleChapterIndices.isEmpty() && !ManualSearchUtil.isQueryEmpty(getSearchQuery())
            ? 0
            : visibleChapterIndices.size();
    }

    private int getSidebarTotalHeight() {
        return getVisibleChapterCount() * SIDEBAR_ITEM_HEIGHT;
    }

    private boolean sidebarNeedsScroll() {
        return getSidebarTotalHeight() > getSidebarListHeight();
    }

    private int getSidebarMaxScroll() {
        return Math.max(0, getSidebarTotalHeight() - getSidebarListHeight());
    }

    private int getSidebarItemWidth() {
        int width = SIDEBAR_WIDTH - 4;
        if (sidebarNeedsScroll()) {
            width -= SIDEBAR_SCROLLBAR_WIDTH + 1;
        }
        return width;
    }

    private void clampSidebarScroll() {
        if (sidebarScrollOffset < 0) sidebarScrollOffset = 0;
        int max = getSidebarMaxScroll();
        if (sidebarScrollOffset > max) sidebarScrollOffset = max;
    }

    private void ensureSidebarShowsChapter(int chapterIndex) {
        if (chapterIndex < 0 || chapterIndex >= chapters.size()) return;
        int visibleIndex = visibleChapterIndices.indexOf(chapterIndex);
        if (visibleIndex < 0) return;
        int listHeight = getSidebarListHeight();
        int itemTop = visibleIndex * SIDEBAR_ITEM_HEIGHT;
        int itemBottom = itemTop + SIDEBAR_ITEM_HEIGHT;
        if (itemTop < sidebarScrollOffset) {
            sidebarScrollOffset = itemTop;
        } else if (itemBottom > sidebarScrollOffset + listHeight) {
            sidebarScrollOffset = itemBottom - listHeight;
        }
        clampSidebarScroll();
    }

    private boolean isMouseOverSidebarList(int mouseX, int mouseY) {
        return mouseX >= guiLeft
            && mouseX < guiLeft + SIDEBAR_WIDTH
            && mouseY >= getSidebarListTop()
            && mouseY < getSidebarListTop() + getSidebarListHeight();
    }

    private void enableScissor(int x, int y, int w, int h) {
        Minecraft mc = Minecraft.getMinecraft();
        int scaleFactor = new net.minecraft.client.gui.ScaledResolution(mc, mc.displayWidth, mc.displayHeight)
            .getScaleFactor();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(x * scaleFactor, mc.displayHeight - (y + h) * scaleFactor, w * scaleFactor, h * scaleFactor);
    }

    private void disableScissor() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawBackground();
        drawSearchField();
        drawSidebar(mouseX, mouseY);
        drawContent(mouseX, mouseY);
        drawPageCounter();
        if (searchField != null) {
            searchField.drawTextBox();
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawSearchField() {
        if (searchField == null) {
            return;
        }
        int x = guiLeft + 2;
        int y = guiTop + SIDEBAR_TOP_PADDING;
        int w = SIDEBAR_WIDTH - 4;
        drawRect(x, y, x + w, y + SIDEBAR_SEARCH_HEIGHT, SEARCH_FIELD_COLOR);
        drawRect(x, y, x + w, y + 1, SEARCH_FIELD_BORDER);
        drawRect(x, y + SIDEBAR_SEARCH_HEIGHT - 1, x + w, y + SIDEBAR_SEARCH_HEIGHT, SEARCH_FIELD_BORDER);
        drawRect(x, y, x + 1, y + SIDEBAR_SEARCH_HEIGHT, SEARCH_FIELD_BORDER);
        drawRect(x + w - 1, y, x + w, y + SIDEBAR_SEARCH_HEIGHT, SEARCH_FIELD_BORDER);
        if (searchField.getText()
            .isEmpty()
            && !searchField.isFocused()) {
            fontRendererObj.drawString(
                I18n.format("adm.manual.search_hint"),
                x + 4,
                y + 3,
                SEARCH_HINT_COLOR);
        }
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
        clampSidebarScroll();
        int listTop = getSidebarListTop();
        int listHeight = getSidebarListHeight();
        int itemWidth = getSidebarItemWidth();
        int x = guiLeft + 2;
        String query = getSearchQuery();

        GL11.glPushMatrix();
        enableScissor(guiLeft, listTop, SIDEBAR_WIDTH, listHeight);

        if (visibleChapterIndices.isEmpty() && !ManualSearchUtil.isQueryEmpty(query)) {
            fontRendererObj.drawString(I18n.format("adm.manual.search_no_results"), x + 2, listTop + 4, SIDEBAR_TEXT);
        } else {
            for (int vi = 0; vi < visibleChapterIndices.size(); vi++) {
                int chapterIndex = visibleChapterIndices.get(vi);
                ManualChapter chapter = chapters.get(chapterIndex);
                int y = listTop + vi * SIDEBAR_ITEM_HEIGHT - sidebarScrollOffset;

                boolean hovered = mouseX >= x && mouseX < x + itemWidth
                    && mouseY >= y
                    && mouseY < y + SIDEBAR_ITEM_HEIGHT;

                if (chapterIndex == selectedChapter) {
                    drawRect(x, y, x + itemWidth, y + SIDEBAR_ITEM_HEIGHT, SELECTED_COLOR);
                } else if (hovered) {
                    drawRect(x, y, x + itemWidth, y + SIDEBAR_ITEM_HEIGHT, HOVER_COLOR);
                }

                ItemStack icon = getIcon(chapter.icon);
                if (icon != null) {
                    renderSmallIcon(icon, x + 1, y + 3);
                }

                String name = I18n.format(chapter.chapterTitleKey);
                List<String> nameLines = fontRendererObj.listFormattedStringToWidth(name, itemWidth - 14);
                int textY = y + 2;
                int textColor = (chapterIndex == selectedChapter) ? SIDEBAR_TEXT_SELECTED : SIDEBAR_TEXT;
                for (String line : nameLines) {
                    ManualTextHighlighter.drawLine(fontRendererObj, line, x + 14, textY, query, textColor);
                    textY += 9;
                }
            }
        }

        disableScissor();
        GL11.glPopMatrix();

        if (sidebarNeedsScroll()) {
            drawSidebarScrollbar(listTop, listHeight, mouseX, mouseY);
        }
    }

    private int getSidebarScrollbarX() {
        return guiLeft + SIDEBAR_WIDTH - SIDEBAR_SCROLLBAR_WIDTH - 1;
    }

    private int getSidebarThumbHeight(int trackHeight) {
        int totalHeight = getSidebarTotalHeight();
        if (totalHeight <= 0) {
            return trackHeight;
        }
        return Math.max(8, trackHeight * trackHeight / totalHeight);
    }

    private int getSidebarThumbY(int trackTop, int trackHeight) {
        int maxScroll = getSidebarMaxScroll();
        int thumbH = getSidebarThumbHeight(trackHeight);
        if (maxScroll == 0) {
            return trackTop;
        }
        return trackTop + sidebarScrollOffset * (trackHeight - thumbH) / maxScroll;
    }

    private void drawSidebarScrollbar(int listTop, int listHeight, int mouseX, int mouseY) {
        int scrollbarX = getSidebarScrollbarX();
        int trackTop = getSidebarScrollTrackTop();
        int trackHeight = getSidebarScrollTrackHeight();
        int thumbH = getSidebarThumbHeight(trackHeight);
        int thumbY = getSidebarThumbY(trackTop, trackHeight);

        int upTop = listTop;
        int downTop = listTop + listHeight - SIDEBAR_SCROLL_BTN_HEIGHT;
        boolean upHover = isMouseOverScrollButton(mouseX, mouseY, upTop);
        boolean downHover = isMouseOverScrollButton(mouseX, mouseY, downTop);
        drawScrollButton(scrollbarX, upTop, upHover, true);
        drawScrollButton(scrollbarX, downTop, downHover, false);

        drawRect(scrollbarX, trackTop, scrollbarX + SIDEBAR_SCROLLBAR_WIDTH, trackTop + trackHeight, 0x60204060);
        drawRect(scrollbarX, thumbY, scrollbarX + SIDEBAR_SCROLLBAR_WIDTH, thumbY + thumbH, 0xFF3080B0);
    }

    private void drawScrollButton(int x, int y, boolean hovered, boolean up) {
        drawRect(x, y, x + SIDEBAR_SCROLLBAR_WIDTH, y + SIDEBAR_SCROLL_BTN_HEIGHT, hovered ? SCROLL_BTN_HOVER : SCROLL_BTN_COLOR);
        String label = up ? "^" : "v";
        int labelX = x + (SIDEBAR_SCROLLBAR_WIDTH - fontRendererObj.getStringWidth(label)) / 2;
        int labelY = y + 1;
        fontRendererObj.drawString(label, labelX, labelY, 0xFFC8E0FF);
    }

    private boolean isMouseOverScrollButton(int mouseX, int mouseY, int buttonTop) {
        int scrollbarX = getSidebarScrollbarX();
        return mouseX >= scrollbarX
            && mouseX < scrollbarX + SIDEBAR_SCROLLBAR_WIDTH
            && mouseY >= buttonTop
            && mouseY < buttonTop + SIDEBAR_SCROLL_BTN_HEIGHT;
    }

    private int getSidebarScrollButtonTop(boolean up) {
        int listTop = getSidebarListTop();
        int listHeight = getSidebarListHeight();
        return up ? listTop : listTop + listHeight - SIDEBAR_SCROLL_BTN_HEIGHT;
    }

    private boolean isMouseOverSidebarScrollbar(int mouseX, int mouseY) {
        if (!sidebarNeedsScroll()) {
            return false;
        }
        int trackTop = getSidebarScrollTrackTop();
        int trackHeight = getSidebarScrollTrackHeight();
        int scrollbarX = getSidebarScrollbarX();
        return mouseX >= scrollbarX
            && mouseX < scrollbarX + SIDEBAR_SCROLLBAR_WIDTH
            && mouseY >= trackTop
            && mouseY < trackTop + trackHeight;
    }

    private void setSidebarScrollFromThumbDrag(int mouseY) {
        int trackTop = getSidebarScrollTrackTop();
        int trackHeight = getSidebarScrollTrackHeight();
        int thumbH = getSidebarThumbHeight(trackHeight);
        int maxScroll = getSidebarMaxScroll();
        int movable = trackHeight - thumbH;
        if (movable <= 0 || maxScroll <= 0) {
            sidebarScrollOffset = 0;
            return;
        }
        int deltaY = mouseY - sidebarDragStartMouseY;
        int scrollDelta = deltaY * maxScroll / movable;
        sidebarScrollOffset = sidebarDragStartOffset + scrollDelta;
        clampSidebarScroll();
    }

    private void scrollSidebarToEnd(boolean top) {
        sidebarScrollOffset = top ? 0 : getSidebarMaxScroll();
        clampSidebarScroll();
    }

    private void drawContent(int mouseX, int mouseY) {
        ManualChapter chapter = getCurrentChapter();
        ManualPage page = getCurrentPage();
        if (chapter == null || page == null) return;

        int cx = guiLeft + CONTENT_X + 4;
        int cy = guiTop + CONTENT_TOP;
        String query = getSearchQuery();

        if (page.isTextPage()) {
            ManualPageRenderer
                .renderTextPage(fontRendererObj, page, cx, cy, CONTENT_WIDTH - 8, CONTENT_HEIGHT_VAL, query);
        } else if (page.isItemShowcase()) {
            ManualPageRenderer
                .renderItemShowcase(fontRendererObj, page, cx, cy, CONTENT_WIDTH - 8, CONTENT_HEIGHT_VAL, query);
        } else if (page.isConfigRef()) {
            renderConfigRefWithScroll(page, cx, cy, query);
        }
    }

    private void renderConfigRefWithScroll(ManualPage page, int x, int y, String searchQuery) {
        GL11.glPushMatrix();
        enableScissor(guiLeft + CONTENT_X + 4, guiTop + CONTENT_TOP, CONTENT_WIDTH - 8, CONTENT_HEIGHT_VAL);
        GL11.glTranslatef(0, -configScrollOffset, 0);
        ManualPageRenderer.renderConfigRef(
            fontRendererObj,
            page,
            x,
            y,
            CONTENT_WIDTH - 8,
            CONTENT_HEIGHT_VAL + configScrollOffset,
            searchQuery);
        GL11.glTranslatef(0, configScrollOffset, 0);
        disableScissor();
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
        int titleX = guiLeft + CONTENT_X + (CONTENT_WIDTH - titleWidth) / 2;
        ManualTextHighlighter
            .drawLine(fontRendererObj, chapterTitle, titleX, guiTop + 5, getSearchQuery(), CHAPTER_TITLE_COLOR);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        if (searchField != null) {
            searchField.mouseClicked(mouseX, mouseY, button);
        }

        super.mouseClicked(mouseX, mouseY, button);

        if (button == 0 && sidebarNeedsScroll()) {
            if (isMouseOverScrollButton(mouseX, mouseY, getSidebarScrollButtonTop(true))) {
                scrollSidebarToEnd(true);
                return;
            }
            if (isMouseOverScrollButton(mouseX, mouseY, getSidebarScrollButtonTop(false))) {
                scrollSidebarToEnd(false);
                return;
            }
        }

        if (button == 0 && isMouseOverSidebarScrollbar(mouseX, mouseY)) {
            int trackTop = getSidebarScrollTrackTop();
            int trackHeight = getSidebarScrollTrackHeight();
            int thumbY = getSidebarThumbY(trackTop, trackHeight);
            int thumbH = getSidebarThumbHeight(trackHeight);
            if (mouseY >= thumbY && mouseY < thumbY + thumbH) {
                sidebarScrollbarDragging = true;
                sidebarDragStartMouseY = mouseY;
                sidebarDragStartOffset = sidebarScrollOffset;
            } else {
                int maxScroll = getSidebarMaxScroll();
                int movable = trackHeight - thumbH;
                if (movable > 0 && maxScroll > 0) {
                    int targetThumbY = mouseY - thumbH / 2;
                    if (targetThumbY < trackTop) {
                        targetThumbY = trackTop;
                    } else if (targetThumbY > trackTop + movable) {
                        targetThumbY = trackTop + movable;
                    }
                    sidebarScrollOffset = (targetThumbY - trackTop) * maxScroll / movable;
                    clampSidebarScroll();
                }
            }
            return;
        }

        if (button == 0 && isMouseOverSidebarList(mouseX, mouseY)) {
            int listTop = getSidebarListTop();
            int visibleIndex = (mouseY - listTop + sidebarScrollOffset) / SIDEBAR_ITEM_HEIGHT;
            if (visibleIndex >= 0 && visibleIndex < visibleChapterIndices.size()) {
                int chapterIndex = visibleChapterIndices.get(visibleIndex);
                int y = listTop + visibleIndex * SIDEBAR_ITEM_HEIGHT - sidebarScrollOffset;
                int x = guiLeft + 2;
                if (mouseX >= x && mouseX < x + getSidebarItemWidth()
                    && mouseY >= y
                    && mouseY < y + SIDEBAR_ITEM_HEIGHT) {
                    if (chapterIndex != selectedChapter) {
                        selectedChapter = chapterIndex;
                        currentPage = 0;
                        configScrollOffset = 0;
                        ensureSidebarShowsChapter(selectedChapter);
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

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
        if (sidebarScrollbarDragging && clickedMouseButton == 0) {
            setSidebarScrollFromThumbDrag(mouseY);
        }
    }

    @Override
    protected void mouseMovedOrUp(int mouseX, int mouseY, int state) {
        super.mouseMovedOrUp(mouseX, mouseY, state);
        if (state == 0) {
            sidebarScrollbarDragging = false;
        }
    }

    private void goToPrevPage() {
        if (currentPage > 0) {
            currentPage--;
        } else if (selectedChapter > 0) {
            selectedChapter--;
            currentPage = chapters.get(selectedChapter)
                .getPageCount() - 1;
            ensureSidebarShowsChapter(selectedChapter);
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
            ensureSidebarShowsChapter(selectedChapter);
        }
        configScrollOffset = 0;
        updateButtons();
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) {
            return;
        }

        int mouseX = Mouse.getEventX() * width / mc.displayWidth;
        int mouseY = height - Mouse.getEventY() * height / mc.displayHeight - 1;

        if (isMouseOverSidebarList(mouseX, mouseY) && sidebarNeedsScroll()) {
            if (wheel > 0) {
                sidebarScrollOffset = Math.max(0, sidebarScrollOffset - SIDEBAR_ITEM_HEIGHT);
            } else {
                sidebarScrollOffset = Math.min(getSidebarMaxScroll(), sidebarScrollOffset + SIDEBAR_ITEM_HEIGHT);
            }
            return;
        }

        ManualPage page = getCurrentPage();
        if (page != null && page.isConfigRef()) {
            configScrollOffset += wheel / 10;
            if (configScrollOffset < 0) configScrollOffset = 0;
            if (configScrollOffset > 800) configScrollOffset = 800;
        }
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (searchField != null) {
            searchField.updateCursorCounter();
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (searchField != null && searchField.isFocused()) {
            if (keyCode == 1) {
                searchField.setFocused(false);
                return;
            }
            if (searchField.textboxKeyTyped(typedChar, keyCode)) {
                onSearchChanged();
            }
            return;
        }

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
