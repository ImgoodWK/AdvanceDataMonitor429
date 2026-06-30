package com.imgood.textech.gui.guiscreen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.gui.custom.ADM_GuiButton;
import com.imgood.textech.gui.custom.ADM_GuiScreen;
import com.imgood.textech.gui.custom.ADM_GuiTextField;
import com.imgood.textech.items.ItemAdvancePlanner;
import com.imgood.textech.items.PlannerEntry;

/**
 * Display names / 显示名称:
 * - EN: Advance Planner
 * - ZH: 高级计划器
 * Lang keys: adm.planner.title
 */
public class GuiAdvancePlanner extends ADM_GuiScreen {

    private final ItemStack plannerStack;
    private final EntityPlayer player;

    private List<PlannerEntry> entries;
    private Map<Integer, PlannerEntry> entryMap;

    private int scrollOffset = 0;
    private int minRowHeight = 20;
    private int textLineHeight = 10;
    private int listStartY;
    private int listStartX;
    private int listWidth;
    private int visibleAreaHeight;

    // Bottom edit panel fields
    private ADM_GuiTextField editingField;
    private int selectedSlotIndex = -1;
    private boolean isAddingNew = false;

    private ADM_GuiTextField titleField;

    private static final ResourceLocation BACKGROUND_TEXTURE = new ResourceLocation(
        AdvanceDataMonitor.MODID,
        "textures/gui/background_ADM_Sub.png");
    private static final ResourceLocation BUTTON_TEXTURE = new ResourceLocation(
        AdvanceDataMonitor.MODID,
        "textures/gui/button_ADM.png");
    private static final ResourceLocation BUTTON_HOVER_TEXTURE = new ResourceLocation(
        AdvanceDataMonitor.MODID,
        "textures/gui/button_hover_ADM.png");
    private static final ResourceLocation TEXTFIELD_TEXTURE = new ResourceLocation(
        AdvanceDataMonitor.MODID,
        "textures/gui/textfield_ADM_8020.png");
    private static final ResourceLocation TEXTFIELD_HOVER_TEXTURE = new ResourceLocation(
        AdvanceDataMonitor.MODID,
        "textures/gui/textfield_hover_ADM_8020.png");

    private int textColor = 0x00FFFF;
    private int textHoverColor = 0x0055FF;
    private int completedColor = 0x55FF55;
    private int pendingColor = 0xFFFFFF;
    private int slotNumberColor = 0x888888;
    private int timestampColor = 0x666666;
    private int selectedBgColor = 0x4000AAFF;

    private int buttonMergeId = 100;
    private int buttonExitId = 101;
    private int buttonDeleteId = 102;
    private int buttonAddNewId = 103;
    private int buttonHudToggleId = 104;
    private int buttonHudConfigId = 105;

    public GuiAdvancePlanner(ItemStack plannerStack, EntityPlayer player) {
        this.plannerStack = plannerStack;
        this.player = player;
        this.setBackgroundTexture(BACKGROUND_TEXTURE);
        this.setSize(430, 330);
        this.setStretch(false);
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        refreshEntries();

        this.setPosition((this.width - 400) / 2, (this.height - 300) / 2);

        this.listStartX = (this.width / 2) - 170;
        this.listStartY = (this.height / 2) - 100;
        this.listWidth = 340;
        this.visibleAreaHeight = 7 * minRowHeight;

        // Title editing field
        String currentTitle = ItemAdvancePlanner.getHudTitle(plannerStack);
        int titleFieldWidth = 200;
        int titleFieldX = (this.width / 2) - (titleFieldWidth / 2);
        int titleFieldY = this.listStartY - 30;
        titleField = new ADM_GuiTextField(this.fontRendererObj, titleFieldX, titleFieldY - 20, titleFieldWidth, 16);
        titleField.setMaxStringLength(32);
        titleField.setText(currentTitle);
        titleField.setBackgroundTexture(TEXTFIELD_TEXTURE);
        titleField.setFocusedBackgroundTexture(TEXTFIELD_HOVER_TEXTURE);

        this.buttonList.clear();

        this.buttonList.add(
            new ADM_GuiButton(
                buttonMergeId,
                this.width / 2 - 170,
                this.height / 2 + 110,
                60,
                20,
                I18n.format("adm.planner.merge")).setTexture(BUTTON_TEXTURE)
                    .setHoverTexture(BUTTON_HOVER_TEXTURE)
                    .setUseHoverEffect(true)
                    .setTextColor(textColor)
                    .setTextHoverColor(textHoverColor));

        this.buttonList.add(
            new ADM_GuiButton(
                buttonHudToggleId,
                this.width / 2 - 100,
                this.height / 2 + 110,
                60,
                20,
                getHudToggleLabel()).setTexture(BUTTON_TEXTURE)
                    .setHoverTexture(BUTTON_HOVER_TEXTURE)
                    .setUseHoverEffect(true)
                    .setTextColor(ItemAdvancePlanner.isHudEnabled(plannerStack) ? 0x00FF00 : 0xFF5555)
                    .setTextHoverColor(textHoverColor));

        this.buttonList.add(
            new ADM_GuiButton(
                buttonHudConfigId,
                this.width / 2 - 30,
                this.height / 2 + 110,
                70,
                20,
                I18n.format("adm.planner.hud_config")).setTexture(BUTTON_TEXTURE)
                    .setHoverTexture(BUTTON_HOVER_TEXTURE)
                    .setUseHoverEffect(true)
                    .setTextColor(textColor)
                    .setTextHoverColor(textHoverColor));

        this.buttonList.add(
            new ADM_GuiButton(
                buttonExitId,
                this.width / 2 + 50,
                this.height / 2 + 110,
                60,
                20,
                I18n.format("adm.planner.exit")).setTexture(BUTTON_TEXTURE)
                    .setHoverTexture(BUTTON_HOVER_TEXTURE)
                    .setUseHoverEffect(true)
                    .setTextColor(textColor)
                    .setTextHoverColor(textHoverColor));

        updateDynamicButtons();
        updateScreen();
    }

    private void updateDynamicButtons() {
        this.buttonList.removeIf(b -> b.id == buttonDeleteId || b.id == buttonAddNewId);

        if (selectedSlotIndex >= 0) {
            if (isAddingNew) {
                this.buttonList.add(
                    new ADM_GuiButton(
                        buttonAddNewId,
                        listStartX + listWidth - 62,
                        listStartY + visibleAreaHeight + 48,
                        58,
                        18,
                        I18n.format("adm.planner.add")).setTexture(BUTTON_TEXTURE)
                            .setHoverTexture(BUTTON_HOVER_TEXTURE)
                            .setUseHoverEffect(true)
                            .setTextColor(0x00FF00)
                            .setTextHoverColor(0x55FF55));
            } else {
                this.buttonList.add(
                    new ADM_GuiButton(
                        buttonDeleteId,
                        listStartX + listWidth - 62,
                        listStartY + visibleAreaHeight + 48,
                        58,
                        18,
                        I18n.format("adm.planner.delete")).setTexture(BUTTON_TEXTURE)
                            .setHoverTexture(BUTTON_HOVER_TEXTURE)
                            .setUseHoverEffect(true)
                            .setTextColor(0xFF5555)
                            .setTextHoverColor(0xFF0000));
            }
        }
    }

    private String getHudToggleLabel() {
        return ItemAdvancePlanner.isHudEnabled(plannerStack) ? I18n.format("adm.planner.hud_enabled")
            : I18n.format("adm.planner.hud_disabled");
    }

    private void refreshEntries() {
        this.entries = ItemAdvancePlanner.getAllEntries(plannerStack);
        this.entryMap = new HashMap<>();
        for (PlannerEntry entry : entries) {
            entryMap.put(entry.getSlotIndex(), entry);
        }
    }

    private List<PlannerEntry> getSortedEntries() {
        return ItemAdvancePlanner.getEntriesSorted(plannerStack, com.imgood.textech.items.PlannerMergeMode.BY_INDEX);
    }

    private int computeRowHeight(PlannerEntry entry) {
        if (entry == null || entry.getText() == null
            || entry.getText()
                .isEmpty()) {
            return minRowHeight;
        }
        int textMaxWidth = listWidth - 135;
        List<String> lines = this.fontRendererObj.listFormattedStringToWidth(entry.getText(), textMaxWidth);
        int numLines = lines.size();
        if (numLines <= 1) return minRowHeight;
        return minRowHeight + (numLines - 1) * textLineHeight;
    }

    private int computeTotalContentHeight(List<PlannerEntry> sortedEntries) {
        int total = 0;
        for (PlannerEntry entry : sortedEntries) {
            total += computeRowHeight(entry);
        }
        total += minRowHeight;
        return total;
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == buttonMergeId) {
            commitEdit();
            this.mc.displayGuiScreen(new GuiPlannerMergeConfirm(plannerStack, player));
        } else if (button.id == buttonExitId) {
            commitEdit();
            this.mc.displayGuiScreen(null);
        } else if (button.id == buttonDeleteId) {
            deleteEntry();
        } else if (button.id == buttonAddNewId) {
            commitEdit();
        } else if (button.id == buttonHudToggleId) {
            commitEdit();
            boolean current = ItemAdvancePlanner.isHudEnabled(plannerStack);
            ItemAdvancePlanner.setHudEnabled(plannerStack, !current);
            ItemAdvancePlanner.syncPlannerToServer(player, plannerStack);
            initGui();
        } else if (button.id == buttonHudConfigId) {
            commitEdit();
            this.mc.displayGuiScreen(new GuiPlannerHudConfig(plannerStack, player));
        }
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int scrollDelta = Mouse.getEventDWheel();
        if (scrollDelta != 0) {
            List<PlannerEntry> sorted = getSortedEntries();
            int totalHeight = computeTotalContentHeight(sorted);
            int maxScroll = Math.max(0, totalHeight - visibleAreaHeight);
            if (scrollDelta > 0) {
                scrollOffset = Math.max(0, scrollOffset - 20);
            } else {
                scrollOffset = Math.min(maxScroll, scrollOffset + 20);
            }
        }
    }

    /**
     * Find which entry is at a given mouseY within the list area.
     * Returns an array of [slotIndex, rowY, rowHeight] or null if none.
     */
    private int[] getEntryAtMouseY(int mouseY) {
        List<PlannerEntry> sorted = getSortedEntries();
        int currentY = listStartY - scrollOffset;
        for (PlannerEntry entry : sorted) {
            int rowH = computeRowHeight(entry);
            if (mouseY >= currentY && mouseY < currentY + rowH
                && currentY + rowH > listStartY
                && currentY < listStartY + visibleAreaHeight) {
                return new int[] { entry.getSlotIndex(), currentY, rowH };
            }
            currentY += rowH;
        }
        return null;
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        boolean wasEditing = (selectedSlotIndex >= 0);
        super.mouseClicked(mouseX, mouseY, mouseButton);

        if (titleField != null) {
            boolean wasFocused = titleField.isFocused();
            titleField.mouseClicked(mouseX, mouseY, mouseButton);
            if (titleField.isFocused() && !wasFocused) {
                if (selectedSlotIndex >= 0) {
                    commitEdit();
                }
            }
            if (!titleField.isFocused() && wasFocused) {
                commitTitle();
            }
        }

        if (editingField != null) {
            editingField.mouseClicked(mouseX, mouseY, mouseButton);
        }

        if (mouseButton == 0) {
            // Handle scrollbar top/bottom jump buttons
            List<PlannerEntry> sorted = getSortedEntries();
            int totalContentHeight = computeTotalContentHeight(sorted);
            int maxScroll = Math.max(0, totalContentHeight - visibleAreaHeight);
            int scrollbarX = listStartX + listWidth + 4;
            int scrollbarH = visibleAreaHeight;
            int btnTopH = 8;

            if (totalContentHeight > visibleAreaHeight) {
                // Top jump button
                if (mouseX >= scrollbarX && mouseX <= scrollbarX + 6
                    && mouseY >= listStartY
                    && mouseY <= listStartY + btnTopH) {
                    scrollOffset = 0;
                    return;
                }
                // Bottom jump button
                if (mouseX >= scrollbarX && mouseX <= scrollbarX + 6
                    && mouseY >= listStartY + scrollbarH - btnTopH
                    && mouseY <= listStartY + scrollbarH) {
                    scrollOffset = maxScroll;
                    return;
                }
            }

            if (isMouseInListArea(mouseX, mouseY)) {
                if (titleField != null && titleField.isFocused()) {
                    commitTitle();
                    titleField.setFocused(false);
                }

                int[] entryInfo = getEntryAtMouseY(mouseY);
                if (entryInfo == null) {
                    // Check if click is on the "add new" row
                    int addRowY = listStartY - scrollOffset + totalContentHeight - minRowHeight;
                    int addRowH = minRowHeight;
                    if (mouseY >= addRowY && mouseY < addRowY + addRowH
                        && addRowY + addRowH > listStartY
                        && addRowY < listStartY + visibleAreaHeight) {
                        commitEdit();
                        int nextSlot = ItemAdvancePlanner.getNextSlotIndex(plannerStack);
                        startEditingNew(nextSlot);
                        return;
                    }
                    return;
                }

                int slotIndex = entryInfo[0];
                int rowY = entryInfo[1];
                int rowH = entryInfo[2];

                int arrowX = listStartX + listWidth - 18;
                int arrowWidth = 14;

                // Check arrow button click
                if (mouseX >= arrowX && mouseX <= arrowX + arrowWidth && mouseY >= rowY + 3 && mouseY <= rowY + 15) {
                    commitEdit();
                    boolean isShiftDown = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)
                        || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
                    if (isShiftDown) {
                        ItemAdvancePlanner.moveEntryToTop(plannerStack, slotIndex);
                    } else {
                        if (slotIndex > 1) {
                            ItemAdvancePlanner.swapEntries(plannerStack, slotIndex, slotIndex - 1);
                        }
                    }
                    ItemAdvancePlanner.syncPlannerToServer(player, plannerStack);
                    refreshEntries();
                    // Re-select the entry if it was moved
                    if (wasEditing) {
                        reselectEntryBySlot(slotIndex);
                    }
                    return;
                }

                int checkboxX = listStartX + 5;
                int checkboxWidth = 12;

                if (mouseX >= checkboxX && mouseX <= checkboxX + checkboxWidth
                    && mouseY >= rowY + 3
                    && mouseY <= rowY + 15) {
                    commitEdit();
                    ItemAdvancePlanner.toggleCompleted(plannerStack, slotIndex);
                    ItemAdvancePlanner.syncPlannerToServer(player, plannerStack);
                    refreshEntries();
                    if (wasEditing) {
                        reselectEntryBySlot(slotIndex);
                    }
                    return;
                }

                int textAreaX = listStartX + 50;
                int textAreaWidth = listWidth - 105;
                if (mouseX >= textAreaX && mouseX <= textAreaX + textAreaWidth) {
                    if (selectedSlotIndex >= 0 && selectedSlotIndex != slotIndex) {
                        commitEdit();
                    }
                    startEditingExisting(slotIndex);
                }
            }
        }
    }

    private void reselectEntryBySlot(int originalSlot) {
        PlannerEntry entry = entryMap.get(originalSlot);
        if (entry != null) {
            selectedSlotIndex = originalSlot;
            isAddingNew = false;
            ensureBottomEditingField();
            if (editingField != null) {
                editingField.setText(entry.getText());
                editingField.setFocused(true);
            }
            updateDynamicButtons();
        }
    }

    private boolean isMouseInListArea(int mouseX, int mouseY) {
        return mouseX >= listStartX && mouseX <= listStartX + listWidth
            && mouseY >= listStartY
            && mouseY <= listStartY + visibleAreaHeight;
    }

    private void startEditingExisting(int slotIndex) {
        selectedSlotIndex = slotIndex;
        isAddingNew = false;
        PlannerEntry existing = entryMap.get(slotIndex);
        String initialText = existing != null ? existing.getText() : "";

        ensureBottomEditingField();
        if (editingField != null) {
            editingField.setText(initialText);
            editingField.setFocused(true);
            editingField.setCursorPositionEnd();
        }
        updateDynamicButtons();
    }

    private void startEditingNew(int nextSlot) {
        selectedSlotIndex = nextSlot;
        isAddingNew = true;

        ensureBottomEditingField();
        if (editingField != null) {
            editingField.setText("");
            editingField.setFocused(true);
        }
        updateDynamicButtons();
    }

    private void ensureBottomEditingField() {
        int editFieldX = listStartX + 50;
        int editFieldY = listStartY + visibleAreaHeight + 28;
        int editFieldWidth = listWidth - 105;

        if (editingField == null) {
            editingField = new ADM_GuiTextField(this.fontRendererObj, editFieldX, editFieldY, editFieldWidth, 16);
            editingField.setMaxStringLength(256);
            editingField.setBackgroundTexture(TEXTFIELD_TEXTURE);
            editingField.setFocusedBackgroundTexture(TEXTFIELD_HOVER_TEXTURE);
            editingField.setHintText(I18n.format("adm.planner.edit_hint"));
        }
    }

    private void commitEdit() {
        if (editingField != null && selectedSlotIndex >= 0) {
            String text = editingField.getText()
                .trim();
            if (isAddingNew) {
                if (!text.isEmpty()) {
                    ItemAdvancePlanner.addEntry(plannerStack, text);
                    ItemAdvancePlanner.syncPlannerToServer(player, plannerStack);
                    refreshEntries();
                }
                // After adding, deselect so user must click "add new" row again
                selectedSlotIndex = -1;
                editingField = null;
                isAddingNew = false;
                updateDynamicButtons();
            } else {
                PlannerEntry existing = entryMap.get(selectedSlotIndex);
                if (existing != null) {
                    existing.setText(text);
                    ItemAdvancePlanner.setEntry(plannerStack, selectedSlotIndex, text, existing.isCompleted());
                    ItemAdvancePlanner.syncPlannerToServer(player, plannerStack);
                    refreshEntries();
                }
            }
        }
    }

    private void deleteEntry() {
        if (selectedSlotIndex >= 0 && !isAddingNew) {
            ItemAdvancePlanner.removeEntry(plannerStack, selectedSlotIndex);
            ItemAdvancePlanner.syncPlannerToServer(player, plannerStack);
            refreshEntries();
            selectedSlotIndex = -1;
            editingField = null;
            isAddingNew = false;
            updateDynamicButtons();
        }
    }

    private void clearSelection() {
        selectedSlotIndex = -1;
        editingField = null;
        isAddingNew = false;
        updateDynamicButtons();
    }

    private void commitTitle() {
        if (titleField != null) {
            String newTitle = titleField.getText()
                .trim();
            ItemAdvancePlanner.setHudTitle(plannerStack, newTitle);
            ItemAdvancePlanner.syncPlannerToServer(player, plannerStack);
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (titleField != null && titleField.isFocused()) {
            if (keyCode == Keyboard.KEY_RETURN) {
                commitTitle();
                titleField.setFocused(false);
                return;
            } else if (keyCode == Keyboard.KEY_ESCAPE) {
                titleField.setFocused(false);
                return;
            }
            titleField.textboxKeyTyped(typedChar, keyCode);
            return;
        }

        if (editingField != null && editingField.isFocused()) {
            if (keyCode == Keyboard.KEY_RETURN) {
                commitEdit();
                return;
            } else if (keyCode == Keyboard.KEY_ESCAPE) {
                clearSelection();
                return;
            }
            editingField.textboxKeyTyped(typedChar, keyCode);
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (editingField != null) {
            editingField.updateCursorCounter();
        }
        if (titleField != null) {
            titleField.updateCursorCounter();
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();

        // Title label
        this.fontRendererObj.drawString(
            I18n.format("adm.planner.custom_title"),
            this.width / 2 - 160,
            this.listStartY - 50,
            slotNumberColor);

        // Title editing field
        if (titleField != null) {
            titleField.drawTextBox();
            if (!titleField.isFocused() && titleField.getText()
                .isEmpty()) {
                this.fontRendererObj.drawString(
                    I18n.format("adm.planner.title_hint"),
                    titleField.xPosition + 4,
                    titleField.yPosition + 4,
                    0x444444);
            }
        }

        int entryCount = ItemAdvancePlanner.getEntryCount(plannerStack);
        int completedCount = ItemAdvancePlanner.getCompletedCount(plannerStack);
        String stats = I18n.format("adm.planner.stats", entryCount, completedCount, entryCount - completedCount);
        this.fontRendererObj.drawString(stats, listStartX, listStartY - 8, slotNumberColor);

        drawListArea(mouseX, mouseY);
        drawBottomEditPanel(mouseX, mouseY);

        super.drawScreen(mouseX, mouseY, partialTicks);

        if (editingField != null) {
            editingField.drawTextBox();
        }

        drawTooltip(mouseX, mouseY);
    }

    private void drawBottomEditPanel(int mouseX, int mouseY) {
        if (selectedSlotIndex < 0) return;

        int panelY = listStartY + visibleAreaHeight + 2;
        int panelHeight = 62;

        // Panel background
        drawRect(listStartX - 2, panelY, listStartX + listWidth + 2, panelY + panelHeight, 0x80002020);

        // Divider line between list and edit panel
        drawRect(listStartX - 2, panelY, listStartX + listWidth + 2, panelY + 1, 0xFF00CCCC);

        // Label
        String label = isAddingNew ? I18n.format("adm.planner.new_entry") : I18n.format("adm.planner.editing_entry");
        this.fontRendererObj.drawString(label, listStartX + 4, panelY + 4, textColor);

        // Preview of selected entry text
        if (!isAddingNew) {
            PlannerEntry selected = entryMap.get(selectedSlotIndex);
            if (selected != null && selected.getText() != null
                && !selected.getText()
                    .isEmpty()) {
                String previewLabel = I18n.format("adm.planner.preview") + " ";
                int labelWidth = this.fontRendererObj.getStringWidth(previewLabel);
                int textMaxWidth = listWidth - labelWidth - 12;
                String previewText = this.fontRendererObj.trimStringToWidth(selected.getText(), textMaxWidth);
                this.fontRendererObj.drawString(previewLabel, listStartX + 4, panelY + 14, slotNumberColor);
                this.fontRendererObj.drawString(previewText, listStartX + 4 + labelWidth, panelY + 14, pendingColor);
            }
        }

        if (editingField != null && !isAddingNew) {
            // Ensure field position is correct when drawing
            ensureBottomEditingField();
        }
    }

    private void drawListArea(int mouseX, int mouseY) {
        List<PlannerEntry> sortedEntries = getSortedEntries();
        int totalContentHeight = computeTotalContentHeight(sortedEntries);

        // Clamp scroll
        int maxScroll = Math.max(0, totalContentHeight - visibleAreaHeight);
        if (scrollOffset > maxScroll) {
            scrollOffset = maxScroll;
        }

        // Enable GL scissor to clip list content within the visible area
        ScaledResolution sr = new ScaledResolution(this.mc, this.mc.displayWidth, this.mc.displayHeight);
        int scaleFactor = sr.getScaleFactor();
        int scissorX = (listStartX - 2) * scaleFactor;
        int scissorY = this.mc.displayHeight - (listStartY + visibleAreaHeight + 2) * scaleFactor;
        int scissorW = (listWidth + 14) * scaleFactor;
        int scissorH = (visibleAreaHeight + 4) * scaleFactor;
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(scissorX, scissorY, scissorW, scissorH);

        // Border
        drawRect(
            listStartX - 2,
            listStartY - 2,
            listStartX + listWidth + 12,
            listStartY + visibleAreaHeight + 2,
            0x80000000);

        int textMaxWidth = listWidth - 135;
        int currentY = listStartY - scrollOffset;
        int rowIndex = 0;

        for (PlannerEntry entry : sortedEntries) {
            int rowH = computeRowHeight(entry);

            // Skip rows entirely above the visible area
            if (currentY + rowH <= listStartY) {
                currentY += rowH;
                continue;
            }
            // Stop if we've passed the visible area
            if (currentY >= listStartY + visibleAreaHeight) {
                break;
            }

            // Clipped row background
            int clipTop = Math.max(currentY, listStartY);
            int clipBottom = Math.min(currentY + rowH, listStartY + visibleAreaHeight);

            boolean isSelected = (selectedSlotIndex == entry.getSlotIndex());

            if (isSelected) {
                drawRect(listStartX, clipTop, listStartX + listWidth, clipBottom, selectedBgColor);
            } else if (rowIndex % 2 == 0) {
                drawRect(listStartX, clipTop, listStartX + listWidth, clipBottom, 0x30004040);
            } else {
                drawRect(listStartX, clipTop, listStartX + listWidth, clipBottom, 0x20002020);
            }

            // Checkbox (always at top of row)
            int checkboxX = listStartX + 5;
            int checkboxY = currentY + 3;
            boolean isChecked = entry.isCompleted();

            drawRect(checkboxX, checkboxY, checkboxX + 12, checkboxY + 12, 0xFF333333);
            drawRect(checkboxX + 1, checkboxY + 1, checkboxX + 11, checkboxY + 11, 0xFF000000);

            boolean hoveringCheckbox = mouseX >= checkboxX && mouseX <= checkboxX + 12
                && mouseY >= checkboxY
                && mouseY <= checkboxY + 12;
            if (hoveringCheckbox) {
                drawRect(checkboxX, checkboxY, checkboxX + 12, checkboxY + 12, 0x40FFFFFF);
            }

            if (isChecked) {
                String checkSymbol = "x";
                int checkWidth = this.fontRendererObj.getStringWidth(checkSymbol);
                this.fontRendererObj.drawStringWithShadow(
                    checkSymbol,
                    checkboxX + (12 - checkWidth) / 2,
                    checkboxY + 2,
                    completedColor);
            }

            // Slot number
            String slotNum = String.format("#%-3d", entry.getSlotIndex());
            this.fontRendererObj.drawString(slotNum, listStartX + 20, currentY + 6, slotNumberColor);

            // Arrow button (at top of row)
            int arrowX = listStartX + listWidth - 18;
            int arrowY = currentY + 3;
            int arrowWidth = 14;
            int arrowHeight = 12;
            boolean hoveringArrow = mouseX >= arrowX && mouseX <= arrowX + arrowWidth
                && mouseY >= arrowY
                && mouseY <= arrowY + arrowHeight;
            int arrowBgColor = hoveringArrow ? 0x6000CCCC : 0x40004040;
            int arrowTextColor = hoveringArrow ? 0x00FFFF : 0x00AAAA;

            drawRect(arrowX, arrowY, arrowX + arrowWidth, arrowY + arrowHeight, 0xFF333333);
            drawRect(arrowX + 1, arrowY + 1, arrowX + arrowWidth - 1, arrowY + arrowHeight - 1, arrowBgColor);
            String arrowSymbol = "\u2191";
            int arrowSymWidth = this.fontRendererObj.getStringWidth(arrowSymbol);
            this.fontRendererObj.drawStringWithShadow(
                arrowSymbol,
                arrowX + (arrowWidth - arrowSymWidth) / 2,
                arrowY + 2,
                arrowTextColor);

            // Text content with wrapping - always preview mode (no inline editing)
            int textX = listStartX + 50;
            if (entry.getText() != null && !entry.getText()
                .isEmpty()) {
                int textColorFinal = isChecked ? completedColor : pendingColor;
                List<String> lines = this.fontRendererObj.listFormattedStringToWidth(entry.getText(), textMaxWidth);
                int lineY = currentY + 6;
                for (String line : lines) {
                    String displayLine = isChecked ? ("\u00a7m" + line) : line;
                    this.fontRendererObj.drawString(displayLine, textX, lineY, textColorFinal);
                    lineY += textLineHeight;
                }

                // Timestamp (shown only if row is tall enough, at bottom right)
                if (rowH >= minRowHeight + textLineHeight) {
                    String timeStr = I18n.format("adm.planner.added_at", entry.getFormattedTime());
                    int timeX = listStartX + listWidth - this.fontRendererObj.getStringWidth(timeStr) - 25;
                    this.fontRendererObj.drawString(timeStr, timeX, lineY - textLineHeight, timestampColor);
                }
            } else {
                String emptyText = I18n.format("adm.planner.empty_slot");
                this.fontRendererObj.drawString(emptyText, textX, currentY + 6, 0x555555);
            }

            currentY += rowH;
            rowIndex++;
        }

        // Draw "add new" row at the bottom
        if (currentY < listStartY + visibleAreaHeight) {
            int addRowY = currentY;
            int addRowH = minRowHeight;
            int addClipTop = Math.max(addRowY, listStartY);
            int addClipBottom = Math.min(addRowY + addRowH, listStartY + visibleAreaHeight);
            if (addClipBottom > addClipTop) {
                if (rowIndex % 2 == 0) {
                    drawRect(listStartX, addClipTop, listStartX + listWidth, addClipBottom, 0x30004040);
                } else {
                    drawRect(listStartX, addClipTop, listStartX + listWidth, addClipBottom, 0x20002020);
                }

                int chkX = listStartX + 5;
                int chkY = addRowY + 3;
                drawRect(chkX, chkY, chkX + 12, chkY + 12, 0xFF333333);
                drawRect(chkX + 1, chkY + 1, chkX + 11, chkY + 11, 0xFF000000);

                int nextSlot = ItemAdvancePlanner.getNextSlotIndex(plannerStack);
                String slotNum = String.format("#%-3d", nextSlot);
                this.fontRendererObj.drawString(slotNum, listStartX + 20, addRowY + 6, slotNumberColor);

                boolean hoveringAdd = mouseX >= listStartX && mouseX <= listStartX + listWidth
                    && mouseY >= addClipTop
                    && mouseY <= addClipBottom;
                int addTextColor = hoveringAdd ? 0x00FF00 : 0x00AA00;
                String addText = I18n.format("adm.planner.add_hint");
                this.fontRendererObj.drawString(addText, listStartX + 50, addRowY + 6, addTextColor);
            }
        }

        GL11.glDisable(GL11.GL_SCISSOR_TEST);

        // Scrollbar
        if (totalContentHeight > visibleAreaHeight) {
            int scrollbarX = listStartX + listWidth + 4;
            int scrollbarHeight = visibleAreaHeight;

            int btnTopH = 8;
            drawRect(scrollbarX, listStartY, scrollbarX + 6, listStartY + btnTopH, 0xFF444444);
            boolean hoveringTopBtn = mouseX >= scrollbarX && mouseX <= scrollbarX + 6
                && mouseY >= listStartY
                && mouseY <= listStartY + btnTopH;
            int topArrowColor = hoveringTopBtn ? 0xFF00FFFF : 0xFF888888;
            drawRect(scrollbarX + 2, listStartY + 2, scrollbarX + 4, listStartY + 3, topArrowColor);
            drawRect(scrollbarX + 1, listStartY + 3, scrollbarX + 5, listStartY + 4, topArrowColor);
            drawRect(scrollbarX, listStartY + 4, scrollbarX + 6, listStartY + 5, topArrowColor);

            int trackTop = listStartY + btnTopH + 1;
            int trackBottom = listStartY + scrollbarHeight - btnTopH - 1;
            drawRect(scrollbarX, trackTop, scrollbarX + 6, trackBottom, 0xFF333333);

            int trackHeight = trackBottom - trackTop;
            int thumbHeight = Math.max(10, trackHeight * visibleAreaHeight / totalContentHeight);
            int thumbY = trackTop + (maxScroll == 0 ? 0 : (scrollOffset * (trackHeight - thumbHeight)) / maxScroll);
            drawRect(scrollbarX + 1, thumbY, scrollbarX + 5, thumbY + thumbHeight, 0xFF00CCCC);

            int btnBottomY = listStartY + scrollbarHeight - btnTopH;
            drawRect(scrollbarX, btnBottomY, scrollbarX + 6, btnBottomY + btnTopH, 0xFF444444);
            boolean hoveringBottomBtn = mouseX >= scrollbarX && mouseX <= scrollbarX + 6
                && mouseY >= btnBottomY
                && mouseY <= btnBottomY + btnTopH;
            int bottomArrowColor = hoveringBottomBtn ? 0xFF00FFFF : 0xFF888888;
            drawRect(scrollbarX, btnBottomY + 3, scrollbarX + 6, btnBottomY + 4, bottomArrowColor);
            drawRect(scrollbarX + 1, btnBottomY + 4, scrollbarX + 5, btnBottomY + 5, bottomArrowColor);
            drawRect(scrollbarX + 2, btnBottomY + 5, scrollbarX + 4, btnBottomY + 6, bottomArrowColor);
        }
    }

    private void drawTooltip(int mouseX, int mouseY) {
        if (!isMouseInListArea(mouseX, mouseY)) return;

        int[] entryInfo = getEntryAtMouseY(mouseY);
        if (entryInfo == null) return;

        int slotIndex = entryInfo[0];
        int rowY = entryInfo[1];
        PlannerEntry entry = entryMap.get(slotIndex);

        int arrowX = listStartX + listWidth - 18;
        int arrowY = rowY + 3;
        if (mouseX >= arrowX && mouseX <= arrowX + 14 && mouseY >= arrowY && mouseY <= arrowY + 12) {
            boolean isShiftDown = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
            String tip = isShiftDown ? I18n.format("adm.planner.move_to_top") : I18n.format("adm.planner.move_up");
            drawHoveringText(tip, mouseX, mouseY);
            return;
        }

        if (entry != null && entry.getText() != null
            && !entry.getText()
                .isEmpty()) {
            int checkboxX = listStartX + 5;
            if (mouseX >= checkboxX && mouseX <= checkboxX + 12) {
                String tip = entry.isCompleted() ? I18n.format("adm.planner.mark_incomplete")
                    : I18n.format("adm.planner.mark_complete");
                drawHoveringText(tip, mouseX, mouseY);
                return;
            }
            int textX = listStartX + 50;
            if (mouseX >= textX && mouseX <= listStartX + listWidth - 25) {
                String status = entry.isCompleted() ? I18n.format("adm.planner.completed")
                    : I18n.format("adm.planner.pending");
                drawHoveringText(status, mouseX, mouseY);
                return;
            }
        }

        for (GuiButton btn : this.buttonList) {
            if (btn instanceof ADM_GuiButton && btn.visible) {
                if (mouseX >= btn.xPosition && mouseX <= btn.xPosition + btn.width
                    && mouseY >= btn.yPosition
                    && mouseY <= btn.yPosition + btn.height) {
                    if (btn.id == buttonMergeId) {
                        drawHoveringText(I18n.format("adm.planner.merge_tooltip"), mouseX, mouseY);
                    } else if (btn.id == buttonDeleteId) {
                        drawHoveringText(I18n.format("adm.planner.delete_tooltip"), mouseX, mouseY);
                    } else if (btn.id == buttonAddNewId) {
                        drawHoveringText(I18n.format("adm.planner.add_tooltip"), mouseX, mouseY);
                    }
                }
            }
        }
    }

    private void drawHoveringText(String text, int x, int y) {
        List<String> lines = new ArrayList<>();
        lines.add(text);
        this.drawHoveringText(lines, x, y, this.fontRendererObj);
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        commitTitle();
        if (selectedSlotIndex >= 0) {
            commitEdit();
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
