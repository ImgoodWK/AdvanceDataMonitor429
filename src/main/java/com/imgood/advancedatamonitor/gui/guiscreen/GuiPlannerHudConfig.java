package com.imgood.advancedatamonitor.gui.guiscreen;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import com.imgood.advancedatamonitor.AdvanceDataMonitor;
import com.imgood.advancedatamonitor.Config;
import com.imgood.advancedatamonitor.gui.costom.ADM_GuiButton;
import com.imgood.advancedatamonitor.gui.costom.ADM_GuiScreen;
import com.imgood.advancedatamonitor.gui.costom.ADM_GuiTextField;
import com.imgood.advancedatamonitor.items.ItemAdvancePlanner;

public class GuiPlannerHudConfig extends ADM_GuiScreen {

    private final ItemStack plannerStack;
    private final EntityPlayer player;

    private ADM_GuiTextField maxDisplayField;
    private ADM_GuiTextField posXField;
    private ADM_GuiTextField posYField;
    private ADM_GuiTextField scaleField;
    private ADM_GuiTextField widthField;
    private String errorTips = "";

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

    private int buttonSaveId = 0;
    private int buttonCancelId = 1;

    private int centerX;
    private int centerY;

    // Preview area dimensions
    private int previewX;
    private int previewY;
    private int previewWidth = 180;
    private int previewHeight = 160;
    private int hudBoxWidth = 40;
    private int hudBoxHeight = 24;

    // Dragging state
    private boolean draggingHudBox = false;
    private int dragOffsetX = 0;
    private int dragOffsetY = 0;

    // Current position in pixels within preview area
    private int hudPixelX;
    private int hudPixelY;

    public GuiPlannerHudConfig(ItemStack plannerStack, EntityPlayer player) {
        this.plannerStack = plannerStack;
        this.player = player;
        this.setBackgroundTexture(BACKGROUND_TEXTURE);
        this.setSize(440, 280);
        this.setStretch(false);
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        this.setPosition((this.width - 440) / 2, (this.height - 280) / 2);
        this.centerX = this.width / 2;
        this.centerY = this.height / 2;

        this.buttonList.clear();

        // Left side: labels + text fields
        int labelX = centerX - 185;
        int fieldX = centerX - 40;

        // Max display
        int y0 = centerY - 70;
        maxDisplayField = createField(fieldX, y0, 60, getMaxDigits(Config.plannerHudMaxMaxDisplay));
        maxDisplayField.setText(String.valueOf(ItemAdvancePlanner.getHudMaxDisplay(plannerStack)));

        // Pos X
        int y1 = centerY - 40;
        posXField = createField(fieldX, y1, 60, 4);
        posXField.setText(String.format("%.2f", ItemAdvancePlanner.getHudPosX(plannerStack)));

        // Pos Y
        int y2 = centerY - 10;
        posYField = createField(fieldX, y2, 60, 4);
        posYField.setText(String.format("%.2f", ItemAdvancePlanner.getHudPosY(plannerStack)));

        // Scale
        int y3 = centerY + 20;
        scaleField = createField(fieldX, y3, 60, 6);
        scaleField.setText(String.format("%.1f", ItemAdvancePlanner.getHudScale(plannerStack)));

        // Width
        int y4 = centerY + 50;
        widthField = createField(fieldX, y4, 60, getMaxDigits(Config.plannerHudMaxWidth));
        widthField.setText(String.valueOf(ItemAdvancePlanner.getHudWidth(plannerStack)));

        maxDisplayField.setFocused(true);

        // Preview area on the right
        previewX = centerX + 20;
        previewY = centerY - 80;

        // Initialize HUD box pixel position from current posX/posY ratios
        float posX = ItemAdvancePlanner.getHudPosX(plannerStack);
        float posY = ItemAdvancePlanner.getHudPosY(plannerStack);
        hudPixelX = (int) (posX * (previewWidth - hudBoxWidth));
        hudPixelY = (int) (posY * (previewHeight - hudBoxHeight));

        // Buttons at the bottom
        this.buttonList.add(
            new ADM_GuiButton(buttonSaveId, centerX - 60, centerY + 80, 50, 20, I18n.format("adm.button.save"))
                .setTexture(BUTTON_TEXTURE)
                .setHoverTexture(BUTTON_HOVER_TEXTURE)
                .setUseHoverEffect(true)
                .setTextColor(0x00FF00)
                .setTextHoverColor(0x55FF55));

        this.buttonList.add(
            new ADM_GuiButton(buttonCancelId, centerX + 10, centerY + 80, 50, 20, I18n.format("adm.button.cancel"))
                .setTexture(BUTTON_TEXTURE)
                .setHoverTexture(BUTTON_HOVER_TEXTURE)
                .setUseHoverEffect(true)
                .setTextColor(0xFF5555)
                .setTextHoverColor(0xFF0000));

        updateScreen();
    }

    private ADM_GuiTextField createField(int x, int y, int w, int maxLen) {
        ADM_GuiTextField field = new ADM_GuiTextField(this.fontRendererObj, x, y, w, 20);
        field.setMaxStringLength(maxLen);
        field.setBackgroundTexture(TEXTFIELD_TEXTURE);
        field.setFocusedBackgroundTexture(TEXTFIELD_HOVER_TEXTURE);
        return field;
    }

    private int getMaxDigits(int value) {
        return String.valueOf(Math.max(0, value))
            .length();
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == buttonSaveId) {
            saveConfig();
        } else if (button.id == buttonCancelId) {
            this.mc.displayGuiScreen(new GuiAdvancePlanner(plannerStack, player));
        }
    }

    private void saveConfig() {
        // Validate max display
        try {
            int maxVal = Integer.parseInt(
                maxDisplayField.getText()
                    .trim());
            if (maxVal < Config.plannerHudMinMaxDisplay || maxVal > Config.plannerHudMaxMaxDisplay) {
                errorTips = I18n.format("adm.planner.hud_max_display_hint");
                return;
            }
            ItemAdvancePlanner.setHudMaxDisplay(plannerStack, maxVal);
        } catch (NumberFormatException e) {
            errorTips = I18n.format("adm.error.invalid_number");
            return;
        }

        // Validate pos X
        try {
            float xVal = Float.parseFloat(
                posXField.getText()
                    .trim());
            if (xVal < Config.plannerHudMinPosX || xVal > Config.plannerHudMaxPosX) {
                errorTips = I18n.format("adm.planner.hud_pos_hint");
                return;
            }
            ItemAdvancePlanner.setHudPosX(plannerStack, xVal);
        } catch (NumberFormatException e) {
            errorTips = I18n.format("adm.error.invalid_number");
            return;
        }

        // Validate pos Y
        try {
            float yVal = Float.parseFloat(
                posYField.getText()
                    .trim());
            if (yVal < Config.plannerHudMinPosY || yVal > Config.plannerHudMaxPosY) {
                errorTips = I18n.format("adm.planner.hud_pos_hint");
                return;
            }
            ItemAdvancePlanner.setHudPosY(plannerStack, yVal);
        } catch (NumberFormatException e) {
            errorTips = I18n.format("adm.error.invalid_number");
            return;
        }

        // Validate scale
        try {
            float sVal = Float.parseFloat(
                scaleField.getText()
                    .trim());
            if (sVal < Config.plannerHudMinScale || sVal > Config.plannerHudMaxScale) {
                errorTips = I18n.format("adm.planner.hud_scale_hint");
                return;
            }
            ItemAdvancePlanner.setHudScale(plannerStack, sVal);
        } catch (NumberFormatException e) {
            errorTips = I18n.format("adm.error.invalid_number");
            return;
        }

        // Validate width
        try {
            int widthVal = Integer.parseInt(
                widthField.getText()
                    .trim());
            if (widthVal < Config.plannerHudMinWidth || widthVal > Config.plannerHudMaxWidth) {
                errorTips = I18n.format("adm.planner.hud_width_hint");
                return;
            }
            ItemAdvancePlanner.setHudWidth(plannerStack, widthVal);
        } catch (NumberFormatException e) {
            errorTips = I18n.format("adm.error.invalid_number");
            return;
        }

        ItemAdvancePlanner.syncPlannerToServer(player, plannerStack);
        errorTips = "";
        this.mc.displayGuiScreen(new GuiAdvancePlanner(plannerStack, player));
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_RETURN) {
            saveConfig();
            return;
        } else if (keyCode == Keyboard.KEY_ESCAPE) {
            this.mc.displayGuiScreen(new GuiAdvancePlanner(plannerStack, player));
            return;
        }
        if (keyCode == Keyboard.KEY_TAB) {
            cycleFocus();
            return;
        }
        if (maxDisplayField != null && maxDisplayField.isFocused()) maxDisplayField.textboxKeyTyped(typedChar, keyCode);
        else if (posXField != null && posXField.isFocused()) posXField.textboxKeyTyped(typedChar, keyCode);
        else if (posYField != null && posYField.isFocused()) posYField.textboxKeyTyped(typedChar, keyCode);
        else if (scaleField != null && scaleField.isFocused()) scaleField.textboxKeyTyped(typedChar, keyCode);
        else if (widthField != null && widthField.isFocused()) widthField.textboxKeyTyped(typedChar, keyCode);
        super.keyTyped(typedChar, keyCode);
    }

    private void cycleFocus() {
        if (maxDisplayField != null && maxDisplayField.isFocused()) {
            maxDisplayField.setFocused(false);
            if (posXField != null) posXField.setFocused(true);
        } else if (posXField != null && posXField.isFocused()) {
            posXField.setFocused(false);
            if (posYField != null) posYField.setFocused(true);
        } else if (posYField != null && posYField.isFocused()) {
            posYField.setFocused(false);
            if (scaleField != null) scaleField.setFocused(true);
        } else if (scaleField != null && scaleField.isFocused()) {
            scaleField.setFocused(false);
            if (widthField != null) widthField.setFocused(true);
        } else if (widthField != null && widthField.isFocused()) {
            widthField.setFocused(false);
            if (maxDisplayField != null) maxDisplayField.setFocused(true);
        }
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();

        int mouseX = Mouse.getEventX() * this.width / this.mc.displayWidth;
        int mouseY = this.height - Mouse.getEventY() * this.height / this.mc.displayHeight - 1;

        int scrollDelta = Mouse.getEventDWheel();

        if (scrollDelta != 0 && isMouseInPreview(mouseX, mouseY)) {
            if (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT)) {
                int currentWidth = ItemAdvancePlanner.getHudWidth(plannerStack);
                int newWidth = currentWidth + (scrollDelta > 0 ? 10 : -10);
                newWidth = Math.max(Config.plannerHudMinWidth, Math.min(Config.plannerHudMaxWidth, newWidth));

                ItemAdvancePlanner.setHudWidth(plannerStack, newWidth);
                widthField.setText(String.valueOf(newWidth));
            } else {
                float currentScale = ItemAdvancePlanner.getHudScale(plannerStack);
                float newScale = currentScale + (scrollDelta > 0 ? 0.1f : -0.1f);
                newScale = Math.max(Config.plannerHudMinScale, Math.min(Config.plannerHudMaxScale, newScale));
                newScale = Math.round(newScale * 10.0f) / 10.0f;

                ItemAdvancePlanner.setHudScale(plannerStack, newScale);
                scaleField.setText(String.format("%.1f", newScale));
            }
            ItemAdvancePlanner.syncPlannerToServer(player, plannerStack);
        }
    }

    private boolean isMouseInPreview(int mouseX, int mouseY) {
        return mouseX >= previewX && mouseX <= previewX + previewWidth
            && mouseY >= previewY
            && mouseY <= previewY + previewHeight;
    }

    private boolean isMouseOnHudBox(int mouseX, int mouseY) {
        int boxScreenX = previewX + hudPixelX;
        int boxScreenY = previewY + hudPixelY;
        return mouseX >= boxScreenX && mouseX <= boxScreenX + hudBoxWidth
            && mouseY >= boxScreenY
            && mouseY <= boxScreenY + hudBoxHeight;
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        super.mouseClicked(mouseX, mouseY, mouseButton);

        // Pass clicks to all fields
        if (maxDisplayField != null) maxDisplayField.mouseClicked(mouseX, mouseY, mouseButton);
        if (posXField != null) posXField.mouseClicked(mouseX, mouseY, mouseButton);
        if (posYField != null) posYField.mouseClicked(mouseX, mouseY, mouseButton);
        if (scaleField != null) scaleField.mouseClicked(mouseX, mouseY, mouseButton);
        if (widthField != null) widthField.mouseClicked(mouseX, mouseY, mouseButton);

        // Start dragging the HUD box
        if (mouseButton == 0 && isMouseOnHudBox(mouseX, mouseY)) {
            draggingHudBox = true;
            dragOffsetX = mouseX - (previewX + hudPixelX);
            dragOffsetY = mouseY - (previewY + hudPixelY);
        }
    }

    @Override
    protected void mouseMovedOrUp(int mouseX, int mouseY, int which) {
        super.mouseMovedOrUp(mouseX, mouseY, which);

        if (which == 0 && draggingHudBox) {
            draggingHudBox = false;
            // Sync position on mouse release
            ItemAdvancePlanner.syncPlannerToServer(player, plannerStack);
        }
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);

        if (draggingHudBox && isMouseInPreview(mouseX, mouseY)) {
            int newPixelX = mouseX - dragOffsetX - previewX;
            int newPixelY = mouseY - dragOffsetY - previewY;

            // Clamp to preview area bounds
            newPixelX = Math.max(0, Math.min(previewWidth - hudBoxWidth, newPixelX));
            newPixelY = Math.max(0, Math.min(previewHeight - hudBoxHeight, newPixelY));

            hudPixelX = newPixelX;
            hudPixelY = newPixelY;

            // Convert pixel position to ratio (0.0 - 1.0)
            float posX = (float) hudPixelX / (previewWidth - hudBoxWidth);
            float posY = (float) hudPixelY / (previewHeight - hudBoxHeight);

            // Clamp ratios
            posX = Math.max(Config.plannerHudMinPosX, Math.min(Config.plannerHudMaxPosX, posX));
            posY = Math.max(Config.plannerHudMinPosY, Math.min(Config.plannerHudMaxPosY, posY));

            ItemAdvancePlanner.setHudPosX(plannerStack, posX);
            ItemAdvancePlanner.setHudPosY(plannerStack, posY);

            posXField.setText(String.format("%.2f", posX));
            posYField.setText(String.format("%.2f", posY));
        }
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (maxDisplayField != null) maxDisplayField.updateCursorCounter();
        if (posXField != null) posXField.updateCursorCounter();
        if (posYField != null) posYField.updateCursorCounter();
        if (scaleField != null) scaleField.updateCursorCounter();
        if (widthField != null) widthField.updateCursorCounter();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();

        this.drawCenteredString(
            this.fontRendererObj,
            I18n.format("adm.planner.hud_config_title"),
            centerX,
            centerY - 105,
            textColor);

        int labelX = centerX - 185;
        int fieldX = centerX - 40;

        // Row 0: Max display
        this.fontRendererObj.drawString(I18n.format("adm.planner.hud_max_display"), labelX, centerY - 65, textColor);
        if (maxDisplayField != null) maxDisplayField.drawTextBox();
        this.fontRendererObj
            .drawString(I18n.format("adm.planner.hud_max_display_hint"), fieldX + 65, centerY - 65, 0x888888);

        // Row 1: Pos X
        this.fontRendererObj.drawString(I18n.format("adm.planner.hud_pos_x"), labelX, centerY - 35, textColor);
        if (posXField != null) posXField.drawTextBox();
        this.fontRendererObj.drawString(I18n.format("adm.planner.hud_pos_hint"), fieldX + 65, centerY - 35, 0x888888);

        // Row 2: Pos Y
        this.fontRendererObj.drawString(I18n.format("adm.planner.hud_pos_y"), labelX, centerY - 5, textColor);
        if (posYField != null) posYField.drawTextBox();
        this.fontRendererObj.drawString(I18n.format("adm.planner.hud_pos_hint"), fieldX + 65, centerY - 5, 0x888888);

        // Row 3: Scale
        this.fontRendererObj.drawString(I18n.format("adm.planner.hud_scale"), labelX, centerY + 25, textColor);
        if (scaleField != null) scaleField.drawTextBox();
        this.fontRendererObj.drawString(I18n.format("adm.planner.hud_scale_hint"), fieldX + 65, centerY + 25, 0x888888);

        // Row 4: Width
        this.fontRendererObj.drawString(I18n.format("adm.planner.hud_width"), labelX, centerY + 55, textColor);
        if (widthField != null) widthField.drawTextBox();
        this.fontRendererObj.drawString(I18n.format("adm.planner.hud_width_hint"), fieldX + 65, centerY + 55, 0x888888);

        // Draw preview area
        drawPreviewArea(mouseX, mouseY);

        if (!errorTips.isEmpty()) {
            this.drawCenteredString(this.fontRendererObj, errorTips, centerX, centerY + 95, 0xFF5555);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawPreviewArea(int mouseX, int mouseY) {
        // Preview area title
        this.fontRendererObj.drawString(I18n.format("adm.planner.hud_preview"), previewX, previewY - 14, textColor);

        // Border
        drawRect(previewX - 1, previewY - 1, previewX + previewWidth + 1, previewY + previewHeight + 1, 0xFF00AAAA);
        // Background
        drawRect(previewX, previewY, previewX + previewWidth, previewY + previewHeight, 0x40000000);

        // Draw grid lines for visual reference
        int gridColor = 0x20FFFFFF;
        for (int i = 1; i < 4; i++) {
            int lineX = previewX + (previewWidth * i / 4);
            int lineY = previewY + (previewHeight * i / 4);
            drawRect(lineX, previewY, lineX + 1, previewY + previewHeight, gridColor);
            drawRect(previewX, lineY, previewX + previewWidth, lineY + 1, gridColor);
        }

        // Draw "screen" label at corners
        int dimColor = 0x40FFFFFF;
        this.fontRendererObj
            .drawString(I18n.format("adm.planner.hud_screen_area"), previewX + 3, previewY + 3, dimColor);

        // Draw the HUD position box
        boolean hovering = isMouseOnHudBox(mouseX, mouseY);
        int boxScreenX = previewX + hudPixelX;
        int boxScreenY = previewY + hudPixelY;
        int boxBorderColor = draggingHudBox || hovering ? 0xFF00FFFF : 0xFF0088CC;
        int boxFillColor = draggingHudBox ? 0x6000AAAA : (hovering ? 0x400088CC : 0x30004466);

        // Box fill
        drawRect(boxScreenX, boxScreenY, boxScreenX + hudBoxWidth, boxScreenY + hudBoxHeight, boxFillColor);
        // Box border
        drawRect(boxScreenX, boxScreenY, boxScreenX + hudBoxWidth, boxScreenY + 1, boxBorderColor); // top
        drawRect(
            boxScreenX,
            boxScreenY + hudBoxHeight - 1,
            boxScreenX + hudBoxWidth,
            boxScreenY + hudBoxHeight,
            boxBorderColor); // bottom
        drawRect(boxScreenX, boxScreenY, boxScreenX + 1, boxScreenY + hudBoxHeight, boxBorderColor); // left
        drawRect(
            boxScreenX + hudBoxWidth - 1,
            boxScreenY,
            boxScreenX + hudBoxWidth,
            boxScreenY + hudBoxHeight,
            boxBorderColor); // right

        // "HUD" label in the box
        String hudBoxLabel = I18n.format("adm.planner.hud_label");
        int labelWidth = this.fontRendererObj.getStringWidth(hudBoxLabel);
        this.fontRendererObj.drawString(
            hudBoxLabel,
            boxScreenX + (hudBoxWidth - labelWidth) / 2,
            boxScreenY + (hudBoxHeight - 8) / 2,
            hovering ? 0x00FFFF : 0x0088CC);

        // Display current scale and width below preview
        float scale = ItemAdvancePlanner.getHudScale(plannerStack);
        int width = ItemAdvancePlanner.getHudWidth(plannerStack);
        String scaleText = I18n.format("adm.planner.hud_preview_scale", String.format("%.1f", scale));
        String widthText = I18n.format("adm.planner.hud_preview_width", width);
        this.fontRendererObj.drawString(scaleText, previewX, previewY + previewHeight + 4, 0x888888);
        this.fontRendererObj.drawString(widthText, previewX, previewY + previewHeight + 16, 0x888888);
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
