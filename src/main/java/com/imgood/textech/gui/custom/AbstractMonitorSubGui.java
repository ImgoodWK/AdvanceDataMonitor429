package com.imgood.textech.gui.custom;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

import org.lwjgl.input.Keyboard;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.gui.guiscreen.GuiMainAdvanceDataMonitor;
import com.imgood.textech.gui.framework.UiFeedbackArea;
import com.imgood.textech.network.packet.PacketSynTileEntity;
import com.imgood.textech.tileentity.TileEntityAdvanceDataMonitor;

/**
 * Base class for per-binding Advance Data Monitor sub-configuration screens.
 * Centralizes text-field rendering, layout helpers, and tile sync.
 */
public abstract class AbstractMonitorSubGui extends ADM_GuiScreen {

    protected final TileEntityAdvanceDataMonitor tileEntity;
    protected final EntityPlayer player;
    protected final World world;
    protected final int index;

    protected final List<ADM_GuiTextField> textFieldsLeft = new ArrayList<>();
    protected final List<ADM_GuiTextField> textFieldsRight = new ArrayList<>();
    protected final Map<ADM_GuiTextField, String> fieldHints = new HashMap<>();

    protected ADM_GuiTextField hoveredTextField;
    protected ADM_GuiTextField focusedField;
    protected final List<String> contents = new ArrayList<>();

    protected String errorTips = "";
    protected boolean isInitialized;
    protected boolean isEnabled;

    protected int offsetX = 100;
    protected int offsetY = 100;
    protected int startOffsetX = -270;
    protected int startOffsetY = -200;
    protected int textColor = 0x00FFFF;
    protected int textHoverColor = 0x0055FF;
    protected int buttonRowYOffset1 = 370;
    protected int buttonRow1Width = 60;

    protected AbstractMonitorSubGui(EntityPlayer player, World world, TileEntityAdvanceDataMonitor tileEntity,
        int index) {
        this.player = player;
        this.world = world;
        this.tileEntity = tileEntity;
        this.index = index;
        this.setBackgroundTexture(AdmGuiTextures.BACKGROUND_SUB);
        this.setStretch(false);
    }

    protected ResourceLocation textFieldNormalTexture() {
        return AdmGuiTextures.TEXTFIELD_8020;
    }

    protected ResourceLocation textFieldFocusedTexture() {
        return AdmGuiTextures.TEXTFIELD_HOVER_8020;
    }

    protected void beginInitGui() {
        Keyboard.enableRepeatEvents(true);
        isEnabled = tileEntity.getEnable(index);
    }

    protected void layoutMonitorPanel() {
        this.offsetX = (this.width / 2) + startOffsetX;
        this.offsetY = (this.height / 2) + startOffsetY;
        this.buttonList.clear();
        this.setPosition(this.offsetX - 20, this.offsetY - 35);
    }

    protected void saveAndSync(NBTTagCompound nbt) {
        tileEntity.setDisplayData(index, nbt);
        tileEntity.writeToNBT(nbt);
        PacketSynTileEntity packet = new PacketSynTileEntity(tileEntity.xCoord, tileEntity.yCoord, tileEntity.zCoord, nbt);
        if (packet.fitsPacketBudget()) {
            AdvanceDataMonitor.ADMCHANEL.sendToServer(packet);
        }
    }

    protected void openMainGui() {
        this.mc.displayGuiScreen(
            new GuiMainAdvanceDataMonitor(player, world, tileEntity).setPosition(0, 0)
                .setSize(200, 200)
                .setBackgroundTexture(AdmGuiTextures.BACKGROUND_MONITOR_MAIN));
    }

    /**
     * Subclasses assign field references when text fields are created.
     */
    protected void assignTextField(String row, int fieldIndex, ADM_GuiTextField field) {}

    public void autoTextField(String row, List<ADM_GuiTextField> textFields, int intervalX, int intervalY, int startX,
        int startY, int width, int height) {
        int curX = 0;
        int curY = 0;
        ResourceLocation normal = textFieldNormalTexture();
        ResourceLocation focused = textFieldFocusedTexture();
        for (int i = 0; i < textFields.size(); i++) {
            final ADM_GuiTextField field = new ADM_GuiTextField(
                this.fontRendererObj,
                startX + curX,
                startY + curY,
                width,
                height).setBackgroundTexture(normal)
                    .setFocusedBackgroundTexture(focused);
            field.setOnTextChanged(new Runnable() {

                @Override
                public void run() {
                    field.setInvalid(false);
                    errorTips = "";
                }
            });
            textFields.set(i, field);
            assignTextField(row, i, field);
            curX += intervalX;
            curY += intervalY;
        }
    }

    public void drawTextFieldBackground(ADM_GuiTextField textField, int x, int y, int width, int height) {
        this.drawImage(textField.getTextFieldTexture(), x, y, width, height);
    }

    public void drawTextFieldFocusBackground(ADM_GuiTextField textField, int x, int y, int width, int height) {
        this.drawImage(textField.getFocusedTextFieldTexture(), x, y, width, height);
    }

    public void drawTextFieldBackground(List<ADM_GuiTextField> textFields) {
        for (ADM_GuiTextField tf : textFields) {
            int xCoord = tf.xPosition;
            int yCoord = tf.yPosition + 2;
            if (tf.isFocused()) {
                drawTextFieldFocusBackground(tf, xCoord, yCoord, 100, 20);
            } else {
                drawTextFieldBackground(tf, xCoord, yCoord, 100, 20);
            }
        }
    }

    public void autoText(String[] text, int intervalX, int intervalY, int startX, int startY, int color) {
        autoText(text, intervalX, intervalY, startX, startY, color, false);
    }

    public void autoText(String[] text, int intervalX, int intervalY, int startX, int startY, int color,
        boolean textCenter) {
        int curX = 0;
        int curY = 0;
        for (String t : text) {
            int x = startX + curX;
            int y = startY + curY;
            if (textCenter) {
                x = startX - fontRendererObj.getStringWidth(t) / 2 + curX;
            }
            fontRendererObj.drawString(t, x, y, color);
            curX += intervalX;
            curY += intervalY;
        }
    }

    protected boolean isMouseOver(ADM_GuiTextField textField, int mouseX, int mouseY) {
        return mouseX >= textField.xPosition && mouseX < textField.xPosition + textField.width
            && mouseY >= textField.yPosition
            && mouseY < textField.yPosition + textField.height;
    }

    protected void drawTextFieldsWithHover(int mouseX, int mouseY) {
        hoveredTextField = null;
        for (ADM_GuiTextField field : textFieldsLeft) {
            field.drawTextBox();
            if (isMouseOver(field, mouseX, mouseY)) {
                hoveredTextField = field;
            }
        }
        for (ADM_GuiTextField field : textFieldsRight) {
            field.drawTextBox();
            if (isMouseOver(field, mouseX, mouseY)) {
                hoveredTextField = field;
            }
        }
    }

    protected void drawFocusedFieldHint(int hintX, int hintY) {
        if (focusedField == null || !fieldHints.containsKey(focusedField)) {
            return;
        }
        String hint = I18n.format(fieldHints.get(focusedField));
        List<String> wrappedHint = com.imgood.textech.utils.ContentsHelper.wrapText(hint, 35);
        int yPos = hintY;
        fontRendererObj.drawStringWithShadow(I18n.format("adm.property.tips"), hintX, yPos, 0x00FFFF);
        for (String line : wrappedHint) {
            fontRendererObj
                .drawStringWithShadow(String.valueOf((char) 0x00A7) + "l" + line, hintX, yPos + 10, 0x00FFFF);
            yPos += 10;
        }
    }

    protected ADM_GuiButton monitorButton(int id, int x, int y, int width, int height, String label, int color,
        int hoverColor) {
        return new ADM_GuiButton(id, x, y, width, height, label).setTexture(AdmGuiTextures.BUTTON)
            .setHoverTexture(AdmGuiTextures.BUTTON_HOVER)
            .setUseHoverEffect(true)
            .setTextColor(color)
            .setTextHoverColor(hoverColor);
    }

    protected void beginValidation() {
        errorTips = "";
        for (ADM_GuiTextField field : textFieldsLeft) field.setInvalid(false);
        for (ADM_GuiTextField field : textFieldsRight) field.setInvalid(false);
    }

    protected boolean rejectField(ADM_GuiTextField field, String message) {
        errorTips = message != null ? message : "";
        if (field != null) {
            field.setInvalid(true);
            field.setFocused(true);
            focusedField = field;
        }
        return false;
    }

    /** Draws a bottom feedback band after the lowest visible button, never on top of a control. */
    protected void drawMonitorFeedbackBand() {
        if (errorTips == null || errorTips.isEmpty()) {
            return;
        }
        int buttonBottom = panelY();
        for (Object object : buttonList) {
            GuiButton button = (GuiButton) object;
            if (button.visible && button.yPosition >= panelY() && button.yPosition < panelY() + panelHeight()) {
                buttonBottom = Math.max(buttonBottom, button.yPosition + button.height);
            }
        }
        UiFeedbackArea feedback = UiFeedbackArea
            .afterControls(panelX() + 16, panelY(), panelWidth() - 32, panelHeight(), buttonBottom, 4, 8, 30);
        if (feedback != null) {
            feedback.draw(fontRendererObj, errorTips, 0xFF5555);
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        super.keyTyped(typedChar, keyCode);
        for (ADM_GuiTextField field : textFieldsLeft) {
            field.textboxKeyTyped(typedChar, keyCode);
        }
        for (ADM_GuiTextField field : textFieldsRight) {
            field.textboxKeyTyped(typedChar, keyCode);
        }
    }

    @Override
    protected void handleAdmMouseClicked(int mouseX, int mouseY, int mouseButton) {
        super.handleAdmMouseClicked(mouseX, mouseY, mouseButton);
        for (ADM_GuiTextField field : textFieldsLeft) {
            field.mouseClicked(mouseX, mouseY, mouseButton);
            if (field.isFocused()) {
                focusedField = field;
            }
        }
        for (ADM_GuiTextField field : textFieldsRight) {
            field.mouseClicked(mouseX, mouseY, mouseButton);
            if (field.isFocused()) {
                focusedField = field;
            }
        }
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        for (ADM_GuiTextField field : textFieldsLeft) {
            field.updateCursorCounter();
        }
        for (ADM_GuiTextField field : textFieldsRight) {
            field.updateCursorCounter();
        }
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
